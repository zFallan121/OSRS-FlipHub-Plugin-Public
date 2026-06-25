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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.GrandExchangeOffer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LocalItemsAssemblerFactoryServiceTest {
    @Test
    public void createBuildsAssemblerThatDelegatesToRuntimeHooks() {
        LocalItemsAssemblerFactoryService factory = new LocalItemsAssemblerFactoryService();
        TestRuntimeHooks hooks = new TestRuntimeHooks();
        hooks.names.put(4151, "Abyssal whip");

        LocalItemsAssembler assembler = factory.create(hooks);
        Map<Integer, LocalTradeInfo> tradeInfo = new HashMap<>();
        tradeInfo.put(4151, new LocalTradeInfo(4151));

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
        assertEquals("Abyssal whip", result.items.get(0).item_name);
        assertTrue(hooks.guidePriceCalls > 0);
        assertTrue(hooks.tradeInfoCalls > 0);
    }

    @Test
    public void createWithNullRuntimeHooksReturnsAssemblerWithoutHooks() {
        LocalItemsAssemblerFactoryService factory = new LocalItemsAssemblerFactoryService();
        LocalItemsAssembler assembler = factory.create(null);
        Map<Integer, LocalTradeInfo> tradeInfo = new HashMap<>();
        tradeInfo.put(4151, new LocalTradeInfo(4151));

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
        assertNull(result.items.get(0).item_name);
    }

    private static final class TestRuntimeHooks implements LocalItemsAssemblerFactoryService.RuntimeHooks {
        private final Map<Integer, String> names = new HashMap<>();
        private int guidePriceCalls;
        private int tradeInfoCalls;

        @Override
        public String getCachedItemName(int itemId) {
            return names.get(itemId);
        }

        @Override
        public void cacheItemName(int itemId) {
        }

        @Override
        public void applyGuidePrices(FlipHubItem item, int itemId) {
            guidePriceCalls++;
            item.instabuy_price = 100;
        }

        @Override
        public void applyLocalTradeInfo(FlipHubItem item, LocalTradeInfo info) {
            tradeInfoCalls++;
        }

        @Override
        public void applyLocalLimitInfo(FlipHubItem item, int itemId, LocalLimitInfo info) {
        }

        @Override
        public void applyMarginInfo(FlipHubItem item) {
        }
    }
}
