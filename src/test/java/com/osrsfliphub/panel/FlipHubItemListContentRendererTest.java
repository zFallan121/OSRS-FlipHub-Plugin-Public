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

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class FlipHubItemListContentRendererTest {
    private static final long AS_OF = 1_700_000_000_000L;

    private final AgeTooltip ageTooltipCoordinator =
        new AgeTooltip(new PanelValueFormat());
    private static final PanelBookmarkStore NO_BOOKMARKS = new PanelBookmarkStore() {
        @Override
        public boolean isBookmarked(int itemId) {
            return false;
        }

        @Override
        public void toggleBookmark(int itemId) {
        }
    };

    private static final PanelHiddenItemStore NOTHING_HIDDEN = new PanelHiddenItemStore() {
        @Override
        public boolean isHidden(int itemId) {
            return false;
        }

        @Override
        public void hideItem(int itemId) {
        }
    };

    private final ItemCardBuilder cardBuilder = new ItemCardBuilder(
        new PanelValueFormat(),
        new UiStyler(),
        null,
        null,
        NO_BOOKMARKS,
        NOTHING_HIDDEN,
        null,
        null,
        ageTooltipCoordinator,
        null
    );

    private ItemListContentRenderer renderer() {
        return new ItemListContentRenderer(
            new UiStyler(),
            NOTHING_HIDDEN,
            NO_BOOKMARKS,
            cardBuilder,
            ageTooltipCoordinator
        );
    }

    private static JPanel listPanel() {
        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        return listPanel;
    }

    private static FlipHubItem item(int itemId, String name, int sellPrice) {
        FlipHubItem item = new FlipHubItem();
        item.item_id = itemId;
        item.item_name = name;
        item.instasell_price = sellPrice;
        item.instabuy_price = sellPrice - 7;
        return item;
    }

    private static boolean render(ItemListContentRenderer renderer,
                                  JPanel listPanel,
                                  List<FlipHubItem> items) {
        return renderer.renderList(listPanel, null, 0L, items, AS_OF, false, "");
    }

    @Test
    public void arefreshOfTheSameItemsKeepsEveryRowAndWritesTheNewValues() {
        ItemListContentRenderer renderer = renderer();
        JPanel listPanel = listPanel();
        List<FlipHubItem> items = new ArrayList<>(Arrays.asList(
            item(1933, "Pot of flour", 114),
            item(4151, "Abyssal whip", 1_800_000)));

        assertTrue(render(renderer, listPanel, items));
        Component[] afterFirst = listPanel.getComponents();

        List<FlipHubItem> refreshed = new ArrayList<>(Arrays.asList(
            item(1933, "Pot of flour", 121),
            item(4151, "Abyssal whip", 1_750_000)));

        assertFalse("a refresh of the same rows must not rebuild them",
            render(renderer, listPanel, refreshed));

        Component[] afterRefresh = listPanel.getComponents();
        assertEquals(afterFirst.length, afterRefresh.length);
        for (int index = 0; index < afterFirst.length; index++) {
            assertSame("row " + index + " was replaced", afterFirst[index], afterRefresh[index]);
        }

        PanelValueFormat format = new PanelValueFormat();
        assertEquals("the row the pointer is on should be showing the new price",
            format.formatGp(121), sellPriceOf(afterRefresh[0]));
        assertEquals(format.formatGp(1_750_000), sellPriceOf(afterRefresh[2]));
    }

    /** The value beside the "Sell price" label on a card, wherever the row sits in it. */
    private static String sellPriceOf(Component card) {
        if (!(card instanceof Container)) {
            return null;
        }
        for (Component child : ((Container) card).getComponents()) {
            if (child instanceof Container) {
                Component[] parts = ((Container) child).getComponents();
                if (parts.length == 2 && parts[0] instanceof JLabel && parts[1] instanceof JLabel
                    && "Sell price".equals(((JLabel) parts[0]).getText())) {
                    return ((JLabel) parts[1]).getText();
                }
                String nested = sellPriceOf(child);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    @Test
    public void adifferentSetOfItemsRebuildsThePanel() {
        ItemListContentRenderer renderer = renderer();
        JPanel listPanel = listPanel();

        assertTrue(render(renderer, listPanel,
            new ArrayList<>(Arrays.asList(item(1933, "Pot of flour", 114)))));
        assertTrue("a new item is a new shape",
            render(renderer, listPanel, new ArrayList<>(Arrays.asList(
                item(1933, "Pot of flour", 114),
                item(4151, "Abyssal whip", 1_800_000)))));
        assertTrue("a reorder is a new shape",
            render(renderer, listPanel, new ArrayList<>(Arrays.asList(
                item(4151, "Abyssal whip", 1_800_000),
                item(1933, "Pot of flour", 114)))));
    }

    @Test
    public void anemptyListShowsItsCardOnceAndThenStandsStill() {
        ItemListContentRenderer renderer = renderer();
        JPanel listPanel = listPanel();

        assertTrue(render(renderer, listPanel, new ArrayList<>()));
        Component emptyCard = listPanel.getComponent(0);

        assertFalse(render(renderer, listPanel, new ArrayList<>()));
        assertSame(emptyCard, listPanel.getComponent(0));
    }

    @Test
    public void theofferPreviewAndTheListAreDifferentShapes() {
        ItemListContentRenderer renderer = renderer();
        JPanel listPanel = listPanel();
        FlipHubItem offer = item(1933, "Pot of flour", 114);

        assertTrue(renderer.renderList(listPanel, offer, AS_OF, new ArrayList<>(), 0L, false, ""));
        assertFalse("the same offer refreshed is the same shape",
            renderer.renderList(listPanel, offer, AS_OF, new ArrayList<>(), 0L, false, ""));
        assertTrue("dropping the preview goes back to the list",
            render(renderer, listPanel, new ArrayList<>(Arrays.asList(offer))));
    }
}
