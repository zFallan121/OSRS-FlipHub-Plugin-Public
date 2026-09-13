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
import static org.junit.Assert.assertNotNull;

/**
 * A safety net around everything that is not a recipe.
 *
 * <p>Written before the move from guessed recipes to recorded ones, so that surgery on the two
 * ledgers can be proved not to have moved any ordinary figure. It pins the whole Profile tab
 * derivation - the cache for the rows, the flip history for the entries, and the reconcile that
 * makes the history authoritative - for plain buying and selling, with no conversion anywhere.</p>
 *
 * <p>Every number here was read out of the current build rather than worked out by hand. That is
 * the point of a characterisation test: it does not claim these answers are right, only that they
 * are what the plugin says today, so a refactor that changes one has to say so out loud.</p>
 *
 * <p>The audit found that exactly one existing test crossed both ledgers under a time range, and
 * none covered active time under a range at all. Those are the gaps this fills.</p>
 */
public class PlainFlipCharacterisationTest {
    private static final int SHARK = 385;
    private static final int BONES = 526;

    /** No recipes at all, so nothing can be inferred and only the plain path runs. */
    private static Ledger emptyLedger() {
        return new Ledger(new RecipeIndex(Collections.emptyList()));
    }

    private static Delta buy(long tsMs, int slot, int itemId, int qty, long gp, long endMs) {
        return new Delta(tsMs, slot, itemId, true, qty, gp, "OFFER_COMPLETED",
            (int) (gp / qty), false, tsMs, endMs);
    }

    /** Sales are stored net of tax, with the gross unit price alongside. */
    private static Delta sell(long tsMs, int slot, int itemId, int qty, long grossUnit, long endMs) {
        long net = grossUnit * qty - GeTax.forSale(itemId, (int) grossUnit, qty);
        return new Delta(tsMs, slot, itemId, false, qty, net, "OFFER_COMPLETED",
            (int) grossUnit, false, tsMs, endMs);
    }

    private static final class Tab {
        final StatsSummary summary;
        final List<StatsItem> items;

        Tab(List<Delta> deltas, Long sinceMs) {
            Ledger ledger = emptyLedger();
            Map<Integer, List<StatsFlipInstance>> history =
                new LocalFlipHistoryService(ledger).buildHistory(deltas, sinceMs, 1L);
            StatsCache cache = new StatsCache(ledger, 1L);
            cache.rebuild(deltas);
            if (sinceMs == null) {
                summary = cache.getSummary();
                items = cache.getItems();
            } else {
                StatsSnapshot snap = cache.buildSnapshotSince(sinceMs);
                summary = snap.summary != null ? snap.summary : new StatsSummary();
                items = snap.items != null ? snap.items : new ArrayList<>();
            }
            StatsView.reconcileWithFlipHistory(summary, items, history);
        }


        StatsItem row(int itemId) {
            for (StatsItem item : items) {
                if (item.item_id == itemId) {
                    return item;
                }
            }
            return null;
        }

        long rowsProfit() {
            long sum = 0L;
            for (StatsItem item : items) {
                sum += item.total_profit_gp != null ? item.total_profit_gp : 0L;
            }
            return sum;
        }
    }

    /** One item bought and sold whole: the simplest thing the plugin does. */
    private static List<Delta> oneCleanFlip() {
        return Arrays.asList(
            buy(1_000L, 1, SHARK, 100, 100_000L, 2_000L),
            sell(10_000L, 2, SHARK, 100, 1_200L, 20_000L));
    }

    @Test
    public void oneCleanFlipOverAllTime() {
        Tab tab = new Tab(oneCleanFlip(), null);

        assertEquals(Long.valueOf(17_600L), tab.summary.total_profit_gp);
        assertEquals(Long.valueOf(100_000L), tab.summary.total_cost_gp);
        assertEquals(Long.valueOf(2_400L), tab.summary.tax_paid_gp);
        assertEquals(Long.valueOf(100L), tab.summary.total_qty);
        assertEquals(Integer.valueOf(1), tab.summary.fill_count);
        assertEquals(Long.valueOf(1_000L), tab.summary.first_buy_ts_ms);
        assertEquals(Long.valueOf(20_000L), tab.summary.last_sell_ts_ms);
        assertEquals(19_000L, tab.summary.active_ms.longValue());
        assertEquals(17.6d, tab.summary.roi_percent, 0.001d);

        StatsItem row = tab.row(SHARK);
        assertNotNull(row);
        assertEquals(Long.valueOf(17_600L), row.total_profit_gp);
        assertEquals(Long.valueOf(100_000L), row.total_cost_gp);
        assertEquals(Integer.valueOf(100), row.total_qty);
        assertEquals(Integer.valueOf(1), row.fill_count);
        assertEquals(17_600L, tab.rowsProfit());
    }

