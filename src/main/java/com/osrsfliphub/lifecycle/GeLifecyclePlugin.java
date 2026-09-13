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

import static com.osrsfliphub.Const.*;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.nio.file.Path;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.ScriptID;
import net.runelite.api.Skill;
import net.runelite.api.VarClientInt;
import net.runelite.api.events.PostClientTick;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.ComponentID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
    name = "OSRS FlipHub",
    description = "Track Grand Exchange flips locally (offer history, margins, buy limits, wiki prices). "
        + "Optionally link a FlipHub account to sync flips to the osrsfliphub.com dashboard.",
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

    @Override
    protected void startUp() {
        Bridge.set(getInjector());
        Access.set(this);
        PluginLifecycle.startUp(this);
    }

    @Override
    protected void shutDown() {
        PluginLifecycle.shutDown(this);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        Bridge.get(ConfigChangedHandler.class).handle(event);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        Bridge.get(GameStateChangedHandler.class).handle(event.getGameState());
    }

    @Subscribe
    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event) {
        Bridge.get(GrandExchangeOfferChangedHandler.class).handle(event);
    }

    /**
     * RuneLite does not stop plugins when the client closes, so shutDown never runs on a normal
     * exit and the pending upload queue would simply be discarded. This is the only hook that
     * fires, and waitFor keeps the client alive while the drain runs.
     */
    @Subscribe
    public void onClientShutdown(ClientShutdown event) {
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
                dispatch.flushPendingBeforeShutdown(apiClient, config, log, SHUTDOWN_FLUSH_MAX_BATCHES);
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
        panelVisible = Bridge.get(TickServices.class).handlePostClientTick(panelVisible);
        // local profile loads are handled on login/selection
    }

    @Subscribe
    public void onStatChanged(StatChanged event) {
        // What a repair cost depends on the player's Smithing level, and this is
        // the cheapest place to learn it: the client reports every skill at login
        // and again whenever one moves, on its own thread, and the fee service
        // only keeps the number - per account, since the profile being viewed
        // is not always the one logged in.
        if (event.getSkill() == Skill.SMITHING) {
            FeeService feeService = Bridge.get(FeeService.class);
            long accountKey = client != null ? client.getAccountHash() : -1L;
            if (feeService != null && feeService.onSmithingLevel(accountKey, event.getLevel())) {
                // Every repair of this account was priced with the old level.
                // The ledgers are pure functions over the deltas, so throwing
                // the aggregates away is the whole of the migration.
                LocalStatsCacheService statsCacheService = Bridge.get(LocalStatsCacheService.class);
                if (statsCacheService != null) {
                    statsCacheService.invalidateAll();
                }
                PanelRefresh coordinator = getPanelRefreshCoordinator();
                if (coordinator != null) {
                    coordinator.triggerStatsRefresh(scheduler);
                }
            }
        }
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event) {
        int scriptId = event.getScriptId();
        if (scriptId == ScriptID.CHAT_TEXT_INPUT_REBUILD ||
            scriptId == ScriptID.CHAT_PROMPT_INIT ||
            scriptId == ScriptID.MESSAGE_LAYER_OPEN) {
            Bridge.get(ChatboxSuggestionRuntimeState.class).markSuggestionDirty();
        }
    }

    @Subscribe
    public void onVarClientIntChanged(VarClientIntChanged event) {
        if (event.getIndex() != VarClientInt.INPUT_TYPE) {
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


    long getProfileFileModifiedMs(Path file) {
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





