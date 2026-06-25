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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AccountwideStatsAggregatorTest {
    @Test
    public void aggregatesSummariesAndItemsAcrossProfiles() {
        TestHooks hooks = new TestHooks();
        hooks.snapshots.put(1L, buildSnapshot(10_000L, 100_000L, 10, 1_000L, 3, 100L, 200L, Arrays.asList(
            buildItem(100, "Item A", 6_000L, 60_000L, 6, 2, 200L),
            buildItem(101, "Item B", 4_000L, 40_000L, 4, 1, 180L)
        )));
        hooks.snapshots.put(2L, buildSnapshot(20_000L, 200_000L, 20, 2_000L, 5, 120L, 250L, Arrays.asList(
            buildItem(100, "Item A", 3_000L, 30_000L, 3, 1, 250L),
            buildItem(102, "Item C", 17_000L, 170_000L, 17, 4, 240L)
        )));

        AccountwideStatsAggregator aggregator = new AccountwideStatsAggregator(hooks);
        Set<Long> profileKeys = new HashSet<>(Arrays.asList(1L, 2L));
        LocalStatsSnapshot result = aggregator.buildFromProfiles(profileKeys, null, StatsItemSort.PROFIT);

        assertEquals(Long.valueOf(30_000L), result.summary.total_profit_gp);
        assertEquals(Long.valueOf(300_000L), result.summary.total_cost_gp);
        assertEquals(Long.valueOf(30L), result.summary.total_qty);
        assertEquals(Long.valueOf(3_000L), result.summary.tax_paid_gp);
        assertEquals(Integer.valueOf(8), result.summary.fill_count);
        assertEquals(Long.valueOf(100L), result.summary.first_buy_ts_ms);
        assertEquals(Long.valueOf(250L), result.summary.last_sell_ts_ms);
        assertEquals(3, result.items.size());
        assertEquals(2, hooks.loaded.size());
        assertEquals(3, hooks.hydratedItems);
    }

    @Test
    public void hasMeaningfulStatsTreatsZeroSnapshotAsEmpty() {
        LocalStatsSnapshot empty = new LocalStatsSnapshot(new StatsSummary(), new ArrayList<>());
        assertFalse(AccountwideStatsAggregator.hasMeaningfulStats(empty));

        StatsSummary nonZero = new StatsSummary();
        nonZero.total_profit_gp = 1L;
        LocalStatsSnapshot nonEmpty = new LocalStatsSnapshot(nonZero, new ArrayList<>());
        assertTrue(AccountwideStatsAggregator.hasMeaningfulStats(nonEmpty));
    }

    private static LocalStatsSnapshot buildSnapshot(long profit,
                                                    long cost,
                                                    int qty,
                                                    long tax,
                                                    int flips,
                                                    long firstBuy,
                                                    long lastSell,
                                                    List<StatsItem> items) {
        StatsSummary summary = new StatsSummary();
        summary.total_profit_gp = profit;
        summary.total_cost_gp = cost;
        summary.total_qty = (long) qty;
        summary.tax_paid_gp = tax;
        summary.fill_count = flips;
        summary.first_buy_ts_ms = firstBuy;
        summary.last_sell_ts_ms = lastSell;
        return new LocalStatsSnapshot(summary, items);
    }

    private static StatsItem buildItem(int id,
                                       String name,
                                       long profit,
                                       long cost,
                                       int qty,
                                       int fills,
                                       long lastSell) {
        StatsItem item = new StatsItem();
        item.item_id = id;
        item.item_name = name;
        item.total_profit_gp = profit;
        item.total_cost_gp = cost;
        item.total_qty = qty;
        item.fill_count = fills;
        item.last_sell_ts_ms = lastSell;
        return item;
    }

    private static final class TestHooks implements AccountwideStatsAggregator.Hooks {
        private final Map<Long, LocalStatsSnapshot> snapshots = new HashMap<>();
        private final Set<Long> loaded = new HashSet<>();
        private int hydratedItems = 0;

        @Override
        public void ensureProfileLoaded(long accountKey) {
            loaded.add(accountKey);
        }

        @Override
        public LocalStatsSnapshot buildSnapshotForProfile(long accountKey, Long sinceMs) {
            return snapshots.get(accountKey);
        }

        @Override
        public void hydrateItemNames(List<StatsItem> items) {
            hydratedItems += items != null ? items.size() : 0;
        }

        @Override
        public Comparator<StatsItem> buildComparator(StatsItemSort sort) {
            return Comparator.comparingLong((StatsItem item) -> item.total_profit_gp != null ? item.total_profit_gp : 0L).reversed();
        }
    }
}
