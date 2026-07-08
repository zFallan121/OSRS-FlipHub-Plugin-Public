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

import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class OfferPreviewSyncService {
    @Inject
    OfferPreviewSyncService() {
    }

    private Integer getOfferPreviewItemId() {
        return PluginAccess.plugin().offerPreviewItemId;
    }

    private FlipHubItem getOfferPreviewItem() {
        return PluginAccess.plugin().offerPreviewItem;
    }

    private void setOfferPreviewItemId(Integer itemId) {
        PluginAccess.plugin().offerPreviewItemId = itemId;
    }

    private void setOfferPreviewItem(FlipHubItem item) {
        PluginAccess.plugin().offerPreviewItem = item;
    }

    private FlipHubItem buildLocalOfferPreview(int itemId) {
        return PluginInjectorBridge.get(GeLifecyclePanelDataRuntimeService.class).buildLocalOfferPreview(itemId);
    }

    private void setPanelOfferPreview(FlipHubItem item, long asOfMs, Long priceCacheMs) {
        FlipHubPanel panel = PluginAccess.plugin().panel;
        if (panel != null) {
            panel.setOfferPreview(item, asOfMs, priceCacheMs);
        }
    }

    private void markSuggestionDirty() {
        ChatboxSuggestionRuntimeStateService service =
            PluginInjectorBridge.get(ChatboxSuggestionRuntimeStateService.class);
        if (service != null) {
            service.markSuggestionDirty();
        }
    }

    private void scheduleRefreshSoon() {
        GeLifecyclePlugin plugin = PluginAccess.plugin();
        PanelRefreshCoordinator coordinator = plugin.getPanelRefreshCoordinator();
        if (coordinator != null) {
            coordinator.scheduleRefreshSoon(plugin.scheduler);
        }
    }

    void clearPreview() {
        if (getOfferPreviewItemId() == null) {
            return;
        }
        setOfferPreviewItemId(null);
        setOfferPreviewItem(null);
        setPanelOfferPreview(null, 0L, null);
        // Returning from offer setup should show the same prices the setup view just used.
        scheduleRefreshSoon();
    }

    boolean setPreviewItem(int itemId) {
        if (itemId <= 0) {
            return false;
        }
        Integer currentItemId = getOfferPreviewItemId();
        FlipHubItem currentItem = getOfferPreviewItem();
        if (currentItemId != null && currentItemId == itemId && currentItem != null) {
            updateLocalPreview(itemId);
            return true;
        }
        setOfferPreviewItemId(itemId);
        updateLocalPreview(itemId);
        return true;
    }

    private void updateLocalPreview(int itemId) {
        FlipHubItem previous = getOfferPreviewItem();
        FlipHubItem next = buildLocalOfferPreview(itemId);
        boolean changed = !isOfferPreviewEquivalent(previous, next);
        boolean pricesChanged = !isOfferPreviewPricesEquivalent(previous, next);

        setOfferPreviewItem(next);
        if (changed) {
            markSuggestionDirty();
            setPanelOfferPreview(next, System.currentTimeMillis(), null);
        }
        // Keep Activity cards in sync with offer-setup prices when they change.
        if (pricesChanged) {
            scheduleRefreshSoon();
        }
    }

    static boolean isOfferPreviewEquivalent(FlipHubItem previous, FlipHubItem next) {
        if (previous == next) {
            return true;
        }
        if (previous == null || next == null) {
            return false;
        }
        return previous.item_id == next.item_id
            && Objects.equals(previous.instabuy_price, next.instabuy_price)
            && Objects.equals(previous.instasell_price, next.instasell_price)
            && Objects.equals(previous.instabuy_ts_ms, next.instabuy_ts_ms)
            && Objects.equals(previous.instasell_ts_ms, next.instasell_ts_ms)
            && Objects.equals(previous.last_buy_price, next.last_buy_price)
            && Objects.equals(previous.last_sell_price, next.last_sell_price)
            && Objects.equals(previous.last_buy_ts_ms, next.last_buy_ts_ms)
            && Objects.equals(previous.last_sell_ts_ms, next.last_sell_ts_ms)
            && Objects.equals(previous.margin, next.margin)
            && Objects.equals(previous.margin_x_limit, next.margin_x_limit)
            && Objects.equals(previous.roi_percent, next.roi_percent)
            && Objects.equals(previous.ge_limit_total, next.ge_limit_total)
            && Objects.equals(previous.ge_limit_remaining, next.ge_limit_remaining)
            && Objects.equals(previous.ge_limit_reset_ms, next.ge_limit_reset_ms);
    }

    static boolean isOfferPreviewPricesEquivalent(FlipHubItem previous, FlipHubItem next) {
        if (previous == next) {
            return true;
        }
        if (previous == null || next == null) {
            return false;
        }
        return previous.item_id == next.item_id
            && Objects.equals(previous.instabuy_price, next.instabuy_price)
            && Objects.equals(previous.instasell_price, next.instasell_price)
            && Objects.equals(previous.instabuy_ts_ms, next.instabuy_ts_ms)
            && Objects.equals(previous.instasell_ts_ms, next.instasell_ts_ms)
            && Objects.equals(previous.last_buy_price, next.last_buy_price)
            && Objects.equals(previous.last_sell_price, next.last_sell_price)
            && Objects.equals(previous.last_buy_ts_ms, next.last_buy_ts_ms)
            && Objects.equals(previous.last_sell_ts_ms, next.last_sell_ts_ms);
    }
}
