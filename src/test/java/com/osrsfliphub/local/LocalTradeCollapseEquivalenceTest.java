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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The same trading, stored fill by fill and stored one record per offer, books the same
 * profit, cost, quantity, tax and flips - through both ledgers, and whether the offers
 * were collapsed as they completed (the live path) or all at once on load (the migration).
 *
 * <p>The pool sees the same quantity and the same coins either way; only the number of
 * records differs. What does change is time: a collapsed sale is booked when it ended
 * where the fills were each booked at their own moment, so active time - the divisor of
 * gold per hour - is measured to the end of the sale offer rather than to each chunk of it.
 * That difference is asserted below, in the open, rather than hidden.
 */
public class LocalTradeCollapseEquivalenceTest {
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60L * MINUTE;
    private static final int SYNCED = Const.GE_HISTORY_SYNTHETIC_SLOT_START;

    private static final int ITEM_A = 560;
    private static final int ITEM_B = 4151;
    private static final int ITEM_C = 1511;
    private static final int ITEM_D = 11802;

    /** The offer pipeline's output for a session of flipping, one record per fill. */
    private static List<Delta> tradedFillByFill() {
        Stream s = new Stream();
        // 1. Buy 1,000 A at 100 in four chunks over five minutes.
        s.offer(0, ITEM_A, true, 100, 1L, 0L, new int[] {100, 250, 400, 250}, new long[] {0L, MINUTE, 2 * MINUTE, 5 * MINUTE});
        // 2. Buy 500 B at 2,000 in one chunk.
        s.offer(1, ITEM_B, true, 2_000, 2L, 10_000L, new int[] {500}, new long[] {0L});
        // 3. Sell 600 of the A at 130 in two chunks: a partial sale of the position.
        s.offer(2, ITEM_A, false, 130, 3L, HOUR, new int[] {200, 400}, new long[] {0L, 5 * MINUTE});
        // 4. The same slot again for A at a different price: 200 more at 110.
        s.offer(0, ITEM_A, true, 110, 4L, 2 * HOUR, new int[] {200}, new long[] {0L});
        // 5. Sell the remaining 600 A at 140, from a pool of mixed cost, in four chunks.
        s.offer(2, ITEM_A, false, 140, 5L, 3 * HOUR, new int[] {100, 100, 100, 300},
            new long[] {0L, MINUTE, 2 * MINUTE, 3 * MINUTE});
        // 6. Sell the 500 B at 2,100: 200 as an update, the remaining 300 carried by the completion itself.
        s.fill(3, ITEM_B, false, 2_100, 6L, 4 * HOUR, 200);
        s.completion(3, ITEM_B, false, 2_100, 6L, 4 * HOUR + MINUTE, 300);
        s.completion(3, ITEM_B, false, 2_100, 0L, 4 * HOUR + 2 * MINUTE, 0);
        // 7. A buy of C cancelled after two chunks; the slot then holds a buy of B.
        s.fill(6, ITEM_C, true, 55, 7L, 4 * HOUR + 10 * MINUTE, 20);
        s.fill(6, ITEM_C, true, 55, 7L, 4 * HOUR + 11 * MINUTE, 30);
        s.offer(6, ITEM_B, true, 1_900, 8L, 4 * HOUR + 30 * MINUTE, new int[] {5}, new long[] {0L});
        // 8. A trade imported from the in-game history, then the D it bought sold live.
        s.add(new Delta(5 * HOUR, SYNCED, ITEM_D, true, 10, 10_000L, "OFFER_UPDATED", 1_000, false));
        s.add(new Delta(5 * HOUR + 4L, SYNCED, ITEM_D, true, 0, 0L, "OFFER_COMPLETED", 1_000, false));
        s.offer(5, ITEM_D, false, 1_100, 9L, 5 * HOUR + 10 * MINUTE, new int[] {10}, new long[] {0L});
        // 9. An offer still filling when the session ends.
        s.fill(4, ITEM_C, true, 50, 10L, 6 * HOUR, 100);
        s.fill(4, ITEM_C, true, 50, 10L, 6 * HOUR + MINUTE, 100);
        return s.records;
    }

