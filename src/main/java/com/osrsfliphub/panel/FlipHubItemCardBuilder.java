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

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

final class FlipHubItemCardBuilder {
    private final FlipHubPanelValueFormatService valueFormatService;
    private final FlipHubUiStyler uiStyler;
    private final FlipHubItemIconResolver itemIconResolver;
    private final FlipHubExternalLinkCoordinator externalLinkCoordinator;
    private final FlipHubPanelBookmarkStore bookmarkStore;
    private final FlipHubPanelHiddenItemStore hiddenItemStore;
    private final FlipHubPanelMutableState panelState;
    private final Runnable renderItems;
    private final FlipHubAgeTooltipCoordinator ageTooltipCoordinator;
    private final FlipHubWheelScrollCoordinator wheelScrollCoordinator;

    FlipHubItemCardBuilder(FlipHubPanelValueFormatService valueFormatService,
                           FlipHubUiStyler uiStyler,
                           FlipHubItemIconResolver itemIconResolver,
                           FlipHubExternalLinkCoordinator externalLinkCoordinator,
                           FlipHubPanelBookmarkStore bookmarkStore,
                           FlipHubPanelHiddenItemStore hiddenItemStore,
                           FlipHubPanelMutableState panelState,
                           Runnable renderItems,
                           FlipHubAgeTooltipCoordinator ageTooltipCoordinator,
                           FlipHubWheelScrollCoordinator wheelScrollCoordinator) {
        this.valueFormatService = valueFormatService;
        this.uiStyler = uiStyler;
        this.itemIconResolver = itemIconResolver;
        this.externalLinkCoordinator = externalLinkCoordinator;
        this.bookmarkStore = bookmarkStore;
        this.hiddenItemStore = hiddenItemStore;
        this.panelState = panelState;
        this.renderItems = renderItems;
        this.ageTooltipCoordinator = ageTooltipCoordinator;
        this.wheelScrollCoordinator = wheelScrollCoordinator;
    }

    private boolean isBookmarked(int itemId) {
        return bookmarkStore != null && bookmarkStore.isBookmarked(itemId);
    }

    private void renderItems() {
        if (renderItems != null) {
            renderItems.run();
        }
    }

    /**
     * A row, built once. The values are written into it by {@link #applyValues} rather than baked
     * in here, so the same call fills a new row and refreshes an existing one.
     */
    FlipHubItemCard buildItemCard(FlipHubItem item, long asOfMs, boolean compactRightPadding) {
        JPanel card = RoundedPanel.glass(CARD_ARC);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        // The same on every side. It used to be ten on the left and four on the right, which
        // put the block off centre inside its own card.
        card.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel header = new JPanel(new BorderLayout(7, 0));
        header.setOpaque(false);

        JLabel iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(32, 32));
        if (itemIconResolver != null) {
            itemIconResolver.setItemIcon(iconLabel, item.item_id);
        }

        JLayeredPane iconLayer = new JLayeredPane();
        iconLayer.setLayout(null);
        iconLayer.setPreferredSize(new Dimension(32, 32));
        iconLayer.setMinimumSize(new Dimension(32, 32));
        iconLayer.setMaximumSize(new Dimension(32, 32));
        iconLabel.setBounds(0, 0, 32, 32);
        iconLayer.add(iconLabel, JLayeredPane.DEFAULT_LAYER);

        JButton removeButton = buildRemoveButton(item);
        int removeSize = REMOVE_MARK_SIZE;
        int removeOffset = (32 - removeSize) / 2;
        removeButton.setBounds(removeOffset, removeOffset, removeSize, removeSize);
        iconLayer.add(removeButton, JLayeredPane.PALETTE_LAYER);
        installRemoveHover(iconLayer, removeButton);

        String resolvedName = resolveName(item);
        EllipsisLabel nameLabel = new EllipsisLabel(resolvedName);
        nameLabel.setForeground(TEXT);
        nameLabel.setFont(fontBold(13f));
        if (externalLinkCoordinator != null) {
            externalLinkCoordinator.attachOpenItemPageHandler(nameLabel, item.item_id, resolvedName);
        }

        header.add(iconLayer, BorderLayout.WEST);
        header.add(nameLabel, BorderLayout.CENTER);

