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

final class LocalStatsCache {
    private static final long LOCAL_EVENT_BUCKET_MS = 600L;
    private final Map<Integer, LocalStatsCacheDeltaService.LocalItemAgg> itemAggs = new HashMap<>();
    private final Map<Integer, LocalStatsCacheDeltaService.LocalInventoryState> inventory = new HashMap<>();
    private final Map<Integer, LocalStatsCacheDeltaService.MatchedSellMarker> recentMatchedSellBySlot = new HashMap<>();
    private final List<LocalTradeDelta> sortedDeltas = new ArrayList<>();
    private final LocalStatsCacheDeltaService.Totals totals = new LocalStatsCacheDeltaService.Totals();
    private final LocalStatsCacheDeltaService deltaService =
        new LocalStatsCacheDeltaService(itemAggs, inventory, recentMatchedSellBySlot, totals);
    private long lastTs = Long.MIN_VALUE;

    synchronized void rebuild(List<LocalTradeDelta> deltas) {
        deltaService.reset();
        sortedDeltas.clear();
        lastTs = Long.MIN_VALUE;
        if (deltas == null || deltas.isEmpty()) {
            return;
        }
        List<LocalTradeDelta> snapshot = new ArrayList<>(deltas);
        snapshot.sort(Comparator
            .comparingLong((LocalTradeDelta delta) -> delta != null ? delta.tsClientMs / LOCAL_EVENT_BUCKET_MS : 0L)
            .thenComparingInt(delta -> delta != null && delta.isBuy ? 0 : 1)
            .thenComparingLong(delta -> delta != null ? delta.tsClientMs : 0L));
        sortedDeltas.addAll(snapshot);
        LocalTradeDelta last = snapshot.get(snapshot.size() - 1);
        if (last != null) {
            lastTs = last.tsClientMs;
        }
        for (LocalTradeDelta delta : snapshot) {
            deltaService.applyDelta(delta);
        }
    }

    synchronized boolean applyDeltaInOrder(LocalTradeDelta delta) {
        if (delta == null) {
            return false;
        }
        if (lastTs != Long.MIN_VALUE && delta.tsClientMs < lastTs) {
            return false;
        }
        sortedDeltas.add(delta);
        lastTs = Math.max(lastTs, delta.tsClientMs);
        deltaService.applyDelta(delta);
        return true;
    }

    synchronized LocalStatsSnapshot buildSnapshotSince(Long sinceMs) {
        if (sinceMs == null) {
            return new LocalStatsSnapshot(getSummary(), getItems());
        }
        LocalStatsCache window = new LocalStatsCache();
        for (LocalTradeDelta delta : sortedDeltas) {
            if (delta == null) {
                continue;
            }
            window.deltaService.applyDelta(delta, sinceMs);
        }
        return new LocalStatsSnapshot(window.getSummary(), window.getItems());
    }

    synchronized StatsSummary getSummary() {
        StatsSummary summary = new StatsSummary();
        summary.total_profit_gp = totals.totalProfit;
        summary.total_cost_gp = totals.totalCost;
        summary.roi_percent = totals.totalCost > 0 ? (totals.totalProfit * 100.0) / totals.totalCost : 0.0;
        summary.gp_per_hour = totals.totalActiveMs > 0 ? (totals.totalProfit / (totals.totalActiveMs / 3600000.0)) : 0.0;
        summary.fill_count = totals.totalCompleted;
        summary.total_qty = totals.totalQty;
        summary.active_ms = totals.totalActiveMs;
        summary.tax_paid_gp = totals.totalTax;
        summary.first_buy_ts_ms = totals.firstBuyTs;
        summary.last_sell_ts_ms = totals.lastSellTs;
        return summary;
    }

    synchronized List<StatsItem> getItems() {
        List<StatsItem> items = new ArrayList<>();
        for (LocalStatsCacheDeltaService.LocalItemAgg agg : itemAggs.values()) {
            if (agg.buyQty <= 0 || agg.sellQty <= 0) {
                continue;
            }
            long profit = agg.sellRevenue - agg.buyCost;
            long cost = agg.buyCost;
            long qty = agg.sellQty;
            double roi = cost > 0 ? (profit * 100.0) / cost : 0.0;

            StatsItem item = new StatsItem();
            item.item_id = agg.itemId;
            item.total_profit_gp = profit;
            item.total_cost_gp = cost;
            item.roi_percent = roi;
            item.total_qty = (int) Math.min(Integer.MAX_VALUE, Math.max(0, qty));
            item.fill_count = agg.completedSells;
            item.last_sell_ts_ms = agg.lastSellTs;
            items.add(item);
        }
        return items;
    }
}
