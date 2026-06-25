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

final class GeHistoryAutoSyncTradeMatcher {
    private GeHistoryAutoSyncTradeMatcher() {
    }

    static SelectionPlan planMissingTrades(
        List<GeHistoryTrade> historyTrades,
        Map<TradeSignature, Integer> existingCounts,
        Map<BaseSignature, Integer> existingQtyByBase
    ) {
        if (historyTrades == null || historyTrades.isEmpty()) {
            return SelectionPlan.empty();
        }

        Map<TradeSignature, Integer> exactCounts = copyCounts(existingCounts);
        Map<BaseSignature, Integer> qtyByBase = copyCounts(existingQtyByBase);
        GeHistoryTrade[] missingByIndex = new GeHistoryTrade[historyTrades.size()];
        List<GeHistoryTrade> missing = new ArrayList<>();

        // GE history UI order is newest-first; process oldest->newest for stable matching.
        for (int i = historyTrades.size() - 1; i >= 0; i--) {
            GeHistoryTrade trade = historyTrades.get(i);
            TradeSignature signature = signatureForTrade(trade);
            if (signature == null) {
                continue;
            }
            BaseSignature base = baseForTrade(trade);
            int exact = exactCounts.getOrDefault(signature, 0);
            if (exact > 0) {
                consumeExact(exactCounts, signature);
                consumeQty(qtyByBase, base, trade.quantity);
                continue;
            }
            int availableQty = qtyByBase.getOrDefault(base, 0);
            if (availableQty >= trade.quantity) {
                consumeQty(qtyByBase, base, trade.quantity);
                continue;
            }
            int missingQty = trade.quantity - Math.max(0, availableQty);
            if (availableQty > 0) {
                consumeQty(qtyByBase, base, availableQty);
            }
            GeHistoryTrade residualTrade = buildResidualTrade(trade, missingQty);
            if (residualTrade == null) {
                continue;
            }
            missingByIndex[i] = residualTrade;
            missing.add(residualTrade);
        }
        return new SelectionPlan(missingByIndex, missing);
    }

    static List<GeHistoryTrade> selectMissingTrades(
        List<GeHistoryTrade> historyTrades,
        Map<TradeSignature, Integer> existingCounts,
        Map<BaseSignature, Integer> existingQtyByBase
    ) {
        return planMissingTrades(historyTrades, existingCounts, existingQtyByBase).missingTrades;
    }

    private static GeHistoryTrade buildResidualTrade(GeHistoryTrade trade, int missingQty) {
        if (trade == null || !trade.isValid() || missingQty <= 0 || missingQty >= trade.quantity) {
            if (trade != null && trade.isValid() && missingQty == trade.quantity) {
                return trade;
            }
            return null;
        }
        long scaledTotalGp = scaleTotalGp(trade, missingQty);
        if (scaledTotalGp <= 0L) {
            return null;
        }
        return new GeHistoryTrade(trade.itemId, trade.isBuy, missingQty, trade.price, scaledTotalGp);
    }

    private static long scaleTotalGp(GeHistoryTrade trade, int quantity) {
        if (trade == null || !trade.isValid() || quantity <= 0 || trade.quantity <= 0) {
            return 0L;
        }
        if (quantity >= trade.quantity) {
            return trade.totalGp;
        }
        long scaled = (trade.totalGp * (long) quantity) / (long) trade.quantity;
        if (scaled > 0L) {
            return scaled;
        }
        return Math.max(1L, (long) trade.price * (long) quantity);
    }

    static Map<TradeSignature, Integer> buildObservedTradeCounts(List<LocalTradeDelta> deltas) {
        Map<TradeSignature, Integer> counts = new HashMap<>();
        if (deltas == null || deltas.isEmpty()) {
            return counts;
        }

        for (LocalTradeDelta delta : deltas) {
            if (delta == null || delta.itemId <= 0) {
                continue;
            }
            if (delta.deltaQty <= 0) {
                continue;
            }
            int unitPrice = resolveUnitPrice(delta);
            if (unitPrice <= 0) {
                continue;
            }
            incrementCount(counts, new TradeSignature(
                delta.itemId,
                delta.isBuy,
                delta.deltaQty,
                unitPrice
            ));
        }
        return counts;
    }

    static Map<BaseSignature, Integer> buildObservedQuantityByBase(List<LocalTradeDelta> deltas) {
        Map<BaseSignature, Integer> totals = new HashMap<>();
        if (deltas == null || deltas.isEmpty()) {
            return totals;
        }
        for (LocalTradeDelta delta : deltas) {
            if (delta == null || delta.itemId <= 0 || delta.deltaQty <= 0) {
                continue;
            }
            int unitPrice = resolveUnitPrice(delta);
            if (unitPrice <= 0) {
                continue;
            }
            BaseSignature base = new BaseSignature(delta.itemId, delta.isBuy, unitPrice);
            int next = totals.getOrDefault(base, 0) + delta.deltaQty;
            totals.put(base, next);
        }
        return totals;
    }

    private static int resolveUnitPrice(LocalTradeDelta delta) {
        if (delta == null) {
            return 0;
        }
        if (delta.price > 0) {
            return delta.price;
        }
        if (delta.deltaQty <= 0) {
            return 0;
        }
        long total = Math.max(0L, LocalTradeDeltaUtils.normalizeDeltaValue(delta));
        if (total <= 0L) {
            return 0;
        }
        return (int) Math.max(1L, total / (long) delta.deltaQty);
    }

