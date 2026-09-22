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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FlipLevelTest {
    @Test
    public void theCurveIsTheGamesOwnScaledToTenBillion() {
        assertEquals(0L, FlipLevel.profitFor(1));
        assertEquals(FlipLevel.TOP, FlipLevel.profitFor(FlipLevel.MAX_LEVEL));
        // 92 is halfway to 99 in experience, so it is halfway here too.
        assertEquals(5_000_028_770L, FlipLevel.profitFor(92));
        for (int level = 2; level <= FlipLevel.MAX_LEVEL; level++) {
            assertTrue("level " + level, FlipLevel.profitFor(level) > FlipLevel.profitFor(level - 1));
        }
        // Off the ends, rather than out of bounds.
        assertEquals(FlipLevel.profitFor(1), FlipLevel.profitFor(0));
        assertEquals(FlipLevel.TOP, FlipLevel.profitFor(200));
    }

    @Test
    public void everyLevelOwnsTheProfitItStartsAt() {
        assertEquals(1, FlipLevel.levelFor(Long.MIN_VALUE));
        assertEquals(1, FlipLevel.levelFor(0L));
        for (int level = 2; level <= FlipLevel.MAX_LEVEL; level++) {
            long line = FlipLevel.profitFor(level);
            assertEquals("level " + level, level, FlipLevel.levelFor(line));
            assertEquals("level " + level, level - 1, FlipLevel.levelFor(line - 1));
        }
        assertEquals(FlipLevel.MAX_LEVEL, FlipLevel.levelFor(Long.MAX_VALUE));
    }

    @Test
    public void theCountdownRunsAcrossTheLevelAndStopsAt99() {
        long floor = FlipLevel.profitFor(50);
        long ceiling = FlipLevel.profitFor(51);
        assertEquals(ceiling - floor, FlipLevel.toNextLevel(floor));
        assertEquals(1L, FlipLevel.toNextLevel(ceiling - 1));
        assertEquals(0L, FlipLevel.toNextLevel(FlipLevel.TOP));
        assertEquals(0L, FlipLevel.toNextLevel(FlipLevel.TOP * 3));
    }

    @Test
    public void prestigeCarriesOnPastTheTopAndKeepsSteepening() {
        assertEquals(FlipLevel.TOP, FlipLevel.profitForPrestige(0));
        assertEquals(15_000_000_000L, FlipLevel.profitForPrestige(1));
        assertEquals(21_250_000_000L, FlipLevel.profitForPrestige(2));
        assertEquals(29_062_500_000L, FlipLevel.profitForPrestige(3));
        // Each tier costs a quarter more than the one before, so no two steps are equal.
        long previous = FlipLevel.PRESTIGE_FIRST;
        for (int tier = 2; tier <= 5; tier++) {
            long step = FlipLevel.profitForPrestige(tier) - FlipLevel.profitForPrestige(tier - 1);
            assertTrue("tier " + tier, step > previous);
            previous = step;
        }
    }

    @Test
    public void prestigeIsZeroUntilTheTopIsPaidFor() {
        assertEquals(0, FlipLevel.prestigeFor(0L));
        assertEquals(0, FlipLevel.prestigeFor(FlipLevel.TOP - 1));
        assertEquals(0, FlipLevel.prestigeFor(FlipLevel.TOP));
        assertEquals(0, FlipLevel.prestigeFor(14_999_999_999L));
        assertEquals(1, FlipLevel.prestigeFor(15_000_000_000L));
        assertEquals(1, FlipLevel.prestigeFor(21_249_999_999L));
        assertEquals(2, FlipLevel.prestigeFor(21_250_000_000L));
    }

    /**
     * Past the last tier nothing more is earned, and nothing breaks. Unbounded, a tier's total
     * wrapped negative somewhere past tier 80 and the count never ended -- on the client
     * thread, where the skills tab asks.
     */
    @Test(timeout = 5_000)
    public void theTiersStopAtTheLastAndEveryProfitHasAnAnswer() {
        long last = FlipLevel.profitForPrestige(FlipLevel.MAX_PRESTIGE);
        assertTrue("fits in a long", last > FlipLevel.profitForPrestige(FlipLevel.MAX_PRESTIGE - 1));
        assertEquals(FlipLevel.MAX_PRESTIGE, FlipLevel.prestigeFor(last));
        assertEquals(FlipLevel.MAX_PRESTIGE - 1, FlipLevel.prestigeFor(last - 1));
        assertEquals(FlipLevel.MAX_PRESTIGE, FlipLevel.prestigeFor(Long.MAX_VALUE));
        assertEquals(0, FlipLevel.prestigeFor(Long.MIN_VALUE));
        assertEquals(0, FlipLevel.prestigeFor(-1L));
        // A tier past the last costs what the last does; one before the first is 99 itself.
        assertEquals(last, FlipLevel.profitForPrestige(Integer.MAX_VALUE));
        assertEquals(FlipLevel.TOP, FlipLevel.profitForPrestige(Integer.MIN_VALUE));
    }

    @Test
    public void theCountdownHasAnAnswerForEveryProfit() {
        assertEquals(FlipLevel.profitFor(2) + 5, FlipLevel.toNextLevel(-5L));
        // More to go than a long holds is as much as a long holds, never "nothing left".
        assertEquals(Long.MAX_VALUE, FlipLevel.toNextLevel(Long.MIN_VALUE));
        assertEquals(0L, FlipLevel.toNextLevel(Long.MAX_VALUE));
        assertEquals(FlipLevel.MAX_LEVEL, FlipLevel.levelFor(Long.MAX_VALUE));
    }

    /** No tier asked for can draw a mark the picture cannot hold, whichever picture. */
    @Test
    public void everyTierDrawsOnBothPictures() {
        java.awt.image.BufferedImage icon = net.runelite.client.util.ImageUtil
            .loadImageResource(RankUp.class, "/com/osrsfliphub/rank/merchant.png");
        java.awt.image.BufferedImage small = net.runelite.client.util.ImageUtil
            .loadImageResource(RankUp.class, "/com/osrsfliphub/rank/merchant-19.png");
        int[] tiers = {FlipLevel.MAX_PRESTIGE, 19, 28, 38, 40, 1000, Integer.MAX_VALUE};
        for (java.awt.image.BufferedImage picture : new java.awt.image.BufferedImage[] {icon, small}) {
            java.awt.image.BufferedImage top = SkillTab.marked(picture, FlipLevel.MAX_PRESTIGE);
            for (int tier : tiers) {
                java.awt.image.BufferedImage marked = SkillTab.marked(picture, tier);
                assertEquals(picture.getWidth(), marked.getWidth());
                for (int x = 0; x < marked.getWidth(); x++) {
                    for (int y = 0; y < marked.getHeight(); y++) {
                        assertEquals("tier " + tier + " at " + x + "," + y, top.getRGB(x, y), marked.getRGB(x, y));
                    }
                }
            }
        }
    }

    @Test
    public void theNumeralReadsAsRoman() {
        assertEquals("", FlipLevel.roman(0));
        assertEquals("", FlipLevel.roman(-3));
        assertEquals("I", FlipLevel.roman(1));
        assertEquals("IV", FlipLevel.roman(4));
        assertEquals("IX", FlipLevel.roman(9));
        assertEquals("XIV", FlipLevel.roman(14));
        assertEquals("XL", FlipLevel.roman(40));
    }
}
