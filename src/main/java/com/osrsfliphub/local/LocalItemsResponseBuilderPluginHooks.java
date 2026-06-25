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
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;

final class LocalItemsResponseBuilderPluginHooks implements LocalItemsResponseBuilderFactoryService.RuntimeHooks {
    @FunctionalInterface
    interface PagedItemsResponseBuilder {
        ApiClient.ItemsResponse build(List<FlipHubItem> items,
                                      int page,
                                      int pageSize,
                                      int totalItems,
                                      int totalPages,
                                      long asOfMs,
                                      Long priceCacheMs);
    }

    @FunctionalInterface
    interface EmptyItemsResponseBuilder {
        ApiClient.ItemsResponse build(long asOfMs, Long priceCacheMs);
    }

    private final Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier;
    private final Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier;
    private final Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier;
    private final Supplier<Client> clientSupplier;
    private final IntPredicate hiddenItemPredicate;
    private final Supplier<GeLimitService> geLimitServiceSupplier;
    private final Supplier<ApiClient.ItemsResponse> offerStampFallbackSupplier;
    private final Supplier<ApiClient.ItemsResponse> offerStatusFallbackSupplier;
    private final PagedItemsResponseBuilder pagedItemsResponseBuilder;
    private final EmptyItemsResponseBuilder emptyItemsResponseBuilder;
    private final LongSupplier nowMs;

    LocalItemsResponseBuilderPluginHooks(
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier,
        Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier,
        Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier,
        Supplier<Client> clientSupplier,
        IntPredicate hiddenItemPredicate,
        Supplier<GeLimitService> geLimitServiceSupplier,
        Supplier<ApiClient.ItemsResponse> offerStampFallbackSupplier,
        Supplier<ApiClient.ItemsResponse> offerStatusFallbackSupplier,
        PagedItemsResponseBuilder pagedItemsResponseBuilder,
        EmptyItemsResponseBuilder emptyItemsResponseBuilder,
        LongSupplier nowMs
    ) {
        this.profileSelectionFacadeServiceSupplier = profileSelectionFacadeServiceSupplier;
        this.localAccountSessionServiceSupplier = localAccountSessionServiceSupplier;
        this.localTradeSessionFacadeServiceSupplier = localTradeSessionFacadeServiceSupplier;
        this.clientSupplier = clientSupplier;
        this.hiddenItemPredicate = hiddenItemPredicate;
        this.geLimitServiceSupplier = geLimitServiceSupplier;
        this.offerStampFallbackSupplier = offerStampFallbackSupplier;
        this.offerStatusFallbackSupplier = offerStatusFallbackSupplier;
        this.pagedItemsResponseBuilder = pagedItemsResponseBuilder;
        this.emptyItemsResponseBuilder = emptyItemsResponseBuilder;
        this.nowMs = nowMs;
    }

    @Override
    public long resolveSelectedProfileKey() {
        ProfileSelectionPresentationFacadeService service = profileSelectionFacadeServiceSupplier != null
            ? profileSelectionFacadeServiceSupplier.get()
            : null;
        return service != null ? service.resolveSelectedProfileKey() : 0L;
    }

    @Override
    public long resolveLimitAccountKey(long tradeAccountKey) {
        LocalAccountSessionService service = localAccountSessionServiceSupplier != null
            ? localAccountSessionServiceSupplier.get()
            : null;
        return service != null ? service.resolveLimitAccountKey(tradeAccountKey) : tradeAccountKey;
    }

    @Override
    public Map<Integer, LocalTradeInfo> buildLocalTradeInfo(long accountKey) {
        LocalTradeSessionFacadeService service = localTradeSessionFacadeServiceSupplier != null
            ? localTradeSessionFacadeServiceSupplier.get()
            : null;
        return service != null ? service.buildLocalTradeInfo(accountKey) : null;
    }

    @Override
    public Map<Integer, LocalLimitInfo> buildLocalLimitInfo(long accountKey, long atMs) {
        LocalTradeSessionFacadeService service = localTradeSessionFacadeServiceSupplier != null
            ? localTradeSessionFacadeServiceSupplier.get()
            : null;
        return service != null ? service.buildLocalLimitInfo(accountKey, atMs) : null;
    }

    @Override
    public GrandExchangeOffer[] getGrandExchangeOffers() {
        Client client = clientSupplier != null ? clientSupplier.get() : null;
        if (client == null) {
            return null;
        }
        GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
        return offers != null && offers.length > 0 ? offers : new GrandExchangeOffer[0];
    }

    @Override
    public boolean isHidden(int itemId) {
        return hiddenItemPredicate != null && hiddenItemPredicate.test(itemId);
    }

    @Override
    public void requestGeLimits(Set<Integer> itemIds) {
        GeLimitService service = geLimitServiceSupplier != null ? geLimitServiceSupplier.get() : null;
        if (service != null) {
            service.requestGeLimits(itemIds);
        }
    }

    @Override
    public ApiClient.ItemsResponse buildOfferStampFallback() {
        return offerStampFallbackSupplier != null ? offerStampFallbackSupplier.get() : null;
    }

    @Override
    public ApiClient.ItemsResponse buildOfferStatusFallback() {
        return offerStatusFallbackSupplier != null ? offerStatusFallbackSupplier.get() : null;
    }

    @Override
    public ApiClient.ItemsResponse buildPagedItemsResponse(List<FlipHubItem> items,
                                                           int page,
                                                           int pageSize,
                                                           int totalItems,
                                                           int totalPages,
                                                           long asOfMs,
                                                           Long priceCacheMs) {
        return pagedItemsResponseBuilder != null
            ? pagedItemsResponseBuilder.build(items, page, pageSize, totalItems, totalPages, asOfMs, priceCacheMs)
            : null;
    }

    @Override
    public ApiClient.ItemsResponse emptyItemsResponse(long asOfMs, Long priceCacheMs) {
        return emptyItemsResponseBuilder != null ? emptyItemsResponseBuilder.build(asOfMs, priceCacheMs) : null;
    }

    @Override
    public long nowMs() {
        return nowMs != null ? nowMs.getAsLong() : System.currentTimeMillis();
    }
}
