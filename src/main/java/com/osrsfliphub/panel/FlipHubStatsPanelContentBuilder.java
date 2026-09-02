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
import javax.swing.JViewport;
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
        statsContentPanel.setOpaque(false);
        statsContentPanel.setLayout(new BoxLayout(statsContentPanel, BoxLayout.Y_AXIS));
        statsContentPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);

        JLabel statsTotalProfitValue = new JLabel("0 gp");
        JLabel statsRoiValue = new JLabel("0.00%");
        JLabel statsFlipsValue = new JLabel("0");
        JLabel statsTaxValue = new JLabel("0 gp");
        JLabel statsSessionTimeValue = new JLabel("00:00:00");
        JLabel statsHourlyValue = new JLabel("0 gp/hr");

        // The tab is one profile asked one question, so the profit takes the size and the five
        // figures it is made of become the line under it - inside ONE card, ruled by hairlines.
        // A card each would be six boxes enumerating a single answer, which is the outline that
        // STYLEGUIDE.md section 3 says has to be earned.
        Object[][] rows = new Object[][]{
            {"ROI", statsRoiValue, TEXT},
            {"Total flips made", statsFlipsValue, TEXT},
            {"Tax paid", statsTaxValue, TEXT},
            {"Session time", statsSessionTimeValue, TEXT},
            {"Hourly profit", statsHourlyValue, SUCCESS}
        };
        statsContentPanel.add(buildSummaryCard(statsTotalProfitValue, rows));

        statsContentPanel.add(Box.createVerticalStrut(12));
        statsContentPanel.add(buildStatsSortRow(statsSortCombo, statsSortDirectionButton));
        statsContentPanel.add(Box.createVerticalStrut(8));

        statsItemsListPanel.setOpaque(false);
        statsItemsListPanel.setLayout(new BoxLayout(statsItemsListPanel, BoxLayout.Y_AXIS));
        statsItemsListPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        statsContentPanel.add(statsItemsListPanel);

        JScrollPane statsScrollPane = new JScrollPane(statsContentPanel);
        statsScrollPane.setBorder(BorderFactory.createEmptyBorder());
        statsScrollPane.setOpaque(false);
        statsScrollPane.getViewport().setOpaque(false);
        // See FlipHubFlippingPanelBuilder: a transparent viewport must not be blitted, or the
        // backdrop's washes smear down the column as the list scrolls.
        statsScrollPane.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
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
        // The activity tab's shape: the label west, the dropdown taking the width between, and
        // the direction button pinned east at the shared trailing width, so both tabs' sort rows
        // span their panel and end on the same edge.
        JPanel row = new JPanel(new BorderLayout(TRAILING_CONTROL_GAP, 0));
        row.setOpaque(false);
        row.setAlignmentX(JPanel.LEFT_ALIGNMENT);

        JLabel sortLabel = new JLabel("Sort");
        uiStyler.styleMicroLabel(sortLabel, 9.5f);
        sortLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 4));

        uiStyler.styleComboBox(statsSortCombo);
        statsSortCombo.setFont(font(10.5f));
        statsSortCombo.setBorder(roundedBorder(INPUT_ARC, CONTROL_BORDER, new Insets(2, 6, 2, 6)));
        if (statsSortCombo.getSelectedItem() == null) {
            statsSortCombo.setSelectedItem(StatsItemSort.COMPLETION);
        }
        statsSortCombo.addActionListener(e -> {
            StatsItemSort sort = (StatsItemSort) statsSortCombo.getSelectedItem();
            if (panelStateService != null && sort != null) {
                panelStateService.onStatsSortSelectionChanged(listener, panelState, sort, renderStatsItems);
            }
        });
        // A rounded square, not the chip radius' oval, at the same width as the star toggle.
        uiStyler.styleGhostControl(statsSortDirectionButton, 9f, new Insets(3, 6, 3, 6), INPUT_ARC);
        uiStyler.matchFieldHeight(statsSortDirectionButton, statsSortCombo);
        uiStyler.sizeTrailingControl(statsSortDirectionButton, statsSortCombo);
        statsSortDirectionButton.addActionListener(e -> {
            if (panelStateService != null) {
                panelStateService.onStatsSortDirectionToggled(panelState, renderStatsItems);
                updateStatsSortDirectionButton(statsSortDirectionButton,
                    panelState != null && panelState.statsSortAscending);
            }
        });
        updateStatsSortDirectionButton(statsSortDirectionButton,
            panelState != null && panelState.statsSortAscending);
        row.add(sortLabel, BorderLayout.WEST);
        row.add(statsSortCombo, BorderLayout.CENTER);
        row.add(statsSortDirectionButton, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    private void updateStatsSortDirectionButton(JButton statsSortDirectionButton, boolean ascending) {
        statsSortDirectionButton.setText(ascending ? "\u25b2" : "\u25bc");
        statsSortDirectionButton.setForeground(ascending ? ACCENT : MUTED);
        statsSortDirectionButton.setToolTipText(ascending ? "Ascending order" : "Descending order");
    }

    /**
     * The one card on the tab: the answer at the top, then the figures it is made of as hairline
     * rows under it. One separator per surface - the rows are ruled, not boxed.
     */
    private JPanel buildSummaryCard(JLabel totalProfitValue, Object[][] rows) {
        JPanel card = RoundedPanel.glass(CARD_ARC);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(12, 14, 8, 14));
        card.setAlignmentX(JPanel.LEFT_ALIGNMENT);

        JPanel answer = new JPanel(new BorderLayout());
        answer.setOpaque(false);
        answer.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        answer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));

        JLabel labelView = new JLabel("Total Profit");
        uiStyler.styleMicroLabel(labelView, 9.5f);

        totalProfitValue.setForeground(SUCCESS);
        totalProfitValue.setFont(fontBold(20f));

        answer.add(labelView, BorderLayout.NORTH);
        answer.add(totalProfitValue, BorderLayout.CENTER);
        card.add(answer);
        card.add(Box.createVerticalStrut(8));

        for (Object[] row : rows) {
            card.add(buildStatsRow((String) row[0], (JLabel) row[1], (Color) row[2]));
        }
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        return card;
    }

    private JPanel buildStatsRow(String label, JLabel valueView, Color valueColor) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, LINE));
        row.setAlignmentX(JPanel.LEFT_ALIGNMENT);

        JLabel labelView = new JLabel(label);
        labelView.setForeground(MUTED);
        labelView.setFont(font(10.5f));

        valueView.setHorizontalAlignment(SwingConstants.RIGHT);
        valueView.setForeground(valueColor);
        valueView.setFont(fontSemiBold(12f));

        row.add(labelView, BorderLayout.WEST);
        row.add(valueView, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.setPreferredSize(new Dimension(0, 28));
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
