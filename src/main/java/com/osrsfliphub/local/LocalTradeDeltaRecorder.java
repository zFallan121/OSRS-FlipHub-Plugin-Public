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

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class LocalTradeDeltaRecorder {
    private final long accountwideKey = GeLifecyclePluginConstants.ACCOUNTWIDE_KEY;

    @Inject
    LocalTradeDeltaRecorder() {
    }

    private void ensureLocalSessionStart(long accountKey, long tsClientMs) {
        LocalTradeSessionFacadeService service = PluginInjectorBridge.get(LocalTradeSessionFacadeService.class);
        if (service != null) {
            service.ensureLocalSessionStart(accountKey, tsClientMs);
        }
    }

    private void applyDeltaToStatsCache(long accountKey, LocalTradeDelta delta) {
        LocalStatsCacheService service = PluginInjectorBridge.get(LocalStatsCacheService.class);
        if (service != null) {
            service.applyDelta(accountKey, delta);
        }
    }

    boolean record(GeEvent event, boolean baselineSynthetic) {
        if (event == null) {
            return false;
        }

        boolean hasDelta = event.delta_qty > 0 || event.delta_gp > 0;
        if (!hasDelta && !"OFFER_COMPLETED".equals(event.event_type)) {
            return false;
        }
        if (baselineSynthetic) {
            return false;
        }

        LocalAccountSessionService sessionService = PluginInjectorBridge.get(LocalAccountSessionService.class);
        long accountKey = sessionService != null ? sessionService.resolveLocalAccountKey() : 0L;
        if (accountKey <= 0) {
            return false;
        }

        GeLifecycleLocalTradesRuntimeService tradesRuntime = PluginAccess.plugin().getLocalTradesRuntimeService();
        tradesRuntime.ensureProfileLoaded(accountKey);
        tradesRuntime.ensureProfileLoaded(accountwideKey);
        ensureLocalSessionStart(accountKey, event.ts_client_ms);
        ensureLocalSessionStart(accountwideKey, event.ts_client_ms);

        LocalTradeDelta delta = new LocalTradeDelta(
            event.ts_client_ms,
            event.slot,
            event.item_id,
            event.is_buy,
            event.delta_qty,
            event.delta_gp,
            event.event_type,
            event.price,
            baselineSynthetic
        );

        ItemLookupService itemLookup = PluginInjectorBridge.get(ItemLookupService.class);
        if (itemLookup != null) {
            itemLookup.cacheItemName(event.item_id);
        }
        tradesRuntime.appendTradeDeltaPair(accountKey, accountwideKey, delta);
        applyDeltaToStatsCache(accountKey, delta);
        if (accountwideKey != accountKey) {
            applyDeltaToStatsCache(accountwideKey, delta);
        }
        tradesRuntime.persistLocalTrades(accountKey);
        tradesRuntime.persistLocalTrades(accountwideKey);
        PanelRefreshCoordinator coordinator = PluginAccess.plugin().getPanelRefreshCoordinator();
        if (coordinator != null) {
            coordinator.triggerStatsRefresh(PluginAccess.plugin().scheduler);
            coordinator.triggerPanelRefresh(PluginAccess.plugin().scheduler);
        }
        return true;
    }
}
