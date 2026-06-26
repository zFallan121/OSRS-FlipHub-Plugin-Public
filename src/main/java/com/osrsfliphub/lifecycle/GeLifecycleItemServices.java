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

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;

final class GeLifecycleItemServices {
    private final ItemManager itemManager;
    private final Supplier<ClientThread> clientThreadSupplier;
    private final Runnable scheduleRefreshSoon;
    private final Runnable triggerStatsRefresh;
    private final Map<String, Integer> itemNameLookupCache;
    private final Map<Integer, String> itemNameCache;
    private final Supplier<LocalItemEnrichmentService> localItemEnrichmentServiceSupplier;
    private final Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier;
    private final Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier;
    private final Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier;
    private final Supplier<Client> clientSupplier;
    private final IntPredicate hiddenItemPredicate;
    private final Supplier<GeLimitService> geLimitServiceSupplier;
    private final Supplier<GeLifecyclePanelDataRuntimeService> panelDataRuntimeServiceSupplier;
    private final Runnable ensureSelectedProfileLoaded;
    private final LongConsumer ensureProfileLoaded;
    private final Supplier<ChatboxSuggestionRuntimeStateService> chatboxSuggestionRuntimeStateServiceSupplier;
    private final Supplier<FlipHubPanel> panelSupplier;
    private final Supplier<Integer> offerPreviewItemIdSupplier;
    private final Supplier<FlipHubItem> offerPreviewItemSupplier;
    private final Consumer<Integer> offerPreviewItemIdSetter;
    private final Consumer<FlipHubItem> offerPreviewItemSetter;
    private final int defaultItemsPageSize;

    private LocalItemsAssemblerFactoryService localItemsAssemblerFactoryService;
    private LocalItemsAssembler localItemsAssembler;
    private LocalItemsResponseBuilderFactoryService localItemsResponseBuilderFactoryService;
    private LocalItemsResponseBuilder localItemsResponseBuilder;
    private LocalOfferPreviewBuilderFactoryService localOfferPreviewBuilderFactoryService;
    private LocalOfferPreviewBuilder localOfferPreviewBuilder;

    GeLifecycleItemServices(
        ItemManager itemManager,
        Supplier<ClientThread> clientThreadSupplier,
        Runnable scheduleRefreshSoon,
        Runnable triggerStatsRefresh,
        Map<String, Integer> itemNameLookupCache,
        Map<Integer, String> itemNameCache,
        Supplier<LocalItemEnrichmentService> localItemEnrichmentServiceSupplier,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier,
        Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier,
        Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier,
        Supplier<Client> clientSupplier,
        IntPredicate hiddenItemPredicate,
        Supplier<GeLimitService> geLimitServiceSupplier,
        Supplier<GeLifecyclePanelDataRuntimeService> panelDataRuntimeServiceSupplier,
        Runnable ensureSelectedProfileLoaded,
        LongConsumer ensureProfileLoaded,
        Supplier<ChatboxSuggestionRuntimeStateService> chatboxSuggestionRuntimeStateServiceSupplier,
        Supplier<FlipHubPanel> panelSupplier,
        Supplier<Integer> offerPreviewItemIdSupplier,
        Supplier<FlipHubItem> offerPreviewItemSupplier,
        Consumer<Integer> offerPreviewItemIdSetter,
        Consumer<FlipHubItem> offerPreviewItemSetter,
        int defaultItemsPageSize
    ) {
        this.itemManager = itemManager;
        this.clientThreadSupplier = clientThreadSupplier;
        this.scheduleRefreshSoon = scheduleRefreshSoon;
        this.triggerStatsRefresh = triggerStatsRefresh;
        this.itemNameLookupCache = itemNameLookupCache;
        this.itemNameCache = itemNameCache;
        this.localItemEnrichmentServiceSupplier = localItemEnrichmentServiceSupplier;
        this.profileSelectionPresentationFacadeServiceSupplier = profileSelectionPresentationFacadeServiceSupplier;
        this.localAccountSessionServiceSupplier = localAccountSessionServiceSupplier;
        this.localTradeSessionFacadeServiceSupplier = localTradeSessionFacadeServiceSupplier;
        this.clientSupplier = clientSupplier;
        this.hiddenItemPredicate = hiddenItemPredicate;
        this.geLimitServiceSupplier = geLimitServiceSupplier;
        this.panelDataRuntimeServiceSupplier = panelDataRuntimeServiceSupplier;
        this.ensureSelectedProfileLoaded = ensureSelectedProfileLoaded;
        this.ensureProfileLoaded = ensureProfileLoaded;
        this.chatboxSuggestionRuntimeStateServiceSupplier = chatboxSuggestionRuntimeStateServiceSupplier;
        this.panelSupplier = panelSupplier;
        this.offerPreviewItemIdSupplier = offerPreviewItemIdSupplier;
        this.offerPreviewItemSupplier = offerPreviewItemSupplier;
        this.offerPreviewItemIdSetter = offerPreviewItemIdSetter;
        this.offerPreviewItemSetter = offerPreviewItemSetter;
        this.defaultItemsPageSize = defaultItemsPageSize;
    }

    ItemLookupService getItemLookupService() {
        return PluginInjectorBridge.get(ItemLookupService.class);
    }

