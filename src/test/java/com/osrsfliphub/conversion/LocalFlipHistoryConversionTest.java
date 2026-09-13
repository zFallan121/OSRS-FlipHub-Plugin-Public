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
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The Guardian boots trade end to end, and the six ways the matcher is supposed
 * to say no.
 *
 * <p>Figures are a real Grand Exchange history: bought at 731,225 and 483,005,
 * sold for 1,393,000 less 27,860 tax. Sale revenue arrives already netted,
 * exactly as the offer pipeline delivers it, so nothing here re-derives tax.
 */
public class LocalFlipHistoryConversionTest {
    private static final int CORE = 21730;
    private static final int BANDOS_BOOTS = 11836;
    private static final int GUARDIAN_BOOTS = 21733;
    private static final int SPEAR = 11824;
    private static final int HASTA = 11889;

    private static final long CORE_COST = 483_005L;
    private static final long BOOTS_COST = 731_225L;
    private static final long INPUT_COST = CORE_COST + BOOTS_COST;
    private static final long SALE_NET = 1_365_140L;
    private static final long SALE_GROSS_UNIT = 1_393_000L;
    private static final long PROFIT = SALE_NET - INPUT_COST;

    private static ConversionRecipe guardianBoots() {
        return new ConversionRecipe(
            ConversionKind.ASSEMBLE,
            "Guardian boots",
            Arrays.asList(new ConversionItem(CORE, 1), new ConversionItem(BANDOS_BOOTS, 1)),
            Collections.singletonList(new ConversionItem(GUARDIAN_BOOTS, 1)),
            0L
        );
    }

    private static LocalFlipHistoryService serviceWith(ConversionRecipe... recipes) {
        RecipeIndex index = new RecipeIndex(Arrays.asList(recipes));
        return new LocalFlipHistoryService(new Ledger(index));
    }

    private static Delta delta(long tsMs, int slot, int itemId, boolean isBuy,
                                         int qty, long gp, String eventType, int price) {
        return new Delta(tsMs, slot, itemId, isBuy, qty, gp, eventType, price, false);
    }

    private static Delta buy(long tsMs, int slot, int itemId, int qty, long gp) {
        return delta(tsMs, slot, itemId, true, qty, gp, "OFFER_COMPLETED", (int) (gp / qty));
    }

    private static List<StatsFlipInstance> historyFor(LocalFlipHistoryService service,
                                                      List<Delta> deltas,
                                                      int itemId) {
        Map<Integer, List<StatsFlipInstance>> result = service.buildHistory(deltas, null);
        List<StatsFlipInstance> history = result.get(itemId);
        return history != null ? history : new ArrayList<>();
    }

    @Test
    public void assemblesGuardianBootsFromTwoIngredientBuys() {
        LocalFlipHistoryService service = serviceWith(guardianBoots());
        List<Delta> deltas = Arrays.asList(
            buy(1_000L, 1, BANDOS_BOOTS, 1, BOOTS_COST),
            buy(2_000L, 2, CORE, 1, CORE_COST),
            delta(3_000L, 3, GUARDIAN_BOOTS, false, 1, SALE_NET, "OFFER_COMPLETED", (int) SALE_GROSS_UNIT)
        );

        List<StatsFlipInstance> history = historyFor(service, deltas, GUARDIAN_BOOTS);

        assertEquals(1, history.size());
        StatsFlipInstance activity = history.get(0);
        assertEquals(INPUT_COST, activity.buyCostGp);
        assertEquals(SALE_NET, activity.sellRevenueGp);
        assertEquals(PROFIT, activity.profitGp);
        assertEquals(150_910L, activity.profitGp);
        assertEquals(1, activity.quantity);
        assertEquals(3_000L, activity.completionTsMs);

        assertEquals(ConversionKind.ASSEMBLE, activity.conversionKind);
        assertEquals("Guardian boots", activity.conversionName);
        assertEquals(2, activity.conversionLines.size());
        long lineTotal = 0L;
        for (Match.Line line : activity.conversionLines) {
            lineTotal += line.costGp;
            assertFalse(line.fee);
        }
        assertEquals(INPUT_COST, lineTotal);
    }

