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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.GrandExchangeOffer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LocalItemsResponseBuilderFactoryServiceTest {
    @Test
    public void createBuildsBuilderThatUsesAssemblerAndRuntimeHooks() {
        LocalItemsAssembler assembler = new LocalItemsAssembler(new LocalItemsAssembler.Hooks() {
            @Override
            public String getCachedItemName(int itemId) {
                return itemId == 4151 ? "Abyssal whip" : null;
            }

            @Override
            public void cacheItemName(int itemId) {
            }

            @Override
            public void applyGuidePrices(FlipHubItem item, int itemId) {
            }

            @Override
            public void applyLocalTradeInfo(FlipHubItem item, LocalTradeInfo info) {
            }

            @Override
            public void applyLocalLimitInfo(FlipHubItem item, int itemId, LocalLimitInfo info) {
            }

            @Override
            public void applyMarginInfo(FlipHubItem item) {
            }
        });

        LocalItemsResponseBuilderFactoryService factory =
            new LocalItemsResponseBuilderFactoryService(8, assembler);
        TestRuntimeHooks hooks = new TestRuntimeHooks();
        LocalItemsResponseBuilder builder = factory.create(hooks);

        ApiClient.ItemsResponse response = builder.build(false, "", false, new HashSet<>(), 1);

        assertEquals(1, response.total_items);
        assertEquals(1, response.items.size());
        assertEquals(4151, response.items.get(0).item_id);
        assertEquals(1, hooks.requestLimitsCalls);
        assertTrue(hooks.lastRequestedLimits.contains(4151));
    }

    @Test
    public void createWithNullRuntimeHooksReturnsBuilderThatYieldsNull() {
        LocalItemsResponseBuilderFactoryService factory =
            new LocalItemsResponseBuilderFactoryService(8, null);
        LocalItemsResponseBuilder builder = factory.create(null);

        ApiClient.ItemsResponse response = builder.build(false, "", false, new HashSet<>(), 1);

        assertNull(response);
    }

    private static final class TestRuntimeHooks implements LocalItemsResponseBuilderFactoryService.RuntimeHooks {
        private final Map<Integer, LocalTradeInfo> tradeInfo = new HashMap<>();
        private int requestLimitsCalls;
        private Set<Integer> lastRequestedLimits = new HashSet<>();

        private TestRuntimeHooks() {
            tradeInfo.put(4151, new LocalTradeInfo(4151));
        }

        @Override
        public long resolveSelectedProfileKey() {
            return 1L;
        }

        @Override
        public long resolveLimitAccountKey(long tradeAccountKey) {
            return tradeAccountKey;
        }

        @Override
        public Map<Integer, LocalTradeInfo> buildLocalTradeInfo(long accountKey) {
            return tradeInfo;
        }

        @Override
        public Map<Integer, LocalLimitInfo> buildLocalLimitInfo(long accountKey, long nowMs) {
            return new HashMap<>();
        }

        @Override
        public GrandExchangeOffer[] getGrandExchangeOffers() {
            return new GrandExchangeOffer[0];
        }

        @Override
        public boolean isHidden(int itemId) {
            return false;
        }

        @Override
        public void requestGeLimits(Set<Integer> itemIds) {
            requestLimitsCalls++;
            lastRequestedLimits = itemIds != null ? new HashSet<>(itemIds) : new HashSet<>();
        }

        @Override
        public ApiClient.ItemsResponse buildOfferStampFallback() {
            return emptyItemsResponse(nowMs(), null);
        }

        @Override
        public ApiClient.ItemsResponse buildOfferStatusFallback() {
            return emptyItemsResponse(nowMs(), null);
        }

        @Override
        public ApiClient.ItemsResponse buildPagedItemsResponse(List<FlipHubItem> items,
                                                               int page,
                                                               int pageSize,
                                                               int totalItems,
                                                               int totalPages,
                                                               long asOfMs,
                                                               Long priceCacheMs) {
            ApiClient.ItemsResponse response = new ApiClient.ItemsResponse();
            response.items = items != null ? items : new ArrayList<>();
            response.page = page;
            response.page_size = pageSize;
            response.total_items = totalItems;
            response.total_pages = totalPages;
            response.as_of_ms = asOfMs;
            response.price_cache_ms = priceCacheMs;
            return response;
        }

        @Override
        public ApiClient.ItemsResponse emptyItemsResponse(long asOfMs, Long priceCacheMs) {
            ApiClient.ItemsResponse response = new ApiClient.ItemsResponse();
            response.items = new ArrayList<>();
            response.page = 1;
            response.page_size = 1;
            response.total_items = 0;
            response.total_pages = 1;
            response.as_of_ms = asOfMs;
            response.price_cache_ms = priceCacheMs;
            return response;
        }

        @Override
        public long nowMs() {
            return 123_456L;
        }
    }
}
