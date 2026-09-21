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

import java.util.*;
import javax.inject.*;
import lombok.RequiredArgsConstructor;
import net.runelite.api.*;
import static com.osrsfliphub.Const.DEFAULT_ITEMS_PAGE_SIZE;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class ItemsResponseBuilder {
    private final ProfileSelectionPresentation profileSelectionPresentation;
    private final AccountSession accountSession;
    private final TradeSession tradeSession;
    private final PluginState pluginState;
    private final GeLimit geLimit;
    private final PanelDataRuntime panelDataRuntime;
    private final Client client;
    private final ItemsAssembler assembler;

    private final int defaultItemsPageSize = Math.max(1, DEFAULT_ITEMS_PAGE_SIZE);

    ApiClient.ItemsResponse build(boolean includeEmptyFallback,
                                  String currentQuery,
                                  boolean bookmarkFilterEnabled,
                                  Set<Integer> bookmarkedItems,
                                  StatsItemSort itemSort,
                                  boolean itemSortAscending,
                                  int currentPage) {
        GrandExchangeOffer[] rawOffers = client.getGrandExchangeOffers();
        GrandExchangeOffer[] offers = rawOffers != null && rawOffers.length > 0
            ? rawOffers : new GrandExchangeOffer[0];

        long tradeAccountKey = profileSelectionPresentation.resolveSelectedProfileKey();
        long limitAccountKey = accountSession.resolveLimitAccountKey(tradeAccountKey);
        long nowMs = System.currentTimeMillis();

        Map<Integer, TradeInfo> tradeInfo = tradeAccountKey >= 0
            ? tradeSession.buildLocalTradeInfo(tradeAccountKey)
            : new HashMap<>();
        Map<Integer, LimitInfo> limitInfo = limitAccountKey >= 0
            ? tradeSession.buildLocalLimitInfo(limitAccountKey, nowMs)
            : new HashMap<>();

        ItemsAssembler.Result assembled = assembler.assemble(
                offers,
                tradeInfo != null ? tradeInfo : new HashMap<>(),
                limitInfo != null ? limitInfo : new HashMap<>(),
                currentQuery,
                bookmarkFilterEnabled,
                bookmarkedItems);
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
                if (pluginState.getHiddenItems().contains(item.item_id)) {
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
            geLimit.requestGeLimits(itemsNeedingLimits);
        }

        // The fallbacks answer "the list is empty because nothing has been traded yet" with the
        // offer on screen. Once a filter is in force an empty list means "nothing you asked for
        // is here", and an item that was not asked for is not an answer to that.
        boolean filtered = bookmarkFilterEnabled
            || Str.hasText(currentQuery);
        if (items.isEmpty() && includeEmptyFallback && !filtered) {
            ApiClient.ItemsResponse stampFallback = panelDataRuntime.buildOfferStampFallback();
            if (stampFallback != null && stampFallback.items != null && !stampFallback.items.isEmpty()) {
                return stampFallback;
            }
            return panelDataRuntime.buildOfferStatusFallback();
        }

        ItemsPager.sortItems(items, itemSort, itemSortAscending);
        ItemsPager.Page page = ItemsPager.paginate(items, currentPage, defaultItemsPageSize);
        return panelDataRuntime.buildPagedItemsResponse(
                page.pageItems,
                page.page,
                page.pageSize,
                page.totalItems,
                page.totalPages,
                System.currentTimeMillis(),
                null);
    }
}