    static TradeSignature signatureForTrade(GeHistoryTrade trade) {
        if (trade == null || !trade.isValid()) {
            return null;
        }
        int unitPrice = trade.price > 0
            ? trade.price
            : (trade.quantity > 0 ? (int) Math.max(1L, trade.totalGp / (long) trade.quantity) : 0);
        if (unitPrice <= 0) {
            return null;
        }
        return new TradeSignature(trade.itemId, trade.isBuy, trade.quantity, unitPrice);
    }

    static TradeSignature signatureForDelta(LocalTradeDelta delta) {
        if (delta == null || delta.itemId <= 0 || delta.deltaQty <= 0) {
            return null;
        }
        int unitPrice = resolveUnitPrice(delta);
        if (unitPrice <= 0) {
            return null;
        }
        return new TradeSignature(delta.itemId, delta.isBuy, delta.deltaQty, unitPrice);
    }

    private static BaseSignature baseForTrade(GeHistoryTrade trade) {
        if (trade == null || !trade.isValid()) {
            return null;
        }
        int unitPrice = trade.price > 0
            ? trade.price
            : (trade.quantity > 0 ? (int) Math.max(1L, trade.totalGp / (long) trade.quantity) : 0);
        if (unitPrice <= 0) {
            return null;
        }
        return new BaseSignature(trade.itemId, trade.isBuy, unitPrice);
    }

    private static void incrementCount(Map<TradeSignature, Integer> counts, TradeSignature signature) {
        if (counts == null || signature == null) {
            return;
        }
        counts.put(signature, counts.getOrDefault(signature, 0) + 1);
    }

    private static <K> Map<K, Integer> copyCounts(Map<K, Integer> input) {
        Map<K, Integer> copy = new HashMap<>();
        if (input == null || input.isEmpty()) {
            return copy;
        }
        for (Map.Entry<K, Integer> entry : input.entrySet()) {
            if (entry == null || entry.getKey() == null) {
                continue;
            }
            int count = entry.getValue() != null ? entry.getValue() : 0;
            if (count > 0) {
                copy.put(entry.getKey(), count);
            }
        }
        return copy;
    }

    private static void consumeExact(Map<TradeSignature, Integer> counts, TradeSignature signature) {
        if (counts == null || signature == null) {
            return;
        }
        int current = counts.getOrDefault(signature, 0);
        if (current <= 1) {
            counts.remove(signature);
            return;
        }
        counts.put(signature, current - 1);
    }

    private static void consumeQty(Map<BaseSignature, Integer> quantities, BaseSignature base, int quantity) {
        if (quantities == null || base == null || quantity <= 0) {
            return;
        }
        int current = quantities.getOrDefault(base, 0);
        if (current <= quantity) {
            quantities.remove(base);
            return;
        }
        quantities.put(base, current - quantity);
    }

    static final class TradeSignature {
        private final int itemId;
        private final boolean isBuy;
        private final int quantity;
        private final int unitPrice;

        TradeSignature(int itemId, boolean isBuy, int quantity, int unitPrice) {
            this.itemId = itemId;
            this.isBuy = isBuy;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TradeSignature)) {
                return false;
            }
            TradeSignature that = (TradeSignature) other;
            return itemId == that.itemId
                && isBuy == that.isBuy
                && quantity == that.quantity
                && unitPrice == that.unitPrice;
        }

        @Override
        public int hashCode() {
            int hash = Integer.hashCode(itemId);
            hash = 31 * hash + Boolean.hashCode(isBuy);
            hash = 31 * hash + Integer.hashCode(quantity);
            hash = 31 * hash + Integer.hashCode(unitPrice);
            return hash;
        }
    }

    static final class BaseSignature {
        private final int itemId;
        private final boolean isBuy;
        private final int unitPrice;

        BaseSignature(int itemId, boolean isBuy, int unitPrice) {
            this.itemId = itemId;
            this.isBuy = isBuy;
            this.unitPrice = unitPrice;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof BaseSignature)) {
                return false;
            }
            BaseSignature that = (BaseSignature) other;
            return itemId == that.itemId && isBuy == that.isBuy && unitPrice == that.unitPrice;
        }

        @Override
        public int hashCode() {
            int hash = Integer.hashCode(itemId);
            hash = 31 * hash + Boolean.hashCode(isBuy);
            hash = 31 * hash + Integer.hashCode(unitPrice);
            return hash;
        }
    }

    static final class SelectionPlan {
        final GeHistoryTrade[] missingByIndex;
        final List<GeHistoryTrade> missingTrades;

        SelectionPlan(GeHistoryTrade[] missingByIndex, List<GeHistoryTrade> missingTrades) {
            this.missingByIndex = missingByIndex != null ? missingByIndex : new GeHistoryTrade[0];
            this.missingTrades = missingTrades != null ? missingTrades : new ArrayList<>();
        }

        static SelectionPlan empty() {
            return new SelectionPlan(new GeHistoryTrade[0], new ArrayList<>());
        }

        boolean isMissing(int index) {
            return index >= 0
                && index < missingByIndex.length
                && missingByIndex[index] != null;
        }

        GeHistoryTrade missingTradeAt(int index) {
            if (index < 0 || index >= missingByIndex.length) {
                return null;
            }
            return missingByIndex[index];
        }
    }
}
