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

import static com.osrsfliphub.FlipHubPanelConstants.*;

import java.awt.BorderLayout;
import java.awt.event.MouseWheelListener;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

final class FlipHubStatsPanelBuilder {
    static final class BuildResult {
        final JPanel panel;
        final JScrollPane scrollPane;
        final JLabel totalProfitValue;
        final JLabel roiValue;
        final JLabel flipsValue;
        final JLabel taxValue;
        final JLabel sessionTimeValue;
        final JLabel hourlyValue;

        BuildResult(JPanel panel,
                    JScrollPane scrollPane,
                    JLabel totalProfitValue,
                    JLabel roiValue,
                    JLabel flipsValue,
                    JLabel taxValue,
                    JLabel sessionTimeValue,
                    JLabel hourlyValue) {
            this.panel = panel;
            this.scrollPane = scrollPane;
            this.totalProfitValue = totalProfitValue;
            this.roiValue = roiValue;
            this.flipsValue = flipsValue;
            this.taxValue = taxValue;
            this.sessionTimeValue = sessionTimeValue;
            this.hourlyValue = hourlyValue;
        }
    }

    private final FlipHubStatsPanelHeaderBuilder headerBuilder;
    private final FlipHubStatsPanelContentBuilder contentBuilder;

    FlipHubStatsPanelBuilder(FlipHubUiStyler uiStyler,
                             FlipHubPanelStateService panelStateService,
                             FlipHubPanelMutableState panelState,
                             FlipHubPanelListener listener,
                             Runnable renderStatsItems,
                             Runnable updateStatsSummary,
                             FlipHubWheelScrollCoordinator wheelScrollCoordinator,
                             MouseWheelListener wheelForwarder) {
        this.headerBuilder = new FlipHubStatsPanelHeaderBuilder(
            uiStyler, panelStateService, panelState, listener, renderStatsItems);
        this.contentBuilder = new FlipHubStatsPanelContentBuilder(
            uiStyler, panelStateService, panelState, listener,
            renderStatsItems, updateStatsSummary, wheelScrollCoordinator, wheelForwarder);
    }

    BuildResult build(
        JComboBox<StatsRange> statsRangeCombo,
        JTextField statsSearchField,
        JButton statsClearButton,
        JLabel statsUpdatedLabel,
        JPanel statsContentPanel,
        JPanel statsItemsListPanel,
        JComboBox<StatsItemSort> statsSortCombo,
        JButton statsSortDirectionButton
    ) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG);

        JPanel header = headerBuilder.buildHeader(statsRangeCombo, statsSearchField, statsClearButton, statsUpdatedLabel);
        FlipHubStatsPanelContentBuilder.ContentResult content = contentBuilder.buildContent(
            statsContentPanel,
            statsItemsListPanel,
            statsSortCombo,
            statsSortDirectionButton
        );

        panel.add(header, BorderLayout.NORTH);
        panel.add(content.scrollPane, BorderLayout.CENTER);
        return new BuildResult(
            panel,
            content.scrollPane,
            content.totalProfitValue,
            content.roiValue,
            content.flipsValue,
            content.taxValue,
            content.sessionTimeValue,
            content.hourlyValue
        );
    }
}
