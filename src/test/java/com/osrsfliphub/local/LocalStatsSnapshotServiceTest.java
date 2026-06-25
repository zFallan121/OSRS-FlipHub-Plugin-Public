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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LocalStatsSnapshotServiceTest {
    @Test
    public void buildSnapshotForProfileSortsAndHydratesItemNames() {
        TestHooks hooks = new TestHooks();
        hooks.cachesByAccount.put(1L, buildCache(
            buy(1_000L, 1, 100, 1, 100),
            sell(2_000L, 1, 100, 1, 120),
            buy(1_500L, 2, 200, 1, 100),
            sell(3_000L, 2, 200, 1, 130)
        ));
        hooks.cachedNames.put(100, "Rune knife");

        LocalStatsSnapshotService service = new LocalStatsSnapshotService(hooks);

        LocalStatsSnapshot snapshot = service.buildSnapshot(1L, null, StatsItemSort.COMPLETION);

        assertNotNull(snapshot);
        assertEquals(Arrays.asList(1L), hooks.ensuredProfiles);
        assertEquals(2, snapshot.items.size());
        assertEquals(200, snapshot.items.get(0).item_id);
        assertEquals(100, snapshot.items.get(1).item_id);
        assertEquals("Rune knife", snapshot.items.get(1).item_name);
        assertTrue(hooks.nameLookupRequests.contains(200));
        assertEquals(0, hooks.accountwideFallbackCalls);
    }

    @Test
    public void buildSnapshotForProfileSinceUsesWindowedSnapshot() {
        TestHooks hooks = new TestHooks();
        hooks.cachesByAccount.put(1L, buildCache(
            buy(1_000L, 1, 100, 1, 100),
            sell(2_000L, 1, 100, 1, 120),
            buy(1_500L, 2, 200, 1, 100),
            sell(3_000L, 2, 200, 1, 130)
        ));
        LocalStatsSnapshotService service = new LocalStatsSnapshotService(hooks);

        LocalStatsSnapshot snapshot = service.buildSnapshot(1L, 2_500L, StatsItemSort.COMPLETION);

        assertNotNull(snapshot);
        assertEquals(1, snapshot.items.size());
        assertEquals(200, snapshot.items.get(0).item_id);
    }

    @Test
    public void buildSnapshotForAccountwideUsesLocalWhenMeaningful() {
        TestHooks hooks = new TestHooks();
        hooks.cachesByAccount.put(0L, buildCache(
            buy(1_000L, 1, 4151, 1, 100),
            sell(2_000L, 1, 4151, 1, 120)
        ));
        hooks.fallbackSnapshot = withProfit(999L);

        LocalStatsSnapshotService service = new LocalStatsSnapshotService(hooks);

        LocalStatsSnapshot snapshot = service.buildSnapshot(0L, null, StatsItemSort.COMPLETION);

        assertNotNull(snapshot);
        assertEquals(0, hooks.accountwideFallbackCalls);
        assertTrue(snapshot.summary.total_profit_gp != null && snapshot.summary.total_profit_gp > 0);
    }

    @Test
    public void buildSnapshotForAccountwideFallsBackWhenLocalIsEmpty() {
        TestHooks hooks = new TestHooks();
        hooks.cachesByAccount.put(0L, buildCache());
        hooks.profileKeys.addAll(Arrays.asList(1L, 2L));
        hooks.fallbackSnapshot = withProfit(777L);

        LocalStatsSnapshotService service = new LocalStatsSnapshotService(hooks);

        LocalStatsSnapshot snapshot = service.buildSnapshot(0L, null, StatsItemSort.COMPLETION);

        assertNotNull(snapshot);
        assertEquals(1, hooks.accountwideFallbackCalls);
        assertEquals(new HashSet<>(Arrays.asList(1L, 2L)), hooks.lastFallbackKeys);
        assertEquals(Long.valueOf(777L), snapshot.summary.total_profit_gp);
    }

    private static LocalStatsCache buildCache(LocalTradeDelta... deltas) {
        LocalStatsCache cache = new LocalStatsCache();
        List<LocalTradeDelta> data = deltas != null ? Arrays.asList(deltas) : Collections.emptyList();
        cache.rebuild(data);
        return cache;
    }

    private static LocalTradeDelta buy(long tsMs, int slot, int itemId, int qty, int price) {
        return new LocalTradeDelta(tsMs, slot, itemId, true, qty, (long) qty * price, "OFFER_UPDATED", price, false);
    }

    private static LocalTradeDelta sell(long tsMs, int slot, int itemId, int qty, int price) {
        return new LocalTradeDelta(tsMs, slot, itemId, false, qty, (long) qty * price, "OFFER_COMPLETED", price, false);
    }

    private static LocalStatsSnapshot withProfit(long profit) {
        StatsSummary summary = new StatsSummary();
        summary.total_profit_gp = profit;
        List<StatsItem> items = new ArrayList<>();
        StatsItem item = new StatsItem();
        item.item_id = 4151;
        item.total_profit_gp = profit;
        item.total_cost_gp = 100L;
        item.last_sell_ts_ms = 2_000L;
        items.add(item);
        return new LocalStatsSnapshot(summary, items);
    }

    private static final class TestHooks implements LocalStatsSnapshotService.Hooks {
        private final Map<Long, LocalStatsCache> cachesByAccount = new HashMap<>();
        private final Map<Integer, String> cachedNames = new HashMap<>();
        private final Set<Integer> nameLookupRequests = new HashSet<>();
        private final Set<Long> profileKeys = new HashSet<>();
        private final List<Long> ensuredProfiles = new ArrayList<>();
        private int accountwideFallbackCalls;
        private Set<Long> lastFallbackKeys = new HashSet<>();
        private LocalStatsSnapshot fallbackSnapshot = new LocalStatsSnapshot(new StatsSummary(), new ArrayList<>());

        @Override
        public long accountwideKey() {
            return 0L;
        }

        @Override
        public void ensureProfileLoaded(long accountKey) {
            ensuredProfiles.add(accountKey);
        }

        @Override
        public LocalStatsCache getOrBuildStatsCache(long accountKey) {
            return cachesByAccount.get(accountKey);
        }

        @Override
        public String getCachedItemName(int itemId) {
            return cachedNames.get(itemId);
        }

        @Override
        public void cacheItemName(int itemId) {
            nameLookupRequests.add(itemId);
        }

        @Override
        public Set<Long> collectAccountwideProfileKeys() {
            return new HashSet<>(profileKeys);
        }

        @Override
        public LocalStatsSnapshot buildAccountwideFromProfiles(Set<Long> profileKeys, Long sinceMs, StatsItemSort sort) {
            accountwideFallbackCalls++;
            lastFallbackKeys = profileKeys != null ? new HashSet<>(profileKeys) : new HashSet<>();
            return fallbackSnapshot;
        }
    }
}
