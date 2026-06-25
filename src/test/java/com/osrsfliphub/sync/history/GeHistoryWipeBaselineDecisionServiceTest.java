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
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GeHistoryWipeBaselineDecisionServiceTest {
    @Test
    public void decideReturnsSetBaselineWhenStoredCursorMissing() {
        GeHistoryWipeBaselineDecisionService service = new GeHistoryWipeBaselineDecisionService(2, 3);

        GeHistoryWipeBaselineDecisionService.Decision decision =
            service.decide(Arrays.asList("a", "b"), Collections.emptyList(), 5, 0);

        assertEquals(GeHistoryWipeBaselineDecisionService.Outcome.SET_BASELINE, decision.outcome);
        assertEquals(0, decision.eligibleTradeCount);
    }

    @Test
    public void decideReturnsSkipMismatchWhenOverlapBelowMinAndNoRollover() {
        GeHistoryWipeBaselineDecisionService service = new GeHistoryWipeBaselineDecisionService(2, 3);

        GeHistoryWipeBaselineDecisionService.Decision decision =
            service.decide(Arrays.asList("x", "y"), Arrays.asList("a", "b", "c"), 8, 1);

        assertEquals(GeHistoryWipeBaselineDecisionService.Outcome.SKIP_MISMATCH, decision.outcome);
        assertEquals(0, decision.eligibleTradeCount);
    }

    @Test
    public void decideReturnsProceedWithTrimmedCountWhenOverlapMeetsMin() {
        GeHistoryWipeBaselineDecisionService service = new GeHistoryWipeBaselineDecisionService(2, 3);

        GeHistoryWipeBaselineDecisionService.Decision decision =
            service.decide(Arrays.asList("a", "b", "c"), Arrays.asList("a", "b", "z"), 10, 2);

        assertEquals(GeHistoryWipeBaselineDecisionService.Outcome.PROCEED, decision.outcome);
        assertEquals(8, decision.eligibleTradeCount);
    }

    @Test
    public void decideReturnsProceedWithAllTradesWhenRolloverDetected() {
        GeHistoryWipeBaselineDecisionService service = new GeHistoryWipeBaselineDecisionService(2, 3);

        GeHistoryWipeBaselineDecisionService.Decision decision =
            service.decide(Arrays.asList("a", "b", "c"), Arrays.asList("x", "y", "z"), 7, 0);

        assertEquals(GeHistoryWipeBaselineDecisionService.Outcome.PROCEED, decision.outcome);
        assertEquals(7, decision.eligibleTradeCount);
    }

    @Test
    public void decideClampsEligibleTradeCountToZero() {
        GeHistoryWipeBaselineDecisionService service = new GeHistoryWipeBaselineDecisionService(1, 3);

        GeHistoryWipeBaselineDecisionService.Decision decision =
            service.decide(Arrays.asList("a"), Arrays.asList("a"), 1, 9);

        assertEquals(GeHistoryWipeBaselineDecisionService.Outcome.PROCEED, decision.outcome);
        assertEquals(0, decision.eligibleTradeCount);
    }
}
