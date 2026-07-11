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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.MouseWheelListener;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.border.Border;

final class FlipHubStatsPanelContentBuilder {
    static final class ContentResult {
        final JScrollPane scrollPane;
        final JLabel totalProfitValue;
        final JLabel roiValue;
        final JLabel flipsValue;
        final JLabel taxValue;
        final JLabel sessionTimeValue;
        final JLabel hourlyValue;

        ContentResult(
            JScrollPane scrollPane,
            JLabel totalProfitValue,
            JLabel roiValue,
            JLabel flipsValue,
            JLabel taxValue,
            JLabel sessionTimeValue,
            JLabel hourlyValue
        ) {
            this.scrollPane = scrollPane;
            this.totalProfitValue = totalProfitValue;
            this.roiValue = roiValue;
            this.flipsValue = flipsValue;
            this.taxValue = taxValue;
            this.sessionTimeValue = sessionTimeValue;
            this.hourlyValue = hourlyValue;
        }
    }

    private final FlipHubUiStyler uiStyler;
    private final FlipHubPanelStateService panelStateService;
    private final FlipHubPanelMutableState panelState;
    private final FlipHubPanelListener listener;
    private final Runnable renderStatsItems;
    private final Runnable updateStatsSummary;
    private final FlipHubWheelScrollCoordinator wheelScrollCoordinator;
    private final MouseWheelListener wheelForwarder;

    FlipHubStatsPanelContentBuilder(FlipHubUiStyler uiStyler,
                                    FlipHubPanelStateService panelStateService,
                                    FlipHubPanelMutableState panelState,
                                    FlipHubPanelListener listener,
                                    Runnable renderStatsItems,
                                    Runnable updateStatsSummary,
                                    FlipHubWheelScrollCoordinator wheelScrollCoordinator,
                                    MouseWheelListener wheelForwarder) {
        this.uiStyler = uiStyler;
        this.panelStateService = panelStateService;
        this.panelState = panelState;
        this.listener = listener;
        this.renderStatsItems = renderStatsItems;
        this.updateStatsSummary = updateStatsSummary;
        this.wheelScrollCoordinator = wheelScrollCoordinator;
        this.wheelForwarder = wheelForwarder;
    }

    ContentResult buildContent(
        JPanel statsContentPanel,
        JPanel statsItemsListPanel,
        JComboBox<StatsItemSort> statsSortCombo,
        JButton statsSortDirectionButton
    ) {
        statsContentPanel.setBackground(BG_ALT);
        statsContentPanel.setLayout(new BoxLayout(statsContentPanel, BoxLayout.Y_AXIS));
        statsContentPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);

        JLabel statsTotalProfitValue = new JLabel("0 gp");
        JPanel totalBlock = buildStatsBlock("Total Profit", statsTotalProfitValue, WARNING);
        statsContentPanel.add(totalBlock);
        statsContentPanel.add(Box.createVerticalStrut(6));

        JLabel statsRoiValue = new JLabel("0.00%");
        JLabel statsFlipsValue = new JLabel("0");
        JLabel statsTaxValue = new JLabel("0 gp");
        JLabel statsSessionTimeValue = new JLabel("00:00:00");
        JLabel statsHourlyValue = new JLabel("0 gp/hr");

        Object[][] rows = new Object[][]{
            {"ROI", statsRoiValue, TEXT},
            {"Total Flips Made", statsFlipsValue, TEXT},
            {"Tax paid", statsTaxValue, TEXT},
            {"Session Time", statsSessionTimeValue, TEXT},
            {"Hourly Profit", statsHourlyValue, SUCCESS}
        };
        for (Object[] row : rows) {
            statsContentPanel.add(buildStatsRow((String) row[0], (JLabel) row[1], (Color) row[2]));
        }

        statsContentPanel.add(Box.createVerticalStrut(10));
        statsContentPanel.add(buildStatsSortRow(statsSortCombo, statsSortDirectionButton));
        statsContentPanel.add(Box.createVerticalStrut(6));

        statsItemsListPanel.setBackground(BG_ALT);
        statsItemsListPanel.setLayout(new BoxLayout(statsItemsListPanel, BoxLayout.Y_AXIS));
        statsItemsListPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        statsContentPanel.add(statsItemsListPanel);

        JScrollPane statsScrollPane = new JScrollPane(statsContentPanel);
        statsScrollPane.setBorder(BorderFactory.createEmptyBorder());
        statsScrollPane.getViewport().setBackground(BG_ALT);
        statsScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        statsScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        statsScrollPane.setWheelScrollingEnabled(true);
        if (wheelForwarder != null) {
            statsScrollPane.addMouseWheelListener(wheelForwarder);
            statsScrollPane.getViewport().addMouseWheelListener(wheelForwarder);
        }
        JScrollBar statsBar = statsScrollPane.getVerticalScrollBar();
        statsBar.setUnitIncrement(SCROLL_UNIT_INCREMENT);
        statsBar.setBlockIncrement(SCROLL_BLOCK_INCREMENT);

        if (wheelScrollCoordinator != null) {
            wheelScrollCoordinator.installWheelForwarder(statsContentPanel);
        }
        if (renderStatsItems != null) {
            renderStatsItems.run();
        }
        if (updateStatsSummary != null) {
            updateStatsSummary.run();
        }