        JButton bookmarkButton = new JButton();
        bookmarkButton.setFocusPainted(false);
        bookmarkButton.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        bookmarkButton.setBorderPainted(false);
        bookmarkButton.setContentAreaFilled(false);
        bookmarkButton.setOpaque(false);
        // The star the filter at the top of the panel is typed at: one control appearing twice,
        // once per row and once for all of them, so it cannot read as two different marks.
        bookmarkButton.setFont(fontSymbol(BOOKMARK_GLYPH_SIZE));
        bookmarkButton.setPreferredSize(new Dimension(TRAILING_CONTROL_WIDTH, 24));
        bookmarkButton.setToolTipText("Bookmark");
        bookmarkButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        // Under the pointer the mark takes the shape the click would leave it in: filled for a
        // row about to be bookmarked, hollow for one about to lose it. Only the shape moves - the
        // colour stays with the state the row is actually in, so a grey fill is always an offer
        // and the gold one is always the fact.
        final Runnable paintBookmark = () -> {
            boolean on = isBookmarked(item.item_id);
            ButtonModel model = bookmarkButton.getModel();
            boolean previewing = model.isRollover() || model.isPressed();
            bookmarkButton.setText(previewing != on ? BOOKMARK_GLYPH : BOOKMARK_GLYPH_EMPTY);
            bookmarkButton.setForeground(on ? WARNING : MUTED_2);
        };
        paintBookmark.run();
        bookmarkButton.getModel().addChangeListener(e -> paintBookmark.run());
        bookmarkButton.addActionListener(e -> {
            if (bookmarkStore != null) {
                bookmarkStore.toggleBookmark(item.item_id);
                paintBookmark.run();
                if (panelState != null && panelState.showBookmarkedOnly && !isBookmarked(item.item_id)) {
                    renderItems();
                }
            }
        });

        header.add(bookmarkButton, BorderLayout.EAST);

        card.add(header);
        card.add(Box.createVerticalStrut(6));
        int rightPadding = compactRightPadding ? OFFER_VALUE_RIGHT_PADDING : VALUE_RIGHT_PADDING;
        // Every row below is coloured by its own state and by nothing else: the label stays --muted
        // and the value carries the reading. A row with nothing to say stays on --text, which is
        // what keeps the ones that do say something worth looking at - your own last two trades
        // included, because a price you already paid is a fact rather than a state.
        LineComponents instaSellLine = buildLineComponents("Sell price", "", TEXT, rightPadding);
        LineComponents instaBuyLine = buildLineComponents("Buy price", "", TEXT, rightPadding);
        LineComponents lastSellLine = buildLineComponents("Last sell price", "", TEXT, rightPadding);
        LineComponents lastBuyLine = buildLineComponents("Last buy price", "", TEXT, rightPadding);
        LineComponents marginLine = buildLineComponents("Margin", "", TEXT, rightPadding);
        LineComponents marginLimitLine = buildLineComponents("Margin x limit", "", TEXT, rightPadding);
        LineComponents roiLine = buildLineComponents("ROI", "", TEXT, rightPadding);
        LineComponents limitLine = buildLineComponents("GE limit remaining", "", TEXT, rightPadding);
        LineComponents resetLine = buildLineComponents("GE limit reset", "", MUTED_2, rightPadding);
        // One section, sunk into the card and set in from its left edge, so the card has a
        // front and a back instead of being a flat list. The heading above, with the picture
        // and the name, deliberately stays at full width.
        JPanel figures = new JPanel();
        figures.setOpaque(false);
        figures.setLayout(new BoxLayout(figures, BoxLayout.Y_AXIS));
        for (LineComponents line : new LineComponents[]{
            instaSellLine, instaBuyLine, lastSellLine, lastBuyLine,
            marginLine, marginLimitLine, roiLine, limitLine, resetLine}) {
            figures.add(line.row);
        }
        // The values carry a right padding of their own, so the block gives them less, and the
        // text ends up the same distance from both edges.
        card.add(CardSection.of(figures, Math.max(0, 6 - rightPadding)));

