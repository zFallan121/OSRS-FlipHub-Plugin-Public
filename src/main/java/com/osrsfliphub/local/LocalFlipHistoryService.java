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
import lombok.RequiredArgsConstructor;

@javax.inject.Singleton
@RequiredArgsConstructor
final class LocalFlipHistoryService {
    private final RecipeFlipStore recipeFlips;

    @javax.inject.Inject
    LocalFlipHistoryService() {
        this(null);
    }

    /** The store is passed in by tests; in the running plugin it is looked up. */

    /** One entry per conversion the player recorded, filed against what it produced. */
    private void appendRecordedConversions(Map<Integer, List<StatsFlipInstance>> byItem,
                                                  RecipeFlipLedger.Result recorded,
                                                  Long sinceMs,
                                                  long accountKey) {
        for (RecipeFlipLedger.Activity activity : recorded.activities) {
            if (sinceMs != null && activity.completionTsMs < sinceMs) {
                continue;
            }
            int quantity = Math.max(1, activity.quantity);
            byItem.computeIfAbsent(activity.itemId, ignored -> new ArrayList<>())
                .add(new StatsFlipInstance(
                    activity.itemId,
                    activity.costGp / quantity,
                    activity.revenueGp / quantity,
                    activity.costGp,
                    activity.revenueGp,
                    activity.profitGp(),
                    activity.quantity,
                    activity.completionTsMs,
                    false,
                    activity.taxGp,
                    activity.kind,
                    activity.name));
        }
    }

    Map<Integer, List<StatsFlipInstance>> buildHistory(List<Delta> deltas, Long sinceMs) {
        return buildHistory(deltas, sinceMs, 0L);
    }

    /**
     * @param accountKey whose trades these are: which recorded recipes apply,
     *                   and which stock other accounts moved to this one. 0
     *                   when unknown, which applies none of either.
     */
    Map<Integer, List<StatsFlipInstance>> buildHistory(List<Delta> deltas, Long sinceMs, long accountKey) {
        Map<Integer, List<StatsFlipInstance>> byItem = new HashMap<>();
        if (deltas == null || deltas.isEmpty()) {
            return byItem;
        }

        // Conversions the player recorded are priced first, and the trades they used are taken
        // out of what follows, so the ordinary replay below never sees a unit that has already
        // been accounted for and needs to know nothing about conversions at all. Purchases another
        // account recorded as moved to this one join it as if they had been made here.
        RecipeFlipLedger.Result recorded = RecipeFlipLedger.apply(deltas, recipeFlips, accountKey);
        List<Delta> snapshot = new ArrayList<>(recorded.remainingTrades(recorded.trades));
        snapshot.sort(TradeDeltaUtils.replayOrder());
        appendRecordedConversions(byItem, recorded, sinceMs, accountKey);

        Map<Integer, Lots> inventoryByItem = new HashMap<>();
        Map<Integer, PendingSellFlip> pendingSellBySlot = new HashMap<>();
        // Offers over, in the order they ended. Written up only once the replay is done, because a
        // purchase up to two minutes after a sale can still complete it (Lots).
        List<PendingSellFlip> ended = new ArrayList<>();
        for (Delta delta : snapshot) {
            if (delta == null || delta.itemId <= 0) {
                continue;
            }
            boolean isCompletion = "OFFER_COMPLETED".equals(delta.eventType);
            if (delta.deltaQty <= 0 && !isCompletion) {
                continue;
            }
            // One flip per sell offer, however many pieces it sold in (the owner's rule, 2 Oct 2026).
            // A slot holds one offer at a time, so anything on it that is not this offer's next piece
            // means the sale waiting there is over -- cancelled part-sold, most often, with no
            // completion ever seen. What it sold is still sold: dropping it here took a real sale out
            // of TOTAL PROFIT while the stats cache kept it, 362,490 gp on one player's single item.
            // Folding it into the next sale of the same item on that slot counted two offers as one.
            PendingSellFlip abandoned = pendingSellBySlot.get(resolveSlotKey(delta));
            if (abandoned != null && !TradeOfferCollapser.sameOffer(abandoned.first, delta)) {
                ended.add(pendingSellBySlot.remove(resolveSlotKey(delta)));
            }

            Lots inventory = inventoryByItem.computeIfAbsent(delta.itemId, ignored -> new Lots());
            if (delta.isBuy) {
                inventory.buy(delta.deltaQty, delta.deltaGp, delta.tsClientMs);
                continue;
            }

            long matchQty = Math.max(0L, Math.min((long) delta.deltaQty, inventory.qty));
            long matchRevenue = Math.max(0L, delta.deltaGp);
            if (matchQty < delta.deltaQty) {
                matchRevenue = (matchRevenue * matchQty) / delta.deltaQty;
            }

            PendingSellFlip pending = pendingSellBySlot.computeIfAbsent(resolveSlotKey(delta), ignored -> new PendingSellFlip(delta));
            pending.book(delta, matchQty, inventory.take(matchQty), matchRevenue);
            inventory.owe(delta.deltaQty - matchQty, Math.max(0L, delta.deltaGp) - matchRevenue, delta.closedAtMs(),
                (quantity, cost, revenue) -> pending.book(delta, quantity, cost, revenue));

            // A record that ended is a whole offer: one the slot moved on from is as finished as one
            // that completed.
            if (isCompletion || delta.endMs > 0) {
                pendingSellBySlot.remove(resolveSlotKey(delta));
                pending.endMs = delta.closedAtMs();
                if (delta.price > 0) {
                    pending.lastSellPriceGp = delta.price;
                }
                ended.add(pending);
            }
        }

        for (PendingSellFlip flip : ended) {
            recordPendingFlip(byItem, flip, false, sinceMs);
        }
        // Sell offers that have filled part-way and are still sitting in the Grand Exchange when
        // the replay runs out of deltas. The coins are real and the running totals already hold
        // them, so the ledger has to hold them too: the totals are reconciled against it, and what
        // the ledger has never heard of is erased. Recorded as in progress rather than as a flip -
        // the offer has not finished, and it will be finalized properly by the completion when it
        // arrives.
        for (PendingSellFlip flip : pendingSellBySlot.values()) {
            recordPendingFlip(byItem, flip, true, sinceMs);
        }

        for (List<StatsFlipInstance> history : byItem.values()) {
            history.sort(Comparator.comparingLong((StatsFlipInstance instance) -> instance.completionTsMs).reversed());
        }
        return byItem;
    }

