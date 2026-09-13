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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The mobile case: buy and combine on a phone, sell on the desktop.
 *
 * <p>The sale is watched live and carries a real timestamp. The ingredient buys
 * only reach the plugin at the next GE History sync, which has no timestamps of
 * its own and invents them - so they land stamped after the sale they paid for.
 * Pass one refuses that ordering, correctly, and pass two exists to stop a real
 * activity being thrown away over a number the plugin made up.
 *
 * <p>What pass two may not do is override the history's own order. One read of
 * the history is one batch, imported in the order the game lists it; a part
 * that batch lists after the sale was bought after it, and the retry has to
 * refuse it as pass one did. Only across separate imports, where nothing
 * relates the invented timestamps, does the retry keep its original reasoning.
 */
public class ConversionSyncedEvidenceTest {
    private static final int CORE = 21730;
    private static final int BANDOS_BOOTS = 11836;
    private static final int GUARDIAN_BOOTS = 21733;

    private static final int GODSWORD_BLADE = 11798;
    private static final int ARMADYL_HILT = 11810;
    private static final int ARMADYL_GODSWORD = 11802;

    private static final long CORE_COST = 483_005L;
    private static final long BOOTS_COST = 731_225L;
    private static final long INPUT_COST = CORE_COST + BOOTS_COST;
    private static final long SALE_NET = 1_365_140L;
    private static final int SALE_GROSS_UNIT = 1_393_000;

    private static final int SYNCED = Const.GE_HISTORY_SYNTHETIC_SLOT_START;

    private static ConversionRecipe guardianBoots() {
        return new ConversionRecipe(
            ConversionKind.ASSEMBLE,
            "Guardian boots",
            Arrays.asList(new ConversionItem(CORE, 1), new ConversionItem(BANDOS_BOOTS, 1)),
            Collections.singletonList(new ConversionItem(GUARDIAN_BOOTS, 1)),
            0L
        );
    }

    private static ConversionRecipe armadylGodsword() {
        return new ConversionRecipe(
            ConversionKind.ASSEMBLE,
            "Armadyl godsword",
            Arrays.asList(new ConversionItem(GODSWORD_BLADE, 1), new ConversionItem(ARMADYL_HILT, 1)),
            Collections.singletonList(new ConversionItem(ARMADYL_GODSWORD, 1)),
            0L
        );
    }

    private static Ledger ledger() {
        return new Ledger(new RecipeIndex(Collections.singletonList(guardianBoots())));
    }

    private static Delta buy(long tsMs, int slot, int itemId, int qty, long gp) {
        return new Delta(tsMs, slot, itemId, true, qty, gp, "OFFER_COMPLETED", (int) (gp / qty), false);
    }

    private static Delta sale(long tsMs, int slot) {
        return new Delta(
            tsMs, slot, GUARDIAN_BOOTS, false, 1, SALE_NET, "OFFER_COMPLETED", SALE_GROSS_UNIT, false);
    }

    private static Delta sell(long tsMs, int slot, int itemId, long netGp, int unitPrice) {
        return new Delta(tsMs, slot, itemId, false, 1, netGp, "OFFER_COMPLETED", unitPrice, false);
    }

    private static List<StatsFlipInstance> guardianBootsHistory(List<Delta> deltas) {
        return historyOf(ledger(), deltas, GUARDIAN_BOOTS);
    }

    private static List<StatsFlipInstance> historyOf(Ledger ledger, List<Delta> deltas, int itemId) {
        Map<Integer, List<StatsFlipInstance>> result = new LocalFlipHistoryService(ledger).buildHistory(deltas, null);
        List<StatsFlipInstance> history = result.get(itemId);
        return history != null ? history : new ArrayList<>();
    }

    @Test
    public void ingredientsSyncedAfterALiveSaleStillCount() {
        List<Delta> deltas = Arrays.asList(
            sale(1_000L, 3),
            buy(5_000L, SYNCED, BANDOS_BOOTS, 1, BOOTS_COST),
            buy(5_008L, SYNCED + 1, CORE, 1, CORE_COST)
        );

        List<StatsFlipInstance> history = guardianBootsHistory(deltas);

        assertEquals(1, history.size());
        StatsFlipInstance activity = history.get(0);
        assertEquals(INPUT_COST, activity.buyCostGp);
        assertEquals(SALE_NET, activity.sellRevenueGp);
        assertEquals(150_910L, activity.profitGp);
        assertEquals(1, activity.quantity);
        // The sale's own timestamp, not the replay's.
        assertEquals(1_000L, activity.completionTsMs);
        assertEquals(ConversionKind.ASSEMBLE, activity.conversionKind);
        assertEquals(Confidence.LIKELY, activity.conversionConfidence);
    }

