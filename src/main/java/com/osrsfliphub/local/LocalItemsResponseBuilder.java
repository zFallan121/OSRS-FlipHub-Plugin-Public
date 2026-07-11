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

import static com.osrsfliphub.GeLifecyclePluginConstants.DEFAULT_ITEMS_PAGE_SIZE;

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
final class LocalItemsResponseBuilder {
    private final int defaultItemsPageSize = Math.max(1, DEFAULT_ITEMS_PAGE_SIZE);

    @Inject
    LocalItemsResponseBuilder() {
    }

    private GeLifecyclePanelDataRuntimeService panelData() {
        return PluginInjectorBridge.get(GeLifecyclePanelDataRuntimeService.class);
    }

    private ApiClient.ItemsResponse emptyItemsResponse(long asOfMs, Long priceCacheMs) {
        GeLifecyclePanelDataRuntimeService service = panelData();
        return service != null ? service.emptyItemsResponse(asOfMs, priceCacheMs) : null;
    }

    ApiClient.ItemsResponse build(boolean includeEmptyFallback,
                                  String currentQuery,
                                  boolean bookmarkFilterEnabled,
                                  Set<Integer> bookmarkedItems,
                                  int currentPage) {
        Client client = PluginAccess.plugin().client;
        if (client == null) {
            return emptyItemsResponse(System.currentTimeMillis(), null);
        }
        GrandExchangeOffer[] rawOffers = client.getGrandExchangeOffers();
        GrandExchangeOffer[] offers = rawOffers != null && rawOffers.length > 0
            ? rawOffers : new GrandExchangeOffer[0];

        ProfileSelectionPresentationFacadeService selection =
            PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class);
        long tradeAccountKey = selection != null ? selection.resolveSelectedProfileKey() : 0L;
        LocalAccountSessionService session = PluginInjectorBridge.get(LocalAccountSessionService.class);
        long limitAccountKey = session != null ? session.resolveLimitAccountKey(tradeAccountKey) : tradeAccountKey;
        long nowMs = System.currentTimeMillis();

        LocalTradeSessionFacadeService tradeSession = PluginInjectorBridge.get(LocalTradeSessionFacadeService.class);
        Map<Integer, LocalTradeInfo> tradeInfo = tradeAccountKey >= 0 && tradeSession != null
            ? tradeSession.buildLocalTradeInfo(tradeAccountKey)
            : new HashMap<>();
        Map<Integer, LocalLimitInfo> limitInfo = limitAccountKey >= 0 && tradeSession != null
            ? tradeSession.buildLocalLimitInfo(limitAccountKey, nowMs)
            : new HashMap<>();

        LocalItemsAssembler assembler = PluginInjectorBridge.get(LocalItemsAssembler.class);
        LocalItemsAssembler.Result assembled = assembler != null
            ? assembler.assemble(
                offers,
                tradeInfo != null ? tradeInfo : new HashMap<>(),
                limitInfo != null ? limitInfo : new HashMap<>(),
                currentQuery,
                bookmarkFilterEnabled,
                bookmarkedItems)
            : new LocalItemsAssembler.Result(new ArrayList<>(), new HashSet<>());
        List<FlipHubItem> items = assembled != null && assembled.items != null ? assembled.items : new ArrayList<>();
        Set<Integer> itemsNeedingLimits = assembled != null && assembled.itemsNeedingLimits != null
            ? assembled.itemsNeedingLimits
            : new HashSet<>();

        // Filter hidden items before pagination so each page has consistent visible card counts.
        if (!items.isEmpty()) {
            PluginState state = PluginInjectorBridge.get(PluginState.class);
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
            GeLimitService geLimitService = PluginInjectorBridge.get(GeLimitService.class);
            if (geLimitService != null) {
                geLimitService.requestGeLimits(itemsNeedingLimits);
            }
        }

        GeLifecyclePanelDataRuntimeService panelData = panelData();
        if (items.isEmpty() && includeEmptyFallback) {
            ApiClient.ItemsResponse stampFallback = panelData != null ? panelData.buildOfferStampFallback() : null;
            if (stampFallback != null && stampFallback.items != null && !stampFallback.items.isEmpty()) {
                return stampFallback;
            }
            return panelData != null ? panelData.buildOfferStatusFallback() : null;
        }

        LocalItemsPager.sortByRecentTradeThenName(items);
        LocalItemsPager.Page page = LocalItemsPager.paginate(items, currentPage, defaultItemsPageSize);
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
