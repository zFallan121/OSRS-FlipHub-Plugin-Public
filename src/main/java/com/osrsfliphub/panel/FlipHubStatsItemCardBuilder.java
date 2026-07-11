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
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

final class FlipHubStatsItemCardBuilder {
    private final FlipHubPanelValueFormatService valueFormatService;
    private final FlipHubStatsItemFormattingService formattingService;
    private final FlipHubStatsCardInteractionInstaller interactionInstaller;
    private final FlipHubUiStyler uiStyler;
    private final FlipHubItemIconResolver itemIconResolver;
    private final Supplier<Integer> expandedStatsItemIdSupplier;
    private final Set<Integer> expandedStatsHistoryItems;
    private final FlipHubPanelMutableState panelState;

    FlipHubStatsItemCardBuilder(FlipHubPanelValueFormatService valueFormatService,
                                FlipHubUiStyler uiStyler,
                                FlipHubItemIconResolver itemIconResolver,
                                Supplier<Integer> expandedStatsItemIdSupplier,
                                Set<Integer> expandedStatsHistoryItems,
                                FlipHubPanelMutableState panelState,
                                IntConsumer toggleStatsItemExpanded,
                                IntConsumer toggleStatsHistoryExpanded) {
        this.valueFormatService = valueFormatService;
        this.formattingService = new FlipHubStatsItemFormattingService(valueFormatService);
        this.interactionInstaller =
            new FlipHubStatsCardInteractionInstaller(toggleStatsItemExpanded, toggleStatsHistoryExpanded);
        this.uiStyler = uiStyler;
        this.itemIconResolver = itemIconResolver;
        this.expandedStatsItemIdSupplier = expandedStatsItemIdSupplier;
        this.expandedStatsHistoryItems = expandedStatsHistoryItems;
        this.panelState = panelState;
    }

    private boolean isStatsItemExpanded(int itemId) {
        Integer expandedId = expandedStatsItemIdSupplier != null ? expandedStatsItemIdSupplier.get() : null;
        return expandedId != null && expandedId == itemId;
    }

    private boolean isStatsHistoryExpanded(int itemId) {
        return expandedStatsHistoryItems != null && expandedStatsHistoryItems.contains(itemId);
    }

    private List<StatsFlipInstance> getStatsFlipHistory(int itemId) {
        if (panelState == null || panelState.statsFlipHistoryByItem == null) {
            return new ArrayList<>();
        }
        List<StatsFlipInstance> history = panelState.statsFlipHistoryByItem.get(itemId);
        return history != null ? history : new ArrayList<>();
    }

    JPanel buildStatsItemCard(StatsItem item) {
        boolean expanded = isStatsItemExpanded(item.item_id);
        JPanel card = new RoundedPanel(CARD_ARC, CARD, SOFT_BORDER);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.setToolTipText(expanded ? "Click to collapse" : "Click to expand");

        JLabel iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(32, 32));
        if (itemIconResolver != null) {
            itemIconResolver.setItemIcon(iconLabel, item.item_id);
        }

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        String name = item.item_name != null && !item.item_name.trim().isEmpty()
            ? item.item_name
            : "Item " + item.item_id;
        EllipsisLabel nameLabel = new EllipsisLabel(name);
        nameLabel.setForeground(TEXT);
        nameLabel.setFont(fontBold(12.5f));

        JLabel metaLabel = new JLabel(formattingService.buildStatsItemMeta(item));
        metaLabel.setForeground(MUTED);
        metaLabel.setFont(font(10.5f));

        center.add(nameLabel);
        center.add(metaLabel);

        long profit = item.total_profit_gp != null ? item.total_profit_gp : 0;
        JLabel profitLabel = new JLabel(valueFormatService.formatGp(profit), SwingConstants.RIGHT);
        profitLabel.setForeground(profit >= 0 ? SUCCESS : DANGER);
        profitLabel.setFont(fontSemiBold(12f));
        JLabel expandLabel = new JLabel(expanded ? "\u25B2" : "\u25BC", SwingConstants.RIGHT);
        expandLabel.setForeground(MUTED);
        expandLabel.setFont(font(10f));
        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.add(profitLabel);
        right.add(expandLabel);