    /** What the live path stores: every record appended as it arrived. */
    private static List<Delta> collapsedLive(List<Delta> fillByFill) {
        List<Delta> stored = new ArrayList<>();
        for (Delta record : fillByFill) {
            TradeOfferCollapser.append(stored, copy(record));
        }
        return stored;
    }

    /** What loading a file written fill by fill produces. */
    private static List<Delta> collapsedOnLoad(List<Delta> fillByFill) {
        List<Delta> copies = new ArrayList<>();
        for (Delta record : fillByFill) {
            copies.add(copy(record));
        }
        return TradeDeltaUtils.dedupeLocalTrades(copies,
            Const.LOCAL_EVENT_BUCKET_MS, Const.DUPLICATE_TRADE_WINDOW_MS);
    }

    @Test
    public void collapsingLeavesTheStatsCacheTotalsUnchanged() {
        List<Delta> fillByFill = tradedFillByFill();
        StatsSummary perFill = summary(fillByFill);
        StatsSummary live = summary(collapsedLive(fillByFill));
        StatsSummary loaded = summary(collapsedOnLoad(fillByFill));

        // 16,800 on the first A sale, 20,800 on the second, 29,000 on B, 780 on D.
        assertEquals(Long.valueOf(67_380L), perFill.total_profit_gp);
        assertEquals(Long.valueOf(1_132_000L), perFill.total_cost_gp);
        assertEquals(Long.valueOf(1_710L), perFill.total_qty);
        assertEquals(Long.valueOf(23_620L), perFill.tax_paid_gp);
        assertEquals(Integer.valueOf(4), perFill.fill_count);

        assertSameTotals(perFill, live);
        assertSameTotals(perFill, loaded);
    }

    @Test
    public void collapsingLeavesEveryItemsFiguresUnchanged() {
        List<Delta> fillByFill = tradedFillByFill();
        Map<Integer, StatsItem> perFill = items(fillByFill);
        Map<Integer, StatsItem> live = items(collapsedLive(fillByFill));
        Map<Integer, StatsItem> loaded = items(collapsedOnLoad(fillByFill));

        assertEquals(3, perFill.size());
        assertEquals(Long.valueOf(37_600L), perFill.get(ITEM_A).total_profit_gp);
        assertEquals(Integer.valueOf(2), perFill.get(ITEM_A).fill_count);
        assertSameItems(perFill, live);
        assertSameItems(perFill, loaded);
    }

    @Test
    public void collapsingLeavesTheFlipHistoryUnchanged() {
        List<Delta> fillByFill = tradedFillByFill();
        List<StatsFlipInstance> perFill = flips(fillByFill);
        List<StatsFlipInstance> live = flips(collapsedLive(fillByFill));
        List<StatsFlipInstance> loaded = flips(collapsedOnLoad(fillByFill));

        assertEquals(4, perFill.size());
        assertSameFlips(perFill, live);
        assertSameFlips(perFill, loaded);
    }

    @Test
    public void theLiveAndLoadPathsStoreTheSameRecords() {
        List<Delta> fillByFill = tradedFillByFill();
        List<Delta> live = collapsedLive(fillByFill);
        List<Delta> loaded = collapsedOnLoad(fillByFill);

        // 37 records in: nine completed offers (one of them imported), the cancelled C run
        // and the open C offer. Live keeps the cancelled run's two fills; the load pass
        // folds them into one.
        assertEquals(37, fillByFill.size());
        assertEquals(9 + 2 + 2, live.size());
        assertEquals(9 + 1 + 2, loaded.size());
        assertTrue(loaded.size() * 3 <= fillByFill.size());
        assertSameCompletedRecords(live, loaded);
    }

