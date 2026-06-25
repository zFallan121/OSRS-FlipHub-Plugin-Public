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
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.MouseWheelListener;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.border.Border;

final class FlipHubFlippingPanelBuilder {
    interface Hooks {
        Font font(float size);
        Font fontSemiBold(float size);
        Font fontSymbol(float size);
        Border roundedBorder(int arc, Color color, Insets padding);
        void styleTextField(JTextField field);
        void onBookmarkFilterChanged(boolean enabled);
        void onPrevPageRequested();
        void onNextPageRequested();
        void hookSearchListener();
        MouseWheelListener wheelForwarder();
    }

    static final class BuildResult {
        final JPanel panel;
        final JPanel footerPanel;

        BuildResult(JPanel panel, JPanel footerPanel) {
            this.panel = panel;
            this.footerPanel = footerPanel;
        }
    }

    private final Hooks hooks;

    FlipHubFlippingPanelBuilder(Hooks hooks) {
        this.hooks = hooks;
    }

    BuildResult build(
        JTextField searchField,
        JToggleButton bookmarkFilterButton,
        JLabel refreshLabel,
        JButton profileButton,
        JPanel listPanel,
        JScrollPane scrollPane,
        JButton prevButton,
        JButton nextButton,
        JLabel pageLabel
    ) {
        JPanel panel = new JPanel();
        panel.setBackground(BG);
        panel.setLayout(new BorderLayout());

        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.setBackground(BG);

        if (hooks != null) {
            hooks.styleTextField(searchField);
        }

        bookmarkFilterButton.setFocusPainted(false);
        bookmarkFilterButton.setFont(fontSymbol(14.5f));
        bookmarkFilterButton.setBackground(BG_ALT);
        bookmarkFilterButton.setForeground(ACCENT);
        bookmarkFilterButton.setBorder(roundedBorder(CHIP_ARC, SOFT_BORDER, new Insets(6, 10, 6, 10)));
        bookmarkFilterButton.setOpaque(true);
        bookmarkFilterButton.setToolTipText("Show bookmarks only");
        bookmarkFilterButton.addActionListener(e -> {
            boolean enabled = bookmarkFilterButton.isSelected();
            bookmarkFilterButton.setBackground(BG_ALT);
            bookmarkFilterButton.setForeground(enabled ? WARNING : ACCENT);
            if (hooks != null) {
                hooks.onBookmarkFilterChanged(enabled);
            }
        });

        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.add(bookmarkFilterButton, BorderLayout.EAST);

        refreshLabel.setForeground(MUTED);
        refreshLabel.setFont(font(10.5f));

        profileButton.setForeground(MUTED);
        profileButton.setFont(font(10.5f));

        JPanel top = new JPanel();
        top.setBackground(BG);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(searchRow);
        top.add(Box.createVerticalStrut(6));

        listPanel.setBackground(BG_ALT);
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));

        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(BG_ALT);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setWheelScrollingEnabled(true);
        if (hooks != null) {
            scrollPane.addMouseWheelListener(hooks.wheelForwarder());
            scrollPane.getViewport().addMouseWheelListener(hooks.wheelForwarder());
            listPanel.addMouseWheelListener(hooks.wheelForwarder());
        }
        JScrollBar vBar = scrollPane.getVerticalScrollBar();
        vBar.setUnitIncrement(SCROLL_UNIT_INCREMENT);
        vBar.setBlockIncrement(SCROLL_BLOCK_INCREMENT);

        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.setBackground(BG);

        JPanel pager = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        pager.setBackground(BG);
        pager.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        prevButton.setFocusPainted(false);
        nextButton.setFocusPainted(false);
        stylePagerButton(prevButton);
        stylePagerButton(nextButton);

        prevButton.addActionListener(e -> {
            if (hooks != null) {
                hooks.onPrevPageRequested();
            }
        });
        nextButton.addActionListener(e -> {
            if (hooks != null) {
                hooks.onNextPageRequested();
            }
        });

        pageLabel.setForeground(MUTED);
        pageLabel.setFont(font(10.5f));
        pager.add(prevButton);
        pager.add(pageLabel);
        pager.add(nextButton);

        footerPanel.add(pager, BorderLayout.CENTER);

        panel.add(top, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(footerPanel, BorderLayout.SOUTH);

        if (hooks != null) {
            hooks.hookSearchListener();
        }
        return new BuildResult(panel, footerPanel);
    }

    private void stylePagerButton(JButton button) {
        button.setBackground(CARD_ALT);
        button.setForeground(TEXT);
        button.setBorder(roundedBorder(CHIP_ARC, SOFT_BORDER, new Insets(4, 10, 4, 10)));
        button.setFont(fontSemiBold(11f));
        button.setFocusPainted(false);
        button.setOpaque(true);
    }

    private Font font(float size) {
        return hooks != null ? hooks.font(size) : new Font("Dialog", Font.PLAIN, Math.max(10, Math.round(size)));
    }

    private Font fontSemiBold(float size) {
        return hooks != null ? hooks.fontSemiBold(size) : new Font("Dialog", Font.BOLD, Math.max(10, Math.round(size)));
    }

    private Font fontSymbol(float size) {
        return hooks != null ? hooks.fontSymbol(size) : fontSemiBold(size);
    }

    private Border roundedBorder(int arc, Color color, Insets padding) {
        return hooks != null ? hooks.roundedBorder(arc, color, padding) : BorderFactory.createEmptyBorder();
    }
}
