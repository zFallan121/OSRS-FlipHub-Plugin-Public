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

import java.util.function.Supplier;

final class GeLifecycleLocalStatsHistoryFactory {
    private GeLifecycleLocalStatsHistoryFactory() {
    }

    static LocalFlipHistoryService createLocalFlipHistoryService() {
        return new LocalFlipHistoryService();
    }

    static LocalAccountMergeService createLocalAccountMergeService() {
        return new LocalAccountMergeService();
    }

    static LocalTradeDeltaRecorder createLocalTradeDeltaRecorder(
        GeLifecycleLocalStatsRuntimeContext context,
        Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier,
        Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier,
        Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier,
        Supplier<ItemLookupService> itemLookupServiceSupplier,
        Supplier<LocalStatsCacheService> localStatsCacheServiceSupplier,
        Runnable triggerStatsRefreshAction,
        Runnable triggerPanelRefreshAction
    ) {
        return new LocalTradeDeltaRecorder(
            context.accountwideKey,
            new LocalTradeDeltaRecorderPluginHooks(
                localAccountSessionServiceSupplier,
                localTradesRuntimeServiceSupplier.get()::ensureProfileLoaded,
                (accountKey, nowMs) -> localTradeSessionFacadeServiceSupplier.get().ensureLocalSessionStart(accountKey, nowMs),
                itemId -> itemLookupServiceSupplier.get().cacheItemName(itemId),
                localTradesRuntimeServiceSupplier.get()::appendTradeDeltaPair,
                (accountKey, delta) -> localStatsCacheServiceSupplier.get().applyDelta(accountKey, delta),
                localTradesRuntimeServiceSupplier.get()::persistLocalTrades,
                triggerStatsRefreshAction,
                triggerPanelRefreshAction
            )
        );
    }
}