    /**
     * Where fills were each booked at their own moment, a collapsed sale is booked at its
     * completion. The three sales that filled in chunks here took 5, 3 and 1 minutes to
     * fill, so the holds run about two minutes longer over six hours of trading.
     */
    @Test
    public void activeTimeRunsToTheSalesCompletionOnceCollapsed() {
        List<Delta> fillByFill = tradedFillByFill();
        StatsSummary perFill = summary(fillByFill);
        StatsSummary collapsed = summary(collapsedOnLoad(fillByFill));

        assertEquals(Long.valueOf(22_766_000L), perFill.active_ms);
        assertEquals(Long.valueOf(22_881_260L), collapsed.active_ms);
        assertEquals(summary(collapsedLive(fillByFill)).active_ms, collapsed.active_ms);
    }

    /**
     * A sale offer left up while more of the item is bought sells its later units out of
     * the later stock. Fill by fill, each chunk saw the pool as it stood; one record can
     * only be matched once, and it is matched when the sale ended - against everything
     * held by then - which is the one time that leaves no real sale unmatched. Booked at
     * its first fill instead, the second ten here would have found an empty pool.
     */
    @Test
    public void aSaleLeftUpWhileMoreIsBoughtIsMatchedAgainstWhatWasHeldWhenItEnded() {
        Stream s = new Stream();
        s.offer(0, ITEM_A, true, 100, 1L, 0L, new int[] {10}, new long[] {0L});
        s.fill(2, ITEM_A, false, 130, 2L, HOUR, 10);
        s.offer(0, ITEM_A, true, 100, 3L, 2 * HOUR, new int[] {10}, new long[] {0L});
        s.fill(2, ITEM_A, false, 130, 2L, 3 * HOUR, 10);
        s.completion(2, ITEM_A, false, 130, 2L, 3 * HOUR + 600L, 0);
        List<Delta> fillByFill = s.records;

        StatsSummary perFill = summary(fillByFill);
        StatsSummary live = summary(collapsedLive(fillByFill));
        StatsSummary loaded = summary(collapsedOnLoad(fillByFill));

        assertEquals(Long.valueOf(20L), perFill.total_qty);
        assertEquals(Long.valueOf(560L), perFill.total_profit_gp);
        assertSameTotals(perFill, live);
        assertSameTotals(perFill, loaded);
        assertEquals(1, flips(collapsedLive(fillByFill)).size());
        assertEquals(20, flips(collapsedLive(fillByFill)).get(0).quantity);
    }

    // ---- ledgers ----

    private static StatsSummary summary(List<Delta> records) {
        StatsCache cache = new StatsCache();
        cache.rebuild(records);
        return cache.getSummary();
    }

    private static Map<Integer, StatsItem> items(List<Delta> records) {
        StatsCache cache = new StatsCache();
        cache.rebuild(records);
        Map<Integer, StatsItem> byItem = new TreeMap<>();
        for (StatsItem item : cache.getItems()) {
            byItem.put(item.item_id, item);
        }
        return byItem;
    }

    private static List<StatsFlipInstance> flips(List<Delta> records) {
        List<StatsFlipInstance> all = new ArrayList<>();
        for (List<StatsFlipInstance> perItem : new LocalFlipHistoryService().buildHistory(records, null).values()) {
            all.addAll(perItem);
        }
        all.sort(Comparator.comparingLong((StatsFlipInstance flip) -> flip.completionTsMs).thenComparingInt(flip -> flip.itemId));
        return all;
    }

    private static void assertSameTotals(StatsSummary expected, StatsSummary actual) {
        assertEquals(expected.total_profit_gp, actual.total_profit_gp);
        assertEquals(expected.total_cost_gp, actual.total_cost_gp);
        assertEquals(expected.total_qty, actual.total_qty);
        assertEquals(expected.tax_paid_gp, actual.tax_paid_gp);
        assertEquals(expected.fill_count, actual.fill_count);
        assertEquals(expected.first_buy_ts_ms, actual.first_buy_ts_ms);
    }

