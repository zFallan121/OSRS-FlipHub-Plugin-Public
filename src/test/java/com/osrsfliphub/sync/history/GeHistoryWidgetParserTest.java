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
import static org.junit.Assert.assertNotNull;

public class GeHistoryWidgetParserTest {
    @Test
    public void parseTradeUsesGrossPriceWhenTaxBreakdownPresent() {
        Trade trade = WidgetParser.parseTrade(
            "Sold:",
            6332,
            11_314,
            "5,543,860 coins (5,657,000 - 113,140)\n= 490 each"
        );

        assertNotNull(trade);
        assertEquals(6332, trade.itemId);
        assertEquals(false, trade.isBuy);
        assertEquals(11_314, trade.quantity);
        assertEquals(500, trade.price);
        assertEquals(5_543_860L, trade.totalGp);
    }

    /**
     * The game writes the brackets on every sale it took a fee from (its history script, 1645,
     * tests the fee and nothing else), so a sale row without them was not taxed and its price is
     * what one item fetched. Adding a tax back, as this used to, made up a price that was never
     * paid: 420 here.
     */
    @Test
    public void aSaleRowWithNoBracketsWasNotTaxedAndItsPriceIsWhatOneFetched() {
        Trade trade = WidgetParser.parseTrade(
            "Sold:",
            20997,
            8_935,
            "3,681,220 coins\n= 412 each"
        );

        assertNotNull(trade);
        assertEquals(412, trade.price);
        assertEquals(3_681_220L, trade.totalGp);
    }

    @Test
    public void parseTradeBuildsBuyTradeUsingDisplayedTotals() {
        Trade trade = WidgetParser.parseTrade(
            "Bought:",
            8780,
            13_000,
            "26,117,000 coins\n= 2,009 each"
        );

        assertNotNull(trade);
        assertEquals(true, trade.isBuy);
        assertEquals(2_009, trade.price);
        assertEquals(26_117_000L, trade.totalGp);
    }

    @Test
    public void parseTradePrefersStateQuantityWhenWidgetQuantityIsStale() {
        Trade trade = WidgetParser.parseTrade(
            "Bought: Coconut x 11,006",
            593,
            1_630,
            "19,855,000 coins\n= 1,805 each"
        );

        assertNotNull(trade);
        assertEquals(true, trade.isBuy);
        assertEquals(11_006, trade.quantity);
        assertEquals(1_805, trade.price);
        assertEquals(19_855_000L, trade.totalGp);
    }

    @Test
    public void parseTradeInfersQuantityFromDetailsWhenStateOmitsIt() {
        Trade trade = WidgetParser.parseTrade(
            "Bought:",
            593,
            1_630,
            "19,855,000 coins\n= 1,805 each"
        );

        assertNotNull(trade);
        assertEquals(11_000, trade.quantity);
        assertEquals(1_805, trade.price);
        assertEquals(19_855_000L, trade.totalGp);
    }

    // The three rows below are written the way the game's own history script (1645) writes them:
    // colour tags, line breaks, and a non-breaking space wherever a number meets a word or sign.

    @Test
    public void aSaleRowAsTheGameWritesItGivesThePriceInItsBrackets() {
        Trade trade = WidgetParser.parseTrade(
            "Sold:",
            30125,
            128,
            "<col=ffb83f>426,240 coins</col><br><col=9f9f9f>(434,816 - 8,696)</col><br>= 3,330 each"
        );

        assertNotNull(trade);
        assertEquals(128, trade.quantity);
        assertEquals(3_397, trade.price);
        assertEquals(426_240L, trade.totalGp);
    }

    @Test
    public void aSaleWhoseTaxIsCappedTakesItsPriceFromTheBracketsNotFromAGuess() {
        // 300,000,000 less the capped 5,000,000. Worked back from the 295,000,000 received, at 2%,
        // the price came out as 301,020,389.
        Trade trade = WidgetParser.parseTrade(
            "Sold:",
            13652,
            1,
            "<col=ffb83f>295,000,000 coins</col><br><col=9f9f9f>(300,000,000 - 5,000,000)</col>"
        );

        assertNotNull(trade);
        assertEquals(300_000_000, trade.price);
        assertEquals(295_000_000L, trade.totalGp);
    }