    @Test
    public void theIngredientsAreConsumedAndDoNotBecomeFlipsOfTheirOwn() {
        LocalFlipHistoryService service = serviceWith(guardianBoots());
        List<Delta> deltas = Arrays.asList(
            buy(1_000L, 1, BANDOS_BOOTS, 1, BOOTS_COST),
            buy(2_000L, 2, CORE, 1, CORE_COST),
            delta(3_000L, 3, GUARDIAN_BOOTS, false, 1, SALE_NET, "OFFER_COMPLETED", (int) SALE_GROSS_UNIT)
        );

        Map<Integer, List<StatsFlipInstance>> result = service.buildHistory(deltas, null);

        assertFalse(result.containsKey(CORE));
        assertFalse(result.containsKey(BANDOS_BOOTS));
    }

    @Test
    public void anOrdinaryFlipOfAnIngredientIsUntouched() {
        // Bandos boots are an input to a live recipe. Buying and selling them is
        // still just a flip, and must never be eaten by the conversion branch.
        LocalFlipHistoryService service = serviceWith(guardianBoots());
        List<Delta> deltas = Arrays.asList(
            buy(1_000L, 1, BANDOS_BOOTS, 1, 700_000L),
            delta(2_000L, 1, BANDOS_BOOTS, false, 1, 731_225L, "OFFER_COMPLETED", 746_148)
        );

        List<StatsFlipInstance> history = historyFor(service, deltas, BANDOS_BOOTS);

        assertEquals(1, history.size());
        assertEquals(31_225L, history.get(0).profitGp);
        assertNull(history.get(0).conversionKind);
    }

    @Test
    public void aPurchasedOutputIsUsedBeforeAnyConversion() {
        // One Guardian boots bought outright and one made. Selling a single pair
        // must spend the purchased one and leave the ingredients alone.
        LocalFlipHistoryService service = serviceWith(guardianBoots());
        List<Delta> deltas = Arrays.asList(
            buy(1_000L, 1, BANDOS_BOOTS, 1, BOOTS_COST),
            buy(2_000L, 2, CORE, 1, CORE_COST),
            buy(3_000L, 3, GUARDIAN_BOOTS, 1, 1_300_000L),
            delta(4_000L, 4, GUARDIAN_BOOTS, false, 1, SALE_NET, "OFFER_COMPLETED", (int) SALE_GROSS_UNIT)
        );

        List<StatsFlipInstance> history = historyFor(service, deltas, GUARDIAN_BOOTS);

        assertEquals(1, history.size());
        assertEquals(1_300_000L, history.get(0).buyCostGp);
        assertNull(history.get(0).conversionKind);
    }

    /**
     * The numbers still come from both sources; the breakdown does not, because
     * no single recipe explains a cost basis that is half a purchase.
     */
    @Test
    public void convertsOnlyTheShortfallWhenSomeOutputWasBought() {
        LocalFlipHistoryService service = serviceWith(guardianBoots());
        List<Delta> deltas = Arrays.asList(
            buy(1_000L, 1, BANDOS_BOOTS, 1, BOOTS_COST),
            buy(2_000L, 2, CORE, 1, CORE_COST),
            buy(3_000L, 3, GUARDIAN_BOOTS, 1, 1_300_000L),
            delta(4_000L, 4, GUARDIAN_BOOTS, false, 2, SALE_NET * 2, "OFFER_COMPLETED", (int) SALE_GROSS_UNIT)
        );

        List<StatsFlipInstance> history = historyFor(service, deltas, GUARDIAN_BOOTS);

        assertEquals(1, history.size());
        StatsFlipInstance activity = history.get(0);
        assertEquals(2, activity.quantity);
        assertEquals(1_300_000L + INPUT_COST, activity.buyCostGp);
        assertNull("one pair was bought, so the pair is not an assemble", activity.conversionKind);
    }

    @Test
    public void aMissingIngredientConvertsNothingAndDropsTheSale() {
        // Half a cost basis reads as profit that was never made, so partial
        // stock does not convert and the sale is discarded exactly as before.
        LocalFlipHistoryService service = serviceWith(guardianBoots());
        List<Delta> deltas = Arrays.asList(
            buy(1_000L, 1, BANDOS_BOOTS, 1, BOOTS_COST),
            delta(3_000L, 3, GUARDIAN_BOOTS, false, 1, SALE_NET, "OFFER_COMPLETED", (int) SALE_GROSS_UNIT)
        );

        Map<Integer, List<StatsFlipInstance>> result = service.buildHistory(deltas, null);

        assertFalse(result.containsKey(GUARDIAN_BOOTS));
    }

