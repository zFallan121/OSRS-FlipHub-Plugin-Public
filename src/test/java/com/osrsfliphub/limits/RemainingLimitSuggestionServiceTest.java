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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RemainingLimitSuggestionServiceTest {
    @Test
    public void getThrottledSuggestionUsesCachedValueWithinRefreshWindow() {
        TestHooks hooks = new TestHooks();
        hooks.localAccountKey = 42L;
        hooks.nowMs = 1_000L;
        hooks.cachedGeLimit = 100;
        hooks.limitInfoByItem.put(4151, limitInfo(4151, 20L, null));
        RemainingLimitSuggestionService service = new RemainingLimitSuggestionService(hooks);

        assertEquals(Integer.valueOf(80), service.getThrottledSuggestion(4151));

        hooks.nowMs = 1_500L;
        hooks.cachedGeLimit = 200;
        hooks.limitInfoByItem.put(4151, limitInfo(4151, 0L, null));
        assertEquals(Integer.valueOf(80), service.getThrottledSuggestion(4151));
        assertEquals(1, hooks.requestGeLimitsCalls);
        assertEquals(1, hooks.buildLocalLimitInfoCalls);
    }

    @Test
    public void getThrottledSuggestionRecomputesAfterRefreshWindow() {
        TestHooks hooks = new TestHooks();
        hooks.localAccountKey = 42L;
        hooks.nowMs = 1_000L;
        hooks.cachedGeLimit = 100;
        hooks.limitInfoByItem.put(4151, limitInfo(4151, 20L, null));
        RemainingLimitSuggestionService service = new RemainingLimitSuggestionService(hooks);

        assertEquals(Integer.valueOf(80), service.getThrottledSuggestion(4151));

        hooks.nowMs = 2_200L;
        hooks.cachedGeLimit = 200;
        hooks.limitInfoByItem.put(4151, limitInfo(4151, 50L, null));
        assertEquals(Integer.valueOf(150), service.getThrottledSuggestion(4151));
        assertEquals(2, hooks.requestGeLimitsCalls);
        assertEquals(2, hooks.buildLocalLimitInfoCalls);
    }

    @Test
    public void cacheSuggestionIsPartitionedByAccountKey() {
        TestHooks hooks = new TestHooks();
        hooks.localAccountKey = 1L;
        hooks.nowMs = 100L;
        hooks.cachedGeLimit = 100;
        RemainingLimitSuggestionService service = new RemainingLimitSuggestionService(hooks);

        service.cacheSuggestion(4151, 33);

        hooks.nowMs = 200L;
        assertEquals(Integer.valueOf(33), service.getThrottledSuggestion(4151));
        assertEquals(0, hooks.requestGeLimitsCalls);

        hooks.localAccountKey = 2L;
        hooks.nowMs = 300L;
        assertEquals(Integer.valueOf(100), service.getThrottledSuggestion(4151));
        assertEquals(1, hooks.requestGeLimitsCalls);
    }

    @Test
    public void computeSuggestionFallsBackToSelectedProfileAndUpdatesPreviewFields() {
        TestHooks hooks = new TestHooks();
        hooks.localAccountKey = 0L;
        hooks.selectedProfileKey = 77L;
        hooks.nowMs = 2_000L;
        hooks.cachedGeLimit = 70;
        hooks.limitInfoByItem.put(4151, limitInfo(4151, 20L, 1_000L));
        hooks.offerPreviewItem = new FlipHubItem();
        hooks.offerPreviewItem.item_id = 4151;
        RemainingLimitSuggestionService service = new RemainingLimitSuggestionService(hooks);

        Integer remaining = service.computeSuggestion(4151);

        assertEquals(Integer.valueOf(50), remaining);
        assertEquals(1, hooks.ensureProfileLoadedCalls);
        assertEquals(77L, hooks.lastEnsuredProfileKey);
        assertEquals(1, hooks.requestGeLimitsCalls);
        assertTrue(hooks.lastRequestedItemIds.contains(4151));
        assertEquals(Integer.valueOf(70), hooks.offerPreviewItem.ge_limit_total);
        assertEquals(Integer.valueOf(50), hooks.offerPreviewItem.ge_limit_remaining);
        assertEquals(Long.valueOf(14_399_000L), hooks.offerPreviewItem.ge_limit_reset_ms);
    }

    @Test
    public void computeSuggestionReturnsNullWhenGeLimitUnavailable() {
        TestHooks hooks = new TestHooks();
        hooks.localAccountKey = 55L;
        hooks.nowMs = 10_000L;
        hooks.cachedGeLimit = null;
        hooks.fallbackGeLimit = 0;
        RemainingLimitSuggestionService service = new RemainingLimitSuggestionService(hooks);

        assertNull(service.computeSuggestion(4151));
        assertEquals(1, hooks.requestGeLimitsCalls);
        assertEquals(0, hooks.buildLocalLimitInfoCalls);
    }

    private static LocalLimitInfo limitInfo(int itemId, long buyQty, Long firstBuyTs) {
        LocalLimitInfo info = new LocalLimitInfo(itemId);
        info.buyQty = buyQty;
        info.firstBuyTs = firstBuyTs;
        return info;
    }

    private static final class TestHooks implements RemainingLimitSuggestionService.Hooks {
        private long localAccountKey = 1L;
        private long selectedProfileKey = -1L;
        private long nowMs;
        private Integer cachedGeLimit;
        private Integer fallbackGeLimit;
        private final Map<Integer, LocalLimitInfo> limitInfoByItem = new HashMap<>();
        private FlipHubItem offerPreviewItem;
        private int ensureProfileLoadedCalls;
        private long lastEnsuredProfileKey;
        private int requestGeLimitsCalls;
        private Set<Integer> lastRequestedItemIds = new HashSet<>();
        private int buildLocalLimitInfoCalls;

        @Override
        public long resolveLocalAccountKey() {
            return localAccountKey;
        }

        @Override
        public long resolveSelectedProfileKey() {
            return selectedProfileKey;
        }

        @Override
        public void ensureProfileLoaded(long accountKey) {
            ensureProfileLoadedCalls++;
            lastEnsuredProfileKey = accountKey;
        }

        @Override
        public void requestGeLimits(Set<Integer> itemIds) {
            requestGeLimitsCalls++;
            lastRequestedItemIds = itemIds != null ? new HashSet<>(itemIds) : new HashSet<>();
        }

        @Override
        public Integer getCachedGeLimit(int itemId) {
            return cachedGeLimit;
        }

        @Override
        public Integer lookupGeLimitSafe(int itemId) {
            return fallbackGeLimit;
        }

        @Override
        public Map<Integer, LocalLimitInfo> buildLocalLimitInfo(long accountKey, long nowMs) {
            buildLocalLimitInfoCalls++;
            return limitInfoByItem;
        }

        @Override
        public FlipHubItem getOfferPreviewItem() {
            return offerPreviewItem;
        }

        @Override
        public long nowMs() {
            return nowMs;
        }
    }
}
