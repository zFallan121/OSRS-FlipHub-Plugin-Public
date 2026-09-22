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

import java.awt.*;
import java.awt.event.MouseWheelListener;
import javax.swing.*;
import lombok.RequiredArgsConstructor;
import static com.osrsfliphub.Skin.*;

@RequiredArgsConstructor
final class FlippingPanelBuilder {

    @RequiredArgsConstructor
    static final class BuildResult {
        final JPanel panel;
        final JPanel footerPanel;
    }

    private final UiStyler uiStyler;
    private final PanelState panelState;
    private final FlipHubSearchCoordinator searchCoordinator;
    private final MouseWheelListener wheelForwarder;

    BuildResult build(
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
        JLabel pageLabel
    ) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BorderLayout());

        JPanel searchRow = plain(new BorderLayout(TRAILING_CONTROL_GAP, 0));

        uiStyler.styleTextField(searchField);
        uiStyler.installInlineClear(searchField);

        uiStyler.styleGhostControl(
            bookmarkFilterButton, BOOKMARK_GLYPH_SIZE, new Insets(3, 6, 3, 6), INPUT_ARC);
        bookmarkFilterButton.setFont(uiStyler.fontSymbol(BOOKMARK_GLYPH_SIZE));
        bookmarkFilterButton.setForeground(ACCENT);
        bookmarkFilterButton.setToolTipText("Show bookmarks only");
        uiStyler.matchFieldHeight(bookmarkFilterButton, searchField);
        // Pinned rather than left to the glyph, so the sort row underneath can take the same
        // width and the two rows share a right edge instead of ending a few pixels apart.
        uiStyler.sizeTrailingControl(bookmarkFilterButton, searchField);
        bookmarkFilterButton.addActionListener(e -> {
            boolean enabled = !panelState.showBookmarkedOnly;
            // On, the star takes the same gold as the stars on the cards the filter is showing,
            // so the control and the rows it selected read as one statement. Off, it drops back
            // to the action colour: an offer to filter rather than a filter in force.
            bookmarkFilterButton.setForeground(enabled ? WARNING : ACCENT);
            panelState.setBookmarkFilter(enabled);
        });

        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.add(bookmarkFilterButton, BorderLayout.EAST);

        refreshLabel.setForeground(MUTED_2);
        refreshLabel.setFont(uiStyler.font(10.5f));

        JPanel top = stack();
        top.add(searchRow);
        top.add(Box.createVerticalStrut(6));
        top.add(buildSortRow(itemSortCombo, itemSortDirectionButton));
        top.add(Box.createVerticalStrut(6));

        listPanel.setOpaque(false);
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));

        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        // A transparent viewport over a painted backdrop cannot be blitted: the blit copies the
        // old pixels and the washes smear down the column as the list scrolls. SIMPLE repaints
        // the exposed strip from the backdrop up, which is the price of keeping the glow.
        scrollPane.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setWheelScrollingEnabled(true);
        scrollPane.addMouseWheelListener(wheelForwarder);
        scrollPane.getViewport().addMouseWheelListener(wheelForwarder);
        listPanel.addMouseWheelListener(wheelForwarder);
        // The backdrop is almost entirely covered by the list, so the click that releases the
        // search box has to be catchable on the list's own surfaces too.
        uiStyler.installClickToDefocus(listPanel);
        uiStyler.installClickToDefocus(scrollPane);

        JScrollBar vBar = scrollPane.getVerticalScrollBar();
        vBar.setUnitIncrement(SCROLL_UNIT_INCREMENT);
        vBar.setBlockIncrement(SCROLL_BLOCK_INCREMENT);

        JPanel footerPanel = plain(new BorderLayout());

        JPanel pager = plain(new FlowLayout(FlowLayout.CENTER, 8, 0));
        // The pager is a footnote under the list, not a toolbar: two pixels clear of the last
        // card, and the backdrop leaves two more below it before the panel edge.
        pager.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        prevButton.setFocusPainted(false);
        nextButton.setFocusPainted(false);
        stylePagerButton(prevButton);
        stylePagerButton(nextButton);

        prevButton.addActionListener(e -> panelState.previousPage());
        nextButton.addActionListener(e -> panelState.nextPage());

        pageLabel.setForeground(MUTED);
        pageLabel.setFont(uiStyler.font(10.5f));
        pager.add(prevButton);
        pager.add(pageLabel);
        pager.add(nextButton);

        footerPanel.add(pager, BorderLayout.CENTER);

        panel.add(top, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(footerPanel, BorderLayout.SOUTH);

        searchCoordinator.hookSearchListener(searchField, () -> panelState.setSearchQuery(searchField.getText()));
        return new BuildResult(panel, footerPanel);
    }

    /**
     * The same sort row the profile tab carries, kept to a 24px control height: this sits between
     * the search box and the first card, so any weight it takes is weight the list loses.
     */
    private JPanel buildSortRow(JComboBox<StatsItemSort> itemSortCombo, JButton itemSortDirectionButton) {
        // The same two-part shape as the search row above - a stretching control with a button
        // pinned east - so the two rows span one width and share both edges. No label on the
        // front of it: a word there set the dropdown in from the search box above and named what
        // the dropdown already says, which cost the row its symmetry to repeat itself.
        JPanel row = plain(new BorderLayout(TRAILING_CONTROL_GAP, 0));
        // The search row takes the JPanel default alignment. A BoxLayout lines its children up
        // on their alignment points, so a single child at LEFT_ALIGNMENT among centred siblings
        // is pushed sideways by half the panel.
        row.setAlignmentX(JPanel.CENTER_ALIGNMENT);

        panelState.restoreItemSort();

        uiStyler.styleComboBox(itemSortCombo);
        itemSortCombo.setBorder(uiStyler.roundedBorder(INPUT_ARC, CONTROL_BORDER, new Insets(2, 6, 2, 6)));
        itemSortCombo.setSelectedItem(panelState.itemSort);
        itemSortCombo.addActionListener(e -> {
            StatsItemSort sort = (StatsItemSort) itemSortCombo.getSelectedItem();
            if (sort != null) {
                panelState.setItemSort(sort, panelState.itemSortAscending);
            }
        });

        // No container: the mark is the control. It takes the same trailing slot the bookmark
        // filter takes in the row above, so the two marks stand on one centre line rather than
        // half a slot apart, and the mark is drawn at the star's own size for the same reason.
        int markSize = uiStyler.sortMarkSize(TRAILING_CONTROL_WIDTH);
        uiStyler.styleBareControl(itemSortDirectionButton);
        uiStyler.matchFieldHeight(itemSortDirectionButton, itemSortCombo);
        uiStyler.sizeTrailingControl(itemSortDirectionButton, itemSortCombo);
        itemSortDirectionButton.addActionListener(e -> {
            boolean ascending = !panelState.itemSortAscending;
            panelState.setItemSort(panelState.itemSort, ascending);
            updateSortDirectionButton(itemSortDirectionButton, ascending, markSize);
        });
        updateSortDirectionButton(itemSortDirectionButton,
            panelState.itemSortAscending, markSize);

        row.add(itemSortCombo, BorderLayout.CENTER);
        row.add(itemSortDirectionButton, BorderLayout.EAST);
        wide(row, row.getPreferredSize().height);
        return row;
    }

    private void updateSortDirectionButton(JButton button, boolean ascending, int markSize) {
        button.setText(null);
        button.setIcon(new SortIcon(ascending, markSize));
        button.setForeground(ascending ? ACCENT : TEXT);
        button.setToolTipText(ascending ? "Sorted low to high" : "Sorted high to low");
    }

    private void stylePagerButton(JButton button) {
        uiStyler.styleGhostControl(button, 9f, new Insets(0, 8, 0, 8), INPUT_ARC);
        Dimension size = new Dimension(button.getPreferredSize().width, 15);
        button.setPreferredSize(size);
        button.setMaximumSize(size);
    }
}
