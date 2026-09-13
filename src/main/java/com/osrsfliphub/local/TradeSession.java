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

import java.util.*;
import javax.inject.*;

@Singleton
final class TradeSession {
    private final long accountwideKey = Const.ACCOUNTWIDE_KEY;
    private final Map<Long, List<Delta>> localTradeDeltasByAccount;
    private final Map<Long, Long> localSessionStartByAccount;
    private final Object localStatsLock;

    @Inject
    TradeSession(PluginState state) {
        this.localTradeDeltasByAccount = state.getLocalTradeDeltasByAccount();
        this.localSessionStartByAccount = state.getLocalSessionStartByAccount();
        this.localStatsLock = state.getLocalStatsLock();
    }

    long resolveAccountHash() {
        AccountSession sessionService = Bridge.get(AccountSession.class);
        return sessionService != null ? sessionService.resolveAccountHash() : -1L;
    }

    void updateLocalAccountSessionStart() {
        AccountSession sessionService = Bridge.get(AccountSession.class);
        if (sessionService == null) {
            return;
        }
        sessionService.updateLocalAccountSessionStart(localSessionStartByAccount, localStatsLock, accountwideKey);
    }

    void clearLocalAccountSessionStarts() {
        AccountSession sessionService = Bridge.get(AccountSession.class);
        if (sessionService == null) {
            return;
        }
        sessionService.clearLocalAccountSessionStarts(localSessionStartByAccount, localStatsLock);
    }

    void ensureLocalSessionStart(long accountKey, long nowMs) {
        AccountSession sessionService = Bridge.get(AccountSession.class);
        if (sessionService == null) {
            return;
        }
        sessionService.ensureLocalSessionStart(localSessionStartByAccount, localStatsLock, accountKey, nowMs);
    }

    long resolveStatsSessionStartMs(long accountKey, long nowMs) {
        AccountSession sessionService = Bridge.get(AccountSession.class);
        if (sessionService == null) {
            return nowMs;
        }
        return sessionService.resolveStatsSessionStartMs(localSessionStartByAccount, localStatsLock, accountKey, nowMs);
    }

    Map<Integer, TradeInfo> buildLocalTradeInfo(long accountKey) {
        TradeAnalytics analyticsService = Bridge.get(TradeAnalytics.class);
        return analyticsService != null
            ? analyticsService.buildLocalTradeInfo(snapshotLocalTradeDeltas(accountKey))
            : java.util.Collections.emptyMap();
    }

    Map<Integer, LimitInfo> buildLocalLimitInfo(long accountKey, long nowMs) {
        TradeAnalytics analyticsService = Bridge.get(TradeAnalytics.class);
        return analyticsService != null
            ? analyticsService.buildLocalLimitInfo(snapshotLocalTradeDeltas(accountKey), nowMs)
            : java.util.Collections.emptyMap();
    }

    List<Delta> snapshotLocalTradeDeltas(long accountKey) {
        TradeAnalytics analyticsService = Bridge.get(TradeAnalytics.class);
        if (analyticsService == null) {
            return new ArrayList<>();
        }
        synchronized (localStatsLock) {
            List<Delta> deltas = localTradeDeltasByAccount != null ? localTradeDeltasByAccount.get(accountKey) : null;
            return analyticsService.copySnapshot(deltas);
        }
    }

    Map<Integer, List<StatsFlipInstance>> buildStatsFlipHistory(long accountKey, Long sinceMs) {
        if (accountKey == accountwideKey) {
            FlipHistory accountwideService = Bridge.get(FlipHistory.class);
            return accountwideService != null
                ? accountwideService.buildAccountwideHistory(sinceMs)
                : java.util.Collections.emptyMap();
        }
        Access.plugin().getLocalTradesRuntimeService().ensureProfileLoaded(accountKey);
        LocalFlipHistoryService localHistoryService = Bridge.get(LocalFlipHistoryService.class);
        return localHistoryService != null
            ? localHistoryService.buildHistory(snapshotLocalTradeDeltas(accountKey), sinceMs, accountKey)
            : java.util.Collections.emptyMap();
    }
}
