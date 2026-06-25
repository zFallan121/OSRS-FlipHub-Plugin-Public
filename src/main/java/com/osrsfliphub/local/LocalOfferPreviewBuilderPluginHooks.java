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
import java.util.Map;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

final class LocalOfferPreviewBuilderPluginHooks implements LocalOfferPreviewBuilderFactoryService.RuntimeHooks {
    private final Runnable ensureSelectedProfileLoaded;
    private final Supplier<GeLimitService> geLimitServiceSupplier;
    private final Supplier<ItemLookupService> itemLookupServiceSupplier;
    private final Supplier<LocalItemEnrichmentService> localItemEnrichmentServiceSupplier;
    private final Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier;
    private final Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier;
    private final Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier;
    private final LongConsumer ensureProfileLoaded;
    private final LongSupplier nowMs;

    LocalOfferPreviewBuilderPluginHooks(
        Runnable ensureSelectedProfileLoaded,
        Supplier<GeLimitService> geLimitServiceSupplier,
        Supplier<ItemLookupService> itemLookupServiceSupplier,
        Supplier<LocalItemEnrichmentService> localItemEnrichmentServiceSupplier,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier,
        Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier,
        Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier,
        LongConsumer ensureProfileLoaded,
        LongSupplier nowMs
    ) {
        this.ensureSelectedProfileLoaded = ensureSelectedProfileLoaded;
        this.geLimitServiceSupplier = geLimitServiceSupplier;
        this.itemLookupServiceSupplier = itemLookupServiceSupplier;
        this.localItemEnrichmentServiceSupplier = localItemEnrichmentServiceSupplier;
        this.profileSelectionFacadeServiceSupplier = profileSelectionFacadeServiceSupplier;
        this.localAccountSessionServiceSupplier = localAccountSessionServiceSupplier;
        this.localTradeSessionFacadeServiceSupplier = localTradeSessionFacadeServiceSupplier;
        this.ensureProfileLoaded = ensureProfileLoaded;
        this.nowMs = nowMs;
    }

    @Override
    public void ensureSelectedProfileLoaded() {
        if (ensureSelectedProfileLoaded != null) {
            ensureSelectedProfileLoaded.run();
        }
    }

    @Override
    public void requestGeLimit(int itemId) {
        if (itemId <= 0) {
            return;
        }
        GeLimitService service = geLimitServiceSupplier != null ? geLimitServiceSupplier.get() : null;
        if (service != null) {
            service.requestGeLimits(Collections.singleton(itemId));
        }
    }

    @Override
    public String getItemName(int itemId) {
        ItemLookupService service = itemLookupServiceSupplier != null ? itemLookupServiceSupplier.get() : null;
        return service != null ? service.lookupItemNameSafe(itemId) : null;
    }

    @Override
    public void applyGuidePrices(FlipHubItem item, int itemId) {
        LocalItemEnrichmentService service = localItemEnrichmentServiceSupplier != null
            ? localItemEnrichmentServiceSupplier.get()
            : null;
        if (service != null) {
            service.applyGuidePrices(item, itemId, false);
        }
    }

    @Override
    public long resolveSelectedProfileKey() {
        ProfileSelectionPresentationFacadeService service = profileSelectionFacadeServiceSupplier != null
            ? profileSelectionFacadeServiceSupplier.get()
            : null;
        return service != null ? service.resolveSelectedProfileKey() : 0L;
    }

    @Override
    public long resolveLimitAccountKey(long fallbackAccountKey) {
        LocalAccountSessionService service = localAccountSessionServiceSupplier != null
            ? localAccountSessionServiceSupplier.get()
            : null;
        return service != null ? service.resolveLimitAccountKey(fallbackAccountKey) : fallbackAccountKey;
    }

    @Override
    public Map<Integer, LocalTradeInfo> buildLocalTradeInfo(long accountKey) {
        LocalTradeSessionFacadeService service = localTradeSessionFacadeServiceSupplier != null
            ? localTradeSessionFacadeServiceSupplier.get()
            : null;
        return service != null ? service.buildLocalTradeInfo(accountKey) : null;
    }

    @Override
    public void applyLocalTradeInfo(FlipHubItem item, LocalTradeInfo info) {
        LocalItemEnrichmentService service = localItemEnrichmentServiceSupplier != null
            ? localItemEnrichmentServiceSupplier.get()
            : null;
        if (service != null) {
            service.applyLocalTradeInfo(item, info);
        }
    }

    @Override
    public void ensureProfileLoaded(long accountKey) {
        if (ensureProfileLoaded != null) {
            ensureProfileLoaded.accept(accountKey);
        }
    }

    @Override
    public Map<Integer, LocalLimitInfo> buildLocalLimitInfo(long accountKey, long atMs) {
        LocalTradeSessionFacadeService service = localTradeSessionFacadeServiceSupplier != null
            ? localTradeSessionFacadeServiceSupplier.get()
            : null;
        return service != null ? service.buildLocalLimitInfo(accountKey, atMs) : null;
    }

    @Override
    public void applyLocalLimitInfo(FlipHubItem item, int itemId, LocalLimitInfo info) {
        LocalItemEnrichmentService service = localItemEnrichmentServiceSupplier != null
            ? localItemEnrichmentServiceSupplier.get()
            : null;
        if (service != null) {
            service.applyLocalLimitInfo(item, itemId, info);
        }
    }

    @Override
    public Integer lookupGeLimit(int itemId) {
        ItemLookupService service = itemLookupServiceSupplier != null ? itemLookupServiceSupplier.get() : null;
        return service != null ? service.lookupGeLimitSafe(itemId) : null;
    }

    @Override
    public void applyMarginInfo(FlipHubItem item) {
        LocalItemEnrichmentService service = localItemEnrichmentServiceSupplier != null
            ? localItemEnrichmentServiceSupplier.get()
            : null;
        if (service != null) {
            service.applyMarginInfo(item);
        }
    }

    @Override
    public long nowMs() {
        return nowMs != null ? nowMs.getAsLong() : System.currentTimeMillis();
    }
}
