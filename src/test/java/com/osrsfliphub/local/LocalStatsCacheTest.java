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
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The stats cache on ordinary flips: what a rebuild replays, and what the
 * running totals say about them.
 */
public class LocalStatsCacheTest {
    private static final int SYNCED = GeLifecyclePluginConstants.GE_HISTORY_SYNTHETIC_SLOT_START;

    private static LocalTradeDelta delta(long tsClientMs, int slot, int itemId, boolean isBuy, int deltaQty,
                                         long deltaGp, String eventType, int price) {
        return new LocalTradeDelta(tsClientMs, slot, itemId, isBuy, deltaQty, deltaGp, eventType, price, false);
    }

    /**
     * The live cache saw the sync's batch in the order the sync wrote it, one
     * delta at a time. A rebuild from disk has to replay the same batch the
     * same way, or the numbers change when the client restarts.
     */
    @Test
    public void rebuildReplaysASyncedBatchInTheOrderTheLiveCacheSawIt() {
        List<LocalTradeDelta> deltas = Arrays.asList(
            delta(5_000L, SYNCED, 560, false, 1, 130L, "OFFER_UPDATED", 130),
            delta(5_004L, SYNCED, 560, false, 0, 0L, "OFFER_COMPLETED", 130),
            delta(5_008L, SYNCED + 1, 560, true, 1, 100L, "OFFER_UPDATED", 100),
            delta(5_012L, SYNCED + 1, 560, true, 0, 0L, "OFFER_COMPLETED", 100)
        );
        LocalStatsCache live = new LocalStatsCache();
        for (LocalTradeDelta delta : deltas) {
            assertTrue(live.applyDeltaInOrder(delta));
        }
        LocalStatsCache rebuilt = new LocalStatsCache();
        rebuilt.rebuild(deltas);

        // The history said the sale came first, so it was not a sale of this stock.
        assertEquals(Integer.valueOf(0), live.getSummary().fill_count);
        assertEquals(Long.valueOf(0L), live.getSummary().total_profit_gp);
        assertEquals(live.getSummary().fill_count, rebuilt.getSummary().fill_count);
        assertEquals(live.getSummary().total_profit_gp, rebuilt.getSummary().total_profit_gp);
    }

    private static final long HOUR_MS = 3_600_000L;

    /**
     * Active time is what the website's gold per hour divides by. One purchase
     * held an hour is an hour in the market however many fills it took to sell
     * it, so each fill carries the share of the position it closed - a tenth
     * of an hour each here - and not the whole hold ten times over.
     */
    @Test
    public void onePurchaseSoldInTenFillsIsHeldOnce() {
        LocalStatsCache cache = new LocalStatsCache();
        assertTrue(cache.applyDeltaInOrder(delta(0L, 1, 560, true, 10, 1_000L, "OFFER_COMPLETED", 100)));
        for (int fill = 0; fill < 10; fill++) {
            String type = fill == 9 ? "OFFER_COMPLETED" : "OFFER_UPDATED";
            assertTrue(cache.applyDeltaInOrder(delta(HOUR_MS + fill, 2, 560, false, 1, 127L, type, 130)));
        }

        StatsSummary summary = cache.getSummary();
        assertEquals(Long.valueOf(HOUR_MS), summary.active_ms);
        // 270 profit over one hour, not over ten.
        assertEquals(270.0, summary.gp_per_hour, 0.001);
    }

    @Test
    public void twoPurchasesSoldSeparatelyAreTwoHolds() {
        // The pool empties between them, so they are two positions and the
        // clock runs from each one's own purchase.
        LocalStatsCache cache = new LocalStatsCache();
        assertTrue(cache.applyDeltaInOrder(delta(0L, 1, 560, true, 5, 500L, "OFFER_COMPLETED", 100)));
        assertTrue(cache.applyDeltaInOrder(delta(HOUR_MS, 2, 560, false, 5, 635L, "OFFER_COMPLETED", 130)));
        assertTrue(cache.applyDeltaInOrder(delta(2 * HOUR_MS, 1, 560, true, 5, 500L, "OFFER_COMPLETED", 100)));
        assertTrue(cache.applyDeltaInOrder(delta(4 * HOUR_MS, 2, 560, false, 5, 635L, "OFFER_COMPLETED", 130)));

        assertEquals(Long.valueOf(3 * HOUR_MS), cache.getSummary().active_ms);
    }
}
