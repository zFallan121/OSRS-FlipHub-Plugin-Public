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


final class FlipHubStatsItemFormattingService {
    private final FlipHubPanelValueFormatService valueFormatService;

    FlipHubStatsItemFormattingService(FlipHubPanelValueFormatService valueFormatService) {
        this.valueFormatService = valueFormatService;
    }

    String buildStatsItemMeta(StatsItem item) {
        String roi = valueFormatService.formatPercent(item.roi_percent);
        int flips = item.fill_count != null ? item.fill_count : 0;
        int qty = item.total_qty != null ? item.total_qty : 0;
        return "ROI " + roi + " | Flips " + flips + " | Qty " + qty;
    }

    /**
     * The collapsed card shares its second line with the profit, so the meta shown there is cut
     * down to what fits: quantity is dropped and the flip count is abbreviated. Both are still in
     * the full meta on the card tooltip and in the expanded rows.
     */
    String buildStatsItemMetaShort(StatsItem item) {
        String roi = valueFormatService.formatPercent(item.roi_percent);
        int flips = item.fill_count != null ? item.fill_count : 0;
        return "ROI " + roi + " | x" + flips;
    }

    /**
     * How long a flip of this item takes, from the money going out to the sale completing.
     */
    String formatStatsTimeToComplete(StatsItem item) {
        if (item == null || item.active_ms == null || item.active_ms <= 0L) {
            return "N/A";
        }
        int flips = item.fill_count != null ? item.fill_count : 0;
        if (flips <= 0) {
            return "N/A";
        }
        return valueFormatService.formatDurationCompact(item.active_ms / flips);
    }

    String formatStatsAvgBuy(StatsItem item) {
        if (item == null) {
            return "N/A";
        }
        int qty = item.total_qty != null ? item.total_qty : 0;
        long cost = item.total_cost_gp != null ? item.total_cost_gp : 0L;
        if (qty <= 0 || cost <= 0) {
            return "N/A";
        }
        long avgBuy = Math.max(0L, cost / qty);
        return valueFormatService.formatGp(avgBuy);
    }

    String formatStatsAvgSell(StatsItem item) {
        if (item == null) {
            return "N/A";
        }
        int qty = item.total_qty != null ? item.total_qty : 0;
        long cost = item.total_cost_gp != null ? item.total_cost_gp : 0L;
        long profit = item.total_profit_gp != null ? item.total_profit_gp : 0L;
        long revenue = cost + profit;
        if (qty <= 0 || revenue <= 0) {
            return "N/A";
        }
        long avgSell = Math.max(0L, revenue / qty);
        return valueFormatService.formatGp(avgSell);
    }
}
