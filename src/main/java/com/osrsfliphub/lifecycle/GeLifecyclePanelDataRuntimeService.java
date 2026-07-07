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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;

@Singleton
final class GeLifecyclePanelDataRuntimeService {
    private final Supplier<LocalOfferPreviewBuilder> localOfferPreviewBuilderSupplier;
    private final Supplier<LocalItemsResponseBuilder> localItemsResponseBuilderSupplier;
    private final Supplier<String> currentQuerySupplier;
    private final BooleanSupplier bookmarkFilterEnabledSupplier;
    private final Supplier<Set<Integer>> bookmarkedItemsSupplier;
    private final IntSupplier currentPageSupplier;
    private final Supplier<FlipHubPanel> panelSupplier;
    private final Supplier<LocalStatsViewService> localStatsViewServiceSupplier;
    private final Supplier<OfferPreviewRuntimeFacadeService> offerPreviewRuntimeFacadeServiceSupplier;
    private final Supplier<Client> clientSupplier;
    private final Supplier<Map<Integer, OfferUpdateStamp>> offerUpdateStampsSupplier;
    private final Supplier<OfferStampFallbackBuilder> offerStampFallbackBuilderSupplier;
    private final LongSupplier nowSupplier;

    @Inject
    GeLifecyclePanelDataRuntimeService() {
        this(
            () -> PluginInjectorBridge.get(LocalOfferPreviewBuilder.class),
            () -> PluginInjectorBridge.get(LocalItemsResponseBuilder.class),
            () -> PluginAccess.plugin().currentQuery,
            () -> PluginAccess.plugin().bookmarkFilterEnabled,
            () -> PluginAccess.plugin().bookmarkedItems,
            () -> PluginAccess.plugin().currentPage,
            () -> PluginAccess.plugin().panel,
            () -> PluginInjectorBridge.get(LocalStatsViewService.class),
            () -> PluginInjectorBridge.get(OfferPreviewRuntimeFacadeService.class),
            () -> PluginAccess.plugin().client,
            () -> PluginAccess.plugin().offerUpdateStamps,
            () -> PluginInjectorBridge.get(OfferStampFallbackBuilder.class),
            System::currentTimeMillis);
    }

    GeLifecyclePanelDataRuntimeService(
        Supplier<LocalOfferPreviewBuilder> localOfferPreviewBuilderSupplier,
        Supplier<LocalItemsResponseBuilder> localItemsResponseBuilderSupplier,
        Supplier<String> currentQuerySupplier,
        BooleanSupplier bookmarkFilterEnabledSupplier,
        Supplier<Set<Integer>> bookmarkedItemsSupplier,
        IntSupplier currentPageSupplier,
        Supplier<FlipHubPanel> panelSupplier,
        Supplier<LocalStatsViewService> localStatsViewServiceSupplier,
        Supplier<OfferPreviewRuntimeFacadeService> offerPreviewRuntimeFacadeServiceSupplier,
        Supplier<Client> clientSupplier,
        Supplier<Map<Integer, OfferUpdateStamp>> offerUpdateStampsSupplier,
        Supplier<OfferStampFallbackBuilder> offerStampFallbackBuilderSupplier,
        LongSupplier nowSupplier
    ) {
        this.localOfferPreviewBuilderSupplier = localOfferPreviewBuilderSupplier;
        this.localItemsResponseBuilderSupplier = localItemsResponseBuilderSupplier;
        this.currentQuerySupplier = currentQuerySupplier;
        this.bookmarkFilterEnabledSupplier = bookmarkFilterEnabledSupplier;
        this.bookmarkedItemsSupplier = bookmarkedItemsSupplier;
        this.currentPageSupplier = currentPageSupplier;
        this.panelSupplier = panelSupplier;
        this.localStatsViewServiceSupplier = localStatsViewServiceSupplier;
        this.offerPreviewRuntimeFacadeServiceSupplier = offerPreviewRuntimeFacadeServiceSupplier;
        this.clientSupplier = clientSupplier;
        this.offerUpdateStampsSupplier = offerUpdateStampsSupplier;
        this.offerStampFallbackBuilderSupplier = offerStampFallbackBuilderSupplier;
        this.nowSupplier = nowSupplier;
    }

