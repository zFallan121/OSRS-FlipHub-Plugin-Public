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
import java.util.Collections;
import java.util.List;

/**
 * What a conversion did, kept so the panel can show the trade behind the profit
 * rather than an unexplained cost basis.
 *
 * <p>Which way the lines run depends on the shape. For a conversion that made
 * one thing the lines are its inputs and they add up to {@link #costGp} - what
 * the item cost to make. For one that made several - a set break - the activity
 * belongs to the thing taken apart, so the lines are the pieces it became and
 * they add up to what those pieces sold for instead.
 */
final class ConversionMatch {
    /** One line of the breakdown. A fee line has no item, so its id is 0. */
    static final class Line {
        final int itemId;
        final long quantity;
        final long costGp;
        final boolean fee;

        Line(int itemId, long quantity, long costGp, boolean fee) {
            this.itemId = itemId;
            this.quantity = Math.max(0L, quantity);
            this.costGp = Math.max(0L, costGp);
            this.fee = fee;
        }
    }

    final ConversionRecipe recipe;
    /** Units produced: of the sold item, or of runs for a break. */
    final long quantity;
    /** What the conversion cost: its inputs plus any fee. */
    final long costGp;
    final List<Line> lines;
    final ConversionConfidence confidence;
    /**
     * The sales this conversion answered for: the one that asked for it, and
     * - for a break - every piece sale it handed stock to. What the player
     * rejects when they say the guess was wrong.
     */
    final List<LocalTradeKey> trades;
    /** Whose ledger this ran in; 0 for the pooled accountwide replay. */
    final long accountKey;

    ConversionMatch(ConversionRecipe recipe,
                    long quantity,
                    long costGp,
                    List<Line> lines,
                    ConversionConfidence confidence) {
        this(recipe, quantity, costGp, lines, confidence, null, 0L);
    }

    ConversionMatch(ConversionRecipe recipe,
                    long quantity,
                    long costGp,
                    List<Line> lines,
                    ConversionConfidence confidence,
                    List<LocalTradeKey> trades,
                    long accountKey) {
        this.accountKey = accountKey;
        this.confidence = confidence != null ? confidence : ConversionConfidence.CONFIRMED;
        this.recipe = recipe;
        this.quantity = Math.max(0L, quantity);
        this.costGp = Math.max(0L, costGp);
        this.lines = lines != null ? Collections.unmodifiableList(lines) : Collections.emptyList();
        this.trades = trades != null
            ? Collections.unmodifiableList(new ArrayList<>(trades))
            : Collections.emptyList();
    }
}