        return new ContentResult(
            statsScrollPane,
            statsTotalProfitValue,
            statsRoiValue,
            statsFlipsValue,
            statsTaxValue,
            statsSessionTimeValue,
            statsHourlyValue
        );
    }

    private JPanel buildStatsSortRow(JComboBox<StatsItemSort> statsSortCombo, JButton statsSortDirectionButton) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(BG_ALT);
        row.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.setPreferredSize(new Dimension(0, 28));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setBackground(BG_ALT);
        JLabel sortLabel = new JLabel("Sort");
        sortLabel.setForeground(MUTED);
        sortLabel.setFont(font(10.5f));
        if (uiStyler != null) {
            uiStyler.styleComboBox(statsSortCombo);
        }
        statsSortCombo.setFont(font(10.5f));
        statsSortCombo.setBorder(roundedBorder(INPUT_ARC, SOFT_BORDER, new Insets(2, 6, 2, 6)));
        Dimension sortSize = statsSortCombo.getPreferredSize();
        statsSortCombo.setPreferredSize(new Dimension(sortSize.width, 24));
        statsSortCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        if (statsSortCombo.getSelectedItem() == null) {
            statsSortCombo.setSelectedItem(StatsItemSort.COMPLETION);
        }
        statsSortCombo.addActionListener(e -> {
            StatsItemSort sort = (StatsItemSort) statsSortCombo.getSelectedItem();
            if (panelStateService != null && sort != null) {
                panelStateService.onStatsSortSelectionChanged(listener, sort, renderStatsItems);
            }
        });
        statsSortDirectionButton.setFocusPainted(false);
        statsSortDirectionButton.setBorder(roundedBorder(INPUT_ARC, SOFT_BORDER, new Insets(2, 8, 2, 8)));
        statsSortDirectionButton.setBackground(BG_ALT);
        statsSortDirectionButton.setOpaque(true);
        statsSortDirectionButton.setFont(fontSemiBold(11.5f));
        statsSortDirectionButton.setPreferredSize(new Dimension(34, 24));
        statsSortDirectionButton.setMaximumSize(new Dimension(34, 24));
        statsSortDirectionButton.addActionListener(e -> {
            if (panelStateService != null) {
                panelStateService.onStatsSortDirectionToggled(panelState, renderStatsItems);
                updateStatsSortDirectionButton(statsSortDirectionButton,
                    panelState != null && panelState.statsSortAscending);
            }
        });
        updateStatsSortDirectionButton(statsSortDirectionButton,
            panelState != null && panelState.statsSortAscending);
        left.add(sortLabel);
        left.add(statsSortCombo);
        left.add(statsSortDirectionButton);
        row.add(left, BorderLayout.WEST);
        return row;
    }

    private void updateStatsSortDirectionButton(JButton statsSortDirectionButton, boolean ascending) {
        statsSortDirectionButton.setText(ascending ? "\u2191" : "\u2193");
        statsSortDirectionButton.setForeground(ascending ? WARNING : MUTED);
        statsSortDirectionButton.setToolTipText(ascending ? "Ascending order" : "Descending order");
    }

    private JPanel buildStatsBlock(String label, JLabel valueView, Color valueColor) {
        JPanel block = new RoundedPanel(CARD_ARC, CARD, SOFT_BORDER);
        block.setLayout(new BorderLayout());
        block.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        block.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        block.setPreferredSize(new Dimension(0, 64));
        block.setAlignmentX(JPanel.LEFT_ALIGNMENT);

        JLabel labelView = new JLabel(label);
        labelView.setForeground(MUTED);
        labelView.setFont(font(10.5f));

        valueView.setForeground(valueColor);
        valueView.setFont(fontBold(17f));
        valueView.setAlignmentX(JPanel.LEFT_ALIGNMENT);

        block.add(labelView, BorderLayout.NORTH);
        block.add(valueView, BorderLayout.CENTER);
        return block;
    }

    private JPanel buildStatsRow(String label, JLabel valueView, Color valueColor) {
        JPanel row = new RoundedPanel(CARD_ARC, CARD_ALT, SOFT_BORDER);
        row.setLayout(new BorderLayout());
        row.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        row.setAlignmentX(JPanel.LEFT_ALIGNMENT);

        JLabel labelView = new JLabel(label);
        labelView.setForeground(MUTED);
        labelView.setFont(font(10.5f));

        valueView.setHorizontalAlignment(SwingConstants.RIGHT);
        valueView.setForeground(valueColor);
        valueView.setFont(fontSemiBold(12f));

        row.add(labelView, BorderLayout.WEST);
        row.add(valueView, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        row.setPreferredSize(new Dimension(0, 32));
        return row;
    }

    private Font font(float size) {
        return uiStyler.font(size);
    }

    private Font fontBold(float size) {
        return uiStyler.fontBold(size);
    }

    private Font fontSemiBold(float size) {
        return uiStyler.fontSemiBold(size);
    }

    private Border roundedBorder(int arc, Color color, Insets padding) {
        return uiStyler.roundedBorder(arc, color, padding);
    }
}
