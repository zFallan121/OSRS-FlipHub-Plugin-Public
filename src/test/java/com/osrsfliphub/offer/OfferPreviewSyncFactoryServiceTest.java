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

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class OfferPreviewSyncFactoryServiceTest {
    @Test
    public void createBuildsServiceThatDelegatesToRuntimeHooks() {
        OfferPreviewSyncFactoryService factory = new OfferPreviewSyncFactoryService();
        TestRuntimeHooks hooks = new TestRuntimeHooks();
        hooks.nowMs = 99_001L;
        hooks.nextBuiltItem = item(4151, 100, 120);

        OfferPreviewSyncService service = factory.create(hooks);
        boolean updated = service.setPreviewItem(4151);

        assertTrue(updated);
        assertEquals(1, hooks.getOfferPreviewItemIdCalls);
        assertEquals(2, hooks.getOfferPreviewItemCalls);
        assertEquals(1, hooks.setOfferPreviewItemIdCalls.size());
        assertEquals(Integer.valueOf(4151), hooks.setOfferPreviewItemIdCalls.get(0));
        assertEquals(1, hooks.setOfferPreviewItemCalls.size());
        assertSame(hooks.nextBuiltItem, hooks.setOfferPreviewItemCalls.get(0));
        assertEquals(1, hooks.buildLocalOfferPreviewCalls.size());
        assertEquals(Integer.valueOf(4151), hooks.buildLocalOfferPreviewCalls.get(0));
        assertEquals(1, hooks.setPanelOfferPreviewCalls);
        assertSame(hooks.nextBuiltItem, hooks.lastPanelItem);
        assertEquals(99_001L, hooks.lastPanelAsOfMs);
        assertNull(hooks.lastPanelPriceCacheMs);
        assertEquals(1, hooks.markSuggestionDirtyCalls);
        assertEquals(1, hooks.scheduleRefreshSoonCalls);
        assertEquals(1, hooks.nowMsCalls);
    }

    @Test
    public void createWithNullRuntimeHooksReturnsNoopService() {
        OfferPreviewSyncFactoryService factory = new OfferPreviewSyncFactoryService();
        OfferPreviewSyncService service = factory.create(null);

        assertFalse(service.setPreviewItem(4151));
        service.clearPreview();
    }

    private static FlipHubItem item(int itemId, Integer instabuy, Integer instasell) {
        FlipHubItem item = new FlipHubItem();
        item.item_id = itemId;
        item.instabuy_price = instabuy;
        item.instasell_price = instasell;
        return item;
    }

    private static final class TestRuntimeHooks implements OfferPreviewSyncFactoryService.RuntimeHooks {
        private Integer offerPreviewItemId;
        private FlipHubItem offerPreviewItem;
        private FlipHubItem nextBuiltItem;
        private long nowMs;
        private int getOfferPreviewItemIdCalls;
        private int getOfferPreviewItemCalls;
        private final List<Integer> setOfferPreviewItemIdCalls = new ArrayList<>();
        private final List<FlipHubItem> setOfferPreviewItemCalls = new ArrayList<>();
        private final List<Integer> buildLocalOfferPreviewCalls = new ArrayList<>();
        private int setPanelOfferPreviewCalls;
        private FlipHubItem lastPanelItem;
        private long lastPanelAsOfMs;
        private Long lastPanelPriceCacheMs;
        private int markSuggestionDirtyCalls;
        private int scheduleRefreshSoonCalls;
        private int nowMsCalls;

        @Override
        public Integer getOfferPreviewItemId() {
            getOfferPreviewItemIdCalls++;
            return offerPreviewItemId;
        }

        @Override
        public FlipHubItem getOfferPreviewItem() {
            getOfferPreviewItemCalls++;
            return offerPreviewItem;
        }

        @Override
        public void setOfferPreviewItemId(Integer itemId) {
            setOfferPreviewItemIdCalls.add(itemId);
            offerPreviewItemId = itemId;
        }

        @Override
        public void setOfferPreviewItem(FlipHubItem item) {
            setOfferPreviewItemCalls.add(item);
            offerPreviewItem = item;
        }

        @Override
        public FlipHubItem buildLocalOfferPreview(int itemId) {
            buildLocalOfferPreviewCalls.add(itemId);
            return nextBuiltItem;
        }

        @Override
        public void setPanelOfferPreview(FlipHubItem item, long asOfMs, Long priceCacheMs) {
            setPanelOfferPreviewCalls++;
            lastPanelItem = item;
            lastPanelAsOfMs = asOfMs;
            lastPanelPriceCacheMs = priceCacheMs;
        }

        @Override
        public void markSuggestionDirty() {
            markSuggestionDirtyCalls++;
        }

        @Override
        public void scheduleRefreshSoon() {
            scheduleRefreshSoonCalls++;
        }

        @Override
        public long nowMs() {
            nowMsCalls++;
            return nowMs;
        }
    }
}
