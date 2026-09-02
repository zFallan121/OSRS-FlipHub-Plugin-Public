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
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LocalItemsPagerTest {
    @Test
    public void completionSortsByTimestampDescThenName() {
        FlipHubItem a = item("Zamorak brew", 1000L, null);
        FlipHubItem b = item("Abyssal whip", 1000L, null);
        FlipHubItem c = item("Rune knife", 2000L, null);
        List<FlipHubItem> items = new ArrayList<>(Arrays.asList(a, b, c));

        LocalItemsPager.sortItems(items, StatsItemSort.COMPLETION, false);

        assertEquals("Rune knife", items.get(0).item_name);
        assertEquals("Abyssal whip", items.get(1).item_name);
        assertEquals("Zamorak brew", items.get(2).item_name);
    }

    @Test
    public void profitSortsByMarginTimesLimitDescending() {
        FlipHubItem thin = item("Mahogany logs", 1000L, null);
        thin.margin_x_limit = 13_000L;
        FlipHubItem fat = item("Abyssal whip", 2000L, null);
        fat.margin_x_limit = 400_000L;
        List<FlipHubItem> items = new ArrayList<>(Arrays.asList(thin, fat));

        LocalItemsPager.sortItems(items, StatsItemSort.PROFIT, false);

        assertEquals("Abyssal whip", items.get(0).item_name);
        assertEquals("Mahogany logs", items.get(1).item_name);
    }

    @Test
    public void roiSortAscendingPutsWorstReturnFirst() {
        FlipHubItem good = item("Abyssal whip", 1000L, null);
        good.roi_percent = 12.5d;
        FlipHubItem bad = item("Ring of wealth", 1000L, null);
        bad.roi_percent = -1.62d;
        List<FlipHubItem> items = new ArrayList<>(Arrays.asList(good, bad));

        LocalItemsPager.sortItems(items, StatsItemSort.ROI, true);

        assertEquals("Ring of wealth", items.get(0).item_name);
        assertEquals("Abyssal whip", items.get(1).item_name);
    }

    /** A catalogue hit with no margin yet is unranked, not the best row on an ascending sort. */
    @Test
    public void itemsWithoutTheSortedFigureSinkInBothDirections() {
        FlipHubItem ranked = item("Abyssal whip", 1000L, null);
        ranked.margin_x_limit = 400_000L;
        FlipHubItem unranked = item("Aaa untraded", 1000L, null);
        List<FlipHubItem> items = new ArrayList<>(Arrays.asList(unranked, ranked));

        LocalItemsPager.sortItems(items, StatsItemSort.PROFIT, false);
        assertEquals("Abyssal whip", items.get(0).item_name);

        LocalItemsPager.sortItems(items, StatsItemSort.PROFIT, true);
        assertEquals("Abyssal whip", items.get(0).item_name);
        assertEquals("Aaa untraded", items.get(1).item_name);
    }

    @Test
    public void paginateCalculatesPageBounds() {
        List<FlipHubItem> items = Arrays.asList(item("a", 1L, null), item("b", 2L, null), item("c", 3L, null));

        LocalItemsPager.Page page = LocalItemsPager.paginate(items, 2, 2);

        assertEquals(2, page.page);
        assertEquals(2, page.pageSize);
        assertEquals(3, page.totalItems);
        assertEquals(2, page.totalPages);
        assertEquals(1, page.pageItems.size());
        assertEquals("c", page.pageItems.get(0).item_name);
    }

    private static FlipHubItem item(String name, Long lastSellTs, Long lastBuyTs) {
        FlipHubItem item = new FlipHubItem();
        item.item_name = name;
        item.last_sell_ts_ms = lastSellTs;
        item.last_buy_ts_ms = lastBuyTs;
        return item;
    }
}
