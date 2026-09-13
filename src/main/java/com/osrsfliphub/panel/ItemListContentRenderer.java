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

import static com.osrsfliphub.Skin.LINE;
import static com.osrsfliphub.Skin.CARD_ARC;
import static com.osrsfliphub.Skin.MUTED;
import static com.osrsfliphub.Skin.TEXT;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import lombok.RequiredArgsConstructor;

/**
 * The list of item rows, kept between refreshes.
 *
 * <p>A refresh a second brings new prices for the same items in the same order, which is a list
 * that has not changed shape - only its numbers. Rebuilding it would throw away the row the
 * pointer is on, the tooltip it has open and every icon, and put back an identical list; so the
 * rows are held here by item id and the refresh writes into them. The panel is only emptied and
 * rebuilt when the shape genuinely changes: a different set of items, a different order, a
 * filter turned on, the offer preview taking over.
 */
@RequiredArgsConstructor
final class ItemListContentRenderer {
    private final UiStyler uiStyler;
    private final PanelHiddenItemStore hiddenItemStore;
    private final PanelBookmarkStore bookmarkStore;
    private final ItemCardBuilder itemCardBuilder;
    private final AgeTooltip ageTooltipCoordinator;

    /** What was last put in the panel: the shape of it, and the rows in the order they went in. */
    private String renderedShape;
    private List<ItemCard> renderedCards = new ArrayList<>();

    /** What the panel should hold, worked out before anything is touched. */
    @RequiredArgsConstructor
    private static final class Plan {
        final String shape;
        final List<FlipHubItem> items;
        final boolean offerPreview;
        final boolean sectionHeader;
        final String emptyTitle;
        final String emptyBody;
    }

    /**
     * @return true when the panel's children changed, so the caller has a layout to run and
     *     hovers to put back. False means every row was already there and only its values moved.
     */
    boolean renderList(JPanel listPanel,
                       FlipHubItem offerPreviewItem,
                       long offerAsOfMs,
                       List<FlipHubItem> lastItems,
                       long lastAsOfMs,
                       boolean showBookmarkedOnly,
                       String searchQuery) {
        if (listPanel == null) {
            return false;
        }
        Plan plan = plan(offerPreviewItem, lastItems, showBookmarkedOnly, searchQuery);
        long asOfMs = plan.offerPreview ? offerAsOfMs : lastAsOfMs;
        if (matchesRendered(plan)) {
            for (int index = 0; index < plan.items.size(); index++) {
                itemCardBuilder.applyValues(renderedCards.get(index), plan.items.get(index), asOfMs);
            }
            return false;
        }
        rebuild(listPanel, plan, asOfMs);
        return true;
    }

    private Plan plan(FlipHubItem offerPreviewItem,
                      List<FlipHubItem> lastItems,
                      boolean showBookmarkedOnly,
                      String searchQuery) {
        if (offerPreviewItem != null) {
            return new Plan("offer", listOf(offerPreviewItem), true, false, null, null);
        }
        if (lastItems == null || lastItems.isEmpty()) {
            return emptyPlan(showBookmarkedOnly, searchQuery);
        }
        List<FlipHubItem> itemsToShow = new ArrayList<>();
        for (FlipHubItem item : lastItems) {
            if (item == null || (hiddenItemStore != null && hiddenItemStore.isHidden(item.item_id))) {
                continue;
            }
            if (showBookmarkedOnly && !isBookmarked(item)) {
                continue;
            }
            itemsToShow.add(item);
        }
        if (showBookmarkedOnly) {
            return itemsToShow.isEmpty()
                ? emptyPlan(true, searchQuery)
                : new Plan("bookmarked", itemsToShow, false, true, null, null);
        }
        return new Plan("all", itemsToShow, false, false, null, null);
    }

    private Plan emptyPlan(boolean showBookmarkedOnly, String searchQuery) {
        boolean searching = searchQuery != null && !searchQuery.trim().isEmpty();
        String title;
        String body;
        if (showBookmarkedOnly) {
            title = searching ? "No bookmarks match" : "No bookmarks";
            body = searching
                ? "Nothing you have bookmarked goes by that name."
                : "Bookmark items to pin them here.";
        } else if (searching) {
            // The search reaches the whole Grand Exchange now, so an empty result means the name
            // is wrong, not that the account has never traded it.
            title = "No matches";
            body = "No tradeable item goes by that name.";
        } else {
            title = "No flip history";
            body = "Make a trade to see your items here.";
        }
        return new Plan("empty:" + title, new ArrayList<>(), false, false, title, body);
    }

    /**
     * Whether the panel already holds exactly this plan's rows, in this order, built for this
     * kind of list. Anything less than exactly and the panel is rebuilt: a row in the wrong place
     * is worse than a rebuild, and cheaper to prevent than to move.
     */
    private boolean matchesRendered(Plan plan) {
        if (renderedShape == null || !renderedShape.equals(plan.shape)) {
            return false;
        }
        if (renderedCards.size() != plan.items.size()) {
            return false;
        }
        // Position by position rather than by item id: the row in slot two has to be the card in
        // slot two, whatever the list happens to hold twice.
        for (int index = 0; index < plan.items.size(); index++) {
            ItemCard card = renderedCards.get(index);
            if (card.itemId != plan.items.get(index).item_id
                || card.compactRightPadding != plan.offerPreview) {
                return false;
            }
        }
        return true;
    }

    private void rebuild(JPanel listPanel, Plan plan, long asOfMs) {
        // Before removeAll(): pulling the rows out from under the pointer synthesises a
        // mouseExited on them, and the hover has to be stood down before that arrives.
        if (ageTooltipCoordinator != null) {
            ageTooltipCoordinator.clearEntriesForRebuild();
        }
        listPanel.removeAll();
        renderedCards = new ArrayList<>();

        if (plan.emptyTitle != null) {
            listPanel.add(buildCard(plan.emptyTitle, plan.emptyBody));
        } else {
            if (plan.sectionHeader) {
                listPanel.add(buildSectionHeader("Bookmarked items"));
                listPanel.add(Box.createVerticalStrut(6));
            }
            for (FlipHubItem item : plan.items) {
                ItemCard card = itemCardBuilder.buildItemCard(item, asOfMs, plan.offerPreview);
                renderedCards.add(card);
                listPanel.add(card.panel);
                if (!plan.offerPreview) {
                    listPanel.add(Box.createVerticalStrut(8));
                }
            }
        }
        renderedShape = plan.shape;
    }

    private List<FlipHubItem> listOf(FlipHubItem item) {
        List<FlipHubItem> items = new ArrayList<>();
        items.add(item);
        return items;
    }

    private JPanel buildSectionHeader(String text) {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        // A hairline and a micro-label: the section is not a box, it is a rule with a name on it.
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        JLabel label = new JLabel(text != null ? text : "");
        uiStyler.styleMicroLabel(label, 9.5f);
        header.add(label, BorderLayout.WEST);
        return header;
    }

    private JPanel buildCard(String title, String body) {
        JPanel card = RoundedPanel.glass(CARD_ARC);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(TEXT);
        titleLabel.setFont(uiStyler.fontSemiBold(12f));

        JLabel bodyLabel = new JLabel(body);
        bodyLabel.setForeground(MUTED);
        bodyLabel.setFont(uiStyler.font(10.5f));

        card.add(titleLabel);
        card.add(Box.createVerticalStrut(4));
        card.add(bodyLabel);
        return card;
    }


    private boolean isBookmarked(FlipHubItem item) {
        return bookmarkStore != null && bookmarkStore.isBookmarked(item.item_id);
    }

}
