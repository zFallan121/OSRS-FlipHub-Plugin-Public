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

import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

final class LocalTradeDeltaRecorderPluginHooks implements LocalTradeDeltaRecorder.Hooks {
    @FunctionalInterface
    interface SessionStartEnsurer {
        void ensure(long accountKey, long tsClientMs);
    }

    @FunctionalInterface
    interface TradeDeltaPairAppender {
        void append(long accountKey, long accountwideKey, LocalTradeDelta delta);
    }

    @FunctionalInterface
    interface StatsCacheDeltaApplier {
        void apply(long accountKey, LocalTradeDelta delta);
    }

    private final Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier;
    private final LongConsumer ensureProfileLoaded;
    private final SessionStartEnsurer ensureLocalSessionStart;
    private final IntConsumer cacheItemName;
    private final TradeDeltaPairAppender appendTradeDeltaPair;
    private final StatsCacheDeltaApplier applyDeltaToStatsCache;
    private final LongConsumer persistLocalTrades;
    private final Runnable triggerStatsRefresh;
    private final Runnable triggerPanelRefresh;

    LocalTradeDeltaRecorderPluginHooks(
        Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier,
        LongConsumer ensureProfileLoaded,
        SessionStartEnsurer ensureLocalSessionStart,
        IntConsumer cacheItemName,
        TradeDeltaPairAppender appendTradeDeltaPair,
        StatsCacheDeltaApplier applyDeltaToStatsCache,
        LongConsumer persistLocalTrades,
        Runnable triggerStatsRefresh,
        Runnable triggerPanelRefresh
    ) {
        this.localAccountSessionServiceSupplier = localAccountSessionServiceSupplier;
        this.ensureProfileLoaded = ensureProfileLoaded;
        this.ensureLocalSessionStart = ensureLocalSessionStart;
        this.cacheItemName = cacheItemName;
        this.appendTradeDeltaPair = appendTradeDeltaPair;
        this.applyDeltaToStatsCache = applyDeltaToStatsCache;
        this.persistLocalTrades = persistLocalTrades;
        this.triggerStatsRefresh = triggerStatsRefresh;
        this.triggerPanelRefresh = triggerPanelRefresh;
    }

    @Override
    public long resolveLocalAccountKey() {
        LocalAccountSessionService service = localAccountSessionServiceSupplier != null
            ? localAccountSessionServiceSupplier.get()
            : null;
        return service != null ? service.resolveLocalAccountKey() : 0L;
    }

    @Override
    public void ensureProfileLoaded(long accountKey) {
        if (ensureProfileLoaded != null) {
            ensureProfileLoaded.accept(accountKey);
        }
    }

    @Override
    public void ensureLocalSessionStart(long accountKey, long tsClientMs) {
        if (ensureLocalSessionStart != null) {
            ensureLocalSessionStart.ensure(accountKey, tsClientMs);
        }
    }

    @Override
    public void cacheItemName(int itemId) {
        if (cacheItemName != null) {
            cacheItemName.accept(itemId);
        }
    }

    @Override
    public void appendTradeDeltaPair(long accountKey, long accountwideKey, LocalTradeDelta delta) {
        if (appendTradeDeltaPair != null) {
            appendTradeDeltaPair.append(accountKey, accountwideKey, delta);
        }
    }

    @Override
    public void applyDeltaToStatsCache(long accountKey, LocalTradeDelta delta) {
        if (applyDeltaToStatsCache != null) {
            applyDeltaToStatsCache.apply(accountKey, delta);
        }
    }

    @Override
    public void persistLocalTrades(long accountKey) {
        if (persistLocalTrades != null) {
            persistLocalTrades.accept(accountKey);
        }
    }

    @Override
    public void triggerStatsRefresh() {
        if (triggerStatsRefresh != null) {
            triggerStatsRefresh.run();
        }
    }

    @Override
    public void triggerPanelRefresh() {
        if (triggerPanelRefresh != null) {
            triggerPanelRefresh.run();
        }
    }
}
