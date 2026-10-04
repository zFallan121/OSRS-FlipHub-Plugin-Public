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

import java.util.*;
import javax.inject.*;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class StatsAggregator {
    private final LocalStatsCacheService localStatsCacheService;
    private final LocalStatsSnapshotService localStatsSnapshotService;
    private final LocalTradesRuntime localTradesRuntime;

    StatsSnapshot buildFromProfiles(Set<Long> profileKeys, Long sinceMs, StatsItemSort sort) {
        if (profileKeys == null || profileKeys.isEmpty()) {
            return new StatsSnapshot(new StatsSummary(), new ArrayList<>());
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
            localTradesRuntime.ensureProfileLoaded(key);
            StatsSnapshot snapshot = localStatsCacheService.getOrBuild(key).buildSnapshotSince(sinceMs);
            StatsSummary summary = snapshot.summary;
            totalProfit += summary.total_profit_gp;
            totalCost += summary.total_cost_gp;
            totalQty += summary.total_qty;
            totalTax += summary.tax_paid_gp;
            totalActiveMs += summary.active_ms;
            totalCompleted += summary.fill_count;
            Long profileFirstBuy = summary.first_buy_ts_ms;
            if (profileFirstBuy != null && profileFirstBuy > 0 && (firstBuyTs == null || profileFirstBuy < firstBuyTs)) {
                firstBuyTs = profileFirstBuy;
            }
            Long profileLastSell = summary.last_sell_ts_ms;
            if (profileLastSell != null && profileLastSell > 0 && (lastSellTs == null || profileLastSell > lastSellTs)) {
                lastSellTs = profileLastSell;
            }
            for (StatsItem item : snapshot.items) {
                if (item.item_id <= 0) {
                    continue;
                }
                StatsItem agg = itemMap.computeIfAbsent(item.item_id, id -> {
                    StatsItem next = new StatsItem();
                    next.item_id = id;
                    return next;
                });
                long nextProfit = (agg.total_profit_gp != null ? agg.total_profit_gp : 0L)
                    + item.total_profit_gp;
                long nextCost = (agg.total_cost_gp != null ? agg.total_cost_gp : 0L)
                    + item.total_cost_gp;
                int nextQty = (agg.total_qty != null ? agg.total_qty : 0)
                    + item.total_qty;
                int nextFillCount = (agg.fill_count != null ? agg.fill_count : 0)
                    + item.fill_count;
                Long aggActive = agg.active_ms;
                Long itemActive = item.active_ms;
                if (aggActive != null || itemActive != null) {
                    agg.active_ms = (aggActive != null ? aggActive : 0L)
                        + (itemActive != null ? itemActive : 0L);
                }
                agg.total_profit_gp = nextProfit;
                agg.total_cost_gp = nextCost;
                agg.total_qty = Math.max(0, nextQty);
                agg.fill_count = Math.max(0, nextFillCount);
                Long aggLastSell = agg.last_sell_ts_ms;
                Long itemLastSell = item.last_sell_ts_ms;
                if (itemLastSell != null && itemLastSell > 0 && (aggLastSell == null || itemLastSell > aggLastSell)) {
                    agg.last_sell_ts_ms = itemLastSell;
                }
                if (Str.isBlank(agg.item_name)
                    && Str.hasText(item.item_name)) {
                    agg.item_name = item.item_name;
                }
            }
        }

        List<StatsItem> aggregatedItems = new ArrayList<>(itemMap.values());
        for (StatsItem item : aggregatedItems) {
            item.roi_percent = item.total_cost_gp > 0 ? (item.total_profit_gp * 100.0) / item.total_cost_gp : 0.0;
        }
        localStatsSnapshotService.hydrateItemNames(aggregatedItems);
        aggregatedItems.sort(StatsItemSort.comparatorFor(sort));
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
        return new StatsSnapshot(summary, aggregatedItems);
    }
}
