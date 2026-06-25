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

public class LocalStatsCacheTest {
    @Test
    public void buildSnapshotSinceUsesOlderBuysForInWindowSells() {
        LocalStatsCache cache = new LocalStatsCache();
        List<LocalTradeDelta> deltas = Arrays.asList(
            delta(1_000L, 1, 561, true, 10, 1_000L, "OFFER_UPDATED", 100, false),
            delta(2_000L, 1, 561, false, 5, 700L, "OFFER_COMPLETED", 140, false),
            delta(5_000L, 1, 561, false, 5, 800L, "OFFER_COMPLETED", 160, false)
        );
        cache.rebuild(deltas);

        LocalStatsSnapshot window = cache.buildSnapshotSince(4_000L);

        assertEquals(Long.valueOf(300L), window.summary.total_profit_gp);
        assertEquals(Long.valueOf(500L), window.summary.total_cost_gp);
        assertEquals(Long.valueOf(5L), window.summary.total_qty);
        assertEquals(Integer.valueOf(1), window.summary.fill_count);
        assertEquals(1, window.items.size());
        assertEquals(Integer.valueOf(1), window.items.get(0).fill_count);
    }

    @Test
    public void unmatchedCompletionDoesNotIncreaseFlipCount() {
        LocalStatsCache cache = new LocalStatsCache();
        List<LocalTradeDelta> deltas = Arrays.asList(
            delta(1_000L, 1, 995, false, 0, 0L, "OFFER_COMPLETED", 100, false),
            delta(2_000L, 1, 995, false, 5, 500L, "OFFER_COMPLETED", 100, false)
        );
        cache.rebuild(deltas);

        StatsSummary summary = cache.getSummary();

        assertEquals(Integer.valueOf(0), summary.fill_count);
        assertEquals(Long.valueOf(0L), summary.total_profit_gp);
        assertEquals(Long.valueOf(0L), summary.total_cost_gp);
        assertTrue(cache.getItems().isEmpty());
    }

    @Test
    public void zeroQuantityCompletionCountsWhenRecentSellWasMatched() {
        LocalStatsCache cache = new LocalStatsCache();
        List<LocalTradeDelta> deltas = Arrays.asList(
            delta(1_000L, 2, 4151, true, 1, 100L, "OFFER_UPDATED", 100, false),
            delta(2_000L, 2, 4151, false, 1, 120L, "OFFER_UPDATED", 120, false),
            delta(2_100L, 2, 4151, false, 0, 0L, "OFFER_COMPLETED", 120, false)
        );
        cache.rebuild(deltas);

        StatsSummary summary = cache.getSummary();

        assertEquals(Long.valueOf(20L), summary.total_profit_gp);
        assertEquals(Integer.valueOf(1), summary.fill_count);
        assertEquals(1, cache.getItems().size());
        assertEquals(Integer.valueOf(1), cache.getItems().get(0).fill_count);
    }

    private static LocalTradeDelta delta(long tsClientMs, int slot, int itemId, boolean isBuy, int deltaQty,
                                         long deltaGp, String eventType, int price, boolean baselineSynthetic) {
        return new LocalTradeDelta(
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
