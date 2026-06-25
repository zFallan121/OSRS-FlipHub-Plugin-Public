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

final class LocalItemsResponseBuilder {
    interface Hooks {
        long resolveSelectedProfileKey();
        long resolveLimitAccountKey(long tradeAccountKey);
        Map<Integer, LocalTradeInfo> buildLocalTradeInfo(long accountKey);
        Map<Integer, LocalLimitInfo> buildLocalLimitInfo(long accountKey, long nowMs);
        GrandExchangeOffer[] getGrandExchangeOffers();
        LocalItemsAssembler.Result assemble(GrandExchangeOffer[] offers,
                                           Map<Integer, LocalTradeInfo> tradeInfo,
                                           Map<Integer, LocalLimitInfo> limitInfo,
                                           String currentQuery,
                                           boolean bookmarkFilterEnabled,
                                           Set<Integer> bookmarkedItems);
        boolean isHidden(int itemId);
        void requestGeLimits(Set<Integer> itemIds);
        ApiClient.ItemsResponse buildOfferStampFallback();
        ApiClient.ItemsResponse buildOfferStatusFallback();
        ApiClient.ItemsResponse buildPagedItemsResponse(List<FlipHubItem> items,
                                                        int page,
                                                        int pageSize,
                                                        int totalItems,
                                                        int totalPages,
                                                        long asOfMs,
                                                        Long priceCacheMs);
        ApiClient.ItemsResponse emptyItemsResponse(long asOfMs, Long priceCacheMs);
        long nowMs();
    }

    private final int defaultItemsPageSize;
    private final Hooks hooks;

    LocalItemsResponseBuilder(int defaultItemsPageSize, Hooks hooks) {
        this.defaultItemsPageSize = Math.max(1, defaultItemsPageSize);
        this.hooks = hooks;
    }

    ApiClient.ItemsResponse build(boolean includeEmptyFallback,
                                  String currentQuery,
                                  boolean bookmarkFilterEnabled,
                                  Set<Integer> bookmarkedItems,
                                  int currentPage) {
        if (hooks == null) {
            return null;
        }

        GrandExchangeOffer[] offers = hooks.getGrandExchangeOffers();
        if (offers == null) {
            return hooks.emptyItemsResponse(hooks.nowMs(), null);
        }

        long tradeAccountKey = hooks.resolveSelectedProfileKey();
        long limitAccountKey = hooks.resolveLimitAccountKey(tradeAccountKey);
        long nowMs = hooks.nowMs();

        Map<Integer, LocalTradeInfo> tradeInfo = tradeAccountKey >= 0
            ? hooks.buildLocalTradeInfo(tradeAccountKey)
            : new HashMap<>();
        Map<Integer, LocalLimitInfo> limitInfo = limitAccountKey >= 0
            ? hooks.buildLocalLimitInfo(limitAccountKey, nowMs)
            : new HashMap<>();

        LocalItemsAssembler.Result assembled = hooks.assemble(
            offers,
            tradeInfo != null ? tradeInfo : new HashMap<>(),
            limitInfo != null ? limitInfo : new HashMap<>(),
            currentQuery,
            bookmarkFilterEnabled,
            bookmarkedItems
        );
        List<FlipHubItem> items = assembled != null && assembled.items != null ? assembled.items : new ArrayList<>();
        Set<Integer> itemsNeedingLimits = assembled != null && assembled.itemsNeedingLimits != null
            ? assembled.itemsNeedingLimits
            : new HashSet<>();

        // Filter hidden items before pagination so each page has consistent visible card counts.
        if (!items.isEmpty()) {
            List<FlipHubItem> visibleItems = new ArrayList<>(items.size());
            Set<Integer> visibleLimitIds = new HashSet<>();
            for (FlipHubItem item : items) {
                if (item == null || item.item_id <= 0) {
                    continue;
                }
                if (hooks.isHidden(item.item_id)) {
                    continue;
                }
                visibleItems.add(item);
                if (itemsNeedingLimits.contains(item.item_id)) {
                    visibleLimitIds.add(item.item_id);
                }
            }
            items = visibleItems;
            itemsNeedingLimits = visibleLimitIds;
        }

        if (!itemsNeedingLimits.isEmpty()) {
            hooks.requestGeLimits(itemsNeedingLimits);
        }

        if (items.isEmpty() && includeEmptyFallback) {
            ApiClient.ItemsResponse stampFallback = hooks.buildOfferStampFallback();
            if (stampFallback != null && stampFallback.items != null && !stampFallback.items.isEmpty()) {
                return stampFallback;
            }
            return hooks.buildOfferStatusFallback();
        }

        LocalItemsPager.sortByRecentTradeThenName(items);
        LocalItemsPager.Page page = LocalItemsPager.paginate(items, currentPage, defaultItemsPageSize);
        return hooks.buildPagedItemsResponse(
            page.pageItems,
            page.page,
            page.pageSize,
            page.totalItems,
            page.totalPages,
            hooks.nowMs(),
            null
        );
    }
}
