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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OfferStampFallbackBuilderTest {
    @Test
    public void buildItemsSkipsInvalidStamps() {
        OfferStampFallbackBuilder builder = new OfferStampFallbackBuilder(new TestHooks());
        OfferUpdateStamp invalid = new OfferUpdateStamp();
        invalid.itemId = 0;

        List<FlipHubItem> items = builder.buildItems(Collections.singletonList(invalid));

        assertTrue(items.isEmpty());
    }

    @Test
    public void buildItemsBuildsBuyItemWithNameAndGuidePrices() {
        TestHooks hooks = new TestHooks();
        hooks.names.put(4151, "Abyssal whip");
        hooks.guidePrices.put(4151, 2_400_000);
        OfferStampFallbackBuilder builder = new OfferStampFallbackBuilder(hooks);
        OfferUpdateStamp buy = stamp(4151, 2_350_000, true);

        List<FlipHubItem> items = builder.buildItems(Collections.singletonList(buy));

        assertEquals(1, items.size());
        FlipHubItem item = items.get(0);
        assertEquals(4151, item.item_id);
        assertEquals("Abyssal whip", item.item_name);
        assertEquals(Integer.valueOf(2_400_000), item.instabuy_price);
        assertEquals(Integer.valueOf(2_400_000), item.instasell_price);
        assertEquals(Integer.valueOf(2_350_000), item.last_buy_price);
        assertNull(item.last_sell_price);
    }

    @Test
    public void buildItemsBuildsSellItem() {
        OfferStampFallbackBuilder builder = new OfferStampFallbackBuilder(new TestHooks());
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

    private static final class TestHooks implements OfferStampFallbackBuilder.Hooks {
        private final Map<Integer, String> names = new HashMap<>();
        private final Map<Integer, Integer> guidePrices = new HashMap<>();

        @Override
        public String getItemName(int itemId) {
            return names.get(itemId);
        }

        @Override
        public Integer getGuidePrice(int itemId) {
            return guidePrices.get(itemId);
        }
    }
}
