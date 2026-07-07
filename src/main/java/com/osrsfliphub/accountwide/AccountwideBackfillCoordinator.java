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

import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
final class AccountwideBackfillCoordinator {
    private static final Logger log = LoggerFactory.getLogger(AccountwideBackfillCoordinator.class);

    interface Hooks {
        Set<Long> collectAccountwideProfileKeys();
        Set<Long> loadBackfilledProfileKeys();
        void ensureProfileLoaded(long key);
        LocalStatsSnapshot buildLocalStatsSnapshot(long key);
        String getSessionToken();
        ApiClient.StatsSummaryResponse fetchRemoteStatsSummary(String token);
        Set<Long> inferLikelySyncedProfiles(Set<Long> profileKeys, Map<Long, StatsSummary> localSummaries,
                                            StatsSummary remoteSummary);
        boolean backfillProfileTrades(long profileKey);
        void persistBackfilledProfileKeys(Set<Long> keys);
        void triggerStatsRefresh();
        void triggerPanelRefresh();
        void resetBackfillRetryState();
        void logWarn(String message);
        void logWarn(String message, Throwable error);
    }

    static final class Result {
        final boolean shouldRetry;

        Result(boolean shouldRetry) {
            this.shouldRetry = shouldRetry;
        }
    }

    private final int maxBackfillProfileCount;
    private final Hooks hooks;

    @Inject
    AccountwideBackfillCoordinator(ApiClient apiClient, PluginConfig config, PluginState state) {
        this(GeLifecyclePluginConstants.MAX_BACKFILL_PROFILE_COUNT, productionHooks(apiClient, config, state));
    }

    AccountwideBackfillCoordinator(int maxBackfillProfileCount, Hooks hooks) {
        this.maxBackfillProfileCount = Math.max(1, maxBackfillProfileCount);
        this.hooks = hooks;
    }

    private static BackfilledProfilesStore backfilledProfilesStore() {
        return PluginInjectorBridge.get(BackfilledProfilesStore.class);
    }

