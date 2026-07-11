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

import static com.osrsfliphub.GeLifecyclePluginConstants.*;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.nio.file.Path;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.ScriptID;
import net.runelite.api.VarClientInt;
import net.runelite.api.events.PostClientTick;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.ComponentID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
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

    ApiClient apiClient;
    final GeLifecyclePanelBootstrapService panelBootstrapService = new GeLifecyclePanelBootstrapService();
    final GeLifecycleRuntimeSchedulerServices runtimeSchedulerServices = new GeLifecycleRuntimeSchedulerServices();
    final GeLifecycleRuntimeUtilityServices runtimeUtilityServices = new GeLifecycleRuntimeUtilityServices();
    private ProfileWatcher profileWatcher;
    final ProfileSelectionState profileSelection = new ProfileSelectionState(ACCOUNTWIDE_KEY_STRING);
    final BookmarkConfigStore bookmarkConfigStore = new BookmarkConfigStore(ACCOUNTWIDE_KEY);
    final HiddenItemConfigStore hiddenItemConfigStore = new HiddenItemConfigStore();
    final OfferUpdateStampConfigStore offerUpdateStampConfigStore = new OfferUpdateStampConfigStore();
    final OfferUpdateStampLegacyMatcher offerUpdateStampLegacyMatcher = new OfferUpdateStampLegacyMatcher();
    ScheduledExecutorService scheduler;
    ExecutorService ioExecutor;
    final UploadDiagnosticsState uploadState = new UploadDiagnosticsState();
    final Map<Integer, OfferSnapshot> snapshots = new ConcurrentHashMap<>();
    final Map<Integer, OfferUpdateStamp> offerUpdateStamps = new ConcurrentHashMap<>();
    final Set<Integer> bookmarkedItems = ConcurrentHashMap.newKeySet();
    final Set<Integer> hiddenItems = ConcurrentHashMap.newKeySet();
    volatile Integer offerPreviewItemId;
    volatile FlipHubItem offerPreviewItem;
    FlipHubPanel panel;
    NavigationButton navButton;
    final Map<Long, Long> loadedProfileFileMs = new ConcurrentHashMap<>();
    final Map<Long, String> profileDisplayNames = new ConcurrentHashMap<>();
    volatile String currentQuery = "";
    volatile int currentPage = 1;
    volatile boolean bookmarkFilterEnabled = false;
    volatile boolean panelVisible;
    volatile StatsRange currentStatsRange = StatsRange.SESSION;
    volatile StatsItemSort currentStatsSort = StatsItemSort.COMPLETION;
    final LocalTradesLoadCoordinator.State localTradesLoadState = new LocalTradesLoadCoordinator.State();
    boolean localTradesLoadedThisLogin = false;
    GeOfferTimerOverlay offerTimerOverlay;

    @Provides
    PluginConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(PluginConfig.class);
    }

    @Override
    protected void startUp() {
        PluginInjectorBridge.set(getInjector());
        PluginAccess.set(this);
        GeLifecyclePluginLifecycleCoordinator.startUp(this);
    }

    @Override
    protected void shutDown() {
        GeLifecyclePluginLifecycleCoordinator.shutDown(this);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        PluginInjectorBridge.get(ConfigChangedHandlerService.class).handle(event);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        PluginInjectorBridge.get(GameStateChangedHandlerService.class).handle(event.getGameState());
    }

    @Subscribe
    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event) {
        PluginInjectorBridge.get(GrandExchangeOfferChangedHandlerService.class).handle(event);
    }

    @Subscribe
    public void onPostClientTick(PostClientTick event) {
        panelVisible = PluginInjectorBridge.get(GeLifecycleTickServices.class).handlePostClientTick(panelVisible);
        // local profile loads are handled on login/selection
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event) {
        int scriptId = event.getScriptId();
        if (scriptId == ScriptID.CHAT_TEXT_INPUT_REBUILD ||
            scriptId == ScriptID.CHAT_PROMPT_INIT ||
            scriptId == ScriptID.MESSAGE_LAYER_OPEN) {
            PluginInjectorBridge.get(ChatboxSuggestionRuntimeStateService.class).markSuggestionDirty();
        }
    }

    @Subscribe
    public void onVarClientIntChanged(VarClientIntChanged event) {
        if (event.getIndex() != VarClientInt.INPUT_TYPE) {
            return;
        }
        // Fallback trigger for GE chatbox prompts when specific chat scripts do not fire on some client builds.
        PluginInjectorBridge.get(ChatboxSuggestionRuntimeStateService.class).markSuggestionDirty();
    }

    long getOfferLastUpdateMs(int slot, GrandExchangeOffer offer) {
        return getOfferStampStateServices().getOfferLastUpdateMs(slot, offer);
    }

    GeLifecycleOfferStampStateServices getOfferStampStateServices() {
        return PluginInjectorBridge.get(GeLifecycleOfferStampStateServices.class);
    }

    boolean isOfferStatusOpen() {
        OfferPreviewRuntimeFacadeService previewFacade = PluginInjectorBridge.get(OfferPreviewRuntimeFacadeService.class);
        Widget geRoot = previewFacade
            .getVisibleGeRoot(client, ComponentID.GRAND_EXCHANGE_WINDOW_CONTAINER);
        if (geRoot == null) {
            return false;
        }
        return previewFacade.isOfferStatusOpen(geRoot, OFFER_STATUS_MARKERS);
    }


    GeLifecycleLocalTradesRuntimeService getLocalTradesRuntimeService() {
        return PluginInjectorBridge.get(GeLifecycleLocalTradesRuntimeService.class);
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
            apiClient = new ApiClient(httpClient, gson);
        }
        return apiClient.wipeWebsiteStats(sessionToken, signingSecret);
    }

    PanelRefreshCoordinator getPanelRefreshCoordinator() {
        return PluginInjectorBridge.get(PanelRefreshCoordinator.class);
    }


    long getProfileFileModifiedMs(Path file) {
        return PluginInjectorBridge.get(ProfileStore.class).getProfileFileModifiedMs(file);
    }

    void executeAsync(Runnable task) {
        if (task == null) {
            return;
        }
        ScheduledExecutorService activeScheduler = scheduler;
        if (activeScheduler != null && !activeScheduler.isShutdown()) {
            activeScheduler.execute(task);
            return;
        }
        ForkJoinPool.commonPool().execute(task);
    }

    void executeIo(Runnable task) {
        if (task == null) {
            return;
        }
        ExecutorService activeIoExecutor = ioExecutor;
        if (activeIoExecutor != null && !activeIoExecutor.isShutdown()) {
            activeIoExecutor.execute(task);
            return;
        }
        executeAsync(task);
    }

    void markAccountwideUploadDirty() {
        PluginInjectorBridge.get(AccountwideSummaryUploader.class).markDirty();
        if (PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class).isLinked()) {
            PluginInjectorBridge.get(UploadBackfillDispatchService.class).requestAccountwideSync();
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

    GeLifecycleProfileWorkflowService getProfileWorkflowService() {
        return PluginInjectorBridge.get(GeLifecycleProfileWorkflowService.class);
    }

    LegacyLocalTradesFilterService getLegacyLocalTradesFilterService() {
        return PluginInjectorBridge.get(LegacyLocalTradesFilterService.class);
    }

}





