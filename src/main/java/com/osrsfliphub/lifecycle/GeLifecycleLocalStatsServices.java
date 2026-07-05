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

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.runelite.api.Client;

final class GeLifecycleLocalStatsServices {
    private final GeLifecycleLocalStatsRuntimeContext context;

    private LocalStatsCacheService localStatsCacheService;
    private LocalStatsSnapshotService localStatsSnapshotService;
    private LocalAccountSessionService localAccountSessionService;
    private LocalTradeAnalyticsService localTradeAnalyticsService;
    private LocalTradeSessionFacadeService localTradeSessionFacadeService;
    private LocalFlipHistoryService localFlipHistoryService;
    private AccountwideFlipHistoryService accountwideFlipHistoryService;
    private LocalAccountMergeService localAccountMergeService;
    private LocalTradeDeltaRecorder localTradeDeltaRecorder;

    GeLifecycleLocalStatsServices(
        long accountwideKey,
        long localLimitWindowMs,
        long localLimitFutureToleranceMs,
        long localEventBucketMs,
        long localTradesLoadRetryMs,
        Map<Long, LocalStatsCache> statsCacheByAccount,
        Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount,
        Map<Long, Long> localSessionStartByAccount,
        Object localStatsLock,
        Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier,
        Supplier<GeLifecycleItemServices> itemServicesSupplier,
        Supplier<AccountwideProfileKeyCollector> accountwideProfileKeyCollectorSupplier,
        Supplier<ProfileStorageFacadeService> profileStorageFacadeServiceSupplier,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier,
        Supplier<AccountwideStatsAggregator> accountwideStatsAggregatorSupplier,
        Supplier<GeLifecycleProfileWorkflowService> profileWorkflowServiceSupplier,
        Supplier<Client> clientSupplier,
        Supplier<StatsRange> currentStatsRangeSupplier,
        Supplier<StatsItemSort> currentStatsSortSupplier,
        Consumer<Runnable> invokeOnClientThreadAction,
        LongConsumerWithScheduler executeOnSchedulerAction,
        Runnable triggerStatsRefreshAction,
        Runnable triggerPanelRefreshAction
    ) {
        this.context = new GeLifecycleLocalStatsRuntimeContext(
            accountwideKey,
            localLimitWindowMs,
            localLimitFutureToleranceMs,
            localEventBucketMs,
            localTradesLoadRetryMs,
            statsCacheByAccount,
            localTradeDeltasByAccount,
            localSessionStartByAccount,
            localStatsLock,
            localTradesRuntimeServiceSupplier,
            itemServicesSupplier,
            accountwideProfileKeyCollectorSupplier,
            profileStorageFacadeServiceSupplier,
            profileSelectionPresentationFacadeServiceSupplier,
            accountwideStatsAggregatorSupplier,
            profileWorkflowServiceSupplier,
            clientSupplier,
            currentStatsRangeSupplier,
            currentStatsSortSupplier,
            invokeOnClientThreadAction,
            executeOnSchedulerAction,
            triggerStatsRefreshAction,
            triggerPanelRefreshAction
        );
    }

    LocalStatsCacheService getLocalStatsCacheService() {
        LocalStatsCacheService service = localStatsCacheService;
        if (service != null) {
            return service;
        }
        service = GeLifecycleLocalStatsCoreFactory.createLocalStatsCacheService(context);
        localStatsCacheService = service;
        return service;
    }

    LocalStatsSnapshotService getLocalStatsSnapshotService() {
        return PluginInjectorBridge.get(LocalStatsSnapshotService.class);
    }

    LocalAccountSessionService getLocalAccountSessionService() {
        return PluginInjectorBridge.get(LocalAccountSessionService.class);
    }

    LocalTradeAnalyticsService getLocalTradeAnalyticsService() {
        return PluginInjectorBridge.get(LocalTradeAnalyticsService.class);
    }

    LocalTradeSessionFacadeService getLocalTradeSessionFacadeService() {
        return PluginInjectorBridge.get(LocalTradeSessionFacadeService.class);
    }

    LocalStatsViewService getLocalStatsViewService() {
        return PluginInjectorBridge.get(LocalStatsViewService.class);
    }

    LocalTradesLoadCoordinator getLocalTradesLoadCoordinator() {
        return PluginInjectorBridge.get(LocalTradesLoadCoordinator.class);
    }

    LocalFlipHistoryService getLocalFlipHistoryService() {
        LocalFlipHistoryService service = localFlipHistoryService;
        if (service != null) {
            return service;
        }
        service = GeLifecycleLocalStatsHistoryFactory.createLocalFlipHistoryService();
        localFlipHistoryService = service;
        return service;
    }

    AccountwideFlipHistoryService getAccountwideFlipHistoryService() {
        return PluginInjectorBridge.get(AccountwideFlipHistoryService.class);
    }

    LocalAccountMergeService getLocalAccountMergeService() {
        LocalAccountMergeService service = localAccountMergeService;
        if (service != null) {
            return service;
        }
        service = GeLifecycleLocalStatsHistoryFactory.createLocalAccountMergeService();
        localAccountMergeService = service;
        return service;
    }

    LocalTradeDeltaRecorder getLocalTradeDeltaRecorder() {
        LocalTradeDeltaRecorder recorder = localTradeDeltaRecorder;
        if (recorder != null) {
            return recorder;
        }
        recorder = GeLifecycleLocalStatsHistoryFactory.createLocalTradeDeltaRecorder(
            context,
            this::getLocalAccountSessionService,
            this::getLocalTradesRuntimeService,
            this::getLocalTradeSessionFacadeService,
            this::getItemLookupService,
            this::getLocalStatsCacheService,
            this::triggerStatsRefresh,
            this::triggerPanelRefresh
        );
        localTradeDeltaRecorder = recorder;
        return recorder;
    }

    private GeLifecycleLocalTradesRuntimeService getLocalTradesRuntimeService() {
        return context.localTradesRuntimeServiceSupplier.get();
    }

    private GeLifecycleItemServices getItemServices() {
        return context.itemServicesSupplier.get();
    }

    private ItemLookupService getItemLookupService() {
        return getItemServices().getItemLookupService();
    }

    private AccountwideProfileKeyCollector getAccountwideProfileKeyCollector() {
        return context.accountwideProfileKeyCollectorSupplier.get();
    }

    private ProfileStorageFacadeService getProfileStorageFacadeService() {
        return context.profileStorageFacadeServiceSupplier.get();
    }

    private ProfileSelectionPresentationFacadeService getProfileSelectionPresentationFacadeService() {
        return context.profileSelectionPresentationFacadeServiceSupplier.get();
    }

    private AccountwideStatsAggregator getAccountwideStatsAggregator() {
        return context.accountwideStatsAggregatorSupplier.get();
    }

    private GeLifecycleProfileWorkflowService getProfileWorkflowService() {
        return context.profileWorkflowServiceSupplier.get();
    }

    private void triggerStatsRefresh() {
        if (context.triggerStatsRefreshAction != null) {
            context.triggerStatsRefreshAction.run();
        }
    }

    private void triggerPanelRefresh() {
        if (context.triggerPanelRefreshAction != null) {
            context.triggerPanelRefreshAction.run();
        }
    }
}
