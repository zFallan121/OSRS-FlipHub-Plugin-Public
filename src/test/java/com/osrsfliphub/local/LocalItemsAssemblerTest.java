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

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LocalItemsAssemblerTest {
    @Test
    public void assembleIncludesTradeOnlyItemsWhenNoOffers() {
        TestHooks hooks = new TestHooks();
        hooks.cachedNames.put(4151, "Abyssal whip");
        LocalItemsAssembler assembler = new LocalItemsAssembler(hooks);
        Map<Integer, LocalTradeInfo> tradeInfo = new HashMap<>();
        tradeInfo.put(4151, tradeInfo(4151, 2_500_000, 2_650_000));

        LocalItemsAssembler.Result result = assembler.assemble(
            new GrandExchangeOffer[0],
            tradeInfo,
            Collections.emptyMap(),
            null,
            false,
            Collections.emptySet()
        );

        assertEquals(1, result.items.size());
        assertEquals(4151, result.items.get(0).item_id);
        assertTrue(result.itemsNeedingLimits.contains(4151));
    }

    @Test
    public void assembleFiltersByQuery() {
        TestHooks hooks = new TestHooks();
        hooks.cachedNames.put(11840, "Dragon boots");
        LocalItemsAssembler assembler = new LocalItemsAssembler(hooks);
        Map<Integer, LocalTradeInfo> tradeInfo = new HashMap<>();
        tradeInfo.put(11840, tradeInfo(11840, 200_000, 210_000));

        LocalItemsAssembler.Result result = assembler.assemble(
            new GrandExchangeOffer[0],
            tradeInfo,
            Collections.emptyMap(),
            "rune",
            false,
            Collections.emptySet()
        );

        assertEquals(0, result.items.size());
        assertEquals(0, result.itemsNeedingLimits.size());
    }

    @Test
    public void assembleFiltersByBookmarksWhenEnabled() {
        TestHooks hooks = new TestHooks();
        hooks.cachedNames.put(379, "Lobster");
        LocalItemsAssembler assembler = new LocalItemsAssembler(hooks);
        Map<Integer, LocalTradeInfo> tradeInfo = new HashMap<>();
        tradeInfo.put(379, tradeInfo(379, 200, 220));

        Set<Integer> bookmarked = new HashSet<>();
        bookmarked.add(4151);

        LocalItemsAssembler.Result result = assembler.assemble(
            new GrandExchangeOffer[0],
            tradeInfo,
            Collections.emptyMap(),
            null,
            true,
            bookmarked
        );

        assertEquals(0, result.items.size());
    }

    @Test
    public void assembleDoesNotTreatOfferConfiguredSellPriceAsLastCompletedSell() {
        TestHooks hooks = new TestHooks();
        hooks.cachedNames.put(4151, "Abyssal whip");
        LocalItemsAssembler assembler = new LocalItemsAssembler(hooks);

        LocalItemsAssembler.Result result = assembler.assemble(
            new GrandExchangeOffer[] {
                offer(4151, 2_750_000, GrandExchangeOfferState.SELLING)
            },
            Collections.emptyMap(),
            Collections.emptyMap(),
            null,
            false,
            Collections.emptySet()
        );

        assertEquals(1, result.items.size());
        FlipHubItem item = result.items.get(0);
        assertNull(item.last_sell_price);
        assertNull(item.last_sell_ts_ms);
    }

    @Test
    public void assembleUsesRecordedTradeInfoForLastCompletedSell() {
        TestHooks hooks = new TestHooks();
        hooks.cachedNames.put(4151, "Abyssal whip");
        LocalItemsAssembler assembler = new LocalItemsAssembler(hooks);
        Map<Integer, LocalTradeInfo> tradeInfo = new HashMap<>();
        tradeInfo.put(4151, tradeInfo(4151, 2_500_000, 2_650_000));

        LocalItemsAssembler.Result result = assembler.assemble(
            new GrandExchangeOffer[] {
                offer(4151, 2_750_000, GrandExchangeOfferState.SELLING)
            },
            tradeInfo,
            Collections.emptyMap(),
            null,
            false,
            Collections.emptySet()
        );

        assertEquals(1, result.items.size());
        FlipHubItem item = result.items.get(0);
        assertEquals(Integer.valueOf(2_650_000), item.last_sell_price);
    }

    private static LocalTradeInfo tradeInfo(int itemId, int lastBuy, int lastSell) {
        LocalTradeInfo info = new LocalTradeInfo(itemId);
        info.lastBuyPrice = lastBuy;
        info.lastSellPrice = lastSell;
        return info;
    }

    private static GrandExchangeOffer offer(int itemId, int price, GrandExchangeOfferState state) {
        return (GrandExchangeOffer) Proxy.newProxyInstance(
            GrandExchangeOffer.class.getClassLoader(),
            new Class<?>[] {GrandExchangeOffer.class},
            (proxy, method, args) -> handleOfferMethod(method, itemId, price, state)
        );
    }

    private static Object handleOfferMethod(Method method,
                                            int itemId,
                                            int price,
                                            GrandExchangeOfferState state) {
        switch (method.getName()) {
            case "getItemId":
                return itemId;
            case "getPrice":
                return price;
            case "getState":
                return state;
            default:
                return defaultValue(method);
        }
    }

    private static Object defaultValue(Method method) {
        Class<?> returnType = method.getReturnType();
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return 0;
    }

    private static final class TestHooks implements LocalItemsAssembler.Hooks {
        private final Map<Integer, String> cachedNames = new HashMap<>();

        @Override
        public String getCachedItemName(int itemId) {
            return cachedNames.get(itemId);
        }

        @Override
        public void cacheItemName(int itemId) {
        }

        @Override
        public void applyGuidePrices(FlipHubItem item, int itemId) {
            item.instabuy_price = 100;
            item.instasell_price = 90;
        }

        @Override
        public void applyLocalTradeInfo(FlipHubItem item, LocalTradeInfo info) {
            if (item == null || info == null) {
                return;
            }
            item.last_buy_price = info.lastBuyPrice;
            item.last_sell_price = info.lastSellPrice;
        }

        @Override
        public void applyLocalLimitInfo(FlipHubItem item, int itemId, LocalLimitInfo info) {
        }

        @Override
        public void applyMarginInfo(FlipHubItem item) {
        }
    }
}
