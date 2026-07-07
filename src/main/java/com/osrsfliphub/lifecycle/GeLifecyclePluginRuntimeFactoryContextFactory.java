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

final class GeLifecyclePluginRuntimeFactoryContextFactory {
    private GeLifecyclePluginRuntimeFactoryContextFactory() {
    }

    static GeLifecyclePluginRuntimeFactoryServices getOrCreate(GeLifecyclePlugin plugin) {
        GeLifecyclePluginRuntimeFactoryServices services = plugin.runtimeFactoryServices;
        if (services != null) {
            return services;
        }
        services = new GeLifecyclePluginRuntimeFactoryServices(new GeLifecyclePluginRuntimeFactoryContext(
            plugin.sharedState,
            plugin.hiddenItemConfigStore,
            plugin.bookmarkConfigStore,
            plugin.offerUpdateStampConfigStore,
            plugin.offerUpdateStampLegacyMatcher,
            plugin.uploadState,
            plugin.runtimeUtilityServices,
            plugin.gson,
            plugin.httpClient,
            ACCOUNTWIDE_KEY,
            MAX_LOCAL_TRADES,
            LOCAL_EVENT_BUCKET_MS,
            DUPLICATE_TRADE_WINDOW_MS,
            MAX_PENDING_UPLOAD_EVENTS,
            MAX_BATCH_SIZE,
            BACKFILL_RETRY_INTERVAL_SECONDS,
            BACKFILL_RETRY_MAX_INTERVAL_SECONDS,
            BACKFILL_MIN_INTERVAL_MS,
            plugin.offerUpdateStamps,
            plugin.itemNameLookupCache,
            plugin.itemNameCache,
            plugin.hiddenItems,
            plugin.profileDisplayNames,
            plugin.profileSelection,
            plugin.legacyNameKeysByHash,
            plugin.loadedProfileFileMs,
            plugin.localStatsLock,
            plugin.localTradeDeltasByAccount,
            plugin.loadedProfiles,
            plugin.localTradesLoadState,
            () -> plugin.config,
            () -> plugin.configManager,
            () -> plugin.client,
            () -> plugin.clientThread,
            () -> plugin.itemManager,
            () -> plugin.panel,
            () -> plugin.scheduler,
            () -> plugin.ioExecutor,
            () -> plugin.apiClient,
            () -> GeLifecyclePlugin.log,
            plugin::getPanelRefreshCoordinator,
            plugin::getLocalTradesRuntimeService,
            plugin::getBackfillServices,
            plugin::getProfileWorkflowService,
            plugin::getEventManageHistoryServices,
            () -> PluginInjectorBridge.get(GeLifecyclePanelDataRuntimeService.class),
            () -> PluginInjectorBridge.get(OfferPreviewRuntimeFacadeService.class),
            plugin::getOfferStampStateServices,
            () -> PluginInjectorBridge.get(BookmarkStateService.class),
            () -> PluginInjectorBridge.get(OfferEventBuildService.class),
            plugin::getLegacyLocalTradesFilterService,
            () -> PluginInjectorBridge.get(UploadEventDispatchFacadeService.class),
            () -> PluginInjectorBridge.get(UploadBackfillDispatchService.class),
            () -> plugin.offerPreviewItemId,
            () -> plugin.offerPreviewItem,
            itemId -> plugin.offerPreviewItemId = itemId,
            item -> plugin.offerPreviewItem = item,
            () -> plugin.currentQuery,
            () -> plugin.bookmarkFilterEnabled,
            () -> plugin.bookmarkedItems,
            () -> plugin.currentPage,
            () -> plugin.currentStatsRange,
            () -> plugin.currentStatsSort,
            () -> plugin.panelVisible,
            visible -> plugin.panelVisible = visible,
            () -> plugin.localTradesLoadedThisLogin,
            () -> {
                plugin.localTradesLoadedThisLogin = false;
                plugin.localTradesLoadState.setLastAttemptMs(0L);
            },
            plugin::markAccountwideUploadDirty,
            () -> plugin.localTradesLoadedThisLogin = true,
            plugin::invokeOnClientThread,
            plugin::executeOnScheduler,
            plugin::executeAsync,
            plugin::executeIo,
            plugin::getProfileFileModifiedMs,
            message -> plugin.getProfileWorkflowService().showManageDataError(message),
            plugin::refreshPanelData
        ));
        plugin.runtimeFactoryServices = services;
        return services;
    }
}
