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

import java.awt.image.BufferedImage;
import net.runelite.client.util.ImageUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class RankUpTest {
    // The gp lines the website ranked on before levels existed.
    private static final long[] OLD_LINES = {0L, 200_000L, 1_000_000L, 4_000_000L, 20_000_000L,
        100_000_000L, 300_000_000L, 800_000_000L, 2_000_000_000L, 6_000_000_000L};

    @Test
    public void everyBandBeginsAtItsFirstLevel() {
        assertEquals(0, RankUp.bandFor(1));
        assertEquals(0, RankUp.bandFor(2));
        assertEquals(1, RankUp.bandFor(3));
        assertEquals(3, RankUp.bandFor(35));
        assertEquals(4, RankUp.bandFor(36));
        assertEquals(4, RankUp.bandFor(51));
        assertEquals(5, RankUp.bandFor(52));
        assertEquals(8, RankUp.bandFor(92));
        assertEquals(9, RankUp.bandFor(93));
        assertEquals(9, RankUp.bandFor(99));
    }

    @Test
    public void theBandsAreTheWebsiteLaddersOwnLevels() {
        assertEquals(10, RankUp.BAND_FIRST_LEVEL.length);
        assertEquals(RankUp.BAND_FIRST_LEVEL.length, RankUp.TITLES.length);
        assertEquals(RankUp.BAND_FIRST_LEVEL.length, RankUp.COLOURS.length);
        for (int band = 1; band < RankUp.BAND_FIRST_LEVEL.length; band++) {
            assertTrue(RankUp.BAND_FIRST_LEVEL[band] > RankUp.BAND_FIRST_LEVEL[band - 1]);
        }
        assertEquals("Varrock Hustler", RankUp.TITLES[4]);
        for (int band = 0; band < OLD_LINES.length; band++) {
            // Each band starts on the level its old gp line fell on, which is at or below that
            // line. Above it, the change would demote an account that had done nothing.
            assertEquals(RankUp.TITLES[band],
                RankUp.BAND_FIRST_LEVEL[band], FlipLevel.levelFor(OLD_LINES[band]));
            assertTrue(RankUp.TITLES[band],
                FlipLevel.profitFor(RankUp.BAND_FIRST_LEVEL[band]) <= OLD_LINES[band]);
        }
    }

    @Test
    public void aSaleOverALevelLineEarnsThatLevel() {
        long line = FlipLevel.profitFor(36);
        assertEquals(36, RankUp.earned(line - 1, line, 35));
    }

    @Test
    public void aSaleThatCrossesNothingEarnsNothing() {
        long line = FlipLevel.profitFor(36);
        assertEquals(-1, RankUp.earned(line, line + 1, 0));
        // A losing sale.
        assertEquals(-1, RankUp.earned(line + 1, line, 0));
    }

    @Test
    public void severalLevelsInOneSaleEarnTheHighestOnly() {
        assertEquals(38, RankUp.earned(FlipLevel.profitFor(35), FlipLevel.profitFor(38), 34));
    }

    @Test
    public void aLevelAlreadyCelebratedIsNeverCelebratedAgain() {
        long line = FlipLevel.profitFor(36);
        // Under the line after a losing sale, then back over it.
        assertEquals(-1, RankUp.earned(line - 1, line, 36));
        // After a wipe, climbing back through levels already had.
        assertEquals(-1, RankUp.earned(FlipLevel.profitFor(9), FlipLevel.profitFor(11), 36));
        assertEquals(37, RankUp.earned(FlipLevel.profitFor(36), FlipLevel.profitFor(37), 36));
    }

    @Test
    public void aSaleOverATierEarnsThatPrestige() {
        assertEquals(1, RankUp.earnedPrestige(14_999_999_999L, 15_000_000_000L, 0));
        assertEquals(2, RankUp.earnedPrestige(15_000_000_000L, 21_250_000_000L, 1));
    }

    @Test
    public void prestigeIsNeverEarnedBelowTheCapOrTwice() {
        // There is nothing to earn until the 10B for level 99 is paid for.
        assertEquals(-1, RankUp.earnedPrestige(1_000_000L, 9_999_999_999L, 0));
        // A tier already celebrated stays quiet, however the total wanders back over it.
        assertEquals(-1, RankUp.earnedPrestige(14_999_999_999L, 15_000_000_000L, 1));
        // Two tiers in one sale earn the higher only.
        assertEquals(2, RankUp.earnedPrestige(10_000_000_000L, 21_250_000_000L, 0));
        // A losing sale earns nothing.
        assertEquals(-1, RankUp.earnedPrestige(21_250_000_000L, 15_000_000_000L, 0));
    }

    @Test
    public void theLevelStopsWhilePrestigeCarriesOn() {
        // Past the cap no further level can be earned, whatever the total does.
        assertEquals(-1, RankUp.earned(15_000_000_000L, 51_000_000_000L, 99));
        assertEquals(4, RankUp.earnedPrestige(15_000_000_000L, 51_000_000_000L, 1));
    }

    @Test
    public void theMessageSaysAnBeforeAVowel() {
        assertEquals("a Greenhorn", RankUp.named(1));
        assertEquals("an Upstart", RankUp.named(2));
        assertEquals("an Edgeville Operator", RankUp.named(3));
        assertEquals("a Trader", RankUp.named(5));
        assertEquals("a GE Mogul", RankUp.named(8));
    }

    @Test
    public void theTooltipNamesTheRankAndTheLevelTheNextOneStartsAt() {
        assertEquals("<html>Flip Rank: Trader<br>Next: Professional at level 63</html>", RankUp.tooltip(5));
        assertEquals("<html>Flip Rank: Lumbridge Looter<br>Next: Greenhorn at level 3</html>", RankUp.tooltip(0));
        assertEquals("<html>Flip Rank: Platinum Flipper<br>Next: GE Mogul at level 82</html>", RankUp.tooltip(7));
        assertEquals("<html>Flip Rank: Gielinor Elite<br>The highest rank</html>", RankUp.tooltip(9));
    }

    @Test
    public void everyRankHasAPictureAtBothSizes() {
        for (int i = 1; i <= RankUp.TITLES.length; i++) {
            BufferedImage picture = ImageUtil.loadImageResource(RankUp.class, "/com/osrsfliphub/rank/" + i + ".png");
            assertNotNull(picture);
            assertEquals(64, Math.max(picture.getWidth(), picture.getHeight()));
            // The Profile tab's picture fills its 28 by 28 space without spilling over it.
            BufferedImage small = ImageUtil.loadImageResource(RankUp.class, "/com/osrsfliphub/rank/" + i + "-panel.png");
            assertNotNull(small);
            assertEquals(28, Math.max(small.getWidth(), small.getHeight()));
        }
    }

    @Test
    public void theRightClickLeavesTheXpTrackerAWordToFind() {
        String option = SkillTab.guideOption();
        // RuneLite's own XP Tracker takes any option in the skills tab starting with "View",
        // splits it on spaces and reads the second word as a skill name. A bare "View" has no
        // second word, and it threw on every hover of our square until this was fixed.
        assertTrue(option.startsWith("View"));
        assertTrue("needs a second word", option.split(" ").length > 1);
        assertEquals("Merchant", option.split(" ")[1].replaceAll("<[^>]*>", ""));
    }

    @Test
    public void theSkillIconDropsIntoACellUnscaled() {
        BufferedImage icon = ImageUtil.loadImageResource(RankUp.class, "/com/osrsfliphub/rank/merchant.png");
        assertNotNull(icon);
        // 25 square is what the skills tab cell was measured for; anything else would be resized
        // at runtime, which is the softness the rank pictures already had to be saved from.
        assertEquals(25, icon.getWidth());
        assertEquals(25, icon.getHeight());
        // The tab only leaves 20px under the grid, so the short row carries this one instead.
        BufferedImage small = ImageUtil.loadImageResource(RankUp.class, "/com/osrsfliphub/rank/merchant-19.png");
        assertNotNull(small);
        assertEquals(19, small.getWidth());
        assertEquals(19, small.getHeight());
        for (int x = 0; x < small.getWidth(); x++) {
            for (int y = 0; y < small.getHeight(); y++) {
                int alpha = (small.getRGB(x, y) >>> 24) & 0xFF;
                assertTrue("soft edge at " + x + "," + y, alpha == 0 || alpha == 255);
            }
        }
    }

    /** Below the cap there is no tier, so the art is handed back untouched rather than copied. */
    @Test
    public void thePictureIsLeftAloneBelowTheCap() {
        BufferedImage icon = ImageUtil.loadImageResource(RankUp.class, "/com/osrsfliphub/rank/merchant.png");
        assertSame(icon, SkillTab.marked(icon, 0));
        // The bottom row of the art is clear all the way across, which is what the plaque uses.
        assertEquals(0, struckWidth(icon));
    }

    /**
     * The plaque is measured off the bottom row, where the art has nothing: it is the only run
     * of solid pixels there, so its width is the plate's.
     */
    @Test
    public void theTierIsStruckIntoTheCorner() {
        BufferedImage icon = ImageUtil.loadImageResource(RankUp.class, "/com/osrsfliphub/rank/merchant.png");
        // Two clear pixels either side of the numeral, so a letter costs its own columns only.
        assertEquals(5, struckWidth(SkillTab.marked(icon, 1)));      // I
        assertEquals(9, struckWidth(SkillTab.marked(icon, 3)));      // III
        assertEquals(7, struckWidth(SkillTab.marked(icon, 5)));      // V
        assertEquals(17, struckWidth(SkillTab.marked(icon, 18)));    // XVIII, the widest it holds
        assertTrue("XVIII overruns the picture", struckWidth(SkillTab.marked(icon, 18)) <= 25);
    }

    /**
     * The sprite drawer paints every pixel that is not fully clear as solid and reads pure black
     * as nothing at all, so a struck picture may hold neither a soft edge nor a 0x000000 pixel.
     */
    @Test
    public void theStruckPictureStaysDrawable() {
        BufferedImage icon = ImageUtil.loadImageResource(RankUp.class, "/com/osrsfliphub/rank/merchant.png");
        BufferedImage small = ImageUtil.loadImageResource(RankUp.class, "/com/osrsfliphub/rank/merchant-19.png");
        for (int tier = 1; tier <= 18; tier++) {
            for (BufferedImage picture : new BufferedImage[] {
                SkillTab.marked(icon, tier), SkillTab.marked(small, tier)}) {
                for (int x = 0; x < picture.getWidth(); x++) {
                    for (int y = 0; y < picture.getHeight(); y++) {
                        int argb = picture.getRGB(x, y);
                        int alpha = argb >>> 24;
                        assertTrue("soft edge at " + x + "," + y, alpha == 0 || alpha == 255);
                        assertTrue("pure black at " + x + "," + y + " on tier " + tier,
                            alpha == 0 || (argb & 0xFFFFFF) != 0);
                    }
                }
            }
        }
    }

    /** How many solid pixels the bottom row of a picture holds. */
    private static int struckWidth(BufferedImage picture) {
        int wide = 0;
        for (int x = 0; x < picture.getWidth(); x++) {
            if ((picture.getRGB(x, picture.getHeight() - 1) >>> 24) != 0) {
                wide++;
            }
        }
        return wide;
    }

    @Test
    public void theGuideShortensEveryNumberPastAThousand() {
        assertEquals("0", SkillTab.shortGp(0L));
        assertEquals("999", SkillTab.shortGp(999L));
        assertEquals("1.0k", SkillTab.shortGp(1_000L));
        assertEquals("133.5k", SkillTab.shortGp(133_492L));
        assertEquals("885.3k", SkillTab.shortGp(885_347L));
        assertEquals("19.0M", SkillTab.shortGp(19_038_300L));
        assertEquals("94.9M", SkillTab.shortGp(94_871_000L));
        assertEquals("5.52B", SkillTab.shortGp(5_520_460_000L));
    }

    /** Neither step may round up into a number the next one should have carried. */
    @Test
    public void theShortFormNeverReadsAThousandOfItsOwnUnit() {
        assertEquals("999.9k", SkillTab.shortGp(999_949L));
        assertEquals("1.0M", SkillTab.shortGp(999_950L));
        assertEquals("999.5M", SkillTab.shortGp(999_499_999L));
        assertEquals("1.00B", SkillTab.shortGp(999_500_000L));
    }
}
