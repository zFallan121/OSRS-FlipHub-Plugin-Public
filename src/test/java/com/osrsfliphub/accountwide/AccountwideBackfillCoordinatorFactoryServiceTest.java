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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AccountwideBackfillCoordinatorFactoryServiceTest {
    @Test
    public void createBuildsCoordinatorThatDelegatesToRuntimeHooks() {
        AccountwideBackfillCoordinatorFactoryService factory =
            new AccountwideBackfillCoordinatorFactoryService(16);
        TestRuntimeHooks hooks = new TestRuntimeHooks();
        hooks.profileKeys.addAll(Arrays.asList(1L, 2L));
        hooks.alreadyBackfilled.add(1L);
        hooks.localSummaries.put(1L, summary(100L, 1_000L, 20L, 1));
        hooks.localSummaries.put(2L, summary(200L, 2_000L, 40L, 2));
        hooks.remoteResponse = remoteSummary(100L, 1_000L, 20L, 1);
        hooks.inferredSynced.add(1L);
        hooks.backfillResults.put(2L, true);

        AccountwideBackfillCoordinator coordinator = factory.create(hooks);
        AccountwideBackfillCoordinator.Result result = coordinator.runCycle();

        assertNotNull(result);
        assertFalse(result.shouldRetry);
        assertEquals(new HashSet<>(Arrays.asList(1L, 2L)), new HashSet<>(hooks.ensuredProfiles));
        assertEquals(Arrays.asList(2L), hooks.backfillAttempts);
        assertEquals(new HashSet<>(Arrays.asList(1L, 2L)), hooks.persistedKeys);
        assertEquals(1, hooks.persistCalls);
        assertEquals(1, hooks.statsRefreshCalls);
        assertEquals(1, hooks.panelRefreshCalls);
        assertEquals(1, hooks.resetCalls);
        assertEquals(1, hooks.collectProfileKeysCalls);
        assertEquals(1, hooks.loadBackfilledCalls);
        assertEquals(1, hooks.getSessionTokenCalls);
        assertEquals(1, hooks.fetchRemoteSummaryCalls);
        assertEquals(1, hooks.inferLikelySyncedCalls);
    }

    @Test
    public void createWithNullRuntimeHooksReturnsNoopCoordinator() {
        AccountwideBackfillCoordinatorFactoryService factory =
            new AccountwideBackfillCoordinatorFactoryService(16);
        AccountwideBackfillCoordinator coordinator = factory.create(null);

        AccountwideBackfillCoordinator.Result result = coordinator.runCycle();

        assertNotNull(result);
        assertFalse(result.shouldRetry);
    }

    private static StatsSummary summary(long profit, long cost, long tax, int flips) {
        StatsSummary summary = new StatsSummary();
        summary.total_profit_gp = profit;
        summary.total_cost_gp = cost;
        summary.tax_paid_gp = tax;
        summary.fill_count = flips;
        return summary;
    }

    private static ApiClient.StatsSummaryResponse remoteSummary(long profit, long cost, long tax, int flips) {
        ApiClient.StatsSummaryResponse response = new ApiClient.StatsSummaryResponse();
        response.summary = summary(profit, cost, tax, flips);
        return response;
    }

    private static final class TestRuntimeHooks implements AccountwideBackfillCoordinatorFactoryService.RuntimeHooks {
        private final Set<Long> profileKeys = new HashSet<>();
        private final Set<Long> alreadyBackfilled = new HashSet<>();
        private final Map<Long, StatsSummary> localSummaries = new HashMap<>();
        private final Set<Long> inferredSynced = new HashSet<>();
        private final Map<Long, Boolean> backfillResults = new HashMap<>();
        private final List<Long> ensuredProfiles = new ArrayList<>();
        private final List<Long> backfillAttempts = new ArrayList<>();
        private Set<Long> persistedKeys = new HashSet<>();
        private ApiClient.StatsSummaryResponse remoteResponse;
        private int collectProfileKeysCalls;
        private int loadBackfilledCalls;
        private int getSessionTokenCalls;
        private int fetchRemoteSummaryCalls;
        private int inferLikelySyncedCalls;
        private int persistCalls;
        private int statsRefreshCalls;
        private int panelRefreshCalls;
        private int resetCalls;

        @Override
        public Set<Long> collectAccountwideProfileKeys() {
            collectProfileKeysCalls++;
            return new HashSet<>(profileKeys);
        }

        @Override
        public Set<Long> loadBackfilledProfileKeys() {
            loadBackfilledCalls++;
            return new HashSet<>(alreadyBackfilled);
        }

        @Override
        public void ensureProfileLoaded(long key) {
            ensuredProfiles.add(key);
        }

        @Override
        public LocalStatsSnapshot buildLocalStatsSnapshot(long key) {
            StatsSummary summary = localSummaries.getOrDefault(key, new StatsSummary());
            return new LocalStatsSnapshot(summary, new ArrayList<>());
        }

        @Override
        public String getSessionToken() {
            getSessionTokenCalls++;
            return "token";
        }

        @Override
        public ApiClient.StatsSummaryResponse fetchRemoteStatsSummary(String token) {
            fetchRemoteSummaryCalls++;
            return remoteResponse;
        }

        @Override
        public Set<Long> inferLikelySyncedProfiles(Set<Long> profileKeys,
                                                   Map<Long, StatsSummary> localSummaries,
                                                   StatsSummary remoteSummary) {
            inferLikelySyncedCalls++;
            return new HashSet<>(inferredSynced);
        }

        @Override
        public boolean backfillProfileTrades(long profileKey) {
            backfillAttempts.add(profileKey);
            return backfillResults.getOrDefault(profileKey, true);
        }

        @Override
        public void persistBackfilledProfileKeys(Set<Long> keys) {
            persistCalls++;
            persistedKeys = keys != null ? new HashSet<>(keys) : new HashSet<>();
        }

        @Override
        public void triggerStatsRefresh() {
            statsRefreshCalls++;
        }

        @Override
        public void triggerPanelRefresh() {
            panelRefreshCalls++;
        }

        @Override
        public void resetBackfillRetryState() {
            resetCalls++;
        }

        @Override
        public void logWarn(String message) {
        }

        @Override
        public void logWarn(String message, Throwable error) {
        }
    }
}
