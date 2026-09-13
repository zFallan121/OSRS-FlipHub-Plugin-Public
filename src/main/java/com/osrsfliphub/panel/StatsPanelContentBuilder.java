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

import static com.osrsfliphub.Skin.*;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
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
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import lombok.RequiredArgsConstructor;

final class StatsPanelContentBuilder {
    @RequiredArgsConstructor
    static final class ContentResult {
        final JScrollPane scrollPane;
        final JLabel totalProfitValue;
        final JLabel roiValue;
        final JLabel flipsValue;
        final JLabel taxValue;
        final JLabel sessionTimeValue;
        final JLabel hourlyValue;
    }

    private final UiStyler uiStyler;
    private final PanelState panelStateService;
    private final PanelMutableState panelState;
    private final PanelListener listener;
    private final Runnable renderStatsItems;
    private final Runnable updateStatsSummary;
    private final WheelScroll wheelScrollCoordinator;
    private final MouseWheelListener wheelForwarder;
    private final Runnable openRecorder;

    StatsPanelContentBuilder(UiStyler uiStyler,
                                    PanelState panelStateService,
                                    PanelMutableState panelState,
                                    PanelListener listener,
                                    Runnable renderStatsItems,
                                    Runnable updateStatsSummary,
                                    WheelScroll wheelScrollCoordinator,
                                    MouseWheelListener wheelForwarder,
                                    Runnable openRecorder) {
        this.openRecorder = openRecorder;
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
        JTextField statsSearchField,
        JComboBox<StatsItemSort> statsSortCombo,
        JComboBox<StatsRecipeFilter> statsFilterCombo,
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

        // The search sits under the answer rather than over it. It narrows the list, not the
        // total - putting it above the card read as if the card were showing the search's
        // profit, and it now stands with the sort and the filter, which is the company it keeps.
        statsContentPanel.add(Box.createVerticalStrut(12));
        statsContentPanel.add(buildSearchRow(statsSearchField));
        statsContentPanel.add(Box.createVerticalStrut(8));
        statsContentPanel.add(buildStatsSortRow(statsSortCombo, statsFilterCombo, statsSortDirectionButton));
        statsContentPanel.add(Box.createVerticalStrut(8));
        statsContentPanel.add(buildItemsHeadingRow());
        statsContentPanel.add(Box.createVerticalStrut(4));

        statsItemsListPanel.setOpaque(false);
        statsItemsListPanel.setLayout(new BoxLayout(statsItemsListPanel, BoxLayout.Y_AXIS));
        statsItemsListPanel.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        statsContentPanel.add(statsItemsListPanel);

        JScrollPane statsScrollPane = new JScrollPane(statsContentPanel);
        statsScrollPane.setBorder(BorderFactory.createEmptyBorder());
        statsScrollPane.setOpaque(false);
        statsScrollPane.getViewport().setOpaque(false);
        // See FlippingPanelBuilder: a transparent viewport must not be blitted, or the
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

    /**
     * The heading over the item list, and the way into recording a recipe.
     *
     * <p>One accent word on a heading the list had been going without, rather than a control of
     * its own: recording is a rare thing to do next to reading the list, and a button would take
     * a row of a 201px column every time the tab is opened to say so. The heading it hangs on is
     * the same micro label every other block in the panel gets.
     */
    private JPanel buildItemsHeadingRow() {
        JPanel row = new JPanel(new BorderLayout(TRAILING_CONTROL_GAP, 0));
        row.setOpaque(false);
        row.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

        JLabel heading = new JLabel("Items");
        uiStyler.styleMicroLabel(heading, 9.5f);
        row.add(heading, BorderLayout.WEST);

        row.add(uiStyler.actionLink("Record a recipe",
            "Record trades as one recipe",
            () -> {
                if (openRecorder != null) {
                    openRecorder.run();
                }
            }), BorderLayout.EAST);
        return row;
    }

    /**
     * The field and nothing else. What stood beside it was a button spelling out "Clear", which
     * took a slot of the row whether or not there was anything in the field to clear; the mark
     * inside the field does the same work only when there is.
     */
    private JPanel buildSearchRow(JTextField statsSearchField) {
        JPanel searchRow = new JPanel(new BorderLayout(TRAILING_CONTROL_GAP, 0));
        searchRow.setOpaque(false);
        searchRow.setAlignmentX(JPanel.LEFT_ALIGNMENT);

        uiStyler.styleTextField(statsSearchField);
        statsSearchField.setToolTipText("Filter items");
        uiStyler.onEdit(statsSearchField, () -> {
            if (panelStateService != null) {
                panelStateService.onStatsSearchQueryChanged(panelState, statsSearchField.getText(), renderStatsItems);
            }
        });

        uiStyler.installInlineClear(statsSearchField);

        searchRow.add(statsSearchField, BorderLayout.CENTER);
        searchRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, searchRow.getPreferredSize().height));
        return searchRow;
    }

    private JPanel buildStatsSortRow(JComboBox<StatsItemSort> statsSortCombo,
                                     JComboBox<StatsRecipeFilter> statsFilterCombo,
                                     JButton statsSortDirectionButton) {
        // Two questions, two controls, side by side at half width each, with the
        // direction button pinned east at the shared trailing width so the row
        // still ends on the panel's edge. The "Sort" micro label is gone: with a
        // second dropdown beside it, a label that names only one of them reads as
        // if it named both - which is the confusion this row is fixing.
        JPanel row = new JPanel(new BorderLayout(TRAILING_CONTROL_GAP, 0));
        row.setOpaque(false);
        row.setAlignmentX(JPanel.LEFT_ALIGNMENT);

        // The direction button belongs to the sort, not to the row, so it sits
        // against it. The filter then takes whatever is left, which is more than
        // an even split gave it - and it is the control with the longest words.
        JPanel sortGroup = new JPanel(new BorderLayout(4, 0));
        sortGroup.setOpaque(false);

        uiStyler.styleComboBox(statsSortCombo);
        statsSortCombo.setBorder(roundedBorder(INPUT_ARC, CONTROL_BORDER, new Insets(2, 6, 2, 6)));
        if (statsSortCombo.getSelectedItem() == null) {
            statsSortCombo.setSelectedItem(StatsItemSort.COMPLETION);
        }
        statsSortCombo.addActionListener(e -> {
            StatsItemSort sort = (StatsItemSort) statsSortCombo.getSelectedItem();
            if (panelStateService != null && sort != null) {
                if (panelState != null) {
                    panelState.statsSort = sort;
                }
                panelStateService.onStatsSortSelectionChanged(listener, panelState, sort, renderStatsItems);
            }
        });

        uiStyler.styleComboBox(statsFilterCombo);
        statsFilterCombo.setBorder(roundedBorder(INPUT_ARC, CONTROL_BORDER, new Insets(2, 6, 2, 6)));
        if (statsFilterCombo.getSelectedItem() == null) {
            statsFilterCombo.setSelectedItem(StatsRecipeFilter.ALL);
        }
        statsFilterCombo.addActionListener(e -> {
            StatsRecipeFilter filter = (StatsRecipeFilter) statsFilterCombo.getSelectedItem();
            if (panelStateService != null && filter != null) {
                panelStateService.onStatsRecipeFilterChanged(panelState, filter, renderStatsItems);
            }
        });

        // No container: the mark is the control. Half the trailing width, because what the slot
        // gives up here is what the filter beside it was short of - the mark itself is the same
        // size the flipping tab draws, so one control does not read as two.
        int markSize = uiStyler.sortMarkSize(SORT_DIRECTION_WIDTH);
        uiStyler.styleBareControl(statsSortDirectionButton);
        uiStyler.matchFieldHeight(statsSortDirectionButton, statsSortCombo);
        uiStyler.sizeTrailingControl(statsSortDirectionButton, statsSortCombo, SORT_DIRECTION_WIDTH);
        statsSortDirectionButton.addActionListener(e -> {
            if (panelStateService != null) {
                panelStateService.onStatsSortDirectionToggled(panelState, renderStatsItems);
                updateStatsSortDirectionButton(statsSortDirectionButton,
                    panelState != null && panelState.statsSortAscending, markSize);
            }
        });
        updateStatsSortDirectionButton(statsSortDirectionButton,
            panelState != null && panelState.statsSortAscending, markSize);
        sortGroup.add(statsSortCombo, BorderLayout.CENTER);
        sortGroup.add(statsSortDirectionButton, BorderLayout.EAST);
        row.add(sortGroup, BorderLayout.WEST);
        row.add(statsFilterCombo, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    private void updateStatsSortDirectionButton(JButton statsSortDirectionButton,
                                                boolean ascending,
                                                int markSize) {
        statsSortDirectionButton.setText(null);
        statsSortDirectionButton.setIcon(new SortIcon(ascending, markSize));
        statsSortDirectionButton.setForeground(ascending ? ACCENT : TEXT);
        statsSortDirectionButton.setToolTipText(ascending ? "Sorted low to high" : "Sorted high to low");
    }

    /**
     * The one card on the tab: the answer at the top, then the figures it is made of as hairline
     * rows under it. One separator per surface - the rows are ruled, not boxed.
     */
    /**
     * The slice menu, opened from the heading. The active row is marked in the
     * action colour rather than by rewriting the heading, so the card keeps one
     * answer and one question.
     */
    private void installProfitFilterMenu(JLabel trigger) {
        // A popup dismisses itself on any press outside it - including the press
        // on the very label that opened it, which then opened it again. The
        // dismissal lands first, so by the time this handler runs the menu is
        // already invisible and there is nothing left to ask. The moment it
        // closed is the only evidence that the press was a close, not an open.
        long[] closedAtMs = {0L};
        trigger.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent event) {
                trigger.setForeground(TEXT);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent event) {
                trigger.setForeground(MUTED_2);
            }

            @Override
            public void mousePressed(java.awt.event.MouseEvent event) {
                if (System.currentTimeMillis() - closedAtMs[0] < 250L) {
                    return;
                }
                javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
                menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
                    @Override
                    public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                    }

                    @Override
                    public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                        closedAtMs[0] = System.currentTimeMillis();
                    }

                    @Override
                    public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
                    }
                });
                menu.setBackground(OVERLAY_BASE);
                menu.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                    javax.swing.BorderFactory.createLineBorder(LINE_STRONG),
                    javax.swing.BorderFactory.createEmptyBorder(2, 0, 2, 0)));
                StatsRecipeFilter active = panelState != null && panelState.statsProfitFilter != null
                    ? panelState.statsProfitFilter
                    : StatsRecipeFilter.ALL;
                for (StatsRecipeFilter filter : StatsRecipeFilter.values()) {
                    javax.swing.JMenuItem entry = new javax.swing.JMenuItem(filter.toString());
                    entry.setOpaque(true);
                    entry.setBackground(OVERLAY_BASE);
                    entry.setForeground(filter == active ? ACCENT : TEXT);
                    entry.setFont(uiStyler.font(UiStyler.DROPDOWN_TEXT_SIZE));
                    entry.setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 10, 3, 14));
                    entry.addChangeListener(e -> entry.setBackground(
                        entry.getModel().isArmed() ? SURFACE_TOP : OVERLAY_BASE));
                    entry.addActionListener(e -> {
                        if (panelStateService != null) {
                            panelStateService.onStatsProfitFilterChanged(panelState, filter, updateStatsSummary);
                        }
                    });
                    menu.add(entry);
                }
                menu.show(trigger, 0, trigger.getHeight());
            }
        });
    }

    private JPanel buildSummaryCard(JLabel totalProfitValue,
                                    Object[][] rows) {
        JPanel card = RoundedPanel.glass(CARD_ARC);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(12, 14, 8, 14));
        card.setAlignmentX(JPanel.LEFT_ALIGNMENT);

        JPanel answer = new JPanel(new BorderLayout());
        answer.setOpaque(false);
        answer.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        // Sized to what it holds. The old fixed 46 cut the descenders off the
        // one number the whole tab exists to show.
        answer.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        // No control of its own: the label is the control. A caret after the
        // words is enough to say there is something to open, and the card still
        // reads as a card rather than a form. The label never changes - naming
        // the slice here would turn the heading into a second answer.
        // U+25BC, not U+25BE. The small down-pointing triangle drew as a
        // tofu box; this is the same triangle the sort button and the card
        // chevrons use, and having been seen is the only evidence a glyph
        // is safe here.
        JLabel labelView = new TipLabel("Total Profit \u25bc", SwingConstants.LEADING);
        uiStyler.styleMicroLabel(labelView, 9.5f);
        labelView.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        labelView.setToolTipText("One kind of activity");
        installProfitFilterMenu(labelView);

        totalProfitValue.setForeground(SUCCESS);
        totalProfitValue.setFont(uiStyler.fontBold(20f));

        JPanel labelRow = new JPanel(new BorderLayout());
        labelRow.setOpaque(false);
        labelRow.add(labelView, BorderLayout.WEST);

        answer.add(labelRow, BorderLayout.NORTH);
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

        JLabel labelView = new TipLabel(label, SwingConstants.LEADING);
        labelView.setForeground(MUTED);
        labelView.setFont(uiStyler.font(10.5f));

        valueView.setHorizontalAlignment(SwingConstants.RIGHT);
        valueView.setForeground(valueColor);
        valueView.setFont(uiStyler.fontSemiBold(12f));

        row.add(labelView, BorderLayout.WEST);
        row.add(valueView, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.setPreferredSize(new Dimension(0, 28));
        return row;
    }

    private Border roundedBorder(int arc, Color color, Insets padding) {
        return uiStyler.roundedBorder(arc, color, padding);
    }
}