    private static Hooks productionHooks(ApiClient apiClient, PluginConfig config, PluginState state) {
        return new Hooks() {
            @Override
            public Set<Long> collectAccountwideProfileKeys() {
                AccountwideProfileKeyCollector collector =
                    PluginInjectorBridge.get(AccountwideProfileKeyCollector.class);
                ProfileStorageFacadeService storage =
                    PluginInjectorBridge.get(ProfileStorageFacadeService.class);
                ProfileSelectionPresentationFacadeService profileSelection = PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class);
                if (collector == null || storage == null || profileSelection == null) {
                    return null;
                }
                return collector.collect(
                    storage.getProfilesDir(),
                    storage.getLegacyProfilesDir(),
                    state.getLocalTradeDeltasByAccount(),
                    state.getLocalStatsLock(),
                    profileSelection::loadProfilesFromDisk);
            }

            @Override
            public Set<Long> loadBackfilledProfileKeys() {
                BackfilledProfilesStore store = backfilledProfilesStore();
                return store != null ? store.load() : null;
            }

            @Override
            public void ensureProfileLoaded(long key) {
                PluginAccess.plugin().getLocalTradesRuntimeService().ensureProfileLoaded(key);
            }

            @Override
            public LocalStatsSnapshot buildLocalStatsSnapshot(long key) {
                LocalStatsSnapshotService service =
                    PluginInjectorBridge.get(LocalStatsSnapshotService.class);
                return service != null ? service.buildSnapshot(key, null, StatsItemSort.COMPLETION) : null;
            }

            @Override
            public String getSessionToken() {
                return config != null ? config.sessionToken() : null;
            }

            @Override
            public ApiClient.StatsSummaryResponse fetchRemoteStatsSummary(String token) {
                GeLifecyclePlugin plugin = PluginAccess.plugin();
                SessionRefreshService sessionRefresh = PluginInjectorBridge.get(SessionRefreshService.class);
                return plugin.runtimeUtilityServices.fetchRemoteStatsSummary(
                    apiClient, config, sessionRefresh, token, null, true);
            }

            @Override
            public Set<Long> inferLikelySyncedProfiles(Set<Long> profileKeys,
                                                       Map<Long, StatsSummary> localSummaries,
                                                       StatsSummary remoteSummary) {
                BackfillSyncMatcher matcher = PluginInjectorBridge.get(BackfillSyncMatcher.class);
                return matcher != null
                    ? matcher.inferLikelySyncedProfiles(profileKeys, localSummaries, remoteSummary) : null;
            }

            @Override
            public boolean backfillProfileTrades(long profileKey) {
                AccountwideProfileBackfillService runner =
                    PluginInjectorBridge.get(AccountwideProfileBackfillService.class);
                BackfillUploader uploader = PluginInjectorBridge.get(BackfillUploader.class);
                if (runner == null || apiClient == null || config == null || uploader == null) {
                    return false;
                }
                return runner.backfillProfileTrades(profileKey, apiClient, config, uploader);
            }

            @Override
            public void persistBackfilledProfileKeys(Set<Long> keys) {
                BackfilledProfilesStore store = backfilledProfilesStore();
                if (store != null) {
                    store.persist(keys);
                }
            }

            @Override
            public void triggerStatsRefresh() {
                GeLifecyclePlugin plugin = PluginAccess.plugin();
                PanelRefreshCoordinator coordinator = plugin.getPanelRefreshCoordinator();
                if (coordinator != null) {
                    coordinator.triggerStatsRefresh(plugin.scheduler);
                }
            }

            @Override
            public void triggerPanelRefresh() {
                GeLifecyclePlugin plugin = PluginAccess.plugin();
                PanelRefreshCoordinator coordinator = plugin.getPanelRefreshCoordinator();
                if (coordinator != null) {
                    coordinator.triggerPanelRefresh(plugin.scheduler);
                }
            }

            @Override
            public void resetBackfillRetryState() {
                UploadBackfillDispatchService service =
                    PluginInjectorBridge.get(UploadBackfillDispatchService.class);
                if (service != null) {
                    service.resetBackfillRetryState();
                }
            }

            @Override
            public void logWarn(String message) {
                if (message != null) {
                    log.warn(message);
                }
            }

            @Override
            public void logWarn(String message, Throwable error) {
                if (message == null) {
                    return;
                }
                if (error != null) {
                    log.warn(message, error);
                } else {
                    log.warn(message);
                }
            }
        };
    }

    Result runCycle() {
        boolean shouldRetry = false;
        if (hooks == null) {
            return new Result(false);
        }
        try {
            Set<Long> collectedProfileKeys = hooks.collectAccountwideProfileKeys();
            if (collectedProfileKeys == null) {
                hooks.resetBackfillRetryState();
                return new Result(false);
            }
            Set<Long> profileKeys = new HashSet<>(collectedProfileKeys);
            profileKeys.removeIf(key -> key == null || key <= 0);
            if (profileKeys.isEmpty()) {
                hooks.resetBackfillRetryState();
                return new Result(false);
            }
            if (profileKeys.size() > maxBackfillProfileCount) {
                hooks.logWarn(
                    "FlipHub backfill skipped: " + profileKeys.size() + " profiles exceeds safe cap "
                        + maxBackfillProfileCount
                );
                hooks.resetBackfillRetryState();
                return new Result(false);
            }

            Set<Long> loadedBackfilled = hooks.loadBackfilledProfileKeys();
            if (loadedBackfilled != null && loadedBackfilled.containsAll(profileKeys)) {
                hooks.resetBackfillRetryState();
                return new Result(false);
            }
            Set<Long> alreadyBackfilled = loadedBackfilled != null
                ? new HashSet<>(loadedBackfilled)
                : new HashSet<>();

            Map<Long, StatsSummary> localSummaries = new HashMap<>();
            for (Long key : profileKeys) {
                if (key == null || key <= 0) {
                    continue;
                }
                hooks.ensureProfileLoaded(key);
                LocalStatsSnapshot snapshot = hooks.buildLocalStatsSnapshot(key);
                localSummaries.put(key, snapshot != null && snapshot.summary != null ? snapshot.summary : new StatsSummary());
            }

            String token = hooks.getSessionToken();
            ApiClient.StatsSummaryResponse remoteResponse = hooks.fetchRemoteStatsSummary(token);
            if (remoteResponse == null || remoteResponse.summary == null) {
                return new Result(true);
            }

            Set<Long> likelySyncedProfiles = hooks.inferLikelySyncedProfiles(
                profileKeys,
                localSummaries,
                remoteResponse.summary
            );
            if (likelySyncedProfiles == null) {
                return new Result(true);
            }

            List<Long> missingProfiles = profileKeys.stream()
                .filter(key -> !likelySyncedProfiles.contains(key))
                .filter(key -> !alreadyBackfilled.contains(key))
                .sorted()
                .collect(Collectors.toList());
            if (missingProfiles.isEmpty()) {
                hooks.resetBackfillRetryState();
                return new Result(false);
            }

            boolean changed = false;
            boolean fullySynced = true;
            for (Long profileKey : missingProfiles) {
                if (profileKey == null || profileKey <= 0) {
                    continue;
                }
                boolean synced = hooks.backfillProfileTrades(profileKey);
                if (!synced) {
                    hooks.logWarn("FlipHub backfill stopped: profile " + profileKey + " upload failed");
                    fullySynced = false;
                    break;
                }
                alreadyBackfilled.add(profileKey);
                changed = true;
            }
            if (changed) {
                hooks.persistBackfilledProfileKeys(alreadyBackfilled);
                hooks.triggerStatsRefresh();
                hooks.triggerPanelRefresh();
            }
            if (fullySynced) {
                hooks.resetBackfillRetryState();
            } else {
                shouldRetry = true;
            }
        } catch (RuntimeException ex) {
            hooks.logWarn("FlipHub accountwide backfill failed", ex);
            shouldRetry = true;
        }
        return new Result(shouldRetry);
    }
}
