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

import java.util.*;

final class ItemsPager {
    static final class Page {
        final int page;
        final int pageSize;
        final int totalItems;
        final int totalPages;
        final List<FlipHubItem> pageItems;

        Page(int page, int pageSize, int totalItems, int totalPages, List<FlipHubItem> pageItems) {
            this.page = page;
            this.pageSize = pageSize;
            this.totalItems = totalItems;
            this.totalPages = totalPages;
            this.pageItems = pageItems != null ? pageItems : Collections.emptyList();
        }
    }

    private ItemsPager() {
    }

    /**
     * Orders the list on one of the three figures the cards show. Descending is the useful
     * direction for all three - the most recent flip, the fattest margin, the best return - so
     * that is what the arrow points at until it is turned around.
     */
    static void sortItems(List<FlipHubItem> items, StatsItemSort sort, boolean ascending) {
        if (items == null || items.size() <= 1) {
            return;
        }
        StatsItemSort activeSort = sort != null ? sort : StatsItemSort.COMPLETION;
        Comparator<FlipHubItem> byFigure = Comparator.comparingDouble(item -> {
            Double figure = figureOf(item, activeSort);
            return figure != null ? figure : 0d;
        });
        Comparator<FlipHubItem> comparator = Comparator
            // An item we have no figure for yet is not the worst one, it is unranked: it sits
            // below everything ranked whichever way the arrow points, rather than winning the
            // ascending order by being empty.
            .comparingInt((FlipHubItem item) -> figureOf(item, activeSort) != null ? 0 : 1)
            .thenComparing(ascending ? byFigure : byFigure.reversed())
            // The name breaks ties the same way in both directions, so a screen of items that
            // share a figure reads A-Z rather than mirroring when the arrow flips.
            .thenComparing(item -> item.item_name != null ? item.item_name : "",
                String.CASE_INSENSITIVE_ORDER);
        items.sort(Comparator.nullsLast(comparator));
    }

    private static Double figureOf(FlipHubItem item, StatsItemSort sort) {
        switch (sort) {
            case PROFIT:
                return item.margin_x_limit != null ? (double) item.margin_x_limit : null;
            case ROI:
                return item.roi_percent;
            case COMPLETION:
            default:
                long lastTradeMs = Math.max(
                    item.last_sell_ts_ms != null ? item.last_sell_ts_ms : 0L,
                    item.last_buy_ts_ms != null ? item.last_buy_ts_ms : 0L
                );
                return lastTradeMs > 0 ? (double) lastTradeMs : null;
        }
    }

    static Page paginate(List<FlipHubItem> items, int currentPage, int pageSize) {
        int safePageSize = pageSize > 0 ? pageSize : 1;
        int totalItems = items != null ? items.size() : 0;
        int totalPages = totalItems == 0 ? 1 : (int) Math.ceil(totalItems / (double) safePageSize);
        int page = Math.max(1, Math.min(currentPage, totalPages));
        int start = (page - 1) * safePageSize;
        int end = Math.min(start + safePageSize, totalItems);
        List<FlipHubItem> pageItems = (items == null || start >= end) ? Collections.emptyList() : items.subList(start, end);
        return new Page(page, safePageSize, totalItems, totalPages, pageItems);
    }
}
