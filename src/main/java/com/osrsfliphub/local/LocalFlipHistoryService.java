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

@javax.inject.Singleton
final class LocalFlipHistoryService {
    private static final long LOCAL_EVENT_BUCKET_MS = 600L;

    @javax.inject.Inject
    LocalFlipHistoryService() {
    }

    Map<Integer, List<StatsFlipInstance>> buildHistory(List<LocalTradeDelta> deltas, Long sinceMs) {
        Map<Integer, List<StatsFlipInstance>> byItem = new HashMap<>();
        if (deltas == null || deltas.isEmpty()) {
            return byItem;
        }

        List<LocalTradeDelta> snapshot = new ArrayList<>(deltas);
        snapshot.sort(Comparator
            .comparingLong((LocalTradeDelta delta) -> delta != null ? delta.tsClientMs / LOCAL_EVENT_BUCKET_MS : 0L)
            .thenComparingInt(delta -> delta != null && delta.isBuy ? 0 : 1)
            .thenComparingLong(delta -> delta != null ? delta.tsClientMs : 0L));

        Map<Integer, InventoryState> inventoryByItem = new HashMap<>();
        Map<Integer, PendingSellFlip> pendingSellBySlot = new HashMap<>();
        for (LocalTradeDelta delta : snapshot) {
            if (delta == null || delta.itemId <= 0) {
                continue;
            }
            boolean isCompletion = "OFFER_COMPLETED".equals(delta.eventType);
            if (delta.deltaQty <= 0 && !isCompletion) {
                continue;
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

            if (inventory.qty <= 0 || delta.deltaQty <= 0) {
                if (isCompletion) {
                    finalizePendingFlip(byItem, pendingSellBySlot, delta, sinceMs);
                }
                continue;
            }

            long matchQty = Math.min((long) delta.deltaQty, inventory.qty);
            if (matchQty <= 0) {
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
            pending.lastSellTsMs = Math.max(pending.lastSellTsMs, delta.tsClientMs);
            if (delta.price > 0) {
                pending.lastSellPriceGp = delta.price;
            }

            if (isCompletion) {
                finalizePendingFlip(byItem, pendingSellBySlot, delta, sinceMs);
            }
        }

        for (List<StatsFlipInstance> history : byItem.values()) {
            history.sort(Comparator.comparingLong((StatsFlipInstance instance) -> instance.completionTsMs).reversed());
        }
        return byItem;
    }

    private static int resolveSlotKey(LocalTradeDelta delta) {
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
                                            LocalTradeDelta completion,
                                            Long sinceMs) {
        if (byItem == null || pendingSellBySlot == null || completion == null || completion.itemId <= 0) {
            return;
        }
        int slotKey = resolveSlotKey(completion);
        PendingSellFlip pending = pendingSellBySlot.remove(slotKey);
        if (pending == null || pending.itemId != completion.itemId || pending.matchedQty <= 0) {
            return;
        }

        long completionTsMs = completion.tsClientMs > 0
            ? completion.tsClientMs
            : Math.max(0L, pending.lastSellTsMs);
        if (sinceMs != null && completionTsMs < sinceMs) {
            return;
        }

        long qty = pending.matchedQty;
        long buyCost = Math.max(0L, pending.matchedCost);
        long sellRevenue = Math.max(0L, pending.matchedRevenue);
        // Use integer division so displayed per-item prices never round up above realized totals.
        long buyPrice = Math.max(0L, pending.matchedCost / qty);
        long sellPrice = Math.max(0L, pending.matchedRevenue / qty);
        if (sellPrice <= 0L) {
            sellPrice = completion.price > 0
                ? completion.price
                : pending.lastSellPriceGp > 0
                    ? pending.lastSellPriceGp
                    : 0L;
        }
        long profit = sellRevenue - buyCost;
        StatsFlipInstance instance = new StatsFlipInstance(
            completion.itemId,
            buyPrice,
            sellPrice,
            buyCost,
            sellRevenue,
            profit,
            (int) Math.min(Integer.MAX_VALUE, qty),
            completionTsMs
        );
        byItem.computeIfAbsent(completion.itemId, ignored -> new ArrayList<>()).add(instance);
    }

    private static final class InventoryState {
        private long qty;
        private long cost;
    }

    private static final class PendingSellFlip {
        private final int itemId;
        private long matchedQty;
        private long matchedCost;
        private long matchedRevenue;
        private long lastSellPriceGp;
        private long lastSellTsMs;

        private PendingSellFlip(int itemId) {
            this.itemId = itemId;
        }
    }
}
