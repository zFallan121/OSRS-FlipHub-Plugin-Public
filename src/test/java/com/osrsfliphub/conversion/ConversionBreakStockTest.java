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
import static org.junit.Assert.assertTrue;

/**
 * The pieces a break produces are not stock a recipe may spend, and they are not
 * units the bought cost may be divided across.
 *
 * <p>Both of these were real, and both overstated profit by millions on the very
 * items a converter trades. They share a cause: a break piece sits in the same
 * bucket as bought stock but carries no cost, so any code that treated the bucket
 * as uniform priced it at zero.</p>
 */
public class ConversionBreakStockTest {
    private static final int ARMADYL_GODSWORD = 11802;
    private static final int GODSWORD_BLADE = 11690;
    private static final int ARMADYL_HILT = 11810;

    private static ConversionRecipe assembleGodsword() {
        return new ConversionRecipe(
            ConversionKind.ASSEMBLE,
            "Armadyl godsword",
            Arrays.asList(new ConversionItem(GODSWORD_BLADE, 1), new ConversionItem(ARMADYL_HILT, 1)),
            Collections.singletonList(new ConversionItem(ARMADYL_GODSWORD, 1)),
            0L);
    }

    private static ConversionRecipe disassembleGodsword() {
        return new ConversionRecipe(
            ConversionKind.DISASSEMBLE,
            "Armadyl godsword",
            Collections.singletonList(new ConversionItem(ARMADYL_GODSWORD, 1)),
            Arrays.asList(new ConversionItem(GODSWORD_BLADE, 1), new ConversionItem(ARMADYL_HILT, 1)),
            0L);
    }

    private static LocalFlipHistoryService serviceWith(ConversionRecipe... recipes) {
        return new LocalFlipHistoryService(new Ledger(new RecipeIndex(Arrays.asList(recipes))));
    }

    private static Delta buy(long tsMs, int slot, int itemId, int qty, long gp) {
        return new Delta(tsMs, slot, itemId, true, qty, gp, "OFFER_COMPLETED", (int) (gp / qty), false);
    }

    /** Sales are stored net of tax, with the gross unit price kept alongside. */
    private static Delta sell(long tsMs, int slot, int itemId, int qty, long grossUnit) {
        long gross = grossUnit * qty;
        long net = gross - GeTax.forSale(itemId, (int) grossUnit, qty);
        return new Delta(tsMs, slot, itemId, false, qty, net, "OFFER_COMPLETED", (int) grossUnit, false);
    }

    private static long totalProfit(LocalFlipHistoryService service, List<Delta> deltas) {
        Map<Integer, List<StatsFlipInstance>> byItem = service.buildHistory(deltas, null);
        long profit = 0L;
        for (List<StatsFlipInstance> entries : byItem.values()) {
            for (StatsFlipInstance entry : entries) {
                if (entry.counted()) {
                    profit += entry.profitGp;
                }
            }
        }
        return profit;
    }

    private static List<StatsFlipInstance> allEntries(LocalFlipHistoryService service, List<Delta> deltas) {
        Map<Integer, List<StatsFlipInstance>> byItem = service.buildHistory(deltas, null);
        List<StatsFlipInstance> all = new ArrayList<>();
        for (List<StatsFlipInstance> entries : byItem.values()) {
            all.addAll(entries);
        }
        return all;
    }

    /**
     * Buy a godsword, break it, sell one piece, buy the other piece back, sell the
     * rebuilt godsword. The rebuild must not be allowed to take the break's blade
     * as a free ingredient.
     *
     * <p>What the player really did: paid 18,000,000 and 11,000,000, received
     * 11,760,000 and 18,130,000 after tax, so 890,000 of real profit. The plugin
     * used to report 7,130,000, because the rebuild helped itself to the break's
     * blade at a cost of zero.</p>
     *
     * <p>It now reports nothing for this sequence instead, and that is the
     * deliberate choice: a break's pieces are spoken for, so they are not offered
     * to a recipe at all. The break stays open and uncounted, and the godsword
     * sale finds no stock to explain it. The position is shown as unfinished
     * rather than resolved at a made-up number.</p>
     *
     * <p>The remaining gap, which this test also pins: settling a break whose
     * piece is legitimately consumed by another recipe would let the 890,000 be
     * reported properly. That needs the break to be settled at the consumed
     * piece's share rather than left outstanding, which is a larger change than
     * stopping the overstatement.</p>
     */
    @Test
    public void aRebuildCannotSpendTheBreaksOwnPieceAsFreeStock() {
        LocalFlipHistoryService service = serviceWith(assembleGodsword(), disassembleGodsword());
        List<Delta> deltas = Arrays.asList(
            buy(1_000L, 1, ARMADYL_GODSWORD, 1, 18_000_000L),
            sell(2_000L, 2, ARMADYL_HILT, 1, 12_000_000L),
            buy(3_000L, 3, ARMADYL_HILT, 1, 11_000_000L),
            sell(4_000L, 4, ARMADYL_GODSWORD, 1, 18_500_000L));

        List<StatsFlipInstance> entries = allEntries(service, deltas);

        assertEquals("no profit may be invented here; the bug reported 7,130,000",
            0L, totalProfit(service, deltas));

        boolean sawOpenBreak = false;
        for (StatsFlipInstance entry : entries) {
            if (entry.conversionKind == ConversionKind.DISASSEMBLE) {
                sawOpenBreak = true;
                assertFalse("an unfinished break must not count", entry.counted());
            }
            for (Match.Line line : entry.conversionLines) {
                if (!line.fee) {
                    assertTrue("an ingredient was taken at no cost: item " + line.itemId,
                        line.costGp > 0L);
                }
            }
        }
        assertTrue("the break should still be shown, as unfinished", sawOpenBreak);
    }

    /**
     * A bucket holding one bought blade and one blade out of a break must charge
     * the bought blade its whole cost when it sells.
     *
     * <p>Real profit on the blade flip: paid 4,000,000, received 4,116,000 after
     * tax, so 116,000. Splitting the cost across the zero-cost break piece charged
     * it only 2,000,000 and reported 2,116,000.</p>
     */
    @Test
    public void aBoughtUnitCarriesItsWholeCostWhenABreakPieceSharesTheBucket() {
        LocalFlipHistoryService service = serviceWith(assembleGodsword(), disassembleGodsword());
        List<Delta> deltas = Arrays.asList(
            buy(1_000L, 1, GODSWORD_BLADE, 1, 4_000_000L),
            buy(2_000L, 2, ARMADYL_GODSWORD, 1, 18_000_000L),
            sell(3_000L, 3, ARMADYL_HILT, 1, 12_000_000L),
            sell(4_000L, 4, GODSWORD_BLADE, 1, 4_200_000L));

        long bladeProfit = 0L;
        for (StatsFlipInstance entry : allEntries(service, deltas)) {
            if (entry.itemId == GODSWORD_BLADE && entry.counted()) {
                bladeProfit += entry.profitGp;
            }
        }

        assertEquals("the pro-rating bug reported 2,116,000 here", 116_000L, bladeProfit);
    }
}
