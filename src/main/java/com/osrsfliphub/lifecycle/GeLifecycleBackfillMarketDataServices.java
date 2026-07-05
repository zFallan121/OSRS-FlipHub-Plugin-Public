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
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.runelite.client.callback.ClientThread;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;

final class GeLifecycleBackfillMarketDataServices {
    private final int maxGeLimitLookupsPerRequest;
    private final long localLimitWindowMs;
    private final long localEventBucketMs;
    private final long duplicateTradeWindowMs;
    private final long wikiCacheTtlMs;
    private final long wikiMinRefreshMs;
    private final String wikiLatestUrl;
    private final String wikiUserAgent;
    private final Supplier<ClientThread> clientThreadSupplier;
    private final Supplier<ItemLookupService> itemLookupServiceSupplier;
    private final Runnable scheduleRefreshSoon;
    private final Supplier<Logger> loggerSupplier;
    private final BooleanSupplier debugEnabledSupplier;
    private final BooleanSupplier panelVisibleSupplier;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final BooleanSupplier isClientFullyReadySupplier;
    private final boolean hasItemManager;

    private RecentTradeDeduper recentTradeDeduper;

    GeLifecycleBackfillMarketDataServices(
        int maxGeLimitLookupsPerRequest,
        long localLimitWindowMs,
        long localEventBucketMs,
        long duplicateTradeWindowMs,
        long wikiCacheTtlMs,
        long wikiMinRefreshMs,
        String wikiLatestUrl,
        String wikiUserAgent,
        Supplier<ClientThread> clientThreadSupplier,
        Supplier<ItemLookupService> itemLookupServiceSupplier,
        Runnable scheduleRefreshSoon,
        Supplier<Logger> loggerSupplier,
        BooleanSupplier debugEnabledSupplier,
        BooleanSupplier panelVisibleSupplier,
        OkHttpClient httpClient,
        Gson gson,
        BooleanSupplier isClientFullyReadySupplier,
        boolean hasItemManager
    ) {
        this.maxGeLimitLookupsPerRequest = maxGeLimitLookupsPerRequest;
        this.localLimitWindowMs = localLimitWindowMs;
        this.localEventBucketMs = localEventBucketMs;
        this.duplicateTradeWindowMs = duplicateTradeWindowMs;
        this.wikiCacheTtlMs = wikiCacheTtlMs;
        this.wikiMinRefreshMs = wikiMinRefreshMs;
        this.wikiLatestUrl = wikiLatestUrl;
        this.wikiUserAgent = wikiUserAgent;
        this.clientThreadSupplier = clientThreadSupplier;
        this.itemLookupServiceSupplier = itemLookupServiceSupplier;
        this.scheduleRefreshSoon = scheduleRefreshSoon;
        this.loggerSupplier = loggerSupplier;
        this.debugEnabledSupplier = debugEnabledSupplier;
        this.panelVisibleSupplier = panelVisibleSupplier;
        this.httpClient = httpClient;
        this.gson = gson;
        this.isClientFullyReadySupplier = isClientFullyReadySupplier;
        this.hasItemManager = hasItemManager;
    }

    GeLimitService getGeLimitService() {
        return PluginInjectorBridge.get(GeLimitService.class);
    }

    LocalItemEnrichmentService getLocalItemEnrichmentService() {
        return PluginInjectorBridge.get(LocalItemEnrichmentService.class);
    }

    RecentTradeDeduper getRecentTradeDeduper() {
        RecentTradeDeduper deduper = recentTradeDeduper;
        if (deduper != null) {
            return deduper;
        }
        deduper = new RecentTradeDeduper(localEventBucketMs, duplicateTradeWindowMs);
        recentTradeDeduper = deduper;
        return deduper;
    }

    WikiPriceService getWikiPriceService() {
        return PluginInjectorBridge.get(WikiPriceService.class);
    }


    private boolean isReadyForGeLimitLookup() {
        return isClientFullyReadySupplier != null
            && isClientFullyReadySupplier.getAsBoolean()
            && clientThreadSupplier != null
            && clientThreadSupplier.get() != null
            && hasItemManager;
    }

    private int lookupGeLimitSafe(int itemId) {
        ItemLookupService lookup = itemLookupServiceSupplier != null ? itemLookupServiceSupplier.get() : null;
        Integer geLimit = lookup != null ? lookup.lookupGeLimitSafe(itemId) : null;
        return geLimit != null ? geLimit : 0;
    }

    private boolean isDebugEnabled() {
        return debugEnabledSupplier != null && debugEnabledSupplier.getAsBoolean();
    }
}
