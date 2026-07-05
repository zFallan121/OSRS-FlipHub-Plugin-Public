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
    interface Hooks {
        long resolveLocalAccountKey();
        void ensureProfileLoaded(long accountKey);
        void ensureLocalSessionStart(long accountKey, long tsClientMs);
        void cacheItemName(int itemId);
        void appendTradeDeltaPair(long accountKey, long accountwideKey, LocalTradeDelta delta);
        void applyDeltaToStatsCache(long accountKey, LocalTradeDelta delta);
        void persistLocalTrades(long accountKey);
        void triggerStatsRefresh();
        void triggerPanelRefresh();
    }

    private final long accountwideKey;
    private final Hooks hooks;

    @Inject
    LocalTradeDeltaRecorder() {
        this(GeLifecyclePluginConstants.ACCOUNTWIDE_KEY, new Hooks() {
            @Override
            public long resolveLocalAccountKey() {
                LocalAccountSessionService service = PluginInjectorBridge.get(LocalAccountSessionService.class);
                return service != null ? service.resolveLocalAccountKey() : 0L;
            }

            @Override
            public void ensureProfileLoaded(long accountKey) {
                PluginAccess.plugin().getLocalTradesRuntimeService().ensureProfileLoaded(accountKey);
            }

            @Override
            public void ensureLocalSessionStart(long accountKey, long tsClientMs) {
                LocalTradeSessionFacadeService service =
                    PluginInjectorBridge.get(LocalTradeSessionFacadeService.class);
                if (service != null) {
                    service.ensureLocalSessionStart(accountKey, tsClientMs);
                }
            }

            @Override
            public void cacheItemName(int itemId) {
                ItemLookupService service = PluginInjectorBridge.get(ItemLookupService.class);
                if (service != null) {
                    service.cacheItemName(itemId);
                }
            }

            @Override
            public void appendTradeDeltaPair(long accountKey, long accountwideKey, LocalTradeDelta delta) {
                PluginAccess.plugin().getLocalTradesRuntimeService()
                    .appendTradeDeltaPair(accountKey, accountwideKey, delta);
            }

            @Override
            public void applyDeltaToStatsCache(long accountKey, LocalTradeDelta delta) {
                LocalStatsCacheService service =
                    PluginAccess.plugin().getStatsTradesServices().getLocalStatsCacheService();
                if (service != null) {
                    service.applyDelta(accountKey, delta);
                }
            }

            @Override
            public void persistLocalTrades(long accountKey) {
                PluginAccess.plugin().getLocalTradesRuntimeService().persistLocalTrades(accountKey);
            }

            @Override
            public void triggerStatsRefresh() {
                PanelRefreshCoordinator coordinator = PluginAccess.plugin().getPanelRefreshCoordinator();
                if (coordinator != null) {
                    coordinator.triggerStatsRefresh(PluginAccess.plugin().scheduler);
                }
            }

            @Override
            public void triggerPanelRefresh() {
                PanelRefreshCoordinator coordinator = PluginAccess.plugin().getPanelRefreshCoordinator();
                if (coordinator != null) {
                    coordinator.triggerPanelRefresh(PluginAccess.plugin().scheduler);
                }
            }
        });
    }

    LocalTradeDeltaRecorder(long accountwideKey, Hooks hooks) {
        this.accountwideKey = accountwideKey;
        this.hooks = hooks;
    }

    boolean record(GeEvent event, boolean baselineSynthetic) {
        if (hooks == null || event == null) {
            return false;
        }

        boolean hasDelta = event.delta_qty > 0 || event.delta_gp > 0;
        if (!hasDelta && !"OFFER_COMPLETED".equals(event.event_type)) {
            return false;
        }
        if (baselineSynthetic) {
            return false;
        }

        long accountKey = hooks.resolveLocalAccountKey();
        if (accountKey <= 0) {
            return false;
        }

        hooks.ensureProfileLoaded(accountKey);
        hooks.ensureProfileLoaded(accountwideKey);
        hooks.ensureLocalSessionStart(accountKey, event.ts_client_ms);
        hooks.ensureLocalSessionStart(accountwideKey, event.ts_client_ms);

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

        hooks.cacheItemName(event.item_id);
        hooks.appendTradeDeltaPair(accountKey, accountwideKey, delta);
        hooks.applyDeltaToStatsCache(accountKey, delta);
        if (accountwideKey != accountKey) {
            hooks.applyDeltaToStatsCache(accountwideKey, delta);
        }
        hooks.persistLocalTrades(accountKey);
        hooks.persistLocalTrades(accountwideKey);
        hooks.triggerStatsRefresh();
        hooks.triggerPanelRefresh();
        return true;
    }
}
