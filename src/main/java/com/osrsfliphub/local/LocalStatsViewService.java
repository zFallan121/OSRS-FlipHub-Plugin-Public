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
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class LocalStatsViewService {
    interface Hooks {
        void ensureSelectedProfileLoaded();
        long nowMs();
        StatsRange currentStatsRange();
        StatsItemSort currentStatsSort();
        long resolveSelectedProfileKey();
        long resolveSessionStartMs(long accountKey, long nowMs);
        LocalStatsSnapshot buildLocalStatsSnapshot(long accountKey, Long sinceMs, StatsItemSort sort);
        Map<Integer, List<StatsFlipInstance>> buildStatsFlipHistory(long accountKey, Long sinceMs);
    }

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

    private final Hooks hooks;

    @Inject
    LocalStatsViewService() {
        this(productionHooks());
    }

    private static ProfileSelectionPresentationFacadeService profileSelectionFacade() {
        return PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class);
    }

    private static LocalTradeSessionFacadeService localTradeSessionFacade() {
        return PluginInjectorBridge.get(LocalTradeSessionFacadeService.class);
    }

    private static Hooks productionHooks() {
        return new Hooks() {
            @Override
            public void ensureSelectedProfileLoaded() {
                ProfileSelectionPresentationFacadeService facade = profileSelectionFacade();
                if (facade != null) {
                    PluginAccess.plugin().getLocalTradesRuntimeService()
                        .ensureProfileLoaded(facade.resolveSelectedProfileKey());
                }
            }

            @Override
            public long nowMs() {
                return System.currentTimeMillis();
            }

            @Override
            public StatsRange currentStatsRange() {
                return PluginAccess.plugin().currentStatsRange;
            }

            @Override
            public StatsItemSort currentStatsSort() {
                return PluginAccess.plugin().currentStatsSort;
            }

            @Override
            public long resolveSelectedProfileKey() {
                ProfileSelectionPresentationFacadeService facade = profileSelectionFacade();
                return facade != null ? facade.resolveSelectedProfileKey() : -1L;
            }

            @Override
            public long resolveSessionStartMs(long accountKey, long nowMs) {
                LocalTradeSessionFacadeService service = localTradeSessionFacade();
                return service != null ? service.resolveStatsSessionStartMs(accountKey, nowMs) : 0L;
            }

            @Override
            public LocalStatsSnapshot buildLocalStatsSnapshot(long accountKey, Long sinceMs, StatsItemSort sort) {
                LocalStatsSnapshotService service =
                    PluginInjectorBridge.get(LocalStatsSnapshotService.class);
                return service != null ? service.buildSnapshot(accountKey, sinceMs, sort) : null;
            }

            @Override
            public Map<Integer, List<StatsFlipInstance>> buildStatsFlipHistory(long accountKey, Long sinceMs) {
                LocalTradeSessionFacadeService service = localTradeSessionFacade();
                return service != null ? service.buildStatsFlipHistory(accountKey, sinceMs) : null;
            }
        };
    }

    LocalStatsViewService(Hooks hooks) {
        this.hooks = hooks;
    }

    Result build() {
        if (hooks == null) {
            return emptyResult(System.currentTimeMillis());
        }
        hooks.ensureSelectedProfileLoaded();

        long nowMs = hooks.nowMs();
        StatsRange range = hooks.currentStatsRange();
        StatsRange effectiveRange = range != null ? range : StatsRange.SESSION;
        StatsItemSort sort = hooks.currentStatsSort();
        StatsItemSort effectiveSort = sort != null ? sort : StatsItemSort.COMPLETION;
        long accountKey = hooks.resolveSelectedProfileKey();
        if (accountKey < 0) {
            return emptyResult(nowMs);
        }

        long sessionStartMs = hooks.resolveSessionStartMs(accountKey, nowMs);
        Long sinceMs = effectiveRange.getSinceMs(sessionStartMs, nowMs);
        LocalStatsSnapshot snapshot = hooks.buildLocalStatsSnapshot(accountKey, sinceMs, effectiveSort);
        Map<Integer, List<StatsFlipInstance>> history = hooks.buildStatsFlipHistory(accountKey, sinceMs);

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
        int summaryFlips = 0;
        long summaryLastSellTs = 0L;
        boolean hasHistory = false;
        for (List<StatsFlipInstance> entries : flipHistory.values()) {
            if (entries == null || entries.isEmpty()) {
                continue;
            }
            for (StatsFlipInstance instance : entries) {
                if (instance == null) {
                    continue;
                }
                hasHistory = true;
                summaryProfit += instance.profitGp;
                summaryCost += instance.buyCostGp;
                summaryQty += Math.max(0, instance.quantity);
                summaryFlips += 1;
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
            for (StatsFlipInstance instance : entries) {
                if (instance == null) {
                    continue;
                }
                itemQty += Math.max(0, instance.quantity);
                itemFlips += 1;
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

    private static Result emptyResult(long nowMs) {
        return new Result(new StatsSummary(), new ArrayList<>(), new HashMap<>(), nowMs);
    }
}
