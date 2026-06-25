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

import static com.osrsfliphub.FlipHubPanelConstants.REFRESH_TIME_FORMATTER;

import java.awt.CardLayout;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;

final class FlipHubPanelStateService {
    void switchTab(
        boolean flippingSelected,
        FlipHubAgeTooltipCoordinator ageTooltipCoordinator,
        FlipHubUiStyler uiStyler,
        JToggleButton flippingTab,
        JToggleButton statsTab,
        CardLayout cardLayout,
        JPanel cardPanel,
        FlipHubPanelListener listener,
        JComboBox<StatsRange> statsRangeCombo
    ) {
        if (ageTooltipCoordinator != null) {
            ageTooltipCoordinator.clearHoverAndHide();
        }
        if (uiStyler != null) {
            uiStyler.styleTab(flippingTab, flippingSelected);
            uiStyler.styleTab(statsTab, !flippingSelected);
        }
        if (cardLayout != null && cardPanel != null) {
            cardLayout.show(cardPanel, flippingSelected ? "flipping" : "stats");
        }
        if (!flippingSelected && listener != null && statsRangeCombo != null) {
            StatsRange range = (StatsRange) statsRangeCombo.getSelectedItem();
            if (range != null) {
                listener.onStatsRangeChanged(range);
            }
        }
    }

    void onBookmarkFilterChanged(
        FlipHubPanelMutableState state,
        boolean enabled,
        FlipHubPanelListener listener,
        Runnable renderItems
    ) {
        if (state == null) {
            return;
        }
        state.showBookmarkedOnly = enabled;
        state.currentPage = 1;
        if (listener != null) {
            listener.onBookmarkFilterChanged(state.showBookmarkedOnly);
        }
        if (renderItems != null) {
            renderItems.run();
        }
    }

    void onPrevPageRequested(FlipHubPanelMutableState state, FlipHubPanelListener listener) {
        if (state != null && state.currentPage > 1 && listener != null) {
            listener.onPageChanged(state.currentPage - 1);
        }
    }

    void onNextPageRequested(FlipHubPanelMutableState state, FlipHubPanelListener listener) {
        if (state != null && state.currentPage < state.totalPages && listener != null) {
            listener.onPageChanged(state.currentPage + 1);
        }
    }

    void onStatsRangeSelectionChanged(FlipHubPanelListener listener, StatsRange range) {
        if (listener != null && range != null) {
            listener.onStatsRangeChanged(range);
        }
    }

    void onStatsSortSelectionChanged(
        FlipHubPanelListener listener,
        StatsItemSort sort,
        Runnable renderStatsItems
    ) {
        if (listener != null && sort != null) {
            listener.onStatsSortChanged(sort);
        }
        if (renderStatsItems != null) {
            renderStatsItems.run();
        }
    }

    void onStatsSortDirectionToggled(FlipHubPanelMutableState state, Runnable renderStatsItems) {
        if (state == null) {
            return;
        }
        state.statsSortAscending = !state.statsSortAscending;
        if (renderStatsItems != null) {
            renderStatsItems.run();
        }
    }

    void onStatsSearchQueryChanged(FlipHubPanelMutableState state, String query, Runnable renderStatsItems) {
        if (state == null) {
            return;
        }
        state.statsSearchQuery = query != null ? query.trim().toLowerCase(Locale.US) : "";
        if (renderStatsItems != null) {
            renderStatsItems.run();
        }
    }

    void setItems(
        FlipHubPanelMutableState state,
        List<FlipHubItem> items,
        int page,
        int totalPages,
        long asOfMs,
        Long priceCacheMs,
        JLabel pageLabel,
        JButton prevButton,
        JButton nextButton,
        Runnable renderItems
    ) {
        if (state == null) {
            return;
        }
        state.lastItems = items;
        state.lastAsOfMs = asOfMs;
        state.lastPriceCacheMs = priceCacheMs;
        state.currentPage = page;
        state.totalPages = totalPages <= 0 ? 1 : totalPages;

        if (pageLabel != null) {
            pageLabel.setText("Page " + page + " of " + state.totalPages);
        }
        if (prevButton != null) {
            prevButton.setEnabled(page > 1);
        }
        if (nextButton != null) {
            nextButton.setEnabled(page < state.totalPages);
        }
        if (renderItems != null) {
            renderItems.run();
        }
    }

    Integer setStatsData(
        FlipHubPanelMutableState state,
        StatsSummary summary,
        List<StatsItem> items,
        Map<Integer, List<StatsFlipInstance>> historyByItem,
        Integer expandedStatsItemId,
        Set<Integer> expandedStatsHistoryItems,
        FlipHubStatsStateCoordinator statsStateCoordinator,
        Consumer<Long> updateStatsUpdatedLabelAction,
        Runnable updateStatsSummaryAction,
        Runnable renderStatsItemsAction,
        long asOfMs
    ) {
        if (state == null || statsStateCoordinator == null) {
            return expandedStatsItemId;
        }
        state.statsSummary = summary;
        FlipHubStatsStateCoordinator.Result normalizedState = statsStateCoordinator.normalize(
            summary,
            items,
            historyByItem,
            expandedStatsItemId,
            expandedStatsHistoryItems
        );
        state.statsItems = normalizedState.statsItems;
        state.statsFlipHistoryByItem = normalizedState.flipHistoryByItem;

        if (updateStatsUpdatedLabelAction != null) {
            updateStatsUpdatedLabelAction.accept(asOfMs);
        }
        if (updateStatsSummaryAction != null) {
            updateStatsSummaryAction.run();
        }
        if (renderStatsItemsAction != null) {
            renderStatsItemsAction.run();
        }
        return normalizedState.expandedStatsItemId;
    }

    void setOfferPreview(
        FlipHubPanelMutableState state,
        FlipHubItem item,
        long asOfMs,
        Long priceCacheMs,
        Runnable renderItems
    ) {
        if (state == null) {
            return;
        }
        if (item == null || item.item_id <= 0) {
            state.offerPreviewItem = null;
            state.offerAsOfMs = 0;
            state.offerPriceCacheMs = null;
        } else {
            state.offerPreviewItem = item;
            state.offerAsOfMs = asOfMs;
            state.offerPriceCacheMs = priceCacheMs;
        }
        if (renderItems != null) {
            renderItems.run();
        }
    }

    void updateStatsUpdatedLabel(JLabel statsUpdatedLabel, long asOfMs) {
        if (statsUpdatedLabel != null && asOfMs > 0) {
            statsUpdatedLabel.setText(buildRefreshText(asOfMs, null));
        }
    }

    String buildRefreshText(long asOfMs, Long priceCacheMs) {
        String asOf = REFRESH_TIME_FORMATTER.format(Instant.ofEpochMilli(asOfMs));
        if (priceCacheMs != null) {
            String cache = REFRESH_TIME_FORMATTER.format(Instant.ofEpochMilli(priceCacheMs));
            return "Updated: " + asOf + " (Prices: " + cache + ")";
        }
        return "Updated: " + asOf;
    }

    void hookSearchListener(
        FlipHubSearchCoordinator searchCoordinator,
        JTextField searchField,
        FlipHubPanelListener listener
    ) {
        if (searchCoordinator == null || searchField == null) {
            return;
        }
        searchCoordinator.hookSearchListener(
            searchField,
            () -> {
                if (listener != null) {
                    listener.onSearchChanged(searchField.getText());
                }
            }
        );
    }
}

