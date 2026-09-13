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

import static com.osrsfliphub.Const.DEFAULT_ITEMS_PAGE_SIZE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;

@Singleton
final class ItemsResponseBuilder {
    private final int defaultItemsPageSize = Math.max(1, DEFAULT_ITEMS_PAGE_SIZE);

    @Inject
    ItemsResponseBuilder() {
    }

    private PanelDataRuntime panelData() {
        return Bridge.get(PanelDataRuntime.class);
    }

    private ApiClient.ItemsResponse emptyItemsResponse(long asOfMs, Long priceCacheMs) {
        PanelDataRuntime service = panelData();
        return service != null ? service.emptyItemsResponse(asOfMs, priceCacheMs) : null;
    }

    ApiClient.ItemsResponse build(boolean includeEmptyFallback,
                                  String currentQuery,
                                  boolean bookmarkFilterEnabled,
                                  Set<Integer> bookmarkedItems,
                                  StatsItemSort itemSort,
                                  boolean itemSortAscending,
                                  int currentPage) {
        Client client = Access.plugin().client;
        if (client == null) {
            return emptyItemsResponse(System.currentTimeMillis(), null);
        }
        GrandExchangeOffer[] rawOffers = client.getGrandExchangeOffers();
        GrandExchangeOffer[] offers = rawOffers != null && rawOffers.length > 0
            ? rawOffers : new GrandExchangeOffer[0];

        ProfileSelectionPresentation selection =
            Bridge.get(ProfileSelectionPresentation.class);
        long tradeAccountKey = selection != null ? selection.resolveSelectedProfileKey() : 0L;
        AccountSession session = Bridge.get(AccountSession.class);
        long limitAccountKey = session != null ? session.resolveLimitAccountKey(tradeAccountKey) : tradeAccountKey;
        long nowMs = System.currentTimeMillis();

        TradeSession tradeSession = Bridge.get(TradeSession.class);
        Map<Integer, TradeInfo> tradeInfo = tradeAccountKey >= 0 && tradeSession != null
            ? tradeSession.buildLocalTradeInfo(tradeAccountKey)
            : new HashMap<>();
        Map<Integer, LimitInfo> limitInfo = limitAccountKey >= 0 && tradeSession != null
            ? tradeSession.buildLocalLimitInfo(limitAccountKey, nowMs)
            : new HashMap<>();

        ItemsAssembler assembler = Bridge.get(ItemsAssembler.class);
        ItemsAssembler.Result assembled = assembler != null
            ? assembler.assemble(
                offers,
                tradeInfo != null ? tradeInfo : new HashMap<>(),
                limitInfo != null ? limitInfo : new HashMap<>(),
                currentQuery,
                bookmarkFilterEnabled,
                bookmarkedItems)
            : new ItemsAssembler.Result(new ArrayList<>(), new HashSet<>());
        List<FlipHubItem> items = assembled != null && assembled.items != null ? assembled.items : new ArrayList<>();
        Set<Integer> itemsNeedingLimits = assembled != null && assembled.itemsNeedingLimits != null
            ? assembled.itemsNeedingLimits
            : new HashSet<>();

        // Filter hidden items before pagination so each page has consistent visible card counts.
        if (!items.isEmpty()) {
            PluginState state = Bridge.get(PluginState.class);
            List<FlipHubItem> visibleItems = new ArrayList<>(items.size());
            Set<Integer> visibleLimitIds = new HashSet<>();
            for (FlipHubItem item : items) {
                if (item == null || item.item_id <= 0) {
                    continue;
                }
                if (state != null && state.getHiddenItems().contains(item.item_id)) {
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
            GeLimit geLimitService = Bridge.get(GeLimit.class);
            if (geLimitService != null) {
                geLimitService.requestGeLimits(itemsNeedingLimits);
            }
        }

        PanelDataRuntime panelData = panelData();
        // The fallbacks answer "the list is empty because nothing has been traded yet" with the
        // offer on screen. Once a filter is in force an empty list means "nothing you asked for
        // is here", and an item that was not asked for is not an answer to that.
        boolean filtered = bookmarkFilterEnabled
            || (currentQuery != null && !currentQuery.trim().isEmpty());
        if (items.isEmpty() && includeEmptyFallback && !filtered) {
            ApiClient.ItemsResponse stampFallback = panelData != null ? panelData.buildOfferStampFallback() : null;
            if (stampFallback != null && stampFallback.items != null && !stampFallback.items.isEmpty()) {
                return stampFallback;
            }
            return panelData != null ? panelData.buildOfferStatusFallback() : null;
        }

        ItemsPager.sortItems(items, itemSort, itemSortAscending);
        ItemsPager.Page page = ItemsPager.paginate(items, currentPage, defaultItemsPageSize);
        return panelData != null
            ? panelData.buildPagedItemsResponse(
                page.pageItems,
                page.page,
                page.pageSize,
                page.totalItems,
                page.totalPages,
                System.currentTimeMillis(),
                null)
            : null;
    }
}
