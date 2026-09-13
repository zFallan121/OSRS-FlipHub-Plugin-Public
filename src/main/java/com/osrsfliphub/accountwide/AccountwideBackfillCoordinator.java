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
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
final class AccountwideBackfillCoordinator {

    static final class Result {
        final boolean shouldRetry;

        Result(boolean shouldRetry) {
            this.shouldRetry = shouldRetry;
        }
    }

    private final int maxBackfillProfileCount =
        Math.max(1, Const.MAX_BACKFILL_PROFILE_COUNT);
    private final ApiClient apiClient;
    private final PluginConfig config;
    private final PluginState state;

    @Inject
    AccountwideBackfillCoordinator(ApiClient apiClient, PluginConfig config, PluginState state) {
        this.apiClient = apiClient;
        this.config = config;
        this.state = state;
    }

    private static BackfilledProfilesStore backfilledProfilesStore() {
        return Bridge.get(BackfilledProfilesStore.class);
    }

    private Set<Long> collectAccountwideProfileKeys() {
        ProfileKeyCollector collector = Bridge.get(ProfileKeyCollector.class);
        ProfileStorage storage = Bridge.get(ProfileStorage.class);
        ProfileSelectionPresentation profileSelection =
            Bridge.get(ProfileSelectionPresentation.class);
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

    private Set<Long> loadBackfilledProfileKeys() {
        BackfilledProfilesStore store = backfilledProfilesStore();
        return store != null ? store.load() : null;
    }

    private void ensureProfileLoaded(long key) {
        Access.plugin().getLocalTradesRuntimeService().ensureProfileLoaded(key);
    }

    private StatsSnapshot buildLocalStatsSnapshot(long key) {
        LocalStatsSnapshotService service = Bridge.get(LocalStatsSnapshotService.class);
        return service != null ? service.buildSnapshot(key, null, StatsItemSort.COMPLETION) : null;
    }

    private ApiClient.StatsSummaryResponse fetchRemoteStatsSummary(String token) {
        GeLifecyclePlugin plugin = Access.plugin();
        SessionRefresh sessionRefresh = Bridge.get(SessionRefresh.class);
        return plugin.runtimeUtilityServices.fetchRemoteStatsSummary(
            apiClient, config, sessionRefresh, token, null, true);
    }

    private Set<Long> inferLikelySyncedProfiles(Set<Long> profileKeys,
                                                Map<Long, StatsSummary> localSummaries,
                                                StatsSummary remoteSummary) {
        BackfillSyncMatcher matcher = Bridge.get(BackfillSyncMatcher.class);
        return matcher != null
            ? matcher.inferLikelySyncedProfiles(profileKeys, localSummaries, remoteSummary) : null;
    }

    private BackfillUploader.Outcome backfillProfileTrades(long profileKey) {
        ProfileBackfill runner = Bridge.get(ProfileBackfill.class);
        BackfillUploader uploader = Bridge.get(BackfillUploader.class);
        if (runner == null || apiClient == null || config == null || uploader == null) {
            return BackfillUploader.Outcome.RETRY;
        }
        return runner.backfillProfileTrades(profileKey, apiClient, config, uploader);
    }

    private void persistBackfilledProfileKeys(Set<Long> keys) {
        BackfilledProfilesStore store = backfilledProfilesStore();
        if (store != null) {
            store.persist(keys);
        }
    }

    private void triggerRefreshes() {
        GeLifecyclePlugin plugin = Access.plugin();
        PanelRefresh coordinator = plugin.getPanelRefreshCoordinator();
        if (coordinator != null) {
            coordinator.triggerStatsRefresh(plugin.scheduler);
            coordinator.triggerPanelRefresh(plugin.scheduler);
        }
    }

    private void resetBackfillRetryState() {
        UploadBackfillDispatch service = Bridge.get(UploadBackfillDispatch.class);
        if (service != null) {
            service.resetBackfillRetryState();
        }
    }

    private void logWarn(String message) {
        if (message != null) {
            log.warn(message);
        }
    }

    private void logWarn(String message, Throwable error) {
        if (message == null) {
            return;
        }
        if (error != null) {
            log.warn(message, error);
        } else {
            log.warn(message);
        }
    }

    Result runCycle() {
        boolean shouldRetry = false;
        try {
            if (config == null || !config.enableFlipHubSync()) {
                resetBackfillRetryState();
                return new Result(false);
            }
            Set<Long> collectedProfileKeys = collectAccountwideProfileKeys();
            if (collectedProfileKeys == null) {
                resetBackfillRetryState();
                return new Result(false);
            }
            Set<Long> profileKeys = new HashSet<>(collectedProfileKeys);
            profileKeys.removeIf(key -> key == null || key <= 0);
            if (profileKeys.isEmpty()) {
                resetBackfillRetryState();
                return new Result(false);
            }
            if (profileKeys.size() > maxBackfillProfileCount) {
                logWarn(
                    "FlipHub backfill skipped: " + profileKeys.size() + " profiles exceeds safe cap "
                        + maxBackfillProfileCount
                );
                resetBackfillRetryState();
                return new Result(false);
            }

            Set<Long> loadedBackfilled = loadBackfilledProfileKeys();
            if (loadedBackfilled != null && loadedBackfilled.containsAll(profileKeys)) {
                resetBackfillRetryState();
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
                ensureProfileLoaded(key);
                StatsSnapshot snapshot = buildLocalStatsSnapshot(key);
                localSummaries.put(key, snapshot != null && snapshot.summary != null ? snapshot.summary : new StatsSummary());
            }

            String token = (config != null ? config.sessionToken() : null);
            ApiClient.StatsSummaryResponse remoteResponse = fetchRemoteStatsSummary(token);
            if (remoteResponse == null || remoteResponse.summary == null) {
                return new Result(true);
            }

            Set<Long> likelySyncedProfiles = inferLikelySyncedProfiles(
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
                resetBackfillRetryState();
                return new Result(false);
            }

            boolean changed = false;
            boolean fullySynced = true;
            for (Long profileKey : missingProfiles) {
                if (profileKey == null || profileKey <= 0) {
                    continue;
                }
                BackfillUploader.Outcome outcome = backfillProfileTrades(profileKey);
                if (outcome == BackfillUploader.Outcome.RETRY) {
                    logWarn("FlipHub backfill paused: profile " + profileKey + " will be retried");
                    fullySynced = false;
                    break;
                }
                if (outcome == BackfillUploader.Outcome.SESSION_DEAD) {
                    // Nothing was said about this profile, only about the link. Marking it done
                    // would mean its remaining trades were never uploaded after the relink.
                    logWarn("FlipHub backfill stopped: the session was rejected, relink to resume");
                    fullySynced = false;
                    break;
                }
                if (outcome == BackfillUploader.Outcome.TERMINAL) {
                    // The server will not take this profile's events. Retrying every ninety
                    // seconds for the rest of the session would achieve nothing, so it is
                    // marked done and the remaining profiles still get their turn.
                    logWarn("FlipHub backfill gave up on profile " + profileKey
                        + ": the server refused its events");
                }
                alreadyBackfilled.add(profileKey);
                changed = true;
            }
            if (changed) {
                persistBackfilledProfileKeys(alreadyBackfilled);
                triggerRefreshes();
            }
            if (fullySynced) {
                resetBackfillRetryState();
            } else {
                shouldRetry = true;
            }
        } catch (RuntimeException ex) {
            logWarn("FlipHub accountwide backfill failed", ex);
            shouldRetry = true;
        }
        return new Result(shouldRetry);
    }
}
