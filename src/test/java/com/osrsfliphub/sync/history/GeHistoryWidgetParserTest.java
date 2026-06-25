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
        GeHistoryTrade trade = GeHistoryWidgetParser.parseTrade(
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

    @Test
    public void parseTradeInfersGrossSellPriceWhenOnlyNetShown() {
        GeHistoryTrade trade = GeHistoryWidgetParser.parseTrade(
            "Sold:",
            20997,
            8_935,
            "3,681,220 coins\n= 412 each"
        );

        assertNotNull(trade);
        assertEquals(420, trade.price);
        assertEquals(3_681_220L, trade.totalGp);
    }

    @Test
    public void parseTradeBuildsBuyTradeUsingDisplayedTotals() {
        GeHistoryTrade trade = GeHistoryWidgetParser.parseTrade(
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
        GeHistoryTrade trade = GeHistoryWidgetParser.parseTrade(
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
        GeHistoryTrade trade = GeHistoryWidgetParser.parseTrade(
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

    @Test
    public void parseCoinsHandlesNbspBetweenAmountAndCoins() {
        long total = GeHistoryWidgetParser.parseCoins("19,855,000\u00A0coins\n= 1,805 each");

        assertEquals(19_855_000L, total);
    }

    @Test
    public void parseGrossCoinsHandlesPlusBreakdownVariant() {
        long gross = GeHistoryWidgetParser.parseGrossCoins("(20,142,000 + 16,000)");

        assertEquals(20_142_000L, gross);
    }
}
