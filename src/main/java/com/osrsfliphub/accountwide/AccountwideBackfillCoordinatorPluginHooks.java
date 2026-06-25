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
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import org.slf4j.Logger;

final class AccountwideBackfillCoordinatorPluginHooks implements AccountwideBackfillCoordinatorFactoryService.RuntimeHooks {
    @FunctionalInterface
    interface ProfileBackfillRunner {
        boolean run(long profileKey, ApiClient apiClient, PluginConfig config, BackfillUploader backfillUploader);
    }

    private final Supplier<AccountwideProfileKeyCollector> accountwideProfileKeyCollectorSupplier;
    private final Supplier<ProfileStorageFacadeService> profileStorageFacadeServiceSupplier;
    private final Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount;
    private final Object localStatsLock;
    private final Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier;
    private final Supplier<BackfilledProfilesStore> backfilledProfilesStoreSupplier;
    private final LongConsumer ensureProfileLoaded;
    private final Supplier<LocalStatsSnapshotService> localStatsSnapshotServiceSupplier;
    private final Supplier<String> sessionTokenSupplier;
    private final Function<String, ApiClient.StatsSummaryResponse> fetchRemoteStatsSummary;
    private final Supplier<BackfillSyncMatcher> backfillSyncMatcherSupplier;
    private final ProfileBackfillRunner profileBackfillRunner;
    private final Supplier<ApiClient> apiClientSupplier;
    private final Supplier<PluginConfig> pluginConfigSupplier;
    private final Supplier<BackfillUploader> backfillUploaderSupplier;
    private final Runnable triggerStatsRefresh;
    private final Runnable triggerPanelRefresh;
    private final Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier;
    private final Supplier<Logger> loggerSupplier;

    AccountwideBackfillCoordinatorPluginHooks(
        Supplier<AccountwideProfileKeyCollector> accountwideProfileKeyCollectorSupplier,
        Supplier<ProfileStorageFacadeService> profileStorageFacadeServiceSupplier,
        Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount,
        Object localStatsLock,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier,
        Supplier<BackfilledProfilesStore> backfilledProfilesStoreSupplier,
        LongConsumer ensureProfileLoaded,
        Supplier<LocalStatsSnapshotService> localStatsSnapshotServiceSupplier,
        Supplier<String> sessionTokenSupplier,
        Function<String, ApiClient.StatsSummaryResponse> fetchRemoteStatsSummary,
        Supplier<BackfillSyncMatcher> backfillSyncMatcherSupplier,
        ProfileBackfillRunner profileBackfillRunner,
        Supplier<ApiClient> apiClientSupplier,
        Supplier<PluginConfig> pluginConfigSupplier,
        Supplier<BackfillUploader> backfillUploaderSupplier,
        Runnable triggerStatsRefresh,
        Runnable triggerPanelRefresh,
        Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier,
        Supplier<Logger> loggerSupplier
    ) {
        this.accountwideProfileKeyCollectorSupplier = accountwideProfileKeyCollectorSupplier;
        this.profileStorageFacadeServiceSupplier = profileStorageFacadeServiceSupplier;
        this.localTradeDeltasByAccount = localTradeDeltasByAccount;
        this.localStatsLock = localStatsLock;
        this.profileSelectionFacadeServiceSupplier = profileSelectionFacadeServiceSupplier;
        this.backfilledProfilesStoreSupplier = backfilledProfilesStoreSupplier;
        this.ensureProfileLoaded = ensureProfileLoaded;
        this.localStatsSnapshotServiceSupplier = localStatsSnapshotServiceSupplier;
        this.sessionTokenSupplier = sessionTokenSupplier;
        this.fetchRemoteStatsSummary = fetchRemoteStatsSummary;
        this.backfillSyncMatcherSupplier = backfillSyncMatcherSupplier;
        this.profileBackfillRunner = profileBackfillRunner;
        this.apiClientSupplier = apiClientSupplier;
        this.pluginConfigSupplier = pluginConfigSupplier;
        this.backfillUploaderSupplier = backfillUploaderSupplier;
        this.triggerStatsRefresh = triggerStatsRefresh;
        this.triggerPanelRefresh = triggerPanelRefresh;
        this.uploadBackfillDispatchServiceSupplier = uploadBackfillDispatchServiceSupplier;
        this.loggerSupplier = loggerSupplier;
    }

