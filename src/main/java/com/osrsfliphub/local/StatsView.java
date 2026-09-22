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
final class StatsView {
    private final ProfileSelectionPresentation profileSelectionPresentation;
    private final TradeSession tradeSession;
    private final LocalStatsSnapshotService localStatsSnapshotService;
    private final LocalTradesRuntime localTradesRuntime;

    static final class Result {
        final StatsSummary summary;
        final List<StatsItem> items;
        final Map<Integer, List<StatsFlipInstance>> flipHistory;
        final long asOfMs;

        private Result(StatsSummary summary,
                       List<StatsItem> items,
                       Map<Integer, List<StatsFlipInstance>> flipHistory,
                       long asOfMs) {
            this.summary = summary;
            this.items = items;
            this.flipHistory = flipHistory;
            this.asOfMs = asOfMs;
        }
    }

    Result build() {
        localTradesRuntime.ensureProfileLoaded(profileSelectionPresentation.resolveSelectedProfileKey());

        long nowMs = System.currentTimeMillis();
        StatsRange range = Access.plugin().currentStatsRange;
        StatsRange effectiveRange = range != null ? range : StatsRange.SESSION;
        StatsItemSort sort = Access.plugin().currentStatsSort;
        StatsItemSort effectiveSort = sort != null ? sort : StatsItemSort.COMPLETION;
        long accountKey = profileSelectionPresentation.resolveSelectedProfileKey();
        if (accountKey < 0) {
            return new Result(new StatsSummary(), new ArrayList<>(), new HashMap<>(), nowMs);
        }

        long sessionStartMs = tradeSession.resolveStatsSessionStartMs(accountKey, nowMs);
        return view(accountKey, effectiveRange.getSinceMs(sessionStartMs, nowMs), effectiveSort, nowMs);
    }

    /**
     * One character's figures, or every character's for the accountwide key, over a range.
     *
     * <p>The Merchant level reads its lifetime profit here too, so the skills tab and TOTAL
     * PROFIT on All time can never name two different figures.
     */
    Result view(long accountKey, Long sinceMs, StatsItemSort sort, long nowMs) {
        StatsSnapshot snapshot = localStatsSnapshotService.buildSnapshot(accountKey, sinceMs, sort);
        Map<Integer, List<StatsFlipInstance>> history = tradeSession.buildStatsFlipHistory(accountKey, sinceMs);

        StatsSummary summary = snapshot != null && snapshot.summary != null ? snapshot.summary : new StatsSummary();
        List<StatsItem> items = snapshot != null && snapshot.items != null ? snapshot.items : new ArrayList<>();
        Map<Integer, List<StatsFlipInstance>> flipHistory = history != null ? history : new HashMap<>();
        reconcileWithFlipHistory(summary, items, flipHistory);
        return new Result(summary, items, flipHistory, nowMs);
    }

    static void reconcileWithFlipHistory(StatsSummary summary,
                                         List<StatsItem> items,
                                         Map<Integer, List<StatsFlipInstance>> flipHistory) {
        if (summary == null || items == null || items.isEmpty() || flipHistory == null || flipHistory.isEmpty()) {
            return;
        }

        long summaryProfit = 0L;
        long summaryCost = 0L;
        long summaryQty = 0L;
        long summaryTax = 0L;
        int summaryFlips = 0;
        long summaryLastSellTs = 0L;
        boolean hasHistory = false;
        for (List<StatsFlipInstance> entries : flipHistory.values()) {
            if (entries == null || entries.isEmpty()) {
                continue;
            }
            for (StatsFlipInstance instance : entries) {
                // A dismissed guess or an unfinished break is shown, not counted.
                if (instance == null) {
                    continue;
                }
                hasHistory = true;
                summaryProfit += instance.profitGp;
                summaryCost += instance.buyCostGp;
                summaryQty += Math.max(0, instance.quantity);
                summaryTax += instance.taxGp;
                // An offer still filling has made its coins but is not a flip yet.
                if (!instance.inProgress) {
                    summaryFlips += 1;
                }
                summaryLastSellTs = Math.max(summaryLastSellTs, instance.completionTsMs);
            }
        }
        if (!hasHistory) {
            return;
        }

        for (StatsItem item : items) {
            if (item == null || item.item_id <= 0) {
                continue;
            }
            List<StatsFlipInstance> entries = flipHistory.get(item.item_id);
            if (entries == null || entries.isEmpty()) {
                continue;
            }

            long itemQty = 0L;
            int itemFlips = 0;
            long itemLastSellTs = 0L;
            long itemProfit = 0L;
            long itemCost = 0L;
            item.conversionKinds = EnumSet.noneOf(ConversionKind.class);
            item.hasPlainFlip = false;
            for (StatsFlipInstance instance : entries) {
                if (instance == null) {
                    continue;
                }
                if (instance.conversionKind != null) {
                    item.conversionKinds.add(instance.conversionKind);
                } else {
                    item.hasPlainFlip = true;
                }
                itemQty += Math.max(0, instance.quantity);
                if (!instance.inProgress) {
                    itemFlips += 1;
                }
                itemLastSellTs = Math.max(itemLastSellTs, instance.completionTsMs);
                itemProfit += instance.profitGp;
                itemCost += instance.buyCostGp;
            }
            item.total_profit_gp = itemProfit;
            item.total_cost_gp = itemCost;
            item.roi_percent = itemCost > 0 ? (itemProfit * 100.0) / itemCost : 0.0;
            item.total_qty = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, itemQty));
            item.fill_count = Math.max(0, itemFlips);
            if (itemLastSellTs > 0) {
                item.last_sell_ts_ms = itemLastSellTs;
            }
        }

        summary.total_profit_gp = summaryProfit;
        summary.total_cost_gp = summaryCost;
        // Tax comes from the same flips as the profit. Under a range the cache
        // admits fills one at a time and the history admits offers whole, so an
        // offer straddling the boundary would otherwise show all of its profit
        // beside a fraction of its tax. Active time stays with the cache: the
        // history keeps no buy timestamps and it is not shown on the card.
        summary.tax_paid_gp = summaryTax;
        summary.roi_percent = summaryCost > 0 ? (summaryProfit * 100.0) / summaryCost : 0.0;
        if (summary.active_ms != null && summary.active_ms > 0) {
            summary.gp_per_hour = summaryProfit / (summary.active_ms / 3600000.0);
        }
        summary.total_qty = Math.max(0L, summaryQty);
        summary.fill_count = Math.max(0, summaryFlips);
        if (summaryLastSellTs > 0L) {
            summary.last_sell_ts_ms = summaryLastSellTs;
        }
    }

}
