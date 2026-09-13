package com.osrsfliphub;

import java.util.Set;

/**
 * The Grand Exchange sale tax, in one place.
 */
final class GeTax {
    /** No single item is taxed more than this, however much it sold for. */
    static final long MAX_TAX_PER_ITEM = 5_000_000L;

    /** Two percent, expressed as the divisor the game's own rounding implies. */
    private static final int RATE_DIVISOR = 50;

    /**
     * The items the game charges no sale tax on, whatever they sell for.
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
     */
    static long forGrossTotal(int itemId, long grossTotal) {
        if (grossTotal <= 0L || isExempt(itemId)) {
            return 0L;
        }
        return grossTotal / RATE_DIVISOR;
    }
}
