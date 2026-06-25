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

import java.util.Map;
import java.util.Set;

final class AccountwideBackfillCoordinatorFactoryService {
    interface RuntimeHooks {
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

    private final int maxBackfillProfileCount;

    AccountwideBackfillCoordinatorFactoryService(int maxBackfillProfileCount) {
        this.maxBackfillProfileCount = maxBackfillProfileCount;
    }

    AccountwideBackfillCoordinator create(RuntimeHooks runtimeHooks) {
        if (runtimeHooks == null) {
            return new AccountwideBackfillCoordinator(maxBackfillProfileCount, null);
        }
        return new AccountwideBackfillCoordinator(
            maxBackfillProfileCount,
            new AccountwideBackfillCoordinator.Hooks() {
                @Override
                public Set<Long> collectAccountwideProfileKeys() {
                    return runtimeHooks.collectAccountwideProfileKeys();
                }

                @Override
                public Set<Long> loadBackfilledProfileKeys() {
                    return runtimeHooks.loadBackfilledProfileKeys();
                }

                @Override
                public void ensureProfileLoaded(long key) {
                    runtimeHooks.ensureProfileLoaded(key);
                }

                @Override
                public LocalStatsSnapshot buildLocalStatsSnapshot(long key) {
                    return runtimeHooks.buildLocalStatsSnapshot(key);
                }

                @Override
                public String getSessionToken() {
                    return runtimeHooks.getSessionToken();
                }

                @Override
                public ApiClient.StatsSummaryResponse fetchRemoteStatsSummary(String token) {
                    return runtimeHooks.fetchRemoteStatsSummary(token);
                }

                @Override
                public Set<Long> inferLikelySyncedProfiles(Set<Long> profileKeys,
                                                           Map<Long, StatsSummary> localSummaries,
                                                           StatsSummary remoteSummary) {
                    return runtimeHooks.inferLikelySyncedProfiles(profileKeys, localSummaries, remoteSummary);
                }

                @Override
                public boolean backfillProfileTrades(long profileKey) {
                    return runtimeHooks.backfillProfileTrades(profileKey);
                }

                @Override
                public void persistBackfilledProfileKeys(Set<Long> keys) {
                    runtimeHooks.persistBackfilledProfileKeys(keys);
                }

                @Override
                public void triggerStatsRefresh() {
                    runtimeHooks.triggerStatsRefresh();
                }

                @Override
                public void triggerPanelRefresh() {
                    runtimeHooks.triggerPanelRefresh();
                }

                @Override
                public void resetBackfillRetryState() {
                    runtimeHooks.resetBackfillRetryState();
                }

                @Override
                public void logWarn(String message) {
                    runtimeHooks.logWarn(message);
                }

                @Override
                public void logWarn(String message, Throwable error) {
                    runtimeHooks.logWarn(message, error);
                }
            }
        );
    }
}
