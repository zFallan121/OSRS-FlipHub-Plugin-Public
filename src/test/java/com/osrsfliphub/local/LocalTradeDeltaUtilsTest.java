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
    /**
     * A legacy completion repeating its update's quantity is first zeroed as the
     * duplicate it is, and then, as the offer's completion, folds the update into one
     * record: the offer's ten, once, timed at the fill and marked with the completion.
     */
    @Test
    public void dedupeLocalTradesCollapsesUpdateCompletionPair() {
        long ts = System.currentTimeMillis();
        Delta update = new Delta(ts, 1, 4151, true, 10, 10000L, "OFFER_UPDATED", 1000, false);
        Delta completion = new Delta(ts + 100, 1, 4151, true, 10, 10000L, "OFFER_COMPLETED", 1000, false);

        List<Delta> result = TradeDeltaUtils.dedupeLocalTrades(
            Arrays.asList(update, completion),
            600L,
            2000L
        );

        assertNotNull(result);
        assertEquals(1, result.size());
        Delta offer = result.get(0);
        assertEquals("OFFER_COMPLETED", offer.eventType);
        assertEquals(10, offer.deltaQty);
        assertEquals(10000L, offer.deltaGp);
        assertEquals(ts, offer.tsClientMs);
        assertEquals(ts + 100, offer.endMs);
    }

    /**
     * Two finished offers of the same size on one slot a quarter of an hour apart are two
     * offers. The legacy repeated-completion heuristic would take the second for a repeat
     * of the first; a record the collapser wrote is exempt from it.
     */
    @Test
    public void twoSameSizedCollapsedOffersOnOneSlotAreBothKept() {
        long ts = 1_000_000L;
        Delta first = new Delta(ts, 3, 4151, true, 1_000, 500_000L, "OFFER_COMPLETED", 500, false,
            ts - 10L, ts + 60_000L);
        Delta second = new Delta(ts + 600_000L, 3, 4151, true, 1_000, 500_000L, "OFFER_COMPLETED",
            500, false, ts + 590_000L, ts + 660_000L);

        List<Delta> result = TradeDeltaUtils.dedupeLocalTrades(
            Arrays.asList(first, second),
            600L,
            2_000L
        );

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(1_000, result.get(0).deltaQty);
        assertEquals(1_000, result.get(1).deltaQty);
    }

    @Test
    public void dedupeLocalTradesConvertsGrossSellDeltaToNet() {
        Delta sell = new Delta(10_000L, 1, 1513, false, 100, 110_000L, "OFFER_UPDATED", 1100, false);

        List<Delta> result = TradeDeltaUtils.dedupeLocalTrades(
            Arrays.asList(sell),
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
        Delta update = new Delta(ts, 2, 1493, false, qty, gross, "OFFER_UPDATED", price, false);
        Delta completion = new Delta(ts + 600_000L, 2, 1493, false, qty, net, "OFFER_COMPLETED", price, false);

        List<Delta> result = TradeDeltaUtils.dedupeLocalTrades(
            Arrays.asList(update, completion),
            600L,
            2_000L
        );

        // The gross update is brought to net, the completion recognised as its repeat and
        // zeroed, and the pair folded into one offer that sold the quantity once, net.
        assertNotNull(result);
        assertEquals(1, result.size());
        Delta offer = result.get(0);
        assertEquals("OFFER_COMPLETED", offer.eventType);
        assertEquals(qty, offer.deltaQty);
        assertEquals(net, offer.deltaGp);
        assertEquals(ts, offer.tsClientMs);
        assertEquals(ts + 600_000L, offer.endMs);
    }

    @Test
    public void dedupeLocalTradesSuppressesSellGrossNetRepeatedCompletionOutsideWindow() {
        long ts = 2_000_000L;
        int qty = 4_370;
        int price = 420;
        long gross = (long) qty * price;
        long net = gross - (((long) price / 50L) * qty);
        Delta completionGross = new Delta(ts, 2, 1493, false, qty, gross, "OFFER_COMPLETED", price, false);
        Delta completionNet = new Delta(ts + 600_000L, 2, 1493, false, qty, net, "OFFER_COMPLETED", price, false);

        List<Delta> result = TradeDeltaUtils.dedupeLocalTrades(
            Arrays.asList(completionGross, completionNet),
            600L,
            2_000L
        );

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("OFFER_COMPLETED", result.get(0).eventType);
    }

    @Test
    public void duplicateTradeDetectionMatchesSameBucketAndType() {
        List<Delta> deltas = new ArrayList<>();
        deltas.add(new Delta(10_000L, 1, 4151, true, 10, 10_000L, "OFFER_UPDATED", 1000, false));
        Delta candidate = new Delta(10_100L, 1, 4151, true, 10, 10_000L, "OFFER_UPDATED", 1000, false);

        boolean duplicate = TradeDeltaUtils.isLikelyDuplicateTradeDelta(
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
        List<Delta> deltas = new ArrayList<>();
        deltas.add(new Delta(10_000L, 1, 4151, true, 10, 10_000L, "OFFER_UPDATED", 1000, false));
        Delta candidate = new Delta(20_500L, 1, 4151, true, 10, 10_000L, "OFFER_UPDATED", 1000, false);

        boolean duplicate = TradeDeltaUtils.isLikelyDuplicateTradeDelta(
            deltas,
            candidate,
            600L,
            2_000L,
            12
        );

        assertFalse(duplicate);
    }
}
