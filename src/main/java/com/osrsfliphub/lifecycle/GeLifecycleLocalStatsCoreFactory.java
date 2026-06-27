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

import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class GeLifecycleLocalStatsCoreFactory {
    private GeLifecycleLocalStatsCoreFactory() {
    }

    static LocalStatsCacheService createLocalStatsCacheService(GeLifecycleLocalStatsRuntimeContext context) {
        return new LocalStatsCacheService(
            context.statsCacheByAccount,
            context.localTradeDeltasByAccount,
            context.localStatsLock
        );
    }

    static LocalStatsSnapshotService createLocalStatsSnapshotService(
        GeLifecycleLocalStatsRuntimeContext context,
        Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier,
        Supplier<LocalStatsCacheService> localStatsCacheServiceSupplier,
        Supplier<ItemLookupService> itemLookupServiceSupplier,
        Supplier<AccountwideProfileKeyCollector> accountwideProfileKeyCollectorSupplier,
        Supplier<ProfileStorageFacadeService> profileStorageFacadeServiceSupplier,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier,
        Supplier<AccountwideStatsAggregator> accountwideStatsAggregatorSupplier
    ) {
        return new LocalStatsSnapshotService(
            new LocalStatsSnapshotPluginHooks(
                context.accountwideKey,
                localTradesRuntimeServiceSupplier.get()::ensureProfileLoaded,
                localStatsCacheServiceSupplier,
                itemLookupServiceSupplier,
                accountwideProfileKeyCollectorSupplier,
                profileStorageFacadeServiceSupplier,
                context.localTradeDeltasByAccount,
                context.localStatsLock,
                profileSelectionPresentationFacadeServiceSupplier,
                accountwideStatsAggregatorSupplier
            )
        );
    }

    static LocalStatsViewService createLocalStatsViewService(
        Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier,
        Supplier<StatsRange> currentStatsRangeSupplier,
        Supplier<StatsItemSort> currentStatsSortSupplier,
        Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier,
        Supplier<LocalStatsSnapshotService> localStatsSnapshotServiceSupplier
    ) {
        return new LocalStatsViewService(
            new LocalStatsViewPluginHooks(
                () -> localTradesRuntimeServiceSupplier.get().ensureProfileLoaded(
                    profileSelectionPresentationFacadeServiceSupplier.get().resolveSelectedProfileKey()
                ),
                System::currentTimeMillis,
                currentStatsRangeSupplier,
                currentStatsSortSupplier,
                profileSelectionPresentationFacadeServiceSupplier,
                localTradeSessionFacadeServiceSupplier,
                localStatsSnapshotServiceSupplier
            )
        );
    }

    static LocalTradesLoadCoordinator createLocalTradesLoadCoordinator(
        GeLifecycleLocalStatsRuntimeContext context,
        Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier,
        Consumer<Runnable> invokeOnClientThreadConsumer,
        LocalTradesLoadCoordinatorPluginHooks.LongConsumerWithScheduler executeOnSchedulerConsumer,
        Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier
    ) {
        return new LocalTradesLoadCoordinator(
            context.localTradesLoadRetryMs,
            new LocalTradesLoadCoordinatorPluginHooks(
                System::currentTimeMillis,
                localTradeSessionFacadeServiceSupplier,
                invokeOnClientThreadConsumer,
                executeOnSchedulerConsumer,
                localTradesRuntimeServiceSupplier.get()::ensureProfileLoadedBoxed,
                localTradesRuntimeServiceSupplier.get()::markLocalTradesLoadedForLogin
            )
        );
    }
}
