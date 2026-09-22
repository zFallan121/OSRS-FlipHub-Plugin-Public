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
import static org.junit.Assert.assertTrue;

/** The recorded conversion, seen through the ledger the Profile tab actually reads. */
public class RecordedConversionHistoryTest {
    private static final int BLADE = 11690;
    private static final int HILT = 11810;
    private static final int GODSWORD = 11802;
    private static final long ACCOUNT = 7L;

    private static Delta buy(long tsMs, int slot, int itemId, int qty, long gp) {
        return new Delta(tsMs, slot, itemId, true, qty, gp, "OFFER_COMPLETED", (int) (gp / qty), false);
    }

    private static Delta sell(long tsMs, int slot, int itemId, int qty, long grossUnit) {
        long net = grossUnit * qty - GeTax.forSale(itemId, (int) grossUnit, qty);
        return new Delta(tsMs, slot, itemId, false, qty, net, "OFFER_COMPLETED", (int) grossUnit, false);
    }

    private static RecipeFlip.Part part(Delta delta, int qty) {
        return new RecipeFlip.Part(TradeKey.of(delta), qty, null);
    }


    private static List<StatsFlipInstance> allEntries(Map<Integer, List<StatsFlipInstance>> byItem) {
        List<StatsFlipInstance> out = new ArrayList<>();
        for (List<StatsFlipInstance> list : byItem.values()) {
            out.addAll(list);
        }
        return out;
    }

    /**
     * Two purchases and a sale the player says were one conversion. Paid 15,000,000, received
     * 18,130,000 after tax: one activity worth 3,130,000, and no separate flip for any of the
     * three trades, because all of them were used up by it.
     */
    @Test
    public void aRecordedConversionBecomesOneActivityAndConsumesItsTrades() {
        Delta blade = buy(1_000L, 1, BLADE, 1, 4_000_000L);
        Delta hilt = buy(2_000L, 2, HILT, 1, 11_000_000L);
        Delta sale = sell(3_000L, 3, GODSWORD, 1, 18_500_000L);
        RecipeFlipStore store = new RecipeFlipStore();
        store.add(ACCOUNT, new RecipeFlip(ConversionKind.ASSEMBLE, "Armadyl godsword",
            Arrays.asList(part(blade, 1), part(hilt, 1)),
            Collections.singletonList(part(sale, 1)), 0L, 9_000L, null, null));

        List<StatsFlipInstance> entries = allEntries(new LocalFlipHistoryService(store)
            .buildHistory(Arrays.asList(blade, hilt, sale), null, ACCOUNT));

        assertEquals(1, entries.size());
        StatsFlipInstance activity = entries.get(0);
        assertEquals(GODSWORD, activity.itemId);
        assertEquals(15_000_000L, activity.buyCostGp);
        assertEquals(18_130_000L, activity.sellRevenueGp);
        assertEquals(3_130_000L, activity.profitGp);
        assertEquals(370_000L, activity.taxGp);
        
    }

    /**
     * The part of a purchase a conversion did not use is still an ordinary flip, and must not be
     * charged twice.
     */
    @Test
    public void theUnusedPartOfAPurchaseIsStillAnOrdinaryFlip() {
        Delta blades = buy(1_000L, 1, BLADE, 2, 8_000_000L);
        Delta hilt = buy(2_000L, 2, HILT, 1, 11_000_000L);
        Delta godswordSale = sell(3_000L, 3, GODSWORD, 1, 18_500_000L);
        Delta bladeSale = sell(4_000L, 4, BLADE, 1, 4_500_000L);
        RecipeFlipStore store = new RecipeFlipStore();
        store.add(ACCOUNT, new RecipeFlip(ConversionKind.ASSEMBLE, "Armadyl godsword",
            Arrays.asList(part(blades, 1), part(hilt, 1)),
            Collections.singletonList(part(godswordSale, 1)), 0L, 9_000L, null, null));

        Map<Integer, List<StatsFlipInstance>> byItem =
            new LocalFlipHistoryService(store)
                .buildHistory(Arrays.asList(blades, hilt, godswordSale, bladeSale), null, ACCOUNT);

        StatsFlipInstance conversion = byItem.get(GODSWORD).get(0);
        assertEquals("one of the two blades, so half what they cost, plus the hilt",
            15_000_000L, conversion.buyCostGp);

        StatsFlipInstance plain = byItem.get(BLADE).get(0);
        assertEquals("the other blade at its own cost", 4_000_000L, plain.buyCostGp);
        assertEquals(410_000L, plain.profitGp);
    }

    /** A record naming a trade that is no longer stored leaves everything an ordinary flip. */
    @Test
    public void aRecordWhoseTradesAreGoneChangesNothing() {
        Delta blade = buy(1_000L, 1, BLADE, 1, 4_000_000L);
        Delta bladeSale = sell(2_000L, 2, BLADE, 1, 4_500_000L);
        RecipeFlipStore store = new RecipeFlipStore();
        store.add(ACCOUNT, new RecipeFlip(ConversionKind.ASSEMBLE, "Armadyl godsword",
            Arrays.asList(part(blade, 1), new RecipeFlip.Part(new TradeKey(99L, 9, HILT), 1, null)),
            Collections.singletonList(new RecipeFlip.Part(new TradeKey(98L, 8, GODSWORD), 1, null)),
            0L, 9_000L, null, null));

        List<StatsFlipInstance> entries = allEntries(new LocalFlipHistoryService(store)
            .buildHistory(Arrays.asList(blade, bladeSale), null, ACCOUNT));

        assertEquals(1, entries.size());
        assertEquals(BLADE, entries.get(0).itemId);
        assertEquals(410_000L, entries.get(0).profitGp);
    }

