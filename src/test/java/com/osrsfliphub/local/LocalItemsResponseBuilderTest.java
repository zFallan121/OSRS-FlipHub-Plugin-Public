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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class LocalItemsResponseBuilderTest {
    @Test
    public void buildReturnsEmptyResponseWhenOffersUnavailable() {
        TestHooks hooks = new TestHooks();
        hooks.offers = null;
        LocalItemsResponseBuilder builder = new LocalItemsResponseBuilder(8, hooks);

        ApiClient.ItemsResponse result = builder.build(true, "", false, new HashSet<>(), 1);

        assertSame(hooks.emptyResponse, result);
        assertEquals(1, hooks.emptyCalls);
    }

    @Test
    public void buildReturnsStampFallbackWhenAssembledItemsEmpty() {
        TestHooks hooks = new TestHooks();
        hooks.assembledResult = new LocalItemsAssembler.Result(new ArrayList<>(), new HashSet<>());
        hooks.stampFallback = response(items(item("Stamp", 1_000L)));
        hooks.offerStatusFallback = response(items(item("Offer", 2_000L)));
        LocalItemsResponseBuilder builder = new LocalItemsResponseBuilder(8, hooks);

        ApiClient.ItemsResponse result = builder.build(true, "", false, new HashSet<>(), 1);

        assertSame(hooks.stampFallback, result);
    }

    @Test
    public void buildFallsBackToOfferStatusWhenStampFallbackEmpty() {
        TestHooks hooks = new TestHooks();
        hooks.assembledResult = new LocalItemsAssembler.Result(new ArrayList<>(), new HashSet<>());
        hooks.stampFallback = response(new ArrayList<>());
        hooks.offerStatusFallback = response(items(item("Offer", 2_000L)));
        LocalItemsResponseBuilder builder = new LocalItemsResponseBuilder(8, hooks);

        ApiClient.ItemsResponse result = builder.build(true, "", false, new HashSet<>(), 1);

        assertSame(hooks.offerStatusFallback, result);
    }

    @Test
    public void buildSortsPaginatesAndRequestsLimits() {
        TestHooks hooks = new TestHooks();
        List<FlipHubItem> assembledItems = new ArrayList<>();
        assembledItems.add(item("Zamorak brew", 1_000L));
        assembledItems.add(item("Abyssal whip", 2_000L));
        Set<Integer> limitIds = new HashSet<>();
        limitIds.add(assembledItems.get(0).item_id);
        limitIds.add(assembledItems.get(1).item_id);
        hooks.assembledResult = new LocalItemsAssembler.Result(assembledItems, limitIds);
        LocalItemsResponseBuilder builder = new LocalItemsResponseBuilder(1, hooks);

        ApiClient.ItemsResponse result = builder.build(false, "", false, new HashSet<>(), 1);

        assertEquals(1, hooks.requestLimitsCalls);
        assertEquals(limitIds, hooks.lastRequestedLimits);
        assertEquals(1, result.page);
        assertEquals(1, result.page_size);
        assertEquals(2, result.total_items);
        assertEquals(2, result.total_pages);
        assertEquals(1, result.items.size());
        assertEquals("Abyssal whip", result.items.get(0).item_name);
        assertTrue(hooks.pagedCalls > 0);
    }

    @Test
    public void buildFiltersHiddenItemsBeforePagination() {
        TestHooks hooks = new TestHooks();
        List<FlipHubItem> assembledItems = new ArrayList<>();
        FlipHubItem hidden = item("Hidden item", 3_000L);
        FlipHubItem visibleOne = item("Visible one", 2_000L);
        FlipHubItem visibleTwo = item("Visible two", 1_000L);
        assembledItems.add(hidden);
        assembledItems.add(visibleOne);
        assembledItems.add(visibleTwo);
        hooks.assembledResult = new LocalItemsAssembler.Result(assembledItems, new HashSet<>());
        hooks.hiddenItemIds.add(hidden.item_id);
        LocalItemsResponseBuilder builder = new LocalItemsResponseBuilder(2, hooks);

        ApiClient.ItemsResponse result = builder.build(false, "", false, new HashSet<>(), 1);

        assertEquals(2, result.total_items);
        assertEquals(1, result.total_pages);
        assertEquals(2, result.items.size());
        assertEquals("Visible one", result.items.get(0).item_name);
        assertEquals("Visible two", result.items.get(1).item_name);
    }

    private static FlipHubItem item(String name, long tsMs) {
        FlipHubItem item = new FlipHubItem();
        item.item_id = name.hashCode();
        item.item_name = name;
        item.last_buy_ts_ms = tsMs;
        return item;
    }

    private static List<FlipHubItem> items(FlipHubItem item) {
        List<FlipHubItem> list = new ArrayList<>();
        list.add(item);
        return list;
    }

    private static ApiClient.ItemsResponse response(List<FlipHubItem> items) {
        ApiClient.ItemsResponse response = new ApiClient.ItemsResponse();
        response.items = items;
        response.page = 1;
        response.page_size = items != null ? Math.max(1, items.size()) : 1;
        response.total_items = items != null ? items.size() : 0;
        response.total_pages = 1;
        response.as_of_ms = System.currentTimeMillis();
        return response;
    }

    private static final class TestHooks implements LocalItemsResponseBuilder.Hooks {
        private GrandExchangeOffer[] offers = new GrandExchangeOffer[0];
        private long selectedProfileKey = 1L;
        private long limitAccountKey = 1L;
        private final Map<Integer, LocalTradeInfo> tradeInfo = new HashMap<>();
        private final Map<Integer, LocalLimitInfo> limitInfo = new HashMap<>();
        private LocalItemsAssembler.Result assembledResult = new LocalItemsAssembler.Result(new ArrayList<>(), new HashSet<>());
        private ApiClient.ItemsResponse stampFallback = response(new ArrayList<>());
        private ApiClient.ItemsResponse offerStatusFallback = response(new ArrayList<>());
        private final ApiClient.ItemsResponse emptyResponse = response(new ArrayList<>());
        private int requestLimitsCalls;
        private Set<Integer> lastRequestedLimits = new HashSet<>();
        private final Set<Integer> hiddenItemIds = new HashSet<>();
        private int pagedCalls;
        private int emptyCalls;

        @Override
        public long resolveSelectedProfileKey() {
            return selectedProfileKey;
        }

        @Override
        public long resolveLimitAccountKey(long tradeAccountKey) {
            return limitAccountKey;
        }

        @Override
        public Map<Integer, LocalTradeInfo> buildLocalTradeInfo(long accountKey) {
            return tradeInfo;
        }

        @Override
        public Map<Integer, LocalLimitInfo> buildLocalLimitInfo(long accountKey, long nowMs) {
            return limitInfo;
        }

        @Override
        public GrandExchangeOffer[] getGrandExchangeOffers() {
            return offers;
        }

        @Override
        public LocalItemsAssembler.Result assemble(GrandExchangeOffer[] offers,
                                                   Map<Integer, LocalTradeInfo> tradeInfo,
                                                   Map<Integer, LocalLimitInfo> limitInfo,
                                                   String currentQuery,
                                                   boolean bookmarkFilterEnabled,
                                                   Set<Integer> bookmarkedItems) {
            return assembledResult;
        }

        @Override
        public boolean isHidden(int itemId) {
            return hiddenItemIds.contains(itemId);
        }

        @Override
        public void requestGeLimits(Set<Integer> itemIds) {
            requestLimitsCalls++;
            lastRequestedLimits = itemIds != null ? new HashSet<>(itemIds) : new HashSet<>();
        }

        @Override
        public ApiClient.ItemsResponse buildOfferStampFallback() {
            return stampFallback;
        }

        @Override
        public ApiClient.ItemsResponse buildOfferStatusFallback() {
            return offerStatusFallback;
        }

        @Override
        public ApiClient.ItemsResponse buildPagedItemsResponse(List<FlipHubItem> items,
                                                               int page,
                                                               int pageSize,
                                                               int totalItems,
                                                               int totalPages,
                                                               long asOfMs,
                                                               Long priceCacheMs) {
            pagedCalls++;
            ApiClient.ItemsResponse response = new ApiClient.ItemsResponse();
            response.items = items;
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
            emptyCalls++;
            emptyResponse.as_of_ms = asOfMs;
            emptyResponse.price_cache_ms = priceCacheMs;
            return emptyResponse;
        }

        @Override
        public long nowMs() {
            return 123_456L;
        }
    }
}
