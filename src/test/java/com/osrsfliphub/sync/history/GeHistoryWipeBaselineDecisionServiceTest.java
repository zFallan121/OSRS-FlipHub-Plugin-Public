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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GeHistoryWipeBaselineDecisionServiceTest {
    @Test
    public void decideReturnsSetBaselineWhenStoredCursorMissing() {
        WipeBaselineDecision service = new WipeBaselineDecision(2, 3);

        WipeBaselineDecision.Decision decision =
            service.decide(Arrays.asList("a", "b"), Collections.emptyList(), 5, 0);

        assertEquals(WipeBaselineDecision.Outcome.SET_BASELINE, decision.outcome);
        assertEquals(0, decision.eligibleTradeCount);
    }

    @Test
    public void decideReturnsSkipMismatchWhenOverlapBelowMinAndNoRollover() {
        WipeBaselineDecision service = new WipeBaselineDecision(2, 3);

        WipeBaselineDecision.Decision decision =
            service.decide(Arrays.asList("x", "y", "w"), Arrays.asList("a", "b", "c"), 8, 1);

        assertEquals(WipeBaselineDecision.Outcome.SKIP_MISMATCH, decision.outcome);
        assertEquals(0, decision.eligibleTradeCount);
    }

    @Test
    public void decideReturnsProceedWithTrimmedCountWhenOverlapMeetsMin() {
        WipeBaselineDecision service = new WipeBaselineDecision(2, 3);

        WipeBaselineDecision.Decision decision =
            service.decide(Arrays.asList("a", "b", "c"), Arrays.asList("a", "b", "z"), 10, 2);

        assertEquals(WipeBaselineDecision.Outcome.PROCEED, decision.outcome);
        assertEquals(8, decision.eligibleTradeCount);
    }

    @Test
    public void decideReturnsProceedWithAllTradesWhenRolloverDetected() {
        WipeBaselineDecision service = new WipeBaselineDecision(2, 3);

        WipeBaselineDecision.Decision decision =
            service.decide(Arrays.asList("a", "b", "c"), Arrays.asList("x", "y", "z"), 7, 0);

        assertEquals(WipeBaselineDecision.Outcome.PROCEED, decision.outcome);
        assertEquals(7, decision.eligibleTradeCount);
    }

    @Test
    public void onlyASyncThatReconcilesTheHistoryReleasesTheWipeBarrier() {
        // The barrier guards the wipe's cursor until one sync has checked the visible
        // history against it. Setting a baseline or skipping checks nothing, so the
        // barrier has to stay up for those; the next proceed lets it go.
        WipeBaselineDecision service = new WipeBaselineDecision(2, 3);

        assertFalse(service.decide(Arrays.asList("a", "b"), Collections.emptyList(), 5, 0)
            .releasesWipeBarrier());
        assertFalse(service.decide(Arrays.asList("x", "y", "w"), Arrays.asList("a", "b", "c"), 8, 1)
            .releasesWipeBarrier());
        assertTrue(service.decide(Arrays.asList("a", "b", "c"), Arrays.asList("a", "b", "z"), 10, 2)
            .releasesWipeBarrier());
        assertTrue(service.decide(Arrays.asList("a", "b", "c"), Arrays.asList("x", "y", "z"), 7, 0)
            .releasesWipeBarrier());
        // The ordinary path has no barrier to release.
        assertFalse(service.decide(false, Arrays.asList("a", "b", "c"), Arrays.asList("a", "b", "z"), 10, 2)
            .releasesWipeBarrier());
    }

    @Test
    public void aReadShorterThanTheStoredCursorIsSkippedOnBothPaths() {
        // The in-game list never shrinks, so two rows where the last sync saw three
        // is a read that lost a row. The overlap would have let it proceed; it must
        // not, because the cursor it would persist would drop that row for good.
        WipeBaselineDecision service = new WipeBaselineDecision(2, 3);

        WipeBaselineDecision.Decision armed =
            service.decide(true, Arrays.asList("b", "c"), Arrays.asList("a", "b", "c"), 2, 2);
        WipeBaselineDecision.Decision ordinary =
            service.decide(false, Arrays.asList("b", "c"), Arrays.asList("a", "b", "c"), 2, 2);

        assertEquals(WipeBaselineDecision.Outcome.SKIP_SHORT_READ, armed.outcome);
        assertEquals(WipeBaselineDecision.Outcome.SKIP_SHORT_READ, ordinary.outcome);
        assertEquals(0, armed.eligibleTradeCount);
        assertEquals(0, ordinary.eligibleTradeCount);
        assertFalse(armed.releasesWipeBarrier());
    }

    @Test
    public void withoutTheBarrierAZeroOverlapResetsTheCursorWithoutImporting() {
        // Every visible row is past the cursor. They may all have been watched live
        // here, or all made on another client; nothing tells them apart, so nothing
        // is imported and the read becomes the new cursor.
        WipeBaselineDecision service = new WipeBaselineDecision(2, 3);

        WipeBaselineDecision.Decision decision =
            service.decide(false, Arrays.asList("x", "y", "z"), Arrays.asList("a", "b", "c"), 3, 0);

        assertEquals(WipeBaselineDecision.Outcome.PROCEED, decision.outcome);
        assertEquals(0, decision.eligibleTradeCount);
    }

    @Test
    public void withoutTheBarrierTheRowsAboveTheOverlapAreEligible() {
        WipeBaselineDecision service = new WipeBaselineDecision(2, 3);

        WipeBaselineDecision.Decision decision =
            service.decide(false, Arrays.asList("n", "a", "b", "c"), Arrays.asList("a", "b", "c"), 4, 3);

        assertEquals(WipeBaselineDecision.Outcome.PROCEED, decision.outcome);
        assertEquals(1, decision.eligibleTradeCount);
    }

    @Test
    public void withoutTheBarrierNoStoredCursorSetsTheBaseline() {
        WipeBaselineDecision service = new WipeBaselineDecision(2, 3);

        WipeBaselineDecision.Decision decision =
            service.decide(false, Arrays.asList("a", "b"), Collections.emptyList(), 2, 0);

        assertEquals(WipeBaselineDecision.Outcome.SET_BASELINE, decision.outcome);
        assertEquals(0, decision.eligibleTradeCount);
    }

    @Test
    public void decideClampsEligibleTradeCountToZero() {
        WipeBaselineDecision service = new WipeBaselineDecision(1, 3);

        WipeBaselineDecision.Decision decision =
            service.decide(Arrays.asList("a"), Arrays.asList("a"), 1, 9);

        assertEquals(WipeBaselineDecision.Outcome.PROCEED, decision.outcome);
        assertEquals(0, decision.eligibleTradeCount);
    }

    /**
     * A list that stays shorter than the stored cursor. The first few reads are skipped,
     * because a half-drawn widget looks exactly like this. After enough of them the cursor is
     * describing rows that are gone, and the sync rewrites it rather than skipping for ever.
     * Nothing is imported by the rewrite, so no trade can be double counted by it.
     */
    @Test
    public void aListThatStaysShortEventuallyRewritesTheCursor() {
        WipeBaselineDecision service =
            new WipeBaselineDecision(6, 30, 3);
        List<String> stored = Arrays.asList("a", "b", "c", "d");
        List<String> shortRead = Arrays.asList("a", "b");

        assertEquals(WipeBaselineDecision.Outcome.SKIP_SHORT_READ,
            service.decide(shortRead, stored, 2, 2).outcome);
        assertEquals(WipeBaselineDecision.Outcome.SKIP_SHORT_READ,
            service.decide(shortRead, stored, 2, 2).outcome);
        assertEquals(WipeBaselineDecision.Outcome.SET_BASELINE,
            service.decide(shortRead, stored, 2, 2).outcome);
    }

    /** One good read in between clears the count, so a passing glitch never adds up. */
    @Test
    public void aGoodReadForgetsTheShortOnesBeforeIt() {
        WipeBaselineDecision service =
            new WipeBaselineDecision(6, 30, 3);
        List<String> stored = Arrays.asList("a", "b", "c", "d");
        List<String> shortRead = Arrays.asList("a", "b");
        List<String> fullRead = Arrays.asList("a", "b", "c", "d");

        assertEquals(WipeBaselineDecision.Outcome.SKIP_SHORT_READ,
            service.decide(shortRead, stored, 2, 2).outcome);
        assertEquals(WipeBaselineDecision.Outcome.SKIP_SHORT_READ,
            service.decide(shortRead, stored, 2, 2).outcome);
        service.decide(fullRead, stored, 4, 4);
        assertEquals(WipeBaselineDecision.Outcome.SKIP_SHORT_READ,
            service.decide(shortRead, stored, 2, 2).outcome);
    }
}
