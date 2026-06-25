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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.GrandExchangeOffer;

final class LocalItemsResponseBuilderFactoryService {
    interface RuntimeHooks {
        long resolveSelectedProfileKey();
        long resolveLimitAccountKey(long tradeAccountKey);
        Map<Integer, LocalTradeInfo> buildLocalTradeInfo(long accountKey);
        Map<Integer, LocalLimitInfo> buildLocalLimitInfo(long accountKey, long nowMs);
        GrandExchangeOffer[] getGrandExchangeOffers();
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
    private final LocalItemsAssembler localItemsAssembler;

    LocalItemsResponseBuilderFactoryService(int defaultItemsPageSize, LocalItemsAssembler localItemsAssembler) {
        this.defaultItemsPageSize = Math.max(1, defaultItemsPageSize);
        this.localItemsAssembler = localItemsAssembler;
    }

    LocalItemsResponseBuilder create(RuntimeHooks runtimeHooks) {
        if (runtimeHooks == null) {
            return new LocalItemsResponseBuilder(defaultItemsPageSize, null);
        }
        return new LocalItemsResponseBuilder(
            defaultItemsPageSize,
            new LocalItemsResponseBuilder.Hooks() {
                @Override
                public long resolveSelectedProfileKey() {
                    return runtimeHooks.resolveSelectedProfileKey();
                }

                @Override
                public long resolveLimitAccountKey(long tradeAccountKey) {
                    return runtimeHooks.resolveLimitAccountKey(tradeAccountKey);
                }

                @Override
                public Map<Integer, LocalTradeInfo> buildLocalTradeInfo(long accountKey) {
                    return runtimeHooks.buildLocalTradeInfo(accountKey);
                }

                @Override
                public Map<Integer, LocalLimitInfo> buildLocalLimitInfo(long accountKey, long nowMs) {
                    return runtimeHooks.buildLocalLimitInfo(accountKey, nowMs);
                }

                @Override
                public GrandExchangeOffer[] getGrandExchangeOffers() {
                    return runtimeHooks.getGrandExchangeOffers();
                }

                @Override
                public LocalItemsAssembler.Result assemble(GrandExchangeOffer[] offers,
                                                           Map<Integer, LocalTradeInfo> tradeInfo,
                                                           Map<Integer, LocalLimitInfo> limitInfo,
                                                           String currentQuery,
                                                           boolean bookmarkFilterEnabled,
                                                           Set<Integer> bookmarkedItems) {
                    if (localItemsAssembler == null) {
                        return new LocalItemsAssembler.Result(new ArrayList<>(), new HashSet<>());
                    }
                    return localItemsAssembler.assemble(
                        offers,
                        tradeInfo,
                        limitInfo,
                        currentQuery,
                        bookmarkFilterEnabled,
                        bookmarkedItems
                    );
                }

                @Override
                public boolean isHidden(int itemId) {
                    return runtimeHooks.isHidden(itemId);
                }

                @Override
                public void requestGeLimits(Set<Integer> itemIds) {
                    runtimeHooks.requestGeLimits(itemIds);
                }

                @Override
                public ApiClient.ItemsResponse buildOfferStampFallback() {
                    return runtimeHooks.buildOfferStampFallback();
                }

                @Override
                public ApiClient.ItemsResponse buildOfferStatusFallback() {
                    return runtimeHooks.buildOfferStatusFallback();
                }

                @Override
                public ApiClient.ItemsResponse buildPagedItemsResponse(List<FlipHubItem> items,
                                                                       int page,
                                                                       int pageSize,
                                                                       int totalItems,
                                                                       int totalPages,
                                                                       long asOfMs,
                                                                       Long priceCacheMs) {
                    return runtimeHooks.buildPagedItemsResponse(
                        items,
                        page,
                        pageSize,
                        totalItems,
                        totalPages,
                        asOfMs,
                        priceCacheMs
                    );
                }

                @Override
                public ApiClient.ItemsResponse emptyItemsResponse(long asOfMs, Long priceCacheMs) {
                    return runtimeHooks.emptyItemsResponse(asOfMs, priceCacheMs);
                }

                @Override
                public long nowMs() {
                    return runtimeHooks.nowMs();
                }
            }
        );
    }
}
