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

final class LocalItemEnrichmentService {
    interface Hooks {
        Integer getCachedGeLimit(int itemId);
        WikiPriceEntry getWikiPriceEntry(int itemId, boolean allowRefresh);
        long nowMs();
    }

    private final Hooks hooks;
    private final long localLimitWindowMs;

    LocalItemEnrichmentService(Hooks hooks, long localLimitWindowMs) {
        this.hooks = hooks;
        this.localLimitWindowMs = localLimitWindowMs;
    }

    void applyGuidePrices(FlipHubItem item, int itemId, boolean allowRefresh) {
        if (item == null || itemId <= 0 || hooks == null) {
            return;
        }
        WikiPriceEntry entry = hooks.getWikiPriceEntry(itemId, allowRefresh);
        if (entry == null) {
            return;
        }
        if (entry.high != null && entry.high > 0) {
            item.instasell_price = entry.high;
        }
        if (entry.low != null && entry.low > 0) {
            item.instabuy_price = entry.low;
        }
        if (entry.highTime != null && entry.highTime > 0) {
            item.instasell_ts_ms = entry.highTime * 1000L;
        }
        if (entry.lowTime != null && entry.lowTime > 0) {
            item.instabuy_ts_ms = entry.lowTime * 1000L;
        }
    }

    void applyLocalTradeInfo(FlipHubItem item, LocalTradeInfo info) {
        if (item == null || info == null) {
            return;
        }
        if (info.lastBuyPrice != null && info.lastBuyPrice > 0) {
            item.last_buy_price = info.lastBuyPrice;
            item.last_buy_ts_ms = info.lastBuyTs;
        }
        if (info.lastSellPrice != null && info.lastSellPrice > 0) {
            item.last_sell_price = info.lastSellPrice;
            item.last_sell_ts_ms = info.lastSellTs;
        }
    }

    void applyLocalLimitInfo(FlipHubItem item, int itemId, LocalLimitInfo info) {
        if (item == null || itemId <= 0 || hooks == null) {
            return;
        }
        Integer geLimit = hooks.getCachedGeLimit(itemId);
        if (geLimit == null || geLimit <= 0) {
            return;
        }

        item.ge_limit_total = geLimit;
        if (info != null && info.buyQty > 0) {
            int remaining = (int) Math.max(0L, geLimit - info.buyQty);
            item.ge_limit_remaining = remaining;
            if (info.firstBuyTs != null) {
                long resetAt = info.firstBuyTs + localLimitWindowMs;
                item.ge_limit_reset_ms = Math.max(0L, resetAt - hooks.nowMs());
            }
            else {
                item.ge_limit_reset_ms = 0L;
            }
        }
        else {
            item.ge_limit_remaining = geLimit;
            item.ge_limit_reset_ms = 0L;
        }
    }

    void applyMarginInfo(FlipHubItem item) {
        if (item == null) {
            return;
        }
        Integer buy = null;
        Integer sell = null;
        boolean hasLastBuy = item.last_buy_price != null && item.last_buy_price > 0;
        boolean hasLastSell = item.last_sell_price != null && item.last_sell_price > 0;
        boolean hasInstaBuy = item.instabuy_price != null && item.instabuy_price > 0;
        boolean hasInstaSell = item.instasell_price != null && item.instasell_price > 0;
        if (hasInstaBuy && hasInstaSell) {
            buy = item.instabuy_price;
            sell = item.instasell_price;
        } else if (hasLastBuy && hasLastSell) {
            buy = item.last_buy_price;
            sell = item.last_sell_price;
        } else if (hasLastSell && hasInstaBuy) {
            buy = item.instabuy_price;
            sell = item.last_sell_price;
        } else if (hasLastBuy && hasInstaSell) {
            buy = item.last_buy_price;
            sell = item.instasell_price;
        }
        if (buy == null || sell == null) {
            return;
        }
        // Keep 2dp precision for post-tax margin (2% tax) so margin x limit is accurate.
        long marginHundredths = ((long) sell * 100L) - ((long) buy * 100L) - ((long) sell * 2L);
        int margin = (int) (marginHundredths / 100L);
        item.margin = margin;
        // Align ROI with recorded flip-history math by applying per-item GE tax floor.
        int sellTaxPerItem = sell / 50;
        long realizedMarginPerItem = ((long) sell - sellTaxPerItem) - buy;
        item.roi_percent = buy > 0 ? (realizedMarginPerItem * 100.0) / buy : null;
        if (item.ge_limit_remaining != null) {
            item.margin_x_limit = (marginHundredths * (long) item.ge_limit_remaining) / 100L;
        } else if (item.ge_limit_total != null) {
            item.margin_x_limit = (marginHundredths * (long) item.ge_limit_total) / 100L;
        }
    }
}
