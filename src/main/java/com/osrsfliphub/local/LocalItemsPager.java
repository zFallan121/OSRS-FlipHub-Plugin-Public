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
import java.util.List;

final class LocalItemsPager {
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

    private LocalItemsPager() {
    }

    static void sortByRecentTradeThenName(List<FlipHubItem> items) {
        if (items == null || items.size() <= 1) {
            return;
        }
        items.sort((a, b) -> {
            long aTs = Math.max(
                a != null && a.last_sell_ts_ms != null ? a.last_sell_ts_ms : 0L,
                a != null && a.last_buy_ts_ms != null ? a.last_buy_ts_ms : 0L
            );
            long bTs = Math.max(
                b != null && b.last_sell_ts_ms != null ? b.last_sell_ts_ms : 0L,
                b != null && b.last_buy_ts_ms != null ? b.last_buy_ts_ms : 0L
            );
            int tsCompare = Long.compare(bTs, aTs);
            if (tsCompare != 0) {
                return tsCompare;
            }
            String aName = a != null && a.item_name != null ? a.item_name : "";
            String bName = b != null && b.item_name != null ? b.item_name : "";
            return aName.compareToIgnoreCase(bName);
        });
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
