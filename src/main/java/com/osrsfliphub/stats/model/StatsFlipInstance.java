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

import java.util.Collections;
import java.util.List;

final class StatsFlipInstance {
    final int itemId;
    final long buyPriceGp;
    final long sellPriceGp;
    final long buyCostGp;
    final long sellRevenueGp;
    final long profitGp;
    final int quantity;
    final long completionTsMs;

    /**
     * How this item was obtained, when it was not simply bought. Null for a
     * plain flip, which is the overwhelming majority, so every existing render
     * path sees exactly what it saw before.
     */
    final ConversionKind conversionKind;
    final String conversionName;
    /**
     * The breakdown behind the activity; empty for a plain flip. For something
     * that was made, these are the inputs and fee behind {@link #buyCostGp};
     * for something taken apart, they are the pieces it became and what each of
     * them sold for, which adds up to {@link #sellRevenueGp} instead.
     */
    final List<Match.Line> conversionLines;
    /** Null for a plain flip. LIKELY when only a made-up timestamp orders it. */
    final Confidence conversionConfidence;
    /**
     * The stored sales the attribution drew on; empty for a plain flip. This is
     * what the player rejects when they say the plugin guessed wrong.
     */
    final List<TradeKey> conversionTrades;
    /**
     * Whose ledger this came out of - 0 for the pooled accountwide replay - so
     * a correction is filed against the profile that owns the trade, even
     * from the accountwide view.
     */
    final long accountKey;
    /**
     * A recipe guess the player dismissed, or null. Such an entry stands where
     * the guess used to be so it can be restored from there, and counts for
     * nothing: no profit, no cost, no flip.
     */
    final ConversionRejection dismissed;

    /**
     * A break the plugin has guessed and not finished. A set is booked whole
     * against the thing taken apart, once its last piece has sold; until then
     * there is no honest number for it, so this entry shows none, counts for
     * nothing, and exists only so the guess can be seen and refused - it
     * carries the sales the guess rests on, which is what a rejection needs.
     */
    final boolean openBreak;

    /**
     * A sell offer that has filled part-way and is still sitting in the Grand
     * Exchange. The coins are already the player's, so the profit counts - but
     * the flip has not happened yet, so nothing that counts flips counts this.
     */
    final boolean inProgress;

    /**
     * What the exchange took on these sales, per {@link GeTax}. Carried so the
     * header's tax figure comes from the same flips as the profit beside it,
     * rather than from a second ledger that admits fills on a different rule.
     */
    final long taxGp;

    StatsFlipInstance(int itemId,
                      long buyPriceGp,
                      long sellPriceGp,
                      long buyCostGp,
                      long sellRevenueGp,
                      long profitGp,
                      int quantity,
                      long completionTsMs) {
        this(itemId, buyPriceGp, sellPriceGp, buyCostGp, sellRevenueGp, profitGp, quantity, completionTsMs, null);
    }

    StatsFlipInstance(int itemId,
                      long buyPriceGp,
                      long sellPriceGp,
                      long buyCostGp,
                      long sellRevenueGp,
                      long profitGp,
                      int quantity,
                      long completionTsMs,
                      Match conversion) {
        this(itemId, buyPriceGp, sellPriceGp, buyCostGp, sellRevenueGp, profitGp, quantity, completionTsMs,
            conversion, false);
    }

    StatsFlipInstance(int itemId,
                      long buyPriceGp,
                      long sellPriceGp,
                      long buyCostGp,
                      long sellRevenueGp,
                      long profitGp,
                      int quantity,
                      long completionTsMs,
                      Match conversion,
                      boolean inProgress) {
        this(itemId, buyPriceGp, sellPriceGp, buyCostGp, sellRevenueGp, profitGp, quantity, completionTsMs,
            conversion, inProgress, 0L);
    }

    StatsFlipInstance(int itemId,
                      long buyPriceGp,
                      long sellPriceGp,
                      long buyCostGp,
                      long sellRevenueGp,
                      long profitGp,
                      int quantity,
                      long completionTsMs,
                      Match conversion,
                      boolean inProgress,
                      long taxGp) {
        this(itemId, buyPriceGp, sellPriceGp, buyCostGp, sellRevenueGp, profitGp, quantity, completionTsMs,
            conversion, inProgress, taxGp, conversion != null ? conversion.accountKey : 0L, null, false);
    }

    /** The entry that stands where a dismissed guess used to be. */
    static StatsFlipInstance dismissed(ConversionRejection rejection, long accountKey) {
        return new StatsFlipInstance(rejection.itemId, 0L, 0L, 0L, 0L, 0L, 0, rejection.completionTsMs,
            null, false, 0L, accountKey, rejection, false);
    }

    /**
     * The entry that stands for a break still waiting on its pieces, filed
     * against {@code itemId} - the thing taken apart, where the finished
     * activity would go. {@code guess} carries the recipe, the pieces sold so
     * far and the sales the break answered for; none of its figures are taken.
     */
    static StatsFlipInstance openBreak(int itemId, Match guess, long tsMs) {
        return new StatsFlipInstance(itemId, 0L, 0L, 0L, 0L, 0L, 0, tsMs,
            guess, false, 0L, guess.accountKey, null, true);
    }

    /**
     * Whether the totals include this entry. A dismissed guess and an
     * unfinished break are shown so they can be acted on, and count for
     * nothing: no profit, no cost, no flip.
     */
    boolean counted() {
        return dismissed == null && !openBreak;
    }

    private StatsFlipInstance(int itemId,
                              long buyPriceGp,
                              long sellPriceGp,
                              long buyCostGp,
                              long sellRevenueGp,
                              long profitGp,
                              int quantity,
                              long completionTsMs,
                              Match conversion,
                              boolean inProgress,
                              long taxGp,
                              long accountKey,
                              ConversionRejection dismissed,
                              boolean openBreak) {
        ConversionRecipe recipe = conversion != null ? conversion.recipe : null;
        this.conversionKind = recipe != null ? recipe.kind : null;
        this.conversionName = recipe != null ? recipe.displayName : null;
        this.conversionLines = conversion != null ? conversion.lines : Collections.emptyList();
        this.conversionConfidence = conversion != null ? conversion.confidence : null;
        this.conversionTrades = conversion != null ? conversion.trades : Collections.emptyList();
        this.accountKey = accountKey;
        this.dismissed = dismissed;
        this.openBreak = openBreak;
        this.itemId = itemId;
        this.buyPriceGp = Math.max(0L, buyPriceGp);
        this.sellPriceGp = Math.max(0L, sellPriceGp);
        this.buyCostGp = Math.max(0L, buyCostGp);
        this.sellRevenueGp = Math.max(0L, sellRevenueGp);
        this.profitGp = profitGp;
        this.quantity = Math.max(0, quantity);
        this.completionTsMs = Math.max(0L, completionTsMs);
        this.inProgress = inProgress;
        this.taxGp = Math.max(0L, taxGp);
    }
}
