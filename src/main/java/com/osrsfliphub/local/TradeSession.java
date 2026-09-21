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
    private final LocalTradesRuntime localTradesRuntime;
    private final AccountSession accountSession;
    private final TradeAnalytics analytics;
    private final LocalFlipHistoryService localFlipHistoryService;
    private final long accountwideKey = Const.ACCOUNTWIDE_KEY;
    private final Map<Long, List<Delta>> localTradeDeltasByAccount;
    private final Map<Long, Long> localSessionStartByAccount;
    private final Object localStatsLock;

    @Inject
    TradeSession(
        PluginState state,
        AccountSession accountSession,
        TradeAnalytics analytics,
        LocalFlipHistoryService localFlipHistoryService,
        LocalTradesRuntime localTradesRuntime
    ) {
        this.localTradesRuntime = localTradesRuntime;
        this.accountSession = accountSession;
        this.analytics = analytics;
        this.localFlipHistoryService = localFlipHistoryService;
        this.localTradeDeltasByAccount = state.getLocalTradeDeltasByAccount();
        this.localSessionStartByAccount = state.getLocalSessionStartByAccount();
        this.localStatsLock = state.getLocalStatsLock();
    }

    long resolveAccountHash() {
        return accountSession.resolveAccountHash();
    }

    void updateLocalAccountSessionStart() {
        accountSession.updateLocalAccountSessionStart(localSessionStartByAccount, localStatsLock, accountwideKey);
    }

    void clearLocalAccountSessionStarts() {
        accountSession.clearLocalAccountSessionStarts(localSessionStartByAccount, localStatsLock);
    }

    void ensureLocalSessionStart(long accountKey, long nowMs) {
        accountSession.ensureLocalSessionStart(localSessionStartByAccount, localStatsLock, accountKey, nowMs);
    }

    long resolveStatsSessionStartMs(long accountKey, long nowMs) {
        return accountSession.resolveStatsSessionStartMs(localSessionStartByAccount, localStatsLock, accountKey, nowMs);
    }

    Map<Integer, TradeInfo> buildLocalTradeInfo(long accountKey) {
        return analytics.buildLocalTradeInfo(snapshotLocalTradeDeltas(accountKey));
    }

    Map<Integer, LimitInfo> buildLocalLimitInfo(long accountKey, long nowMs) {
        return analytics.buildLocalLimitInfo(snapshotLocalTradeDeltas(accountKey), nowMs);
    }

    List<Delta> snapshotLocalTradeDeltas(long accountKey) {
        synchronized (localStatsLock) {
            List<Delta> deltas = localTradeDeltasByAccount.get(accountKey);
            return analytics.copySnapshot(deltas);
        }
    }

    Map<Integer, List<StatsFlipInstance>> buildStatsFlipHistory(long accountKey, Long sinceMs) {
        if (accountKey == accountwideKey) {
            FlipHistory accountwideService = Bridge.get(FlipHistory.class);
            return accountwideService != null
                ? accountwideService.buildAccountwideHistory(sinceMs)
                : Collections.emptyMap();
        }
        localTradesRuntime.ensureProfileLoaded(accountKey);
        return localFlipHistoryService.buildHistory(snapshotLocalTradeDeltas(accountKey), sinceMs, accountKey);
    }
}
