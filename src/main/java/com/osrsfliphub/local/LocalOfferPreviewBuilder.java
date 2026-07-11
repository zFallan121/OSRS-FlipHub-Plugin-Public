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
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class LocalOfferPreviewBuilder {
    private static final long GE_LIMIT_WINDOW_MS = 4L * 60L * 60L * 1000L;

    @Inject
    LocalOfferPreviewBuilder() {
    }

    private LocalItemEnrichmentService enrichment() {
        return PluginInjectorBridge.get(LocalItemEnrichmentService.class);
    }

    private LocalTradeSessionFacadeService tradeSession() {
        return PluginInjectorBridge.get(LocalTradeSessionFacadeService.class);
    }

    private ItemLookupService itemLookup() {
        return PluginInjectorBridge.get(ItemLookupService.class);
    }

    FlipHubItem build(int itemId) {
        if (itemId <= 0) {
            return null;
        }

        PluginAccess.plugin().getProfileWorkflowService().ensureSelectedProfileLoaded();
        GeLimitService geLimitService = PluginInjectorBridge.get(GeLimitService.class);
        if (geLimitService != null) {
            geLimitService.requestGeLimits(Collections.singleton(itemId));
        }

        FlipHubItem item = new FlipHubItem();
        item.item_id = itemId;
        ItemLookupService itemLookup = itemLookup();
        String itemName = itemLookup != null ? itemLookup.lookupItemNameSafe(itemId) : null;
        if (itemName != null && !itemName.trim().isEmpty()) {
            item.item_name = itemName;
        }
        LocalItemEnrichmentService enrichment = enrichment();
        if (enrichment != null) {
            enrichment.applyGuidePrices(item, itemId, false);
        }

        ProfileSelectionPresentationFacadeService selection =
            PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class);
        long tradeAccountKey = selection != null ? selection.resolveSelectedProfileKey() : 0L;
        LocalAccountSessionService session = PluginInjectorBridge.get(LocalAccountSessionService.class);
        long limitAccountKey = session != null ? session.resolveLimitAccountKey(tradeAccountKey) : tradeAccountKey;
        long nowMs = System.currentTimeMillis();

        LocalLimitInfo limitInfoForItem = null;
        if (tradeAccountKey >= 0) {
            LocalTradeSessionFacadeService tradeSession = tradeSession();
            Map<Integer, LocalTradeInfo> tradeInfo =
                tradeSession != null ? tradeSession.buildLocalTradeInfo(tradeAccountKey) : null;
            if (enrichment != null) {
                enrichment.applyLocalTradeInfo(item, tradeInfo != null ? tradeInfo.get(itemId) : null);
            }
        }
        if (limitAccountKey >= 0) {
            PluginAccess.plugin().getLocalTradesRuntimeService().ensureProfileLoaded(limitAccountKey);
            LocalTradeSessionFacadeService tradeSession = tradeSession();
            Map<Integer, LocalLimitInfo> limitInfo =
                tradeSession != null ? tradeSession.buildLocalLimitInfo(limitAccountKey, nowMs) : null;
            limitInfoForItem = limitInfo != null ? limitInfo.get(itemId) : null;
            if (enrichment != null) {
                enrichment.applyLocalLimitInfo(item, itemId, limitInfoForItem);
            }
        }

        if (item.ge_limit_total == null || item.ge_limit_total <= 0) {
            Integer geLimit = itemLookup != null ? itemLookup.lookupGeLimitSafe(itemId) : null;
            if (geLimit != null && geLimit > 0) {
                item.ge_limit_total = geLimit;
                if (limitInfoForItem != null && limitInfoForItem.buyQty > 0) {
                    int remaining = (int) Math.max(0L, geLimit - limitInfoForItem.buyQty);
                    item.ge_limit_remaining = remaining;
                    if (limitInfoForItem.firstBuyTs != null) {
                        long resetAt = limitInfoForItem.firstBuyTs + GE_LIMIT_WINDOW_MS;
                        long remainingMs = Math.max(0L, resetAt - nowMs);
                        item.ge_limit_reset_ms = remainingMs;
                    } else {
                        item.ge_limit_reset_ms = 0L;
                    }
                } else {
                    item.ge_limit_remaining = geLimit;
                    item.ge_limit_reset_ms = 0L;
                }
            }
        }

        if (enrichment != null) {
            enrichment.applyMarginInfo(item);
        }
        return item;
    }
}
