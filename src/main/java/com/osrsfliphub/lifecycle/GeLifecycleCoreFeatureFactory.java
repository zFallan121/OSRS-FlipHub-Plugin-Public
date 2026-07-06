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

import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;

final class GeLifecycleCoreFeatureFactory {
    private GeLifecycleCoreFeatureFactory() {
    }

    static GeLifecycleEventManageHistoryServices createEventManageHistoryServices(
        GeLifecycleCoreFeatureRuntimeContext context,
        Supplier<PanelRefreshCoordinator> panelRefreshCoordinatorSupplier,
        Supplier<GeLifecycleProfileWorkflowService> profileWorkflowServiceSupplier,
        Supplier<GeLifecycleOfferStampStateServices> offerStampStateServicesSupplier,
        Supplier<GeLifecycleBackfillServices> backfillServicesSupplier,
        Supplier<GeLifecycleStatsTradesServices> statsTradesServicesSupplier,
        Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier,
        Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier
    ) {
        return new GeLifecycleEventManageHistoryServices(
            context.sharedState,
            context.hiddenItemConfigStore,
            context.gson,
            context.configSupplier,
            context.configManagerSupplier,
            context.clientSupplier,
            context.panelSupplier,
            context.schedulerSupplier,
            context.ioExecutorSupplier,
            context.invokeOnClientThreadConsumer,
            context.wipeStatsInvoker,
            message -> context.runtimeUtilityServices.pushGameMessage(context.clientSupplier.get(), message),
            context.showManageDataErrorConsumer,
            () -> context.runtimeUtilityServices.triggerStatsRefresh(
                panelRefreshCoordinatorSupplier.get(),
                context.schedulerSupplier.get()
            ),
            () -> context.runtimeUtilityServices.triggerPanelRefresh(
                panelRefreshCoordinatorSupplier.get(),
                context.schedulerSupplier.get()
            ),
            () -> profileWorkflowServiceSupplier.get().updateProfileOptionsUI(),
            () -> profileWorkflowServiceSupplier.get().updateProfileHeader(),
            () -> profileWorkflowServiceSupplier.get().updateProfileForLogin(),
            () -> profileWorkflowServiceSupplier.get().primeOfferSnapshots(),
            () -> offerStampStateServicesSupplier.get().persistOfferUpdateTimes(),
            () -> offerStampStateServicesSupplier.get().resetOfferUpdateStampsOnLogout(),
            () -> context.sharedState.getSnapshots().clear(),
            () -> offerStampStateServicesSupplier.get().setLastLoginNow(),
            () -> offerStampStateServicesSupplier.get().loadOfferUpdateTimesForCurrentAccount(),
            context.resetLocalTradesLoadStateAction,
            () -> localTradesRuntimeServiceSupplier.get().scheduleLocalTradesLoad(),
            () -> profileWorkflowServiceSupplier.get().persistProfileSelectionState(),
            () -> context.runtimeUtilityServices.isPanelVisible(context.panelSupplier.get()),
            context.panelVisibleSetter,
            context.localTradesLoadedThisLoginSupplier,
            () -> offerStampStateServicesSupplier.get().getLastLoginMs(),
            () -> PluginInjectorBridge.get(LinkAttemptService.class),
            () -> PluginInjectorBridge.get(LinkSessionConfigStore.class),
            () -> backfillServicesSupplier.get().getAccountwideSummaryUploader(),
            context.uploadEventDispatchFacadeServiceSupplier,
            context.uploadBackfillDispatchServiceSupplier,
            context.bookmarkStateServiceSupplier,
            () -> PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class),
            () -> backfillServicesSupplier.get().getBackfillMarketServices().getRecentTradeDeduper(),
            () -> statsTradesServicesSupplier.get().getLocalTradeSessionFacadeService(),
            localTradesRuntimeServiceSupplier,
            () -> statsTradesServicesSupplier.get().getLocalAccountSessionService(),
            () -> PluginInjectorBridge.get(ProfileStorageFacadeService.class),
            () -> PluginInjectorBridge.get(LegacyLocalTradesStore.class),
            () -> statsTradesServicesSupplier.get().getLocalStatsCacheService(),
            () -> backfillServicesSupplier.get().getBackfillMarketServices().getWikiPriceService(),
            () -> statsTradesServicesSupplier.get().getGeHistoryAutoSyncService(),
            context.offerEventBuildServiceSupplier,
            context.loggerSupplier,
            (event, baselineSynthetic) -> statsTradesServicesSupplier.get().getLocalTradeDeltaRecorder()
                .record(event, baselineSynthetic),
            event -> backfillServicesSupplier.get().getBackfillMarketServices().getRecentTradeDeduper()
                .normalizeOrSuppress(event),
            slot -> backfillServicesSupplier.get().getBackfillMarketServices().getRecentTradeDeduper()
                .clearSlot(slot),
            () -> context.runtimeUtilityServices.scheduleRefreshSoon(
                panelRefreshCoordinatorSupplier.get(),
                context.schedulerSupplier.get()
            ),
            (delaySeconds, forceRefresh) -> uploadBackfillDispatchServiceSupplier.get().requestBackfillAttempt(
                context.schedulerSupplier.get(),
                delaySeconds,
                forceRefresh
            ),
            context.executeAsyncConsumer,
            accountHash -> localTradesRuntimeServiceSupplier.get().loadLocalTradesAsync(accountHash),
            (slot, previous, next) -> offerStampStateServicesSupplier.get().trackOfferUpdate(slot, previous, next),
            () -> statsTradesServicesSupplier.get().getLocalTradeSessionFacadeService().resolveAccountHash(),
            accountKey -> localTradesRuntimeServiceSupplier.get().ensureLocalTradesLoaded(accountKey)
        );
    }

    static GeLifecycleStatsTradesServices createStatsTradesServices(
        GeLifecycleCoreFeatureRuntimeContext context,
        Supplier<GeLifecycleBackfillServices> backfillServicesSupplier,
        Supplier<PanelRefreshCoordinator> panelRefreshCoordinatorSupplier,
        Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier,
        Supplier<GeLifecycleProfileWorkflowService> profileWorkflowServiceSupplier
    ) {
        return new GeLifecycleStatsTradesServices(
            context.gson,
            context.sharedState,
            localTradesRuntimeServiceSupplier,
            context.itemServicesSupplier,
            () -> PluginInjectorBridge.get(ProfileStorageFacadeService.class),
            () -> PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class),
            () -> PluginInjectorBridge.get(AccountwideStatsAggregator.class),
            profileWorkflowServiceSupplier,
            context.clientSupplier,
            context.currentStatsRangeSupplier,
            context.currentStatsSortSupplier,
            context.invokeOnClientThreadConsumer,
            context.executeOnSchedulerConsumer,
            () -> context.runtimeUtilityServices.triggerStatsRefresh(
                panelRefreshCoordinatorSupplier.get(),
                context.schedulerSupplier.get()
            ),
            () -> context.runtimeUtilityServices.triggerPanelRefresh(
                panelRefreshCoordinatorSupplier.get(),
                context.schedulerSupplier.get()
            ),
            () -> backfillServicesSupplier.get().getBackfillMarketServices().getBackfillUploader(),
            context.uploadEventDispatchFacadeServiceSupplier,
            context.uploadBackfillDispatchServiceSupplier,
            context.legacyLocalTradesFilterServiceSupplier,
            () -> PluginInjectorBridge.get(LegacyLocalTradesStore.class),
            context.markAccountwideUploadDirtyAction,
            () -> context.runtimeUtilityServices.scheduleRefreshSoon(
                panelRefreshCoordinatorSupplier.get(),
                context.schedulerSupplier.get()
            ),
            context.profileFileModifiedMsFn,
            context.configManagerSupplier
        );
    }

    static PanelRefreshCoordinator createPanelRefreshCoordinator(
        GeLifecycleCoreFeatureRuntimeContext context,
        Supplier<GeLifecycleProfileWorkflowService> profileWorkflowServiceSupplier
    ) {
        return context.panelRefreshCoordinatorFactoryService.create(
            new PanelRefreshCoordinatorPluginHooks(
                () -> context.runtimeUtilityServices.isClientFullyReady(context.clientSupplier.get()),
                () -> context.runtimeUtilityServices.isPanelVisible(context.panelSupplier.get()),
                context.panelSupplier,
                () -> profileWorkflowServiceSupplier.get().ensureSelectedProfileLoaded(),
                () -> profileWorkflowServiceSupplier.get().updateProfileHeader(),
                context.clientThreadSupplier,
                () -> context.panelDataRuntimeServiceSupplier.get().updateLocalItemsPanel(),
                () -> context.panelDataRuntimeServiceSupplier.get().renderLocalStats(),
                context.executeAsyncConsumer,
                context.loggerSupplier
            )
        );
    }

    static GeLifecycleBackfillServices createBackfillServices(
        GeLifecycleCoreFeatureRuntimeContext context,
        Supplier<GeLifecycleStatsTradesServices> statsTradesServicesSupplier,
        Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier,
        Supplier<PanelRefreshCoordinator> panelRefreshCoordinatorSupplier,
        Function<String, ApiClient.StatsSummaryResponse> fetchRemoteStatsSummaryByToken
    ) {
        return new GeLifecycleBackfillServices(
            context.apiClientSupplier,
            context.configSupplier,
            context.configManagerSupplier,
            context.uploadBackfillDispatchServiceSupplier,
            context.uploadEventDispatchFacadeServiceSupplier,
            () -> statsTradesServicesSupplier.get().getAccountwideProfileKeyCollector(),
            () -> PluginInjectorBridge.get(ProfileStorageFacadeService.class),
            context.sharedState,
            () -> PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class),
            () -> statsTradesServicesSupplier.get().getBackfilledProfilesStore(),
            accountKey -> localTradesRuntimeServiceSupplier.get().ensureProfileLoaded(accountKey),
            () -> statsTradesServicesSupplier.get().getLocalStatsSnapshotService(),
            fetchRemoteStatsSummaryByToken::apply,
            () -> context.runtimeUtilityServices.triggerStatsRefresh(
                panelRefreshCoordinatorSupplier.get(),
                context.schedulerSupplier.get()
            ),
            () -> context.runtimeUtilityServices.triggerPanelRefresh(
                panelRefreshCoordinatorSupplier.get(),
                context.schedulerSupplier.get()
            ),
            () -> statsTradesServicesSupplier.get().getLocalTradeSessionFacadeService(),
            context.clientSupplier,
            context.clientThreadSupplier,
            () -> context.itemServicesSupplier.get().getItemLookupService(),
            () -> context.runtimeUtilityServices.scheduleRefreshSoon(
                panelRefreshCoordinatorSupplier.get(),
                context.schedulerSupplier.get()
            ),
            context.panelVisibleSupplier,
            context.loggerSupplier,
            () -> {
                Logger logger = context.loggerSupplier.get();
                return logger != null && logger.isDebugEnabled();
            },
            context.httpClient,
            context.gson,
            () -> context.runtimeUtilityServices.isClientFullyReady(context.clientSupplier.get()),
            context.hasItemManager
        );
    }
}
