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

final class LocalOfferPreviewBuilder {
    interface Hooks {
        void ensureSelectedProfileLoaded();
        void requestGeLimit(int itemId);
        String getItemName(int itemId);
        void applyGuidePrices(FlipHubItem item, int itemId);
        long resolveSelectedProfileKey();
        long resolveLimitAccountKey(long fallbackAccountKey);
        Map<Integer, LocalTradeInfo> buildLocalTradeInfo(long accountKey);
        void applyLocalTradeInfo(FlipHubItem item, LocalTradeInfo info);
        void ensureProfileLoaded(long accountKey);
        Map<Integer, LocalLimitInfo> buildLocalLimitInfo(long accountKey, long nowMs);
        void applyLocalLimitInfo(FlipHubItem item, int itemId, LocalLimitInfo info);
        Integer lookupGeLimit(int itemId);
        void applyMarginInfo(FlipHubItem item);
        long nowMs();
    }

    private static final long GE_LIMIT_WINDOW_MS = 4L * 60L * 60L * 1000L;
    private final Hooks hooks;

    LocalOfferPreviewBuilder(Hooks hooks) {
        this.hooks = hooks;
    }

    FlipHubItem build(int itemId) {
        if (hooks == null || itemId <= 0) {
            return null;
        }

        hooks.ensureSelectedProfileLoaded();
        hooks.requestGeLimit(itemId);

        FlipHubItem item = new FlipHubItem();
        item.item_id = itemId;
        String itemName = hooks.getItemName(itemId);
        if (itemName != null && !itemName.trim().isEmpty()) {
            item.item_name = itemName;
        }
        hooks.applyGuidePrices(item, itemId);

        long tradeAccountKey = hooks.resolveSelectedProfileKey();
        long limitAccountKey = hooks.resolveLimitAccountKey(tradeAccountKey);
        long nowMs = hooks.nowMs();

        LocalLimitInfo limitInfoForItem = null;
        if (tradeAccountKey >= 0) {
            Map<Integer, LocalTradeInfo> tradeInfo = hooks.buildLocalTradeInfo(tradeAccountKey);
            hooks.applyLocalTradeInfo(item, tradeInfo != null ? tradeInfo.get(itemId) : null);
        }
        if (limitAccountKey >= 0) {
            hooks.ensureProfileLoaded(limitAccountKey);
            Map<Integer, LocalLimitInfo> limitInfo = hooks.buildLocalLimitInfo(limitAccountKey, nowMs);
            limitInfoForItem = limitInfo != null ? limitInfo.get(itemId) : null;
            hooks.applyLocalLimitInfo(item, itemId, limitInfoForItem);
        }

        if (item.ge_limit_total == null || item.ge_limit_total <= 0) {
            Integer geLimit = hooks.lookupGeLimit(itemId);
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

        hooks.applyMarginInfo(item);
        return item;
    }
}
