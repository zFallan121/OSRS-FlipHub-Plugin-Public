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
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;

final class LocalProfileTradesLoadRuntimeHooks implements LocalProfileTradesLoadService.Hooks {
    @FunctionalInterface
    interface LongLongConsumer {
        void accept(long first, long second);
    }

    private final LongFunction<ProfileTradesLoader.Result> loadProfileTrades;
    private final LongLongConsumer putLoadedProfileFileMs;
    private final BiConsumer<Long, List<LocalTradeDelta>> setLocalTradeDeltas;
    private final BiConsumer<Long, List<LocalTradeDelta>> rebuildStatsCache;
    private final BiConsumer<Long, String> putProfileDisplayName;
    private final IntConsumer cacheItemName;
    private final LongConsumer persistLocalTrades;
    private final Runnable markAccountwideUploadDirty;
    private final Runnable scheduleRefreshSoon;
    private final Runnable triggerStatsRefresh;

    LocalProfileTradesLoadRuntimeHooks(LongFunction<ProfileTradesLoader.Result> loadProfileTrades,
                                       LongLongConsumer putLoadedProfileFileMs,
                                       BiConsumer<Long, List<LocalTradeDelta>> setLocalTradeDeltas,
                                       BiConsumer<Long, List<LocalTradeDelta>> rebuildStatsCache,
                                       BiConsumer<Long, String> putProfileDisplayName,
                                       IntConsumer cacheItemName,
                                       LongConsumer persistLocalTrades,
                                       Runnable markAccountwideUploadDirty,
                                       Runnable scheduleRefreshSoon,
                                       Runnable triggerStatsRefresh) {
        this.loadProfileTrades = loadProfileTrades;
        this.putLoadedProfileFileMs = putLoadedProfileFileMs;
        this.setLocalTradeDeltas = setLocalTradeDeltas;
        this.rebuildStatsCache = rebuildStatsCache;
        this.putProfileDisplayName = putProfileDisplayName;
        this.cacheItemName = cacheItemName;
        this.persistLocalTrades = persistLocalTrades;
        this.markAccountwideUploadDirty = markAccountwideUploadDirty;
        this.scheduleRefreshSoon = scheduleRefreshSoon;
        this.triggerStatsRefresh = triggerStatsRefresh;
    }

    @Override
    public ProfileTradesLoader.Result loadProfileTrades(long accountHash) {
        return loadProfileTrades != null ? loadProfileTrades.apply(accountHash) : null;
    }

    @Override
    public void putLoadedProfileFileMs(long accountHash, long fileMs) {
        if (putLoadedProfileFileMs != null) {
            putLoadedProfileFileMs.accept(accountHash, fileMs);
        }
    }

    @Override
    public void setLocalTradeDeltas(long accountHash, List<LocalTradeDelta> deltas) {
        if (setLocalTradeDeltas != null) {
            setLocalTradeDeltas.accept(accountHash, deltas);
        }
    }

    @Override
    public void rebuildStatsCache(long accountHash, List<LocalTradeDelta> deltas) {
        if (rebuildStatsCache != null) {
            rebuildStatsCache.accept(accountHash, deltas);
        }
    }

    @Override
    public void putProfileDisplayName(long accountHash, String displayName) {
        if (putProfileDisplayName != null) {
            putProfileDisplayName.accept(accountHash, displayName);
        }
    }

    @Override
    public void cacheItemName(int itemId) {
        if (cacheItemName != null) {
            cacheItemName.accept(itemId);
        }
    }

    @Override
    public void persistLocalTrades(long accountHash) {
        if (persistLocalTrades != null) {
            persistLocalTrades.accept(accountHash);
        }
    }

    @Override
    public void markAccountwideUploadDirty() {
        if (markAccountwideUploadDirty != null) {
            markAccountwideUploadDirty.run();
        }
    }

    @Override
    public void scheduleRefreshSoon() {
        if (scheduleRefreshSoon != null) {
            scheduleRefreshSoon.run();
        }
    }

    @Override
    public void triggerStatsRefresh() {
        if (triggerStatsRefresh != null) {
            triggerStatsRefresh.run();
        }
    }
}