    private static void assertSameCompletedRecords(List<Delta> live, List<Delta> loaded) {
        List<Delta> liveDone = completed(live);
        List<Delta> loadedDone = completed(loaded);
        assertEquals(9, liveDone.size());
        assertEquals(liveDone.size(), loadedDone.size());
        for (int i = 0; i < liveDone.size(); i++) {
            Delta a = liveDone.get(i);
            Delta b = loadedDone.get(i);
            assertEquals(a.tsClientMs, b.tsClientMs);
            assertEquals(a.endMs, b.endMs);
            assertEquals(a.slot, b.slot);
            assertEquals(a.itemId, b.itemId);
            assertEquals(a.deltaQty, b.deltaQty);
            assertEquals(a.deltaGp, b.deltaGp);
            assertEquals(a.offerStartMs, b.offerStartMs);
        }
    }

    private static List<Delta> completed(List<Delta> records) {
        List<Delta> done = new ArrayList<>();
        for (Delta record : records) {
            if (TradeOfferCollapser.isCompletion(record)) {
                done.add(record);
            }
        }
        done.sort(Comparator.comparingLong((Delta record) -> record.tsClientMs).thenComparingInt(record -> record.slot));
        return done;
    }

    private static void assertSameItems(Map<Integer, StatsItem> expected, Map<Integer, StatsItem> actual) {
        assertEquals(expected.keySet(), actual.keySet());
        for (Map.Entry<Integer, StatsItem> entry : expected.entrySet()) {
            StatsItem want = entry.getValue();
            StatsItem got = actual.get(entry.getKey());
            assertEquals(want.total_profit_gp, got.total_profit_gp);
            assertEquals(want.total_cost_gp, got.total_cost_gp);
            assertEquals(want.total_qty, got.total_qty);
            assertEquals(want.fill_count, got.fill_count);
        }
    }

    private static void assertSameFlips(List<StatsFlipInstance> expected, List<StatsFlipInstance> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            StatsFlipInstance want = expected.get(i);
            StatsFlipInstance got = actual.get(i);
            assertEquals(want.itemId, got.itemId);
            assertEquals(want.quantity, got.quantity);
            assertEquals(want.buyCostGp, got.buyCostGp);
            assertEquals(want.sellRevenueGp, got.sellRevenueGp);
            assertEquals(want.profitGp, got.profitGp);
            assertEquals(want.taxGp, got.taxGp);
            assertEquals(want.inProgress, got.inProgress);
            // The history already booked an offer at its completion; that does not move.
            assertEquals(want.completionTsMs, got.completionTsMs);
        }
    }

    // ---- building the stream ----

    private static Delta copy(Delta d) {
        return new Delta(d.tsClientMs, d.slot, d.itemId, d.isBuy, d.deltaQty, d.deltaGp, d.eventType,
            d.price, d.baselineSynthetic, d.offerStartMs, d.endMs);
    }

    private static final class Stream {
        final List<Delta> records = new ArrayList<>();

        void add(Delta record) {
            records.add(record);
        }

        /**
         * One offer as the pipeline reports it: a fill per chunk, a zero completion 600 ms
         * after the last, and the zero collect a minute later.
         */
        void offer(int slot, int itemId, boolean isBuy, int price, long start, long placedAt, int[] chunks, long[] at) {
            long last = 0L;
            for (int i = 0; i < chunks.length; i++) {
                fill(slot, itemId, isBuy, price, start, placedAt + at[i], chunks[i]);
                last = placedAt + at[i];
            }
            completion(slot, itemId, isBuy, price, start, last + 600L, 0);
            completion(slot, itemId, isBuy, price, 0L, last + MINUTE, 0);
        }

        void fill(int slot, int itemId, boolean isBuy, int price, long start, long ts, int qty) {
            records.add(new Delta(ts, slot, itemId, isBuy, qty, coins(itemId, isBuy, price, qty),
                "OFFER_UPDATED", price, false, start, 0L));
        }

        void completion(int slot, int itemId, boolean isBuy, int price, long start, long ts, int qty) {
            records.add(new Delta(ts, slot, itemId, isBuy, qty, coins(itemId, isBuy, price, qty),
                "OFFER_COMPLETED", price, false, start, 0L));
        }

        private static long coins(int itemId, boolean isBuy, int price, int qty) {
            long gross = (long) price * qty;
            return isBuy ? gross : gross - GeTax.forSale(itemId, price, qty);
        }
    }
}
