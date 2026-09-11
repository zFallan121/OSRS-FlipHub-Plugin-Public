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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The other half of the end-to-end case. The flip-history ledger produces the
 * activity; this one produces the per-item row and the summary the Profile tab
 * actually renders, and it has to agree with the first to the coin.
 */
public class LocalStatsCacheConversionTest {
    private static final int CORE = 21730;
    private static final int BANDOS_BOOTS = 11836;
    private static final int GUARDIAN_BOOTS = 21733;

    private static final int DHAROKS_HELM = 4716;
    private static final int DHAROKS_HELM_0 = 4880;
    private static final int DHAROKS_PLATEBODY = 4720;
    private static final int DHAROKS_PLATELEGS = 4722;
    private static final int DHAROKS_GREATAXE = 4718;
    private static final int DHAROKS_ARMOUR_SET = 12881;

    private static final long CORE_COST = 483_005L;
    private static final long BOOTS_COST = 731_225L;
    private static final long INPUT_COST = CORE_COST + BOOTS_COST;
    private static final long SALE_NET = 1_365_140L;
    private static final int SALE_GROSS_UNIT = 1_393_000;

    private final Map<Integer, LocalStatsCacheDeltaService.LocalItemAgg> itemAggs = new HashMap<>();
    private final Map<Integer, LocalStatsCacheDeltaService.LocalInventoryState> inventory = new HashMap<>();
    private final Map<Integer, LocalStatsCacheDeltaService.MatchedSellMarker> markers = new HashMap<>();
    private final LocalStatsCacheDeltaService.Totals totals = new LocalStatsCacheDeltaService.Totals();

    private LocalStatsCacheDeltaService serviceWithGuardianBoots() {
        ConversionRecipe recipe = new ConversionRecipe(
            ConversionKind.ASSEMBLE,
            "Guardian boots",
            Arrays.asList(new ConversionItem(CORE, 1), new ConversionItem(BANDOS_BOOTS, 1)),
            Collections.singletonList(new ConversionItem(GUARDIAN_BOOTS, 1)),
            0L
        );
        ConversionLedger ledger = new ConversionLedger(new ConversionRecipeIndex(Collections.singletonList(recipe)));
        return new LocalStatsCacheDeltaService(itemAggs, inventory, markers, totals, ledger);
    }

    private static LocalTradeDelta buy(long tsMs, int slot, int itemId, int qty, long gp) {
        return new LocalTradeDelta(tsMs, slot, itemId, true, qty, gp, "OFFER_COMPLETED", (int) (gp / qty), false);
    }

    private void applyGuardianBootsTrade(LocalStatsCacheDeltaService service) {
        service.applyDelta(buy(1_000L, 1, BANDOS_BOOTS, 1, BOOTS_COST));
        service.applyDelta(buy(2_000L, 2, CORE, 1, CORE_COST));
        service.applyDelta(new LocalTradeDelta(
            3_000L, 3, GUARDIAN_BOOTS, false, 1, SALE_NET, "OFFER_COMPLETED", SALE_GROSS_UNIT, false));
    }

    @Test
    public void anAssembledItemEarnsItsOwnRowInTheProfileList() {
        LocalStatsCacheDeltaService service = serviceWithGuardianBoots();

        applyGuardianBootsTrade(service);

        LocalStatsCacheDeltaService.LocalItemAgg agg = itemAggs.get(GUARDIAN_BOOTS);
        assertNotNull("the sold item needs an aggregate or the Profile list has no row", agg);
        assertEquals(INPUT_COST, agg.buyCost);
        assertEquals(SALE_NET, agg.sellRevenue);
        // getItems() only emits an item with both a buy and a sell quantity, so
        // a converted item that recorded neither would never be shown.
        assertTrue(agg.buyQty > 0);
        assertTrue(agg.sellQty > 0);
        assertEquals(1, agg.completedSells);
    }

