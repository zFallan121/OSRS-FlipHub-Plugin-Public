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
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which rows of the in-game history the plugin has not already recorded.
 */
final class GeHistoryAutoSyncTradeMatcher {
    /**
     * How far a lot's coins may sit from a row's before they stop being the same
     * offer: one coin per unit traded.
     */
    static final long TOLERANCE_COINS_PER_UNIT = 1L;

    private GeHistoryAutoSyncTradeMatcher() {
    }

    static SelectionPlan planMissingTrades(List<GeHistoryTrade> historyTrades, List<LocalTradeDelta> existingDeltas) {
        if (historyTrades == null || historyTrades.isEmpty()) {
            return SelectionPlan.empty();
        }

        Map<ItemSide, List<OfferLot>> lotsByItemSide = indexByItemSide(groupOffers(existingDeltas));
        int size = historyTrades.size();
        GeHistoryTrade[] missingByIndex = new GeHistoryTrade[size];
        boolean[] unexplained = new boolean[size];

        // GE history UI order is newest-first; process oldest->newest for stable matching.
        for (int i = size - 1; i >= 0; i--) {
            GeHistoryTrade trade = historyTrades.get(i);
            int unitPrice = resolveUnitPrice(trade);
            if (trade == null || !trade.isValid() || unitPrice <= 0) {
                continue;
            }
            List<OfferLot> lots = lotsByItemSide.get(new ItemSide(trade.itemId, trade.isBuy));
            if (claimExact(lots, trade, unitPrice) || claimWithinTolerance(lots, trade)) {
                continue;
            }
            unexplained[i] = true;
        }

        List<GeHistoryTrade> missing = new ArrayList<>();
        for (int i = size - 1; i >= 0; i--) {
            if (!unexplained[i]) {
                continue;
            }
            GeHistoryTrade trade = historyTrades.get(i);
            List<OfferLot> lots = lotsByItemSide.get(new ItemSide(trade.itemId, trade.isBuy));
            if (claimCoveredWithinOneOffer(lots, trade, resolveUnitPrice(trade))) {
                continue;
            }
            int covered = consumeAtUnitPrice(lots, resolveUnitPrice(trade), trade.quantity);
            GeHistoryTrade residualTrade = buildResidualTrade(trade, trade.quantity - covered);
            if (residualTrade == null) {
                continue;
            }
            missingByIndex[i] = residualTrade;
            missing.add(residualTrade);
        }
        return new SelectionPlan(missingByIndex, missing);
    }

    static List<GeHistoryTrade> selectMissingTrades(List<GeHistoryTrade> historyTrades, List<LocalTradeDelta> existingDeltas) {
        return planMissingTrades(historyTrades, existingDeltas).missingTrades;
    }

    static long toleranceCoins(int quantity) {
        return TOLERANCE_COINS_PER_UNIT * (long) Math.max(0, quantity);
    }

    // ---- the tiers ----

