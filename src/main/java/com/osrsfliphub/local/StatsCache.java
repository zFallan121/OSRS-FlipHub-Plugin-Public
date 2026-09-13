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

final class StatsCache {
    private final Map<Integer, StatsCacheDelta.ItemAgg> itemAggs = new HashMap<>();
    private final Map<Integer, StatsCacheDelta.LocalInventoryState> inventory = new HashMap<>();
    private final Map<Integer, StatsCacheDelta.MatchedSellMarker> recentMatchedSellBySlot = new HashMap<>();
    private final List<Delta> sortedDeltas = new ArrayList<>();
    private final StatsCacheDelta.Totals totals = new StatsCacheDelta.Totals();
    private final Ledger conversionLedger;
    /** Whose trades these are; a repair fee depends on the player's Smithing. */
    private final long accountKey;
    private final StatsCacheDelta deltaService;
    private long lastTs = Long.MIN_VALUE;
    /** What the player's recorded conversions were worth, and which trades they used. */
    private RecipeFlipLedger.Result recorded = RecipeFlipLedger.empty();
    private final RecipeFlipStore recipeFlips;

    StatsCache() {
        this(0L);
    }

    // Looked up rather than injected: caches are built with `new`, per account and
    // per window, well after startUp has wired the injector.
    StatsCache(long accountKey) {
        this(Bridge.get(Ledger.class), accountKey);
    }

    StatsCache(Ledger conversionLedger) {
        this(conversionLedger, 0L);
    }

    StatsCache(Ledger conversionLedger, long accountKey) {
        this(conversionLedger, accountKey, null);
    }

    /** The store is passed in by tests; in the running plugin it is looked up. */
    StatsCache(Ledger conversionLedger, long accountKey, RecipeFlipStore recipeFlips) {
        this.recipeFlips = recipeFlips;
        this.conversionLedger = conversionLedger;
        this.accountKey = accountKey;
        this.deltaService = new StatsCacheDelta(
            itemAggs, inventory, recentMatchedSellBySlot, totals, conversionLedger, accountKey);
    }

    synchronized void rebuild(List<Delta> deltas) {
        deltaService.reset();
        sortedDeltas.clear();
        lastTs = Long.MIN_VALUE;
        if (deltas == null || deltas.isEmpty()) {
            return;
        }
        // Price what the player recorded, then replay only what those conversions did not use.
        recorded = RecipeFlipLedger.apply(deltas, recordedFlips());
        List<Delta> snapshot = new ArrayList<>(recorded.remainingTrades(deltas));
        snapshot.sort(TradeDeltaUtils.replayOrder());
        sortedDeltas.addAll(snapshot);
        for (Delta delta : snapshot) {
            lastTs = Math.max(lastTs, TradeDeltaUtils.replayTimeMs(delta));
            deltaService.applyDelta(delta);
        }
        addRecordedConversions(null);
    }

    private List<RecipeFlip> recordedFlips() {
        RecipeFlipStore store = recipeFlips != null ? recipeFlips : Bridge.get(RecipeFlipStore.class);
        return store != null ? store.applicable(accountKey) : java.util.Collections.emptyList();
    }

    private void addRecordedConversions(Long sinceMs) {
        for (RecipeFlipLedger.Activity activity : recorded.activities) {
            if (sinceMs != null && activity.completionTsMs < sinceMs) {
                continue;
            }
            StatsCacheDelta.addRecordedActivity(itemAggs, totals, activity);
        }
    }

    synchronized boolean applyDeltaInOrder(Delta delta) {
        if (delta == null) {
            return false;
        }
        long replayTimeMs = TradeDeltaUtils.replayTimeMs(delta);
        if (lastTs != Long.MIN_VALUE && replayTimeMs < lastTs) {
            return false;
        }
        sortedDeltas.add(delta);
        lastTs = Math.max(lastTs, replayTimeMs);
        deltaService.applyDelta(delta);
        return true;
    }

    synchronized StatsSnapshot buildSnapshotSince(Long sinceMs) {
        if (sinceMs == null) {
            return new StatsSnapshot(getSummary(), getItems());
        }
        // The window has to see the same conversions the live cache does.
        StatsCache window = new StatsCache(conversionLedger, accountKey, recipeFlips);
        for (Delta delta : sortedDeltas) {
            if (delta == null) {
                continue;
            }
            window.deltaService.applyDelta(delta, sinceMs);
        }
        // sortedDeltas already has the recorded conversions' trades taken out, so the window
        // reuses what was priced rather than pricing it again.
        window.recorded = recorded;
        window.addRecordedConversions(sinceMs);
        return new StatsSnapshot(window.getSummary(), window.getItems());
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
        for (StatsCacheDelta.ItemAgg agg : itemAggs.values()) {
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
            item.active_ms = agg.activeMs > 0L ? agg.activeMs : null;
            items.add(item);
        }
        return items;
    }
}
