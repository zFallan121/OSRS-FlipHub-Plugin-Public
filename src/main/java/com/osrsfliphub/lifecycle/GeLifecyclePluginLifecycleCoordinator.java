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
        PluginInjectorBridge.get(BookmarkStateService.class).clearCache();
        PluginInjectorBridge.get(BookmarkStateService.class).loadSelectedBookmarks(
            PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class).resolveSelectedProfileKey(),
            plugin.bookmarkedItems
        );
        plugin.hiddenItems.clear();
        plugin.hiddenItems.addAll(plugin.hiddenItemConfigStore.parseItemIds(plugin.config.hiddenItems()));
        plugin.getOfferStampStateServices().resetForStartup();
        PluginInjectorBridge.get(ProfileStore.class);
        PluginInjectorBridge.get(LegacyLocalTradesStore.class);
        plugin.getLocalTradesRuntimeService().ensureProfileLoaded(GeLifecyclePluginConstants.ACCOUNTWIDE_KEY);
        if (plugin.client != null && plugin.client.getGameState() == GameState.LOGGED_IN) {
            plugin.getOfferStampStateServices().setLastLoginNow();
            plugin.getEventManageHistoryServices().getGeHistoryAutoSyncStateService().arm();
            plugin.getOfferStampStateServices().loadOfferUpdateTimesForCurrentAccount();
        }
        GeLifecyclePanelBootstrapService.UiState uiState = plugin.panelBootstrapService.initialize(
            plugin.itemManager,
            plugin.config,
            plugin.configManager,
            plugin.client,
            plugin.clientToolbar,
            plugin.overlayManager,
            plugin,
            plugin.profileSelection,
            plugin.bookmarkedItems,
            plugin.hiddenItems,
            plugin.hiddenItemConfigStore,
            query -> plugin.currentQuery = query,
            page -> plugin.currentPage = page,
            enabled -> plugin.bookmarkFilterEnabled = enabled,
            range -> plugin.currentStatsRange = range,
            sort -> plugin.currentStatsSort = sort,
            plugin::refreshPanelData,
            plugin::refreshStatsData,
            plugin.getProfileWorkflowService()::persistProfileSelectionState,
            () -> PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class),
            () -> PluginInjectorBridge.get(BookmarkStateService.class),
            plugin.getLocalTradesRuntimeService()::ensureProfileLoaded,
            plugin.getProfileWorkflowService()::updateProfileOptionsUI,
            plugin.getProfileWorkflowService()::updateProfileHeader,
            () -> plugin.runtimeUtilityServices.triggerPanelRefresh(plugin.getPanelRefreshCoordinator(), plugin.scheduler),
            () -> plugin.runtimeUtilityServices.triggerStatsRefresh(plugin.getPanelRefreshCoordinator(), plugin.scheduler),
            () -> plugin.getEventManageHistoryServices().getManageDataDialogService().showManageDataDialog()
        );
        plugin.panel = uiState.getPanel();
        plugin.navButton = uiState.getNavButton();
        plugin.offerTimerOverlay = uiState.getOfferTimerOverlay();

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
            () -> plugin.getBackfillServices().getBackfillMarketServices().getWikiPriceService(),
            plugin::startProfileWatcher,
            () -> PluginInjectorBridge.get(LinkAttemptService.class),
            () -> plugin.config
        );
        plugin.apiClient = runtimeState.getApiClient();
        plugin.scheduler = runtimeState.getScheduler();
        plugin.ioExecutor = runtimeState.getIoExecutor();
        // Start profile watcher after scheduler assignment; otherwise watcher startup can no-op.
        plugin.startProfileWatcher();
    }

    static void shutDown(GeLifecyclePlugin plugin) {
        if (plugin.navButton != null) {
            plugin.clientToolbar.removeNavigation(plugin.navButton);
        }
        if (plugin.offerTimerOverlay != null) {
            plugin.overlayManager.remove(plugin.offerTimerOverlay);
            plugin.offerTimerOverlay = null;
        }
        plugin.runtimeSchedulerServices.shutDown(
            plugin.apiClient,
            plugin.scheduler,
            plugin.ioExecutor,
            () -> plugin.clientThread,
            () -> plugin.getBackfillServices().getBackfillMarketServices().getWikiPriceService(),
            plugin::stopProfileWatcher,
            () -> PluginInjectorBridge.get(UploadBackfillDispatchService.class),
            () -> PluginInjectorBridge.get(UploadEventDispatchFacadeService.class),
            () -> plugin.config,
            () -> GeLifecyclePlugin.log,
            plugin.snapshots,
            () -> plugin.getOfferStampStateServices().persistOfferUpdateTimes(),
            plugin.offerUpdateStamps,
            () -> plugin.getBackfillServices().getBackfillMarketServices().getRecentTradeDeduper(),
            plugin.uploadState
        );
    }
}
