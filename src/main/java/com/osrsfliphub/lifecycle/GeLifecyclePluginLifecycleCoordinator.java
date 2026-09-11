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

import net.runelite.api.GameState;

final class GeLifecyclePluginLifecycleCoordinator {
    private GeLifecyclePluginLifecycleCoordinator() {
    }

    static void startUp(GeLifecyclePlugin plugin) {
        GeLifecyclePlugin.log.info("FlipHub OSRS plugin loaded");
        plugin.getOfferStampStateServices().migrateLegacyDevConfigIfNeeded();
        plugin.getProfileWorkflowService().loadProfileSelectionState();
        PluginState pluginState = PluginInjectorBridge.get(PluginState.class);
        PluginInjectorBridge.get(BookmarkStateService.class).clearCache();
        PluginInjectorBridge.get(BookmarkStateService.class).loadSelectedBookmarks(
            PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class).resolveSelectedProfileKey(),
            pluginState.getBookmarkedItems()
        );
        plugin.currentItemSort = StatsItemSort.fromName(plugin.config.itemSort());
        plugin.currentItemSortAscending = plugin.config.itemSortAscending();
        pluginState.getHiddenItems().clear();
        pluginState.getHiddenItems().addAll(
            pluginState.getHiddenItemConfigStore().parseItemIds(plugin.config.hiddenItems()));
        plugin.getOfferStampStateServices().resetForStartup();
        PluginInjectorBridge.get(ProfileStore.class);
        PluginInjectorBridge.get(LinkStatusService.class).refresh();
        if (plugin.client != null && plugin.client.getGameState() == GameState.LOGGED_IN) {
            plugin.getOfferStampStateServices().setLastLoginNow();
            PluginInjectorBridge.get(GeHistoryAutoSyncStateService.class).arm();
            plugin.getOfferStampStateServices().loadOfferUpdateTimesForCurrentAccount();
        }
        GeLifecyclePanelBootstrapService.UiState uiState = plugin.panelBootstrapService.initialize(
            plugin.itemManager,
            plugin.config,
            plugin.client,
            plugin.clientToolbar,
            plugin.overlayManager,
            plugin
        );
        plugin.panel = uiState.getPanel();
        plugin.navButton = uiState.getNavButton();
        plugin.offerTimerOverlay = uiState.getOfferTimerOverlay();

        // Both of these return early when the plugin has no panel, so they have to come after
        // the assignment above. Run before it, they did nothing and the profile menu stayed
        // empty until some later refresh happened to rebuild it.
        plugin.getProfileWorkflowService().updateProfileOptionsUI();
        plugin.getProfileWorkflowService().updateProfileHeader();

