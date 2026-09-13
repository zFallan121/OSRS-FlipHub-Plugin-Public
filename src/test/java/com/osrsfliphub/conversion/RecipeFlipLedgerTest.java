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
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RecipeFlipLedgerTest {
    private static final int BLADE = 11690;
    private static final int HILT = 11810;
    private static final int GODSWORD = 11802;
    private static final int TORVA_DAMAGED = 28256;
    private static final int TORVA = 26382;

    private static Delta buy(long tsMs, int slot, int itemId, int qty, long gp) {
        return new Delta(tsMs, slot, itemId, true, qty, gp, "OFFER_COMPLETED", (int) (gp / qty), false);
    }

    /** Sales are stored net of tax, with the gross unit price alongside. */
    private static Delta sell(long tsMs, int slot, int itemId, int qty, long grossUnit) {
        long net = grossUnit * qty - GeTax.forSale(itemId, (int) grossUnit, qty);
        return new Delta(tsMs, slot, itemId, false, qty, net, "OFFER_COMPLETED", (int) grossUnit, false);
    }

    private static RecipeFlip.Part part(Delta delta, int qty) {
        return new RecipeFlip.Part(TradeKey.of(delta), qty);
    }

    /**
     * The case the whole feature exists for. Buy a blade for 4,000,000 and a hilt for
     * 11,000,000, combine them, sell the godsword for 18,500,000 gross. Tax is 370,000, so
     * 18,130,000 comes back against 15,000,000 spent: 3,130,000.
     */
    @Test
    public void aRecordedAssemblePricesTheWholeConversion() {
        Delta blade = buy(1_000L, 1, BLADE, 1, 4_000_000L);
        Delta hilt = buy(2_000L, 2, HILT, 1, 11_000_000L);
        Delta sale = sell(3_000L, 3, GODSWORD, 1, 18_500_000L);
        RecipeFlip flip = new RecipeFlip(ConversionKind.ASSEMBLE, "Armadyl godsword",
            Arrays.asList(part(blade, 1), part(hilt, 1)),
            Collections.singletonList(part(sale, 1)), 0L, 9_000L);

        RecipeFlipLedger.Result result = RecipeFlipLedger.apply(
            Arrays.asList(blade, hilt, sale), Collections.singletonList(flip));

        assertEquals(1, result.activities.size());
        RecipeFlipLedger.Activity activity = result.activities.get(0);
        assertEquals(GODSWORD, activity.itemId);
        assertEquals(ConversionKind.ASSEMBLE, activity.kind);
        assertEquals(15_000_000L, activity.costGp);
        assertEquals(18_130_000L, activity.revenueGp);
        assertEquals(3_130_000L, activity.profitGp());
        assertEquals(370_000L, activity.taxGp);
        assertEquals(1, activity.quantity);

        // all three trades are spoken for, so the plain replay must skip them entirely
        assertEquals(1, result.claimedOn(blade));
        assertEquals(1, result.claimedOn(hilt));
        assertEquals(1, result.claimedOn(sale));
    }

    /** A repair costs coins as well as parts, and the fee has to land in the cost. */
    @Test
    public void aFeeIsPartOfTheCost() {
        Delta damaged = buy(1_000L, 1, TORVA_DAMAGED, 1, 100_000_000L);
        Delta sale = sell(2_000L, 2, TORVA, 1, 120_000_000L);
        RecipeFlip flip = new RecipeFlip(ConversionKind.REPAIR, "Torva full helm",
            Collections.singletonList(part(damaged, 1)),
            Collections.singletonList(part(sale, 1)), 1_500_000L, 3_000L);

        RecipeFlipLedger.Activity activity = RecipeFlipLedger
            .apply(Arrays.asList(damaged, sale), Collections.singletonList(flip))
            .activities.get(0);

        assertEquals(101_500_000L, activity.costGp);
        assertEquals(117_600_000L, activity.revenueGp);
        assertEquals(16_100_000L, activity.profitGp());
    }

    /** Only part of a purchase may go into a conversion; the rest stays an ordinary flip. */
    @Test
    public void onlyThePartUsedIsTakenOutOfThePlainReplay() {
        Delta blades = buy(1_000L, 1, BLADE, 4, 16_000_000L);
        Delta hilt = buy(2_000L, 2, HILT, 1, 11_000_000L);
        Delta sale = sell(3_000L, 3, GODSWORD, 1, 18_500_000L);
        RecipeFlip flip = new RecipeFlip(ConversionKind.ASSEMBLE, "Armadyl godsword",
            Arrays.asList(part(blades, 1), part(hilt, 1)),
            Collections.singletonList(part(sale, 1)), 0L, 9_000L);

        RecipeFlipLedger.Result result = RecipeFlipLedger.apply(
            Arrays.asList(blades, hilt, sale), Collections.singletonList(flip));

        assertEquals("one of the four blades", 1, result.claimedOn(blades));
        assertEquals("a quarter of what they cost", 15_000_000L, result.activities.get(0).costGp);
    }

    /** Taking something apart: one purchase in, several sales out. */
    @Test
    public void aRecordedBreakIsOneActivityAgainstTheThingTakenApart() {
        Delta godsword = buy(1_000L, 1, GODSWORD, 1, 18_000_000L);
        Delta bladeSale = sell(2_000L, 2, BLADE, 1, 4_200_000L);
        Delta hiltSale = sell(3_000L, 3, HILT, 1, 12_000_000L);
        RecipeFlip flip = new RecipeFlip(ConversionKind.DISASSEMBLE, "Armadyl godsword",
            Collections.singletonList(part(godsword, 1)),
            Arrays.asList(part(bladeSale, 1), part(hiltSale, 1)), 0L, 9_000L);

        RecipeFlipLedger.Activity activity = RecipeFlipLedger
            .apply(Arrays.asList(godsword, bladeSale, hiltSale), Collections.singletonList(flip))
            .activities.get(0);

        assertEquals("filed against what was taken apart", GODSWORD, activity.itemId);
        assertEquals(18_000_000L, activity.costGp);
        assertEquals(4_116_000L + 11_760_000L, activity.revenueGp);
        assertEquals(84_000L + 240_000L, activity.taxGp);
    }

    /** A record naming a trade that is no longer stored is skipped whole, not part-applied. */
    @Test
    public void aRecordWhoseTradesAreGoneIsIgnored() {
        Delta blade = buy(1_000L, 1, BLADE, 1, 4_000_000L);
        Delta sale = sell(3_000L, 3, GODSWORD, 1, 18_500_000L);
        RecipeFlip flip = new RecipeFlip(ConversionKind.ASSEMBLE, "Armadyl godsword",
            Arrays.asList(part(blade, 1), new RecipeFlip.Part(new TradeKey(99_000L, 9, HILT), 1)),
            Collections.singletonList(part(sale, 1)), 0L, 9_000L);

        RecipeFlipLedger.Result result = RecipeFlipLedger.apply(
            Arrays.asList(blade, sale), Collections.singletonList(flip));

        assertTrue(result.isEmpty());
        assertEquals("the blade stays an ordinary purchase", 0, result.claimedOn(blade));
    }

    /** Two records competing for one purchase: the older wins, and the same way every replay. */
    @Test
    public void twoRecordsCompetingForOnePurchaseResolveTheSameWayEveryTime() {
        Delta blade = buy(1_000L, 1, BLADE, 1, 4_000_000L);
        Delta hilt = buy(2_000L, 2, HILT, 2, 22_000_000L);
        Delta first = sell(3_000L, 3, GODSWORD, 1, 18_500_000L);
        Delta second = sell(4_000L, 4, GODSWORD, 1, 18_000_000L);
        RecipeFlip older = new RecipeFlip(ConversionKind.ASSEMBLE, "Armadyl godsword",
            Arrays.asList(part(blade, 1), part(hilt, 1)),
            Collections.singletonList(part(first, 1)), 0L, 10L);
        RecipeFlip newer = new RecipeFlip(ConversionKind.ASSEMBLE, "Armadyl godsword",
            Arrays.asList(part(blade, 1), part(hilt, 1)),
            Collections.singletonList(part(second, 1)), 0L, 20L);

        List<Delta> deltas = Arrays.asList(blade, hilt, first, second);
        RecipeFlipLedger.Result a = RecipeFlipLedger.apply(deltas, Arrays.asList(older, newer));
        RecipeFlipLedger.Result b = RecipeFlipLedger.apply(deltas, Arrays.asList(newer, older));

        assertEquals("only one blade, so only one conversion", 1, a.activities.size());
        assertEquals(a.activities.size(), b.activities.size());
        assertEquals(a.activities.get(0).completionTsMs, b.activities.get(0).completionTsMs);
        assertEquals("the older record took it", first.closedAtMs(), a.activities.get(0).completionTsMs);
    }

    /** A record that calls a sale a purchase describes something that did not happen. */
    @Test
    public void aRecordWithTheSidesTheWrongWayRoundIsIgnored() {
        Delta blade = buy(1_000L, 1, BLADE, 1, 4_000_000L);
        Delta sale = sell(3_000L, 3, GODSWORD, 1, 18_500_000L);
        RecipeFlip flip = new RecipeFlip(ConversionKind.ASSEMBLE, "Armadyl godsword",
            Collections.singletonList(part(sale, 1)),
            Collections.singletonList(part(blade, 1)), 0L, 9_000L);

        assertTrue(RecipeFlipLedger.apply(Arrays.asList(blade, sale),
            Collections.singletonList(flip)).isEmpty());
    }

    @Test
    public void noRecordsMeansNothingChanges() {
        Delta blade = buy(1_000L, 1, BLADE, 1, 4_000_000L);

        assertTrue(RecipeFlipLedger.apply(Collections.singletonList(blade),
            Collections.emptyList()).isEmpty());
    }
}
