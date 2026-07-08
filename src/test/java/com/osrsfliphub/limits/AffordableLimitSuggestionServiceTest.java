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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AffordableLimitSuggestionServiceTest {
    private final AffordableLimitSuggestionService service = new AffordableLimitSuggestionService(null, null);

    @Test
    public void computeUsesEnteredPriceBeforeSelectedOfferPrice() {
        assertEquals(Integer.valueOf(100), service.computeAffordableLimit(null, 100, 200, 10_000L));
    }

    @Test
    public void computeFallsBackToSelectedOfferPrice() {
        assertEquals(Integer.valueOf(11), service.computeAffordableLimit(null, 0, 250, 2_750L));
    }

    @Test
    public void computeClampsToRemainingLimitWhenPresent() {
        assertEquals(Integer.valueOf(25), service.computeAffordableLimit(25, 100, null, 10_000L));
    }

    @Test
    public void computeReturnsNullWithoutUsablePriceOrCoins() {
        assertNull(service.computeAffordableLimit(10, null, null, 0L));
        assertNull(service.computeAffordableLimit(10, 100, null, 0L));
    }

    @Test
    public void computeCapsAtIntegerMaxValue() {
        assertEquals(Integer.valueOf(Integer.MAX_VALUE),
            service.computeAffordableLimit(null, 1, null, (long) Integer.MAX_VALUE + 1000L));
    }
}