    private static int resolveSlotKey(Delta delta) {
        if (delta == null) {
            return Integer.MIN_VALUE;
        }
        if (delta.slot >= 0) {
            return delta.slot;
        }
        return -1 - Math.max(0, delta.itemId);
    }

    /** One offer's flip, when anything it sold found stock and it falls in the range. */
    private static void recordPendingFlip(Map<Integer, List<StatsFlipInstance>> byItem,
                                          PendingSellFlip pending,
                                          boolean inProgress,
                                          Long sinceMs) {
        long qty = pending.matchedQty;
        long tsMs = pending.endMs > 0 ? pending.endMs : Math.max(0L, pending.lastSellTsMs);
        if (qty <= 0 || sinceMs != null && tsMs < sinceMs) {
            return;
        }
        int itemId = pending.first.itemId;
        long buyCost = Math.max(0L, pending.matchedCost);
        long sellRevenue = Math.max(0L, pending.matchedRevenue);
        // Use integer division so displayed per-item prices never round up above realized totals.
        long buyPrice = Math.max(0L, pending.matchedCost / qty);
        long sellPrice = Math.max(0L, pending.matchedRevenue / qty);
        if (sellPrice <= 0L) {
            sellPrice = pending.lastSellPriceGp;
        }
        long profit = sellRevenue - buyCost;
        StatsFlipInstance instance = new StatsFlipInstance(
            itemId,
            buyPrice,
            sellPrice,
            buyCost,
            sellRevenue,
            profit,
            (int) Math.min(Integer.MAX_VALUE, qty),
            tsMs,
            inProgress,
            pending.matchedTax
        );
        byItem.computeIfAbsent(itemId, ignored -> new ArrayList<>()).add(instance);
    }

    @RequiredArgsConstructor
    private static final class PendingSellFlip {
        /** The offer's first piece: what tells its next piece from another offer on the slot. */
        private final Delta first;
        private long matchedQty;
        private long matchedCost;
        private long matchedRevenue;
        private long matchedTax;
        private long lastSellPriceGp;
        private long lastSellTsMs;
        /** When the offer ended; 0 while it is open or when the slot moved on from it. */
        private long endMs;

        /** Units of {@code sale} paired with stock, at once or by a purchase made just after it. */
        void book(Delta sale, long quantity, long cost, long revenue) {
            if (quantity > 0) {
                matchedQty += quantity;
                matchedCost += cost;
                matchedRevenue += revenue;
                // Per fill, on the units matched here, exactly as the stats cache
                // books it - so the two ledgers name the same tax for the same offer.
                matchedTax += GeTax.forSale(sale.itemId, sale.price, quantity);
                lastSellTsMs = Math.max(lastSellTsMs, sale.closedAtMs());
                if (sale.price > 0) {
                    lastSellPriceGp = sale.price;
                }
            }
        }
    }
}
