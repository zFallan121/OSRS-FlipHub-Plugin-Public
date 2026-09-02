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

import static com.osrsfliphub.FlipHubPanelConstants.LINE;
import static com.osrsfliphub.FlipHubPanelConstants.CARD_ARC;
import static com.osrsfliphub.FlipHubPanelConstants.MUTED;
import static com.osrsfliphub.FlipHubPanelConstants.TEXT;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

final class FlipHubItemListContentRenderer {
    private final FlipHubUiStyler uiStyler;
    private final FlipHubPanelHiddenItemStore hiddenItemStore;
    private final FlipHubPanelBookmarkStore bookmarkStore;
    private final FlipHubItemCardBuilder itemCardBuilder;

    FlipHubItemListContentRenderer(FlipHubUiStyler uiStyler,
                                   FlipHubPanelHiddenItemStore hiddenItemStore,
                                   FlipHubPanelBookmarkStore bookmarkStore,
                                   FlipHubItemCardBuilder itemCardBuilder) {
        this.uiStyler = uiStyler;
        this.hiddenItemStore = hiddenItemStore;
        this.bookmarkStore = bookmarkStore;
        this.itemCardBuilder = itemCardBuilder;
    }

    void renderList(JPanel listPanel,
                    FlipHubItem offerPreviewItem,
                    long offerAsOfMs,
                    List<FlipHubItem> lastItems,
                    long lastAsOfMs,
                    boolean showBookmarkedOnly,
                    String searchQuery) {
        if (listPanel == null) {
            return;
        }

        if (offerPreviewItem != null) {
            listPanel.add(buildItemCard(offerPreviewItem, offerAsOfMs, true));
            return;
        }
        if (lastItems == null || lastItems.isEmpty()) {
            listPanel.add(buildEmptyStateCard(showBookmarkedOnly, searchQuery));
            return;
        }
        if (showBookmarkedOnly) {
            List<FlipHubItem> itemsToShow = new ArrayList<>();
            for (FlipHubItem item : lastItems) {
                if (item == null) {
                    continue;
                }
                if (!isHidden(item.item_id) && isBookmarked(item.item_id)) {
                    itemsToShow.add(item);
                }
            }
            if (itemsToShow.isEmpty()) {
                listPanel.add(buildEmptyStateCard(true, searchQuery));
            } else {
                listPanel.add(buildSectionHeader("Bookmarked items"));
                listPanel.add(Box.createVerticalStrut(6));
                addItemCards(listPanel, itemsToShow, lastAsOfMs);
            }
            return;
        }
        addItemCards(listPanel, lastItems, lastAsOfMs);
    }

    private JComponent buildEmptyStateCard(boolean showBookmarkedOnly, String searchQuery) {
        boolean searching = searchQuery != null && !searchQuery.trim().isEmpty();
        if (showBookmarkedOnly) {
            return searching
                ? buildCard("No bookmarks match", "Nothing you have bookmarked goes by that name.")
                : buildCard("No bookmarks", "Bookmark items to pin them here.");
        }
        // The search reaches the whole Grand Exchange now, so an empty result means the name is
        // wrong, not that the account has never traded it.
        return searching
            ? buildCard("No matches", "No tradeable item goes by that name.")
            : buildCard("No flip history", "Make a trade to see your items here.");
    }

    private void addItemCards(JPanel listPanel, List<FlipHubItem> items, long asOfMs) {
        if (listPanel == null || items == null) {
            return;
        }
        for (FlipHubItem item : items) {
            if (item == null || isHidden(item.item_id)) {
                continue;
            }
            listPanel.add(buildItemCard(item, asOfMs, false));
            listPanel.add(Box.createVerticalStrut(8));
        }
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
        titleLabel.setFont(fontSemiBold(12f));

        JLabel bodyLabel = new JLabel(body);
        bodyLabel.setForeground(MUTED);
        bodyLabel.setFont(font(10.5f));

        card.add(titleLabel);
        card.add(Box.createVerticalStrut(4));
        card.add(bodyLabel);
        return card;
    }

    private boolean isHidden(int itemId) {
        return hiddenItemStore != null && hiddenItemStore.isHidden(itemId);
    }

    private boolean isBookmarked(int itemId) {
        return bookmarkStore != null && bookmarkStore.isBookmarked(itemId);
    }

    private JComponent buildItemCard(FlipHubItem item, long asOfMs, boolean compactRightPadding) {
        return itemCardBuilder != null
            ? itemCardBuilder.buildItemCard(item, asOfMs, compactRightPadding)
            : new JPanel();
    }

    private Font font(float size) {
        return uiStyler.font(size);
    }

    private Font fontSemiBold(float size) {
        return uiStyler.fontSemiBold(size);
    }
}
