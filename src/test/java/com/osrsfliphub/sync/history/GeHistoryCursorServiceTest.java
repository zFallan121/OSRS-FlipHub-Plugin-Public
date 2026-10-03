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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GeHistoryCursorServiceTest {
    // Version 2 held a row's price in an int. A purchase past max cash came out as nothing or
    // less and was left out of the list, so it was never in a cursor. Version 3 reads it. For
    // every row both list they write the same thing, so a cursor stored under version 2 is
    // still good, lined up against the list as version 2 read it: nobody's place is lost to
    // the update. What that sync then hands over is in GeHistoryFirstSyncAfterUpdateTest.

    @Test
    public void aVersionTwoCursorIsStillReadAndIsMarkedAsOne() {
        GeHistoryCursorService.StoredCursor cursor =
            GeHistoryCursorService.decode("v2,1513|S|70000|77000000,561|B|1000|118000");

        assertEquals(Arrays.asList("1513|S|70000|77000000", "561|B|1000|118000"), cursor.signatures);
        assertFalse(cursor.staleFormat);
        assertTrue(cursor.version2);
        assertFalse(GeHistoryCursorService.decode("v3,1513|S|70000|77000000").version2);
    }

    /**
     * What version 2 listed: every row but a purchase it could not read. A sale past max cash,
     * and a purchase whose price came out wrong but positive, were in its lists with the same
     * quantity and coins as now. Any other cursor lines up against the whole list.
     */
    @Test
    public void aVersionTwoCursorLinesUpAgainstTheListWithoutThePurchasesItLeftOut() {
        Trade sale = new Trade(20011, false, 1, 2_400_000_000L, 2_395_000_000L);
        Trade leftOut = new Trade(20011, true, 1, 2_394_000_000L, 2_394_000_000L);
        Trade wrapped = new Trade(20014, true, 1, 9_199_000_000L, 9_199_000_000L);
        Trade runes = new Trade(561, true, 1_000, 118, 118_000L);
        List<Trade> rows = Arrays.asList(sale, leftOut, wrapped, runes);

        assertEquals(Arrays.asList(sale, wrapped, runes),
            GeHistoryCursorService.decode("v2,561|B|1000|118000").listed(rows));
        assertEquals(rows, GeHistoryCursorService.decode("v3,561|B|1000|118000").listed(rows));
        assertEquals(rows, GeHistoryCursorService.StoredCursor.NONE.listed(rows));
        assertEquals(rows, GeHistoryCursorService.StoredCursor.STALE.listed(rows));
    }

    @Test
    public void onlyAPurchaseWhosePriceCameOutAsNothingOrLessWasLeftOut() {
        assertTrue(GeHistoryCursorService.unreadBefore(new Trade(20011, true, 1, 2_394_000_000L, 2_394_000_000L)));
        assertTrue(GeHistoryCursorService.unreadBefore(new Trade(20011, true, 1, 4_294_967_296L, 4_294_967_296L)));
        assertFalse(GeHistoryCursorService.unreadBefore(new Trade(20014, true, 1, 9_199_000_000L, 9_199_000_000L)));
        assertFalse(GeHistoryCursorService.unreadBefore(new Trade(20011, false, 1, 2_394_000_000L, 2_389_000_000L)));
        assertFalse(GeHistoryCursorService.unreadBefore(new Trade(20011, true, 1, 2_147_483_647L, 2_147_483_647L)));
        assertFalse(GeHistoryCursorService.unreadBefore(new Trade(561, true, 1_000, 118, 118_000L)));
    }

    @Test
    public void buildSignatureReturnsExpectedFormatForValidTrade() {
        GeHistoryCursorService service = new GeHistoryCursorService(45);
        Trade trade = new Trade(1513, false, 70_000, 1_100, 77_000_000L);

        assertEquals("1513|S|70000|77000000", service.buildSignature(trade));
    }

    @Test
    public void buildSignatureReturnsNullForInvalidTrade() {
        GeHistoryCursorService service = new GeHistoryCursorService(45);
        assertNull(service.buildSignature(null));
        assertNull(service.buildSignature(new Trade(0, true, 1, 1, 1L)));
    }

    @Test
    public void buildCursorSignaturesCapsToMaxAndSkipsInvalid() {
        GeHistoryCursorService service = new GeHistoryCursorService(2);
        List<Trade> trades = new ArrayList<>();
        trades.add(new Trade(100, true, 1, 10, 10L));
        trades.add(new Trade(0, true, 1, 10, 10L)); // invalid, skipped
        trades.add(new Trade(200, false, 2, 20, 40L));

        List<String> cursor = service.buildCursorSignatures(trades);
        assertEquals(Arrays.asList("100|B|1|10"), cursor);
    }

    @Test
    public void aStoredCursorReadsBackAsWhatWasWritten() {
        List<String> signatures = Arrays.asList("1513|S|70000|77000000", "561|B|1000|118000");

        String raw = GeHistoryCursorService.encode(signatures);
        GeHistoryCursorService.StoredCursor cursor = GeHistoryCursorService.decode(raw);

        assertEquals("v3,1513|S|70000|77000000,561|B|1000|118000", raw);
        assertEquals(signatures, cursor.signatures);
        assertFalse(cursor.staleFormat);
    }

    @Test
    public void aCursorWrittenByAnEarlierVersionIsNoCursorRatherThanARollover() {
        // A version-one cursor: no tag, and a heuristic sell price in every row. Read
        // as signatures it would match nothing, and on the wipe barrier a cursor that
        // matches nothing is taken for a history that rolled over - importing every
        // row the player just wiped. It has to come back as no cursor at all.
        GeHistoryCursorService.StoredCursor cursor =
            GeHistoryCursorService.decode("1513|S|70000|1100|77000000,561|B|1000|118|118000");

        assertTrue(cursor.isEmpty());
        assertTrue(cursor.staleFormat);
    }

    @Test
    public void nothingStoredIsNoCursorAndNotStale() {
        assertTrue(GeHistoryCursorService.decode(null).isEmpty());
        assertFalse(GeHistoryCursorService.decode(null).staleFormat);
        assertTrue(GeHistoryCursorService.decode("   ").isEmpty());
        assertFalse(GeHistoryCursorService.decode("   ").staleFormat);
        assertTrue(GeHistoryCursorService.decode("v3").isEmpty());
        assertFalse(GeHistoryCursorService.decode("v3").staleFormat);
        // A separator and nothing else is no version this code wrote.
        assertTrue(GeHistoryCursorService.decode(",").staleFormat);
        assertEquals("", GeHistoryCursorService.encode(new ArrayList<>()));
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
