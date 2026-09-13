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
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LocalFlipHistoryServiceTest {
    @Test
    public void buildsFlipInstancesForMatchedSells() {
        LocalFlipHistoryService service = new LocalFlipHistoryService();
        List<Delta> deltas = Arrays.asList(
            delta(1_000L, 1, 560, true, 10, 1_000L, "OFFER_UPDATED", 100, false),
            delta(2_000L, 1, 560, false, 4, 520L, "OFFER_UPDATED", 130, false),
            delta(2_100L, 1, 560, false, 0, 0L, "OFFER_COMPLETED", 130, false)
        );

        Map<Integer, List<StatsFlipInstance>> result = service.buildHistory(deltas, null);

        assertTrue(result.containsKey(560));
        assertEquals(1, result.get(560).size());
        StatsFlipInstance flip = result.get(560).get(0);
        assertEquals(560, flip.itemId);
        assertEquals(100L, flip.buyPriceGp);
        assertEquals(130L, flip.sellPriceGp);
        assertEquals(120L, flip.profitGp);
        assertEquals(4, flip.quantity);
        assertEquals(2_100L, flip.completionTsMs);
        // floor(130 / 50) = 2 on each of the four.
        assertEquals(8L, flip.taxGp);
    }

    @Test
    public void rangeFilterIncludesInWindowSellsUsingOlderBuys() {
        LocalFlipHistoryService service = new LocalFlipHistoryService();
        List<Delta> deltas = Arrays.asList(
            delta(1_000L, 1, 561, true, 10, 1_000L, "OFFER_UPDATED", 100, false),
            delta(2_000L, 1, 561, false, 5, 700L, "OFFER_COMPLETED", 140, false),
            delta(5_000L, 1, 561, false, 5, 800L, "OFFER_COMPLETED", 160, false)
        );

        Map<Integer, List<StatsFlipInstance>> result = service.buildHistory(deltas, 4_000L);

        assertTrue(result.containsKey(561));
        assertEquals(1, result.get(561).size());
        StatsFlipInstance flip = result.get(561).get(0);
        assertEquals(100L, flip.buyPriceGp);
        assertEquals(160L, flip.sellPriceGp);
        assertEquals(300L, flip.profitGp);
        assertEquals(5, flip.quantity);
        assertEquals(5_000L, flip.completionTsMs);
    }

    @Test
    public void multipleSellUpdatesCollapseToSingleHistoryEntryOnCompletion() {
        LocalFlipHistoryService service = new LocalFlipHistoryService();
        List<Delta> deltas = Arrays.asList(
            delta(1_000L, 2, 6332, true, 10, 20_000L, "OFFER_UPDATED", 2_000, false),
            delta(2_000L, 2, 6332, false, 3, 6_300L, "OFFER_UPDATED", 2_100, false),
            delta(3_000L, 2, 6332, false, 4, 8_400L, "OFFER_UPDATED", 2_100, false),
            delta(4_000L, 2, 6332, false, 3, 6_300L, "OFFER_COMPLETED", 2_100, false)
        );

        Map<Integer, List<StatsFlipInstance>> result = service.buildHistory(deltas, null);

        assertTrue(result.containsKey(6332));
        assertEquals(1, result.get(6332).size());
        StatsFlipInstance flip = result.get(6332).get(0);
        assertEquals(10, flip.quantity);
        assertEquals(2_000L, flip.buyPriceGp);
        assertEquals(2_100L, flip.sellPriceGp);
        assertEquals(1_000L, flip.profitGp);
        assertEquals(4_000L, flip.completionTsMs);
    }

    @Test
    public void sellPriceUsesFloorDivisionToAvoidRoundingUp() {
        LocalFlipHistoryService service = new LocalFlipHistoryService();
        List<Delta> deltas = Arrays.asList(
            delta(1_000L, 3, 536, true, 2, 1_152L, "OFFER_UPDATED", 576, false),
            delta(2_000L, 3, 536, false, 2, 1_105L, "OFFER_UPDATED", 563, false),
            delta(2_100L, 3, 536, false, 0, 0L, "OFFER_COMPLETED", 563, false)
        );

        Map<Integer, List<StatsFlipInstance>> result = service.buildHistory(deltas, null);

        assertTrue(result.containsKey(536));
        assertEquals(1, result.get(536).size());
        StatsFlipInstance flip = result.get(536).get(0);
        assertEquals(576L, flip.buyPriceGp);
        assertEquals(552L, flip.sellPriceGp);
    }

    @Test
    public void openSellOfferIsRecordedAsAnInProgressFlip() {
        LocalFlipHistoryService service = new LocalFlipHistoryService();
        List<Delta> deltas = Arrays.asList(
            delta(1_000L, 3, 573, true, 1_976, 2_614_248L, "OFFER_UPDATED", 1_323, false),
            delta(1_600L, 3, 573, true, 0, 0L, "OFFER_COMPLETED", 1_323, false),
            // Sold 303 of the 1,976; the offer is still sitting in the exchange.
            delta(20_000L, 4, 573, false, 303, 420_867L, "OFFER_UPDATED", 1_417, false)
        );

        Map<Integer, List<StatsFlipInstance>> result = service.buildHistory(deltas, null);

        assertTrue(result.containsKey(573));
        assertEquals(1, result.get(573).size());
        StatsFlipInstance flip = result.get(573).get(0);
        assertTrue(flip.inProgress);
        assertEquals(303, flip.quantity);
        assertEquals(400_869L, flip.buyCostGp);
        assertEquals(19_998L, flip.profitGp);
        assertEquals(20_000L, flip.completionTsMs);
    }

    @Test
    public void completedOfferReplacesItsInProgressEntry() {
        LocalFlipHistoryService service = new LocalFlipHistoryService();
        List<Delta> deltas = Arrays.asList(
            delta(1_000L, 3, 573, true, 1_000, 1_000_000L, "OFFER_UPDATED", 1_000, false),
            delta(20_000L, 4, 573, false, 300, 330_000L, "OFFER_UPDATED", 1_100, false),
            delta(30_000L, 4, 573, false, 700, 770_000L, "OFFER_UPDATED", 1_100, false),
            delta(30_600L, 4, 573, false, 0, 0L, "OFFER_COMPLETED", 1_100, false)
        );

        Map<Integer, List<StatsFlipInstance>> result = service.buildHistory(deltas, null);

        assertEquals(1, result.get(573).size());
        StatsFlipInstance flip = result.get(573).get(0);
        assertFalse(flip.inProgress);
        assertEquals(1_000, flip.quantity);
        assertEquals(100_000L, flip.profitGp);
    }

    /**
     * The partial's profit is already in the delta-cache totals. Reconciling
     * those totals against the ledger used to erase it, for every range whose
     * ledger held anything at all.
     */
    @Test
    public void reconcileKeepsProfitFromAnOpenOfferWithoutCountingItAsAFlip() {
        LocalFlipHistoryService service = new LocalFlipHistoryService();
        List<Delta> deltas = Arrays.asList(
            // one finished flip
            delta(1_000L, 1, 573, true, 100, 100_000L, "OFFER_UPDATED", 1_000, false),
            delta(2_000L, 2, 573, false, 100, 110_000L, "OFFER_UPDATED", 1_100, false),
            delta(2_600L, 2, 573, false, 0, 0L, "OFFER_COMPLETED", 1_100, false),
            // and one that is still filling
            delta(10_000L, 3, 573, true, 1_976, 2_614_248L, "OFFER_UPDATED", 1_323, false),
            delta(10_600L, 3, 573, true, 0, 0L, "OFFER_COMPLETED", 1_323, false),
            delta(20_000L, 4, 573, false, 303, 420_867L, "OFFER_UPDATED", 1_417, false)
        );
        StatsCache cache = new StatsCache();
        cache.rebuild(deltas);
        StatsSummary summary = cache.getSummary();
        List<StatsItem> items = new ArrayList<>(cache.getItems());

        StatsView.reconcileWithFlipHistory(summary, items, service.buildHistory(deltas, null));

        assertEquals(Long.valueOf(29_998L), summary.total_profit_gp);
        assertEquals(Long.valueOf(403L), summary.total_qty);
        assertEquals(Integer.valueOf(1), summary.fill_count);
        assertEquals(1, items.size());
        assertEquals(Long.valueOf(29_998L), items.get(0).total_profit_gp);
        assertEquals(Integer.valueOf(1), items.get(0).fill_count);
        assertEquals(Long.valueOf(20_000L), items.get(0).last_sell_ts_ms);
    }

    /**
     * Under a range the cache admits fills one at a time while the history
     * admits an offer whole, by when it completed. An offer straddling the
     * boundary used to show its whole profit and one flip next to a tax figure
     * for only the fills inside the range. The header is one statement about
     * one set of trades, so every number on it has to come from the same ledger.
     */
    @Test
    public void reconcileTakesTaxFromTheSameOffersAsProfit() {
        LocalFlipHistoryService service = new LocalFlipHistoryService();
        List<Delta> deltas = Arrays.asList(
            delta(1_000L, 1, 560, true, 10, 10_000L, "OFFER_COMPLETED", 1_000, false),
            // Five sold before the range opens, five after; the offer completes inside it.
            delta(60_000L, 2, 560, false, 5, 5_390L, "OFFER_UPDATED", 1_100, false),
            delta(180_000L, 2, 560, false, 5, 5_390L, "OFFER_COMPLETED", 1_100, false)
        );
        Long sinceMs = 120_000L;
        StatsCache cache = new StatsCache();
        cache.rebuild(deltas);
        StatsSnapshot snapshot = cache.buildSnapshotSince(sinceMs);
        StatsSummary summary = snapshot.summary;
        List<StatsItem> items = new ArrayList<>(snapshot.items);

        StatsView.reconcileWithFlipHistory(summary, items, service.buildHistory(deltas, sinceMs));

        // The whole offer: 10,780 back on 10,000 out, one flip, and floor(1,100 / 50) = 22 on each of the ten.
        assertEquals(Long.valueOf(780L), summary.total_profit_gp);
        assertEquals(Integer.valueOf(1), summary.fill_count);
        assertEquals(Long.valueOf(10L), summary.total_qty);
        assertEquals(Long.valueOf(220L), summary.tax_paid_gp);
    }

    /**
     * Inside one 600 ms bucket a buy is replayed before a sell, because two
     * live fills in one tick can be observed in either order. Trades imported
     * from the in-game history are stamped 8 ms apart by the sync, so a whole
     * batch shares a bucket - and there the invented order is the history's
     * own order, which is the evidence. A sale the history puts before the buy
     * was not a sale of that stock.
     */
    @Test
    public void aSyncedBatchIsReplayedInItsOwnOrderNotBuysFirst() {
        LocalFlipHistoryService service = new LocalFlipHistoryService();
        int synced = Const.GE_HISTORY_SYNTHETIC_SLOT_START;
        List<Delta> deltas = Arrays.asList(
            delta(5_000L, synced, 560, false, 1, 130L, "OFFER_UPDATED", 130, false),
            delta(5_004L, synced, 560, false, 0, 0L, "OFFER_COMPLETED", 130, false),
            delta(5_008L, synced + 1, 560, true, 1, 100L, "OFFER_UPDATED", 100, false),
            delta(5_012L, synced + 1, 560, true, 0, 0L, "OFFER_COMPLETED", 100, false)
        );

        assertTrue(service.buildHistory(deltas, null).isEmpty());
    }

    @Test
    public void liveFillsInOneBucketAreStillReplayedBuysFirst() {
        // Same shape on real slots: two fills of one tick are believed to be
        // buy-then-sell whatever order the client reported them in.
        LocalFlipHistoryService service = new LocalFlipHistoryService();
        List<Delta> deltas = Arrays.asList(
            delta(5_000L, 2, 560, false, 1, 130L, "OFFER_COMPLETED", 130, false),
            delta(5_008L, 1, 560, true, 1, 100L, "OFFER_COMPLETED", 100, false)
        );

        Map<Integer, List<StatsFlipInstance>> result = service.buildHistory(deltas, null);

        assertEquals(1, result.get(560).size());
        assertEquals(30L, result.get(560).get(0).profitGp);
    }

    private static Delta delta(long tsClientMs, int slot, int itemId, boolean isBuy, int deltaQty,
                                         long deltaGp, String eventType, int price, boolean baselineSynthetic) {
        return new Delta(
            tsClientMs,
            slot,
            itemId,
            isBuy,
            deltaQty,
            deltaGp,
            eventType,
            price,
            baselineSynthetic
        );
    }
}