    @Test
    public void liveIngredientsBoughtAfterASaleAreStillRefused() {
        // Same shape, real slots. A live timestamp is evidence, so buying the
        // parts after selling the whole cannot be the same trade.
        List<Delta> deltas = Arrays.asList(
            sale(1_000L, 3),
            buy(5_000L, 1, BANDOS_BOOTS, 1, BOOTS_COST),
            buy(5_008L, 2, CORE, 1, CORE_COST)
        );

        assertTrue(guardianBootsHistory(deltas).isEmpty());
    }

    @Test
    public void oneLiveIngredientIsEnoughToRefuseTheWholeConversion() {
        List<Delta> deltas = Arrays.asList(
            sale(1_000L, 3),
            buy(5_000L, SYNCED, BANDOS_BOOTS, 1, BOOTS_COST),
            buy(5_008L, 2, CORE, 1, CORE_COST)
        );

        assertTrue(guardianBootsHistory(deltas).isEmpty());
    }

    @Test
    public void everythingOnMobileIsOrderedAndConfirmed() {
        // A player who never opens the desktop client gets the whole trade in
        // one sync, in history order, so pass one handles it and nothing is
        // downgraded.
        List<Delta> deltas = Arrays.asList(
            buy(5_000L, SYNCED, BANDOS_BOOTS, 1, BOOTS_COST),
            buy(5_008L, SYNCED + 1, CORE, 1, CORE_COST),
            sale(5_016L, SYNCED + 2)
        );

        List<StatsFlipInstance> history = guardianBootsHistory(deltas);

        assertEquals(1, history.size());
        assertEquals(150_910L, history.get(0).profitGp);
        assertEquals(Confidence.CONFIRMED, history.get(0).conversionConfidence);
    }

    @Test
    public void aSyncedBatchKeepsTheOrderTheHistoryGaveIt() {
        // One sync, one batch: the history said the boots were sold and then
        // the parts were bought. Pass one sees the sale first and refuses it.
        // The synced retry used to take it anyway, on the reasoning that the
        // timestamps were invented - but the order inside one read of the
        // history is the game's own, and it says the parts came after. The
        // retry refuses too, and the sale stands unmatched.
        List<Delta> deltas = Arrays.asList(
            sale(5_000L, SYNCED),
            buy(5_008L, SYNCED + 1, BANDOS_BOOTS, 1, BOOTS_COST),
            buy(5_016L, SYNCED + 2, CORE, 1, CORE_COST)
        );

        assertTrue(guardianBootsHistory(deltas).isEmpty());
    }

    @Test
    public void theGodswordSoldBeforeItsPartsWereBoughtIsNotBuiltFromThem() {
        // The owner's case. They owned an Armadyl godsword and sold it; later
        // they bought a blade and a hilt to build another. One read of the
        // history, in that order. Calling the sale an assemble would price the
        // sword at parts bought after it and make the parts vanish from stock.
        Ledger ledger =
            new Ledger(new RecipeIndex(Collections.singletonList(armadylGodsword())));
        List<Delta> deltas = Arrays.asList(
            sell(5_000L, SYNCED, ARMADYL_GODSWORD, 11_760_000L, 12_000_000),
            buy(5_008L, SYNCED + 1, GODSWORD_BLADE, 1, 500_000L),
            buy(5_016L, SYNCED + 2, ARMADYL_HILT, 1, 10_800_000L),
            // The parts are still there afterwards, and sell as what they are.
            sell(9_000L, 1, GODSWORD_BLADE, 490_000L, 500_000),
            sell(9_100L, 2, ARMADYL_HILT, 11_000_000L, 11_224_490)
        );

        Map<Integer, List<StatsFlipInstance>> history = new LocalFlipHistoryService(ledger).buildHistory(deltas, null);

        assertNull("the sale stands on its own", history.get(ARMADYL_GODSWORD));
        assertEquals(1, history.get(GODSWORD_BLADE).size());
        assertEquals(-10_000L, history.get(GODSWORD_BLADE).get(0).profitGp);
        assertEquals(1, history.get(ARMADYL_HILT).size());
        assertEquals(200_000L, history.get(ARMADYL_HILT).get(0).profitGp);
        assertNull(history.get(ARMADYL_HILT).get(0).conversionKind);
    }

