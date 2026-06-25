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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

final class FlipHubItemCardBuilder {
    interface Hooks {
        Font font(float size);
        Font fontBold(float size);
        Font fontSemiBold(float size);
        Font fontSymbol(float size);
        void setItemIcon(JLabel label, int itemId);
        void attachOpenItemPageHandler(JComponent component, int itemId, String itemName);
        boolean isBookmarked(int itemId);
        void toggleBookmark(int itemId);
        boolean isShowBookmarkedOnly();
        void renderItems();
        void hideItem(int itemId);
        void registerAgePair(Long buyTimestampMs, Long sellTimestampMs, LineComponents buyLine, LineComponents sellLine);
        void registerCountdownLabel(JLabel label, Long remainingMs, long asOfMs);
        void installWheelForwarder(Component component);
    }

    private final FlipHubPanelValueFormatService valueFormatService;
    private final Hooks hooks;

    FlipHubItemCardBuilder(FlipHubPanelValueFormatService valueFormatService, Hooks hooks) {
        this.valueFormatService = valueFormatService;
        this.hooks = hooks;
    }

    JPanel buildItemCard(FlipHubItem item, long asOfMs, boolean compactRightPadding) {
        JPanel card = new RoundedPanel(CARD_ARC, CARD, SOFT_BORDER);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        int cardRightPadding = compactRightPadding ? OFFER_VALUE_RIGHT_PADDING : VALUE_RIGHT_PADDING;
        card.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, cardRightPadding));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel header = new JPanel(new BorderLayout(7, 0));
        header.setBackground(CARD);

        JLabel iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(32, 32));
        if (hooks != null) {
            hooks.setItemIcon(iconLabel, item.item_id);
        }

        JLayeredPane iconLayer = new JLayeredPane();
        iconLayer.setLayout(null);
        iconLayer.setPreferredSize(new Dimension(32, 32));
        iconLayer.setMinimumSize(new Dimension(32, 32));
        iconLayer.setMaximumSize(new Dimension(32, 32));
        iconLabel.setBounds(0, 0, 32, 32);
        iconLayer.add(iconLabel, JLayeredPane.DEFAULT_LAYER);

        JButton removeButton = buildRemoveButton(item);
        int removeSize = 12;
        int removeOffset = (32 - removeSize) / 2;
        removeButton.setBounds(removeOffset, removeOffset, removeSize, removeSize);
        iconLayer.add(removeButton, JLayeredPane.PALETTE_LAYER);
        installRemoveHover(iconLayer, removeButton);

        String resolvedName = item.item_name != null && !item.item_name.trim().isEmpty()
            ? item.item_name
            : "Item " + item.item_id;
        EllipsisLabel nameLabel = new EllipsisLabel(resolvedName);
        nameLabel.setForeground(TEXT);
        nameLabel.setFont(fontBold(13f));
        if (hooks != null) {
            hooks.attachOpenItemPageHandler(nameLabel, item.item_id, resolvedName);
        }

        header.add(iconLayer, BorderLayout.WEST);
        header.add(nameLabel, BorderLayout.CENTER);

        boolean bookmarked = hooks != null && hooks.isBookmarked(item.item_id);
        JButton bookmarkButton = new JButton(bookmarked ? "\u2605" : "\u2606");
        bookmarkButton.setFocusPainted(false);
        bookmarkButton.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        bookmarkButton.setBorderPainted(false);
        bookmarkButton.setContentAreaFilled(false);
        bookmarkButton.setOpaque(false);
        bookmarkButton.setForeground(bookmarked ? WARNING : MUTED);
        bookmarkButton.setFont(fontSymbol(16f));
        bookmarkButton.setPreferredSize(new Dimension(30, 24));
        bookmarkButton.setToolTipText("Bookmark");
        bookmarkButton.addActionListener(e -> {
            if (hooks != null) {
                hooks.toggleBookmark(item.item_id);
                boolean nowBookmarked = hooks.isBookmarked(item.item_id);
                bookmarkButton.setText(nowBookmarked ? "\u2605" : "\u2606");
                bookmarkButton.setForeground(nowBookmarked ? WARNING : MUTED);
                if (hooks.isShowBookmarkedOnly() && !nowBookmarked) {
                    hooks.renderItems();
                }
            }
        });

        header.add(bookmarkButton, BorderLayout.EAST);

        card.add(header);
        card.add(Box.createVerticalStrut(6));
        int rightPadding = compactRightPadding ? OFFER_VALUE_RIGHT_PADDING : VALUE_RIGHT_PADDING;
        LineComponents instaSellLine = buildLineComponents(
            "Sell Price",
            valueFormatService.formatGp(item.instasell_price),
            SUCCESS,
            rightPadding
        );
        LineComponents instaBuyLine = buildLineComponents(
            "Buy Price",
            valueFormatService.formatGp(item.instabuy_price),
            MUTED,
            rightPadding
        );
        card.add(instaSellLine.row);
        card.add(instaBuyLine.row);
        if (hooks != null) {
            hooks.registerAgePair(item.instabuy_ts_ms, item.instasell_ts_ms, instaBuyLine, instaSellLine);
        }
        Color roiColor = item.roi_percent != null && item.roi_percent < 0 ? DANGER : TEXT;
        Object[][] lines = new Object[][]{
            {"Last sell price", valueFormatService.formatGp(item.last_sell_price), TEXT},
            {"Last buy price", valueFormatService.formatGp(item.last_buy_price), TEXT},
            {"Margin", valueFormatService.formatGp(item.margin), WARNING},
            {"Margin x limit", valueFormatService.formatGp(item.margin_x_limit), WARNING},
            {"ROI", valueFormatService.formatPercent(item.roi_percent), roiColor},
            {"GE limit remaining", valueFormatService.formatLimit(item.ge_limit_remaining, item.ge_limit_total), TEXT}
        };
        for (Object[] line : lines) {
            card.add(buildLine((String) line[0], (String) line[1], (Color) line[2], rightPadding));
        }
        Long resetMs = item.ge_limit_reset_ms;
        if (item.ge_limit_total != null && item.ge_limit_total > 0
            && item.ge_limit_remaining != null
            && item.ge_limit_remaining >= item.ge_limit_total) {
            resetMs = 0L;
        }
        card.add(buildCountdownLine("GE limit reset", resetMs, asOfMs, SUCCESS, rightPadding));

        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        if (hooks != null) {
            hooks.installWheelForwarder(card);
        }
        return card;
    }

    private JPanel buildLine(String label, String value, Color valueColor, int rightPadding) {
        return buildLineComponents(label, value, valueColor, rightPadding).row;
    }

    private LineComponents buildLineComponents(String label, String value, Color valueColor, int rightPadding) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(CARD);

        JLabel left = new JLabel(label + ":");
        left.setForeground(MUTED);
        left.setFont(font(10.5f));

        JLabel right = new JLabel(value, SwingConstants.RIGHT);
        right.setForeground(valueColor);
        right.setFont(fontSemiBold(12f));
        right.setBorder(new EmptyBorder(0, 0, 0, rightPadding));

        row.add(left, BorderLayout.WEST);
        row.add(right, BorderLayout.EAST);
        return new LineComponents(row, left, right);
    }

    private JButton buildRemoveButton(FlipHubItem item) {
        JButton removeButton = new JButton("X");
        removeButton.setFocusPainted(false);
        removeButton.setBorderPainted(false);
        removeButton.setContentAreaFilled(false);
        removeButton.setOpaque(false);
        removeButton.setForeground(DANGER);
        removeButton.setFont(fontBold(12f));
        removeButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
        removeButton.setToolTipText("Remove item");
        removeButton.setVisible(false);
        removeButton.addActionListener(e -> {
            if (hooks != null && item != null && item.item_id > 0) {
                hooks.hideItem(item.item_id);
                hooks.renderItems();
            }
        });
        return removeButton;
    }

    private void installRemoveHover(JLayeredPane iconLayer, JButton removeButton) {
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                removeButton.setVisible(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                updateRemoveVisibility(iconLayer, removeButton);
            }
        };
        iconLayer.addMouseListener(adapter);
        for (Component component : iconLayer.getComponents()) {
            if (component instanceof JComponent) {
                ((JComponent) component).addMouseListener(adapter);
            }
        }
    }

    private void updateRemoveVisibility(JComponent iconLayer, JButton removeButton) {
        Point pointer = java.awt.MouseInfo.getPointerInfo() != null
            ? java.awt.MouseInfo.getPointerInfo().getLocation()
            : null;
        if (pointer == null) {
            removeButton.setVisible(false);
            return;
        }
        SwingUtilities.convertPointFromScreen(pointer, iconLayer);
        removeButton.setVisible(iconLayer.contains(pointer));
    }

    private JPanel buildCountdownLine(String label, Long remainingMs, long asOfMs, Color valueColor, int rightPadding) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(CARD);

        JLabel left = new JLabel(label + ":");
        left.setForeground(MUTED);
        left.setFont(font(10.5f));

        JLabel right = new JLabel(valueFormatService.formatDuration(remainingMs), SwingConstants.RIGHT);
        right.setForeground(valueColor);
        right.setFont(fontSemiBold(12f));
        right.setBorder(new EmptyBorder(0, 0, 0, rightPadding));

        row.add(left, BorderLayout.WEST);
        row.add(right, BorderLayout.EAST);

        if (hooks != null) {
            hooks.registerCountdownLabel(right, remainingMs, asOfMs);
        }
        return row;
    }

    private Font font(float size) {
        return hooks != null ? hooks.font(size) : new Font("Dialog", Font.PLAIN, Math.max(10, Math.round(size)));
    }

    private Font fontBold(float size) {
        return hooks != null ? hooks.fontBold(size) : new Font("Dialog", Font.BOLD, Math.max(10, Math.round(size)));
    }

    private Font fontSemiBold(float size) {
        return hooks != null ? hooks.fontSemiBold(size) : fontBold(size);
    }

    private Font fontSymbol(float size) {
        return hooks != null ? hooks.fontSymbol(size) : fontBold(size);
    }
}