    private static boolean claimExact(List<OfferLot> lots, GeHistoryTrade trade, int unitPrice) {
        if (lots == null) {
            return false;
        }
        Iterator<OfferLot> it = lots.iterator();
        while (it.hasNext()) {
            OfferLot lot = it.next();
            if (lot.qty == trade.quantity && lot.unitPrice == unitPrice) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    /**
     * One offer's fills, kept apart by the store, that between them cover the row.
     */
    private static boolean claimCoveredWithinOneOffer(List<OfferLot> lots, GeHistoryTrade trade, int unitPrice) {
        if (lots == null || unitPrice <= 0) {
            return false;
        }
        Set<Integer> slots = new LinkedHashSet<>();
        for (OfferLot lot : lots) {
            if (lot.unitPrice == unitPrice) {
                slots.add(lot.slot);
            }
        }
        for (Integer slot : slots) {
            long available = 0L;
            for (OfferLot lot : lots) {
                if (lot.unitPrice == unitPrice && lot.slot == slot) {
                    available += lot.qty;
                }
            }
            if (available < trade.quantity) {
                continue;
            }
            consumeAtUnitPriceInSlot(lots, unitPrice, slot, trade.quantity);
            return true;
        }
        return false;
    }

    private static void consumeAtUnitPriceInSlot(List<OfferLot> lots, int unitPrice, int slot, int quantity) {
        int remaining = Math.max(0, quantity);
        Iterator<OfferLot> it = lots.iterator();
        while (it.hasNext() && remaining > 0) {
            OfferLot lot = it.next();
            if (lot.unitPrice != unitPrice || lot.slot != slot) {
                continue;
            }
            int taken = Math.min(lot.qty, remaining);
            lot.qty -= taken;
            remaining -= taken;
            if (lot.qty <= 0) {
                it.remove();
            }
        }
    }

    private static boolean claimWithinTolerance(List<OfferLot> lots, GeHistoryTrade trade) {
        if (lots == null) {
            return false;
        }
        long tolerance = toleranceCoins(trade.quantity);
        OfferLot best = null;
        long bestDistance = Long.MAX_VALUE;
        for (OfferLot lot : lots) {
            if (lot.qty != trade.quantity) {
                continue;
            }
            long distance = Math.abs(lot.gp - trade.totalGp);
            if (distance <= tolerance && distance < bestDistance) {
                best = lot;
                bestDistance = distance;
            }
        }
        if (best == null) {
            return false;
        }
        lots.remove(best);
        return true;
    }

    /** Takes up to {@code wanted} units off the lots at this unit price, oldest first, and says how many it got. */
    private static int consumeAtUnitPrice(List<OfferLot> lots, int unitPrice, int wanted) {
        if (lots == null || unitPrice <= 0 || wanted <= 0) {
            return 0;
        }
        int taken = 0;
        Iterator<OfferLot> it = lots.iterator();
        while (it.hasNext() && taken < wanted) {
            OfferLot lot = it.next();
            if (lot.unitPrice != unitPrice) {
                continue;
            }
            int take = Math.min(lot.qty, wanted - taken);
            lot.qty -= take;
            lot.gp = Math.max(0L, lot.gp - (long) take * (long) unitPrice);
            taken += take;
            if (lot.qty <= 0) {
                it.remove();
            }
        }
        return taken;
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

    // ---- the stored side ----

    /**
     * The stored records as one lot per offer, oldest first.
     */
    static List<OfferLot> groupOffers(List<LocalTradeDelta> deltas) {
        List<OfferLot> lots = new ArrayList<>();
        if (deltas == null || deltas.isEmpty()) {
            return lots;
        }
        List<LocalTradeDelta> sorted = new ArrayList<>();
        for (LocalTradeDelta delta : deltas) {
            if (delta != null && delta.itemId > 0) {
                sorted.add(delta);
            }
        }
        sorted.sort(Comparator.comparingLong(delta -> delta.tsClientMs));

        Map<Integer, OfferLot> openBySlot = new HashMap<>();
        for (LocalTradeDelta delta : sorted) {
            boolean completion = LocalTradeOfferCollapser.isCompletion(delta);
            if (!completion && delta.deltaQty <= 0) {
                continue;
            }
            OfferLot open = openBySlot.get(delta.slot);
            if (open != null && !open.belongsTo(delta)) {
                lots.add(open);
                openBySlot.remove(delta.slot);
                open = null;
            }
            if (completion) {
                if (open != null) {
                    open.absorb(delta);
                    lots.add(open);
                    openBySlot.remove(delta.slot);
                } else if (delta.deltaQty > 0) {
                    lots.add(new OfferLot(delta));
                }
                continue;
            }
            if (open == null) {
                openBySlot.put(delta.slot, new OfferLot(delta));
            } else {
                open.absorb(delta);
            }
        }
        lots.addAll(openBySlot.values());

        List<OfferLot> priced = new ArrayList<>(lots.size());
        for (OfferLot lot : lots) {
            if (lot.qty > 0 && lot.seal() > 0) {
                priced.add(lot);
            }
        }
        priced.sort(Comparator.comparingLong(lot -> lot.firstMs));
        return priced;
    }

    private static Map<ItemSide, List<OfferLot>> indexByItemSide(List<OfferLot> lots) {
        Map<ItemSide, List<OfferLot>> index = new HashMap<>();
        for (OfferLot lot : lots) {
            index.computeIfAbsent(new ItemSide(lot.itemId, lot.isBuy), key -> new ArrayList<>()).add(lot);
        }
        return index;
    }

    /**
     * What one unit of this trade actually came to, in coins that moved.
     */
    private static int resolveUnitPrice(LocalTradeDelta delta) {
        if (delta == null || delta.deltaQty <= 0) {
            return 0;
        }
        return resolveUnitPrice(Math.max(0L, delta.deltaGp), delta.deltaQty, delta.price);
    }

    /** The realised unit price again, read off a history row the same way. */
    private static int resolveUnitPrice(GeHistoryTrade trade) {
        if (trade == null || trade.quantity <= 0) {
            return 0;
        }
        return resolveUnitPrice(trade.totalGp, trade.quantity, trade.price);
    }

    private static int resolveUnitPrice(long totalGp, int quantity, int listedPrice) {
        if (quantity <= 0) {
            return 0;
        }
        if (totalGp > 0L) {
            return (int) Math.max(1L, totalGp / (long) quantity);
        }
        // No coins recorded at all - a synthetic or malformed record. The listed
        // price is all there is left to go on.
        return Math.max(0, listedPrice);
    }

    static TradeSignature signatureForTrade(GeHistoryTrade trade) {
        if (trade == null || !trade.isValid()) {
            return null;
        }
        int unitPrice = resolveUnitPrice(trade);
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

    /** One stored offer: its fills summed, and what is left of it once rows have claimed their share. */
    static final class OfferLot {
        final int itemId;
        final boolean isBuy;
        /** The Grand Exchange slot the offer ran in. Only lots from one slot can be one offer. */
        final int slot;
        final long firstMs;
        private final LocalTradeDelta first;
        private long offerStartMs;
        int qty;
        long gp;
        int unitPrice;

        OfferLot(LocalTradeDelta first) {
            this.first = first;
            this.itemId = first.itemId;
            this.isBuy = first.isBuy;
            this.slot = first.slot;
            this.firstMs = first.tsClientMs;
            this.offerStartMs = first.offerStartMs;
            this.qty = Math.max(0, first.deltaQty);
            this.gp = Math.max(0L, first.deltaGp);
        }

        boolean belongsTo(LocalTradeDelta delta) {
            if (!LocalTradeOfferCollapser.sameOffer(first, delta)) {
                return false;
            }
            // The first fill may predate start tracking while a later one carries it.
            return offerStartMs <= 0 || delta.offerStartMs <= 0 || offerStartMs == delta.offerStartMs;
        }

        void absorb(LocalTradeDelta delta) {
            qty += Math.max(0, delta.deltaQty);
            gp += Math.max(0L, delta.deltaGp);
            if (offerStartMs <= 0) {
                offerStartMs = delta.offerStartMs;
            }
        }

        /** Fixes the unit price once the fills are all in; returns it. */
        int seal() {
            unitPrice = resolveUnitPrice(gp, qty, first.price);
            return unitPrice;
        }
    }

    private static final class ItemSide {
        private final int itemId;
        private final boolean isBuy;

        ItemSide(int itemId, boolean isBuy) {
            this.itemId = itemId;
            this.isBuy = isBuy;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ItemSide)) {
                return false;
            }
            ItemSide that = (ItemSide) other;
            return itemId == that.itemId && isBuy == that.isBuy;
        }

        @Override
        public int hashCode() {
            return 31 * Integer.hashCode(itemId) + Boolean.hashCode(isBuy);
        }
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
