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
import java.util.concurrent.ScheduledExecutorService;
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
    private LocalStatsViewService localStatsViewService;
    private LocalTradesLoadCoordinator localTradesLoadCoordinator;
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
        LocalTradesLoadCoordinatorPluginHooks.LongConsumerWithScheduler executeOnSchedulerAction,
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
        LocalStatsSnapshotService service = localStatsSnapshotService;
        if (service != null) {
            return service;
        }
        service = GeLifecycleLocalStatsCoreFactory.createLocalStatsSnapshotService(
            context,
            this::getLocalTradesRuntimeService,
            this::getLocalStatsCacheService,
            this::getItemLookupService,
            this::getAccountwideProfileKeyCollector,
            this::getProfileStorageFacadeService,
            this::getProfileSelectionPresentationFacadeService,
            this::getAccountwideStatsAggregator
        );
        localStatsSnapshotService = service;
        return service;
    }

    LocalAccountSessionService getLocalAccountSessionService() {
        LocalAccountSessionService service = localAccountSessionService;
        if (service != null) {
            return service;
        }
        service = GeLifecycleLocalStatsCoreFactory.createLocalAccountSessionService(
            context,
            this::getProfileWorkflowService
        );
        localAccountSessionService = service;
        return service;
    }

    LocalTradeAnalyticsService getLocalTradeAnalyticsService() {
        LocalTradeAnalyticsService service = localTradeAnalyticsService;
        if (service != null) {
            return service;
        }
        service = GeLifecycleLocalStatsCoreFactory.createLocalTradeAnalyticsService(context);
        localTradeAnalyticsService = service;
        return service;
    }

    LocalTradeSessionFacadeService getLocalTradeSessionFacadeService() {
        LocalTradeSessionFacadeService service = localTradeSessionFacadeService;
        if (service != null) {
            return service;
        }
        service = GeLifecycleLocalStatsCoreFactory.createLocalTradeSessionFacadeService(
            context,
            this::getLocalAccountSessionService,
            this::getLocalTradeAnalyticsService,
            this::getLocalFlipHistoryService,
            this::getAccountwideFlipHistoryService,
            this::getLocalTradesRuntimeService
        );
        localTradeSessionFacadeService = service;
        return service;
    }

    LocalStatsViewService getLocalStatsViewService() {
        LocalStatsViewService service = localStatsViewService;
        if (service != null) {
            return service;
        }
        service = GeLifecycleLocalStatsCoreFactory.createLocalStatsViewService(
            this::getLocalTradesRuntimeService,
            this::getProfileSelectionPresentationFacadeService,
            this::getCurrentStatsRange,
            this::getCurrentStatsSort,
            this::getLocalTradeSessionFacadeService,
            this::getLocalStatsSnapshotService
        );
        localStatsViewService = service;
        return service;
    }

    LocalTradesLoadCoordinator getLocalTradesLoadCoordinator() {
        LocalTradesLoadCoordinator coordinator = localTradesLoadCoordinator;
        if (coordinator != null) {
            return coordinator;
        }
        coordinator = GeLifecycleLocalStatsCoreFactory.createLocalTradesLoadCoordinator(
            context,
            this::getLocalTradeSessionFacadeService,
            this::invokeOnClientThread,
            this::executeOnScheduler,
            this::getLocalTradesRuntimeService
        );
        localTradesLoadCoordinator = coordinator;
        return coordinator;
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

    private StatsRange getCurrentStatsRange() {
        return context.currentStatsRangeSupplier != null ? context.currentStatsRangeSupplier.get() : StatsRange.SESSION;
    }

    private StatsItemSort getCurrentStatsSort() {
        return context.currentStatsSortSupplier != null ? context.currentStatsSortSupplier.get() : StatsItemSort.COMPLETION;
    }

    private void invokeOnClientThread(Runnable task) {
        if (context.invokeOnClientThreadAction != null) {
            context.invokeOnClientThreadAction.accept(task);
        }
    }

    private void executeOnScheduler(ScheduledExecutorService scheduler, Runnable task) {
        if (context.executeOnSchedulerAction != null) {
            context.executeOnSchedulerAction.schedule(scheduler, task);
        }
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
