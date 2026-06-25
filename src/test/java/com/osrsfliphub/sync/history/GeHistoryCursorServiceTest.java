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
import static org.junit.Assert.assertNull;

public class GeHistoryCursorServiceTest {
    @Test
    public void buildSignatureReturnsExpectedFormatForValidTrade() {
        GeHistoryCursorService service = new GeHistoryCursorService(45);
        GeHistoryTrade trade = new GeHistoryTrade(1513, false, 70_000, 1_100, 77_000_000L);

        assertEquals("1513|S|70000|1100|77000000", service.buildSignature(trade));
    }

    @Test
    public void buildSignatureReturnsNullForInvalidTrade() {
        GeHistoryCursorService service = new GeHistoryCursorService(45);
        assertNull(service.buildSignature(null));
        assertNull(service.buildSignature(new GeHistoryTrade(0, true, 1, 1, 1L)));
    }

    @Test
    public void buildCursorSignaturesCapsToMaxAndSkipsInvalid() {
        GeHistoryCursorService service = new GeHistoryCursorService(2);
        List<GeHistoryTrade> trades = new ArrayList<>();
        trades.add(new GeHistoryTrade(100, true, 1, 10, 10L));
        trades.add(new GeHistoryTrade(0, true, 1, 10, 10L)); // invalid, skipped
        trades.add(new GeHistoryTrade(200, false, 2, 20, 40L));

        List<String> cursor = service.buildCursorSignatures(trades);
        assertEquals(Arrays.asList("100|B|1|10|10"), cursor);
    }

    @Test
    public void computeOverlapMatchesSuffixToPrefix() {
        GeHistoryCursorService service = new GeHistoryCursorService(45);
        List<String> current = Arrays.asList("x", "a", "b");
        List<String> stored = Arrays.asList("a", "b", "c");

        assertEquals(2, service.computeOverlap(current, stored));
        assertEquals(0, service.computeOverlap(Arrays.asList("1", "2"), Arrays.asList("3", "4")));
        assertEquals(0, service.computeOverlap(new ArrayList<>(), stored));
    }
}