    LocalItemsResponseBuilder getLocalItemsResponseBuilder() {
        LocalItemsResponseBuilder builder = localItemsResponseBuilder;
        if (builder != null) {
            return builder;
        }
        builder = getLocalItemsResponseBuilderFactoryService().create(
            new LocalItemsResponseBuilderPluginHooks(
                profileSelectionPresentationFacadeServiceSupplier,
                localAccountSessionServiceSupplier,
                localTradeSessionFacadeServiceSupplier,
                clientSupplier,
                hiddenItemPredicate,
                geLimitServiceSupplier,
                this::buildOfferStampFallback,
                this::buildOfferStatusFallback,
                this::buildPagedItemsResponse,
                this::emptyItemsResponse,
                System::currentTimeMillis
            )
        );
        localItemsResponseBuilder = builder;
        return builder;
    }

    LocalOfferPreviewBuilder getLocalOfferPreviewBuilder() {
        LocalOfferPreviewBuilder builder = localOfferPreviewBuilder;
        if (builder != null) {
            return builder;
        }
        builder = getLocalOfferPreviewBuilderFactoryService().create(
            new LocalOfferPreviewBuilderPluginHooks(
                ensureSelectedProfileLoaded,
                geLimitServiceSupplier,
                this::getItemLookupService,
                localItemEnrichmentServiceSupplier,
                profileSelectionPresentationFacadeServiceSupplier,
                localAccountSessionServiceSupplier,
                localTradeSessionFacadeServiceSupplier,
                ensureProfileLoaded,
                System::currentTimeMillis
            )
        );
        localOfferPreviewBuilder = builder;
        return builder;
    }

    OfferPreviewSyncService getOfferPreviewSyncService() {
        return PluginInjectorBridge.get(OfferPreviewSyncService.class);
    }


    private LocalItemsAssembler getLocalItemsAssembler() {
        LocalItemsAssembler assembler = localItemsAssembler;
        if (assembler != null) {
            return assembler;
        }
        assembler = getLocalItemsAssemblerFactoryService().create(
            new LocalItemsAssemblerPluginHooks(
                () -> itemManager != null,
                this::getItemLookupService,
                localItemEnrichmentServiceSupplier
            )
        );
        localItemsAssembler = assembler;
        return assembler;
    }

    private LocalItemsAssemblerFactoryService getLocalItemsAssemblerFactoryService() {
        LocalItemsAssemblerFactoryService service = localItemsAssemblerFactoryService;
        if (service != null) {
            return service;
        }
        service = new LocalItemsAssemblerFactoryService();
        localItemsAssemblerFactoryService = service;
        return service;
    }

    private LocalItemsResponseBuilderFactoryService getLocalItemsResponseBuilderFactoryService() {
        LocalItemsResponseBuilderFactoryService service = localItemsResponseBuilderFactoryService;
        if (service != null) {
            return service;
        }
        service = new LocalItemsResponseBuilderFactoryService(defaultItemsPageSize, getLocalItemsAssembler());
        localItemsResponseBuilderFactoryService = service;
        return service;
    }

    private LocalOfferPreviewBuilderFactoryService getLocalOfferPreviewBuilderFactoryService() {
        LocalOfferPreviewBuilderFactoryService service = localOfferPreviewBuilderFactoryService;
        if (service != null) {
            return service;
        }
        service = new LocalOfferPreviewBuilderFactoryService();
        localOfferPreviewBuilderFactoryService = service;
        return service;
    }


    private ApiClient.ItemsResponse buildOfferStampFallback() {
        GeLifecyclePanelDataRuntimeService service = panelDataRuntimeServiceSupplier != null
            ? panelDataRuntimeServiceSupplier.get()
            : null;
        return service != null ? service.buildOfferStampFallback() : null;
    }

    private ApiClient.ItemsResponse buildOfferStatusFallback() {
        GeLifecyclePanelDataRuntimeService service = panelDataRuntimeServiceSupplier != null
            ? panelDataRuntimeServiceSupplier.get()
            : null;
        return service != null ? service.buildOfferStatusFallback() : null;
    }

    private ApiClient.ItemsResponse buildPagedItemsResponse(List<FlipHubItem> items,
                                                            int page,
                                                            int pageSize,
                                                            int totalItems,
                                                            int totalPages,
                                                            long asOfMs,
                                                            Long priceCacheMs) {
        GeLifecyclePanelDataRuntimeService service = panelDataRuntimeServiceSupplier != null
            ? panelDataRuntimeServiceSupplier.get()
            : null;
        if (service == null) {
            return null;
        }
        return service.buildPagedItemsResponse(items, page, pageSize, totalItems, totalPages, asOfMs, priceCacheMs);
    }

    private ApiClient.ItemsResponse emptyItemsResponse(long asOfMs, Long priceCacheMs) {
        GeLifecyclePanelDataRuntimeService service = panelDataRuntimeServiceSupplier != null
            ? panelDataRuntimeServiceSupplier.get()
            : null;
        return service != null ? service.emptyItemsResponse(asOfMs, priceCacheMs) : null;
    }

    private FlipHubItem buildLocalOfferPreview(int itemId) {
        GeLifecyclePanelDataRuntimeService service = panelDataRuntimeServiceSupplier != null
            ? panelDataRuntimeServiceSupplier.get()
            : null;
        return service != null ? service.buildLocalOfferPreview(itemId) : null;
    }
}
