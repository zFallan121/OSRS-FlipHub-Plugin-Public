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
import static org.junit.Assert.assertTrue;

public class RankUpTest {
    @Test
    public void everyLineBelongsToTheRankItStarts() {
        assertEquals(0, RankUp.rankFor(-5_000_000L));
        assertEquals(0, RankUp.rankFor(0L));
        assertEquals(0, RankUp.rankFor(199_999L));
        assertEquals(1, RankUp.rankFor(200_000L));
        assertEquals(2, RankUp.rankFor(1_000_000L));
        assertEquals(3, RankUp.rankFor(4_000_000L));
        assertEquals(3, RankUp.rankFor(19_999_999L));
        assertEquals(4, RankUp.rankFor(20_000_000L));
        assertEquals(5, RankUp.rankFor(206_631_022L));
        assertEquals(8, RankUp.rankFor(5_999_999_999L));
        assertEquals(9, RankUp.rankFor(6_000_000_000L));
        assertEquals(9, RankUp.rankFor(Long.MAX_VALUE));
    }

    @Test
    public void theLadderMatchesTheWebsite() {
        assertEquals(10, RankUp.LINES.length);
        assertEquals(RankUp.LINES.length, RankUp.TITLES.length);
        assertEquals(RankUp.LINES.length, RankUp.COLOURS.length);
        for (int i = 1; i < RankUp.LINES.length; i++) {
            assertTrue(RankUp.LINES[i] > RankUp.LINES[i - 1]);
        }
        assertEquals("Varrock Hustler", RankUp.TITLES[4]);
        assertEquals(20_000_000L, RankUp.LINES[4]);
    }

    @Test
    public void aSaleOverALineEarnsThatRank() {
        assertEquals(4, RankUp.earned(19_600_000L, 20_300_000L, 3));
    }

    @Test
    public void aSaleThatCrossesNothingEarnsNothing() {
        assertEquals(-1, RankUp.earned(50_000_000L, 52_000_000L, 0));
        assertEquals(-1, RankUp.earned(52_000_000L, 50_000_000L, 0));
    }

    @Test
    public void twoLinesInOneSaleEarnTheHigherRankOnly() {
        assertEquals(2, RankUp.earned(150_000L, 1_200_000L, 0));
    }

    @Test
    public void aRankAlreadyCelebratedIsNeverCelebratedAgain() {
        // Under 20M after a losing sale, then back over it.
        assertEquals(-1, RankUp.earned(19_900_000L, 20_400_000L, 4));
        // After a wipe, climbing back through ranks already had.
        assertEquals(-1, RankUp.earned(900_000L, 1_100_000L, 4));
        assertEquals(5, RankUp.earned(99_900_000L, 100_100_000L, 4));
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
    public void theTooltipNamesTheRankAndTheNextLine() {
        assertEquals("<html>Flip Rank: Trader<br>Next: Professional at 300M</html>", RankUp.tooltip(5));
        assertEquals("<html>Flip Rank: Lumbridge Looter<br>Next: Greenhorn at 200K</html>", RankUp.tooltip(0));
        assertEquals("<html>Flip Rank: Platinum Flipper<br>Next: GE Mogul at 2B</html>", RankUp.tooltip(7));
        assertEquals("<html>Flip Rank: Gielinor Elite<br>The highest rank</html>", RankUp.tooltip(9));
    }

    @Test
    public void everyRankHasAPictureThatFitsTheChatbox() {
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
}
