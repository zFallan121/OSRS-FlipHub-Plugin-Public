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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

final class StatsCacheDelta {
    private static final long COMPLETION_MARKER_MAX_AGE_MS = 5_000L;

    private final Map<Integer, ItemAgg> itemAggs;
    private final Map<Integer, LocalInventoryState> inventory;
    private final Map<Integer, MatchedSellMarker> recentMatchedSellBySlot;
    private final Totals totals;
    /** Whose trades these are; a repair fee depends on the player's Smithing. */
    private final long accountKey;
    StatsCacheDelta(
        Map<Integer, ItemAgg> itemAggs,
        Map<Integer, LocalInventoryState> inventory,
        Map<Integer, MatchedSellMarker> recentMatchedSellBySlot,
        Totals totals
    ) {
        this(itemAggs, inventory, recentMatchedSellBySlot, totals, 0L);
    }

    StatsCacheDelta(
        Map<Integer, ItemAgg> itemAggs,
        Map<Integer, LocalInventoryState> inventory,
        Map<Integer, MatchedSellMarker> recentMatchedSellBySlot,
        Totals totals,
        long accountKey
    ) {
        this.itemAggs = itemAggs;
        this.inventory = inventory;
        this.recentMatchedSellBySlot = recentMatchedSellBySlot;
        this.totals = totals;
        this.accountKey = accountKey;
    }

    void reset() {
        itemAggs.clear();
        inventory.clear();
        recentMatchedSellBySlot.clear();
        totals.reset();
    }

    void applyDelta(Delta delta) {
        applyDelta(delta, null);
    }

