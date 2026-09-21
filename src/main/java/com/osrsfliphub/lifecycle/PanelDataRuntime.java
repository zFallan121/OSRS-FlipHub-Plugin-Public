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
import net.runelite.api.Client;
import net.runelite.api.widgets.*;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class PanelDataRuntime {
    private final PluginState pluginState;
    private final StatsView statsViews;
    private final RankUp rankUp;
    private final OfferPreviewRuntime offerPreviewRuntime;
    private final OfferPreviewBuilder offerPreviewBuilder;
    private final Client client;
    private final OfferStampFallbackBuilder offerStampFallbackBuilder;

    /**
     * The stamps, or null when there is no state to hold them yet.
     *
     * <p>The only one of these that is not a single expression, and the reason is that the
     * state itself can be absent - during start-up, and after a wipe.
     */
    private Map<Integer, Stamp> offerUpdateStamps() {
        return pluginState.getOfferUpdateStamps();
    }

    FlipHubItem buildLocalOfferPreview(int itemId) {
        return offerPreviewBuilder.build(itemId);
    }

    ApiClient.ItemsResponse buildLocalItemsResponse(boolean includeEmptyFallback) {
        ItemsResponseBuilder builder = Bridge.get(ItemsResponseBuilder.class);
        if (builder == null) {
            return emptyItemsResponse(System.currentTimeMillis(), null);
        }
        return builder.build(
            includeEmptyFallback,
            Access.plugin().currentQuery,
            Access.plugin().bookmarkFilterEnabled,
            pluginState.getBookmarkedItems(),
            Access.plugin().currentItemSort,
            Access.plugin().currentItemSortAscending,
            Access.plugin().currentPage
        );
    }

    void updateLocalItemsPanel() {
        ApiClient.ItemsResponse local = buildLocalItemsResponse(true);
        Panel panel = Access.plugin().panel;
        if (panel == null) {
            return;
        }
        panel.setItems(
            local != null ? local.items : null,
            local != null ? local.page : 1,
            local != null ? local.total_pages : 1,
            local != null ? local.as_of_ms : System.currentTimeMillis(),
            local != null ? local.price_cache_ms : null
        );
    }

    void renderLocalStats() {
        Panel panel = Access.plugin().panel;
        if (panel == null) {
            return;
        }
        StatsView.Result statsView = statsViews.build();
        panel.setStatsData(statsView.summary, statsView.items, statsView.flipHistory, statsView.asOfMs);
        rankUp.refreshPanel();
    }

    ApiClient.ItemsResponse buildOfferStatusFallback() {
        Widget geRoot = offerPreviewRuntime
            .getVisibleGeRoot(client, ComponentID.GRAND_EXCHANGE_WINDOW_CONTAINER);
        if (geRoot == null) {
            return emptyItemsResponse(System.currentTimeMillis(), null);
        }
        int itemId = offerPreviewRuntime.findFirstItemId(geRoot);
        if (itemId <= 0) {
            return emptyItemsResponse(System.currentTimeMillis(), null);
        }

        FlipHubItem item = buildLocalOfferPreview(itemId);
        if (item == null) {
            return emptyItemsResponse(System.currentTimeMillis(), null);
        }

        return buildPagedItemsResponse(
            Collections.singletonList(item),
            1,
            1,
            1,
            1,
            System.currentTimeMillis(),
            null
        );
    }

    ApiClient.ItemsResponse buildOfferStampFallback() {
        Map<Integer, Stamp> offerUpdateStamps = offerUpdateStamps();
        if (offerUpdateStamps == null || offerUpdateStamps.isEmpty()) {
            return emptyItemsResponse(System.currentTimeMillis(), null);
        }

        List<FlipHubItem> items = offerStampFallbackBuilder.buildItems(offerUpdateStamps.values());
        if (items.isEmpty()) {
            return emptyItemsResponse(System.currentTimeMillis(), null);
        }

        int total = items.size();
        return buildPagedItemsResponse(items, 1, total, total, 1, System.currentTimeMillis(), null);
    }

    ApiClient.ItemsResponse emptyItemsResponse(long asOfMs, Long priceCacheMs) {
        return ItemsResponseFactory.empty(asOfMs, priceCacheMs);
    }

    ApiClient.ItemsResponse buildPagedItemsResponse(List<FlipHubItem> items,
                                                    int page,
                                                    int pageSize,
                                                    int totalItems,
                                                    int totalPages,
                                                    long asOfMs,
                                                    Long priceCacheMs) {
        return ItemsResponseFactory.paged(items, page, pageSize, totalItems, totalPages, asOfMs, priceCacheMs);
    }
}