    @Override
    public Set<Long> collectAccountwideProfileKeys() {
        AccountwideProfileKeyCollector collector = accountwideProfileKeyCollectorSupplier != null
            ? accountwideProfileKeyCollectorSupplier.get()
            : null;
        ProfileStorageFacadeService storage = profileStorageFacadeServiceSupplier != null
            ? profileStorageFacadeServiceSupplier.get()
            : null;
        ProfileSelectionPresentationFacadeService profileSelection = profileSelectionFacadeServiceSupplier != null
            ? profileSelectionFacadeServiceSupplier.get()
            : null;
        if (collector == null || storage == null || profileSelection == null || localStatsLock == null) {
            return null;
        }
        return collector.collect(
            storage.getProfilesDir(),
            storage.getLegacyProfilesDir(),
            localTradeDeltasByAccount,
            localStatsLock,
            profileSelection::loadProfilesFromDisk
        );
    }

    @Override
    public Set<Long> loadBackfilledProfileKeys() {
        BackfilledProfilesStore store = backfilledProfilesStoreSupplier != null ? backfilledProfilesStoreSupplier.get() : null;
        return store != null ? store.load() : null;
    }

    @Override
    public void ensureProfileLoaded(long key) {
        if (ensureProfileLoaded != null) {
            ensureProfileLoaded.accept(key);
        }
    }

    @Override
    public LocalStatsSnapshot buildLocalStatsSnapshot(long key) {
        LocalStatsSnapshotService service = localStatsSnapshotServiceSupplier != null
            ? localStatsSnapshotServiceSupplier.get()
            : null;
        return service != null ? service.buildSnapshot(key, null, StatsItemSort.COMPLETION) : null;
    }

    @Override
    public String getSessionToken() {
        return sessionTokenSupplier != null ? sessionTokenSupplier.get() : null;
    }

    @Override
    public ApiClient.StatsSummaryResponse fetchRemoteStatsSummary(String token) {
        return fetchRemoteStatsSummary != null ? fetchRemoteStatsSummary.apply(token) : null;
    }

    @Override
    public Set<Long> inferLikelySyncedProfiles(Set<Long> profileKeys,
                                               Map<Long, StatsSummary> localSummaries,
                                               StatsSummary remoteSummary) {
        BackfillSyncMatcher matcher = backfillSyncMatcherSupplier != null ? backfillSyncMatcherSupplier.get() : null;
        return matcher != null ? matcher.inferLikelySyncedProfiles(profileKeys, localSummaries, remoteSummary) : null;
    }

    @Override
    public boolean backfillProfileTrades(long profileKey) {
        if (profileBackfillRunner == null) {
            return false;
        }
        ApiClient apiClient = apiClientSupplier != null ? apiClientSupplier.get() : null;
        PluginConfig config = pluginConfigSupplier != null ? pluginConfigSupplier.get() : null;
        BackfillUploader uploader = backfillUploaderSupplier != null ? backfillUploaderSupplier.get() : null;
        if (apiClient == null || config == null || uploader == null) {
            return false;
        }
        return profileBackfillRunner.run(profileKey, apiClient, config, uploader);
    }

    @Override
    public void persistBackfilledProfileKeys(Set<Long> keys) {
        BackfilledProfilesStore store = backfilledProfilesStoreSupplier != null ? backfilledProfilesStoreSupplier.get() : null;
        if (store != null) {
            store.persist(keys);
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

    @Override
    public void resetBackfillRetryState() {
        UploadBackfillDispatchService service = uploadBackfillDispatchServiceSupplier != null
            ? uploadBackfillDispatchServiceSupplier.get()
            : null;
        if (service != null) {
            service.resetBackfillRetryState();
        }
    }

    @Override
    public void logWarn(String message) {
        Logger logger = loggerSupplier != null ? loggerSupplier.get() : null;
        if (message != null && logger != null) {
            logger.warn(message);
        }
    }

    @Override
    public void logWarn(String message, Throwable error) {
        Logger logger = loggerSupplier != null ? loggerSupplier.get() : null;
        if (message == null || logger == null) {
            return;
        }
        if (error != null) {
            logger.warn(message, error);
        } else {
            logger.warn(message);
        }
    }
}
