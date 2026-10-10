/*
 * Copyright (c) 2026, zFallan121
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.osrsfliphub;

import com.google.gson.Gson;
import com.google.inject.*;
import java.util.concurrent.*;
import javax.inject.Inject;
import javax.swing.Timer;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.widgets.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.*;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.*;
import net.runelite.client.ui.*;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Filepath;
import okhttp3.OkHttpClient;
import org.slf4j.*;
import static com.osrsfliphub.Const.*;

@PluginDescriptor(
    name = "FlipHub OSRS",
    // The folder RuneLite keeps for the plugin, .runelite/plugin-data/osrs-fliphub, and the one
    // every build before this kept for itself, .runelite/fliphub, which RuneLite renames to it.
    internalName = "osrs-fliphub",
    legacyDataDirectory = "fliphub",
    description = "Track Grand Exchange flips locally (offer history, margins, buy limits, wiki prices). "
        + "Optionally link a FlipHub account to sync flips to the fliphubosrs.com dashboard.",
    configName = FliphubConfigGroups.CONFIG_GROUP,
    tags = {"ge", "flipping", "analytics"},
    hidden = false,
    developerPlugin = false
)
public class GeLifecyclePlugin extends Plugin {
    static final Logger log = LoggerFactory.getLogger(GeLifecyclePlugin.class);

    @Inject
    Client client;

    @Inject
    ClientThread clientThread;

    @Inject
    PluginConfig config;

    @Inject
    ConfigManager configManager;

    @Inject
    OkHttpClient httpClient;

    @Inject
    Gson gson;

    @Inject
    ClientToolbar clientToolbar;

    @Inject
    ItemManager itemManager;

    @Inject
    OverlayManager overlayManager;

    @Inject
    KeyManager keyManager;

    /** Batches the closing drain will try before giving up, so the client is never held long. */
    private static final int SHUTDOWN_FLUSH_MAX_BATCHES = 10;
    private static final String CLOSE_OTHER_WINDOWS = "FlipHub: close your other RuneLite windows to finish"
        + " its update. Nothing is recorded in this window until then.";

    /**
     * Asks RuneLite for the plugin's folder. RuneLite answers by renaming the old folder to the new
     * place first, and throws when it cannot: another RuneLite window is using the old one.
     */
    Callable<Filepath> folderSource = this::getPluginDirectory;
    /** FlipHub's start, run once the folder is there. */
    Runnable start = () -> PluginLifecycle.startUp(this);
    /** How long before RuneLite is asked again for a folder it could not hand over. */
    long folderRetryMs = 2_000L;
    /** Where the trade files are kept, or null until RuneLite has handed it over. */
    volatile Filepath folder;
    /**
     * Set while RuneLite cannot hand the folder over. Until it can FlipHub is as good as switched
     * off: with nowhere to read the player's history from, anything it recorded would in the end
     * be saved over that history.
     */
    private volatile boolean waitingForFolder;
    /** Asks again while {@link #waitingForFolder}, on the thread RuneLite starts plugins on. */
    private volatile Timer folderRetry;
    /** Whether this login has been told why nothing is recorded: once is enough. */
    private volatile boolean toldToCloseWindows;

    ApiClient apiClient;
    final PanelBootstrap panelBootstrapService = new PanelBootstrap();
    final RuntimeSchedulerServices runtimeSchedulerServices = new RuntimeSchedulerServices();
    final RuntimeUtilityServices runtimeUtilityServices = new RuntimeUtilityServices();
    private ProfileWatcher profileWatcher;
    ScheduledExecutorService scheduler;
    ExecutorService ioExecutor;
    volatile Integer offerPreviewItemId;
    volatile FlipHubItem offerPreviewItem;
    Panel panel;
    NavigationButton navButton;
    volatile String currentQuery = "";
    volatile int currentPage = 1;
    volatile boolean bookmarkFilterEnabled = false;
    volatile StatsItemSort currentItemSort = StatsItemSort.COMPLETION;
    volatile boolean currentItemSortAscending = false;
    volatile boolean panelVisible;
    volatile StatsRange currentStatsRange = StatsRange.SESSION;
    /**
     * When the current play session began, or 0 when logged out. Set from the game state handler,
     * which runs on the client thread; the panel only ever reads it, so the session clock never
     * has to ask the client anything from the Swing thread. Local-only - never uploaded.
     */
    volatile long sessionStartMs;
    volatile StatsItemSort currentStatsSort = StatsItemSort.COMPLETION;
    boolean localTradesLoadedThisLogin = false;
    GeOfferTimerOverlay offerTimerOverlay;

    @Provides
    PluginConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(PluginConfig.class);
    }

    /**
     * Keeps the trades runtime in the plugin's own injector, which is all that lets the plugin
     * start.
     *
     * <p>RuneLite builds a plugin in an injector of its own beneath the client's, and Guice tries
     * every service in the client's first. That one cannot finish any service that needs the
     * config, which only the plugin's holds, and when it gives one up it throws away whatever was
     * half built along the way. A service thrown away while its own constructor is still being
     * worked out, and then reached again, is "Recursive load": the first start fails, or never
     * returns, and RuneLite does not try a second time by itself. Only a service that leads back
     * to itself can be reached again, and every such loop in the plugin runs through this one
     * (the others take it or are taken by it as a Provider). Bound here it belongs to the
     * plugin's injector alone, the client's refuses it at once, and no loop is left for it to
     * half build. EveryServiceCanBeBuiltTest starts the plugin from every service in turn.
     */
    @Override
    public void configure(Binder binder) {
        binder.bind(LocalTradesRuntime.class);
    }

    @Override
    protected void startUp() {
        Bridge.set(getInjector());
        Access.set(this);
        // One still asking from a start before this one would start FlipHub a second time.
        stopAskingForFolder();
        if (folderReady()) {
            start.run();
            waitingForFolder = false;
            return;
        }
        waitingForFolder = true;
        toldToCloseWindows = false;
        Timer retry = new Timer((int) folderRetryMs, tick -> {
            // Only the one that ends the asking starts FlipHub: a tick already on its way when the
            // plugin was switched off, or the client closed, starts nothing.
            if (folderReady() && stopAskingForFolder()) {
                startAfterWaiting();
            }
        });
        folderRetry = retry;
        retry.start();
        tellPlayerToCloseOtherWindows();
    }

    /**
     * The handlers stay shut until the start has returned, as RuneLite itself sends a plugin
     * nothing before its startUp has. A fill handled part way through would be recorded with no
     * scheduler to save it on, and the start would then clear the slot position it had just set.
     * A start that throws leaves them shut for good, as RuneLite leaves such a plugin unregistered.
     */
    private void startAfterWaiting() {
        try {
            start.run();
        } catch (RuntimeException ex) {
            // RuneLite reports a start that threw only when the call was its own.
            log.error("FlipHub: unable to start", ex);
            return;
        }
        waitingForFolder = false;
    }

    @Override
    protected void shutDown() {
        if (waitingForFolder) {
            // Nothing was started, so there is nothing to stop or to save.
            stopAskingForFolder();
            waitingForFolder = false;
            return;
        }
        PluginLifecycle.shutDown(this);
    }

    /**
     * Whether RuneLite handed the folder over. A refusal is "not yet", and never "this player has
     * no history": the history is whole in the old folder, where this build does not look.
     */
    private boolean folderReady() {
        try {
            Filepath given = folderSource.call();
            folder = given;
            return given != null;
        } catch (Exception ex) {
            if (!waitingForFolder) {
                log.warn("FlipHub: RuneLite could not move the plugin's folder, so nothing is recorded"
                    + " until it can. Another RuneLite window is probably using it.", ex);
            }
            return false;
        }
    }

    /** @return whether it was still asking. */
    private synchronized boolean stopAskingForFolder() {
        Timer retry = folderRetry;
        folderRetry = null;
        if (retry != null) {
            retry.stop();
        }
        return retry != null;
    }

    /** One line in the chat for each login while it waits, written on the game thread. */
    private void tellPlayerToCloseOtherWindows() {
        invokeOnClientThread(() -> {
            // Only while it is still asking: once the folder is there, closing a window changes
            // nothing, whether FlipHub is part way through its start or the start threw.
            if (waitingForFolder && folderRetry != null && !toldToCloseWindows && Access.loggedIn(client)) {
                toldToCloseWindows = true;
                runtimeUtilityServices.pushGameMessage(client, CLOSE_OTHER_WINDOWS);
            }
        });
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (waitingForFolder) {
            return;
        }
        Bridge.get(ConfigChangedHandler.class).handle(event);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (waitingForFolder) {
            // LOGGED_IN comes again after every loading screen and world hop; only a login from
            // the login screen is told.
            if (event.getGameState() == GameState.LOGIN_SCREEN) {
                toldToCloseWindows = false;
            } else if (event.getGameState() == GameState.LOGGED_IN) {
                tellPlayerToCloseOtherWindows();
            }
            return;
        }
        Bridge.get(GameStateChangedHandler.class).handle(event.getGameState());
    }

    @Subscribe
    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event) {
        if (waitingForFolder) {
            return;
        }
        Bridge.get(GrandExchangeOfferChangedHandler.class).handle(event);
    }

    /**
     * RuneLite does not stop plugins when the client closes, so shutDown never runs on a normal
     * exit and the pending upload queue would simply be discarded. This is the only hook that
     * fires, and waitFor keeps the client alive while the drain runs.
     */
    @Subscribe
    public void onClientShutdown(ClientShutdown event) {
        if (waitingForFolder) {
            // Nothing was recorded, so nothing is unsaved. It goes on waiting, without asking, so
            // that whatever the game still reports on its way out is not recorded either.
            stopAskingForFolder();
            return;
        }
        ExecutorService activeIoExecutor = ioExecutor;
        UploadEventDispatch dispatch =
            Bridge.get(UploadEventDispatch.class);
        ApiClient apiClient = Bridge.get(ApiClient.class);
        // Writing the player's own trades is not conditional on the upload pipeline being
        // healthy. These used to share one guard, so a null dispatch service - which Guice can
        // produce silently at runtime - threw away the evening's unsaved trades on the way out.
        boolean canUpload = dispatch != null && apiClient != null && config != null;

        if (activeIoExecutor == null || activeIoExecutor.isShutdown()) {
            // No pool left to hand it to. Write on this thread rather than lose the trades.
            flushUnsavedProfilesQuietly();
            return;
        }

        event.waitFor(activeIoExecutor.submit(() -> {
            // Profile writes are queued rather than written on the game thread, so anything
            // still unsaved has to be written before the process goes. waitFor holds the client
            // open until this returns.
            flushUnsavedProfilesQuietly();
            if (canUpload) {
                dispatch.flushPendingBeforeShutdown(apiClient, config, SHUTDOWN_FLUSH_MAX_BATCHES);
            }
        }));
    }

    /**
     * Flushes unsaved trades, never letting a failure escape. At shutdown a thrown exception
     * would skip whatever the caller meant to do next, and there is no later chance to retry.
     */
    private void flushUnsavedProfilesQuietly() {
        try {
            LocalTradesRuntime runtimeService = getLocalTradesRuntimeService();
            if (runtimeService != null) {
                runtimeService.flushUnsavedProfiles();
            } else {
                log.warn("FlipHub: no local trades service at shutdown, unsaved trades not written");
            }
        } catch (RuntimeException ex) {
            log.warn("FlipHub: failed to flush unsaved trades at client shutdown", ex);
        }
    }

    @Subscribe
    public void onPostClientTick(PostClientTick event) {
        if (waitingForFolder) {
            return;
        }
        panelVisible = Bridge.get(TickServices.class).handlePostClientTick(panelVisible);
        // local profile loads are handled on login/selection
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event) {
        if (waitingForFolder) {
            return;
        }
        int scriptId = event.getScriptId();
        if (scriptId == ScriptID.CHAT_TEXT_INPUT_REBUILD ||
            scriptId == ScriptID.CHAT_PROMPT_INIT ||
            scriptId == ScriptID.MESSAGE_LAYER_OPEN) {
            Bridge.get(ChatboxSuggestionRuntimeState.class).markSuggestionDirty();
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (waitingForFolder) {
            return;
        }
        Bridge.get(SkillTab.class).clicked(event);
    }

    @Subscribe
    public void onVarClientIntChanged(VarClientIntChanged event) {
        if (waitingForFolder || event.getIndex() != VarClientInt.INPUT_TYPE) {
            return;
        }
        // Fallback trigger for GE chatbox prompts when specific chat scripts do not fire on some client builds.
        Bridge.get(ChatboxSuggestionRuntimeState.class).markSuggestionDirty();
    }

    long getOfferLastUpdateMs(int slot, GrandExchangeOffer offer) {
        return getOfferStampStateServices().getOfferLastUpdateMs(slot, offer);
    }

    OfferStampStateServices getOfferStampStateServices() {
        return Bridge.get(OfferStampStateServices.class);
    }

    boolean isOfferStatusOpen() {
        OfferPreviewRuntime previewFacade = Bridge.get(OfferPreviewRuntime.class);
        Widget geRoot = previewFacade
            .getVisibleGeRoot(client, ComponentID.GRAND_EXCHANGE_WINDOW_CONTAINER);
        if (geRoot == null) {
            return false;
        }
        return previewFacade.isOfferStatusOpen(geRoot, OFFER_STATUS_MARKERS);
    }

    LocalTradesRuntime getLocalTradesRuntimeService() {
        return Bridge.get(LocalTradesRuntime.class);
    }

    void refreshPanelData() {
        getPanelRefreshCoordinator().refreshPanelData(scheduler);
    }

    void refreshStatsData() {
        getPanelRefreshCoordinator().refreshStatsData(scheduler);
    }

    // Retained as narrow compatibility shims for reflection-based tests.
    private void ensureProfileLoaded(long accountKey) {
        getLocalTradesRuntimeService().ensureProfileLoaded(accountKey);
    }

    void executeOnScheduler(ScheduledExecutorService scheduler, Runnable task) {
        if (scheduler != null && task != null) {
            scheduler.execute(task);
        }
    }

    void invokeOnClientThread(Runnable task) {
        if (clientThread != null && task != null) {
            clientThread.invokeLater(task);
        }
    }

    ApiClient.WipeStatsResponse wipeWebsiteStats(String sessionToken, String signingSecret) throws Exception {
        if (apiClient == null) {
            apiClient = new ApiClient(httpClient, gson, config);
        }
        return apiClient.wipeWebsiteStats(sessionToken, signingSecret);
    }

    PanelRefresh getPanelRefreshCoordinator() {
        return Bridge.get(PanelRefresh.class);
    }

    long getProfileFileModifiedMs(Filepath file) {
        return Bridge.get(ProfileStore.class).getProfileFileModifiedMs(file);
    }

    /**
     * Runs work on the plugin's own scheduler, or drops it.
     *
     * <p>It used to fall back to the common pool, which meant that once the plugin was disabled
     * its stragglers carried on running there, reaching for a client and a panel that were on
     * their way out. Nothing here is important enough to outlive the plugin.
     */
    /**
     * @return whether the task was accepted. A caller that raised an "in flight" flag before
     *         submitting must lower it again when this returns false, because the task that
     *         would have lowered it is never going to run. A flag left raised on a singleton
     *         that survives a plugin toggle disables its feature for the rest of the session.
     */
    boolean executeAsync(Runnable task) {
        if (task == null) {
            return false;
        }
        ScheduledExecutorService activeScheduler = scheduler;
        if (activeScheduler == null || activeScheduler.isShutdown()) {
            return false;
        }
        try {
            activeScheduler.execute(task);
            return true;
        } catch (RejectedExecutionException ignored) {
            // Shut down between the check and the submit. Dropping it is the point.
            return false;
        }
    }

    /** @return whether the task was accepted; see {@link #executeAsync(Runnable)}. */
    boolean executeIo(Runnable task) {
        if (task == null) {
            return false;
        }
        ExecutorService activeIoExecutor = ioExecutor;
        if (activeIoExecutor == null || activeIoExecutor.isShutdown()) {
            return executeAsync(task);
        }
        try {
            activeIoExecutor.execute(task);
            return true;
        } catch (RejectedExecutionException ignored) {
            return executeAsync(task);
        }
    }

    void markAccountwideUploadDirty() {
        Bridge.get(SummaryUploader.class).markDirty();
        if (Bridge.get(ProfileSelectionPresentation.class).isLinked()) {
            Bridge.get(UploadBackfillDispatch.class).requestAccountwideSync();
        }
    }

    void startProfileWatcher() {
        if (scheduler == null || scheduler.isShutdown()) {
            return;
        }
        stopProfileWatcher();
        profileWatcher = new ProfileWatcher(scheduler, PROFILE_WATCH_DEBOUNCE_MS);
        profileWatcher.start();
    }

    void stopProfileWatcher() {
        if (profileWatcher != null) {
            profileWatcher.stop();
            profileWatcher = null;
        }
    }

    ProfileWorkflow getProfileWorkflowService() {
        return Bridge.get(ProfileWorkflow.class);
    }
}

