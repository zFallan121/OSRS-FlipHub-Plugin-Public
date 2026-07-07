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

final class GeLifecycleLocalStatsRuntimeContext {
    final long accountwideKey;
    final long localLimitWindowMs;
    final long localLimitFutureToleranceMs;
    final long localEventBucketMs;
    final long localTradesLoadRetryMs;
    final Map<Long, LocalStatsCache> statsCacheByAccount;
    final Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount;
    final Map<Long, Long> localSessionStartByAccount;
    final Object localStatsLock;
    final Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier;
    final Supplier<AccountwideProfileKeyCollector> accountwideProfileKeyCollectorSupplier;
    final Supplier<ProfileStorageFacadeService> profileStorageFacadeServiceSupplier;
    final Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier;
    final Supplier<AccountwideStatsAggregator> accountwideStatsAggregatorSupplier;
    final Supplier<GeLifecycleProfileWorkflowService> profileWorkflowServiceSupplier;
    final Supplier<Client> clientSupplier;
    final Supplier<StatsRange> currentStatsRangeSupplier;
    final Supplier<StatsItemSort> currentStatsSortSupplier;
    final Consumer<Runnable> invokeOnClientThreadAction;
    final LongConsumerWithScheduler executeOnSchedulerAction;
    final Runnable triggerStatsRefreshAction;
    final Runnable triggerPanelRefreshAction;

    GeLifecycleLocalStatsRuntimeContext(
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
        this.accountwideKey = accountwideKey;
        this.localLimitWindowMs = localLimitWindowMs;
        this.localLimitFutureToleranceMs = localLimitFutureToleranceMs;
        this.localEventBucketMs = localEventBucketMs;
        this.localTradesLoadRetryMs = localTradesLoadRetryMs;
        this.statsCacheByAccount = statsCacheByAccount;
        this.localTradeDeltasByAccount = localTradeDeltasByAccount;
        this.localSessionStartByAccount = localSessionStartByAccount;
        this.localStatsLock = localStatsLock;
        this.localTradesRuntimeServiceSupplier = localTradesRuntimeServiceSupplier;
        this.accountwideProfileKeyCollectorSupplier = accountwideProfileKeyCollectorSupplier;
        this.profileStorageFacadeServiceSupplier = profileStorageFacadeServiceSupplier;
        this.profileSelectionPresentationFacadeServiceSupplier = profileSelectionPresentationFacadeServiceSupplier;
        this.accountwideStatsAggregatorSupplier = accountwideStatsAggregatorSupplier;
        this.profileWorkflowServiceSupplier = profileWorkflowServiceSupplier;
        this.clientSupplier = clientSupplier;
        this.currentStatsRangeSupplier = currentStatsRangeSupplier;
        this.currentStatsSortSupplier = currentStatsSortSupplier;
        this.invokeOnClientThreadAction = invokeOnClientThreadAction;
        this.executeOnSchedulerAction = executeOnSchedulerAction;
        this.triggerStatsRefreshAction = triggerStatsRefreshAction;
        this.triggerPanelRefreshAction = triggerPanelRefreshAction;
    }
}
