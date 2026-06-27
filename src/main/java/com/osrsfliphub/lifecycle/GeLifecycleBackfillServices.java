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

import static com.osrsfliphub.GeLifecyclePluginConstants.*;

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

final class GeLifecycleBackfillServices {
    private final Supplier<ApiClient> apiClientSupplier;
    private final Supplier<PluginConfig> configSupplier;
    private final Supplier<ConfigManager> configManagerSupplier;
    private final Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier;
    private final Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier;
    private final Supplier<AccountwideProfileKeyCollector> accountwideProfileKeyCollectorSupplier;
    private final Supplier<ProfileStorageFacadeService> profileStorageFacadeServiceSupplier;
    private final Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount;
    private final Object localStatsLock;
    private final Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier;
    private final Supplier<BackfilledProfilesStore> backfilledProfilesStoreSupplier;
    private final LongConsumer ensureProfileLoaded;
    private final Supplier<LocalStatsSnapshotService> localStatsSnapshotServiceSupplier;
    private final Function<String, ApiClient.StatsSummaryResponse> fetchRemoteStatsSummaryFn;
    private final Runnable triggerStatsRefresh;
    private final Runnable triggerPanelRefresh;
    private final Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier;
    private final Supplier<Client> clientSupplier;
    private final Supplier<ClientThread> clientThreadSupplier;
    private final Supplier<ItemLookupService> itemLookupServiceSupplier;
    private final Runnable scheduleRefreshSoon;
    private final BooleanSupplier panelVisibleSupplier;
    private final Supplier<Logger> loggerSupplier;
    private final BooleanSupplier debugEnabledSupplier;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final BooleanSupplier isClientFullyReadySupplier;
    private final boolean hasItemManager;

    private AccountwideSummaryUploader accountwideSummaryUploader;
    private GeLifecycleBackfillMarketServices backfillMarketServices;

    GeLifecycleBackfillServices(
        Supplier<ApiClient> apiClientSupplier,
        Supplier<PluginConfig> configSupplier,
        Supplier<ConfigManager> configManagerSupplier,
        Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier,
        Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier,
        Supplier<AccountwideProfileKeyCollector> accountwideProfileKeyCollectorSupplier,
        Supplier<ProfileStorageFacadeService> profileStorageFacadeServiceSupplier,
        GeLifecycleSharedState sharedState,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier,
        Supplier<BackfilledProfilesStore> backfilledProfilesStoreSupplier,
        LongConsumer ensureProfileLoaded,
        Supplier<LocalStatsSnapshotService> localStatsSnapshotServiceSupplier,
        Function<String, ApiClient.StatsSummaryResponse> fetchRemoteStatsSummaryFn,
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
        this.apiClientSupplier = apiClientSupplier;
        this.configSupplier = configSupplier;
        this.configManagerSupplier = configManagerSupplier;
        this.uploadBackfillDispatchServiceSupplier = uploadBackfillDispatchServiceSupplier;
        this.uploadEventDispatchFacadeServiceSupplier = uploadEventDispatchFacadeServiceSupplier;
        this.accountwideProfileKeyCollectorSupplier = accountwideProfileKeyCollectorSupplier;
        this.profileStorageFacadeServiceSupplier = profileStorageFacadeServiceSupplier;
        this.localTradeDeltasByAccount = sharedState.getLocalTradeDeltasByAccount();
        this.localStatsLock = sharedState.getLocalStatsLock();
        this.profileSelectionPresentationFacadeServiceSupplier = profileSelectionPresentationFacadeServiceSupplier;
        this.backfilledProfilesStoreSupplier = backfilledProfilesStoreSupplier;
        this.ensureProfileLoaded = ensureProfileLoaded;
        this.localStatsSnapshotServiceSupplier = localStatsSnapshotServiceSupplier;
        this.fetchRemoteStatsSummaryFn = fetchRemoteStatsSummaryFn;
        this.triggerStatsRefresh = triggerStatsRefresh;
        this.triggerPanelRefresh = triggerPanelRefresh;
        this.localTradeSessionFacadeServiceSupplier = localTradeSessionFacadeServiceSupplier;
        this.clientSupplier = clientSupplier;
        this.clientThreadSupplier = clientThreadSupplier;
        this.itemLookupServiceSupplier = itemLookupServiceSupplier;
        this.scheduleRefreshSoon = scheduleRefreshSoon;
        this.panelVisibleSupplier = panelVisibleSupplier;
        this.loggerSupplier = loggerSupplier;
        this.debugEnabledSupplier = debugEnabledSupplier;
        this.httpClient = httpClient;
        this.gson = gson;
        this.isClientFullyReadySupplier = isClientFullyReadySupplier;
        this.hasItemManager = hasItemManager;
    }

    AccountwideSummaryUploader getAccountwideSummaryUploader() {
        return PluginInjectorBridge.get(AccountwideSummaryUploader.class);
    }

    GeLifecycleBackfillMarketServices getBackfillMarketServices() {
        GeLifecycleBackfillMarketServices services = backfillMarketServices;
        if (services != null) {
            return services;
        }
        services = new GeLifecycleBackfillMarketServices(
            MAX_BACKFILL_PROFILE_COUNT,
            BACKFILL_MATCH_SCORE_THRESHOLD,
            MAX_BATCH_SIZE,
            MAX_LOCAL_TRADES,
            LOCAL_EVENT_BUCKET_MS,
            DUPLICATE_TRADE_WINDOW_MS,
            MAX_GE_LIMIT_LOOKUPS_PER_REQUEST,
            LOCAL_LIMIT_WINDOW_MS,
            WIKI_CACHE_TTL_MS,
            WIKI_MIN_REFRESH_MS,
            WIKI_LATEST_URL,
            WIKI_USER_AGENT,
            apiClientSupplier,
            configSupplier,
            configManagerSupplier,
            this::getAccountwideSummaryUploader,
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
            fetchRemoteStatsSummaryFn,
            triggerStatsRefresh,
            triggerPanelRefresh,
            localTradeSessionFacadeServiceSupplier,
            clientSupplier,
            clientThreadSupplier,
            itemLookupServiceSupplier,
            scheduleRefreshSoon,
            panelVisibleSupplier,
            loggerSupplier,
            debugEnabledSupplier,
            httpClient,
            gson,
            isClientFullyReadySupplier,
            hasItemManager
        );
        backfillMarketServices = services;
        return services;
    }
}
