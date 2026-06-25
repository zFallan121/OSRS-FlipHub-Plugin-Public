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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class AccountwideStatsAggregator {
    interface Hooks {
        void ensureProfileLoaded(long accountKey);
        LocalStatsSnapshot buildSnapshotForProfile(long accountKey, Long sinceMs);
        void hydrateItemNames(List<StatsItem> items);
        Comparator<StatsItem> buildComparator(StatsItemSort sort);
    }

    private final Hooks hooks;

    AccountwideStatsAggregator(Hooks hooks) {
        this.hooks = hooks;
    }

    LocalStatsSnapshot buildFromProfiles(Set<Long> profileKeys, Long sinceMs, StatsItemSort sort) {
        if (profileKeys == null || profileKeys.isEmpty() || hooks == null) {
            return new LocalStatsSnapshot(new StatsSummary(), new ArrayList<>());
        }

        Map<Integer, StatsItem> itemMap = new HashMap<>();
        long totalProfit = 0L;
        long totalCost = 0L;
        long totalQty = 0L;
        long totalTax = 0L;
        long totalActiveMs = 0L;
        int totalCompleted = 0;
        Long firstBuyTs = null;
        Long lastSellTs = null;

        for (Long key : profileKeys) {
            if (key == null || key <= 0) {
                continue;
            }
            hooks.ensureProfileLoaded(key);
            LocalStatsSnapshot snapshot = hooks.buildSnapshotForProfile(key, sinceMs);
            if (snapshot == null) {
                continue;
            }
            StatsSummary summary = snapshot.summary;
            if (summary != null) {
                totalProfit += summary.total_profit_gp != null ? summary.total_profit_gp : 0L;
                totalCost += summary.total_cost_gp != null ? summary.total_cost_gp : 0L;
                totalQty += summary.total_qty != null ? summary.total_qty : 0L;
                totalTax += summary.tax_paid_gp != null ? summary.tax_paid_gp : 0L;
                totalActiveMs += summary.active_ms != null ? summary.active_ms : 0L;
                totalCompleted += summary.fill_count != null ? summary.fill_count : 0;
                Long profileFirstBuy = summary.first_buy_ts_ms;
                if (profileFirstBuy != null && profileFirstBuy > 0 && (firstBuyTs == null || profileFirstBuy < firstBuyTs)) {
                    firstBuyTs = profileFirstBuy;
                }
                Long profileLastSell = summary.last_sell_ts_ms;
                if (profileLastSell != null && profileLastSell > 0 && (lastSellTs == null || profileLastSell > lastSellTs)) {
                    lastSellTs = profileLastSell;
                }
            }
            List<StatsItem> items = snapshot.items;
            if (items == null || items.isEmpty()) {
                continue;
            }
            for (StatsItem item : items) {
                if (item == null || item.item_id <= 0) {
                    continue;
                }
                StatsItem agg = itemMap.computeIfAbsent(item.item_id, id -> {
                    StatsItem next = new StatsItem();
                    next.item_id = id;
                    return next;
                });
                long nextProfit = (agg.total_profit_gp != null ? agg.total_profit_gp : 0L)
                    + (item.total_profit_gp != null ? item.total_profit_gp : 0L);
                long nextCost = (agg.total_cost_gp != null ? agg.total_cost_gp : 0L)
                    + (item.total_cost_gp != null ? item.total_cost_gp : 0L);
                int nextQty = (agg.total_qty != null ? agg.total_qty : 0)
                    + (item.total_qty != null ? item.total_qty : 0);
                int nextFillCount = (agg.fill_count != null ? agg.fill_count : 0)
                    + (item.fill_count != null ? item.fill_count : 0);
                agg.total_profit_gp = nextProfit;
                agg.total_cost_gp = nextCost;
                agg.total_qty = Math.max(0, nextQty);
                agg.fill_count = Math.max(0, nextFillCount);
                Long aggLastSell = agg.last_sell_ts_ms;
                Long itemLastSell = item.last_sell_ts_ms;
                if (itemLastSell != null && itemLastSell > 0 && (aggLastSell == null || itemLastSell > aggLastSell)) {
                    agg.last_sell_ts_ms = itemLastSell;
                }
                if ((agg.item_name == null || agg.item_name.trim().isEmpty())
                    && item.item_name != null && !item.item_name.trim().isEmpty()) {
                    agg.item_name = item.item_name;
                }
            }
        }

        List<StatsItem> aggregatedItems = new ArrayList<>(itemMap.values());
        StatsSummary summary = finalizeSnapshot(
            aggregatedItems,
            sort,
            totalProfit,
            totalCost,
            totalQty,
            totalTax,
            totalActiveMs,
            totalCompleted,
            firstBuyTs,
            lastSellTs
        );
        return new LocalStatsSnapshot(summary, aggregatedItems);
    }

    static boolean hasMeaningfulStats(LocalStatsSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        if (snapshot.items != null && !snapshot.items.isEmpty()) {
            return true;
        }
        StatsSummary summary = snapshot.summary;
        if (summary == null) {
            return false;
        }
        long profit = summary.total_profit_gp != null ? summary.total_profit_gp : 0L;
        long cost = summary.total_cost_gp != null ? summary.total_cost_gp : 0L;
        long tax = summary.tax_paid_gp != null ? summary.tax_paid_gp : 0L;
        long qty = summary.total_qty != null ? summary.total_qty : 0L;
        long flips = summary.fill_count != null ? summary.fill_count : 0L;
        return profit != 0L || cost != 0L || tax != 0L || qty != 0L || flips != 0L;
    }

    private StatsSummary finalizeSnapshot(List<StatsItem> items,
                                          StatsItemSort sort,
                                          long totalProfit,
                                          long totalCost,
                                          long totalQty,
                                          long totalTax,
                                          long totalActiveMs,
                                          int totalCompleted,
                                          Long firstBuyTs,
                                          Long lastSellTs) {
        if (items != null) {
            for (StatsItem item : items) {
                if (item == null) {
                    continue;
                }
                long cost = item.total_cost_gp != null ? item.total_cost_gp : 0L;
                long profit = item.total_profit_gp != null ? item.total_profit_gp : 0L;
                item.roi_percent = cost > 0 ? (profit * 100.0) / cost : 0.0;
            }
            hooks.hydrateItemNames(items);
            Comparator<StatsItem> comparator = hooks.buildComparator(sort);
            if (comparator != null) {
                items.sort(comparator);
            }
        }
        StatsSummary summary = new StatsSummary();
        summary.total_profit_gp = totalProfit;
        summary.total_cost_gp = totalCost;
        summary.roi_percent = totalCost > 0 ? (totalProfit * 100.0) / totalCost : 0.0;
        summary.gp_per_hour = totalActiveMs > 0 ? (totalProfit / (totalActiveMs / 3600000.0)) : 0.0;
        summary.fill_count = totalCompleted;
        summary.total_qty = totalQty;
        summary.active_ms = totalActiveMs;
        summary.tax_paid_gp = totalTax;
        summary.first_buy_ts_ms = firstBuyTs;
        summary.last_sell_ts_ms = lastSellTs;
        return summary;
    }
}
