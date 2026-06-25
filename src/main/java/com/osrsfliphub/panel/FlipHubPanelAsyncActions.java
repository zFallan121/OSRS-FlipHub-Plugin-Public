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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

final class FlipHubPanelAsyncActions {
    void setItemsAsync(
        FlipHubPanelStateService panelStateService,
        FlipHubPanelMutableState panelState,
        List<FlipHubItem> items,
        int page,
        int totalPages,
        long asOfMs,
        Long priceCacheMs,
        JLabel pageLabel,
        JButton prevButton,
        JButton nextButton,
        Runnable renderItemsAction
    ) {
        SwingUtilities.invokeLater(() -> panelStateService.setItems(
            panelState,
            items,
            page,
            totalPages,
            asOfMs,
            priceCacheMs,
            pageLabel,
            prevButton,
            nextButton,
            renderItemsAction
        ));
    }

    void setStatsDataAsync(
        FlipHubPanelStateService panelStateService,
        FlipHubPanelMutableState panelState,
        StatsSummary summary,
        List<StatsItem> items,
        Map<Integer, List<StatsFlipInstance>> historyByItem,
        Supplier<Integer> expandedStatsItemIdSupplier,
        Consumer<Integer> expandedStatsItemIdSetter,
        Set<Integer> expandedStatsHistoryItems,
        FlipHubStatsStateCoordinator statsStateCoordinator,
        Consumer<Long> updateStatsUpdatedLabelAction,
        Runnable updateStatsSummaryAction,
        Runnable renderStatsItemsAction,
        long asOfMs
    ) {
        SwingUtilities.invokeLater(() -> {
            Integer updatedExpandedId = panelStateService.setStatsData(
                panelState,
                summary,
                items,
                historyByItem,
                expandedStatsItemIdSupplier.get(),
                expandedStatsHistoryItems,
                statsStateCoordinator,
                updateStatsUpdatedLabelAction,
                updateStatsSummaryAction,
                renderStatsItemsAction,
                asOfMs
            );
            expandedStatsItemIdSetter.accept(updatedExpandedId);
        });
    }

    void setOfferPreviewAsync(
        FlipHubPanelStateService panelStateService,
        FlipHubPanelMutableState panelState,
        FlipHubItem item,
        long asOfMs,
        Long priceCacheMs,
        Runnable renderItemsAction
    ) {
        SwingUtilities.invokeLater(() -> panelStateService.setOfferPreview(
            panelState,
            item,
            asOfMs,
            priceCacheMs,
            renderItemsAction
        ));
    }

    void refreshBookmarksAsync(Runnable renderItemsAction) {
        SwingUtilities.invokeLater(renderItemsAction);
    }

    void setStatusMessageAsync(FlipHubProfileMenuCoordinator profileMenuCoordinator, String message) {
        SwingUtilities.invokeLater(() -> profileMenuCoordinator.setStatusMessage(message));
    }

    void setProfileHeaderAsync(FlipHubProfileMenuCoordinator profileMenuCoordinator, String label, boolean linked) {
        SwingUtilities.invokeLater(() -> profileMenuCoordinator.setProfileHeader(label, linked));
    }

    void setUploadDiagnosticsTooltipAsync(FlipHubProfileMenuCoordinator profileMenuCoordinator, String tooltip) {
        SwingUtilities.invokeLater(() -> profileMenuCoordinator.setUploadDiagnosticsTooltip(tooltip));
    }

    void setProfileOptionsAsync(
        FlipHubProfileMenuCoordinator profileMenuCoordinator,
        List<FlipHubProfileOption> options,
        String selectedKey
    ) {
        SwingUtilities.invokeLater(() -> profileMenuCoordinator.setProfileOptions(options, selectedKey));
    }
}
