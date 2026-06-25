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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class LocalStatsViewServiceTest {
    @Test
    public void buildReturnsEmptyResultWhenNoSelectedProfile() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 12_345L;
        hooks.selectedProfileKey = -1L;

        LocalStatsViewService service = new LocalStatsViewService(hooks);
        LocalStatsViewService.Result result = service.build();

        assertTrue(hooks.ensureLoadedCalled);
        assertEquals(12_345L, result.asOfMs);
        assertNotNull(result.summary);
        assertNotNull(result.items);
        assertNotNull(result.flipHistory);
        assertTrue(result.items.isEmpty());
        assertTrue(result.flipHistory.isEmpty());
    }

    @Test
    public void buildUsesSessionRangeSortAndDelegatesSnapshotAndHistory() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 10_000L;
        hooks.range = StatsRange.SESSION;
        hooks.sort = StatsItemSort.ROI;
        hooks.selectedProfileKey = 123L;
        hooks.sessionStartMs = 5_000L;

        StatsSummary summary = new StatsSummary();
        summary.total_profit_gp = 100L;
        List<StatsItem> items = new ArrayList<>();
        StatsItem item = new StatsItem();
        item.item_id = 561;
        items.add(item);
        hooks.snapshot = new LocalStatsSnapshot(summary, items);
        Map<Integer, List<StatsFlipInstance>> history = new HashMap<>();
        history.put(561, new ArrayList<>());
        hooks.flipHistory = history;

        LocalStatsViewService service = new LocalStatsViewService(hooks);
        LocalStatsViewService.Result result = service.build();

        assertTrue(hooks.ensureLoadedCalled);
        assertEquals(Long.valueOf(5_000L), hooks.receivedSinceMs);
        assertEquals(123L, hooks.receivedSnapshotAccountKey);
        assertEquals(123L, hooks.receivedHistoryAccountKey);
        assertEquals(StatsItemSort.ROI, hooks.receivedSort);
        assertSame(summary, result.summary);
        assertSame(items, result.items);
        assertSame(history, result.flipHistory);
        assertEquals(10_000L, result.asOfMs);
    }

    @Test
    public void buildReconcilesQuantityAndFlipCountFromHistory() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 30_000L;
        hooks.range = StatsRange.ALL_TIME;
        hooks.sort = StatsItemSort.COMPLETION;
        hooks.selectedProfileKey = 77L;
        hooks.sessionStartMs = 0L;

        StatsSummary summary = new StatsSummary();
        summary.total_qty = 10_735L;
        summary.fill_count = 2;

        StatsItem item = new StatsItem();
        item.item_id = 5952;
        item.total_qty = 10_735;
        item.fill_count = 2;

        List<StatsItem> items = new ArrayList<>();
        items.add(item);
        hooks.snapshot = new LocalStatsSnapshot(summary, items);

        List<StatsFlipInstance> flipHistory = new ArrayList<>();
        flipHistory.add(new StatsFlipInstance(5952, 6_071L, 6_447L, 9_000_000L, 9_368_614L, 368_614L, 1_492, 10_000L));
        flipHistory.add(new StatsFlipInstance(5952, 6_071L, 6_447L, 9_000_000L, 9_943_008L, 943_008L, 2_508, 20_000L));
        hooks.flipHistory = new HashMap<>();
        hooks.flipHistory.put(5952, flipHistory);

        LocalStatsViewService service = new LocalStatsViewService(hooks);
        LocalStatsViewService.Result result = service.build();

        assertEquals(Integer.valueOf(4_000), result.items.get(0).total_qty);
        assertEquals(Integer.valueOf(2), result.items.get(0).fill_count);
        assertEquals(Long.valueOf(4_000L), result.summary.total_qty);
        assertEquals(Integer.valueOf(2), result.summary.fill_count);
        assertEquals(Long.valueOf(20_000L), result.summary.last_sell_ts_ms);
    }

    private static final class TestHooks implements LocalStatsViewService.Hooks {
        private boolean ensureLoadedCalled;
        private long nowMs;
        private StatsRange range;
        private StatsItemSort sort;
        private long selectedProfileKey;
        private long sessionStartMs;
        private LocalStatsSnapshot snapshot;
        private Map<Integer, List<StatsFlipInstance>> flipHistory;
        private long receivedSnapshotAccountKey = -1L;
        private long receivedHistoryAccountKey = -1L;
        private Long receivedSinceMs;
        private StatsItemSort receivedSort;

        @Override
        public void ensureSelectedProfileLoaded() {
            ensureLoadedCalled = true;
        }

        @Override
        public long nowMs() {
            return nowMs;
        }

        @Override
        public StatsRange currentStatsRange() {
            return range;
        }

        @Override
        public StatsItemSort currentStatsSort() {
            return sort;
        }

        @Override
        public long resolveSelectedProfileKey() {
            return selectedProfileKey;
        }

        @Override
        public long resolveSessionStartMs(long accountKey, long nowMs) {
            return sessionStartMs;
        }

        @Override
        public LocalStatsSnapshot buildLocalStatsSnapshot(long accountKey, Long sinceMs, StatsItemSort sort) {
            receivedSnapshotAccountKey = accountKey;
            receivedSinceMs = sinceMs;
            receivedSort = sort;
            return snapshot;
        }

        @Override
        public Map<Integer, List<StatsFlipInstance>> buildStatsFlipHistory(long accountKey, Long sinceMs) {
            receivedHistoryAccountKey = accountKey;
            return flipHistory;
        }
    }
}