    @Test
    public void aBulkBuyKeepsTheQuantityOnItsIconNotOneWorkedBackFromEach() {
        // 10,000 bought for 25,000 shows "= 2 each", rounded down. 25,000 / 2 would be 12,500, and
        // a wrong quantity also changes the row's signature in the sync's stored place.
        Trade trade = WidgetParser.parseTrade(
            "Bought:",
            314,
            10_000,
            "<col=ffb83f>25,000 coins</col><br>= 2 each"
        );

        assertNotNull(trade);
        assertEquals(10_000, trade.quantity);
        assertEquals(2, trade.price);
        assertEquals(25_000L, trade.totalGp);
    }

    @Test
    public void parseCoinsHandlesNbspBetweenAmountAndCoins() {
        long total = WidgetParser.parseCoins("19,855,000\u00A0coins\n= 1,805 each");

        assertEquals(19_855_000L, total);
    }

    @Test
    public void parseGrossCoinsHandlesPlusBreakdownVariant() {
        long gross = WidgetParser.parseGrossCoins("(20,142,000 + 16,000)");

        assertEquals(20_142_000L, gross);
    }

    // Past max cash. Since 30 Sep 2026 one item can cost more than 2,147,483,647, and the rows
    // below are written as the game writes any other.

    /** Held in an int, 2,394,000,000 came out negative and the row was thrown away. */
    @Test
    public void aPurchasePastMaxCashIsReadWhole() {
        Trade trade = WidgetParser.parseTrade(
            "Bought:",
            20011,
            1,
            "<col=ffb83f>2,394,000,000\u00A0coins</col><br>=\u00A02,394,000,000\u00A0each"
        );

        assertNotNull(trade);
        assertEquals(true, trade.isValid());
        assertEquals(1, trade.quantity);
        assertEquals(2_394_000_000L, trade.price);
        assertEquals(2_394_000_000L, trade.totalGp);
    }

    /** Held in an int, 9,199,000,000 came out as 609,065,408: a real-looking wrong price. */
    @Test
    public void aPurchaseFarPastMaxCashIsReadWhole() {
        Trade trade = WidgetParser.parseTrade("Bought:", 20014, 1, "<col=ffb83f>9,199,000,000\u00A0coins</col>");

        assertNotNull(trade);
        assertEquals(9_199_000_000L, trade.price);
        assertEquals(9_199_000_000L, trade.totalGp);
    }

    @Test
    public void aSalePastMaxCashTakesItsPriceFromTheBrackets() {
        Trade trade = WidgetParser.parseTrade(
            "Sold:",
            20011,
            1,
            "<col=ffb83f>2,389,000,000\u00A0coins</col><br><col=9f9f9f>(2,394,000,000\u00A0-\u00A05,000,000)</col>"
        );

        assertNotNull(trade);
        assertEquals(2_394_000_000L, trade.price);
        assertEquals(2_389_000_000L, trade.totalGp);
    }

    /** Two sold in one offer: the brackets hold the total, and the price is one item's. */
    @Test
    public void aSaleOfSeveralPastMaxCashGivesThePriceOfOne() {
        Trade trade = WidgetParser.parseTrade(
            "Sold:",
            20011,
            2,
            "<col=ffb83f>4,778,000,000\u00A0coins</col><br><col=9f9f9f>(4,788,000,000\u00A0-\u00A010,000,000)</col>"
        );

        assertNotNull(trade);
        assertEquals(2, trade.quantity);
        assertEquals(2_394_000_000L, trade.price);
        assertEquals(4_778_000_000L, trade.totalGp);
    }

    /**
     * No brackets, no fee, at any price: what was received is the price. An item the game stops
     * taxing before the plugin's own list hears of it is the case that matters, and a tax worked
     * back onto it gave 300,000,000, 2,394,000,000 and 245,000,000 here.
     */
    @Test
    public void aSaleWithNoBracketsHasNoTaxAddedBackAtAnyPrice() {
        assertEquals(295_000_000L,
            WidgetParser.parseTrade("Sold:", 13652, 1, "<col=ffb83f>295,000,000\u00A0coins</col>").price);
        assertEquals(2_389_000_000L,
            WidgetParser.parseTrade("Sold:", 20011, 1, "<col=ffb83f>2,389,000,000\u00A0coins</col>").price);
        assertEquals(240_100_000L,
            WidgetParser.parseTrade("Sold:", 13652, 1, "<col=ffb83f>240,100,000\u00A0coins</col>").price);
        // A bond, which is on the list, read the same before and after.
        assertEquals(14_000_000L,
            WidgetParser.parseTrade("Sold:", 13190, 1, "<col=ffb83f>14,000,000\u00A0coins</col>").price);
    }
}
