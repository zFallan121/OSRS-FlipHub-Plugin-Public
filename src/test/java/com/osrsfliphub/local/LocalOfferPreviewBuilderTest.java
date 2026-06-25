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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LocalOfferPreviewBuilderTest {
    @Test
    public void buildUsesFallbackLimitWhenLocalLimitNotApplied() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 10_000_000L;
        hooks.selectedProfileKey = 123L;
        hooks.limitAccountKey = 123L;
        hooks.itemName = "Rune knife";
        hooks.lookupGeLimit = 13_000;
        hooks.applyLocalLimitInHook = false;

        LocalTradeInfo tradeInfo = new LocalTradeInfo(560);
        tradeInfo.lastBuyPrice = 210;
        tradeInfo.lastBuyTs = 1_700_000_000_000L;
        tradeInfo.lastSellPrice = 215;
        tradeInfo.lastSellTs = 1_700_000_010_000L;
        hooks.tradeInfoByAccount.put(123L, singletonTradeInfo(560, tradeInfo));

        LocalLimitInfo limitInfo = new LocalLimitInfo(560);
        limitInfo.buyQty = 1_200;
        limitInfo.firstBuyTs = hooks.nowMs - (60L * 60L * 1000L);
        hooks.limitInfoByAccount.put(123L, singletonLimitInfo(560, limitInfo));

        LocalOfferPreviewBuilder builder = new LocalOfferPreviewBuilder(hooks);

        FlipHubItem item = builder.build(560);

        assertNotNull(item);
        assertEquals(560, item.item_id);
        assertEquals("Rune knife", item.item_name);
        assertEquals(Integer.valueOf(210), item.last_buy_price);
        assertEquals(Integer.valueOf(215), item.last_sell_price);
        assertEquals(Integer.valueOf(13_000), item.ge_limit_total);
        assertEquals(Integer.valueOf(11_800), item.ge_limit_remaining);
        assertEquals(Long.valueOf(10_800_000L), item.ge_limit_reset_ms);
        assertEquals(1, hooks.ensureSelectedProfileLoadedCalls);
        assertEquals(1, hooks.requestedGeLimitItemIds.size());
        assertEquals(Integer.valueOf(560), hooks.requestedGeLimitItemIds.get(0));
        assertEquals(1, hooks.ensureProfileLoadedCalls.size());
        assertEquals(Long.valueOf(123L), hooks.ensureProfileLoadedCalls.get(0));
        assertTrue(hooks.marginApplied);
    }

    @Test
    public void buildReturnsNullForInvalidItemId() {
        TestHooks hooks = new TestHooks();
        LocalOfferPreviewBuilder builder = new LocalOfferPreviewBuilder(hooks);

        FlipHubItem item = builder.build(0);

        assertNull(item);
        assertEquals(0, hooks.ensureSelectedProfileLoadedCalls);
        assertTrue(hooks.requestedGeLimitItemIds.isEmpty());
    }

    private static Map<Integer, LocalTradeInfo> singletonTradeInfo(int itemId, LocalTradeInfo info) {
        Map<Integer, LocalTradeInfo> map = new HashMap<>();
        map.put(itemId, info);
        return map;
    }

    private static Map<Integer, LocalLimitInfo> singletonLimitInfo(int itemId, LocalLimitInfo info) {
        Map<Integer, LocalLimitInfo> map = new HashMap<>();
        map.put(itemId, info);
        return map;
    }

    private static final class TestHooks implements LocalOfferPreviewBuilder.Hooks {
        private long nowMs = System.currentTimeMillis();
        private long selectedProfileKey = -1L;
        private long limitAccountKey = -1L;
        private String itemName;
        private Integer lookupGeLimit;
        private boolean applyLocalLimitInHook;
        private boolean marginApplied;
        private int ensureSelectedProfileLoadedCalls;
        private final List<Integer> requestedGeLimitItemIds = new ArrayList<>();
        private final List<Long> ensureProfileLoadedCalls = new ArrayList<>();
        private final Map<Long, Map<Integer, LocalTradeInfo>> tradeInfoByAccount = new HashMap<>();
        private final Map<Long, Map<Integer, LocalLimitInfo>> limitInfoByAccount = new HashMap<>();

        @Override
        public void ensureSelectedProfileLoaded() {
            ensureSelectedProfileLoadedCalls++;
        }

        @Override
        public void requestGeLimit(int itemId) {
            requestedGeLimitItemIds.add(itemId);
        }

        @Override
        public String getItemName(int itemId) {
            return itemName;
        }

        @Override
        public void applyGuidePrices(FlipHubItem item, int itemId) {
            if (item == null) {
                return;
            }
            item.instabuy_price = 210;
            item.instasell_price = 215;
        }

        @Override
        public long resolveSelectedProfileKey() {
            return selectedProfileKey;
        }

        @Override
        public long resolveLimitAccountKey(long fallbackAccountKey) {
            return limitAccountKey;
        }

        @Override
        public Map<Integer, LocalTradeInfo> buildLocalTradeInfo(long accountKey) {
            return tradeInfoByAccount.getOrDefault(accountKey, new HashMap<>());
        }

        @Override
        public void applyLocalTradeInfo(FlipHubItem item, LocalTradeInfo info) {
            if (item == null || info == null) {
                return;
            }
            if (info.lastBuyPrice != null && info.lastBuyPrice > 0) {
                item.last_buy_price = info.lastBuyPrice;
                item.last_buy_ts_ms = info.lastBuyTs;
            }
            if (info.lastSellPrice != null && info.lastSellPrice > 0) {
                item.last_sell_price = info.lastSellPrice;
                item.last_sell_ts_ms = info.lastSellTs;
            }
        }

        @Override
        public void ensureProfileLoaded(long accountKey) {
            ensureProfileLoadedCalls.add(accountKey);
        }

        @Override
        public Map<Integer, LocalLimitInfo> buildLocalLimitInfo(long accountKey, long nowMs) {
            return limitInfoByAccount.getOrDefault(accountKey, new HashMap<>());
        }

        @Override
        public void applyLocalLimitInfo(FlipHubItem item, int itemId, LocalLimitInfo info) {
            if (!applyLocalLimitInHook || item == null || info == null) {
                return;
            }
            item.ge_limit_total = 13_000;
            item.ge_limit_remaining = (int) Math.max(0L, 13_000L - info.buyQty);
            item.ge_limit_reset_ms = 123L;
        }

        @Override
        public Integer lookupGeLimit(int itemId) {
            return lookupGeLimit;
        }

        @Override
        public void applyMarginInfo(FlipHubItem item) {
            marginApplied = true;
        }

        @Override
        public long nowMs() {
            return nowMs;
        }
    }
}
