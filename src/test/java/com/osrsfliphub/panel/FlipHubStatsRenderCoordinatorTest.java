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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.Set;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FlipHubStatsRenderCoordinatorTest {
    @Test
    public void toggleItemExpandedCollapsingCurrentItemClearsHistoryExpansion() {
        StatsRender coordinator = new StatsRender();
        Set<Integer> expandedHistoryItems = new HashSet<>();
        expandedHistoryItems.add(4151);

        Integer nextExpanded = coordinator.toggleItemExpanded(4151, expandedHistoryItems, 4151);

        assertNull(nextExpanded);
        assertFalse(expandedHistoryItems.contains(4151));
    }

    @Test
    public void toggleItemExpandedSwitchingItemClearsPreviousHistoryExpansion() {
        StatsRender coordinator = new StatsRender();
        Set<Integer> expandedHistoryItems = new HashSet<>();
        expandedHistoryItems.add(4151);
        expandedHistoryItems.add(11840);

        Integer nextExpanded = coordinator.toggleItemExpanded(4151, expandedHistoryItems, 11840);

        assertEquals(Integer.valueOf(11840), nextExpanded);
        assertFalse(expandedHistoryItems.contains(4151));
        assertTrue(expandedHistoryItems.contains(11840));
    }

    @Test
    public void toggleHistoryExpandedAddsThenRemovesItem() {
        StatsRender coordinator = new StatsRender();
        Set<Integer> expandedHistoryItems = new HashSet<>();

        coordinator.toggleHistoryExpanded(expandedHistoryItems, 4151);
        assertTrue(expandedHistoryItems.contains(4151));

        coordinator.toggleHistoryExpanded(expandedHistoryItems, 4151);
        assertFalse(expandedHistoryItems.contains(4151));
    }

    @Test
    public void totalPagesRoundsPartialPagesUp() {
        assertEquals(1, StatsRender.totalPages(0));
        assertEquals(1, StatsRender.totalPages(10));
        assertEquals(2, StatsRender.totalPages(11));
        assertEquals(3, StatsRender.totalPages(25));
    }

    @Test
    public void clampPageFallsBackToTheLastPageThatExists() {
        assertEquals(1, StatsRender.clampPage(0, 3));
        assertEquals(2, StatsRender.clampPage(2, 3));
        assertEquals(3, StatsRender.clampPage(9, 3));
        assertEquals(1, StatsRender.clampPage(4, 0));
    }

    @Test
    public void renderItemsDrawsOnlyTheRequestedPageOfItems() {
        StatsRender coordinator = new StatsRender();
        List<StatsItem> rendered = new ArrayList<>();

        int page = coordinator.renderItems(
            newListPanel(),
            buildItems(25),
            "",
            StatsItemSort.PROFIT,
            StatsRecipeFilter.ALL,
            false,
            2,
            item -> {
                rendered.add(item);
                return new JPanel();
            },
            UnaryOperator.identity(),
            null,
            null,
            null
        );

        assertEquals(2, page);
        assertEquals(10, rendered.size());
        assertEquals(14, rendered.get(0).item_id);
        assertEquals(5, rendered.get(9).item_id);
    }

    @Test
    public void renderItemsKeepsOnlyTheRequestedRecipeKind() {
        StatsRender coordinator = new StatsRender();
        List<StatsItem> items = buildItems(3);
        items.get(0).conversionKinds = java.util.EnumSet.of(ConversionKind.ASSEMBLE);
        items.get(1).conversionKinds = java.util.EnumSet.of(ConversionKind.REPAIR);
        // items.get(2) was only ever flipped.
        List<StatsItem> rendered = new ArrayList<>();

        coordinator.renderItems(
            newListPanel(),
            items,
            "",
            StatsItemSort.PROFIT,
            StatsRecipeFilter.ASSEMBLE,
            false,
            1,
            item -> {
                rendered.add(item);
                return new JPanel();
            },
            UnaryOperator.identity(),
            null,
            null,
            null
        );

        assertEquals(1, rendered.size());
        assertEquals(items.get(0).item_id, rendered.get(0).item_id);
    }

    @Test
    public void allRecipesDropsThePlainFlips() {
        StatsRender coordinator = new StatsRender();
        List<StatsItem> items = buildItems(3);
        items.get(0).conversionKinds = java.util.EnumSet.of(ConversionKind.ASSEMBLE);
        items.get(1).conversionKinds = java.util.EnumSet.of(ConversionKind.SET_BREAK);
        List<StatsItem> rendered = new ArrayList<>();

        coordinator.renderItems(
            newListPanel(),
            items,
            "",
            StatsItemSort.PROFIT,
            StatsRecipeFilter.ANY_RECIPE,
            false,
            1,
            item -> {
                rendered.add(item);
                return new JPanel();
            },
            UnaryOperator.identity(),
            null,
            null,
            null
        );

        assertEquals(2, rendered.size());
    }

    @Test
    public void theDefaultFilterChangesNothingForAnAccountOfPlainFlips() {
        StatsRender coordinator = new StatsRender();
        List<StatsItem> rendered = new ArrayList<>();

        coordinator.renderItems(
            newListPanel(),
            buildItems(4),
            "",
            StatsItemSort.PROFIT,
            StatsRecipeFilter.ALL,
            false,
            1,
            item -> {
                rendered.add(item);
                return new JPanel();
            },
            UnaryOperator.identity(),
            null,
            null,
            null
        );

        assertEquals(4, rendered.size());
    }

    @Test
    public void renderItemsClampsAPageThatNoLongerExists() {
        StatsRender coordinator = new StatsRender();
        List<StatsItem> rendered = new ArrayList<>();

        int page = coordinator.renderItems(
            newListPanel(),
            buildItems(12),
            "",
            StatsItemSort.PROFIT,
            StatsRecipeFilter.ALL,
            false,
            7,
            item -> {
                rendered.add(item);
                return new JPanel();
            },
            UnaryOperator.identity(),
            null,
            null,
            null
        );

        assertEquals(2, page);
        assertEquals(2, rendered.size());
    }

    private JPanel newListPanel() {
        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        return listPanel;
    }

    /**
     * Profit descends with the item id, so sorting by profit keeps the items in id order and the
     * page slice stays readable in the assertions.
     */
    /**
     * With a recipe filter on, the card shows what that recipe earned, not the item's whole
     * total. Ordering by the whole total put an item that had made a fortune flipping, and
     * almost nothing assembling, above one that had actually made money assembling.
     */
    @Test
    public void theFilteredListIsOrderedByWhatTheCardsWillShow() {
        StatsRender coordinator = new StatsRender();
        List<StatsItem> items = buildItems(2);
        StatsItem mostlyFlipped = items.get(0);
        StatsItem mostlyAssembled = items.get(1);
        mostlyFlipped.conversionKinds = java.util.EnumSet.of(ConversionKind.ASSEMBLE);
        mostlyAssembled.conversionKinds = java.util.EnumSet.of(ConversionKind.ASSEMBLE);
        mostlyFlipped.total_profit_gp = 5_000_000L;
        mostlyAssembled.total_profit_gp = 400_000L;
        List<StatsItem> rendered = new ArrayList<>();

        coordinator.renderItems(
            newListPanel(),
            items,
            "",
            StatsItemSort.PROFIT,
            StatsRecipeFilter.ASSEMBLE,
            false,
            1,
            item -> {
                rendered.add(item);
                return new JPanel();
            },
            item -> {
                // What assembling alone earned each of them.
                StatsItem view = new StatsItem();
                view.item_id = item.item_id;
                view.item_name = item.item_name;
                view.conversionKinds = item.conversionKinds;
                view.total_profit_gp = item.item_id == mostlyFlipped.item_id ? 10_000L : 400_000L;
                return view;
            },
            null,
            null,
            null
        );

        assertEquals(2, rendered.size());
        assertEquals(mostlyAssembled.item_id, rendered.get(0).item_id);
        assertEquals(400_000L, (long) rendered.get(0).total_profit_gp);
    }

    private List<StatsItem> buildItems(int count) {
        List<StatsItem> items = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            StatsItem item = new StatsItem();
            item.item_id = count - index - 1;
            item.item_name = "Item " + item.item_id;
            item.total_profit_gp = (long) item.item_id;
            items.add(item);
        }
        return items;
    }

    @Test
    public void toggleItemExpandedIgnoresInvalidItemId() {
        StatsRender coordinator = new StatsRender();
        Set<Integer> expandedHistoryItems = new HashSet<>();
        expandedHistoryItems.add(4151);

        Integer nextExpanded = coordinator.toggleItemExpanded(4151, expandedHistoryItems, 0);

        assertEquals(Integer.valueOf(4151), nextExpanded);
        assertTrue(expandedHistoryItems.contains(4151));
    }
}

