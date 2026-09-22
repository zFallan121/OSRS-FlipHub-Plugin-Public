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

/**
 * Merchant levels: lifetime profit read on Old School's own experience curve.
 *
 * <p>The curve is the game's, unaltered &mdash; the same
 * {@code floor(sum of floor(x + 300 * 2^(x/7)) / 4)} that gives 13,034,431 at 99 &mdash; scaled
 * so that <b>level 99 falls on 10B profit</b>. Nothing else about it is invented, which is the
 * point: a player already knows what level 70 means, and level 92 lands on 5B because 92 is
 * half the experience to 99.
 *
 * <p>The ten rank names in {@link RankUp#TITLES} survive as BANDS over level ranges, so the
 * ladder a player knows is the ladder they keep. {@link RankUp#BAND_FIRST_LEVEL} holds where
 * each one starts.
 *
 * <p>Past 99 the level stops and prestige carries on, because profit does. The first tier costs
 * 5B and every tier after costs a quarter more than the one before, so the climb keeps steepening
 * the way it did below 99 rather than flattening into a counter.
 */
final class FlipLevel {
    /** The skill's name, as every message about it reads. */
    static final String SKILL = "Merchant";

    static final int MAX_LEVEL = 99;

    /** Profit at level 99. Every other line is this scaled down the game's curve. */
    static final long TOP = 10_000_000_000L;

    /** Experience at level 99 on the real curve; the number the scaling is anchored to. */
    static final long XP_AT_99 = 13_034_431L;

    /** The first prestige tier past 99, and the factor each tier after grows by. */
    static final long PRESTIGE_FIRST = 5_000_000_000L;
    private static final double PRESTIGE_GROWTH = 1.25d;

    /**
     * The last prestige tier: XVIII, at 1.1 trillion gp, where the tiers stop.
     *
     * <p>Two things end here. The numeral is struck into the picture's corner on a plate, and
     * XVIII is the widest the corner holds: a wider plate covers the coins, the plate's letters
     * are I, V and X alone (40 is XL), and at XXVIII it is wider than the small picture itself.
     * And without an end the sums below do not have one either: somewhere past tier 80 a tier's
     * total no longer fits in a long, wraps negative, and prestigeFor then counts for ever --
     * on the client thread, which is where the skills tab asks.
     */
    static final int MAX_PRESTIGE = 18;

    /**
     * PROFIT[n] is the profit needed for level n. Index 0 is unused so the array reads as the
     * level, which is how every caller wants it.
     *
     * <p>Not private: the development client's level-up tester moves one line to just above the
     * player's profit, so that a real sale can cross it on demand. Nothing that ships writes here.
     */
    static final long[] PROFIT = new long[MAX_LEVEL + 1];

    static {
        double points = 0d;
        long[] xp = new long[MAX_LEVEL + 1];
        for (int level = 1; level < MAX_LEVEL; level++) {
            points += Math.floor(level + 300d * Math.pow(2d, level / 7d));
            xp[level + 1] = (long) Math.floor(points / 4d);
        }
        for (int level = 1; level <= MAX_LEVEL; level++) {
            // Rounded, not truncated: at 99 this has to land exactly on TOP, and truncating
            // leaves it a few gp short, so the top level would read as unreachable.
            PROFIT[level] = Math.round((double) xp[level] / XP_AT_99 * TOP);
        }
    }

    private FlipLevel() {
    }

    /** Profit needed for a level, 1..99. */
    static long profitFor(int level) {
        return PROFIT[Math.max(1, Math.min(MAX_LEVEL, level))];
    }

    /** The level a profit reaches, 1..99. Negative profit is level 1, as rank 0 always was. */
    static int levelFor(long profit) {
        if (profit < PROFIT[2]) {
            return 1;
        }
        int level = 1;
        // Ninety-nine comparisons at most, and only ever off a fresh total; a binary search
        // here would save nothing measurable and read worse.
        while (level < MAX_LEVEL && profit >= PROFIT[level + 1]) {
            level++;
        }
        return level;
    }

    /** Profit still to go before the next level, or 0 at 99. */
    static long toNextLevel(long profit) {
        int level = levelFor(profit);
        if (level >= MAX_LEVEL) {
            return 0L;
        }
        // Below 99 the line is always above the profit, so this is never truly negative. It
        // only comes out negative when a profit near the bottom of a long leaves more to go
        // than a long holds, and then the most a long holds is the honest answer.
        long left = PROFIT[level + 1] - profit;
        return left < 0 ? Long.MAX_VALUE : left;
    }

    /**
     * Total profit needed for a prestige tier, counting from 1. Tier 0 is 99 itself, and a
     * tier past {@link #MAX_PRESTIGE} costs what the last one does.
     */
    static long profitForPrestige(int tier) {
        long total = TOP;
        double step = PRESTIGE_FIRST;
        for (int i = 0; i < Math.min(tier, MAX_PRESTIGE); i++) {
            total += Math.round(step);
            step *= PRESTIGE_GROWTH;
        }
        return total;
    }

    /** Prestige tiers earned past 99; 0 below the cap, and never past {@link #MAX_PRESTIGE}. */
    static int prestigeFor(long profit) {
        int tier = 0;
        while (tier < MAX_PRESTIGE && profit >= profitForPrestige(tier + 1)) {
            tier++;
        }
        return tier;
    }

    /** I, II, III... for the prestige numeral. */
    static String roman(int tier) {
        if (tier <= 0) {
            return "";
        }
        final int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        final String[] letters = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder out = new StringBuilder();
        int left = tier;
        for (int i = 0; i < values.length; i++) {
            while (left >= values[i]) {
                out.append(letters[i]);
                left -= values[i];
            }
        }
        return out.toString();
    }
}