    /** A conversion that finished before the window opened is not in it. */
    @Test
    public void aRangeExcludesAConversionThatFinishedBeforeIt() {
        Delta blade = buy(1_000L, 1, BLADE, 1, 4_000_000L);
        Delta hilt = buy(2_000L, 2, HILT, 1, 11_000_000L);
        Delta sale = sell(3_000L, 3, GODSWORD, 1, 18_500_000L);
        RecipeFlipStore store = new RecipeFlipStore();
        store.add(ACCOUNT, new RecipeFlip(ConversionKind.ASSEMBLE, "Armadyl godsword",
            Arrays.asList(part(blade, 1), part(hilt, 1)),
            Collections.singletonList(part(sale, 1)), 0L, 9_000L, null, null));

        Map<Integer, List<StatsFlipInstance>> byItem =
            new LocalFlipHistoryService(store)
                .buildHistory(Arrays.asList(blade, hilt, sale), 10_000L, ACCOUNT);

        assertTrue(allEntries(byItem).isEmpty());
    }

    /**
     * The two ledgers must agree about what a conversion was worth. Under guessing they could
     * not: they retried unmatched sales at different moments and split a mixed bucket's cost
     * differently, so the header and the rows could disagree. Both now call one calculator.
     */
    @Test
    public void bothLedgersAgreeOnARecordedConversion() {
        Delta blade = buy(1_000L, 1, BLADE, 1, 4_000_000L);
        Delta hilt = buy(2_000L, 2, HILT, 1, 11_000_000L);
        Delta sale = sell(3_000L, 3, GODSWORD, 1, 18_500_000L);
        List<Delta> deltas = Arrays.asList(blade, hilt, sale);
        RecipeFlipStore store = new RecipeFlipStore();
        store.add(ACCOUNT, new RecipeFlip(ConversionKind.ASSEMBLE, "Armadyl godsword",
            Arrays.asList(part(blade, 1), part(hilt, 1)),
            Collections.singletonList(part(sale, 1)), 0L, 9_000L, null, null));

        Map<Integer, List<StatsFlipInstance>> history =
            new LocalFlipHistoryService(store).buildHistory(deltas, null, ACCOUNT);
        StatsCache cache = new StatsCache(ACCOUNT, store);
        cache.rebuild(deltas);
        StatsSummary summary = cache.getSummary();

        long historyProfit = 0L;
        long historyTax = 0L;
        for (List<StatsFlipInstance> list : history.values()) {
            for (StatsFlipInstance entry : list) {
                if (entry != null) {
                    historyProfit += entry.profitGp;
                    historyTax += entry.taxGp;
                }
            }
        }

        assertEquals(3_130_000L, historyProfit);
        assertEquals(Long.valueOf(historyProfit), summary.total_profit_gp);
        assertEquals(Long.valueOf(historyTax), summary.tax_paid_gp);
        assertEquals(Long.valueOf(15_000_000L), summary.total_cost_gp);
        assertEquals(Integer.valueOf(1), summary.fill_count);
    }

    /** The rows the Profile tab shows have to add up to the header above them. */
    @Test
    public void theRowsStillSumToTheHeader() {
        Delta blades = buy(1_000L, 1, BLADE, 2, 8_000_000L);
        Delta hilt = buy(2_000L, 2, HILT, 1, 11_000_000L);
        Delta godswordSale = sell(3_000L, 3, GODSWORD, 1, 18_500_000L);
        Delta bladeSale = sell(4_000L, 4, BLADE, 1, 4_500_000L);
        List<Delta> deltas = Arrays.asList(blades, hilt, godswordSale, bladeSale);
        RecipeFlipStore store = new RecipeFlipStore();
        store.add(ACCOUNT, new RecipeFlip(ConversionKind.ASSEMBLE, "Armadyl godsword",
            Arrays.asList(part(blades, 1), part(hilt, 1)),
            Collections.singletonList(part(godswordSale, 1)), 0L, 9_000L, null, null));

        Map<Integer, List<StatsFlipInstance>> history =
            new LocalFlipHistoryService(store).buildHistory(deltas, null, ACCOUNT);
        StatsCache cache = new StatsCache(ACCOUNT, store);
        cache.rebuild(deltas);
        StatsSummary summary = cache.getSummary();
        List<StatsItem> items = cache.getItems();
        StatsView.reconcileWithFlipHistory(summary, items, history);

        long rows = 0L;
        for (StatsItem item : items) {
            rows += item.total_profit_gp != null ? item.total_profit_gp : 0L;
        }
        assertEquals(summary.total_profit_gp.longValue(), rows);
        assertEquals(3_130_000L + 410_000L, rows);
    }
}
