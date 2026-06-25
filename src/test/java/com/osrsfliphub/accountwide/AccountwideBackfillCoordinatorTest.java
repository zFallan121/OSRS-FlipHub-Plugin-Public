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
import java.util.Collections;
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

public class AccountwideBackfillCoordinatorTest {
    @Test
    public void runCycleBackfillsMissingProfilesAndPersistsResult() {
        TestHooks hooks = new TestHooks();
        hooks.profileKeys.addAll(Arrays.asList(1L, 2L));
        hooks.alreadyBackfilled.add(1L);
        hooks.localSummaries.put(1L, summary(100L, 1_000L, 20L, 1));
        hooks.localSummaries.put(2L, summary(200L, 2_000L, 40L, 2));
        hooks.remoteResponse = remoteSummary(100L, 1_000L, 20L, 1);
        hooks.inferredSynced.add(1L);
        hooks.backfillResults.put(2L, true);

        AccountwideBackfillCoordinator coordinator = new AccountwideBackfillCoordinator(16, hooks);

        AccountwideBackfillCoordinator.Result result = coordinator.runCycle();

        assertNotNull(result);
        assertFalse(result.shouldRetry);
        assertEquals(Arrays.asList(1L, 2L), hooks.ensuredProfiles);
        assertEquals(Arrays.asList(2L), hooks.backfillAttempts);
        assertEquals(new HashSet<>(Arrays.asList(1L, 2L)), hooks.persistedKeys);
        assertEquals(1, hooks.persistCalls);
        assertEquals(1, hooks.statsRefreshCalls);
        assertEquals(1, hooks.panelRefreshCalls);
        assertEquals(1, hooks.resetCalls);
    }

    @Test
    public void runCycleReturnsRetryWhenRemoteSummaryMissing() {
        TestHooks hooks = new TestHooks();
        hooks.profileKeys.add(42L);
        hooks.localSummaries.put(42L, summary(100L, 1_000L, 20L, 1));
        hooks.remoteResponse = null;

        AccountwideBackfillCoordinator coordinator = new AccountwideBackfillCoordinator(16, hooks);

        AccountwideBackfillCoordinator.Result result = coordinator.runCycle();

        assertTrue(result.shouldRetry);
        assertEquals(0, hooks.resetCalls);
        assertEquals(0, hooks.persistCalls);
    }

    @Test
    public void runCycleReturnsRetryWhenBackfillUploadFails() {
        TestHooks hooks = new TestHooks();
        hooks.profileKeys.add(7L);
        hooks.localSummaries.put(7L, summary(100L, 1_000L, 20L, 1));
        hooks.remoteResponse = remoteSummary(0L, 0L, 0L, 0);
        hooks.inferredSynced.clear();
        hooks.backfillResults.put(7L, false);

        AccountwideBackfillCoordinator coordinator = new AccountwideBackfillCoordinator(16, hooks);

        AccountwideBackfillCoordinator.Result result = coordinator.runCycle();

        assertTrue(result.shouldRetry);
        assertEquals(Arrays.asList(7L), hooks.backfillAttempts);
        assertEquals(0, hooks.persistCalls);
        assertEquals(0, hooks.statsRefreshCalls);
        assertEquals(0, hooks.panelRefreshCalls);
    }

    @Test
    public void runCycleReturnsRetryWhenCollectorThrowsRuntimeException() {
        TestHooks hooks = new TestHooks();
        hooks.throwOnCollectProfileKeys = true;
        AccountwideBackfillCoordinator coordinator = new AccountwideBackfillCoordinator(16, hooks);

        AccountwideBackfillCoordinator.Result result = coordinator.runCycle();

        assertTrue(result.shouldRetry);
        assertEquals(1, hooks.logWarnWithThrowableCalls);
        assertEquals(0, hooks.resetCalls);
    }

    @Test
    public void runCycleHandlesImmutableHookSets() {
        TestHooks hooks = new TestHooks();
        hooks.profileKeys.addAll(Arrays.asList(1L, 2L));
        hooks.alreadyBackfilled.add(1L);
        hooks.localSummaries.put(1L, summary(100L, 1_000L, 20L, 1));
        hooks.localSummaries.put(2L, summary(200L, 2_000L, 40L, 2));
        hooks.remoteResponse = remoteSummary(100L, 1_000L, 20L, 1);
        hooks.inferredSynced.add(1L);
        hooks.backfillResults.put(2L, true);
        hooks.returnImmutableProfileKeys = true;
        hooks.returnImmutableBackfilledKeys = true;

        AccountwideBackfillCoordinator coordinator = new AccountwideBackfillCoordinator(16, hooks);

        AccountwideBackfillCoordinator.Result result = coordinator.runCycle();

        assertNotNull(result);
        assertFalse(result.shouldRetry);
        assertEquals(Arrays.asList(2L), hooks.backfillAttempts);
        assertEquals(new HashSet<>(Arrays.asList(1L, 2L)), hooks.persistedKeys);
        assertEquals(1, hooks.persistCalls);
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

    private static final class TestHooks implements AccountwideBackfillCoordinator.Hooks {
        private final Set<Long> profileKeys = new HashSet<>();
        private final Set<Long> alreadyBackfilled = new HashSet<>();
        private final Map<Long, StatsSummary> localSummaries = new HashMap<>();
        private final Set<Long> inferredSynced = new HashSet<>();
        private final Map<Long, Boolean> backfillResults = new HashMap<>();
        private final List<Long> ensuredProfiles = new ArrayList<>();
        private final List<Long> backfillAttempts = new ArrayList<>();
        private Set<Long> persistedKeys = new HashSet<>();
        private ApiClient.StatsSummaryResponse remoteResponse;
        private boolean returnImmutableProfileKeys;
        private boolean returnImmutableBackfilledKeys;
        private boolean throwOnCollectProfileKeys;
        private int persistCalls;
        private int statsRefreshCalls;
        private int panelRefreshCalls;
        private int resetCalls;
        private int logWarnWithThrowableCalls;

        @Override
        public Set<Long> collectAccountwideProfileKeys() {
            if (throwOnCollectProfileKeys) {
                throw new IllegalStateException("collect failed");
            }
            Set<Long> copy = new HashSet<>(profileKeys);
            return returnImmutableProfileKeys ? Collections.unmodifiableSet(copy) : copy;
        }

        @Override
        public Set<Long> loadBackfilledProfileKeys() {
            Set<Long> copy = new HashSet<>(alreadyBackfilled);
            return returnImmutableBackfilledKeys ? Collections.unmodifiableSet(copy) : copy;
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
            return "token";
        }

        @Override
        public ApiClient.StatsSummaryResponse fetchRemoteStatsSummary(String token) {
            return remoteResponse;
        }

        @Override
        public Set<Long> inferLikelySyncedProfiles(Set<Long> profileKeys,
                                                   Map<Long, StatsSummary> localSummaries,
                                                   StatsSummary remoteSummary) {
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
            logWarnWithThrowableCalls++;
        }
    }
}
