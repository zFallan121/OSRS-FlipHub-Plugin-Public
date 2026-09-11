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
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The Profile list's second dimension: which activities are in it. */
public class StatsRecipeFilterTest {
    private static final int GUARDIAN_BOOTS = 21733;

    @Test
    public void allActivityKeepsPlainFlips() {
        assertTrue(StatsRecipeFilter.ALL.matches(EnumSet.noneOf(ConversionKind.class), true));
        assertTrue(StatsRecipeFilter.ALL.matches(null, true));
        assertTrue(StatsRecipeFilter.ALL.matches(EnumSet.of(ConversionKind.REPAIR), false));
    }

    @Test
    public void allRecipesShowsEveryDirectionAndNoFlips() {
        assertFalse("a flip is not a recipe",
            StatsRecipeFilter.ANY_RECIPE.matches(EnumSet.noneOf(ConversionKind.class), true));
        for (ConversionKind kind : ConversionKind.values()) {
            assertTrue(kind + " belongs in All recipes",
                StatsRecipeFilter.ANY_RECIPE.matches(EnumSet.of(kind), false));
        }
    }

    @Test
    public void eachDirectionMatchesOnlyItself() {
        assertTrue(StatsRecipeFilter.ASSEMBLE.matches(EnumSet.of(ConversionKind.ASSEMBLE), false));
        assertFalse(StatsRecipeFilter.ASSEMBLE.matches(EnumSet.of(ConversionKind.DISASSEMBLE), false));
        assertTrue(StatsRecipeFilter.SET_BREAK.matches(EnumSet.of(ConversionKind.SET_BREAK), false));
        assertFalse(StatsRecipeFilter.SET_BREAK.matches(EnumSet.of(ConversionKind.SET_COMBINE), false));
    }

    @Test
    public void anItemThatWasBothFlippedAndAssembledMatchesBoth() {
        // Real accounts do this: buy and flip an item one week, assemble it the
        // next. The item carries every kind behind its totals.
        java.util.Set<ConversionKind> kinds = EnumSet.of(ConversionKind.ASSEMBLE);
        assertTrue(StatsRecipeFilter.ALL.matches(kinds, true));
        assertTrue(StatsRecipeFilter.ASSEMBLE.matches(kinds, true));
        assertFalse(StatsRecipeFilter.REPAIR.matches(kinds, true));
    }

    @Test
    public void theFlipsFilterHidesAnItemThatWasOnlyEverAssembled() {
        assertFalse(StatsRecipeFilter.FLIP.matches(EnumSet.of(ConversionKind.ASSEMBLE), false));
        assertTrue(StatsRecipeFilter.FLIP.matches(EnumSet.of(ConversionKind.ASSEMBLE), true));
        assertTrue(StatsRecipeFilter.FLIP.matches(EnumSet.noneOf(ConversionKind.class), true));
    }

    @Test
    public void flipsMeansTheActivitiesWithNoRecipeBehindThem() {
        assertTrue(StatsRecipeFilter.FLIP.matchesKind(null));
        assertFalse(StatsRecipeFilter.FLIP.matchesKind(ConversionKind.ASSEMBLE));
        assertFalse(StatsRecipeFilter.ANY_RECIPE.matchesKind(null));
        assertTrue(StatsRecipeFilter.ANY_RECIPE.matchesKind(ConversionKind.SET_BREAK));
        assertTrue(StatsRecipeFilter.ALL.matchesKind(null));
        assertTrue(StatsRecipeFilter.ALL.matchesKind(ConversionKind.REPAIR));
        assertTrue(StatsRecipeFilter.ASSEMBLE.matchesKind(ConversionKind.ASSEMBLE));
        assertFalse(StatsRecipeFilter.ASSEMBLE.matchesKind(ConversionKind.DISASSEMBLE));
    }

    /**
     * The Guardian boots card that started this: two assembles and one ordinary
     * flip of six pairs. Blended, it reports 6.46%, which is true of neither
     * half - the assembles returned 12.69% and the flip 5.38%.
     */
    private static Map<Integer, List<StatsFlipInstance>> guardianBootsCard() {
        ConversionRecipe recipe = new ConversionRecipe(
            ConversionKind.ASSEMBLE, "Guardian boots",
            Collections.singletonList(new ConversionItem(11836, 1)),
            Collections.singletonList(new ConversionItem(GUARDIAN_BOOTS, 1)), 0L);
        ConversionMatch match = new ConversionMatch(recipe, 1L, 1_214_230L,
            Collections.singletonList(new ConversionMatch.Line(11836, 1L, 1_214_230L, false)),
            ConversionConfidence.CONFIRMED);

        Map<Integer, List<StatsFlipInstance>> history = new HashMap<>();
        history.put(GUARDIAN_BOOTS, Arrays.asList(
            new StatsFlipInstance(GUARDIAN_BOOTS, 1_214_230L, 1_365_140L, 1_214_230L,
                1_365_140L, 150_910L, 1, 5_000L, match),
            new StatsFlipInstance(GUARDIAN_BOOTS, 1_210_514L, 1_367_394L, 1_210_514L,
                1_367_394L, 156_880L, 1, 4_000L, match),
            new StatsFlipInstance(GUARDIAN_BOOTS, 2_325_000L, 2_450_000L, 13_950_000L,
                14_700_000L, 750_000L, 6, 3_000L)));
        return history;
    }