    @Test
    public void theSummaryAgreesWithTheActivity() {
        LocalStatsCacheDeltaService service = serviceWithGuardianBoots();

        applyGuardianBootsTrade(service);

        assertEquals(150_910L, totals.totalProfit);
        assertEquals(INPUT_COST, totals.totalCost);
        assertEquals(1L, totals.totalQty);
        assertEquals(1, totals.totalCompleted);
        // Tax on a converted sale is the ordinary tax on the ordinary sale:
        // floor(1,393,000 / 50) = 27,860.
        assertEquals(27_860L, totals.totalTax);
    }

    @Test
    public void theHoldTimeRunsFromTheFirstIngredientPurchase() {
        // Without carrying the inputs' buy time onto the item they made, the
        // hold would start at the sale and gp per hour would be infinite.
        LocalStatsCacheDeltaService service = serviceWithGuardianBoots();

        applyGuardianBootsTrade(service);

        assertEquals(Long.valueOf(1_000L), totals.firstBuyTs);
        assertEquals(2_000L, totals.totalActiveMs);
    }

    @Test
    public void theIngredientsLeaveNoRowsBehind() {
        LocalStatsCacheDeltaService service = serviceWithGuardianBoots();

        applyGuardianBootsTrade(service);

        assertNull(itemAggs.get(CORE));
        assertNull(itemAggs.get(BANDOS_BOOTS));
    }

    private static ConversionRecipe dharoksSetBreak() {
        return new ConversionRecipe(
            ConversionKind.SET_BREAK,
            "Dharok's armour set",
            Collections.singletonList(new ConversionItem(DHAROKS_ARMOUR_SET, 1)),
            Arrays.asList(
                new ConversionItem(DHAROKS_HELM, 1),
                new ConversionItem(DHAROKS_PLATEBODY, 1),
                new ConversionItem(DHAROKS_PLATELEGS, 1),
                new ConversionItem(DHAROKS_GREATAXE, 1)),
            0L);
    }

    private static LocalTradeDelta sell(long tsMs, int slot, int itemId, int qty, long gp, int unitPrice) {
        return new LocalTradeDelta(tsMs, slot, itemId, false, qty, gp, "OFFER_COMPLETED", unitPrice, false);
    }

    /**
     * Two sets bought together and broken, with the eight pieces sold one per
     * offer in an order that leaves both breaks open at once. Each piece's
     * gross price is its net revenue plus the two percent the exchange took.
     */
    private static List<LocalTradeDelta> twoSetsBrokenAndSoldPieceByPiece() {
        return Arrays.asList(
            buy(1_000L, 1, DHAROKS_ARMOUR_SET, 2, 14_800_000L),
            sell(2_000L, 2, DHAROKS_HELM, 1, 1_050_000L, 1_071_428),
            sell(3_000L, 3, DHAROKS_HELM, 1, 1_050_000L, 1_071_428),
            sell(4_000L, 4, DHAROKS_PLATEBODY, 1, 2_050_000L, 2_091_836),
            sell(5_000L, 5, DHAROKS_PLATEBODY, 1, 2_050_000L, 2_091_836),
            sell(6_000L, 6, DHAROKS_PLATELEGS, 1, 1_550_000L, 1_581_632),
            sell(7_000L, 7, DHAROKS_PLATELEGS, 1, 1_550_000L, 1_581_632),
            sell(8_000L, 8, DHAROKS_GREATAXE, 1, 3_050_000L, 3_112_244),
            sell(9_000L, 9, DHAROKS_GREATAXE, 1, 3_050_000L, 3_112_244)
        );
    }

