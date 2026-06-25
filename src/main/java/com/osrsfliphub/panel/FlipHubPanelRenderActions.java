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

import java.util.Set;
import java.util.function.BiFunction;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

final class FlipHubPanelRenderActions {
    void updateStatsUpdatedLabel(
        FlipHubPanelStateService panelStateService,
        JLabel statsUpdatedLabel,
        long asOfMs
    ) {
        panelStateService.updateStatsUpdatedLabel(statsUpdatedLabel, asOfMs);
    }

    void renderItems(
        FlipHubItemsRenderCoordinator itemsRenderCoordinator,
        JPanel listPanel,
        FlipHubAgeTooltipCoordinator ageTooltipCoordinator,
        FlipHubItemListContentRenderer itemListContentRenderer,
        FlipHubPanelMutableState panelState,
        JLabel refreshLabel,
        FlipHubPanelStateService panelStateService,
        JPanel footerPanel,
        JScrollPane scrollPane
    ) {
        itemsRenderCoordinator.renderItems(
            listPanel,
            ageTooltipCoordinator,
            itemListContentRenderer,
            panelState.offerPreviewItem,
            panelState.offerAsOfMs,
            panelState.lastItems,
            panelState.lastAsOfMs,
            panelState.showBookmarkedOnly,
            refreshLabel,
            panelState.lastPriceCacheMs,
            panelState.offerPriceCacheMs,
            panelStateService::buildRefreshText,
            footerPanel,
            scrollPane
        );
    }

    void updateStatsSummary(
        FlipHubStatsRenderCoordinator statsRenderCoordinator,
        FlipHubPanelMutableState panelState,
        FlipHubPanelValueFormatService valueFormatService,
        JLabel statsTotalProfitValue,
        JLabel statsRoiValue,
        JLabel statsFlipsValue,
        JLabel statsTaxValue,
        JLabel statsSessionTimeValue,
        JLabel statsHourlyValue
    ) {
        statsRenderCoordinator.updateSummary(
            panelState.statsSummary,
            valueFormatService,
            statsTotalProfitValue,
            statsRoiValue,
            statsFlipsValue,
            statsTaxValue,
            statsSessionTimeValue,
            statsHourlyValue
        );
    }

    void renderStatsItems(
        FlipHubStatsRenderCoordinator statsRenderCoordinator,
        JPanel statsItemsListPanel,
        FlipHubPanelMutableState panelState,
        StatsItemSort sort,
        FlipHubStatsItemCardBuilder statsItemCardBuilder,
        BiFunction<String, String, JPanel> cardBuilder
    ) {
        statsRenderCoordinator.renderItems(
            statsItemsListPanel,
            panelState.statsItems,
            panelState.statsSearchQuery,
            sort,
            panelState.statsSortAscending,
            statsItemCardBuilder::buildStatsItemCard,
            cardBuilder
        );
    }

    Integer toggleStatsItemExpanded(
        FlipHubStatsRenderCoordinator statsRenderCoordinator,
        Integer expandedStatsItemId,
        Set<Integer> expandedStatsHistoryItems,
        int itemId
    ) {
        return statsRenderCoordinator.toggleItemExpanded(
            expandedStatsItemId,
            expandedStatsHistoryItems,
            itemId
        );
    }

    void toggleStatsHistoryExpanded(
        FlipHubStatsRenderCoordinator statsRenderCoordinator,
        Set<Integer> expandedStatsHistoryItems,
        int itemId
    ) {
        statsRenderCoordinator.toggleHistoryExpanded(expandedStatsHistoryItems, itemId);
    }
}
