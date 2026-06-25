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
import java.util.function.Supplier;

final class GeLifecycleStatsTradesOps {
    private final Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier;
    private final Supplier<GeLifecycleItemServices> itemServicesSupplier;
    private final Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier;
    private final Supplier<LocalStatsCacheService> localStatsCacheServiceSupplier;
    private final Runnable markAccountwideUploadDirtyAction;
    private final Runnable scheduleRefreshSoonAction;
    private final Runnable triggerStatsRefreshAction;
    private final Runnable triggerPanelRefreshAction;

    GeLifecycleStatsTradesOps(
        Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier,
        Supplier<GeLifecycleItemServices> itemServicesSupplier,
        Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier,
        Supplier<LocalStatsCacheService> localStatsCacheServiceSupplier,
        Runnable markAccountwideUploadDirtyAction,
        Runnable scheduleRefreshSoonAction,
        Runnable triggerStatsRefreshAction,
        Runnable triggerPanelRefreshAction
    ) {
        this.localTradesRuntimeServiceSupplier = localTradesRuntimeServiceSupplier;
        this.itemServicesSupplier = itemServicesSupplier;
        this.localTradeSessionFacadeServiceSupplier = localTradeSessionFacadeServiceSupplier;
        this.localStatsCacheServiceSupplier = localStatsCacheServiceSupplier;
        this.markAccountwideUploadDirtyAction = markAccountwideUploadDirtyAction;
        this.scheduleRefreshSoonAction = scheduleRefreshSoonAction;
        this.triggerStatsRefreshAction = triggerStatsRefreshAction;
        this.triggerPanelRefreshAction = triggerPanelRefreshAction;
    }

    void ensureProfileLoaded(long accountKey) {
        GeLifecycleLocalTradesRuntimeService service = resolve(localTradesRuntimeServiceSupplier);
        if (service != null) {
            service.ensureProfileLoaded(accountKey);
        }
    }

    void ensureLocalSessionStart(long accountKey, long nowMs) {
        LocalTradeSessionFacadeService service = resolve(localTradeSessionFacadeServiceSupplier);
        if (service != null) {
            service.ensureLocalSessionStart(accountKey, nowMs);
        }
    }

    List<LocalTradeDelta> snapshotLocalTradeDeltas(long accountKey) {
        LocalTradeSessionFacadeService service = resolve(localTradeSessionFacadeServiceSupplier);
        return service != null ? service.snapshotLocalTradeDeltas(accountKey) : null;
    }

    void cacheItemName(int itemId) {
        ItemLookupService lookup = getItemLookupService();
        if (lookup != null) {
            lookup.cacheItemName(itemId);
        }
    }

    void appendTradeDeltaPair(long accountKey, long accountwideKey, LocalTradeDelta delta) {
        GeLifecycleLocalTradesRuntimeService service = resolve(localTradesRuntimeServiceSupplier);
        if (service != null) {
            service.appendTradeDeltaPair(accountKey, accountwideKey, delta);
        }
    }

    void applyDeltaToStatsCache(long accountKey, LocalTradeDelta delta) {
        LocalStatsCacheService cacheService = resolve(localStatsCacheServiceSupplier);
        if (cacheService != null) {
            cacheService.applyDelta(accountKey, delta);
        }
    }

    void persistLocalTrades(long accountKey) {
        GeLifecycleLocalTradesRuntimeService service = resolve(localTradesRuntimeServiceSupplier);
        if (service != null) {
            service.persistLocalTrades(accountKey);
        }
    }

    ItemLookupService getItemLookupService() {
        GeLifecycleItemServices services = resolve(itemServicesSupplier);
        return services != null ? services.getItemLookupService() : null;
    }

    void markAccountwideUploadDirty() {
        if (markAccountwideUploadDirtyAction != null) {
            markAccountwideUploadDirtyAction.run();
        }
    }

    void scheduleRefreshSoon() {
        if (scheduleRefreshSoonAction != null) {
            scheduleRefreshSoonAction.run();
        }
    }

    void triggerStatsRefresh() {
        if (triggerStatsRefreshAction != null) {
            triggerStatsRefreshAction.run();
        }
    }

    void triggerPanelRefresh() {
        if (triggerPanelRefreshAction != null) {
            triggerPanelRefreshAction.run();
        }
    }

    private <T> T resolve(Supplier<T> supplier) {
        return supplier != null ? supplier.get() : null;
    }
}
