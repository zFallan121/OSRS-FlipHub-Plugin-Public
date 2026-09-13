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

import java.util.Set;

/**
 * The Grand Exchange sale tax, in one place.
 *
 * <p>The game charges two percent of what a single item sold for, rounded down, which is why
 * anything under fifty coins is taxed nothing at all. No single item is ever taxed more than
 * five million, and a short list of items is exempt outright whatever it sells for. Buying is
 * never taxed.
 *
 * <p>Everything that needs the tax asks here. The Activity card, both trade ledgers and the
 * offer pipeline each used to carry their own version, and they disagreed: one applied the cap
 * and one did not, one rounded per item and one took two percent of the whole sale. Two numbers
 * on the same card could be computed from two different taxes.
 */
final class GeTax {
    /** No single item is taxed more than this, however much it sold for. */
    static final long MAX_TAX_PER_ITEM = 5_000_000L;

    /** Two percent, expressed as the divisor the game's own rounding implies. */
    private static final int RATE_DIVISOR = 50;

    /**
     * The items the game charges no sale tax on, whatever they sell for.
     *
     * <p>Jagex exempted low-value and early-game items when the tax went to two percent on
     * 29 May 2025, and the list has not moved since. It is keyed on item id alone: price,
     * quantity and whether the stack was noted make no difference, and the fifty-coin rounding
     * threshold is a separate reason a sale can be untaxed rather than a substitute for this.
     *
     * <p>Ids verified against the wiki's own item infoboxes, the wiki's price mapping data and
     * an independent third source. The near misses matter more than the hits here: the raw form
     * of every exempt fish, the poisoned form of every exempt arrow and dart, Chocolate cake,
     * Gilded spade and the other standard teleport tablets are all taxed normally.
     */
    private static final Set<Integer> EXEMPT_ITEM_IDS = Set.of(
        // Old school bond
        13190,
        // Energy potion, every dose
        3008, 3010, 3012, 3014,
        // Early-game ammunition and Mind rune
        882, 884, 886, 806, 807, 808, 558,
        // Low-level food
        365, 2309, 1891, 2140, 2142, 347, 379, 355, 2327, 351, 329, 315, 361,
        // Teleport tablets and the fully charged jewellery
        8011, 8010, 28824, 8009, 3853, 28790, 8008, 2552, 8013, 8007,
        // Tools
        1755, 5325, 1785, 2347, 1733, 233, 5341, 8794, 5329, 5343, 1735, 952, 5331
    );

    private GeTax() {
    }

    /** Whether the game charges no sale tax on this item at any price. */
    static boolean isExempt(int itemId) {
        return EXEMPT_ITEM_IDS.contains(itemId);
    }

    /**
     * Tax on one item sold at this price. Rounded down, capped, and zero for an exempt item or
     * a price the caller does not know.
     */
    static long perItem(int itemId, long unitPrice) {
        if (unitPrice <= 0L || isExempt(itemId)) {
            return 0L;
        }
        return Math.min(unitPrice / RATE_DIVISOR, MAX_TAX_PER_ITEM);
    }

    /**
     * Tax on a sale of this many items at this price. The game rounds each item separately, so
     * this is the per-item tax multiplied out rather than a percentage of the total.
     */
    static long forSale(int itemId, long unitPrice, long quantity) {
        if (quantity <= 0L) {
            return 0L;
        }
        return perItem(itemId, unitPrice) * quantity;
    }

    /**
     * Tax estimated from the coins received when the price per item is not known. Less accurate
     * than {@link #forSale} because the per-item rounding cannot be reproduced, so it is only
     * for the paths that have a total and nothing else.
     *
     * <p>The cap still has to hold. Two per cent of the total is the right estimate right up to
     * the point where an item is worth more than 250,000,000, and the pieces that are - a
     * twisted bow, a scythe - would otherwise be taxed five times over: 24,000,000 charged on a
     * 1,200,000,000 sale the game takes 5,000,000 on. So the quantity comes in too, and the
     * estimate is held to what that many items could possibly have been taxed.
     */
    static long forGrossTotal(int itemId, long grossTotal, long quantity) {
        if (grossTotal <= 0L || isExempt(itemId)) {
            return 0L;
        }
        long estimated = grossTotal / RATE_DIVISOR;
        return quantity > 0L ? Math.min(estimated, MAX_TAX_PER_ITEM * quantity) : estimated;
    }
}