    @Test
    public void twoOpenBreaksSellTheirPiecesToTheBreaksAndNotAsFlips() {
        // With both sets broken, every piece bucket holds one unit from each
        // break. Selling one piece must hand it to the older break and leave
        // the other in place for the younger one - never match it as bought
        // stock at zero cost, and never zero the break count while a break is
        // still waiting on it.
        LocalStatsCacheDeltaService service = new LocalStatsCacheDeltaService(
            itemAggs, inventory, markers, totals,
            new ConversionLedger(new ConversionRecipeIndex(Collections.singletonList(dharoksSetBreak()))));

        for (LocalTradeDelta delta : twoSetsBrokenAndSoldPieceByPiece()) {
            service.applyDelta(delta);
        }

        for (int pieceId : new int[]{DHAROKS_HELM, DHAROKS_PLATEBODY, DHAROKS_PLATELEGS, DHAROKS_GREATAXE}) {
            assertNull("a piece off a set is never a row of its own", itemAggs.get(pieceId));
        }
        LocalStatsCacheDeltaService.LocalItemAgg set = itemAggs.get(DHAROKS_ARMOUR_SET);
        assertNotNull(set);
        assertEquals(14_800_000L, set.buyCost);
        assertEquals(15_400_000L, set.sellRevenue);
        assertEquals(2, set.completedSells);

        assertEquals(600_000L, totals.totalProfit);
        assertEquals(14_800_000L, totals.totalCost);
        assertEquals(2L, totals.totalQty);
        assertEquals(2, totals.totalCompleted);
        // Two percent of each piece's gross price, floored per item, twice over:
        // (21,428 + 41,836 + 31,632 + 62,244) x 2.
        assertEquals(314_280L, totals.totalTax);
    }

    @Test
    public void theCacheAgreesWithTheFlipHistoryOnTwoBrokenSets() {
        // The header's profit comes from the flip history and the rows from
        // this cache. If they disagree the rows stop adding up to the header.
        ConversionLedger ledger =
            new ConversionLedger(new ConversionRecipeIndex(Collections.singletonList(dharoksSetBreak())));
        LocalStatsCacheDeltaService service =
            new LocalStatsCacheDeltaService(itemAggs, inventory, markers, totals, ledger);
        List<LocalTradeDelta> deltas = twoSetsBrokenAndSoldPieceByPiece();

        for (LocalTradeDelta delta : deltas) {
            service.applyDelta(delta);
        }
        Map<Integer, List<StatsFlipInstance>> history =
            new LocalFlipHistoryService(ledger).buildHistory(deltas, null);

        long historyProfit = 0L;
        long historyCost = 0L;
        long historyTax = 0L;
        int historyFlips = 0;
        for (List<StatsFlipInstance> entries : history.values()) {
            for (StatsFlipInstance instance : entries) {
                historyProfit += instance.profitGp;
                historyCost += instance.buyCostGp;
                historyTax += instance.taxGp;
                historyFlips += 1;
            }
        }
        assertEquals(2, historyFlips);
        assertEquals(historyProfit, totals.totalProfit);
        assertEquals(historyCost, totals.totalCost);
        assertEquals(historyFlips, totals.totalCompleted);
        // The header's tax comes from the history too, so a break's piece sales
        // have to hand their tax to the break there just as they do here.
        assertEquals(totals.totalTax, historyTax);
    }

    private static ConversionRecipe dharoksHelmRepair() {
        return new ConversionRecipe(
            ConversionKind.REPAIR,
            "Dharok's helm",
            Collections.singletonList(new ConversionItem(DHAROKS_HELM_0, 1)),
            Collections.singletonList(new ConversionItem(DHAROKS_HELM, 1)),
            60_000L);
    }

    /**
     * A broken helm and a set both in stock. The helm sells first, and with
     * two routes that could have made it the ledger claims neither, so the
     * sale is deferred. The body can only have come off the set, which breaks
     * it - and that is the moment the helm sale becomes explicable.
     */
    private static List<LocalTradeDelta> helmSoldWhileAmbiguousThenTheRestOfTheSet() {
        return Arrays.asList(
            buy(1_000L, 1, DHAROKS_HELM_0, 1, 800_000L),
            buy(2_000L, 2, DHAROKS_ARMOUR_SET, 1, 7_400_000L),
            sell(3_000L, 3, DHAROKS_HELM, 1, 1_050_000L, 1_071_428),
            sell(4_000L, 4, DHAROKS_PLATEBODY, 1, 2_050_000L, 2_091_836),
            sell(5_000L, 5, DHAROKS_PLATELEGS, 1, 1_550_000L, 1_581_632),
            sell(6_000L, 6, DHAROKS_GREATAXE, 1, 3_050_000L, 3_112_244)
        );
    }

