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
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Turns purchases into the thing they were bought to make - but only when a
 * sale asks for it.
 *
 * <p>The obvious design converts as soon as a player owns the ingredients, and
 * that design eats an ordinary Bandos boots flip. This one is demand-driven:
 * both ledgers consume real purchased inventory first, and only a shortfall
 * reaches {@link #coverShortfall}. To be converted, a purchase therefore has to
 * be an input to a recipe whose output was sold without ever being bought,
 * which is a pattern with no other explanation.
 *
 * <p>Ordering needs no timestamps. Both ledgers replay deltas in chronological
 * order, so at the moment a sell is processed the buckets hold exactly the buys
 * that preceded it. An ingredient bought after the sale simply is not there.
 *
 * <p>A conversion that produces several things - a set broken into its
 * pieces - is still triggered by one sale, but it credits every piece it
 * made, not only the one that asked. The others are sold later against stock
 * that no purchase covers, and the set they came out of is gone by then.
 */
@Singleton
final class ConversionLedger {
    private final ConversionRecipeIndex index;
    private final ConversionFeeService feeService;
    /** The guesses the player has ruled out; null means none ever are. */
    private final ConversionRejectionStore rejections;

    @Inject
    ConversionLedger(ConversionRecipeIndex index,
                     ConversionFeeService feeService,
                     ConversionRejectionStore rejections) {
        this.index = index;
        this.feeService = feeService;
        this.rejections = rejections;
    }

    ConversionLedger(ConversionRecipeIndex index, ConversionFeeService feeService) {
        this(index, feeService, null);
    }

    /** Test seam: no fee policy, so every fee is the NPC price the recipe carries. */
    ConversionLedger(ConversionRecipeIndex index) {
        this(index, null, null);
    }

    /** Test seam: no fee policy, and the given corrections. */
    ConversionLedger(ConversionRecipeIndex index, ConversionRejectionStore rejections) {
        this(index, null, rejections);
    }

    ConversionRejectionStore rejections() {
        return rejections;
    }

    /**
     * Try to produce {@code neededQuantity} of {@code outputItemId} out of what
     * the buckets hold. Returns false - and touches nothing - when the evidence
     * does not support exactly one answer.
     *
     * <p>What was made is left on the buckets rather than handed back, because
     * one conversion can credit several of them and each is sold separately.
     */
    boolean coverShortfall(int outputItemId, long neededQuantity, ConversionBuckets buckets) {
        return coverShortfall(outputItemId, neededQuantity, buckets, ConversionEvidence.ORDERED);
    }

    boolean coverShortfall(int outputItemId,
                           long neededQuantity,
                           ConversionBuckets buckets,
                           ConversionEvidence evidence) {
        return coverShortfall(outputItemId, neededQuantity, buckets, evidence, 0L);
    }

    /**
     * @param accountKey whose trades these are. A repair fee depends on the
     *                   player's Smithing level; 0 when unknown, which prices
     *                   the fee as the NPC would.
     */
    boolean coverShortfall(int outputItemId,
                           long neededQuantity,
                           ConversionBuckets buckets,
                           ConversionEvidence evidence,
                           long accountKey) {
        return coverShortfall(outputItemId, neededQuantity, buckets, evidence, accountKey, null);
    }

    /**
     * @param trigger the stored sale asking to be covered. A sale the player has
     *                said was not made by a recipe gets nothing, whatever is in
     *                stock - the same answer as when no recipe fits, so the
     *                inputs stay as bought and the sale stands on its own.
     */
    boolean coverShortfall(int outputItemId,
                           long neededQuantity,
                           ConversionBuckets buckets,
                           ConversionEvidence evidence,
                           long accountKey,
                           LocalTradeKey trigger) {
        if (index == null || buckets == null || outputItemId <= 0 || neededQuantity <= 0) {
            return false;
        }
        if (trigger != null && rejections != null && rejections.isRejected(accountKey, trigger)) {
            return false;
        }
        List<ConversionRecipe> candidates = index.recipesProducing(outputItemId);
        if (candidates.isEmpty()) {
            return false;
        }

        ConversionRecipe chosen = null;
        long availableRuns = 0L;
        for (ConversionRecipe candidate : candidates) {
            long runs = runsAvailable(candidate, outputItemId, buckets, evidence);
            if (runs <= 0L) {
                continue;
            }
            if (chosen != null) {
                // Two routes to the same item, and stock for both. 128 items in
                // the dataset have more than one route, nearly all of them
                // Barrows and Torva pieces that are both a repair output and a
                // set-break output. Which one the player used is unknowable, so
                // neither is claimed: a missed activity costs less than moving
                // six figures onto the wrong item.
                return false;
            }
            chosen = candidate;
            availableRuns = runs;
        }
        if (chosen == null) {
            return false;
        }

        int perRun = chosen.outputQuantityOf(outputItemId);
        if (perRun <= 0) {
            return false;
        }
        long runs = Math.min(availableRuns, (neededQuantity + perRun - 1) / perRun);
        if (runs <= 0L) {
            return false;
        }

        List<ConversionMatch.Line> lines = new ArrayList<>(chosen.inputs.size() + 1);
        long costGp = 0L;
        for (ConversionItem input : chosen.inputs) {
            long take = (long) input.quantity * runs;
            // Bought stock only. Break pieces carry no cost, so counting them
            // here both let a recipe eat one for nothing and divided the real
            // cost across units that never had any, leaving the bought unit
            // carrying less than it cost and the remainder stranded.
            long available = buckets.convertibleQuantityOf(input.itemId);
            if (take > available) {
                // runsAvailable already proved this cannot happen; if a bucket
                // implementation ever makes it possible, stop rather than
                // invent a cost.
                return false;
            }
            long bucketCost = Math.max(0L, buckets.costOf(input.itemId));
            long share = available > 0L ? (bucketCost * take) / available : 0L;
            buckets.consume(input.itemId, take, share);
            costGp += share;
            lines.add(new ConversionMatch.Line(input.itemId, take, share, false));
        }

        long fee = feeService != null ? feeService.feeFor(chosen, runs, accountKey) : chosen.feeGp * runs;
        if (fee > 0L) {
            costGp += fee;
            lines.add(new ConversionMatch.Line(0, runs, fee, true));
        }

        ConversionConfidence confidence = evidence == ConversionEvidence.SYNCED
            ? ConversionConfidence.LIKELY
            : ConversionConfidence.CONFIRMED;

        if (chosen.outputs.size() == 1) {
            // One thing made, and it carries everything the making cost.
            ConversionItem output = chosen.outputs.get(0);
            long produced = (long) output.quantity * runs;
            buckets.credit(output.itemId, produced, costGp,
                new ConversionMatch(chosen, produced, costGp, lines, confidence,
                    trigger != null ? java.util.Collections.singletonList(trigger) : null, accountKey));
            return true;
        }

        // Several things made. The cost stays whole on the break rather than
        // being divided between the pieces: the activity is the break, and the
        // pieces are only how it gets sold.
        ConversionBreak pending = new ConversionBreak(chosen, runs, costGp, confidence, trigger, accountKey);
        for (ConversionItem output : chosen.outputs) {
            buckets.creditBreak(output.itemId, (long) output.quantity * runs, pending);
        }
        return true;
    }

    /** How many times this recipe could have been run from what is in stock. */
    private static long runsAvailable(ConversionRecipe recipe,
                                      int outputItemId,
                                      ConversionBuckets buckets,
                                      ConversionEvidence evidence) {
        if (recipe == null || !recipe.isUsable()) {
            return 0L;
        }
        if (recipe.outputQuantityOf(outputItemId) <= 0) {
            return 0L;
        }
        long runs = Long.MAX_VALUE;
        for (ConversionItem input : recipe.inputs) {
            long available = evidence == ConversionEvidence.SYNCED
                ? Math.min(buckets.syncedQuantityOf(input.itemId),
                    buckets.convertibleQuantityOf(input.itemId))
                : buckets.convertibleQuantityOf(input.itemId);
            if (available < input.quantity) {
                // Partial stock does not convert. A player who already owned one
                // ingredient has a cost basis the plugin cannot see, and half a
                // cost basis reads as profit that was never made.
                return 0L;
            }
            runs = Math.min(runs, available / input.quantity);
        }
        return runs == Long.MAX_VALUE ? 0L : runs;
    }
}
