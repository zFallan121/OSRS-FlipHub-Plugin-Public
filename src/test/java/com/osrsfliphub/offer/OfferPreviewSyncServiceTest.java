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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class OfferPreviewSyncServiceTest {
    @Test
    public void setPreviewItemUpdatesStateAndNotifiesWhenChanged() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 12345L;
        hooks.nextBuiltItem = item(4151, 100, 120);
        OfferPreviewSyncService service = new OfferPreviewSyncService(hooks);

        boolean updated = service.setPreviewItem(4151);

        assertTrue(updated);
        assertEquals(Integer.valueOf(4151), hooks.offerPreviewItemId);
        assertSame(hooks.nextBuiltItem, hooks.offerPreviewItem);
        assertEquals(1, hooks.markSuggestionDirtyCalls);
        assertEquals(1, hooks.panelUpdateCalls);
        assertSame(hooks.nextBuiltItem, hooks.lastPanelItem);
        assertEquals(12345L, hooks.lastPanelAsOfMs);
        assertNull(hooks.lastPanelPriceCacheMs);
        assertEquals(1, hooks.scheduleRefreshSoonCalls);
    }

    @Test
    public void setPreviewItemNoopsWhenEquivalent() {
        TestHooks hooks = new TestHooks();
        hooks.offerPreviewItemId = 4151;
        hooks.offerPreviewItem = item(4151, 100, 120);
        hooks.nextBuiltItem = item(4151, 100, 120);
        OfferPreviewSyncService service = new OfferPreviewSyncService(hooks);

        boolean updated = service.setPreviewItem(4151);

        assertTrue(updated);
        assertEquals(0, hooks.markSuggestionDirtyCalls);
        assertEquals(0, hooks.panelUpdateCalls);
        assertEquals(0, hooks.scheduleRefreshSoonCalls);
    }

    @Test
    public void clearPreviewClearsPanelAndSchedulesRefresh() {
        TestHooks hooks = new TestHooks();
        hooks.offerPreviewItemId = 4151;
        hooks.offerPreviewItem = item(4151, 100, 120);
        OfferPreviewSyncService service = new OfferPreviewSyncService(hooks);

        service.clearPreview();

        assertNull(hooks.offerPreviewItemId);
        assertNull(hooks.offerPreviewItem);
        assertEquals(1, hooks.panelUpdateCalls);
        assertNull(hooks.lastPanelItem);
        assertEquals(1, hooks.scheduleRefreshSoonCalls);
    }

    @Test
    public void clearPreviewNoopsWhenNotActive() {
        TestHooks hooks = new TestHooks();
        OfferPreviewSyncService service = new OfferPreviewSyncService(hooks);

        service.clearPreview();

        assertEquals(0, hooks.panelUpdateCalls);
        assertEquals(0, hooks.scheduleRefreshSoonCalls);
    }

    @Test
    public void setPreviewItemReturnsFalseForInvalidItemId() {
        TestHooks hooks = new TestHooks();
        OfferPreviewSyncService service = new OfferPreviewSyncService(hooks);

        assertFalse(service.setPreviewItem(0));
        assertFalse(service.setPreviewItem(-1));
        assertEquals(0, hooks.panelUpdateCalls);
        assertEquals(0, hooks.markSuggestionDirtyCalls);
        assertEquals(0, hooks.scheduleRefreshSoonCalls);
    }

    private static FlipHubItem item(int itemId, Integer instabuy, Integer instasell) {
        FlipHubItem item = new FlipHubItem();
        item.item_id = itemId;
        item.instabuy_price = instabuy;
        item.instasell_price = instasell;
        item.instabuy_ts_ms = 1000L;
        item.instasell_ts_ms = 2000L;
        item.last_buy_price = 95;
        item.last_sell_price = 118;
        item.last_buy_ts_ms = 800L;
        item.last_sell_ts_ms = 1900L;
        item.margin = 18;
        item.margin_x_limit = 1800L;
        item.roi_percent = 15.0;
        item.ge_limit_total = 100;
        item.ge_limit_remaining = 40;
        item.ge_limit_reset_ms = 3600_000L;
        return item;
    }

    private static final class TestHooks implements OfferPreviewSyncService.Hooks {
        private Integer offerPreviewItemId;
        private FlipHubItem offerPreviewItem;
        private FlipHubItem nextBuiltItem;
        private long nowMs;
        private int panelUpdateCalls;
        private FlipHubItem lastPanelItem;
        private long lastPanelAsOfMs;
        private Long lastPanelPriceCacheMs;
        private int markSuggestionDirtyCalls;
        private int scheduleRefreshSoonCalls;

        @Override
        public Integer getOfferPreviewItemId() {
            return offerPreviewItemId;
        }

        @Override
        public FlipHubItem getOfferPreviewItem() {
            return offerPreviewItem;
        }

        @Override
        public void setOfferPreviewItemId(Integer itemId) {
            offerPreviewItemId = itemId;
        }

        @Override
        public void setOfferPreviewItem(FlipHubItem item) {
            offerPreviewItem = item;
        }

        @Override
        public FlipHubItem buildLocalOfferPreview(int itemId) {
            return nextBuiltItem;
        }

        @Override
        public void setPanelOfferPreview(FlipHubItem item, long asOfMs, Long priceCacheMs) {
            panelUpdateCalls++;
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
            return nowMs;
        }
    }
}
