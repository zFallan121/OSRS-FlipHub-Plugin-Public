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

import java.util.Map;

final class LocalStatsCacheDeltaService {
    private static final long COMPLETION_MARKER_MAX_AGE_MS = 5_000L;

    private final Map<Integer, LocalItemAgg> itemAggs;
    private final Map<Integer, LocalInventoryState> inventory;
    private final Map<Integer, MatchedSellMarker> recentMatchedSellBySlot;
    private final Totals totals;

    LocalStatsCacheDeltaService(
        Map<Integer, LocalItemAgg> itemAggs,
        Map<Integer, LocalInventoryState> inventory,
        Map<Integer, MatchedSellMarker> recentMatchedSellBySlot,
        Totals totals
    ) {
        this.itemAggs = itemAggs;
        this.inventory = inventory;
        this.recentMatchedSellBySlot = recentMatchedSellBySlot;
        this.totals = totals;
    }

    void reset() {
        itemAggs.clear();
        inventory.clear();
        recentMatchedSellBySlot.clear();
        totals.reset();
    }

    void applyDelta(LocalTradeDelta delta) {
        applyDelta(delta, null);
    }

    void applyDelta(LocalTradeDelta delta, Long sellSinceMs) {
        if (delta == null) {
            return;
        }
        boolean isCompletion = "OFFER_COMPLETED".equals(delta.eventType);
        if (delta.deltaQty <= 0 && !isCompletion) {
            return;
        }
        LocalInventoryState state = inventory.computeIfAbsent(delta.itemId, LocalInventoryState::new);
        if (delta.isBuy) {
            if (delta.deltaQty <= 0) {
                return;
            }
            state.qty += delta.deltaQty;
            state.cost += Math.max(0L, delta.deltaGp);
            if (state.firstBuyTs == null || delta.tsClientMs < state.firstBuyTs) {
                state.firstBuyTs = delta.tsClientMs;
            }
            return;
        }

        if (state.qty <= 0 && !isCompletion) {
            return;
        }

        long matchQty = 0L;
        long matchCost = 0L;
        long matchRevenue = 0L;
        Long matchedBuyTs = state.firstBuyTs;
        if (delta.deltaQty > 0 && state.qty > 0) {
            matchQty = Math.min((long) delta.deltaQty, state.qty);
            if (matchQty > 0) {
                matchRevenue = delta.deltaGp;
                if (matchQty < delta.deltaQty) {
                    matchRevenue = (delta.deltaGp * matchQty) / delta.deltaQty;
                }
                if (matchQty >= state.qty) {
                    matchCost = state.cost;
                    state.qty = 0L;
                    state.cost = 0L;
                    state.firstBuyTs = null;
                } else {
                    matchCost = (state.cost * matchQty) / state.qty;
                    state.qty -= matchQty;
                    state.cost = Math.max(0L, state.cost - matchCost);
                }
            }
        }

        boolean includeInStats = sellSinceMs == null || delta.tsClientMs >= sellSinceMs;
        if (includeInStats && matchQty > 0) {
            rememberMatchedSell(delta);
        }
        boolean completionMarked = includeInStats && isCompletion && !delta.isBuy && hasRecentMatchedSell(delta);
        if (!includeInStats || matchQty <= 0) {
            if (completionMarked) {
                LocalItemAgg agg = itemAggs.get(delta.itemId);
                if (agg != null && agg.sellQty > 0) {
                    agg.completedSells += 1;
                    totals.totalCompleted += 1;
                    if (agg.lastSellTs == null || delta.tsClientMs > agg.lastSellTs) {
                        agg.lastSellTs = delta.tsClientMs;
                    }
                    if (totals.lastSellTs == null || agg.lastSellTs > totals.lastSellTs) {
                        totals.lastSellTs = agg.lastSellTs;
                    }
                }
                clearMatchedSell(delta);
            }
            return;
        }

        LocalItemAgg agg = itemAggs.computeIfAbsent(delta.itemId, LocalItemAgg::new);
        agg.buyCost += matchCost;
        agg.buyQty += matchQty;
        agg.sellRevenue += Math.max(0L, matchRevenue);
        agg.sellQty += matchQty;
        long tax = 0L;
        if (delta.price > 0 && matchQty > 0) {
            long taxByRate = ((long) delta.price / 50L) * matchQty;
            long taxCap = matchQty * 5_000_000L;
            tax = Math.max(0L, Math.min(taxByRate, taxCap));
        }
        agg.taxPaid += Math.max(0L, tax);
        if (matchedBuyTs == null) {
            matchedBuyTs = delta.tsClientMs;
        }
        if (agg.firstBuyTs == null || matchedBuyTs < agg.firstBuyTs) {
            agg.firstBuyTs = matchedBuyTs;
        }
        long duration = delta.tsClientMs - matchedBuyTs;
        if (duration > 0) {
            agg.activeMs += duration;
        }
        totals.totalProfit += (Math.max(0L, matchRevenue) - matchCost);
        totals.totalCost += matchCost;
        totals.totalQty += matchQty;
        totals.totalTax += Math.max(0L, tax);
        totals.totalActiveMs += Math.max(0L, duration);
        if (agg.lastSellTs == null || delta.tsClientMs > agg.lastSellTs) {
            agg.lastSellTs = delta.tsClientMs;
        }
        if (isCompletion && !delta.isBuy) {
            agg.completedSells += 1;
            totals.totalCompleted += 1;
            clearMatchedSell(delta);
        }
        if (agg.firstBuyTs != null && (totals.firstBuyTs == null || agg.firstBuyTs < totals.firstBuyTs)) {
            totals.firstBuyTs = agg.firstBuyTs;
        }
        if (agg.lastSellTs != null && (totals.lastSellTs == null || agg.lastSellTs > totals.lastSellTs)) {
            totals.lastSellTs = agg.lastSellTs;
        }
    }

