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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The card at the top of the Profile tab, end to end.
 *
 * <p>Two derivations have to agree before that card is right: the flip-history
 * ledger, which produces the activities, and the stats cache, which produces
 * the per-item rows and the tax figure. LocalStatsViewService.reconcile then
 * makes the history authoritative for profit, cost, ROI, quantity and flip
 * count - so if only one of the two knew about conversions, this would show it.
 */
public class ProfileSummaryConversionTest {
    private static final int CORE = 21730;
    private static final int BANDOS_BOOTS = 11836;
    private static final int GUARDIAN_BOOTS = 21733;
    private static final int MAHOGANY = 6332;

    private static final long CORE_COST = 483_005L;
    private static final long BOOTS_COST = 731_225L;
    private static final long INPUT_COST = CORE_COST + BOOTS_COST;
    private static final long SALE_NET = 1_365_140L;
    private static final int SALE_GROSS_UNIT = 1_393_000;
    private static final long RECIPE_PROFIT = SALE_NET - INPUT_COST;

    private static ConversionLedger ledger() {
        ConversionRecipe recipe = new ConversionRecipe(
            ConversionKind.ASSEMBLE,
            "Guardian boots",
            Arrays.asList(new ConversionItem(CORE, 1), new ConversionItem(BANDOS_BOOTS, 1)),
            Collections.singletonList(new ConversionItem(GUARDIAN_BOOTS, 1)),
            0L
        );
        return new ConversionLedger(new ConversionRecipeIndex(Collections.singletonList(recipe)));
    }

    private static LocalTradeDelta buy(long tsMs, int slot, int itemId, int qty, long gp) {
        return new LocalTradeDelta(tsMs, slot, itemId, true, qty, gp, "OFFER_COMPLETED", (int) (gp / qty), false);
    }

    /** One assembled sale, and one ordinary flip alongside it. */
    private static List<LocalTradeDelta> deltas() {
        return Arrays.asList(
            buy(1_000L, 1, BANDOS_BOOTS, 1, BOOTS_COST),
            buy(2_000L, 2, CORE, 1, CORE_COST),
            new LocalTradeDelta(3_000L, 3, GUARDIAN_BOOTS, false, 1, SALE_NET, "OFFER_COMPLETED", SALE_GROSS_UNIT, false),
            buy(4_000L, 4, MAHOGANY, 10, 12_000L),
            new LocalTradeDelta(5_000L, 4, MAHOGANY, false, 10, 13_720L, "OFFER_COMPLETED", 1_400, false)
        );
    }

    private static LocalStatsSnapshot snapshot() {
        LocalStatsCache cache = new LocalStatsCache(ledger());
        cache.rebuild(deltas());
        return new LocalStatsSnapshot(cache.getSummary(), cache.getItems());
    }

    private static Map<Integer, List<StatsFlipInstance>> history() {
        return new LocalFlipHistoryService(ledger()).buildHistory(deltas(), null);
    }

    private static StatsItem itemFor(List<StatsItem> items, int itemId) {
        for (StatsItem item : items) {
            if (item != null && item.item_id == itemId) {
                return item;
            }
        }
        return null;
    }

    @Test
    public void theAssembledItemReachesTheProfileList() {
        List<StatsItem> items = snapshot().items;

        StatsItem guardianBoots = itemFor(items, GUARDIAN_BOOTS);
        assertNotNull("an assembled item with no buy of its own must still get a row", guardianBoots);
        assertEquals(Long.valueOf(RECIPE_PROFIT), guardianBoots.total_profit_gp);
        assertEquals(Long.valueOf(INPUT_COST), guardianBoots.total_cost_gp);
    }

    @Test
    public void totalProfitIncludesTheRecipeAndTheFlip() {
        LocalStatsSnapshot snapshot = snapshot();
        StatsSummary summary = snapshot.summary;
        LocalStatsViewService.reconcileWithFlipHistory(summary, snapshot.items, history());

        // 150,910 assembled + 1,720 flipped.
        assertEquals(Long.valueOf(RECIPE_PROFIT + 1_720L), summary.total_profit_gp);
        assertEquals(Long.valueOf(INPUT_COST + 12_000L), summary.total_cost_gp);
        assertEquals(Integer.valueOf(2), summary.fill_count);
        assertEquals(Long.valueOf(11L), summary.total_qty);
    }

    @Test
    public void roiIsProfitOverTheRealInputCost() {
        LocalStatsSnapshot snapshot = snapshot();
        StatsSummary summary = snapshot.summary;
        LocalStatsViewService.reconcileWithFlipHistory(summary, snapshot.items, history());

        double expected = ((RECIPE_PROFIT + 1_720L) * 100.0) / (INPUT_COST + 12_000L);
        assertEquals(expected, summary.roi_percent, 0.0001);
        // Sanity: an assemble that cost 1.2M to make 150k is a low-teens return,
        // not the infinite one a zero cost basis would produce.
        assertTrue(summary.roi_percent > 10.0 && summary.roi_percent < 15.0);
    }

    @Test
    public void taxPaidCountsTheConvertedSale() {
        // Both ledgers have to know about conversions: the cache's tax stands
        // until reconcile replaces it with the history's, and the card must
        // read the same either way.
        LocalStatsSnapshot snapshot = snapshot();
        StatsSummary summary = snapshot.summary;

        // floor(1,393,000 / 50) + floor(1,400 / 50) x 10 = 27,860 + 280.
        assertEquals(Long.valueOf(28_140L), summary.tax_paid_gp);
        LocalStatsViewService.reconcileWithFlipHistory(summary, snapshot.items, history());
        assertEquals(Long.valueOf(28_140L), summary.tax_paid_gp);
    }

    @Test
    public void theItemRowRoiMatchesItsOwnActivity() {
        LocalStatsSnapshot snapshot = snapshot();
        LocalStatsViewService.reconcileWithFlipHistory(snapshot.summary, snapshot.items, history());

        StatsItem guardianBoots = itemFor(snapshot.items, GUARDIAN_BOOTS);
        assertEquals(Long.valueOf(RECIPE_PROFIT), guardianBoots.total_profit_gp);
        assertEquals((RECIPE_PROFIT * 100.0) / INPUT_COST, guardianBoots.roi_percent, 0.0001);
        assertEquals(Integer.valueOf(1), guardianBoots.fill_count);
        assertEquals(Integer.valueOf(1), guardianBoots.total_qty);
    }

    @Test
    public void theIngredientsNeverAppearAsRowsOfTheirOwn() {
        List<StatsItem> items = snapshot().items;

        assertEquals(null, itemFor(items, CORE));
        assertEquals(null, itemFor(items, BANDOS_BOOTS));
    }
}
