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
import static org.junit.Assert.assertNull;

public class ItemLookupServiceTest {
    @Test
    public void resolveItemIdFromNameUsesCacheThenHook() {
        Map<String, Integer> nameToId = new HashMap<>();
        Map<Integer, String> idToName = new HashMap<>();
        TestHooks hooks = new TestHooks();
        hooks.exactNameId = 4151;
        ItemLookupService service = new ItemLookupService(nameToId, idToName, hooks);

        int resolved = service.resolveItemIdFromName("Abyssal whip");

        assertEquals(4151, resolved);
        assertEquals(Integer.valueOf(4151), nameToId.get("abyssal whip"));
        assertEquals(1, hooks.findByNameCalls);

        int cached = service.resolveItemIdFromName("Abyssal whip");
        assertEquals(4151, cached);
        assertEquals(1, hooks.findByNameCalls);
    }

    @Test
    public void lookupSafeMethodsFilterInvalidOrMissingValues() {
        Map<String, Integer> nameToId = new HashMap<>();
        Map<Integer, String> idToName = new HashMap<>();
        TestHooks hooks = new TestHooks();
        hooks.itemName = "Abyssal whip";
        hooks.geLimit = 70;
        hooks.guidePrice = 2_000_000;
        ItemLookupService service = new ItemLookupService(nameToId, idToName, hooks);

        assertEquals("Abyssal whip", service.lookupItemNameSafe(4151));
        assertEquals(Integer.valueOf(70), service.lookupGeLimitSafe(4151));
        assertEquals(Integer.valueOf(2_000_000), service.lookupGuidePriceSafe(4151));

        hooks.geLimit = 0;
        hooks.guidePrice = 0;
        assertNull(service.lookupGeLimitSafe(4151));
        assertNull(service.lookupGuidePriceSafe(4151));
        assertNull(service.lookupItemNameSafe(0));
    }

    @Test
    public void cacheItemNameStoresAndTriggersSingleRefresh() {
        Map<String, Integer> nameToId = new HashMap<>();
        Map<Integer, String> idToName = new HashMap<>();
        TestHooks hooks = new TestHooks();
        hooks.itemName = "Abyssal whip";
        hooks.canCacheAsync = true;
        ItemLookupService service = new ItemLookupService(nameToId, idToName, hooks);

        service.cacheItemName(4151);
        service.cacheItemName(4151);

        assertEquals("Abyssal whip", idToName.get(4151));
        assertEquals("Abyssal whip", service.getCachedItemName(4151));
        assertEquals(1, hooks.cacheUpdatedCalls);
        assertEquals(1, hooks.invokeCalls);
    }

    @Test
    public void cacheItemNameSkipsWhenAsyncUnavailable() {
        Map<String, Integer> nameToId = new HashMap<>();
        Map<Integer, String> idToName = new HashMap<>();
        TestHooks hooks = new TestHooks();
        hooks.itemName = "Abyssal whip";
        hooks.canCacheAsync = false;
        ItemLookupService service = new ItemLookupService(nameToId, idToName, hooks);

        service.cacheItemName(4151);

        assertNull(idToName.get(4151));
        assertEquals(0, hooks.cacheUpdatedCalls);
        assertEquals(0, hooks.invokeCalls);
    }

    private static final class TestHooks implements ItemLookupService.Hooks {
        private Integer exactNameId;
        private String itemName;
        private Integer geLimit;
        private Integer guidePrice;
        private boolean canCacheAsync;
        private int findByNameCalls;
        private int invokeCalls;
        private int cacheUpdatedCalls;

        @Override
        public Integer findItemIdByExactName(String name) {
            findByNameCalls++;
            return exactNameId;
        }

        @Override
        public String lookupItemName(int itemId) {
            return itemName;
        }

        @Override
        public Integer lookupGeLimit(int itemId) {
            return geLimit;
        }

        @Override
        public Integer lookupGuidePrice(int itemId) {
            return guidePrice;
        }

        @Override
        public boolean canCacheItemNamesAsync() {
            return canCacheAsync;
        }

        @Override
        public void invokeOnClientThread(Runnable task) {
            invokeCalls++;
            if (task != null) {
                task.run();
            }
        }

        @Override
        public void onItemNameCacheUpdated() {
            cacheUpdatedCalls++;
        }
    }
}