        header.add(iconLabel, BorderLayout.WEST);
        header.add(center, BorderLayout.CENTER);
        header.add(right, BorderLayout.EAST);
        card.add(header);
        if (expanded) {
            card.add(Box.createVerticalStrut(6));
            card.add(buildStatsItemDetails(item));
        }
        if (expanded) {
            Dimension preferred = card.getPreferredSize();
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
        } else {
            card.setPreferredSize(new Dimension(0, 56));
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        }
        interactionInstaller.installStatsCardToggle(card, item.item_id);
        return card;
    }

    private JPanel buildStatsItemDetails(StatsItem item) {
        JPanel details = new JPanel();
        details.setOpaque(false);
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        long profit = item.total_profit_gp != null ? item.total_profit_gp : 0L;
        Color profitColor = profit >= 0 ? SUCCESS : DANGER;
        details.add(buildStatsItemDetailLine("Total Profit", valueFormatService.formatGp(item.total_profit_gp), profitColor));
        details.add(buildStatsItemDetailLine("Total Cost", valueFormatService.formatGp(item.total_cost_gp), TEXT));
        details.add(buildStatsItemDetailLine("Avg Sell", formattingService.formatStatsAvgSell(item), TEXT));
        details.add(buildStatsItemDetailLine("Avg Buy", formattingService.formatStatsAvgBuy(item), TEXT));
        details.add(
            buildStatsItemDetailLine(
                "ROI",
                valueFormatService.formatPercent(item.roi_percent),
                item.roi_percent != null && item.roi_percent < 0 ? DANGER : TEXT
            )
        );
        details.add(buildStatsItemDetailLine("Flips", String.valueOf(item.fill_count != null ? item.fill_count : 0), TEXT));
        details.add(buildStatsItemDetailLine("Quantity", String.valueOf(item.total_qty != null ? item.total_qty : 0), TEXT));
        details.add(buildStatsItemDetailLine("Last Completion", formattingService.formatStatsTimestamp(item.last_sell_ts_ms), MUTED));
        details.add(Box.createVerticalStrut(6));
        details.add(buildStatsFlipHistorySection(item.item_id));
        return details;
    }

    private JPanel buildStatsFlipHistorySection(int itemId) {
        JPanel section = new JPanel();
        section.setOpaque(false);
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));

        List<StatsFlipInstance> history = getStatsFlipHistory(itemId);
        if (history == null) {
            history = new ArrayList<>();
        }
        boolean expanded = isStatsHistoryExpanded(itemId);

        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setOpaque(false);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        JLabel title = new JLabel("Flip History (" + history.size() + ")");
        title.setForeground(MUTED);
        title.setFont(fontSemiBold(10f));
        JLabel chevron = new JLabel(expanded ? "\u25B2" : "\u25BC", SwingConstants.RIGHT);
        chevron.setForeground(MUTED);
        chevron.setFont(font(10f));
        header.add(title, BorderLayout.WEST);
        header.add(chevron, BorderLayout.EAST);
        section.add(header);

        interactionInstaller.markSkipStatsCardToggle(section);
        if (!history.isEmpty()) {
            interactionInstaller.installStatsHistoryToggle(header, itemId);
            interactionInstaller.installStatsHistoryHoverFeedback(header, title, chevron);
            header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            title.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            chevron.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        if (!expanded || history.isEmpty()) {
            return section;
        }

        section.add(Box.createVerticalStrut(4));
        int index = 1;
        for (StatsFlipInstance instance : history) {
            if (instance == null) {
                continue;
            }
            section.add(buildStatsFlipHistoryEntry(instance, index++));
            section.add(Box.createVerticalStrut(3));
        }
        return section;
    }

    private JPanel buildStatsFlipHistoryEntry(StatsFlipInstance instance, int index) {
        JPanel entry = new JPanel();
        entry.setOpaque(false);
        entry.setLayout(new BoxLayout(entry, BoxLayout.Y_AXIS));
        entry.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, SOFT_BORDER),
            BorderFactory.createEmptyBorder(3, 0, 1, 0)
        ));

        JPanel topRow = new JPanel(new BorderLayout(6, 0));
        topRow.setOpaque(false);
        JLabel flipLabel = new JLabel("#" + index);
        flipLabel.setForeground(MUTED);
        flipLabel.setFont(font(9.5f));
        Color profitColor = instance.profitGp >= 0 ? SUCCESS : DANGER;
        JLabel profitLabel = new JLabel("Profit: " + valueFormatService.formatGp(instance.profitGp), SwingConstants.RIGHT);
        profitLabel.setForeground(profitColor);
        profitLabel.setFont(fontSemiBold(10f));
        topRow.add(flipLabel, BorderLayout.WEST);
        topRow.add(profitLabel, BorderLayout.EAST);

        JPanel pricesRow = new JPanel(new BorderLayout(6, 0));
        pricesRow.setOpaque(false);
        pricesRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));
        JLabel buyLabel = new JLabel("Buy: " + valueFormatService.formatGp(instance.buyPriceGp));
        buyLabel.setForeground(TEXT);
        buyLabel.setFont(font(9.5f));
        JLabel sellLabel = new JLabel("Sell: " + valueFormatService.formatGp(instance.sellPriceGp), SwingConstants.RIGHT);
        sellLabel.setForeground(TEXT);
        sellLabel.setFont(font(9.5f));
        pricesRow.add(buyLabel, BorderLayout.WEST);
        pricesRow.add(sellLabel, BorderLayout.EAST);

        JPanel qtyRow = new JPanel(new BorderLayout(6, 0));
        qtyRow.setOpaque(false);
        qtyRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));
        JLabel qtyLabel = new JLabel("Qty: " + valueFormatService.formatNumber(instance.quantity), SwingConstants.RIGHT);
        qtyLabel.setForeground(MUTED);
        qtyLabel.setFont(font(9.5f));
        qtyRow.add(qtyLabel, BorderLayout.EAST);

        entry.add(topRow);
        entry.add(pricesRow);
        entry.add(qtyRow);
        return entry;
    }

    private JPanel buildStatsItemDetailLine(String label, String value, Color valueColor) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        JLabel left = new JLabel(label + ":");
        left.setForeground(MUTED);
        left.setFont(font(10f));
        JLabel right = new JLabel(value != null ? value : "N/A", SwingConstants.RIGHT);
        right.setForeground(valueColor != null ? valueColor : TEXT);
        right.setFont(fontSemiBold(10f));
        row.add(left, BorderLayout.WEST);
        row.add(right, BorderLayout.EAST);
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
}