    @Test
    public void ingredientsBoughtAfterTheSaleAreNotUsed() {
        // Ordering needs no timestamps: the buckets hold only what preceded the
        // sale, so a later purchase simply is not there to convert.
        LocalFlipHistoryService service = serviceWith(guardianBoots());
        List<Delta> deltas = Arrays.asList(
            buy(1_000L, 1, BANDOS_BOOTS, 1, BOOTS_COST),
            delta(2_000L, 3, GUARDIAN_BOOTS, false, 1, SALE_NET, "OFFER_COMPLETED", (int) SALE_GROSS_UNIT),
            buy(3_000L, 2, CORE, 1, CORE_COST)
        );

        Map<Integer, List<StatsFlipInstance>> result = service.buildHistory(deltas, null);

        assertFalse(result.containsKey(GUARDIAN_BOOTS));
    }

    @Test
    public void twoRoutesToTheSameItemConvertNeither() {
        ConversionRecipe viaCore = guardianBoots();
        ConversionRecipe viaSpear = new ConversionRecipe(
            ConversionKind.ASSEMBLE,
            "Guardian boots (other route)",
            Collections.singletonList(new ConversionItem(SPEAR, 1)),
            Collections.singletonList(new ConversionItem(GUARDIAN_BOOTS, 1)),
            0L
        );
        LocalFlipHistoryService service = serviceWith(viaCore, viaSpear);
        List<Delta> deltas = Arrays.asList(
            buy(1_000L, 1, BANDOS_BOOTS, 1, BOOTS_COST),
            buy(2_000L, 2, CORE, 1, CORE_COST),
            buy(2_500L, 5, SPEAR, 1, 900_000L),
            delta(3_000L, 3, GUARDIAN_BOOTS, false, 1, SALE_NET, "OFFER_COMPLETED", (int) SALE_GROSS_UNIT)
        );

        Map<Integer, List<StatsFlipInstance>> result = service.buildHistory(deltas, null);

        assertFalse(result.containsKey(GUARDIAN_BOOTS));
    }

    @Test
    public void anUnavoidableNpcFeeIsChargedToTheActivity() {
        ConversionRecipe hasta = new ConversionRecipe(
            ConversionKind.ASSEMBLE,
            "Zamorakian hasta",
            Collections.singletonList(new ConversionItem(SPEAR, 1)),
            Collections.singletonList(new ConversionItem(HASTA, 1)),
            300_000L
        );
        LocalFlipHistoryService service = serviceWith(hasta);
        List<Delta> deltas = Arrays.asList(
            buy(1_000L, 1, SPEAR, 1, 900_000L),
            delta(2_000L, 2, HASTA, false, 1, 1_470_000L, "OFFER_COMPLETED", 1_500_000)
        );

        List<StatsFlipInstance> history = historyFor(service, deltas, HASTA);

        assertEquals(1, history.size());
        StatsFlipInstance activity = history.get(0);
        assertEquals(1_200_000L, activity.buyCostGp);
        assertEquals(270_000L, activity.profitGp);

        boolean sawFee = false;
        for (Match.Line line : activity.conversionLines) {
            if (line.fee) {
                sawFee = true;
                assertEquals(300_000L, line.costGp);
                assertEquals(0, line.itemId);
            }
        }
        assertTrue("the fee should be its own line in the breakdown", sawFee);
    }

    @Test
    public void withoutALedgerNothingConverts() {
        // The parity guarantee: an absent recipe table has to leave the matcher
        // behaving exactly as it did before conversions existed.
        LocalFlipHistoryService service = new LocalFlipHistoryService();
        List<Delta> deltas = Arrays.asList(
            buy(1_000L, 1, BANDOS_BOOTS, 1, BOOTS_COST),
            buy(2_000L, 2, CORE, 1, CORE_COST),
            delta(3_000L, 3, GUARDIAN_BOOTS, false, 1, SALE_NET, "OFFER_COMPLETED", (int) SALE_GROSS_UNIT)
        );

        Map<Integer, List<StatsFlipInstance>> result = service.buildHistory(deltas, null);

        assertTrue(result.isEmpty());
    }
}
