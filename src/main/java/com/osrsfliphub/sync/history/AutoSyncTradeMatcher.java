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
import lombok.*;

/**
 * Which rows of the in-game history the plugin has not already recorded.
 *
 * <p>A row imported twice is a trade counted twice, so every rule here is asked one
 * question: could this row be an offer already stored? The stored side is one record
 * per completed offer; a row is one offer. What was watched live can still be lying
 * around as separate fills - an offer cancelled part way through, say - so the stored
 * records are first folded into one lot per offer by the same rule the store uses
 * ({@link TradeOfferCollapser#sameOffer}), and rows are matched against lots.
 *
 * <p>The tiers, in the order tried. The first two both match a single lot of the row's
 * own quantity, and they run for every row before the rest are tried at all, so a row
 * that one lot explains outright always claims that lot and a looser rule can never
 * take it away from that row.
 * <ol>
 *   <li>A lot of the same quantity at the same coins-per-unit.</li>
 *   <li>A lot of the same quantity whose coins sit within
 *       {@link #TOLERANCE_COINS_PER_UNIT} of the row's. An offer that fills at more
 *       than one price has no single unit price, so the two sides round it
 *       differently; this is the rule that keeps such an offer from being imported
 *       a second time.</li>
 *   <li>Lots of one offer, at that coins-per-unit, that between them cover the row's
 *       quantity: the row is one offer whose fills the store kept apart. Only lots
 *       from the same slot are pooled. Pooling across slots let two separate offers
 *       at the same price "explain" a third, larger row that was never recorded, and
 *       that row was then silently never imported.</li>
 *   <li>Whatever the lots at that unit price do cover is taken, and only the
 *       shortfall is imported.</li>
 * </ol>
 *
 * <p>A history row carries no date, so nothing above can tell today's sale from one of
 * the same item, size and coins made a month ago - and a flipper makes that same trade
 * again and again. The rows the sync hands over are the ones above its cursor, which
 * makes them newer than the last sync; an offer that ended before that sync is
 * therefore not one of them, however alike, and is left out ({@link LastSync}). Without
 * that, a sale made on another client was judged already recorded and never imported,
 * and the item went on showing as held.
 */
final class AutoSyncTradeMatcher {
    /**
     * How far a lot's coins may sit from a row's before they stop being the same
     * offer: one coin per unit traded.
     *
     * <p>That is exactly the rounding the two sides can disagree by, and no more.
     * The history reports what an offer made in total; the plugin recorded it fill
     * by fill. Where the two differ for one offer it is by a rounding of one coin
     * per item: the widget's "each" price is a rounded average that the parser may
     * have to multiply back out, and on a sale the game taxes each item at the
     * price it actually went for while the plugin can only tax the average, and
     * the tax rounds down per item. Both are bounded by one coin an item. Anything
     * wider starts to cover two offers of the same size at genuinely different
     * prices - a flipper's bread and butter - and would judge a real trade already
     * recorded.
     */
    static final long TOLERANCE_COINS_PER_UNIT = 1L;

    private AutoSyncTradeMatcher() {
    }