    private void rememberMatchedSell(LocalTradeDelta delta) {
        if (delta == null || delta.isBuy || delta.slot < 0) {
            return;
        }
        recentMatchedSellBySlot.put(delta.slot, new MatchedSellMarker(delta.itemId, delta.price, delta.tsClientMs));
    }

    private boolean hasRecentMatchedSell(LocalTradeDelta delta) {
        if (delta == null || delta.isBuy || delta.slot < 0) {
            return false;
        }
        MatchedSellMarker marker = recentMatchedSellBySlot.get(delta.slot);
        if (marker == null) {
            return false;
        }
        if (marker.itemId != delta.itemId || marker.price != delta.price) {
            return false;
        }
        return Math.abs(delta.tsClientMs - marker.tsClientMs) <= COMPLETION_MARKER_MAX_AGE_MS;
    }

    private void clearMatchedSell(LocalTradeDelta delta) {
        if (delta == null || delta.slot < 0) {
            return;
        }
        recentMatchedSellBySlot.remove(delta.slot);
    }

    static final class Totals {
        long totalProfit;
        long totalCost;
        long totalQty;
        long totalTax;
        long totalActiveMs;
        int totalCompleted;
        Long firstBuyTs;
        Long lastSellTs;

        void reset() {
            totalProfit = 0L;
            totalCost = 0L;
            totalQty = 0L;
            totalTax = 0L;
            totalActiveMs = 0L;
            totalCompleted = 0;
            firstBuyTs = null;
            lastSellTs = null;
        }
    }

    static final class LocalInventoryState {
        private final int itemId;
        private long qty;
        private long cost;
        private Long firstBuyTs;

        private LocalInventoryState(int itemId) {
            this.itemId = itemId;
        }
    }

    static final class LocalItemAgg {
        final int itemId;
        long buyCost;
        long sellRevenue;
        long buyQty;
        long sellQty;
        long taxPaid;
        long activeMs;
        int completedSells;
        Long firstBuyTs;
        Long lastSellTs;

        private LocalItemAgg(int itemId) {
            this.itemId = itemId;
        }
    }

    static final class MatchedSellMarker {
        private final int itemId;
        private final int price;
        private final long tsClientMs;

        private MatchedSellMarker(int itemId, int price, long tsClientMs) {
            this.itemId = itemId;
            this.price = price;
            this.tsClientMs = tsClientMs;
        }
    }
}
