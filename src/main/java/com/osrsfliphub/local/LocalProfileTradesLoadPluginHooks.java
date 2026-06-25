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

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

final class LocalProfileTradesLoadPluginHooks implements LocalProfileTradesLoadService.Hooks {
    private final Supplier<Gson> gsonSupplier;
    private final Supplier<ProfileTradesLoader> profileTradesLoaderSupplier;
    private final Map<Long, String> legacyNameKeysByHash;
    private final int maxLocalTrades;
    private final long localEventBucketMs;
    private final long duplicateTradeWindowMs;
    private final Map<Long, Long> loadedProfileFileMs;
    private final Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount;
    private final Object localStatsLock;
    private final Supplier<LocalStatsCacheService> localStatsCacheServiceSupplier;
    private final Map<Long, String> profileDisplayNames;
    private final Supplier<ItemLookupService> itemLookupServiceSupplier;
    private final LongConsumer persistLocalTrades;
    private final Runnable markAccountwideUploadDirty;
    private final Runnable scheduleRefreshSoon;
    private final Runnable triggerStatsRefresh;

    LocalProfileTradesLoadPluginHooks(
        Supplier<Gson> gsonSupplier,
        Supplier<ProfileTradesLoader> profileTradesLoaderSupplier,
        Map<Long, String> legacyNameKeysByHash,
        int maxLocalTrades,
        long localEventBucketMs,
        long duplicateTradeWindowMs,
        Map<Long, Long> loadedProfileFileMs,
        Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount,
        Object localStatsLock,
        Supplier<LocalStatsCacheService> localStatsCacheServiceSupplier,
        Map<Long, String> profileDisplayNames,
        Supplier<ItemLookupService> itemLookupServiceSupplier,
        LongConsumer persistLocalTrades,
        Runnable markAccountwideUploadDirty,
        Runnable scheduleRefreshSoon,
        Runnable triggerStatsRefresh
    ) {
        this.gsonSupplier = gsonSupplier;
        this.profileTradesLoaderSupplier = profileTradesLoaderSupplier;
        this.legacyNameKeysByHash = legacyNameKeysByHash;
        this.maxLocalTrades = maxLocalTrades;
        this.localEventBucketMs = localEventBucketMs;
        this.duplicateTradeWindowMs = duplicateTradeWindowMs;
        this.loadedProfileFileMs = loadedProfileFileMs;
        this.localTradeDeltasByAccount = localTradeDeltasByAccount;
        this.localStatsLock = localStatsLock;
        this.localStatsCacheServiceSupplier = localStatsCacheServiceSupplier;
        this.profileDisplayNames = profileDisplayNames;
        this.itemLookupServiceSupplier = itemLookupServiceSupplier;
        this.persistLocalTrades = persistLocalTrades;
        this.markAccountwideUploadDirty = markAccountwideUploadDirty;
        this.scheduleRefreshSoon = scheduleRefreshSoon;
        this.triggerStatsRefresh = triggerStatsRefresh;
    }

    @Override
    public ProfileTradesLoader.Result loadProfileTrades(long accountHash) {
        Gson gson = gsonSupplier != null ? gsonSupplier.get() : null;
        ProfileTradesLoader loader = profileTradesLoaderSupplier != null ? profileTradesLoaderSupplier.get() : null;
        if (gson == null || loader == null) {
            return null;
        }
        return loader.load(
            accountHash,
            legacyNameKeysByHash,
            maxLocalTrades,
            localEventBucketMs,
            duplicateTradeWindowMs
        );
    }

    @Override
    public void putLoadedProfileFileMs(long accountHash, long fileMs) {
        if (loadedProfileFileMs != null) {
            loadedProfileFileMs.put(accountHash, fileMs);
        }
    }

    @Override
    public void setLocalTradeDeltas(long accountHash, List<LocalTradeDelta> deltas) {
        if (localTradeDeltasByAccount == null || localStatsLock == null) {
            return;
        }
        synchronized (localStatsLock) {
            localTradeDeltasByAccount.put(accountHash, deltas != null ? deltas : new ArrayList<>());
        }
    }

    @Override
    public void rebuildStatsCache(long accountHash, List<LocalTradeDelta> deltas) {
        LocalStatsCacheService service = localStatsCacheServiceSupplier != null
            ? localStatsCacheServiceSupplier.get()
            : null;
        if (service != null) {
            service.rebuild(accountHash, deltas);
        }
    }

    @Override
    public void putProfileDisplayName(long accountHash, String displayName) {
        if (profileDisplayNames == null || displayName == null || displayName.trim().isEmpty()) {
            return;
        }
        profileDisplayNames.put(accountHash, displayName.trim());
    }

    @Override
    public void cacheItemName(int itemId) {
        ItemLookupService service = itemLookupServiceSupplier != null ? itemLookupServiceSupplier.get() : null;
        if (service != null) {
            service.cacheItemName(itemId);
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