        FlipHubItemCard built = new FlipHubItemCard(
            card,
            item.item_id,
            compactRightPadding,
            nameLabel,
            instaSellLine.right,
            instaBuyLine.right,
            lastSellLine.right,
            lastBuyLine.right,
            marginLine.right,
            marginLimitLine.right,
            roiLine.right,
            limitLine.right,
            resetLine.right,
            paintBookmark
        );
        if (ageTooltipCoordinator != null) {
            built.agePair = ageTooltipCoordinator.registerAgePair(
                item.instabuy_ts_ms, item.instasell_ts_ms, instaBuyLine, instaSellLine);
        }
        applyValues(built, item, asOfMs);

        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
        if (wheelScrollCoordinator != null) {
            wheelScrollCoordinator.installWheelForwarder(card);
        }
        return built;
    }

    /**
     * Writes an item into a row that already exists.
     */
    void applyValues(FlipHubItemCard card, FlipHubItem item, long asOfMs) {
        if (card == null || item == null) {
            return;
        }
        long now = System.currentTimeMillis();
        card.nameLabel.setText(resolveName(item));
        setValue(card.sellValue,
            valueFormatService.formatGp(item.instasell_price),
            livePriceColor(item.instasell_price, item.instasell_ts_ms, now));
        setValue(card.buyValue,
            valueFormatService.formatGp(item.instabuy_price),
            livePriceColor(item.instabuy_price, item.instabuy_ts_ms, now));
        setValue(card.lastSellValue, valueFormatService.formatGp(item.last_sell_price), TEXT);
        setValue(card.lastBuyValue, valueFormatService.formatGp(item.last_buy_price), TEXT);
        setValue(card.marginValue, valueFormatService.formatGp(item.margin), moneyColor(item.margin));
        setValue(card.marginLimitValue,
            valueFormatService.formatGp(item.margin_x_limit), moneyColor(item.margin_x_limit));
        setValue(card.roiValue,
            valueFormatService.formatPercent(item.roi_percent), moneyColor(item.roi_percent));
        setValue(card.limitRemainingValue,
            valueFormatService.formatLimit(item.ge_limit_remaining, item.ge_limit_total),
            limitRemainingColor(item.ge_limit_remaining, item.ge_limit_total));

        Long resetMs = resolveResetMs(item);
        setValue(card.countdownValue, valueFormatService.formatDuration(resetMs), countdownColor(resetMs));
        if (ageTooltipCoordinator != null) {
            ageTooltipCoordinator.updateAgePair(card.agePair, item.instabuy_ts_ms, item.instasell_ts_ms);
            if (resetMs == null) {
                ageTooltipCoordinator.releaseCountdown(card.countdown);
                card.countdown = null;
            } else if (card.countdown != null) {
                ageTooltipCoordinator.updateCountdown(card.countdown, resetMs, asOfMs);
            } else {
                card.countdown =
                    ageTooltipCoordinator.registerCountdownLabel(card.countdownValue, resetMs, asOfMs);
            }
        }
        if (card.repaintBookmark != null) {
            card.repaintBookmark.run();
        }
    }

    /** Only what changed: a label set to the value it already holds is a repaint for nothing. */
    private void setValue(JLabel label, String text, Color color) {
        if (label == null) {
            return;
        }
        String resolved = text != null ? text : "";
        if (!resolved.equals(label.getText())) {
            label.setText(resolved);
        }
        if (color != null && !color.equals(label.getForeground())) {
            label.setForeground(color);
        }
    }

    private String resolveName(FlipHubItem item) {
        return item.item_name != null && !item.item_name.trim().isEmpty()
            ? item.item_name
            : "Item " + item.item_id;
    }

    /** A limit with nothing spent out of it is not counting down towards anything. */
    private Long resolveResetMs(FlipHubItem item) {
        if (item.ge_limit_total != null && item.ge_limit_total > 0
            && item.ge_limit_remaining != null
            && item.ge_limit_remaining >= item.ge_limit_total) {
            return 0L;
        }
        return item.ge_limit_reset_ms;
    }

    /**
     * The money ramp: green is a gain, red is a loss, and an absent figure stays on --text.
     */
    private Color moneyColor(Number value) {
        if (value == null) {
            return TEXT;
        }
        double amount = value.doubleValue();
        if (amount == 0d) {
            return TEXT;
        }
        return amount < 0 ? DANGER : SUCCESS;
    }

    /**
     * A live price holds --text for its first half hour, takes amber to the hour, and red after
     * that. None of those say the number is wrong: it is the best one available at every stage.
     * They say how long it has been since anybody acted on it, which is worth knowing before it
     * goes into an offer. A price with no timestamp cannot be judged, so it is not.
     */
    private Color livePriceColor(Integer price, Long tradeTimeMs, long now) {
        if (price == null || price <= 0) {
            return TEXT;
        }
        return FlipHubPanelConstants.priceAgeColor(tradeTimeMs, now);
    }

    /**
     * The buy limit as one of three states: the whole window still open, part of it spent, or
     * nothing left to buy until it resets. An item with no known limit has no state to show.
     */
    private Color limitRemainingColor(Integer remaining, Integer total) {
        if (remaining == null || total == null || total <= 0) {
            return TEXT;
        }
        if (remaining <= 0) {
            return DANGER;
        }
        return remaining >= total ? SUCCESS : WARNING;
    }

    /** A running countdown is a limit you are inside of. A zeroed one is not news. */
    private Color countdownColor(Long remainingMs) {
        return remainingMs != null && remainingMs > 0 ? WARNING : MUTED_2;
    }

    private LineComponents buildLineComponents(String label, String value, Color valueColor, int rightPadding) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);

        // Centre, not west: the value keeps its corner and the label gives way, so a narrow
        // row shortens the wording instead of printing the two halves over each other.
        EllipsisLabel left = new EllipsisLabel(label);
        left.setForeground(MUTED);
        left.setFont(font(10.5f));

        JLabel right = new JLabel(value, SwingConstants.RIGHT);
        right.setForeground(valueColor);
        right.setFont(fontSemiBold(12f));
        right.setBorder(new EmptyBorder(0, 0, 0, rightPadding));

        row.add(left, BorderLayout.CENTER);
        row.add(right, BorderLayout.EAST);
        return new LineComponents(row, left, right);
    }

    /**
     * The remove mark: a disc on --icon-scrim with a red cross struck through it, drawn rather
     * than set in type. A letter laid straight over an item icon takes its contrast from whatever
     * sprite happens to be underneath - readable over a herb, lost against a rune - so the mark
     * brings its own ground and its own stroke, and reads the same on every row. The ring is the
     * hover affordance the rest of the panel's ghost controls use.
     */
    private JButton buildRemoveButton(FlipHubItem item) {
        JButton removeButton = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                int size = Math.min(getWidth(), getHeight()) - 1;
                if (size <= 0) {
                    return;
                }
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ICON_SCRIM);
                g2.fillOval(0, 0, size, size);
                g2.setColor(getModel().isRollover() ? CONTROL_BORDER_HOVER : CONTROL_BORDER);
                g2.drawOval(0, 0, size, size);
                g2.setColor(DANGER);
                g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int inset = Math.round(size * 0.28f);
                g2.drawLine(inset, inset, size - inset, size - inset);
                g2.drawLine(size - inset, inset, inset, size - inset);
                g2.dispose();
            }
        };
        removeButton.setFocusPainted(false);
        removeButton.setBorderPainted(false);
        removeButton.setContentAreaFilled(false);
        removeButton.setOpaque(false);
        removeButton.setRolloverEnabled(true);
        removeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        removeButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
        removeButton.setToolTipText("Remove item");
        removeButton.setVisible(false);
        removeButton.addActionListener(e -> {
            if (hiddenItemStore != null && item != null && item.item_id > 0) {
                hiddenItemStore.hideItem(item.item_id);
                renderItems();
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
        // renderItems() throws every card away and builds it again a few times a minute, and the
        // replacement mark is born hidden under a pointer that has not moved - so the mouseEntered
        // that would show it never arrives, and the mark vanishes mid-hover.
        //
        // The tile therefore has to ask where the pointer is itself, and it can only ask once it
        // has bounds to test the answer against. Its own resize is the first moment it does: a
        // hierarchy listener fires while the card is being added, when the tile is still 0x0, and
        // even an invokeLater queued from there runs ahead of the layout pass that sizes it -
        // which is measurably true, and is why the first attempt at this fixed nothing.
        iconLayer.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                SwingUtilities.invokeLater(() -> updateRemoveVisibility(iconLayer, removeButton));
            }
        });
    }

    private void updateRemoveVisibility(JComponent iconLayer, JButton removeButton) {
        if (!iconLayer.isShowing()) {
            // convertPointFromScreen has no frame of reference for a tile that has left the panel.
            removeButton.setVisible(false);
            return;
        }
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

    private Font font(float size) {
        return uiStyler.font(size);
    }

    private Font fontBold(float size) {
        return uiStyler.fontBold(size);
    }

    private Font fontSemiBold(float size) {
        return uiStyler.fontSemiBold(size);
    }

    private Font fontSymbol(float size) {
        return uiStyler.fontSymbol(size);
    }
}
