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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RemainingLimitSuggestionFactoryServiceTest {
    @Test
    public void createBuildsServiceThatDelegatesToRuntimeHooks() {
        RemainingLimitSuggestionFactoryService factory = new RemainingLimitSuggestionFactoryService();
        TestRuntimeHooks hooks = new TestRuntimeHooks();
        hooks.localAccountKey = 42L;
        hooks.nowMs = 1_000L;
        hooks.cachedGeLimit = 100;
        hooks.limitInfoByItem.put(4151, limitInfo(4151, 20L, 500L));
        hooks.offerPreviewItem = new FlipHubItem();
        hooks.offerPreviewItem.item_id = 4151;

        RemainingLimitSuggestionService service = factory.create(hooks);
        Integer remaining = service.computeSuggestion(4151);

        assertEquals(Integer.valueOf(80), remaining);
        assertEquals(1, hooks.resolveLocalAccountKeyCalls);
        assertEquals(1, hooks.ensureProfileLoadedCalls);
        assertEquals(1, hooks.requestGeLimitsCalls);
        assertTrue(hooks.lastRequestedItemIds.contains(4151));
        assertEquals(1, hooks.getCachedGeLimitCalls);
        assertEquals(0, hooks.lookupGeLimitSafeCalls);
        assertEquals(1, hooks.buildLocalLimitInfoCalls);
        assertEquals(1, hooks.getOfferPreviewItemCalls);
        assertEquals(2, hooks.nowMsCalls);
        assertEquals(Integer.valueOf(100), hooks.offerPreviewItem.ge_limit_total);
        assertEquals(Integer.valueOf(80), hooks.offerPreviewItem.ge_limit_remaining);
    }

    @Test
    public void createWithNullRuntimeHooksReturnsNoopService() {
        RemainingLimitSuggestionFactoryService factory = new RemainingLimitSuggestionFactoryService();
        RemainingLimitSuggestionService service = factory.create(null);

        assertNull(service.computeSuggestion(4151));
        assertNull(service.getThrottledSuggestion(4151));
        service.cacheSuggestion(4151, 10);
    }

    private static LocalLimitInfo limitInfo(int itemId, long buyQty, Long firstBuyTs) {
        LocalLimitInfo info = new LocalLimitInfo(itemId);
        info.buyQty = buyQty;
        info.firstBuyTs = firstBuyTs;
        return info;
    }

    private static final class TestRuntimeHooks implements RemainingLimitSuggestionFactoryService.RuntimeHooks {
        private long localAccountKey = 1L;
        private long selectedProfileKey = -1L;
        private long nowMs;
        private Integer cachedGeLimit;
        private Integer fallbackGeLimit;
        private final Map<Integer, LocalLimitInfo> limitInfoByItem = new HashMap<>();
        private FlipHubItem offerPreviewItem;
        private int resolveLocalAccountKeyCalls;
        private int ensureProfileLoadedCalls;
        private int requestGeLimitsCalls;
        private Set<Integer> lastRequestedItemIds = Collections.emptySet();
        private int getCachedGeLimitCalls;
        private int lookupGeLimitSafeCalls;
        private int buildLocalLimitInfoCalls;
        private int getOfferPreviewItemCalls;
        private int nowMsCalls;

        @Override
        public long resolveLocalAccountKey() {
            resolveLocalAccountKeyCalls++;
            return localAccountKey;
        }

        @Override
        public long resolveSelectedProfileKey() {
            return selectedProfileKey;
        }

        @Override
        public void ensureProfileLoaded(long accountKey) {
            ensureProfileLoadedCalls++;
        }

        @Override
        public void requestGeLimits(Set<Integer> itemIds) {
            requestGeLimitsCalls++;
            lastRequestedItemIds = itemIds != null ? new HashSet<>(itemIds) : Collections.emptySet();
        }

        @Override
        public Integer getCachedGeLimit(int itemId) {
            getCachedGeLimitCalls++;
            return cachedGeLimit;
        }

        @Override
        public Integer lookupGeLimitSafe(int itemId) {
            lookupGeLimitSafeCalls++;
            return fallbackGeLimit;
        }

        @Override
        public Map<Integer, LocalLimitInfo> buildLocalLimitInfo(long accountKey, long nowMs) {
            buildLocalLimitInfoCalls++;
            return limitInfoByItem;
        }

        @Override
        public FlipHubItem getOfferPreviewItem() {
            getOfferPreviewItemCalls++;
            return offerPreviewItem;
        }

        @Override
        public long nowMs() {
            nowMsCalls++;
            return nowMs;
        }
    }
}