    void applyDelta(Delta delta, Long sellSinceMs) {
        if (delta == null) {
            return;
        }
        boolean isCompletion = "OFFER_COMPLETED".equals(delta.eventType);
        if (delta.deltaQty <= 0 && !isCompletion) {
            return;
        }
        LocalInventoryState state = inventory.computeIfAbsent(delta.itemId, ignored -> new LocalInventoryState());
        if (delta.isBuy) {
            if (delta.deltaQty <= 0) {
                return;
            }
            state.qty += delta.deltaQty;
            state.cost += Math.max(0L, delta.deltaGp);
            state.positionQty += delta.deltaQty;
            if (state.firstBuyTs == null || delta.tsClientMs < state.firstBuyTs) {
                state.firstBuyTs = delta.tsClientMs;
            }
            return;
        }

        boolean includeInStats = sellSinceMs == null || delta.closedAtMs() >= sellSinceMs;

        if (state.qty <= 0 && !isCompletion) {
            return;
        }

        long remainingQty = delta.deltaQty;
        long remainingGp = delta.deltaGp;

        long matchQty = 0L;
        long matchCost = 0L;
        long matchRevenue = 0L;
        Long matchedBuyTs = state.firstBuyTs;
        long positionQty = state.positionQty;
        // Same as the flip-history ledger: only stock bought outright is matched
        // here. What is left of a break's pieces belongs to a break that is
        // still open, and matching it would invent a flip with no cost.
        long boughtAvailable = Math.max(0L, state.qty - state.breakQty);
        if (remainingQty > 0 && boughtAvailable > 0) {
            matchQty = Math.min(remainingQty, boughtAvailable);
            if (matchQty > 0) {
                matchRevenue = remainingGp;
                if (matchQty < remainingQty) {
                    matchRevenue = (remainingGp * matchQty) / remainingQty;
                }
                if (matchQty >= state.qty) {
                    // Only reachable with no break stock in the bucket, so no
                    // open break loses its count here.
                    matchCost = state.cost;
                    state.qty = 0L;
                    state.cost = 0L;
                    state.convertedQty = 0L;
                    state.positionQty = 0L;
                    state.firstBuyTs = null;
                } else if (matchQty >= boughtAvailable) {
                    // Every bought unit is going. The whole remaining cost was
                    // theirs, so none of it may be left behind on the break
                    // pieces that stay: they carry no cost, and a residue on an
                    // empty bucket is silently inherited by the next purchase.
                    matchCost = state.cost;
                    state.qty -= matchQty;
                    state.cost = 0L;
                    state.convertedQty = Math.min(state.convertedQty, state.qty);
                    state.breakQty = Math.min(state.breakQty, state.qty);
                    state.positionQty = 0L;
                } else {
                    // Divided across bought units only. Dividing across the whole
                    // bucket handed part of the cost to zero-cost break pieces,
                    // so the unit actually sold carried less than it cost.
                    matchCost = (state.cost * matchQty) / boughtAvailable;
                    state.qty -= matchQty;
                    state.cost = Math.max(0L, state.cost - matchCost);
                    state.convertedQty = Math.min(state.convertedQty, state.qty);
                    state.breakQty = Math.min(state.breakQty, state.qty);
                    if (state.qty <= state.breakQty) {
                        // Only a break's pieces are left, and they are not a
                        // position of their own: the next purchase opens a new one.
                        state.positionQty = 0L;
                    }
                }
            }
        }

        if (includeInStats && matchQty > 0) {
            rememberMatchedSell(delta);
        }
        boolean completionMarked = includeInStats && isCompletion && !delta.isBuy && hasRecentMatchedSell(delta);
        if (!includeInStats || matchQty <= 0) {
            if (completionMarked) {
                ItemAgg agg = itemAggs.get(delta.itemId);
                if (agg != null && agg.sellQty > 0) {
                    agg.completedSells += 1;
                    totals.totalCompleted += 1;
                    if (agg.lastSellTs == null || delta.closedAtMs() > agg.lastSellTs) {
                        agg.lastSellTs = delta.closedAtMs();
                    }
                    if (totals.lastSellTs == null || agg.lastSellTs > totals.lastSellTs) {
                        totals.lastSellTs = agg.lastSellTs;
                    }
                }
                clearMatchedSell(delta);
            }
            return;
        }

        ItemAgg agg = itemAggs.computeIfAbsent(delta.itemId, ItemAgg::new);
        agg.buyCost += matchCost;
        agg.buyQty += matchQty;
        agg.sellRevenue += Math.max(0L, matchRevenue);
        agg.sellQty += matchQty;
        long tax = salesTax(delta.itemId, delta.price, matchQty);
        agg.taxPaid += Math.max(0L, tax);
        if (matchedBuyTs == null) {
            matchedBuyTs = delta.tsClientMs;
        }
        if (agg.firstBuyTs == null || matchedBuyTs < agg.firstBuyTs) {
            agg.firstBuyTs = matchedBuyTs;
        }
        long duration = heldShare(delta.closedAtMs() - matchedBuyTs, matchQty, positionQty);
        agg.activeMs += duration;
        totals.totalProfit += (Math.max(0L, matchRevenue) - matchCost);
        totals.totalCost += matchCost;
        totals.totalQty += matchQty;
        totals.totalTax += Math.max(0L, tax);
        totals.totalActiveMs += duration;
        if (agg.lastSellTs == null || delta.closedAtMs() > agg.lastSellTs) {
            agg.lastSellTs = delta.closedAtMs();
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

    private static long salesTax(int itemId, int unitPrice, long quantity) {
        return GeTax.forSale(itemId, unitPrice, quantity);
    }

    /**
     * The part of a hold that one sale closes.
     *
     * <p>Active time is what gold per hour divides by, and a purchase held for
     * an hour was an hour in the market however many fills it took to sell.
     * Each fill therefore carries the share of the position it closed - the
     * units it sold over everything that entered the pool since it was last
     * empty - so a purchase sold in ten pieces adds up to one hold, not ten.
     * A negative hold is a fiction of a replay and counts as nothing.
     */
    private static long heldShare(long holdMs, long soldQty, long positionQty) {
        if (holdMs <= 0L || soldQty <= 0L) {
            return 0L;
        }
        if (positionQty <= soldQty) {
            return holdMs;
        }
        return (holdMs * soldQty) / positionQty;
    }

    private void rememberMatchedSell(Delta delta) {
        if (delta == null || delta.isBuy || delta.slot < 0) {
            return;
        }
        recentMatchedSellBySlot.put(delta.slot, new MatchedSellMarker(delta.itemId, delta.price, delta.closedAtMs()));
    }

    private boolean hasRecentMatchedSell(Delta delta) {
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
        return Math.abs(delta.closedAtMs() - marker.tsClientMs) <= COMPLETION_MARKER_MAX_AGE_MS;
    }

    private void clearMatchedSell(Delta delta) {
        if (delta == null || delta.slot < 0) {
            return;
        }
        recentMatchedSellBySlot.remove(delta.slot);
    }

    /**
     * Fold one conversion the player recorded into the running aggregates.
     *
     * <p>It counts as one completed sale of the thing produced, carrying the cost of everything
     * that went into it. The trades it used were taken out of the replay before it started, so
     * nothing here is counted twice. Held time is deliberately not touched: the cache measures
     * that from purchases it watched go in and out, and a conversion's own span is not a
     * holding of the item it produced.</p>
     */
    static void addRecordedActivity(Map<Integer, ItemAgg> itemAggs,
                                    Totals totals,
                                    RecipeFlipLedger.Activity activity) {
        if (activity == null || activity.itemId <= 0) {
            return;
        }
        ItemAgg agg = itemAggs.computeIfAbsent(activity.itemId, ItemAgg::new);
        agg.buyCost += activity.costGp;
        agg.sellRevenue += activity.revenueGp;
        agg.buyQty += activity.quantity;
        agg.sellQty += activity.quantity;
        agg.taxPaid += activity.taxGp;
        agg.completedSells += 1;
        if (agg.lastSellTs == null || activity.completionTsMs > agg.lastSellTs) {
            agg.lastSellTs = activity.completionTsMs;
        }
        totals.totalProfit += activity.profitGp();
        totals.totalCost += activity.costGp;
        totals.totalQty += activity.quantity;
        totals.totalTax += activity.taxGp;
        totals.totalCompleted += 1;
        if (totals.lastSellTs == null || activity.completionTsMs > totals.lastSellTs) {
            totals.lastSellTs = activity.completionTsMs;
        }
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
        private long qty;
        private long cost;
        /**
         * How much of the quantity was made rather than bought. A deferred sale
         * covered by a sibling's conversion has no shortfall left to ask about,
         * so this is what tells it apart from stock simply bought afterwards.
         */
        private long convertedQty;
        /** How much of the quantity belongs to a break that is still open. */
        private long breakQty;
        /**
         * Everything that has entered the pool since it was last empty: the
         * size of the open position, which is what a sale closes a share of.
         */
        private long positionQty;
        private Long firstBuyTs;

        private LocalInventoryState() {
        }
    }


    static final class ItemAgg {
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

        private ItemAgg(int itemId) {
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
