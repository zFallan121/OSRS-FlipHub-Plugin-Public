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
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import org.slf4j.Logger;

final class GeLifecycleBackfillExecutionServices {
    private final int maxBackfillProfileCount;
    private final double backfillMatchScoreThreshold;
    private final int maxBatchSize;
    private final int maxLocalTrades;
    private final long localEventBucketMs;
    private final long duplicateTradeWindowMs;
    private final Supplier<ApiClient> apiClientSupplier;
    private final Supplier<PluginConfig> configSupplier;
    private final Supplier<ConfigManager> configManagerSupplier;
    private final Supplier<AccountwideSummaryUploader> accountwideSummaryUploaderSupplier;
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
    private final Function<String, ApiClient.StatsSummaryResponse> fetchRemoteStatsSummary;
    private final Runnable triggerStatsRefresh;
    private final Runnable triggerPanelRefresh;
    private final Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier;
    private final Supplier<Client> clientSupplier;
    private final Supplier<Logger> loggerSupplier;
    private final BooleanSupplier debugEnabledSupplier;

    private SessionRefreshFactoryService sessionRefreshFactoryService;
    private SessionRefreshService sessionRefreshService;
    private AccountwideBackfillCoordinatorFactoryService accountwideBackfillCoordinatorFactoryService;
    private AccountwideBackfillCoordinator accountwideBackfillCoordinator;
    private BackfillUploaderFactoryService backfillUploaderFactoryService;
    private BackfillUploader backfillUploader;
    private AccountwideProfileBackfillFactoryService accountwideProfileBackfillFactoryService;
    private AccountwideProfileBackfillService accountwideProfileBackfillService;
    private BackfillSyncMatcherFactoryService backfillSyncMatcherFactoryService;
    private BackfillSyncMatcher backfillSyncMatcher;

    GeLifecycleBackfillExecutionServices(
        int maxBackfillProfileCount,
        double backfillMatchScoreThreshold,
        int maxBatchSize,
        int maxLocalTrades,
        long localEventBucketMs,
        long duplicateTradeWindowMs,
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
        Supplier<Logger> loggerSupplier,
        BooleanSupplier debugEnabledSupplier
    ) {
        this.maxBackfillProfileCount = maxBackfillProfileCount;
        this.backfillMatchScoreThreshold = backfillMatchScoreThreshold;
        this.maxBatchSize = maxBatchSize;
        this.maxLocalTrades = maxLocalTrades;
        this.localEventBucketMs = localEventBucketMs;
        this.duplicateTradeWindowMs = duplicateTradeWindowMs;
        this.apiClientSupplier = apiClientSupplier;
        this.configSupplier = configSupplier;
        this.configManagerSupplier = configManagerSupplier;
        this.accountwideSummaryUploaderSupplier = accountwideSummaryUploaderSupplier;
        this.uploadBackfillDispatchServiceSupplier = uploadBackfillDispatchServiceSupplier;
        this.uploadEventDispatchFacadeServiceSupplier = uploadEventDispatchFacadeServiceSupplier;
        this.accountwideProfileKeyCollectorSupplier = accountwideProfileKeyCollectorSupplier;
        this.profileStorageFacadeServiceSupplier = profileStorageFacadeServiceSupplier;
        this.localTradeDeltasByAccount = localTradeDeltasByAccount;
        this.localStatsLock = localStatsLock;
        this.profileSelectionPresentationFacadeServiceSupplier = profileSelectionPresentationFacadeServiceSupplier;
        this.backfilledProfilesStoreSupplier = backfilledProfilesStoreSupplier;
        this.ensureProfileLoaded = ensureProfileLoaded;
        this.localStatsSnapshotServiceSupplier = localStatsSnapshotServiceSupplier;
        this.fetchRemoteStatsSummary = fetchRemoteStatsSummary;
        this.triggerStatsRefresh = triggerStatsRefresh;
        this.triggerPanelRefresh = triggerPanelRefresh;
        this.localTradeSessionFacadeServiceSupplier = localTradeSessionFacadeServiceSupplier;
        this.clientSupplier = clientSupplier;
        this.loggerSupplier = loggerSupplier;
        this.debugEnabledSupplier = debugEnabledSupplier;
    }

    SessionRefreshService getSessionRefreshService() {
        SessionRefreshService service = sessionRefreshService;
        if (service != null) {
            return service;
        }
        service = getSessionRefreshFactoryService().create(
            new SessionRefreshPluginHooks(
                apiClientSupplier,
                configSupplier,
                configManagerSupplier,
                accountwideSummaryUploaderSupplier,
                uploadBackfillDispatchServiceSupplier,
                uploadEventDispatchFacadeServiceSupplier
            )
        );
        sessionRefreshService = service;
        return service;
    }