    @Test
    public void theWholeCardStillTotalsEverything() {
        FlipHubStatsRenderCoordinator.StatsProfitSlice all =
            FlipHubStatsRenderCoordinator.sliceActivities(guardianBootsCard(), StatsRecipeFilter.ALL);

        assertEquals(1_057_790L, all.profitGp);
        assertEquals(16_374_744L, all.costGp);
        assertEquals(3, all.count);
        assertEquals(6.46, all.roiPercent(), 0.01);
    }

    @Test
    public void theAssembleSliceLeavesTheFlipOut() {
        FlipHubStatsRenderCoordinator.StatsProfitSlice assembles =
            FlipHubStatsRenderCoordinator.sliceActivities(guardianBootsCard(), StatsRecipeFilter.ASSEMBLE);

        assertEquals(307_790L, assembles.profitGp);
        assertEquals(2_424_744L, assembles.costGp);
        assertEquals(2, assembles.count);
        assertEquals(12.69, assembles.roiPercent(), 0.01);
    }

    @Test
    public void theFlipSliceLeavesTheAssemblesOut() {
        FlipHubStatsRenderCoordinator.StatsProfitSlice flips =
            FlipHubStatsRenderCoordinator.sliceActivities(guardianBootsCard(), StatsRecipeFilter.FLIP);

        assertEquals(750_000L, flips.profitGp);
        assertEquals(13_950_000L, flips.costGp);
        assertEquals(1, flips.count);
        assertEquals(5.38, flips.roiPercent(), 0.01);
    }

    @Test
    public void theSlicesAddBackUpToTheWhole() {
        Map<Integer, List<StatsFlipInstance>> card = guardianBootsCard();
        long whole = FlipHubStatsRenderCoordinator.sliceActivities(card, StatsRecipeFilter.ALL).profitGp;
        long recipes = FlipHubStatsRenderCoordinator.sliceActivities(card, StatsRecipeFilter.ANY_RECIPE).profitGp;
        long flips = FlipHubStatsRenderCoordinator.sliceActivities(card, StatsRecipeFilter.FLIP).profitGp;

        assertEquals(whole, recipes + flips);
    }

    @Test
    public void reconcileRecordsTheKindsBehindAnItemsTotals() {
        // The filter reads item.conversionKinds, which is only ever populated
        // here - so if reconcile stopped doing it, every recipe filter would
        // quietly show nothing.
        StatsItem item = new StatsItem();
        item.item_id = GUARDIAN_BOOTS;

        ConversionRecipe recipe = new ConversionRecipe(
            ConversionKind.ASSEMBLE,
            "Guardian boots",
            Collections.singletonList(new ConversionItem(11836, 1)),
            Collections.singletonList(new ConversionItem(GUARDIAN_BOOTS, 1)),
            0L);
        ConversionMatch match = new ConversionMatch(
            recipe, 1L, 1_214_230L,
            Collections.singletonList(new ConversionMatch.Line(11836, 1L, 1_214_230L, false)),
            ConversionConfidence.CONFIRMED);

        Map<Integer, List<StatsFlipInstance>> history = new HashMap<>();
        history.put(GUARDIAN_BOOTS, Arrays.asList(
            new StatsFlipInstance(GUARDIAN_BOOTS, 1_214_230L, 1_365_140L, 1_214_230L,
                1_365_140L, 150_910L, 1, 3_000L, match),
            new StatsFlipInstance(GUARDIAN_BOOTS, 900L, 1_000L, 900L, 1_000L, 100L, 1, 4_000L)));

        List<StatsItem> items = new ArrayList<>(Collections.singletonList(item));
        LocalStatsViewService.reconcileWithFlipHistory(new StatsSummary(), items, history);

        assertEquals(EnumSet.of(ConversionKind.ASSEMBLE), item.conversionKinds);
        assertTrue(StatsRecipeFilter.ASSEMBLE.matches(item.conversionKinds, item.hasPlainFlip));
        assertTrue(StatsRecipeFilter.ALL.matches(item.conversionKinds, item.hasPlainFlip));
        assertFalse(StatsRecipeFilter.SET_BREAK.matches(item.conversionKinds, item.hasPlainFlip));
    }
}
