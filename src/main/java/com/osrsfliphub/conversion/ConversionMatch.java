package com.osrsfliphub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a conversion did, kept so the panel can show the trade behind the profit
 * rather than an unexplained cost basis.
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