    AccountwideBackfillCoordinator getAccountwideBackfillCoordinator() {
        AccountwideBackfillCoordinator coordinator = accountwideBackfillCoordinator;
        if (coordinator != null) {
            return coordinator;
        }
        coordinator = getAccountwideBackfillCoordinatorFactoryService().create(
            new AccountwideBackfillCoordinatorPluginHooks(
                accountwideProfileKeyCollectorSupplier,
                profileStorageFacadeServiceSupplier,
                localTradeDeltasByAccount,
                localStatsLock,
                profileSelectionPresentationFacadeServiceSupplier,
                backfilledProfilesStoreSupplier,
                ensureProfileLoaded,
                localStatsSnapshotServiceSupplier,
                this::resolveSessionToken,
                fetchRemoteStatsSummary,
                this::getBackfillSyncMatcher,
                (profileKey, activeApiClient, activeConfig, uploader) ->
                    getAccountwideProfileBackfillService().backfillProfileTrades(profileKey, activeApiClient, activeConfig, uploader),
                apiClientSupplier,
                configSupplier,
                this::getBackfillUploader,
                triggerStatsRefresh,
                triggerPanelRefresh,
                uploadBackfillDispatchServiceSupplier,
                loggerSupplier
            )
        );
        accountwideBackfillCoordinator = coordinator;
        return coordinator;
    }

    BackfillUploader getBackfillUploader() {
        BackfillUploader uploader = backfillUploader;
        if (uploader != null) {
            return uploader;
        }
        uploader = getBackfillUploaderFactoryService().create(
            new BackfillUploaderPluginHooks(
                currentToken -> getSessionRefreshService().attemptRefresh(currentToken),
                () -> getSessionRefreshService().clearSession(),
                uploadEventDispatchFacadeServiceSupplier
            )
        );
        backfillUploader = uploader;
        return uploader;
    }

    AccountwideProfileBackfillService getAccountwideProfileBackfillService() {
        AccountwideProfileBackfillService service = accountwideProfileBackfillService;
        if (service != null) {
            return service;
        }
        service = getAccountwideProfileBackfillFactoryService().create(
            new AccountwideProfileBackfillRuntimePluginHooks(
                localTradeSessionFacadeServiceSupplier,
                clientSupplier
            )
        );
        accountwideProfileBackfillService = service;
        return service;
    }

    BackfillSyncMatcher getBackfillSyncMatcher() {
        BackfillSyncMatcher matcher = backfillSyncMatcher;
        if (matcher != null) {
            return matcher;
        }
        matcher = getBackfillSyncMatcherFactoryService().create(
            new BackfillSyncMatcherRuntimePluginHooks(
                this::isDebugEnabled,
                this::debugLog
            )
        );
        backfillSyncMatcher = matcher;
        return matcher;
    }

    private SessionRefreshFactoryService getSessionRefreshFactoryService() {
        SessionRefreshFactoryService service = sessionRefreshFactoryService;
        if (service != null) {
            return service;
        }
        service = new SessionRefreshFactoryService();
        sessionRefreshFactoryService = service;
        return service;
    }

    private AccountwideBackfillCoordinatorFactoryService getAccountwideBackfillCoordinatorFactoryService() {
        AccountwideBackfillCoordinatorFactoryService service = accountwideBackfillCoordinatorFactoryService;
        if (service != null) {
            return service;
        }
        service = new AccountwideBackfillCoordinatorFactoryService(maxBackfillProfileCount);
        accountwideBackfillCoordinatorFactoryService = service;
        return service;
    }

    private BackfillUploaderFactoryService getBackfillUploaderFactoryService() {
        BackfillUploaderFactoryService service = backfillUploaderFactoryService;
        if (service != null) {
            return service;
        }
        service = new BackfillUploaderFactoryService();
        backfillUploaderFactoryService = service;
        return service;
    }

    private AccountwideProfileBackfillFactoryService getAccountwideProfileBackfillFactoryService() {
        AccountwideProfileBackfillFactoryService service = accountwideProfileBackfillFactoryService;
        if (service != null) {
            return service;
        }
        service = new AccountwideProfileBackfillFactoryService(
            maxBatchSize,
            maxLocalTrades,
            localEventBucketMs,
            duplicateTradeWindowMs
        );
        accountwideProfileBackfillFactoryService = service;
        return service;
    }

    private BackfillSyncMatcherFactoryService getBackfillSyncMatcherFactoryService() {
        BackfillSyncMatcherFactoryService service = backfillSyncMatcherFactoryService;
        if (service != null) {
            return service;
        }
        service = new BackfillSyncMatcherFactoryService(maxBackfillProfileCount, backfillMatchScoreThreshold);
        backfillSyncMatcherFactoryService = service;
        return service;
    }

    private String resolveSessionToken() {
        PluginConfig config = configSupplier != null ? configSupplier.get() : null;
        return config != null ? config.sessionToken() : null;
    }

    private boolean isDebugEnabled() {
        return debugEnabledSupplier != null && debugEnabledSupplier.getAsBoolean();
    }

    private void debugLog(String message) {
        Logger logger = loggerSupplier != null ? loggerSupplier.get() : null;
        if (message != null && logger != null && isDebugEnabled()) {
            logger.debug(message);
        }
    }
}
