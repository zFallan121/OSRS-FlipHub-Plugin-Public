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
import lombok.RequiredArgsConstructor;

/**
 * What the side panel is showing, and the changes its controls make to it.
 *
 * <p>This was two classes: the fields, and a stateless set of transitions that took the fields as
 * a parameter on every call, along with the listener to tell and the redraw to run afterwards. So
 * every control carried four things to say one. It now holds the listener and the redraws itself.
 */
@RequiredArgsConstructor
final class PanelState {
    private final PanelListener listener;
    private final Runnable renderItems;
    private final Runnable renderStatsItems;
    private final Runnable updateStatsSummary;

    int currentPage = 1;
    int totalPages = 1;
    List<FlipHubItem> lastItems;
    long lastAsOfMs;
    Long lastPriceCacheMs;
    FlipHubItem offerPreviewItem;
    long offerAsOfMs;
    Long offerPriceCacheMs;
    String searchQuery = "";
    boolean showBookmarkedOnly;
    StatsItemSort itemSort = StatsItemSort.COMPLETION;
    boolean itemSortAscending;
    StatsItemSort statsSort = StatsItemSort.COMPLETION;
    /** Which activities the item list shows. */
    StatsRecipeFilter statsRecipeFilter = StatsRecipeFilter.ALL;
    /** Which activities the total at the top of the tab adds up. */
    StatsRecipeFilter statsProfitFilter = StatsRecipeFilter.ALL;
    boolean statsSortAscending;
    int statsPage = 1;
    String statsSearchQuery = "";
    StatsSummary statsSummary;
    List<StatsItem> statsItems = new ArrayList<>();
    Map<Integer, List<StatsFlipInstance>> statsFlipHistoryByItem = new HashMap<>();

    // ---- the Activity tab ----

    void setBookmarkFilter(boolean enabled) {
        showBookmarkedOnly = enabled;
        currentPage = 1;
        listener.onBookmarkFilterChanged(enabled);
        renderItems.run();
    }

    /** Seeds the sort controls from the choice restored at start-up, before they are shown. */
    void restoreItemSort() {
        GeLifecyclePlugin plugin = Access.pluginOrNull();
        if (plugin != null) {
            itemSort = plugin.currentItemSort != null ? plugin.currentItemSort : StatsItemSort.COMPLETION;
            itemSortAscending = plugin.currentItemSortAscending;
        }
    }

    void setItemSort(StatsItemSort sort, boolean ascending) {
        itemSort = sort;
        itemSortAscending = ascending;
        currentPage = 1;
        listener.onItemSortChanged(sort, ascending);
    }

    void previousPage() {
        if (currentPage > 1) {
            listener.onPageChanged(currentPage - 1);
        }
    }

    void nextPage() {
        if (currentPage < totalPages) {
            listener.onPageChanged(currentPage + 1);
        }
    }

    void setSearchQuery(String text) {
        // The empty state has to know whether the list is empty because nothing was
        // traded or because nothing matched, and only the query can tell it apart.
        searchQuery = text != null ? text : "";
        listener.onSearchChanged(text);
    }

    void setOfferPreview(FlipHubItem item, long asOfMs, Long priceCacheMs) {
        boolean shown = item != null && item.item_id > 0;
        offerPreviewItem = shown ? item : null;
        offerAsOfMs = shown ? asOfMs : 0;
        offerPriceCacheMs = shown ? priceCacheMs : null;
        renderItems.run();
    }

    // ---- the Profile tab ----

    /** Draws the tab's list and its total, which is also how the tab first fills in. */
    void drawStats() {
        renderStatsItems.run();
        updateStatsSummary.run();
    }

    void setStatsRange(StatsRange range) {
        // A new range is a different set of items, so the page the user was on no longer refers to
        // anything. Data refreshes within a range deliberately keep their page instead.
        statsPage = 1;
        listener.onStatsRangeChanged(range);
    }

    /** Which activities the list shows. Nothing needs refetching to answer it. */
    void setStatsRecipeFilter(StatsRecipeFilter filter) {
        statsRecipeFilter = filter;
        // A different set of items means page 3 of the old list says nothing
        // about page 3 of the new one.
        restartStatsList();
    }

    /** Which activities the total at the top adds up. The list is unaffected. */
    void setStatsProfitFilter(StatsRecipeFilter filter) {
        statsProfitFilter = filter;
        updateStatsSummary.run();
    }

    void setStatsSort(StatsItemSort sort) {
        statsSort = sort;
        listener.onStatsSortChanged(sort);
        // Re-sorting reshuffles which items land on which page, so page 3 of the old order says
        // nothing about page 3 of the new one.
        restartStatsList();
    }

    void toggleStatsSortDirection() {
        statsSortAscending = !statsSortAscending;
        restartStatsList();
    }

    void setStatsSearchQuery(String query) {
        String normalizedQuery = query != null ? query.trim().toLowerCase(Locale.US) : "";
        if (!normalizedQuery.equals(statsSearchQuery)) {
            statsPage = 1;
        }
        statsSearchQuery = normalizedQuery;
        renderStatsItems.run();
    }

    void setStatsPage(int page) {
        if (page >= 1) {
            statsPage = page;
            renderStatsItems.run();
        }
    }

    private void restartStatsList() {
        statsPage = 1;
        renderStatsItems.run();
    }
}
