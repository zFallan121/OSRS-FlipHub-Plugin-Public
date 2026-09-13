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
import lombok.RequiredArgsConstructor;

/**
 * Works out what the player's recorded conversions are worth, and which of their trades are
 * therefore spoken for.
 *
 * <p>This is the whole of conversion accounting now. A recorded conversion names its purchases
 * and its sales outright, so there is nothing to infer: the cost is what those purchases cost
 * plus the fee, the revenue is what those sales brought in, and the quantities involved are
 * simply taken out of the ordinary buying-and-selling replay so they cannot be counted twice.</p>
 *
 * <p>It replaces a recipe table, a matcher, a confidence rating, machinery for breaks whose
 * pieces had not all sold yet, and a way to dismiss a wrong guess - all of which existed only
 * because the game never tells a plugin that two items were combined.</p>
 *
 * <p>Both ledgers call this, so they cannot disagree about what a conversion was worth.</p>
 */
final class RecipeFlipLedger {
    /** One conversion, priced. */
    @RequiredArgsConstructor
    static final class Activity {
        final int itemId;
        final ConversionKind kind;
        final String name;
        final long costGp;
        final long revenueGp;
        final long taxGp;
        final int quantity;
        final long completionTsMs;

        long profitGp() {
            return revenueGp - costGp;
        }
    }

    @RequiredArgsConstructor
    static final class Result {
        /** How much of each trade a conversion has taken, so the plain replay can skip it. */
        final Map<TradeKey, Integer> claimed;
        final List<Activity> activities;

        int claimedOn(Delta delta) {
            if (delta == null) {
                return 0;
            }
            Integer taken = claimed.get(TradeKey.of(delta));
            return taken != null ? taken : 0;
        }

        boolean isEmpty() {
            return activities.isEmpty();
        }

        /**
         * The trades with the recorded conversions' share taken out, so the ordinary
         * buying-and-selling replay sees only what is left and cannot count a unit twice.
         *
         * <p>Doing it here, once, is why the plain replay needed no changes at all: it still
         * receives a list of trades and knows nothing about conversions.</p>
         */
        List<Delta> remainingTrades(List<Delta> deltas) {
            if (deltas == null || claimed.isEmpty()) {
                return deltas;
            }
            List<Delta> out = new ArrayList<>(deltas.size());
            Map<TradeKey, Integer> left = new HashMap<>(claimed);
            for (Delta delta : deltas) {
                if (delta == null) {
                    continue;
                }
                TradeKey key = TradeKey.of(delta);
                Integer taken = left.get(key);
                if (taken == null || taken <= 0 || delta.deltaQty <= 0) {
                    out.add(delta);
                    continue;
                }
                int take = Math.min(taken, delta.deltaQty);
                left.put(key, taken - take);
                int keptQty = delta.deltaQty - take;
                boolean completion = "OFFER_COMPLETED".equals(delta.eventType);
                if (keptQty <= 0 && !completion) {
                    // Wholly spoken for, and nothing else to say about it.
                    continue;
                }
                long keptGp = delta.deltaQty > 0
                    ? (Math.max(0L, delta.deltaGp) * keptQty) / delta.deltaQty
                    : 0L;
                out.add(new Delta(delta.tsClientMs, delta.slot, delta.itemId, delta.isBuy,
                    keptQty, keptGp, delta.eventType, delta.price, delta.baselineSynthetic,
                    delta.offerStartMs, delta.endMs));
            }
            return out;
        }
    }

    private static final Result EMPTY = new Result(new HashMap<>(), new ArrayList<>());

    private RecipeFlipLedger() {
    }

    static Result empty() {
        return EMPTY;
    }

