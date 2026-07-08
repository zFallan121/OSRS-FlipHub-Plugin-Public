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
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OfferStampFallbackBuilderTest {
    @Test
    public void buildItemsSkipsInvalidStamps() {
        OfferStampFallbackBuilder builder = new OfferStampFallbackBuilder();
        OfferUpdateStamp invalid = new OfferUpdateStamp();
        invalid.itemId = 0;

        List<FlipHubItem> items = builder.buildItems(Collections.singletonList(invalid));

        assertTrue(items.isEmpty());
    }

    @Test
    public void buildItemsBuildsSellItem() {
        OfferStampFallbackBuilder builder = new OfferStampFallbackBuilder();
        OfferUpdateStamp sell = stamp(11286, 5_100_000, false);

        List<FlipHubItem> items = builder.buildItems(Arrays.asList(sell));

        assertEquals(1, items.size());
        FlipHubItem item = items.get(0);
        assertEquals(11286, item.item_id);
        assertEquals(Integer.valueOf(5_100_000), item.last_sell_price);
        assertNull(item.last_buy_price);
    }

    private static OfferUpdateStamp stamp(int itemId, int price, boolean isBuy) {
        OfferUpdateStamp stamp = new OfferUpdateStamp();
        stamp.itemId = itemId;
        stamp.price = price;
        stamp.isBuy = isBuy;
        return stamp;
    }
}
