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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AccountwideFlipHistoryServiceTest {
    @Test
    public void buildAccountwideHistoryPrefersPerProfileHistoryWhenProfilesPresent() {
        TestHooks hooks = new TestHooks();
        hooks.profileKeys.addAll(Arrays.asList(0L, 1L, 2L));
        hooks.setHistory(0L, history(entry(4151, 100L), entry(4151, 150L)));
        hooks.setHistory(1L, history(entry(4151, 200L)));
        hooks.setHistory(2L, history(entry(4151, 300L)));

        AccountwideFlipHistoryService service = new AccountwideFlipHistoryService(hooks);

        Map<Integer, List<StatsFlipInstance>> result = service.buildAccountwideHistory(null);

        assertEquals(2, result.get(4151).size());
        assertEquals(300L, result.get(4151).get(0).completionTsMs);
        assertEquals(200L, result.get(4151).get(1).completionTsMs);
        assertEquals(3, hooks.ensuredProfiles.size());
        assertEquals(new HashSet<>(Arrays.asList(0L, 1L, 2L)), new HashSet<>(hooks.ensuredProfiles));
    }

    @Test
    public void buildAccountwideHistoryFallsBackToAccountwideHistoryWhenProfilesProduceNoEntries() {
        TestHooks hooks = new TestHooks();
        hooks.profileKeys.add(null);
        hooks.profileKeys.add(-1L);
        hooks.profileKeys.add(0L);
        hooks.profileKeys.add(1L);
        hooks.profileKeys.add(2L);
        hooks.setHistory(0L, history(entry(4151, 500L)));

        hooks.setHistory(1L, Collections.emptyMap());
        hooks.setHistory(2L, Collections.emptyMap());

        AccountwideFlipHistoryService service = new AccountwideFlipHistoryService(hooks);

        Map<Integer, List<StatsFlipInstance>> result = service.buildAccountwideHistory(null);

        assertEquals(1, result.get(4151).size());
        assertEquals(500L, result.get(4151).get(0).completionTsMs);
        assertEquals(3, hooks.ensuredProfiles.size());
        assertEquals(new HashSet<>(Arrays.asList(0L, 1L, 2L)), new HashSet<>(hooks.ensuredProfiles));
    }

    @Test
    public void buildAccountwideHistoryMergesProfilesWhenAccountwideHistoryIsEmpty() {
        TestHooks hooks = new TestHooks();
        hooks.profileKeys.add(null);
        hooks.profileKeys.add(-1L);
        hooks.profileKeys.add(0L);
        hooks.profileKeys.add(1L);
        hooks.profileKeys.add(2L);
        hooks.setHistory(0L, Collections.emptyMap());
        hooks.setHistory(1L, history(entry(4151, 400L)));
        hooks.setHistory(2L, history(entry(4151, 450L)));

        AccountwideFlipHistoryService service = new AccountwideFlipHistoryService(hooks);

        Map<Integer, List<StatsFlipInstance>> result = service.buildAccountwideHistory(null);

        assertEquals(2, result.get(4151).size());
        assertEquals(450L, result.get(4151).get(0).completionTsMs);
        assertEquals(400L, result.get(4151).get(1).completionTsMs);
        assertEquals(3, hooks.ensuredProfiles.size());
        assertEquals(new HashSet<>(Arrays.asList(0L, 1L, 2L)), new HashSet<>(hooks.ensuredProfiles));
    }

    @Test
    public void buildAccountwideHistoryAvoidsAccountwideSlotCollisionByUsingPerProfileEntries() {
        TestHooks hooks = new TestHooks();
        hooks.profileKeys.addAll(Arrays.asList(0L, 10L, 20L));
        // Simulate collapsed accountwide history for same item (slot collision).
        hooks.setHistory(0L, history(entry(22449, 500L)));
        hooks.setHistory(10L, history(entry(22449, 300L)));
        hooks.setHistory(20L, history(entry(22449, 400L)));

        AccountwideFlipHistoryService service = new AccountwideFlipHistoryService(hooks);

        Map<Integer, List<StatsFlipInstance>> result = service.buildAccountwideHistory(null);

        assertEquals(2, result.get(22449).size());
        assertEquals(400L, result.get(22449).get(0).completionTsMs);
        assertEquals(300L, result.get(22449).get(1).completionTsMs);
    }

    @Test
    public void buildAccountwideHistoryReturnsEmptyWhenNoProfileKeys() {
        TestHooks hooks = new TestHooks();
        hooks.profileKeys = null;
        hooks.setHistory(0L, Collections.emptyMap());
        AccountwideFlipHistoryService service = new AccountwideFlipHistoryService(hooks);

        Map<Integer, List<StatsFlipInstance>> result = service.buildAccountwideHistory(null);

        assertTrue(result.isEmpty());
        assertEquals(Arrays.asList(0L), hooks.ensuredProfiles);
    }

    @Test
    public void buildAccountwideHistoryMergesAndSortsPerProfileEntries() {
        TestHooks hooks = new TestHooks();
        hooks.profileKeys.addAll(Arrays.asList(0L, 11L, 22L));
        hooks.setHistory(0L, Collections.emptyMap());

        Map<Integer, List<StatsFlipInstance>> profileOne = new HashMap<>();
        profileOne.put(526, new ArrayList<>(Arrays.asList(entry(526, 100L), entry(526, 300L))));
        hooks.setHistory(11L, profileOne);

        Map<Integer, List<StatsFlipInstance>> profileTwo = new HashMap<>();
        profileTwo.put(526, new ArrayList<>(Collections.singletonList(entry(526, 200L))));
        profileTwo.put(4151, new ArrayList<>(Collections.singletonList(entry(4151, 50L))));
        hooks.setHistory(22L, profileTwo);

        AccountwideFlipHistoryService service = new AccountwideFlipHistoryService(hooks);

        Map<Integer, List<StatsFlipInstance>> result = service.buildAccountwideHistory(null);

        assertEquals(3, result.get(526).size());
        assertEquals(300L, result.get(526).get(0).completionTsMs);
        assertEquals(200L, result.get(526).get(1).completionTsMs);
        assertEquals(100L, result.get(526).get(2).completionTsMs);
        assertEquals(1, result.get(4151).size());
        assertEquals(50L, result.get(4151).get(0).completionTsMs);
        assertEquals(3, hooks.ensuredProfiles.size());
        assertEquals(new HashSet<>(Arrays.asList(0L, 11L, 22L)), new HashSet<>(hooks.ensuredProfiles));
    }

    private static Map<Integer, List<StatsFlipInstance>> history(StatsFlipInstance... entries) {
        Map<Integer, List<StatsFlipInstance>> history = new HashMap<>();
        if (entries == null) {
            return history;
        }
        for (StatsFlipInstance entry : entries) {
            history.computeIfAbsent(entry.itemId, ignored -> new ArrayList<>()).add(entry);
        }
        return history;
    }

    private static StatsFlipInstance entry(int itemId, long completionTsMs) {
        return new StatsFlipInstance(itemId, 100, 120, 100, 120, 20, 1, completionTsMs);
    }

    private static final class TestHooks implements AccountwideFlipHistoryService.Hooks {
        private final Map<Long, List<LocalTradeDelta>> snapshotsByAccount = new HashMap<>();
        private final IdentityHashMap<List<LocalTradeDelta>, Map<Integer, List<StatsFlipInstance>>> historyBySnapshot =
            new IdentityHashMap<>();
        private final List<Long> ensuredProfiles = new ArrayList<>();
        private Set<Long> profileKeys = new HashSet<>();

        private void setHistory(long accountKey, Map<Integer, List<StatsFlipInstance>> history) {
            List<LocalTradeDelta> snapshot = snapshotsByAccount.computeIfAbsent(accountKey, ignored -> new ArrayList<>());
            historyBySnapshot.put(snapshot, history != null ? history : new HashMap<>());
        }

        @Override
        public long accountwideKey() {
            return 0L;
        }

        @Override
        public void ensureProfileLoaded(long accountKey) {
            ensuredProfiles.add(accountKey);
        }

        @Override
        public List<LocalTradeDelta> snapshotLocalTradeDeltas(long accountKey) {
            return snapshotsByAccount.computeIfAbsent(accountKey, ignored -> new ArrayList<>());
        }

        @Override
        public Map<Integer, List<StatsFlipInstance>> buildLocalHistory(List<LocalTradeDelta> deltas, Long sinceMs) {
            Map<Integer, List<StatsFlipInstance>> source = historyBySnapshot.get(deltas);
            Map<Integer, List<StatsFlipInstance>> copy = new HashMap<>();
            if (source == null) {
                return copy;
            }
            for (Map.Entry<Integer, List<StatsFlipInstance>> entry : source.entrySet()) {
                copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            return copy;
        }

        @Override
        public Set<Long> collectAccountwideProfileKeys() {
            return profileKeys != null ? new HashSet<>(profileKeys) : null;
        }
    }
}