    /**
     * Price every recorded conversion that its trades still support.
     *
     * <p>Records are applied oldest first so that two conversions competing for one purchase
     * resolve the same way on every replay. A record whose trades are missing, or whose
     * quantities are no longer available, is skipped whole rather than part-applied: half a
     * conversion would price a sale against fewer parts than it really had.</p>
     */
    static Result apply(List<Delta> deltas, List<RecipeFlip> flips) {
        if (deltas == null || deltas.isEmpty() || flips == null || flips.isEmpty()) {
            return empty();
        }
        Map<TradeKey, Delta> byKey = new HashMap<>();
        Map<TradeKey, Integer> remaining = new HashMap<>();
        for (Delta delta : deltas) {
            if (delta == null || delta.deltaQty <= 0) {
                continue;
            }
            TradeKey key = TradeKey.of(delta);
            // A repeated key would make "which trade" ambiguous, so the first wins and the
            // rest are left to the ordinary replay.
            if (byKey.putIfAbsent(key, delta) == null) {
                remaining.put(key, delta.deltaQty);
            }
        }

        List<RecipeFlip> ordered = new ArrayList<>(flips);
        ordered.sort(Comparator.comparingLong(flip -> flip.recordedMs));

        Map<TradeKey, Integer> claimed = new HashMap<>();
        List<Activity> activities = new ArrayList<>();
        for (RecipeFlip flip : ordered) {
            if (flip == null || !flip.isUsable() || !canApply(flip, byKey, remaining)) {
                continue;
            }
            long cost = Math.max(0L, flip.feeGp);
            for (RecipeFlip.Part part : flip.inputParts()) {
                Delta delta = byKey.get(part.trade);
                cost += share(delta.deltaGp, part.quantity, delta.deltaQty);
                take(remaining, claimed, part.trade, part.quantity);
            }
            long revenue = 0L;
            long tax = 0L;
            int quantity = 0;
            long completion = 0L;
            for (RecipeFlip.Part part : flip.outputParts()) {
                Delta delta = byKey.get(part.trade);
                revenue += share(delta.deltaGp, part.quantity, delta.deltaQty);
                tax += GeTax.forSale(delta.itemId, delta.price, part.quantity);
                quantity += part.quantity;
                completion = Math.max(completion, delta.closedAtMs());
                take(remaining, claimed, part.trade, part.quantity);
            }
            activities.add(new Activity(flip.subjectItemId(), flip.kind, flip.name,
                cost, revenue, tax, quantity, completion));
        }
        if (activities.isEmpty()) {
            return empty();
        }
        return new Result(claimed, activities);
    }

    private static boolean canApply(RecipeFlip flip, Map<TradeKey, Delta> byKey,
                                    Map<TradeKey, Integer> remaining) {
        Map<TradeKey, Integer> wanted = new HashMap<>();
        for (RecipeFlip.Part part : flip.inputParts()) {
            wanted.merge(part.trade, part.quantity, Integer::sum);
        }
        for (RecipeFlip.Part part : flip.outputParts()) {
            wanted.merge(part.trade, part.quantity, Integer::sum);
        }
        for (Map.Entry<TradeKey, Integer> entry : wanted.entrySet()) {
            Delta delta = byKey.get(entry.getKey());
            if (delta == null) {
                return false;
            }
            Integer left = remaining.get(entry.getKey());
            if (left == null || left < entry.getValue()) {
                return false;
            }
        }
        // Purchases must be purchases and sales must be sales, or the record describes
        // something that did not happen.
        for (RecipeFlip.Part part : flip.inputParts()) {
            if (!byKey.get(part.trade).isBuy) {
                return false;
            }
        }
        for (RecipeFlip.Part part : flip.outputParts()) {
            if (byKey.get(part.trade).isBuy) {
                return false;
            }
        }
        return true;
    }

    private static void take(Map<TradeKey, Integer> remaining, Map<TradeKey, Integer> claimed,
                             TradeKey key, int quantity) {
        remaining.merge(key, -quantity, Integer::sum);
        claimed.merge(key, quantity, Integer::sum);
    }

    /** The part of a trade's coins that belongs to {@code quantity} of it. */
    private static long share(long totalGp, int quantity, int totalQty) {
        if (totalQty <= 0) {
            return 0L;
        }
        if (quantity >= totalQty) {
            return Math.max(0L, totalGp);
        }
        return (Math.max(0L, totalGp) * quantity) / totalQty;
    }
}
