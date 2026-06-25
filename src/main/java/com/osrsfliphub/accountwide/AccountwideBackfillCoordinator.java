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

final class AccountwideBackfillCoordinator {
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

    AccountwideBackfillCoordinator(int maxBackfillProfileCount, Hooks hooks) {
        this.maxBackfillProfileCount = Math.max(1, maxBackfillProfileCount);
        this.hooks = hooks;
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
