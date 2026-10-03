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
import static org.junit.Assert.assertNull;

public class LocalItemEnrichmentServiceTest {
    /**
     * The card used to take an exact two percent for the margin and a floored two percent for
     * ROI, neither of them capped. On a big-ticket item the uncapped tax swallowed most of the
     * margin, and the two numbers beside each other described different trades.
     */
    @Test
    public void marginOnABigTicketItemStopsAtTheFiveMillionTaxCap() {
        FlipHubItem item = itemPriced(4151, 1_400_000_000, 1_450_000_000);
        item.ge_limit_remaining = 8;

        new ItemEnrichment().applyMarginInfo(item);

        assertEquals(Long.valueOf(45_000_000L), item.margin);
        assertEquals(Long.valueOf(360_000_000L), item.margin_x_limit);
    }

    @Test
    public void marginAndRoiAgreeOnTheSameTax() {
        FlipHubItem item = itemPriced(561, 1_000, 1_049);

        new ItemEnrichment().applyMarginInfo(item);

        assertEquals(Long.valueOf(29L), item.margin);
        assertEquals(2.9d, item.roi_percent, 0.0001d);
    }

    /** Under fifty coins the tax rounds away, so the margin is the plain difference. */
    @Test
    public void aCheapItemIsNotTaxed() {
        FlipHubItem item = itemPriced(1511, 20, 45);

        new ItemEnrichment().applyMarginInfo(item);

        assertEquals(Long.valueOf(25L), item.margin);
    }

    /**
     * The 3rd age axe on 3 Oct 2026: 9,002,000,003 to buy, 9,199,000,000 to sell. Both used to
     * be left N/A for being past max cash, which left the whole card with nothing on it.
     */
    @Test
    public void aWikiPriceAboveMaxCashIsShownWhole() {
        WikiPriceEntry axe = new WikiPriceEntry();
        axe.high = 9_199_000_000L;
        axe.low = 9_002_000_003L;
        PluginRuntime runtime = new PluginRuntime();
        runtime.setPanelVisible(true);
        WikiPrice prices = new WikiPrice(60_000L, 0L, runtime,
            callback -> callback.onSuccess(java.util.Collections.singletonMap(20014, axe)));
        prices.refreshPrices();
        Bridge.set(com.google.inject.Guice.createInjector(binder -> binder.bind(WikiPrice.class).toInstance(prices)));
        try {
            FlipHubItem item = new FlipHubItem();
            new ItemEnrichment().applyGuidePrices(item, 20014, false);

            assertEquals(Long.valueOf(9_199_000_000L), item.instasell_price);
            assertEquals(Long.valueOf(9_002_000_003L), item.instabuy_price);
        } finally {
            Bridge.set(null);
        }
    }

    /**
     * The 3rd age pickaxe, 2,320,000,000 to 2,394,000,000 with 40 left to buy: the margin is the
     * difference less the capped tax, and forty of them is itself past max cash.
     */
    @Test
    public void marginOnAnItemPastMaxCashIsWorkedOutInFull() {
        FlipHubItem item = itemPriced(20011, 2_320_000_000L, 2_394_000_000L);
        item.ge_limit_remaining = 40;

        new ItemEnrichment().applyMarginInfo(item);

        assertEquals(Long.valueOf(69_000_000L), item.margin);
        assertEquals(Long.valueOf(2_760_000_000L), item.margin_x_limit);
        assertEquals(2.9741d, item.roi_percent, 0.0001d);
    }

    /** With only one live price, the other side is the player's own last trade, in full. */
    @Test
    public void marginOnAnItemPastMaxCashCanUseTheLastPurchase() {
        FlipHubItem item = new FlipHubItem();
        item.item_id = 20011;
        item.instasell_price = 2_394_000_000L;
        item.last_buy_price = 2_100_000_000L;

        new ItemEnrichment().applyMarginInfo(item);

        assertEquals(Long.valueOf(289_000_000L), item.margin);
    }

    /** With no live prices at all, the margin is the player's own last purchase and sale. */
    @Test
    public void marginOnAnItemPastMaxCashCanUseTheLastTradesAlone() {
        FlipHubItem item = new FlipHubItem();
        item.item_id = 20011;
        item.last_buy_price = 2_320_000_000L;
        item.last_sell_price = 2_394_000_000L;

        new ItemEnrichment().applyMarginInfo(item);

        assertEquals(Long.valueOf(69_000_000L), item.margin);
    }

    /** A last price the trade file could not tell is left empty, and no margin is made up from it. */
    @Test
    public void noMarginIsShownFromALastPriceThatIsNotKnown() {
        FlipHubItem item = new FlipHubItem();
        item.item_id = 20011;
        item.instabuy_price = 2_320_000_000L;
        item.ge_limit_remaining = 40;

        new ItemEnrichment().applyMarginInfo(item);

        assertNull(item.margin);
        assertNull(item.margin_x_limit);
        assertNull(item.roi_percent);
    }

    private static FlipHubItem itemPriced(int itemId, long buy, long sell) {
        FlipHubItem item = new FlipHubItem();
        item.item_id = itemId;
        item.instabuy_price = buy;
        item.instasell_price = sell;
        return item;
    }
}
