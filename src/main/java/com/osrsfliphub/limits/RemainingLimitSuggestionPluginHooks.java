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

import java.util.Map;
import java.util.Set;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

final class RemainingLimitSuggestionPluginHooks implements RemainingLimitSuggestionFactoryService.RuntimeHooks {
    private final Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier;
    private final Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier;
    private final LongConsumer ensureProfileLoaded;
    private final Supplier<GeLimitService> geLimitServiceSupplier;
    private final Supplier<ItemLookupService> itemLookupServiceSupplier;
    private final Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier;
    private final Supplier<FlipHubItem> offerPreviewItemSupplier;
    private final LongSupplier nowMs;

    RemainingLimitSuggestionPluginHooks(
        Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier,
        LongConsumer ensureProfileLoaded,
        Supplier<GeLimitService> geLimitServiceSupplier,
        Supplier<ItemLookupService> itemLookupServiceSupplier,
        Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier,
        Supplier<FlipHubItem> offerPreviewItemSupplier,
        LongSupplier nowMs
    ) {
        this.localAccountSessionServiceSupplier = localAccountSessionServiceSupplier;
        this.profileSelectionFacadeServiceSupplier = profileSelectionFacadeServiceSupplier;
        this.ensureProfileLoaded = ensureProfileLoaded;
        this.geLimitServiceSupplier = geLimitServiceSupplier;
        this.itemLookupServiceSupplier = itemLookupServiceSupplier;
        this.localTradeSessionFacadeServiceSupplier = localTradeSessionFacadeServiceSupplier;
        this.offerPreviewItemSupplier = offerPreviewItemSupplier;
        this.nowMs = nowMs;
    }

    @Override
    public long resolveLocalAccountKey() {
        LocalAccountSessionService service = localAccountSessionServiceSupplier != null
            ? localAccountSessionServiceSupplier.get()
            : null;
        return service != null ? service.resolveLocalAccountKey() : 0L;
    }

    @Override
    public long resolveSelectedProfileKey() {
        ProfileSelectionPresentationFacadeService service = profileSelectionFacadeServiceSupplier != null
            ? profileSelectionFacadeServiceSupplier.get()
            : null;
        return service != null ? service.resolveSelectedProfileKey() : 0L;
    }

    @Override
    public void ensureProfileLoaded(long accountKey) {
        if (ensureProfileLoaded != null) {
            ensureProfileLoaded.accept(accountKey);
        }
    }

    @Override
    public void requestGeLimits(Set<Integer> itemIds) {
        GeLimitService service = geLimitServiceSupplier != null ? geLimitServiceSupplier.get() : null;
        if (service != null) {
            service.requestGeLimits(itemIds);
        }
    }

    @Override
    public Integer getCachedGeLimit(int itemId) {
        GeLimitService service = geLimitServiceSupplier != null ? geLimitServiceSupplier.get() : null;
        return service != null ? service.getCachedGeLimit(itemId) : null;
    }

    @Override
    public Integer lookupGeLimitSafe(int itemId) {
        ItemLookupService service = itemLookupServiceSupplier != null ? itemLookupServiceSupplier.get() : null;
        return service != null ? service.lookupGeLimitSafe(itemId) : null;
    }

    @Override
    public Map<Integer, LocalLimitInfo> buildLocalLimitInfo(long accountKey, long atMs) {
        LocalTradeSessionFacadeService service = localTradeSessionFacadeServiceSupplier != null
            ? localTradeSessionFacadeServiceSupplier.get()
            : null;
        return service != null ? service.buildLocalLimitInfo(accountKey, atMs) : null;
    }

    @Override
    public FlipHubItem getOfferPreviewItem() {
        return offerPreviewItemSupplier != null ? offerPreviewItemSupplier.get() : null;
    }

    @Override
    public long nowMs() {
        return nowMs != null ? nowMs.getAsLong() : 0L;
    }
}