    /**
     * @param lastSync every row is known to be newer than it, so the offers it rules out are
     *                 not compared. An offer is kept or left out whole: one that began before
     *                 it and ended after it is one row. {@link LastSync#NONE} when nothing is
     *                 known about the rows, and every stored offer is compared.
     */
    static SelectionPlan planMissingTrades(List<Trade> historyTrades, List<Delta> existingDeltas, LastSync lastSync) {
        if (historyTrades == null || historyTrades.isEmpty()) {
            return SelectionPlan.empty();
        }

        List<OfferLot> offers = groupOffers(existingDeltas);
        offers.removeIf(lot -> lastSync.predates(lot.lastMs, lot.slot, lot.firstMs));
        Map<ItemSide, List<OfferLot>> lotsByItemSide = indexByItemSide(offers);
        int size = historyTrades.size();
        Trade[] missingByIndex = new Trade[size];
        boolean[] unexplained = new boolean[size];

        // GE history UI order is newest-first; process oldest->newest for stable matching.
        for (int i = size - 1; i >= 0; i--) {
            Trade trade = historyTrades.get(i);
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

        List<Trade> missing = new ArrayList<>();
        for (int i = size - 1; i >= 0; i--) {
            if (!unexplained[i]) {
                continue;
            }
            Trade trade = historyTrades.get(i);
            List<OfferLot> lots = lotsByItemSide.get(new ItemSide(trade.itemId, trade.isBuy));
            if (claimCoveredWithinOneOffer(lots, trade, resolveUnitPrice(trade))) {
                continue;
            }
            int covered = consumeAtUnitPrice(lots, resolveUnitPrice(trade), trade.quantity);
            Trade residualTrade = buildResidualTrade(trade, trade.quantity - covered);
            if (residualTrade == null) {
                continue;
            }
            missingByIndex[i] = residualTrade;
            missing.add(residualTrade);
        }
        return new SelectionPlan(missingByIndex, missing);
    }

    /**
     * Where the last sync left off: when it ran, and the offers still in a slot then.
     *
     * <p>A trade that ended before it cannot be one of the rows newer than it. The exception is
     * an offer still in its slot: its row may not be in the history yet, and when it arrives it
     * has to find the fills recorded before. Those are remembered by slot and placing time, so
     * they alone stay comparable. The cutoff used to be pulled back to the oldest such offer
     * instead, and one sell offer left up since June held every trade since June comparable -
     * which undid the rule on exactly the accounts it is for.
     */
    @RequiredArgsConstructor
    static final class LastSync {
        /** Nothing known: every stored trade is compared. */
        static final LastSync NONE = new LastSync(0L, Collections.emptyMap());

        final long ms;
        /** Slot to when its offer was placed; zero when that is not known, which keeps the whole slot. */
        final Map<Integer, Long> openOffers;

        /** Now, with the offers still in their slots. */
        static LastSync at(long nowMs, Map<Integer, Stamp> stamps) {
            Map<Integer, Long> open = new TreeMap<>();
            stamps.forEach((slot, stamp) -> {
                if (stamp.lastEmptyMs <= 0) {
                    open.put(slot, Math.max(0L, stamp.firstSeenMs));
                }
            });
            return new LastSync(nowMs, open);
        }

        /**
         * Whether a stored trade is too old to be one of the rows newer than this sync.
         *
         * <p>So is any trade an earlier sync imported, whatever its time says: it was a row of
         * that sync, which puts it below the cursor now, and its time was made up just before
         * that sync - close enough to pass for recent. Left in, each imported sale went on
         * explaining the next identical one made on another client.
         */
        boolean predates(long closedAtMs, int slot, long firstMs) {
            if (ms <= 0) {
                return false;
            }
            Long placedMs = openOffers.get(slot);
            return slot >= Const.GE_HISTORY_SYNTHETIC_SLOT_START
                || closedAtMs < ms && (placedMs == null || firstMs < placedMs);
        }

        String encode() {
            StringBuilder out = new StringBuilder(Long.toString(ms));
            openOffers.forEach((slot, placedMs) -> out.append(',').append(slot).append(':').append(placedMs));
            return out.toString();
        }

        /**
         * One stored by {@link #encode}, every moment in it read back a little earlier. Nothing stored,
         * anything unreadable, or a moment later than now (the clock was moved) is {@link #NONE}.
         */
        static LastSync decode(String raw, long nowMs) {
            if (Str.isBlank(raw)) {
                return NONE;
            }
            try {
                String[] parts = raw.split(",");
                long ms = Long.parseLong(parts[0]);
                Map<Integer, Long> open = new TreeMap<>();
                for (int i = 1; i < parts.length; i++) {
                    String[] slotAndPlaced = parts[i].split(":");
                    open.put(Integer.parseInt(slotAndPlaced[0]), earlier(Long.parseLong(slotAndPlaced[1])));
                }
                return ms > 0 && ms <= nowMs ? new LastSync(earlier(ms), open) : NONE;
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException ex) {
                return NONE;
            }
        }

        private static long earlier(long ms) {
            return ms > 0 ? Math.max(1L, ms - Const.GE_HISTORY_SYNCED_SINCE_SLACK_MS) : 0L;
        }
    }

    static long toleranceCoins(int quantity) {
        return TOLERANCE_COINS_PER_UNIT * (long) Math.max(0, quantity);
    }

    // ---- the tiers ----

    private static boolean claimExact(List<OfferLot> lots, Trade trade, int unitPrice) {
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
     *
     * <p>Confined to a single slot. The game runs one offer per slot at a time, so lots
     * sharing a slot are the only ones that can be pieces of the same offer. Pooling every
     * lot at the price let two genuinely separate offers cover a third, larger row that was
     * never recorded at all, and that row was then dropped instead of imported.
     */
    private static boolean claimCoveredWithinOneOffer(List<OfferLot> lots, Trade trade, int unitPrice) {
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

    private static boolean claimWithinTolerance(List<OfferLot> lots, Trade trade) {
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

    private static Trade buildResidualTrade(Trade trade, int missingQty) {
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
        return new Trade(trade.itemId, trade.isBuy, missingQty, trade.price, scaledTotalGp);
    }

    private static long scaleTotalGp(Trade trade, int quantity) {
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
     *
     * <p>The same walk the store makes when it loads: records on one slot that are one
     * offer's are summed until that offer's completion, or until the slot is seen to
     * have moved on. The one difference is that a run with no completion is summed
     * too, because a cancelled offer is in the history as one row however many fills
     * it managed. Records with no quantity are no offer's fill and are left out.
     */
    static List<OfferLot> groupOffers(List<Delta> deltas) {
        List<OfferLot> lots = new ArrayList<>();
        if (deltas == null || deltas.isEmpty()) {
            return lots;
        }
        List<Delta> sorted = new ArrayList<>();
        for (Delta delta : deltas) {
            if (delta != null && delta.itemId > 0) {
                sorted.add(delta);
            }
        }
        sorted.sort(Comparator.comparingLong(delta -> delta.tsClientMs));

        Map<Integer, OfferLot> openBySlot = new HashMap<>();
        for (Delta delta : sorted) {
            boolean completion = TradeOfferCollapser.isCompletion(delta);
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
     *
     * <p>Deliberately not {@code delta.price}. That is the price the offer was
     * listed at, and an offer very rarely fills at it - a buy fills at or under,
     * a sell at or over. The history widget reports what the trade really made,
     * so a listed price and a realised one are two different numbers, and
     * comparing them made every trade already recorded live look missing: a
     * blue dragonhide set bought at a 23,401 offer for 15,000 matched nothing,
     * and got imported a second time. The coins are the one figure both sides
     * state the same way, tax and all.
     */
    private static int resolveUnitPrice(Delta delta) {
        if (delta == null || delta.deltaQty <= 0) {
            return 0;
        }
        return resolveUnitPrice(Math.max(0L, delta.deltaGp), delta.deltaQty, delta.price);
    }

    /** The realised unit price again, read off a history row the same way. */
    private static int resolveUnitPrice(Trade trade) {
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

    static TradeSignature signatureForTrade(Trade trade) {
        if (trade == null || !trade.isValid()) {
            return null;
        }
        int unitPrice = resolveUnitPrice(trade);
        if (unitPrice <= 0) {
            return null;
        }
        return new TradeSignature(trade.itemId, trade.isBuy, trade.quantity, unitPrice);
    }

    static TradeSignature signatureForDelta(Delta delta) {
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
        /** When the offer ended, as far as the records say: the latest of their closing times. */
        long lastMs;
        private final Delta first;
        private long offerStartMs;
        int qty;
        long gp;
        int unitPrice;

        OfferLot(Delta first) {
            this.first = first;
            this.itemId = first.itemId;
            this.isBuy = first.isBuy;
            this.slot = first.slot;
            this.firstMs = first.tsClientMs;
            this.lastMs = first.closedAtMs();
            this.offerStartMs = first.offerStartMs;
            this.qty = Math.max(0, first.deltaQty);
            this.gp = Math.max(0L, first.deltaGp);
        }

        boolean belongsTo(Delta delta) {
            if (!TradeOfferCollapser.sameOffer(first, delta)) {
                return false;
            }
            // The first fill may predate start tracking while a later one carries it.
            return offerStartMs <= 0 || delta.offerStartMs <= 0 || offerStartMs == delta.offerStartMs;
        }

        void absorb(Delta delta) {
            qty += Math.max(0, delta.deltaQty);
            gp += Math.max(0L, delta.deltaGp);
            lastMs = Math.max(lastMs, delta.closedAtMs());
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

    @EqualsAndHashCode
    @RequiredArgsConstructor
    private static final class ItemSide {
        private final int itemId;
        private final boolean isBuy;
    }

    @EqualsAndHashCode
    @RequiredArgsConstructor
    static final class TradeSignature {
        private final int itemId;
        private final boolean isBuy;
        private final int quantity;
        private final int unitPrice;
    }

    static final class SelectionPlan {
        final Trade[] missingByIndex;
        final List<Trade> missingTrades;

        SelectionPlan(Trade[] missingByIndex, List<Trade> missingTrades) {
            this.missingByIndex = missingByIndex != null ? missingByIndex : new Trade[0];
            this.missingTrades = missingTrades != null ? missingTrades : new ArrayList<>();
        }

        static SelectionPlan empty() {
            return new SelectionPlan(new Trade[0], new ArrayList<>());
        }

        boolean isMissing(int index) {
            return index >= 0
                && index < missingByIndex.length
                && missingByIndex[index] != null;
        }

        Trade missingTradeAt(int index) {
            if (index < 0 || index >= missingByIndex.length) {
                return null;
            }
            return missingByIndex[index];
        }
    }
}