        plugin.getOfferStampStateServices().ensureDeviceId();
        GeLifecycleRuntimeSchedulerServices.RuntimeState runtimeState = plugin.runtimeSchedulerServices.start(
            plugin.httpClient,
            plugin.gson,
            () -> PluginInjectorBridge.get(UploadBackfillDispatchService.class),
            plugin::refreshPanelData,
            plugin::refreshStatsData,
            () -> PluginInjectorBridge.get(OfferPreviewRuntimeFacadeService.class),
            () -> plugin.clientThread,
            () -> PluginInjectorBridge.get(OfferPreviewItemResolver.class),
            () -> PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class),
            GeLifecyclePluginConstants.ACCOUNTWIDE_UPLOAD_INTERVAL_SECONDS,
            GeLifecyclePluginConstants.OFFER_POLL_INTERVAL_MS,
            () -> PluginInjectorBridge.get(WikiPriceService.class),
            plugin::startProfileWatcher,
            () -> PluginInjectorBridge.get(LinkAttemptService.class),
            () -> plugin.config
        );
        plugin.apiClient = runtimeState.getApiClient();
        plugin.scheduler = runtimeState.getScheduler();
        plugin.ioExecutor = runtimeState.getIoExecutor();
        // Start profile watcher after scheduler assignment; otherwise watcher startup can no-op.
        plugin.startProfileWatcher();

        // Merges every profile file from disk and writes one back. Far too much for the thread
        // drawing the client, and it needs the scheduler assigned just above to have somewhere
        // to run, so it is queued here rather than earlier in startUp.
        plugin.executeAsync(() ->
            plugin.getLocalTradesRuntimeService().ensureProfileLoaded(GeLifecyclePluginConstants.ACCOUNTWIDE_KEY));

        PanelRefreshCoordinator refreshCoordinator = PluginInjectorBridge.get(PanelRefreshCoordinator.class);
        if (refreshCoordinator != null) {
            // The coordinator is a singleton that survives a toggle, so it can still be holding
            // flags from the last shutdown.
            refreshCoordinator.resetForStartUp();
        }

        // Stays registered while the plugin runs; the listener itself reads the config toggle, so
        // turning decimal amounts off takes effect without re-registering.
        ChatboxDecimalInputListener decimalInputListener =
            PluginInjectorBridge.get(ChatboxDecimalInputListener.class);
        if (plugin.keyManager != null && decimalInputListener != null) {
            plugin.keyManager.registerKeyListener(decimalInputListener);
        }

        // Last, because it needs the scheduler and the panel that everything above assigns.
        // A player who ticks the plugin on while already in the game gets no login event, so
        // the login work is done here instead. It ends in a no-op at the login screen.
        plugin.invokeOnClientThread(() ->
            PluginInjectorBridge.get(GameStateChangedHandlerService.class).catchUpWithAnAlreadyRunningGame());
    }

    static void shutDown(GeLifecyclePlugin plugin) {
        ChatboxDecimalInputListener decimalInputListener =
            PluginInjectorBridge.get(ChatboxDecimalInputListener.class);
        if (plugin.keyManager != null && decimalInputListener != null) {
            plugin.keyManager.unregisterKeyListener(decimalInputListener);
        }
        if (plugin.navButton != null) {
            plugin.clientToolbar.removeNavigation(plugin.navButton);
        }
        if (plugin.offerTimerOverlay != null) {
            plugin.overlayManager.remove(plugin.offerTimerOverlay);
            plugin.offerTimerOverlay = null;
        }
        // Profile writes are queued to the IO pool now, so anything still unsaved has to be
        // written here before that pool goes away.
        plugin.getLocalTradesRuntimeService().flushUnsavedProfiles();
        if (plugin.panel != null) {
            // The panel itself is dropped below, but its two one-second timers and the global
            // wheel listener would keep hold of it and keep firing on a panel nobody can see.
            plugin.panel.dispose();
        }
        // The singletons outlive a plugin toggle, so what shutDown leaves behind is what the
        // next startUp inherits.
        PluginState lifecycleState = PluginInjectorBridge.get(PluginState.class);
        plugin.runtimeSchedulerServices.shutDown(
            plugin.apiClient,
            plugin.scheduler,
            plugin.ioExecutor,
            () -> plugin.clientThread,
            () -> PluginInjectorBridge.get(WikiPriceService.class),
            plugin::stopProfileWatcher,
            () -> PluginInjectorBridge.get(UploadBackfillDispatchService.class),
            () -> PluginInjectorBridge.get(UploadEventDispatchFacadeService.class),
            () -> plugin.config,
            () -> GeLifecyclePlugin.log,
            lifecycleState != null ? lifecycleState.getSnapshots() : null,
            () -> plugin.getOfferStampStateServices().persistOfferUpdateTimes(),
            lifecycleState != null ? lifecycleState.getOfferUpdateStamps() : null,
            () -> PluginInjectorBridge.get(RecentTradeDeduper.class),
            lifecycleState != null ? lifecycleState.getUploadState() : null
        );

        plugin.panel = null;
        plugin.navButton = null;
        plugin.scheduler = null;
        plugin.ioExecutor = null;
    }
}
