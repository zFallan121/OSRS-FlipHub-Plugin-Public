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

final class GeLifecyclePluginRuntimeFactoryServices {
    private final GeLifecycleOfferUiRuntimeFactory offerUiRuntimeFactory = new GeLifecycleOfferUiRuntimeFactory();
    private final GeLifecycleUploadRuntimeFactory uploadRuntimeFactory = new GeLifecycleUploadRuntimeFactory();
    private final GeLifecyclePanelLocalRuntimeFactory panelLocalRuntimeFactory = new GeLifecyclePanelLocalRuntimeFactory();
    private final GeLifecycleProfileLinkWorkflowRuntimeFactory profileLinkWorkflowRuntimeFactory = new GeLifecycleProfileLinkWorkflowRuntimeFactory();
    private final GeLifecycleCoreFeatureRuntimeFactory coreFeatureRuntimeFactory = new GeLifecycleCoreFeatureRuntimeFactory();
    private final GeLifecyclePluginRuntimeFactoryContext context;

    GeLifecyclePluginRuntimeFactoryServices(GeLifecyclePluginRuntimeFactoryContext context) {
        this.context = context;
    }

    GeLifecycleOfferUiRuntimeServices createOfferUiRuntimeServices() {
        return offerUiRuntimeFactory.create(
            context.runtimeUtilityServices,
            context.configSupplier,
            context.configManagerSupplier,
            context.gson,
            context.clientSupplier,
            context.clientThreadSupplier,
            context.itemManagerSupplier,
            context.panelSupplier,
            context.schedulerSupplier,
            context.panelRefreshCoordinatorSupplier,
            context.statsTradesServicesSupplier,
            context.profileSelectionServicesSupplier,
            context.localTradesRuntimeServiceSupplier,
            context.backfillServicesSupplier,
            context.profileWorkflowServiceSupplier,
            context.eventManageHistoryServicesSupplier,
            context.panelDataRuntimeServiceSupplier,
            context.offerUpdateStamps,
            context.itemNameLookupCache,
            context.itemNameCache,
            context.hiddenItems,
            context.profileDisplayNames,
            context.bookmarkConfigStore,
            context.offerUpdateStampConfigStore,
            context.offerUpdateStampLegacyMatcher,
            context.offerPreviewItemIdSupplier,
            context.offerPreviewItemSupplier,
            context.offerPreviewItemIdSetter,
            context.offerPreviewItemSetter
        );
    }

    GeLifecycleUploadRuntimeServices createUploadRuntimeServices() {
        return uploadRuntimeFactory.create(
            context.uploadState,
            context.runtimeUtilityServices,
            context.maxPendingUploadEvents,
            context.maxBatchSize,
            context.backfillRetryIntervalSeconds,
            context.backfillRetryMaxIntervalSeconds,
            context.backfillMinIntervalMs,
            context.clientSupplier,
            context.panelSupplier,
            context.apiClientSupplier,
            context.configSupplier,
            context.configManagerSupplier,
            context.loggerSupplier,
            context.schedulerSupplier,
            context.backfillServicesSupplier,
            context.profileWorkflowServiceSupplier,
            context.profileSelectionServicesSupplier,
            context.executeIoConsumer
        );
    }

    GeLifecyclePanelLocalRuntimeServices createPanelLocalRuntimeServices() {
        return panelLocalRuntimeFactory.create(
            context.accountwideKey,
            context.maxLocalTrades,
            context.localEventBucketMs,
            context.duplicateTradeWindowMs,
            context.localStatsLock,
            context.localTradeDeltasByAccount,
            context.loadedProfiles,
            context.localTradesLoadState,
            context.itemServicesSupplier,
            context.currentQuerySupplier,
            context.bookmarkFilterEnabledSupplier,
            context.bookmarkedItemsSupplier,
            context.currentPageSupplier,
            context.panelSupplier,
            context.statsTradesServicesSupplier,
            context.offerPreviewRuntimeFacadeServiceSupplier,
            context.clientSupplier,
            context.offerUpdateStamps,
            context.schedulerSupplier,
            context.clientThreadSupplier,
            context.profileSelectionServicesSupplier,
            context.setLocalTradesLoadedThisLoginAction,
            context.backfillServicesSupplier,
            context.uploadBackfillDispatchServiceSupplier,
            context.profileWorkflowServiceSupplier
        );
    }

    GeLifecycleProfileLinkWorkflowRuntimeServices createProfileLinkWorkflowRuntimeServices() {
        return profileLinkWorkflowRuntimeFactory.create(
            context.sharedState,
            context.profileSelection,
            context.profileDisplayNames,
            context.legacyNameKeysByHash,
            context.loadedProfileFileMs,
            context.gson,
            context.configSupplier,
            context.configManagerSupplier,
            context.clientSupplier,
            context.apiClientSupplier,
            context.schedulerSupplier,
            context.panelSupplier,
            context.eventManageHistoryServicesSupplier,
            context.localTradesRuntimeServiceSupplier,
            context.statsTradesServicesSupplier,
            context.uploadEventDispatchFacadeServiceSupplier,
            context.uploadBackfillDispatchServiceSupplier,
            context.backfillServicesSupplier,
            context.bookmarkStateServiceSupplier,
            context.refreshPanelDataAction,
            context.executeIoConsumer,
            context.runtimeUtilityServices,
            context.loggerSupplier
        );
    }

    GeLifecycleCoreFeatureRuntimeServices createCoreFeatureRuntimeServices() {
        return coreFeatureRuntimeFactory.create(
            context.sharedState,
            context.hiddenItemConfigStore,
            context.gson,
            context.httpClient,
            context.runtimeUtilityServices,
            context.panelRefreshCoordinatorFactoryService,
            context.itemManagerSupplier,
            context.configSupplier,
            context.configManagerSupplier,
            context.clientSupplier,
            context.clientThreadSupplier,
            context.apiClientSupplier,
            context.panelSupplier,
            context.schedulerSupplier,
            context.ioExecutorSupplier,
            context.offerStampStateServicesSupplier,
            context.profileSelectionServicesSupplier,
            context.linkServicesSupplier,
            context.localTradesRuntimeServiceSupplier,
            context.panelDataRuntimeServiceSupplier,
            context.itemServicesSupplier,
            context.bookmarkStateServiceSupplier,
            context.offerEventBuildServiceSupplier,
            context.profileWorkflowServiceSupplier,
            context.legacyLocalTradesFilterServiceSupplier,
            context.uploadEventDispatchFacadeServiceSupplier,
            context.uploadBackfillDispatchServiceSupplier,
            context.currentStatsRangeSupplier,
            context.currentStatsSortSupplier,
            context.panelVisibleSupplier,
            context.panelVisibleSetter,
            context.localTradesLoadedThisLoginSupplier,
            context.resetLocalTradesLoadStateAction,
            context.markAccountwideUploadDirtyAction,
            context.invokeOnClientThreadConsumer,
            context.executeOnSchedulerConsumer,
            context.executeAsyncConsumer,
            context.wipeStatsInvoker,
            context.profileFileModifiedMsFn,
            context.showManageDataErrorConsumer,
            context.loggerSupplier
        );
    }
}
