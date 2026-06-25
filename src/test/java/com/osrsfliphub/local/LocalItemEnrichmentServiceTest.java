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

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class LocalItemEnrichmentServiceTest {
    @Test
    public void applyGuidePricesCopiesPricesAndTimestamps() {
        TestHooks hooks = new TestHooks();
        WikiPriceEntry entry = new WikiPriceEntry();
        entry.high = 215;
        entry.low = 210;
        entry.highTime = 200L;
        entry.lowTime = 100L;
        hooks.wikiEntries.put(560, entry);

        LocalItemEnrichmentService service = new LocalItemEnrichmentService(hooks, 4_000L);
        FlipHubItem item = new FlipHubItem();
        service.applyGuidePrices(item, 560, true);

        assertEquals(Integer.valueOf(215), item.instasell_price);
        assertEquals(Integer.valueOf(210), item.instabuy_price);
        assertEquals(Long.valueOf(200_000L), item.instasell_ts_ms);
        assertEquals(Long.valueOf(100_000L), item.instabuy_ts_ms);
    }

    @Test
    public void applyLocalLimitInfoUsesCachedLimitAndWindow() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 1_500L;
        hooks.cachedLimits.put(4151, 13000);

        LocalLimitInfo info = new LocalLimitInfo(4151);
        info.buyQty = 1200;
        info.firstBuyTs = 1_000L;

        LocalItemEnrichmentService service = new LocalItemEnrichmentService(hooks, 4_000L);
        FlipHubItem item = new FlipHubItem();
        service.applyLocalLimitInfo(item, 4151, info);

        assertEquals(Integer.valueOf(13000), item.ge_limit_total);
        assertEquals(Integer.valueOf(11800), item.ge_limit_remaining);
        assertEquals(Long.valueOf(3_500L), item.ge_limit_reset_ms);
    }

    @Test
    public void applyMarginInfoUsesBestAvailablePricesAndLimit() {
        TestHooks hooks = new TestHooks();
        LocalItemEnrichmentService service = new LocalItemEnrichmentService(hooks, 4_000L);

        FlipHubItem item = new FlipHubItem();
        item.last_buy_price = 100;
        item.last_sell_price = 120;
        item.ge_limit_remaining = 50;

        service.applyMarginInfo(item);

        assertEquals(Integer.valueOf(17), item.margin);
        assertNotNull(item.roi_percent);
        assertEquals(18.0, item.roi_percent, 0.0001);
        assertEquals(Long.valueOf(880L), item.margin_x_limit);
    }

    @Test
    public void applyMarginInfoUsesIntegerTaxForSmallSpread() {
        TestHooks hooks = new TestHooks();
        LocalItemEnrichmentService service = new LocalItemEnrichmentService(hooks, 4_000L);

        FlipHubItem item = new FlipHubItem();
        item.instabuy_price = 210;
        item.instasell_price = 215;
        item.ge_limit_remaining = 11000;

        service.applyMarginInfo(item);

        assertEquals(Integer.valueOf(0), item.margin);
        assertEquals(Long.valueOf(7700L), item.margin_x_limit);
    }

    @Test
    public void applyMarginInfoUsesPreciseAfterTaxForMarginXLimit() {
        TestHooks hooks = new TestHooks();
        LocalItemEnrichmentService service = new LocalItemEnrichmentService(hooks, 4_000L);

        FlipHubItem item = new FlipHubItem();
        item.instabuy_price = 142;
        item.instasell_price = 147;
        item.ge_limit_remaining = 18000;

        service.applyMarginInfo(item);

        assertEquals(Integer.valueOf(2), item.margin);
        assertEquals(Long.valueOf(37080L), item.margin_x_limit);
        assertEquals(2.1127, item.roi_percent, 0.0001);
    }

    @Test
    public void applyMarginInfoRoiUsesPerItemTaxFloorLikeHistory() {
        TestHooks hooks = new TestHooks();
        LocalItemEnrichmentService service = new LocalItemEnrichmentService(hooks, 4_000L);

        FlipHubItem item = new FlipHubItem();
        item.instabuy_price = 254;
        item.instasell_price = 263;

        service.applyMarginInfo(item);

        assertEquals(Integer.valueOf(3), item.margin);
        assertEquals(1.5748, item.roi_percent, 0.0001);
    }

    @Test
    public void applyLocalTradeInfoCopiesLastBuyAndSell() {
        LocalItemEnrichmentService service = new LocalItemEnrichmentService(new TestHooks(), 4_000L);
        LocalTradeInfo info = new LocalTradeInfo(11212);
        info.lastBuyPrice = 6071;
        info.lastBuyTs = 100L;
        info.lastSellPrice = 6447;
        info.lastSellTs = 200L;

        FlipHubItem item = new FlipHubItem();
        service.applyLocalTradeInfo(item, info);

        assertEquals(Integer.valueOf(6071), item.last_buy_price);
        assertEquals(Long.valueOf(100L), item.last_buy_ts_ms);
        assertEquals(Integer.valueOf(6447), item.last_sell_price);
        assertEquals(Long.valueOf(200L), item.last_sell_ts_ms);
    }

    private static final class TestHooks implements LocalItemEnrichmentService.Hooks {
        private final Map<Integer, Integer> cachedLimits = new HashMap<>();
        private final Map<Integer, WikiPriceEntry> wikiEntries = new HashMap<>();
        private long nowMs;

        @Override
        public Integer getCachedGeLimit(int itemId) {
            return cachedLimits.get(itemId);
        }

        @Override
        public WikiPriceEntry getWikiPriceEntry(int itemId, boolean allowRefresh) {
            return wikiEntries.get(itemId);
        }

        @Override
        public long nowMs() {
            return nowMs;
        }
    }
}
