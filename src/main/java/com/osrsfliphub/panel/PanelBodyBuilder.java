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

import javax.swing.*;
import lombok.RequiredArgsConstructor;

final class PanelBodyBuilder {
    @RequiredArgsConstructor
    static final class BuildResult {
        final JPanel panel;
        final JPanel footerPanel;
        final JScrollPane statsScrollPane;
        final JLabel totalProfitValue;
        final JLabel roiValue;
        final JLabel flipsValue;
        final JLabel taxValue;
        final JLabel sessionTimeValue;
        final JLabel hourlyValue;
    }

    BuildResult build(JPanel cardPanel,
                      FlippingPanelBuilder flippingPanelBuilder,
                      StatsPanelBuilder statsPanelBuilder,
                      JTextField searchField,
                      JButton bookmarkFilterButton,
                      JComboBox<StatsItemSort> itemSortCombo,
                      JButton itemSortDirectionButton,
                      JLabel refreshLabel,
                      JButton profileButton,
                      JPanel listPanel,
                      JScrollPane scrollPane,
                      JButton prevButton,
                      JButton nextButton,
                      JLabel pageLabel,
                      JComboBox<StatsRange> statsRangeCombo,
                      JTextField statsSearchField,
                      JLabel statsUpdatedLabel,
                      JPanel statsContentPanel,
                      JPanel statsItemsListPanel,
                      JComboBox<StatsItemSort> statsSortCombo,
        JComboBox<StatsRecipeFilter> statsFilterCombo,
                      JButton statsSortDirectionButton) {
        cardPanel.setOpaque(false);

        FlippingPanelBuilder.BuildResult flipping = flippingPanelBuilder.build(
            searchField,
            bookmarkFilterButton,
            itemSortCombo,
            itemSortDirectionButton,
            refreshLabel,
            profileButton,
            listPanel,
            scrollPane,
            prevButton,
            nextButton,
            pageLabel
        );
        StatsPanelBuilder.BuildResult stats = statsPanelBuilder.build(
            statsRangeCombo,
            statsSearchField,
            statsUpdatedLabel,
            statsContentPanel,
            statsItemsListPanel,
            statsSortCombo,
            statsFilterCombo,
            statsSortDirectionButton
        );

        cardPanel.add(flipping.panel, "flipping");
        cardPanel.add(stats.panel, "stats");

        return new BuildResult(
            cardPanel,
            flipping.footerPanel,
            stats.scrollPane,
            stats.totalProfitValue,
            stats.roiValue,
            stats.flipsValue,
            stats.taxValue,
            stats.sessionTimeValue,
            stats.hourlyValue
        );
    }
}
