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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class LocalTradeSessionFacadeService {
    interface Hooks {
        LocalAccountSessionService getLocalAccountSessionService();
        LocalTradeAnalyticsService getLocalTradeAnalyticsService();
        LocalFlipHistoryService getLocalFlipHistoryService();
        AccountwideFlipHistoryService getAccountwideFlipHistoryService();
        void ensureProfileLoaded(long accountKey);
    }

    private final long accountwideKey;
    private final Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount;
    private final Map<Long, Long> localSessionStartByAccount;
    private final Object localStatsLock;
    private final Hooks hooks;

    @Inject
    LocalTradeSessionFacadeService(PluginState state) {
        this(GeLifecyclePluginConstants.ACCOUNTWIDE_KEY,
            state.getLocalTradeDeltasByAccount(),
            state.getLocalSessionStartByAccount(),
            state.getLocalStatsLock(),
            new Hooks() {
                @Override
                public LocalAccountSessionService getLocalAccountSessionService() {
                    return PluginInjectorBridge.get(LocalAccountSessionService.class);
                }

                @Override
                public LocalTradeAnalyticsService getLocalTradeAnalyticsService() {
                    return PluginInjectorBridge.get(LocalTradeAnalyticsService.class);
                }

                @Override
                public LocalFlipHistoryService getLocalFlipHistoryService() {
                    return PluginInjectorBridge.get(LocalFlipHistoryService.class);
                }

                @Override
                public AccountwideFlipHistoryService getAccountwideFlipHistoryService() {
                    return PluginInjectorBridge.get(AccountwideFlipHistoryService.class);
                }

                @Override
                public void ensureProfileLoaded(long accountKey) {
                    PluginAccess.plugin().getLocalTradesRuntimeService().ensureProfileLoaded(accountKey);
                }
            });
    }

    LocalTradeSessionFacadeService(long accountwideKey,
                                   Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount,
                                   Map<Long, Long> localSessionStartByAccount,
                                   Object localStatsLock,
                                   Hooks hooks) {
        this.accountwideKey = accountwideKey;
        this.localTradeDeltasByAccount = localTradeDeltasByAccount;
        this.localSessionStartByAccount = localSessionStartByAccount;
        this.localStatsLock = localStatsLock;
        this.hooks = hooks;
    }

    long resolveAccountHash() {
        LocalAccountSessionService sessionService = hooks != null ? hooks.getLocalAccountSessionService() : null;
        return sessionService != null ? sessionService.resolveAccountHash() : -1L;
    }

    void updateLocalAccountSessionStart() {
        LocalAccountSessionService sessionService = hooks != null ? hooks.getLocalAccountSessionService() : null;
        if (sessionService == null) {
            return;
        }
        sessionService.updateLocalAccountSessionStart(localSessionStartByAccount, localStatsLock, accountwideKey);
    }

    void ensureLocalSessionStart(long accountKey, long nowMs) {
        LocalAccountSessionService sessionService = hooks != null ? hooks.getLocalAccountSessionService() : null;
        if (sessionService == null) {
            return;
        }
        sessionService.ensureLocalSessionStart(localSessionStartByAccount, localStatsLock, accountKey, nowMs);
    }

    long resolveStatsSessionStartMs(long accountKey, long nowMs) {
        LocalAccountSessionService sessionService = hooks != null ? hooks.getLocalAccountSessionService() : null;
        if (sessionService == null) {
            return nowMs;
        }
        return sessionService.resolveStatsSessionStartMs(localSessionStartByAccount, localStatsLock, accountKey, nowMs);
    }

    Map<Integer, LocalTradeInfo> buildLocalTradeInfo(long accountKey) {
        LocalTradeAnalyticsService analyticsService = hooks != null ? hooks.getLocalTradeAnalyticsService() : null;
        return analyticsService != null
            ? analyticsService.buildLocalTradeInfo(snapshotLocalTradeDeltas(accountKey))
            : java.util.Collections.emptyMap();
    }

    Map<Integer, LocalLimitInfo> buildLocalLimitInfo(long accountKey, long nowMs) {
        LocalTradeAnalyticsService analyticsService = hooks != null ? hooks.getLocalTradeAnalyticsService() : null;
        return analyticsService != null
            ? analyticsService.buildLocalLimitInfo(snapshotLocalTradeDeltas(accountKey), nowMs)
            : java.util.Collections.emptyMap();
    }

    boolean hasRecentLocalBuy(long accountKey, int itemId, long nowMs) {
        if (accountKey <= 0 || itemId <= 0) {
            return false;
        }
        LocalTradeAnalyticsService analyticsService = hooks != null ? hooks.getLocalTradeAnalyticsService() : null;
        return analyticsService != null && analyticsService.hasRecentLocalBuy(snapshotLocalTradeDeltas(accountKey), itemId, nowMs);
    }

    List<LocalTradeDelta> snapshotLocalTradeDeltas(long accountKey) {
        LocalTradeAnalyticsService analyticsService = hooks != null ? hooks.getLocalTradeAnalyticsService() : null;
        if (analyticsService == null) {
            return new ArrayList<>();
        }
        synchronized (localStatsLock) {
            List<LocalTradeDelta> deltas = localTradeDeltasByAccount != null ? localTradeDeltasByAccount.get(accountKey) : null;
            return analyticsService.copySnapshot(deltas);
        }
    }

    Map<Integer, List<StatsFlipInstance>> buildStatsFlipHistory(long accountKey, Long sinceMs) {
        if (accountKey == accountwideKey) {
            AccountwideFlipHistoryService accountwideService = hooks != null ? hooks.getAccountwideFlipHistoryService() : null;
            return accountwideService != null
                ? accountwideService.buildAccountwideHistory(sinceMs)
                : java.util.Collections.emptyMap();
        }
        if (hooks != null) {
            hooks.ensureProfileLoaded(accountKey);
        }
        LocalFlipHistoryService localHistoryService = hooks != null ? hooks.getLocalFlipHistoryService() : null;
        return localHistoryService != null
            ? localHistoryService.buildHistory(snapshotLocalTradeDeltas(accountKey), sinceMs)
            : java.util.Collections.emptyMap();
    }
}