    /**
     * Two items at once, so the header is a sum over more than one row and the rows have to keep
     * adding up to it.
     */
    @Test
    public void twoItemsSumToTheHeader() {
        List<Delta> deltas = Arrays.asList(
            buy(1_000L, 1, SHARK, 100, 100_000L, 2_000L),
            sell(10_000L, 2, SHARK, 100, 1_200L, 20_000L),
            buy(3_000L, 3, BONES, 50, 200_000L, 4_000L),
            sell(30_000L, 4, BONES, 50, 4_500L, 40_000L));

        Tab tab = new Tab(deltas, null);

        assertEquals(Long.valueOf(38_100L), tab.summary.total_profit_gp);
        assertEquals(Long.valueOf(300_000L), tab.summary.total_cost_gp);
        assertEquals(Long.valueOf(6_900L), tab.summary.tax_paid_gp);
        assertEquals(Long.valueOf(150L), tab.summary.total_qty);
        assertEquals(Integer.valueOf(2), tab.summary.fill_count);
        assertEquals(tab.summary.total_profit_gp.longValue(), tab.rowsProfit());
    }

    /**
     * A sell offer that filled in two parts either side of a range boundary. This is the shape
     * the reconcile exists for: the cache admits a fill, the history admits the whole offer, and
     * the header has to describe one offer rather than half of one.
     */
    @Test
    public void anOfferStraddlingTheRangeBoundary() {
        List<Delta> deltas = Arrays.asList(
            buy(1_000L, 1, SHARK, 100, 100_000L, 2_000L),
            new Delta(10_000L, 2, SHARK, false, 50,
                60_000L - GeTax.forSale(SHARK, 1_200, 50), "OFFER_UPDATED", 1_200, false, 10_000L, 0L),
            sell(10_000L, 2, SHARK, 50, 1_200L, 30_000L));

        Tab tab = new Tab(deltas, 20_000L);

        assertEquals(Long.valueOf(17_600L), tab.summary.total_profit_gp);
        assertEquals(Long.valueOf(100_000L), tab.summary.total_cost_gp);
        assertEquals(Long.valueOf(2_400L), tab.summary.tax_paid_gp);
        assertEquals(Long.valueOf(100L), tab.summary.total_qty);
        assertEquals(Integer.valueOf(1), tab.summary.fill_count);
    }

    /** A sell offer still sitting in the exchange: the coins count, the flip does not. */
    @Test
    public void anOfferStillOpenCountsItsCoinsButNotAsAFlip() {
        List<Delta> deltas = Arrays.asList(
            buy(1_000L, 1, SHARK, 100, 100_000L, 2_000L),
            new Delta(10_000L, 2, SHARK, false, 50,
                60_000L - GeTax.forSale(SHARK, 1_200, 50), "OFFER_UPDATED", 1_200, false, 10_000L, 0L));

        Tab tab = new Tab(deltas, null);

        assertEquals(Long.valueOf(8_800L), tab.summary.total_profit_gp);
        assertEquals(Long.valueOf(50_000L), tab.summary.total_cost_gp);
        assertEquals(Long.valueOf(1_200L), tab.summary.tax_paid_gp);
        assertEquals(Integer.valueOf(0), tab.summary.fill_count);
    }

    /** Selling less than was bought leaves the rest held, and only the sold part is costed. */
    @Test
    public void sellingPartOfAHolding() {
        List<Delta> deltas = Arrays.asList(
            buy(1_000L, 1, SHARK, 100, 100_000L, 2_000L),
            sell(10_000L, 2, SHARK, 40, 1_200L, 20_000L));

        Tab tab = new Tab(deltas, null);

        assertEquals(Long.valueOf(7_040L), tab.summary.total_profit_gp);
        assertEquals(Long.valueOf(40_000L), tab.summary.total_cost_gp);
        assertEquals(Long.valueOf(960L), tab.summary.tax_paid_gp);
        assertEquals(Long.valueOf(40L), tab.summary.total_qty);
        assertEquals(Integer.valueOf(1), tab.summary.fill_count);
    }

    /** A loss must stay a loss, and the return must go negative with it. */
    @Test
    public void aFlipThatLostMoney() {
        List<Delta> deltas = Arrays.asList(
            buy(1_000L, 1, SHARK, 100, 120_000L, 2_000L),
            sell(10_000L, 2, SHARK, 100, 1_000L, 20_000L));

        Tab tab = new Tab(deltas, null);

        assertEquals(Long.valueOf(-22_000L), tab.summary.total_profit_gp);
        assertEquals(Long.valueOf(120_000L), tab.summary.total_cost_gp);
        assertEquals(Long.valueOf(2_000L), tab.summary.tax_paid_gp);
        assertEquals(-18.333d, tab.summary.roi_percent, 0.01d);
    }
}
