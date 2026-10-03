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
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class StatsCacheDelta {
    private static final long COMPLETION_MARKER_MAX_AGE_MS = 5_000L;

    private final Map<Integer, ItemAgg> itemAggs;
    private final Map<Integer, LocalInventoryState> inventory;
    private final Map<Integer, MatchedSellMarker> recentMatchedSellBySlot;
    private final Totals totals;

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
            // Only what is held opens or adds to a position; units that completed a sale made
            // just before (Lots) were sold as soon as they were bought.
            long held = state.buy(delta.deltaQty, delta.deltaGp, delta.tsClientMs);
            if (held > 0) {
                state.positionQty += held;
                if (state.firstBuyTs == null || delta.tsClientMs < state.firstBuyTs) {
                    state.firstBuyTs = delta.tsClientMs;
                }
            }
            return;
        }

        boolean includeInStats = sellSinceMs == null || delta.closedAtMs() >= sellSinceMs;

        // Paired as the flip-history ledger pairs it (Lots): the newest purchase first.
        long matchQty = Math.max(0L, Math.min((long) delta.deltaQty, state.qty));
        long matchCost = 0L;
        long matchRevenue = 0L;
        Long matchedBuyTs = state.firstBuyTs;
        long positionQty = state.positionQty;
        if (matchQty > 0) {
            matchRevenue = delta.deltaGp;
            if (matchQty < delta.deltaQty) {
                matchRevenue = (delta.deltaGp * matchQty) / delta.deltaQty;
            }
            matchCost = state.take(matchQty);
            if (state.qty <= 0) {
                // Sold out: the next purchase opens a new position.
                state.positionQty = 0L;
                state.firstBuyTs = null;
            }
        }
        // The rest waits for a purchase, as in the flip history. Bought after the sale, it was
        // never held, so it adds no time. Out of the range, it is still not stock.
        state.owe(delta.deltaQty - matchQty, delta.deltaGp - matchRevenue, delta.closedAtMs(),
            (quantity, cost, revenue) -> {
                if (includeInStats) {
                    book(delta, quantity, cost, revenue, 0L);
                }
            });

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

        if (matchedBuyTs == null) {
            matchedBuyTs = delta.tsClientMs;
        }
        ItemAgg agg = book(delta, matchQty, matchCost, matchRevenue,
            heldShare(delta.closedAtMs() - matchedBuyTs, matchQty, positionQty));
        if (agg.firstBuyTs == null || matchedBuyTs < agg.firstBuyTs) {
            agg.firstBuyTs = matchedBuyTs;
        }
        if (isCompletion) {
            agg.completedSells += 1;
            totals.totalCompleted += 1;
            clearMatchedSell(delta);
        }
        if (totals.firstBuyTs == null || agg.firstBuyTs < totals.firstBuyTs) {
            totals.firstBuyTs = agg.firstBuyTs;
        }
    }

    /** Sold units, paired with what they cost, into the item's figures and the totals. */
    private ItemAgg book(Delta sale, long quantity, long cost, long revenue, long heldMs) {
        ItemAgg agg = itemAggs.computeIfAbsent(sale.itemId, ItemAgg::new);
        long tax = Math.max(0L, GeTax.forSale(sale.itemId, sale.price, quantity));
        revenue = Math.max(0L, revenue);
        agg.buyCost += cost;
        agg.buyQty += quantity;
        agg.sellRevenue += revenue;
        agg.sellQty += quantity;
        agg.taxPaid += tax;
        agg.activeMs += heldMs;
        totals.totalProfit += revenue - cost;
        totals.totalCost += cost;
        totals.totalQty += quantity;
        totals.totalTax += tax;
        totals.totalActiveMs += heldMs;
        if (agg.lastSellTs == null || sale.closedAtMs() > agg.lastSellTs) {
            agg.lastSellTs = sale.closedAtMs();
        }
        if (totals.lastSellTs == null || agg.lastSellTs > totals.lastSellTs) {
            totals.lastSellTs = agg.lastSellTs;
        }
        return agg;
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

    static final class LocalInventoryState extends Lots {
        /**
         * Everything that has entered the pool since it was last empty: the
         * size of the open position, which is what a sale closes a share of.
         */
        private long positionQty;
        private Long firstBuyTs;

        private LocalInventoryState() {
        }
    }

    @RequiredArgsConstructor
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
    }

    @RequiredArgsConstructor
    static final class MatchedSellMarker {
        private final int itemId;
        private final int price;
        private final long tsClientMs;
    }
}
