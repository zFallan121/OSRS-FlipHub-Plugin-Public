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
import static org.junit.Assert.assertTrue;

public class LocalItemEnrichmentFactoryServiceTest {
    @Test
    public void createBuildsServiceThatDelegatesToRuntimeHooks() {
        LocalItemEnrichmentFactoryService factory = new LocalItemEnrichmentFactoryService(4_000L);
        TestRuntimeHooks hooks = new TestRuntimeHooks();
        hooks.cachedGeLimit = 100;
        hooks.nowMs = 5_000L;
        hooks.wikiEntry = wikiEntry(150, 100, 10L, 12L);

        LocalItemEnrichmentService service = factory.create(hooks);
        FlipHubItem item = new FlipHubItem();
        item.item_id = 4151;

        service.applyGuidePrices(item, 4151, true);
        service.applyLocalLimitInfo(item, 4151, limitInfo(4151, 20L, 2_000L));

        assertEquals(1, hooks.getWikiPriceEntryCalls);
        assertEquals(Integer.valueOf(100), item.instabuy_price);
        assertEquals(Integer.valueOf(150), item.instasell_price);
        assertEquals(1, hooks.getCachedGeLimitCalls);
        assertEquals(1, hooks.nowMsCalls);
        assertEquals(Integer.valueOf(100), item.ge_limit_total);
        assertEquals(Integer.valueOf(80), item.ge_limit_remaining);
        assertEquals(Long.valueOf(1_000L), item.ge_limit_reset_ms);
    }

    @Test
    public void createWithNullRuntimeHooksReturnsNoopService() {
        LocalItemEnrichmentFactoryService factory = new LocalItemEnrichmentFactoryService(4_000L);
        LocalItemEnrichmentService service = factory.create(null);
        FlipHubItem item = new FlipHubItem();
        item.item_id = 4151;

        service.applyGuidePrices(item, 4151, true);
        service.applyLocalLimitInfo(item, 4151, limitInfo(4151, 20L, 2_000L));

        assertNull(item.instabuy_price);
        assertNull(item.instasell_price);
        assertNull(item.ge_limit_total);
    }

    private static WikiPriceEntry wikiEntry(Integer high, Integer low, Long highTime, Long lowTime) {
        WikiPriceEntry entry = new WikiPriceEntry();
        entry.high = high;
        entry.low = low;
        entry.highTime = highTime;
        entry.lowTime = lowTime;
        return entry;
    }

    private static LocalLimitInfo limitInfo(int itemId, long buyQty, Long firstBuyTs) {
        LocalLimitInfo info = new LocalLimitInfo(itemId);
        info.buyQty = buyQty;
        info.firstBuyTs = firstBuyTs;
        return info;
    }

    private static final class TestRuntimeHooks implements LocalItemEnrichmentFactoryService.RuntimeHooks {
        private Integer cachedGeLimit;
        private WikiPriceEntry wikiEntry;
        private long nowMs;
        private int getCachedGeLimitCalls;
        private int getWikiPriceEntryCalls;
        private int nowMsCalls;

        @Override
        public Integer getCachedGeLimit(int itemId) {
            getCachedGeLimitCalls++;
            return cachedGeLimit;
        }

        @Override
        public WikiPriceEntry getWikiPriceEntry(int itemId, boolean allowRefresh) {
            getWikiPriceEntryCalls++;
            assertTrue(allowRefresh);
            return wikiEntry;
        }

        @Override
        public long nowMs() {
            nowMsCalls++;
            return nowMs;
        }
    }
}