    @Test
    public void aDeferredPieceSaleIsRevisitedTheMomentABreakMakesItsPiece() {
        // The cache has no end of stream to sweep deferred sales at, so it has
        // to look again whenever a conversion puts new stock in a bucket. If
        // it only looks on a synced buy, the helm's coins never reach the
        // break, the break never completes, and the set has no row while the
        // header - which comes from the flip history - counts its 300,000.
        ConversionLedger ledger = new ConversionLedger(
            new ConversionRecipeIndex(Arrays.asList(dharoksHelmRepair(), dharoksSetBreak())));
        LocalStatsCacheDeltaService service =
            new LocalStatsCacheDeltaService(itemAggs, inventory, markers, totals, ledger);

        for (LocalTradeDelta delta : helmSoldWhileAmbiguousThenTheRestOfTheSet()) {
            service.applyDelta(delta);
        }

        LocalStatsCacheDeltaService.LocalItemAgg set = itemAggs.get(DHAROKS_ARMOUR_SET);
        assertNotNull("the break has to complete and file its row against the set", set);
        assertEquals(7_400_000L, set.buyCost);
        assertEquals(7_700_000L, set.sellRevenue);
        assertEquals(1, set.completedSells);
        for (int pieceId : new int[]{DHAROKS_HELM, DHAROKS_PLATEBODY, DHAROKS_PLATELEGS, DHAROKS_GREATAXE}) {
            assertNull(itemAggs.get(pieceId));
        }
        assertEquals(300_000L, totals.totalProfit);
        assertEquals(7_400_000L, totals.totalCost);
        assertEquals(1, totals.totalCompleted);
        assertEquals(157_140L, totals.totalTax);
    }

    @Test
    public void theCacheAgreesWithTheFlipHistoryOnADeferredPieceSale() {
        ConversionLedger ledger = new ConversionLedger(
            new ConversionRecipeIndex(Arrays.asList(dharoksHelmRepair(), dharoksSetBreak())));
        LocalStatsCacheDeltaService service =
            new LocalStatsCacheDeltaService(itemAggs, inventory, markers, totals, ledger);
        List<LocalTradeDelta> deltas = helmSoldWhileAmbiguousThenTheRestOfTheSet();

        for (LocalTradeDelta delta : deltas) {
            service.applyDelta(delta);
        }
        Map<Integer, List<StatsFlipInstance>> history =
            new LocalFlipHistoryService(ledger).buildHistory(deltas, null);

        long historyProfit = 0L;
        long historyCost = 0L;
        long historyTax = 0L;
        int historyFlips = 0;
        for (List<StatsFlipInstance> entries : history.values()) {
            for (StatsFlipInstance instance : entries) {
                historyProfit += instance.profitGp;
                historyCost += instance.buyCostGp;
                historyTax += instance.taxGp;
                historyFlips += 1;
            }
        }
        assertEquals(1, historyFlips);
        assertEquals(ConversionKind.SET_BREAK, history.get(DHAROKS_ARMOUR_SET).get(0).conversionKind);
        assertEquals(historyProfit, totals.totalProfit);
        assertEquals(historyCost, totals.totalCost);
        assertEquals(historyFlips, totals.totalCompleted);
        assertEquals(totals.totalTax, historyTax);
    }

    @Test
    public void withoutALedgerTheSaleIsStillDropped() {
        LocalStatsCacheDeltaService service =
            new LocalStatsCacheDeltaService(itemAggs, inventory, markers, totals);

        applyGuardianBootsTrade(service);

        assertNull(itemAggs.get(GUARDIAN_BOOTS));
        assertEquals(0L, totals.totalProfit);
    }
}