    @Test
    public void aPartTheHistoryListsBeforeTheSaleDoesNotRescueOneItListsAfter() {
        // Boots in stock when the sale comes, the core bought after it, all in
        // one read. Pass one refuses for want of the core; the retry may not
        // then take the core, because the same read says it came later.
        List<Delta> deltas = Arrays.asList(
            buy(5_000L, SYNCED, BANDOS_BOOTS, 1, BOOTS_COST),
            sale(5_008L, SYNCED + 1),
            buy(5_016L, SYNCED + 2, CORE, 1, CORE_COST)
        );

        assertTrue(guardianBootsHistory(deltas).isEmpty());
    }

    @Test
    public void partsFromALaterImportStillCoverAnEarlierSyncedSale() {
        // Two reads of the history. The sale came in the first, the parts in
        // the second, and the second read starts its slots from the same number
        // again - which is how the two are told apart. Nothing orders one read
        // against another, so this is the invented-timestamp case the retry
        // exists for, and it still takes it.
        List<Delta> deltas = Arrays.asList(
            sale(5_000L, SYNCED),
            buy(9_000L, SYNCED, BANDOS_BOOTS, 1, BOOTS_COST),
            buy(9_008L, SYNCED + 1, CORE, 1, CORE_COST)
        );

        List<StatsFlipInstance> history = guardianBootsHistory(deltas);

        assertEquals(1, history.size());
        assertEquals(150_910L, history.get(0).profitGp);
        assertEquals(Confidence.LIKELY, history.get(0).conversionConfidence);
    }

    @Test
    public void aLaterImportsPartsAreSpentOnOneSaleNotEveryEarlierOne() {
        // Two sales in one read, then that read's parts, then a second read's
        // parts. The first sale may take the second read's parts; what is left
        // for the second sale is the first read's, which that read lists after
        // it - so it is refused, rather than both sales being called assembles
        // out of one pair of parts each.
        List<Delta> deltas = Arrays.asList(
            sale(5_000L, SYNCED),
            sale(5_008L, SYNCED + 1),
            buy(5_016L, SYNCED + 2, BANDOS_BOOTS, 1, BOOTS_COST),
            buy(5_024L, SYNCED + 3, CORE, 1, CORE_COST),
            buy(9_000L, SYNCED, BANDOS_BOOTS, 1, BOOTS_COST),
            buy(9_008L, SYNCED + 1, CORE, 1, CORE_COST)
        );

        List<StatsFlipInstance> history = guardianBootsHistory(deltas);

        assertEquals(1, history.size());
        assertEquals(5_000L, history.get(0).completionTsMs);
        assertEquals(Confidence.LIKELY, history.get(0).conversionConfidence);
    }

    @Test
    public void aPartAlreadySoldLiveCannotStandInForOneListedAfterTheSale() {
        // Boots from an earlier read, sold live since - gone. A later read
        // brings a sale, then boots and a core after it. The only boots in
        // stock are the ones that read lists after the sale; the earlier
        // read's may not answer for them, because they were sold.
        List<Delta> deltas = Arrays.asList(
            buy(1_000L, SYNCED, BANDOS_BOOTS, 1, BOOTS_COST),
            sell(2_000L, 1, BANDOS_BOOTS, 750_000L, 765_306),
            sale(5_000L, SYNCED),
            buy(5_008L, SYNCED + 1, BANDOS_BOOTS, 1, BOOTS_COST),
            buy(5_016L, SYNCED + 2, CORE, 1, CORE_COST)
        );

        Map<Integer, List<StatsFlipInstance>> history = new LocalFlipHistoryService(ledger()).buildHistory(deltas, null);

        assertNull(history.get(GUARDIAN_BOOTS));
        assertEquals(1, history.get(BANDOS_BOOTS).size());
        assertEquals(18_775L, history.get(BANDOS_BOOTS).get(0).profitGp);
    }

    @Test
    public void aSyncedSaleIsNotResurrectedTwice() {
        // Re-syncing the same buys must not produce a second activity out of one
        // sale, and must not double the profit.
        List<Delta> deltas = Arrays.asList(
            sale(1_000L, 3),
            buy(5_000L, SYNCED, BANDOS_BOOTS, 1, BOOTS_COST),
            buy(5_008L, SYNCED + 1, CORE, 1, CORE_COST),
            buy(6_000L, SYNCED + 2, BANDOS_BOOTS, 1, BOOTS_COST),
            buy(6_008L, SYNCED + 3, CORE, 1, CORE_COST)
        );

        List<StatsFlipInstance> history = guardianBootsHistory(deltas);

        assertEquals(1, history.size());
        assertEquals(150_910L, history.get(0).profitGp);
    }

