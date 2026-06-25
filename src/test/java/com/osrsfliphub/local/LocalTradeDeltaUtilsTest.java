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
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LocalTradeDeltaUtilsTest {
    @Test
    public void dedupeLocalTradesCollapsesUpdateCompletionPair() {
        long ts = System.currentTimeMillis();
        LocalTradeDelta update = new LocalTradeDelta(ts, 1, 4151, true, 10, 10000L, "OFFER_UPDATED", 1000, false);
        LocalTradeDelta completion = new LocalTradeDelta(ts + 100, 1, 4151, true, 10, 10000L, "OFFER_COMPLETED", 1000, false);

        List<LocalTradeDelta> result = LocalTradeDeltaUtils.dedupeLocalTrades(
            Arrays.asList(update, completion),
            100,
            600L,
            2000L
        );

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("OFFER_UPDATED", result.get(0).eventType);
        assertEquals("OFFER_COMPLETED", result.get(1).eventType);
        assertEquals(0, result.get(1).deltaQty);
        assertEquals(0L, result.get(1).deltaGp);
    }

    @Test
    public void dedupeLocalTradesConvertsGrossSellDeltaToNet() {
        LocalTradeDelta sell = new LocalTradeDelta(10_000L, 1, 1513, false, 100, 110_000L, "OFFER_UPDATED", 1100, false);

        List<LocalTradeDelta> result = LocalTradeDeltaUtils.dedupeLocalTrades(
            Arrays.asList(sell),
            100,
            600L,
            2_000L
        );

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(107_800L, result.get(0).deltaGp);
    }

    @Test
    public void dedupeLocalTradesCollapsesSellGrossNetCompletionPairOutsideWindow() {
        long ts = 1_000_000L;
        int qty = 4_370;
        int price = 420;
        long gross = (long) qty * price;
        long net = gross - (((long) price / 50L) * qty);
        LocalTradeDelta update = new LocalTradeDelta(ts, 2, 1493, false, qty, gross, "OFFER_UPDATED", price, false);
        LocalTradeDelta completion = new LocalTradeDelta(ts + 600_000L, 2, 1493, false, qty, net, "OFFER_COMPLETED", price, false);

        List<LocalTradeDelta> result = LocalTradeDeltaUtils.dedupeLocalTrades(
            Arrays.asList(update, completion),
            100,
            600L,
            2_000L
        );

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("OFFER_UPDATED", result.get(0).eventType);
        assertEquals("OFFER_COMPLETED", result.get(1).eventType);
        assertEquals(0, result.get(1).deltaQty);
        assertEquals(0L, result.get(1).deltaGp);
    }

    @Test
    public void dedupeLocalTradesSuppressesSellGrossNetRepeatedCompletionOutsideWindow() {
        long ts = 2_000_000L;
        int qty = 4_370;
        int price = 420;
        long gross = (long) qty * price;
        long net = gross - (((long) price / 50L) * qty);
        LocalTradeDelta completionGross = new LocalTradeDelta(ts, 2, 1493, false, qty, gross, "OFFER_COMPLETED", price, false);
        LocalTradeDelta completionNet = new LocalTradeDelta(ts + 600_000L, 2, 1493, false, qty, net, "OFFER_COMPLETED", price, false);

        List<LocalTradeDelta> result = LocalTradeDeltaUtils.dedupeLocalTrades(
            Arrays.asList(completionGross, completionNet),
            100,
            600L,
            2_000L
        );

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("OFFER_COMPLETED", result.get(0).eventType);
    }

    @Test
    public void mergeLocalTradesDedupesAndTrimsToMax() {
        LocalTradeDelta a = new LocalTradeDelta(1000L, 1, 1, true, 1, 100L, "OFFER_UPDATED", 100, false);
        LocalTradeDelta b = new LocalTradeDelta(2000L, 1, 2, true, 1, 200L, "OFFER_UPDATED", 200, false);
        LocalTradeDelta duplicateOfA = new LocalTradeDelta(1000L, 1, 1, true, 1, 100L, "OFFER_UPDATED", 100, false);
        LocalTradeDelta c = new LocalTradeDelta(3000L, 1, 3, true, 1, 300L, "OFFER_UPDATED", 300, false);

        List<LocalTradeDelta> merged = LocalTradeDeltaUtils.mergeLocalTrades(
            new ArrayList<>(Arrays.asList(a, b)),
            new ArrayList<>(Arrays.asList(duplicateOfA, c)),
            null,
            null,
            2
        );

        assertNotNull(merged);
        assertEquals(2, merged.size());
        assertTrue(merged.stream().anyMatch(delta -> delta.itemId == 2));
        assertTrue(merged.stream().anyMatch(delta -> delta.itemId == 3));
    }

    @Test
    public void duplicateTradeDetectionMatchesSameBucketAndType() {
        List<LocalTradeDelta> deltas = new ArrayList<>();
        deltas.add(new LocalTradeDelta(10_000L, 1, 4151, true, 10, 10_000L, "OFFER_UPDATED", 1000, false));
        LocalTradeDelta candidate = new LocalTradeDelta(10_100L, 1, 4151, true, 10, 10_000L, "OFFER_UPDATED", 1000, false);

        boolean duplicate = LocalTradeDeltaUtils.isLikelyDuplicateTradeDelta(
            deltas,
            candidate,
            600L,
            2_000L,
            12
        );

        assertTrue(duplicate);
    }

    @Test
    public void duplicateTradeDetectionIgnoresOutsideWindow() {
        List<LocalTradeDelta> deltas = new ArrayList<>();
        deltas.add(new LocalTradeDelta(10_000L, 1, 4151, true, 10, 10_000L, "OFFER_UPDATED", 1000, false));
        LocalTradeDelta candidate = new LocalTradeDelta(20_500L, 1, 4151, true, 10, 10_000L, "OFFER_UPDATED", 1000, false);

        boolean duplicate = LocalTradeDeltaUtils.isLikelyDuplicateTradeDelta(
            deltas,
            candidate,
            600L,
            2_000L,
            12
        );

        assertFalse(duplicate);
    }
}
