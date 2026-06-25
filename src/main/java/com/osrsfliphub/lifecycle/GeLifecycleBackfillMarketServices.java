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
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;

final class GeLifecycleBackfillMarketServices {
    private final GeLifecycleBackfillExecutionServices executionServices;
    private final GeLifecycleBackfillMarketDataServices marketDataServices;

    GeLifecycleBackfillMarketServices(
        int maxBackfillProfileCount,
        double backfillMatchScoreThreshold,
        int maxBatchSize,
        int maxLocalTrades,
        long localEventBucketMs,
        long duplicateTradeWindowMs,
        int maxGeLimitLookupsPerRequest,
        long localLimitWindowMs,
        long wikiCacheTtlMs,
        long wikiMinRefreshMs,
        String wikiLatestUrl,
        String wikiUserAgent,
        Supplier<ApiClient> apiClientSupplier,
        Supplier<PluginConfig> configSupplier,
        Supplier<ConfigManager> configManagerSupplier,
        Supplier<AccountwideSummaryUploader> accountwideSummaryUploaderSupplier,
        Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier,
        Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier,
        Supplier<AccountwideProfileKeyCollector> accountwideProfileKeyCollectorSupplier,
        Supplier<ProfileStorageFacadeService> profileStorageFacadeServiceSupplier,
        Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount,
        Object localStatsLock,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier,
        Supplier<BackfilledProfilesStore> backfilledProfilesStoreSupplier,
        LongConsumer ensureProfileLoaded,
        Supplier<LocalStatsSnapshotService> localStatsSnapshotServiceSupplier,
        Function<String, ApiClient.StatsSummaryResponse> fetchRemoteStatsSummary,
        Runnable triggerStatsRefresh,
        Runnable triggerPanelRefresh,
        Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier,
        Supplier<Client> clientSupplier,
        Supplier<ClientThread> clientThreadSupplier,
        Supplier<ItemLookupService> itemLookupServiceSupplier,
        Runnable scheduleRefreshSoon,
        BooleanSupplier panelVisibleSupplier,
        Supplier<Logger> loggerSupplier,
        BooleanSupplier debugEnabledSupplier,
        OkHttpClient httpClient,
        Gson gson,
        BooleanSupplier isClientFullyReadySupplier,
        boolean hasItemManager
    ) {
        this.executionServices = new GeLifecycleBackfillExecutionServices(
            maxBackfillProfileCount,
            backfillMatchScoreThreshold,
            maxBatchSize,
            maxLocalTrades,
            localEventBucketMs,
            duplicateTradeWindowMs,
            apiClientSupplier,
            configSupplier,
            configManagerSupplier,
            accountwideSummaryUploaderSupplier,
            uploadBackfillDispatchServiceSupplier,
            uploadEventDispatchFacadeServiceSupplier,
            accountwideProfileKeyCollectorSupplier,
            profileStorageFacadeServiceSupplier,
            localTradeDeltasByAccount,
            localStatsLock,
            profileSelectionPresentationFacadeServiceSupplier,
            backfilledProfilesStoreSupplier,
            ensureProfileLoaded,
            localStatsSnapshotServiceSupplier,
            fetchRemoteStatsSummary,
            triggerStatsRefresh,
            triggerPanelRefresh,
            localTradeSessionFacadeServiceSupplier,
            clientSupplier,
            loggerSupplier,
            debugEnabledSupplier
        );
        this.marketDataServices = new GeLifecycleBackfillMarketDataServices(
            maxGeLimitLookupsPerRequest,
            localLimitWindowMs,
            localEventBucketMs,
            duplicateTradeWindowMs,
            wikiCacheTtlMs,
            wikiMinRefreshMs,
            wikiLatestUrl,
            wikiUserAgent,
            clientThreadSupplier,
            itemLookupServiceSupplier,
            scheduleRefreshSoon,
            loggerSupplier,
            debugEnabledSupplier,
            panelVisibleSupplier,
            httpClient,
            gson,
            isClientFullyReadySupplier,
            hasItemManager
        );
    }

    SessionRefreshService getSessionRefreshService() {
        return executionServices.getSessionRefreshService();
    }

    AccountwideBackfillCoordinator getAccountwideBackfillCoordinator() {
        return executionServices.getAccountwideBackfillCoordinator();
    }

    BackfillUploader getBackfillUploader() {
        return executionServices.getBackfillUploader();
    }

    AccountwideProfileBackfillService getAccountwideProfileBackfillService() {
        return executionServices.getAccountwideProfileBackfillService();
    }

    BackfillSyncMatcher getBackfillSyncMatcher() {
        return executionServices.getBackfillSyncMatcher();
    }

    GeLimitService getGeLimitService() {
        return marketDataServices.getGeLimitService();
    }

    LocalItemEnrichmentService getLocalItemEnrichmentService() {
        return marketDataServices.getLocalItemEnrichmentService();
    }

    RecentTradeDeduper getRecentTradeDeduper() {
        return marketDataServices.getRecentTradeDeduper();
    }

    WikiPriceService getWikiPriceService() {
        return marketDataServices.getWikiPriceService();
    }
}
