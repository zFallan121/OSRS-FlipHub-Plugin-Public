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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One thing taken apart into several, and the pieces it is still waiting on.
 *
 * <p>A set break is one activity, not four. The player bought a set at one
 * price and got four items back; what they made is what the four sold for less
 * what the set cost, and that number does not exist until the last piece has
 * gone. So the pieces carry no cost of their own - the break holds all of it -
 * and selling one records nothing except the coins it brought in. When the last
 * piece sells the break is finished and becomes a single activity against the
 * set.
 *
 * <p>That is also why there is no guide price anywhere near this. Dividing the
 * set's cost between the pieces was only ever needed to give each piece its own
 * profit line, and there are no per-piece lines any more - so there is nothing
 * left to estimate, and the activity is made entirely of numbers the player
 * actually traded at.
 *
 * <p>A piece that is never sold leaves its break unfinished for good, and an
 * unfinished break reports nothing. That is the deliberate side of the trade:
 * an activity that shows up late is better than one that shows a loss it is
 * only halfway through making.
 */
final class ConversionBreak {
    private final ConversionRecipe recipe;
    private final long runs;
    private final long costGp;
    private final ConversionConfidence confidence;
    /** Output item -> {units still owed, units sold, coins they fetched}. */
    private final Map<Integer, long[]> outputs = new LinkedHashMap<>();
    private long outstandingQty;
    private long revenueGp;
    private long taxGp;
    private long lastSellTsMs;
    private Long firstBuyTs;
    /** The sale that set the break off, then every piece sale it fed. */
    private final List<LocalTradeKey> trades = new ArrayList<>();
    private final long accountKey;

    ConversionBreak(ConversionRecipe recipe,
                    long runs,
                    long costGp,
                    ConversionConfidence confidence,
                    LocalTradeKey trigger,
                    long accountKey) {
        this.accountKey = accountKey;
        this.recipe = recipe;
        this.runs = Math.max(1L, runs);
        this.costGp = Math.max(0L, costGp);
        this.confidence = confidence != null ? confidence : ConversionConfidence.CONFIRMED;
        rememberTrade(trigger);
        for (ConversionItem output : recipe.outputs) {
            long produced = (long) output.quantity * this.runs;
            long[] state = outputs.get(output.itemId);
            if (state == null) {
                outputs.put(output.itemId, new long[]{produced, 0L, 0L});
            } else {
                state[0] += produced;
            }
            outstandingQty += produced;
        }
    }

    /** The item the activity is filed against: the thing that was taken apart. */
    int inputItemId() {
        return recipe.inputs.isEmpty() ? 0 : recipe.inputs.get(0).itemId;
    }

    long runs() {
        return runs;
    }

    long costGp() {
        return costGp;
    }

    long revenueGp() {
        return revenueGp;
    }

    long taxGp() {
        return taxGp;
    }

    long lastSellTsMs() {
        return lastSellTsMs;
    }

    Long firstBuyTs() {
        return firstBuyTs;
    }

    ConversionConfidence confidence() {
        return confidence;
    }

    /** Carried from the input, so the hold time starts when the set was bought. */
    void rememberFirstBuyTs(Long tsMs) {
        if (tsMs != null && (firstBuyTs == null || tsMs < firstBuyTs)) {
            firstBuyTs = tsMs;
        }
    }

    /** How many of this piece the break is still waiting to see sold. */
    long outstandingOf(int itemId) {
        long[] state = outputs.get(itemId);
        return state != null ? Math.max(0L, state[0]) : 0L;
    }

    boolean isComplete() {
        return outstandingQty <= 0L;
    }

    /** Every sale the break answered for, in the order they were seen. */
    List<LocalTradeKey> trades() {
        return new ArrayList<>(trades);
    }

    private void rememberTrade(LocalTradeKey sale) {
        if (sale != null && !trades.contains(sale)) {
            trades.add(sale);
        }
    }

    /**
     * Book a sale of one of the pieces. Returns what was actually taken, which
     * is less than asked for when the offer also sold stock bought outright.
     *
     * @param sale which stored sale this is, so the break can say what it
     *             answered for if the player later says it never happened
     */
    long sell(int itemId, long quantity, long revenue, long tax, long tsMs, LocalTradeKey sale) {
        long[] state = outputs.get(itemId);
        if (state == null || quantity <= 0L) {
            return 0L;
        }
        long taken = Math.min(quantity, Math.max(0L, state[0]));
        if (taken <= 0L) {
            return 0L;
        }
        rememberTrade(sale);
        long booked = taken >= quantity ? revenue : (revenue * taken) / quantity;
        long bookedTax = taken >= quantity ? tax : (tax * taken) / quantity;
        state[0] -= taken;
        state[1] += taken;
        state[2] += Math.max(0L, booked);
        outstandingQty -= taken;
        revenueGp += Math.max(0L, booked);
        taxGp += Math.max(0L, bookedTax);
        lastSellTsMs = Math.max(lastSellTsMs, tsMs);
        return taken;
    }

    /**
     * What the break turned into, in the order the recipe lists it. The gp on
     * each line is what that piece sold for, not what it cost - a break has no
     * per-piece cost, which is the whole point of it being one activity.
     */
    List<ConversionMatch.Line> outputLines() {
        List<ConversionMatch.Line> lines = new ArrayList<>(outputs.size());
        for (Map.Entry<Integer, long[]> entry : outputs.entrySet()) {
            long[] state = entry.getValue();
            if (state[1] <= 0L) {
                continue;
            }
            lines.add(new ConversionMatch.Line(entry.getKey(), state[1], state[2], false));
        }
        return lines;
    }

    /** The finished activity, ready to be filed against the input item. */
    ConversionMatch toMatch() {
        return new ConversionMatch(recipe, runs, costGp, outputLines(), confidence, trades, accountKey);
    }
}
