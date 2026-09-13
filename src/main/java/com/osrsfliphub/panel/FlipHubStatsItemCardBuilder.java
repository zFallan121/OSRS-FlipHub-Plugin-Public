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
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

final class FlipHubStatsItemCardBuilder {
    /** Big enough for a wrench to still read; small enough to leave the name its row. */
    private static final int TYPE_ICON_SIZE = 13;
    private static final int MAX_TYPE_MARKS = 2;

    private final FlipHubPanelValueFormatService valueFormatService;
    private final FlipHubStatsItemFormattingService formattingService;
    private final FlipHubStatsCardInteractionInstaller interactionInstaller;
    private final FlipHubUiStyler uiStyler;
    private final FlipHubItemIconResolver itemIconResolver;
    private final Consumer<StatsFlipInstance> onConversionRejected;
    private final Consumer<StatsFlipInstance> onConversionRestored;
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
                                IntConsumer toggleStatsHistoryExpanded,
                                Consumer<StatsFlipInstance> onConversionRejected,
                                Consumer<StatsFlipInstance> onConversionRestored) {
        this.onConversionRejected = onConversionRejected;
        this.onConversionRestored = onConversionRestored;
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

    /**
     * The activities this card is allowed to show.
     *
     * <p>Filtering only the list of items was half an answer: an item that was
     * both flipped and assembled still opened onto all of it, so "Assembled"
     * showed a flip sitting under two assembles and the card's own total added
     * up all three.
     */
    private List<StatsFlipInstance> getStatsFlipHistory(int itemId) {
        if (panelState == null || panelState.statsFlipHistoryByItem == null) {
            return new ArrayList<>();
        }
        List<StatsFlipInstance> history = panelState.statsFlipHistoryByItem.get(itemId);
        if (history == null) {
            return new ArrayList<>();
        }
        StatsRecipeFilter filter = activeFilter();
        if (filter == StatsRecipeFilter.ALL) {
            return history;
        }
        List<StatsFlipInstance> visible = new ArrayList<>(history.size());
        for (StatsFlipInstance instance : history) {
            // A dismissed guess or an unfinished break is not an activity of
            // any kind; they show only unfiltered.
            if (instance != null && instance.counted() && filter.matchesKind(instance.conversionKind)) {
                visible.add(instance);
            }
        }
        return visible;
    }

    /**
     * The activity kinds behind this card, most profitable first. Null is a
     * flip.
     *
     * <p>Capped at two marks: an item that was flipped, assembled and repaired
     * is real but rare, and three icons would cost the name more room than a
     * third mark is worth. The tooltip on each names it in full.
     */
    private List<ConversionKind> visibleKinds(int itemId) {
        java.util.Map<ConversionKind, Long> profitByKind = new java.util.HashMap<>();
        for (StatsFlipInstance instance : getStatsFlipHistory(itemId)) {
            if (instance == null || !instance.counted()) {
                continue;
            }
            ConversionKind kind = instance.conversionKind;
            profitByKind.merge(kind, Math.abs(instance.profitGp), Long::sum);
        }
        List<ConversionKind> kinds = new ArrayList<>(profitByKind.keySet());
        kinds.sort((left, right) -> Long.compare(profitByKind.get(right), profitByKind.get(left)));
        return kinds.size() > MAX_TYPE_MARKS ? kinds.subList(0, MAX_TYPE_MARKS) : kinds;
    }

    private StatsRecipeFilter activeFilter() {
        return panelState != null && panelState.statsRecipeFilter != null
            ? panelState.statsRecipeFilter
            : StatsRecipeFilter.ALL;
    }

    /**
     * The item as the filter leaves it: totals rebuilt from the activities still
     * on show, so the headline figure and the entries under it are the same
     * answer. Unfiltered, this is the item exactly as it arrived.
     */
    StatsItem visibleItem(StatsItem item) {
        if (item == null || activeFilter() == StatsRecipeFilter.ALL) {
            return item;
        }
        List<StatsFlipInstance> visible = getStatsFlipHistory(item.item_id);
        StatsItem view = new StatsItem();
        view.item_id = item.item_id;
        view.item_name = item.item_name;
        view.conversionKinds = item.conversionKinds;
        view.hasPlainFlip = item.hasPlainFlip;

        long profit = 0L;
        long cost = 0L;
        long qty = 0L;
        int flips = 0;
        long lastSellTs = 0L;
        for (StatsFlipInstance instance : visible) {
            if (instance == null || !instance.counted()) {
                continue;
            }
            profit += instance.profitGp;
            cost += instance.buyCostGp;
            qty += Math.max(0, instance.quantity);
            if (!instance.inProgress) {
                flips += 1;
            }
            lastSellTs = Math.max(lastSellTs, instance.completionTsMs);
        }
        view.total_profit_gp = profit;
        view.total_cost_gp = cost;
        view.roi_percent = cost > 0 ? (profit * 100.0) / cost : 0.0;
        view.total_qty = (int) Math.min(Integer.MAX_VALUE, qty);
        view.fill_count = flips;
        view.last_sell_ts_ms = lastSellTs > 0 ? lastSellTs : item.last_sell_ts_ms;
        return view;
    }

    JPanel buildStatsItemCard(StatsItem sourceItem) {
        StatsItem item = visibleItem(sourceItem);
        boolean expanded = isStatsItemExpanded(item.item_id);
        JPanel card = RoundedPanel.glass(CARD_ARC);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        String name = item.item_name != null && !item.item_name.trim().isEmpty()
            ? item.item_name
            : "Item " + item.item_id;
        card.setToolTipText(buildStatsCardTooltip(name, item, expanded));

        JLabel iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(32, 32));
        if (itemIconResolver != null) {
            itemIconResolver.setItemIcon(iconLabel, item.item_id);
        }

        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setOpaque(false);
        // BorderLayout, not BoxLayout: a label in a Y-axis BoxLayout is capped at its preferred
        // width, so the name would be squeezed again instead of taking the row.
        JPanel center = new JPanel(new BorderLayout(0, 0));
        center.setOpaque(false);

        // The name gets a row to itself: sharing one line with the profit left it barely half
        // the panel width, which is not enough for most item names to survive.
        EllipsisLabel nameLabel = new EllipsisLabel(name);
        nameLabel.setForeground(TEXT);
        nameLabel.setFont(fontBold(12.5f));

        long profit = item.total_profit_gp != null ? item.total_profit_gp : 0;
        JLabel profitLabel = new JLabel(valueFormatService.formatGpCompact(profit), SwingConstants.RIGHT);
        profitLabel.setForeground(profit >= 0 ? SUCCESS : DANGER);
        // One point up from where it was, and tracked. This is the figure the card is read for
        // and the one that was losing its decimal point, but it must not grow to the size of
        // the item's own name above it, which leads.
        profitLabel.setFont(fontNumeric(11.5f));

        EllipsisLabel metaLabel = new EllipsisLabel(formattingService.buildStatsItemMetaShort(item));
        metaLabel.setForeground(MUTED_2);
        metaLabel.setFont(font(10f));

        // Profit sits in EAST so it is always drawn in full; the meta takes whatever is left and
        // clips itself, since every value in it is repeated in the expanded detail rows.
        JPanel metaRow = new JPanel(new BorderLayout(6, 0));
        metaRow.setOpaque(false);
        metaRow.add(metaLabel, BorderLayout.CENTER);
        metaRow.add(profitLabel, BorderLayout.EAST);

        JLabel expandLabel = new JLabel(expanded ? "\u25B2" : "\u25BC", SwingConstants.RIGHT);
        expandLabel.setForeground(MUTED_2);
        expandLabel.setFont(font(10f));

        // These ride on the name's line, which is what lets the line below it run all the way
        // to the card's edge and put the profit in the corner where it is looked for.
        JPanel trailing = new JPanel();
        trailing.setOpaque(false);
        trailing.setLayout(new BoxLayout(trailing, BoxLayout.X_AXIS));
        for (ConversionKind kind : visibleKinds(item.item_id)) {
            JLabel typeMark = new JLabel(new FlipHubActivityIcon(kind, TYPE_ICON_SIZE, ACCENT));
            typeMark.setToolTipText(FlipHubActivityIcon.singularLabel(kind));
            trailing.add(typeMark);
            trailing.add(Box.createHorizontalStrut(4));
        }
        trailing.add(expandLabel);

        JPanel nameRow = new JPanel(new BorderLayout(6, 0));
        nameRow.setOpaque(false);
        nameRow.add(nameLabel, BorderLayout.CENTER);
        nameRow.add(trailing, BorderLayout.EAST);

        center.add(nameRow, BorderLayout.NORTH);
        center.add(metaRow, BorderLayout.CENTER);

        header.add(iconLabel, BorderLayout.WEST);
        header.add(center, BorderLayout.CENTER);
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

    /**
     * The collapsed card trades detail for width - the name is clipped to fit, the meta line is
     * clipped before it and the profit is abbreviated - so the full values live here.
     */
    private String buildStatsCardTooltip(String name, StatsItem item, boolean expanded) {
        return "<html><b>" + escapeHtml(name) + "</b><br>"
            + escapeHtml(valueFormatService.formatGp(item.total_profit_gp)) + "<br>"
            + escapeHtml(formattingService.buildStatsItemMeta(item)) + "<br>"
            + (expanded ? "Click to collapse" : "Click to expand")
            + "</html>";
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private JPanel buildStatsItemDetails(StatsItem item) {
        JPanel details = new JPanel();
        details.setOpaque(false);
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        JPanel figures = new JPanel();
        figures.setOpaque(false);
        figures.setLayout(new BoxLayout(figures, BoxLayout.Y_AXIS));
        long profit = item.total_profit_gp != null ? item.total_profit_gp : 0L;
        Color profitColor = profit >= 0 ? SUCCESS : DANGER;
        figures.add(buildStatsItemDetailLine("Total profit", valueFormatService.formatGp(item.total_profit_gp), profitColor));
        figures.add(buildStatsItemDetailLine("Total cost", valueFormatService.formatGp(item.total_cost_gp), TEXT));
        figures.add(buildStatsItemDetailLine("Avg sell", formattingService.formatStatsAvgSell(item), TEXT));
        figures.add(buildStatsItemDetailLine("Avg buy", formattingService.formatStatsAvgBuy(item), TEXT));
        figures.add(
            buildStatsItemDetailLine(
                "ROI",
                valueFormatService.formatPercent(item.roi_percent),
                item.roi_percent != null && item.roi_percent < 0 ? DANGER : SUCCESS
            )
        );
        figures.add(buildStatsItemDetailLine(
            activityCountLabel(item.item_id),
            String.valueOf(item.fill_count != null ? item.fill_count : 0),
            TEXT));
        figures.add(buildStatsItemDetailLine("Quantity", String.valueOf(item.total_qty != null ? item.total_qty : 0), TEXT));
        // "Avg", matching the two rows above it, because the figure is a mean across every
        // completed flip in the range. Called "Time to complete" it read as the time this one
        // took, and one slow flip among quick ones makes that reading badly wrong.
        figures.add(buildStatsItemDetailLine(
            "Avg time to complete", formattingService.formatStatsTimeToComplete(item), MUTED_2));
        // Two sections, each sunk into the card. The gap between them is the separation; no
        // rule is drawn, because the sinking already says where one block ends and the next
        // begins. The card's own heading stays at full width above both.
        details.add(CardSection.of(figures));
        details.add(Box.createVerticalStrut(6));
        JPanel historySection = buildStatsFlipHistorySection(item.item_id);
        // Everything in this block, its padding included: clicking any of it must not collapse
        // the card out from under whatever was being read.
        interactionInstaller.markSkipStatsCardToggle(historySection);
        details.add(historySection);
        return details;
    }

    /**
     * What to call the count. A card holding two assembles says "Assembles 2",
     * because calling them flips is simply wrong - and a card holding both says
     * "Activities", because there is no one word for the mixture.
     */
    private String activityCountLabel(int itemId) {
        List<ConversionKind> kinds = visibleKinds(itemId);
        if (kinds.size() != 1) {
            return kinds.isEmpty() ? "Flips" : "Activities";
        }
        return FlipHubActivityIcon.pluralLabel(kinds.get(0));
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
        JLabel title = new JLabel(historySectionTitle(history));
        title.setForeground(MUTED);
        title.setFont(fontSemiBold(10f));
        JLabel chevron = new JLabel(expanded ? "\u25B2" : "\u25BC", SwingConstants.RIGHT);
        chevron.setForeground(MUTED_2);
        chevron.setFont(font(10f));
        header.add(title, BorderLayout.WEST);
        header.add(chevron, BorderLayout.EAST);
        section.add(header);

        if (!history.isEmpty()) {
            interactionInstaller.installStatsHistoryToggle(header, itemId);
            interactionInstaller.installStatsHistoryHoverFeedback(header, title, chevron);
            header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            title.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            chevron.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        if (!expanded || history.isEmpty()) {
            interactionInstaller.markSkipStatsCardToggle(section);
            return section;
        }

        // The heading is a control and stays at the card's full width, like the item's own name
        // above it. Only what it opens is set in, which is what makes the indent mean "this
        // belongs to the thing above" rather than decorating every row alike.
        JPanel entries = new JPanel();
        entries.setOpaque(false);
        entries.setLayout(new BoxLayout(entries, BoxLayout.Y_AXIS));
        int numbered = 0;
        boolean anyAdded = false;
        for (StatsFlipInstance instance : history) {
            if (instance == null) {
                continue;
            }
            if (instance.counted() && !instance.inProgress) {
                numbered++;
            }
            // A rule between one entry and the next, and nowhere else. Above the first it would
            // sit under the heading and read as a second underline; below the last it would
            // hang off the end of the block with nothing beneath it to divide.
            entries.add(buildStatsFlipHistoryEntry(
                instance, historyEntryLabel(instance, numbered), anyAdded));
            anyAdded = true;
        }
        section.add(Box.createVerticalStrut(4));
        section.add(CardSection.of(entries));
        // Last, not before the entries are built: the mark is stamped on the components that
        // exist when it runs. Stamped early it missed every entry, so the card-wide collapse
        // handler was installed on them too and clicking "Not assembled" dismissed the guess
        // and snapped the card shut in the same click.
        interactionInstaller.markSkipStatsCardToggle(section);
        return section;
    }

    /**
     * What the entry is called in the list. A finished flip or activity gets
     * its number; anything else gets a word, because it is not the next one in
     * the list - it is the offer that has not finished, the guess that was
     * withdrawn, or the break still waiting on its pieces.
     */
    static String historyEntryLabel(StatsFlipInstance instance, int ordinal) {
        if (instance.dismissed != null) {
            return "Dismissed";
        }
        if (instance.openBreak) {
            return "Unfinished";
        }
        if (instance.inProgress) {
            return "In progress";
        }
        return "#" + ordinal;
    }

    /**
     * @param separated whether an entry comes before this one, and so whether this one is
     *                  divided from it by a rule
     */
    private JPanel buildStatsFlipHistoryEntry(StatsFlipInstance instance, String label,
                                              boolean separated) {
        JPanel entry = new JPanel();
        entry.setOpaque(false);
        entry.setLayout(new BoxLayout(entry, BoxLayout.Y_AXIS));
        entry.setBorder(separated
            ? BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, LINE),
                BorderFactory.createEmptyBorder(7, 0, 2, 0))
            : BorderFactory.createEmptyBorder(0, 0, 2, 0));
        if (instance.dismissed != null) {
            // Nothing here to price. The guess was withdrawn and the sale it
            // named stands unmatched, so the entry says only that, and how to
            // take it back - never a zero dressed up as a buy or a profit.
            entry.add(buildEntryWordRow(label));
            entry.add(buildDismissedBlock(instance));
            return entry;
        }
        if (instance.openBreak) {
            // Nothing here to price either. A break is booked whole once its
            // last piece has sold, and until then the only honest things to
            // say are what the plugin thinks happened, which sales it thinks
            // so from, and how to say it did not.
            entry.add(buildEntryWordRow(label));
            entry.add(buildOpenBreakBlock(instance));
            return entry;
        }

        // The entry reads top to bottom as what was traded, then at what prices, then what came
        // of it. So the quantity sits on the heading row beside the number of the flip, and the
        // profit goes last, where the eye lands after the two prices it comes from.
        JPanel topRow = new JPanel(new BorderLayout(6, 0));
        topRow.setOpaque(false);
        JLabel flipLabel = new JLabel(label);
        flipLabel.setForeground(MUTED_2);
        flipLabel.setFont(font(9.5f));
        JLabel qtyLabel = new JLabel("Qty: " + valueFormatService.formatNumber(instance.quantity),
            SwingConstants.RIGHT);
        qtyLabel.setForeground(MUTED_2);
        qtyLabel.setFont(font(9.5f));
        topRow.add(flipLabel, BorderLayout.WEST);
        topRow.add(qtyLabel, BorderLayout.EAST);

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

        JPanel profitRow = new JPanel(new BorderLayout(6, 0));
        profitRow.setOpaque(false);
        profitRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
        Color profitColor = instance.profitGp >= 0 ? SUCCESS : DANGER;
        JLabel profitLabel = new JLabel("Profit: " + valueFormatService.formatGp(instance.profitGp),
            SwingConstants.RIGHT);
        profitLabel.setForeground(profitColor);
        profitLabel.setFont(fontNumeric(10.5f));
        profitRow.add(profitLabel, BorderLayout.EAST);

        entry.add(topRow);
        if (instance.conversionKind != null && !instance.conversionLines.isEmpty()) {
            entry.add(buildConversionBreakdown(instance));
        }
        entry.add(pricesRow);
        entry.add(profitRow);
        return entry;
    }

    static String historySectionTitle(List<StatsFlipInstance> history) {
        for (StatsFlipInstance instance : history) {
            if (instance != null && instance.conversionKind != null) {
                // Once one entry is something the player made, "flip history" is
                // the wrong word for the list it sits in.
                return "Activity (" + history.size() + ")";
            }
        }
        return "Flip history (" + history.size() + ")";
    }

    /**
     * What the cost basis is actually made of, for an item that was made rather
     * than bought.
     *
     * <p>The direction is spelled out rather than iconified. FlipHubUiStyler
     * leads its family list with Inter, which has no U+2692, so a hammer glyph
     * would render as a tofu box on most Windows clients - in the one place the
     * feature has to explain itself.
     */
    private JPanel buildConversionBreakdown(StatsFlipInstance instance) {
        JPanel breakdown = new JPanel();
        breakdown.setOpaque(false);
        breakdown.setLayout(new BoxLayout(breakdown, BoxLayout.Y_AXIS));
        // No alignmentX of its own. A Y_AXIS BoxLayout aligns children against
        // each other, so a LEFT_ALIGNMENT panel among the entry's default
        // centred rows gets indented and narrowed - which is what was clipping
        // the item names and leaving a gutter down the left of the block.

        // A micro label over a section, which is what STYLEGUIDE.md reserves it
        // for. Not the accent: the panel has one action colour and a heading is
        // not an action.
        JLabel heading = new JLabel(conversionHeading(instance.conversionKind));
        uiStyler.styleMicroLabel(heading, 9.5f);
        JPanel headingRow = new JPanel(new BorderLayout());
        headingRow.setOpaque(false);
        headingRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));
        headingRow.add(heading, BorderLayout.WEST);
        if (onConversionRejected != null && !instance.conversionTrades.isEmpty()) {
            // The way out of a wrong guess, beside the guess. The plugin works
            // out on its own that a trade was assembled or a set broken up,
            // which is worth having - but it reads that off what was bought
            // and sold, and selling one piece of a set out of the bank looks
            // exactly like breaking up the set sitting there waiting to be
            // flipped. Left alone a wrong guess is invisible and permanent, so
            // the guess is offered back rather than asserted: a word in the
            // panel's one action colour, on the heading's row, with no box of
            // its own to earn.
            headingRow.add(actionLink(
                rejectLabel(instance.conversionKind),
                rejectTooltip(instance.conversionKind),
                () -> onConversionRejected.accept(instance)), BorderLayout.EAST);
        }
        breakdown.add(headingRow);

        for (ConversionMatch.Line line : instance.conversionLines) {
            if (line != null) {
                breakdown.add(buildConversionLine(line));
            }
        }
        // A Y_AXIS child with no ceiling absorbs slack from the box above it.
        breakdown.setMaximumSize(new Dimension(Integer.MAX_VALUE, breakdown.getPreferredSize().height));
        return breakdown;
    }

    /**
     * A word that does something. The panel has one action colour and this is
     * where it goes; there is no rule around it, because a box inside a ledger
     * row has nothing to earn itself with.
     */
    private JLabel actionLink(String text, String tooltip, Runnable action) {
        JLabel link = new JLabel(text, SwingConstants.RIGHT);
        link.setForeground(ACCENT);
        link.setFont(font(9.5f));
        link.setToolTipText(tooltip);
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.addMouseListener(new FlipHubStatsClickMouseAdapter(action));
        // Under the pointer the word goes to plain text. The action colour says "this does
        // something"; white says "this one, the one you are on".
        link.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent event) {
                link.setForeground(TEXT);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent event) {
                link.setForeground(ACCENT);
            }
        });
        return link;
    }

    /** The word an unnumbered entry gets where a numbered one has its profit row. */
    private JPanel buildEntryWordRow(String label) {
        JLabel word = new JLabel(label);
        word.setForeground(MUTED_2);
        word.setFont(font(9.5f));
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));
        row.add(word, BorderLayout.WEST);
        return row;
    }

    /**
     * A break the plugin has guessed and not finished: the heading the
     * finished activity would get, the control to refuse it, and under them
     * the pieces whose sales the guess rests on - named, not priced. There is
     * no figure anywhere on it, because an unfinished break has none that is
     * honest, and a zero would read as one.
     */
    private JPanel buildOpenBreakBlock(StatsFlipInstance instance) {
        JPanel block = new JPanel();
        block.setOpaque(false);
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));

        JLabel heading = new JLabel(conversionHeading(instance.conversionKind));
        uiStyler.styleMicroLabel(heading, 9.5f);
        JPanel headingRow = new JPanel(new BorderLayout());
        headingRow.setOpaque(false);
        headingRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));
        headingRow.add(heading, BorderLayout.WEST);
        if (onConversionRejected != null && !instance.conversionTrades.isEmpty()) {
            headingRow.add(actionLink(
                rejectLabel(instance.conversionKind),
                rejectTooltip(instance.conversionKind),
                () -> onConversionRejected.accept(instance)), BorderLayout.EAST);
        }
        block.add(headingRow);

        for (ConversionMatch.Line line : instance.conversionLines) {
            if (line == null || line.fee) {
                continue;
            }
            JPanel row = new JPanel(new BorderLayout(6, 0));
            row.setOpaque(false);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));
            EllipsisLabel name = new EllipsisLabel(itemLineName(line));
            name.setForeground(MUTED);
            name.setFont(font(9.5f));
            name.setHorizontalAlignment(SwingConstants.LEFT);
            row.add(name, BorderLayout.CENTER);
            block.add(row);
        }
        block.setMaximumSize(new Dimension(Integer.MAX_VALUE, block.getPreferredSize().height));
        return block;
    }

    /**
     * Where a dismissed guess used to be: what it was, and the way back. The
     * heading names the correction in the same words the control offered it
     * in, so the entry reads as the answer to the question that was asked.
     */
    private JPanel buildDismissedBlock(StatsFlipInstance instance) {
        ConversionKind kind = instance.dismissed != null ? instance.dismissed.kindOrNull() : null;
        JLabel heading = new JLabel(rejectLabel(kind));
        uiStyler.styleMicroLabel(heading, 9.5f);
        JPanel headingRow = new JPanel(new BorderLayout());
        headingRow.setOpaque(false);
        headingRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));
        headingRow.add(heading, BorderLayout.WEST);
        if (onConversionRestored != null) {
            headingRow.add(actionLink(
                "Restore",
                "Put the recipe back on this trade.",
                () -> onConversionRestored.accept(instance)), BorderLayout.EAST);
        }
        return headingRow;
    }

    /**
     * The refusal, in the direction's own words. "Not a recipe" would be true
     * of all five and clear about none; the player is correcting a specific
     * claim, and the control should repeat the claim it withdraws.
     */
    static String rejectLabel(ConversionKind kind) {
        if (kind == null) {
            return "Not a recipe";
        }
        switch (kind) {
            case DISASSEMBLE:
                return "Not disassembled";
            case SET_BREAK:
                return "Not broken up";
            case REPAIR:
                return "Not repaired";
            case SET_COMBINE:
                return "Not combined";
            case ASSEMBLE:
            default:
                return "Not assembled";
        }
    }

    private static String rejectTooltip(ConversionKind kind) {
        boolean takenApart = kind == ConversionKind.SET_BREAK || kind == ConversionKind.DISASSEMBLE;
        return takenApart
            ? "The plugin guessed this was taken apart from what was bought and sold. If it was not, "
                + "click: the pieces' sales stand on their own, the whole item stays as bought, and "
                + "every total is recomputed. It can be restored."
            : "The plugin guessed this was made from those parts. If it was not, click: the sale "
                + "stands on its own, the parts stay as bought, and every total is recomputed. It can "
                + "be restored.";
    }

    private JPanel buildConversionLine(ConversionMatch.Line line) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));

        // The name takes the row and the number is pinned right, so a full
        // "Black tourmaline core" fits instead of being cut to make room for
        // space nothing was using.
        EllipsisLabel name = new EllipsisLabel(conversionLineLabel(line));
        name.setForeground(MUTED);
        name.setFont(font(9.5f));
        name.setHorizontalAlignment(SwingConstants.LEFT);

        JLabel value = new JLabel(valueFormatService.formatGp(line.costGp), SwingConstants.RIGHT);
        value.setForeground(TEXT);
        value.setFont(font(9.5f));

        row.add(name, BorderLayout.CENTER);
        row.add(value, BorderLayout.EAST);
        return row;
    }

    /** A fee has no item, and saying so is the point: it was never a trade. */
    static String conversionLineLabel(ConversionMatch.Line line) {
        if (line.fee) {
            return "NPC fee";
        }
        String name = itemLineName(line);
        return line.quantity > 1 ? name + " x" + line.quantity : name;
    }

    /** The item on a line by name alone - no count, which is a figure. */
    static String itemLineName(ConversionMatch.Line line) {
        String name = resolveItemName(line.itemId);
        return name == null || name.trim().isEmpty() ? "Item " + line.itemId : name;
    }

    private static String resolveItemName(int itemId) {
        ItemLookupService lookup = PluginInjectorBridge.get(ItemLookupService.class);
        return lookup != null ? lookup.getCachedItemName(itemId) : null;
    }

    /**
     * Which way the block reads. An activity that made one thing is filed
     * against that thing and lists what went into it; one that made several is
     * filed against the thing taken apart and lists what came out.
     */
    static String conversionHeading(ConversionKind kind) {
        if (kind == null) {
            return "Made from";
        }
        switch (kind) {
            case DISASSEMBLE:
                return "Disassembled into";
            case SET_BREAK:
                return "Broken into";
            case REPAIR:
                return "Repaired from";
            case SET_COMBINE:
                return "Combined from";
            case ASSEMBLE:
            default:
                return "Assembled from";
        }
    }

    private JPanel buildStatsItemDetailLine(String label, String value, Color valueColor) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        // The value is pinned to the right and always drawn whole; the label takes whatever is
        // left and clips itself. Both used to be pinned to opposite edges, and a row too narrow
        // for the pair of them printed one on top of the other rather than shortening either.
        EllipsisLabel left = new EllipsisLabel(label);
        left.setForeground(MUTED);
        left.setFont(font(10f));
        JLabel right = new JLabel(value != null ? value : "N/A", SwingConstants.RIGHT);
        right.setForeground(valueColor != null ? valueColor : TEXT);
        right.setFont(fontNumeric(10.5f));
        row.add(left, BorderLayout.CENTER);
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

    /** Type for a figure: see {@link FlipHubUiStyler#fontNumeric(float)}. */
    private Font fontNumeric(float size) {
        return uiStyler.fontNumeric(size);
    }
}