    @Test
    public void anOrdinaryUnmatchedSaleIsStillDropped() {
        // No recipe involved: selling something never bought stays invisible,
        // which is what the plugin has always done.
        List<Delta> deltas = Arrays.asList(
            new Delta(1_000L, 3, 4151, false, 1, 1_000_000L, "OFFER_COMPLETED", 1_020_408, false),
            buy(5_000L, SYNCED, BANDOS_BOOTS, 1, BOOTS_COST)
        );

        Map<Integer, List<StatsFlipInstance>> result =
            new LocalFlipHistoryService(ledger()).buildHistory(deltas, null);

        assertFalse(result.containsKey(4151));
    }

    @Test
    public void theStatsCacheRecoversTheSameSaleIncrementally() {
        // The cache is fed one delta at a time and has no end of stream, so its
        // retry has to be driven by the arriving synced buy. Same numbers.
        Map<Integer, StatsCacheDelta.ItemAgg> itemAggs = new HashMap<>();
        Map<Integer, StatsCacheDelta.LocalInventoryState> inventory = new HashMap<>();
        Map<Integer, StatsCacheDelta.MatchedSellMarker> markers = new HashMap<>();
        StatsCacheDelta.Totals totals = new StatsCacheDelta.Totals();
        StatsCacheDelta service =
            new StatsCacheDelta(itemAggs, inventory, markers, totals, ledger());

        service.applyDelta(sale(1_000L, 3));
        assertTrue("nothing should be attributed before the buys arrive", itemAggs.isEmpty());

        service.applyDelta(buy(5_000L, SYNCED, BANDOS_BOOTS, 1, BOOTS_COST));
        service.applyDelta(buy(5_008L, SYNCED + 1, CORE, 1, CORE_COST));

        StatsCacheDelta.ItemAgg agg = itemAggs.get(GUARDIAN_BOOTS);
        assertEquals(INPUT_COST, agg.buyCost);
        assertEquals(SALE_NET, agg.sellRevenue);
        assertEquals(1, agg.completedSells);
        assertEquals(150_910L, totals.totalProfit);
        assertEquals(27_860L, totals.totalTax);
        assertEquals(1, totals.totalCompleted);
        // The buys were replayed after the sale; a negative hold is a fiction of
        // the replay, so the activity reports none rather than a wrong one.
        assertEquals(0L, totals.totalActiveMs);
    }

    @Test
    public void theStatsCacheHonoursTheBatchOrderAsItArrives() {
        // The cache sees a sync land one delta at a time, in the history's
        // order, and a second sync start its slots over. The first read's
        // parts came after its sale and are refused; the second read's are
        // another import and are taken, exactly as the flip history does it.
        Map<Integer, StatsCacheDelta.ItemAgg> itemAggs = new HashMap<>();
        Map<Integer, StatsCacheDelta.LocalInventoryState> inventory = new HashMap<>();
        Map<Integer, StatsCacheDelta.MatchedSellMarker> markers = new HashMap<>();
        StatsCacheDelta.Totals totals = new StatsCacheDelta.Totals();
        StatsCacheDelta service =
            new StatsCacheDelta(itemAggs, inventory, markers, totals, ledger());

        service.applyDelta(sale(5_000L, SYNCED));
        service.applyDelta(buy(5_008L, SYNCED + 1, BANDOS_BOOTS, 1, BOOTS_COST));
        service.applyDelta(buy(5_016L, SYNCED + 2, CORE, 1, CORE_COST));
        assertTrue("the parts this read lists after the sale do not explain it", itemAggs.isEmpty());
        assertEquals(0L, totals.totalProfit);
        assertEquals(0, totals.totalCompleted);

        service.applyDelta(buy(9_000L, SYNCED, BANDOS_BOOTS, 1, BOOTS_COST));
        service.applyDelta(buy(9_008L, SYNCED + 1, CORE, 1, CORE_COST));
        StatsCacheDelta.ItemAgg agg = itemAggs.get(GUARDIAN_BOOTS);
        assertEquals(INPUT_COST, agg.buyCost);
        assertEquals(150_910L, totals.totalProfit);
        assertEquals(1, totals.totalCompleted);
    }
}
