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

        Map<Integer, InventoryState> inventoryByItem = new HashMap<>();
        Map<Integer, PendingSellFlip> pendingSellBySlot = new HashMap<>();
        for (Delta delta : snapshot) {
            if (delta == null || delta.itemId <= 0) {
                continue;
            }
            boolean isCompletion = "OFFER_COMPLETED".equals(delta.eventType);
            if (delta.deltaQty <= 0 && !isCompletion) {
                continue;
            }
            // A slot holds one offer at a time, so another item turning up in it means the sale
            // waiting there is over -- cancelled part-sold, most often, with no completion ever
            // seen. What it sold is still sold: dropping it here took a real sale out of TOTAL
            // PROFIT while the stats cache kept it, 362,490 gp on one player's single item.
            PendingSellFlip abandoned = pendingSellBySlot.get(resolveSlotKey(delta));
            if (abandoned != null && abandoned.itemId != delta.itemId && abandoned.matchedQty > 0) {
                pendingSellBySlot.remove(resolveSlotKey(delta));
                if (sinceMs == null || abandoned.lastSellTsMs >= sinceMs) {
                    recordPendingFlip(byItem, abandoned, abandoned.itemId, abandoned.lastSellTsMs, 0, false);
                }
            }

            InventoryState inventory = inventoryByItem.computeIfAbsent(delta.itemId, ignored -> new InventoryState());
            if (delta.isBuy) {
                if (delta.deltaQty <= 0) {
                    continue;
                }
                inventory.qty += delta.deltaQty;
                inventory.cost += Math.max(0L, delta.deltaGp);
                continue;
            }

            long matchQty = Math.max(0L, Math.min((long) delta.deltaQty, inventory.qty));
            if (inventory.qty <= 0 || delta.deltaQty <= 0 || matchQty <= 0) {
                if (isCompletion) {
                    finalizePendingFlip(byItem, pendingSellBySlot, delta, sinceMs);
                }
                continue;
            }

            long matchRevenue = Math.max(0L, delta.deltaGp);
            if (matchQty < delta.deltaQty) {
                matchRevenue = (matchRevenue * matchQty) / delta.deltaQty;
            }

            long matchCost;
            if (matchQty >= inventory.qty) {
                matchCost = inventory.cost;
                inventory.qty = 0L;
                inventory.cost = 0L;
            } else {
                matchCost = (inventory.cost * matchQty) / inventory.qty;
                inventory.qty -= matchQty;
                inventory.cost = Math.max(0L, inventory.cost - matchCost);
            }

            int slotKey = resolveSlotKey(delta);
            PendingSellFlip pending = pendingSellBySlot.computeIfAbsent(slotKey, ignored -> new PendingSellFlip(delta.itemId));
            if (pending.itemId != delta.itemId) {
                pending = new PendingSellFlip(delta.itemId);
                pendingSellBySlot.put(slotKey, pending);
            }
            pending.matchedQty += matchQty;
            pending.matchedCost += matchCost;
            pending.matchedRevenue += matchRevenue;
            // Per fill, on the units matched here, exactly as the stats cache
            // books it - so the two ledgers name the same tax for the same offer.
            pending.matchedTax += GeTax.forSale(delta.itemId, delta.price, matchQty);
            pending.lastSellTsMs = Math.max(pending.lastSellTsMs, delta.closedAtMs());
            if (delta.price > 0) {
                pending.lastSellPriceGp = delta.price;
            }

            if (isCompletion) {
                finalizePendingFlip(byItem, pendingSellBySlot, delta, sinceMs);
            }
        }

        flushOpenSellFlips(byItem, pendingSellBySlot, sinceMs);

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

    private static void finalizePendingFlip(Map<Integer, List<StatsFlipInstance>> byItem,
                                            Map<Integer, PendingSellFlip> pendingSellBySlot,
                                            Delta completion,
                                            Long sinceMs) {
        if (byItem == null || pendingSellBySlot == null || completion == null || completion.itemId <= 0) {
            return;
        }
        int slotKey = resolveSlotKey(completion);
        PendingSellFlip pending = pendingSellBySlot.remove(slotKey);
        if (pending == null || pending.itemId != completion.itemId || pending.matchedQty <= 0) {
            return;
        }

        long completionTsMs = completion.closedAtMs() > 0
            ? completion.closedAtMs()
            : Math.max(0L, pending.lastSellTsMs);
        if (sinceMs != null && completionTsMs < sinceMs) {
            return;
        }

        recordPendingFlip(byItem, pending, completion.itemId, completionTsMs, completion.price, false);
    }

    /**
     * Sell offers that have filled part-way and are still sitting in the Grand
     * Exchange when the replay runs out of deltas.
     *
     * <p>The coins are real and the running totals already hold them, so the
     * ledger has to hold them too: the totals are reconciled against it, and
     * what the ledger has never heard of is erased. Recorded as in progress
     * rather than as a flip - the offer has not finished, and it will be
     * finalized properly by the completion when it arrives.
     */
    private static void flushOpenSellFlips(Map<Integer, List<StatsFlipInstance>> byItem,
                                           Map<Integer, PendingSellFlip> pendingSellBySlot,
                                           Long sinceMs) {
        if (byItem == null || pendingSellBySlot == null || pendingSellBySlot.isEmpty()) {
            return;
        }
        for (PendingSellFlip pending : pendingSellBySlot.values()) {
            if (pending == null || pending.itemId <= 0 || pending.matchedQty <= 0) {
                continue;
            }
            long tsMs = Math.max(0L, pending.lastSellTsMs);
            if (sinceMs != null && tsMs < sinceMs) {
                continue;
            }
            recordPendingFlip(byItem, pending, pending.itemId, tsMs, 0, true);
        }
        pendingSellBySlot.clear();
    }

    private static void recordPendingFlip(Map<Integer, List<StatsFlipInstance>> byItem,
                                          PendingSellFlip pending,
                                          int itemId,
                                          long tsMs,
                                          int fallbackSellPrice,
                                          boolean inProgress) {
        long qty = pending.matchedQty;
        long buyCost = Math.max(0L, pending.matchedCost);
        long sellRevenue = Math.max(0L, pending.matchedRevenue);
        // Use integer division so displayed per-item prices never round up above realized totals.
        long buyPrice = Math.max(0L, pending.matchedCost / qty);
        long sellPrice = Math.max(0L, pending.matchedRevenue / qty);
        if (sellPrice <= 0L) {
            sellPrice = fallbackSellPrice > 0
                ? fallbackSellPrice
                : pending.lastSellPriceGp > 0
                    ? pending.lastSellPriceGp
                    : 0L;
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

    private static final class InventoryState {
        private long qty;
        private long cost;
    }

    @RequiredArgsConstructor
    private static final class PendingSellFlip {
        private final int itemId;
        private long matchedQty;
        private long matchedCost;
        private long matchedRevenue;
        private long matchedTax;
        private long lastSellPriceGp;
        private long lastSellTsMs;
    }
}
