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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OfferStampFallbackBuilderTest {
    @Test
    public void buildItemsSkipsInvalidStamps() {
        OfferStampFallbackBuilder builder = new OfferStampFallbackBuilder();
        Stamp invalid = new Stamp();
        invalid.itemId = 0;

        List<FlipHubItem> items = builder.buildItems(Map.of(0, invalid), Map.of());

        assertTrue(items.isEmpty());
    }

    @Test
    public void buildItemsBuildsSellItem() {
        OfferStampFallbackBuilder builder = new OfferStampFallbackBuilder();
        Stamp sell = stamp(11286, 5_100_000, false);

        List<FlipHubItem> items = builder.buildItems(Map.of(0, sell), Map.of());

        assertEquals(1, items.size());
        FlipHubItem item = items.get(0);
        assertEquals(11286, item.item_id);
        assertEquals(Long.valueOf(5_100_000L), item.last_sell_price);
        assertNull(item.last_buy_price);
    }

    /**
     * An axe on sale at 8,351,000,000 and nothing traded yet. The saved slot position holds the
     * price at the cap; the offer still in the slot holds it whole, and the card shows that.
     */
    @Test
    public void anOpenOfferPastMaxCashShowsItsRealPrice() {
        OfferSnapshot offer = new OfferSnapshot(3, 12426, 8_351_000_000L, 1, 0, 0L, "SELLING", false);
        Stamp axe = Stamp.fromSnapshot(offer, 1_000L);

        List<FlipHubItem> items = new OfferStampFallbackBuilder().buildItems(Map.of(3, axe), Map.of(3, offer));

        assertEquals(Integer.MAX_VALUE, axe.price);
        assertEquals(Long.valueOf(8_351_000_000L), items.get(0).last_sell_price);
        assertNull(items.get(0).last_buy_price);
    }

    /**
     * Before the game has reported the slot - straight after a start - only the capped position
     * is known, and the cap is no price: the card says N/A. So does a slot that now holds another
     * item, or a position in another slot.
     */
    @Test
    public void aPricePastMaxCashShowsAsNotAvailableUntilTheOfferIsRead() {
        OfferSnapshot offer = new OfferSnapshot(3, 12426, 8_351_000_000L, 1, 0, 0L, "SELLING", false);
        Stamp axe = Stamp.fromSnapshot(offer, 1_000L);
        OfferSnapshot another = new OfferSnapshot(3, 20011, 2_394_000_000L, 1, 0, 0L, "SELLING", false);
        OfferStampFallbackBuilder builder = new OfferStampFallbackBuilder();

        assertNull(builder.buildItems(Map.of(3, axe), Map.of()).get(0).last_sell_price);
        assertNull(builder.buildItems(Map.of(3, axe), Map.of(3, another)).get(0).last_sell_price);
        assertNull(builder.buildItems(Map.of(3, axe), Map.of(4, offer)).get(0).last_sell_price);
    }

    /** Under max cash the saved position is the price, whatever the slot holds now. */
    @Test
    public void aPriceUnderMaxCashComesFromTheSavedPosition() {
        OfferSnapshot offer = new OfferSnapshot(0, 11286, 5_250_000L, 1, 0, 0L, "SELLING", false);

        List<FlipHubItem> items = new OfferStampFallbackBuilder()
            .buildItems(Map.of(0, stamp(11286, 5_100_000, false)), Map.of(0, offer));

        assertEquals(Long.valueOf(5_100_000L), items.get(0).last_sell_price);
    }

    private static Stamp stamp(int itemId, int price, boolean isBuy) {
        Stamp stamp = new Stamp();
        stamp.itemId = itemId;
        stamp.price = price;
        stamp.isBuy = isBuy;
        return stamp;
    }
}