    FlipHubItem buildLocalOfferPreview(int itemId) {
        LocalOfferPreviewBuilder builder = localOfferPreviewBuilderSupplier.get();
        if (builder == null) {
            return null;
        }
        return builder.build(itemId);
    }

    ApiClient.ItemsResponse buildLocalItemsResponse(boolean includeEmptyFallback) {
        LocalItemsResponseBuilder builder = localItemsResponseBuilderSupplier.get();
        if (builder == null) {
            return emptyItemsResponse(nowSupplier.getAsLong(), null);
        }
        return builder.build(
            includeEmptyFallback,
            currentQuerySupplier.get(),
            bookmarkFilterEnabledSupplier.getAsBoolean(),
            bookmarkedItemsSupplier.get(),
            currentPageSupplier.getAsInt()
        );
    }

    void updateLocalItemsPanel() {
        ApiClient.ItemsResponse local = buildLocalItemsResponse(true);
        FlipHubPanel panel = panelSupplier.get();
        if (panel == null) {
            return;
        }
        panel.setItems(
            local != null ? local.items : null,
            local != null ? local.page : 1,
            local != null ? local.total_pages : 1,
            local != null ? local.as_of_ms : nowSupplier.getAsLong(),
            local != null ? local.price_cache_ms : null
        );
    }

    void renderLocalStats() {
        FlipHubPanel panel = panelSupplier.get();
        if (panel == null) {
            return;
        }
        LocalStatsViewService statsService = localStatsViewServiceSupplier.get();
        if (statsService == null) {
            return;
        }
        LocalStatsViewService.Result statsView = statsService.build();
        panel.setStatsData(statsView.summary, statsView.items, statsView.flipHistory, statsView.asOfMs);
    }

    ApiClient.ItemsResponse buildOfferStatusFallback() {
        OfferPreviewRuntimeFacadeService offerPreviewRuntimeFacadeService = offerPreviewRuntimeFacadeServiceSupplier.get();
        Client client = clientSupplier.get();
        if (offerPreviewRuntimeFacadeService == null || client == null) {
            return emptyItemsResponse(nowSupplier.getAsLong(), null);
        }
        Widget geRoot = offerPreviewRuntimeFacadeService
            .getVisibleGeRoot(client, ComponentID.GRAND_EXCHANGE_WINDOW_CONTAINER);
        if (geRoot == null) {
            return emptyItemsResponse(nowSupplier.getAsLong(), null);
        }
        int itemId = offerPreviewRuntimeFacadeService.findFirstItemId(geRoot);
        if (itemId <= 0) {
            return emptyItemsResponse(nowSupplier.getAsLong(), null);
        }

        FlipHubItem item = buildLocalOfferPreview(itemId);
        if (item == null) {
            return emptyItemsResponse(nowSupplier.getAsLong(), null);
        }

        return buildPagedItemsResponse(
            Collections.singletonList(item),
            1,
            1,
            1,
            1,
            nowSupplier.getAsLong(),
            null
        );
    }

    ApiClient.ItemsResponse buildOfferStampFallback() {
        Map<Integer, OfferUpdateStamp> offerUpdateStamps = offerUpdateStampsSupplier.get();
        if (offerUpdateStamps == null || offerUpdateStamps.isEmpty()) {
            return emptyItemsResponse(nowSupplier.getAsLong(), null);
        }

        OfferStampFallbackBuilder offerStampFallbackBuilder = offerStampFallbackBuilderSupplier.get();
        if (offerStampFallbackBuilder == null) {
            return emptyItemsResponse(nowSupplier.getAsLong(), null);
        }
        List<FlipHubItem> items = offerStampFallbackBuilder.buildItems(offerUpdateStamps.values());
        if (items.isEmpty()) {
            return emptyItemsResponse(nowSupplier.getAsLong(), null);
        }

        int total = items.size();
        return buildPagedItemsResponse(items, 1, total, total, 1, nowSupplier.getAsLong(), null);
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
