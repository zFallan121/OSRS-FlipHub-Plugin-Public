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
    /** A history with nothing past max cash in it, which is nearly every history. */
    private static final List<Trade> ORDINARY_ROWS = Arrays.asList(
        new Trade(1513, false, 70_000, 1_100, 77_000_000L),
        new Trade(561, true, 1_000, 118, 118_000L));

    // Version 2 held a row's price in an int. A purchase past max cash came out as nothing or
    // less and was left out of the list, so it was never in a cursor. Version 3 reads it. Where
    // the list holds no such purchase the two versions write the same cursor, and one stored
    // under version 2 is still good: nobody's place is lost to the update.

    @Test
    public void aVersionTwoCursorIsStillReadWhenTheListHoldsNothingItLeftOut() {
        GeHistoryCursorService.StoredCursor cursor =
            GeHistoryCursorService.decode("v2,1513|S|70000|77000000,561|B|1000|118000", ORDINARY_ROWS);

        assertEquals(Arrays.asList("1513|S|70000|77000000", "561|B|1000|118000"), cursor.signatures);
        assertFalse(cursor.staleFormat);
    }

    /**
     * A sale past max cash, and a purchase whose price came out wrong but positive, were both in
     * version 2's lists with the same quantity and coins, so its cursors still line up.
     */
    @Test
    public void aVersionTwoCursorIsStillReadBesideRowsPastMaxCashThatItDidList() {
        List<Trade> rows = Arrays.asList(
            new Trade(20011, false, 1, 2_400_000_000L, 2_395_000_000L),
            new Trade(20014, true, 1, 9_199_000_000L, 9_199_000_000L),
            new Trade(561, true, 1_000, 118, 118_000L));

        assertFalse(GeHistoryCursorService.decode("v2,561|B|1000|118000", rows).staleFormat);
        assertEquals(1, GeHistoryCursorService.decode("v2,561|B|1000|118000", rows).signatures.size());
    }

    /**
     * With a pickaxe bought at 2,394,000,000 in the list, the old cursor has a gap where the new
     * read has a row. Lined up against each other they can match in the wrong place, and rows
     * recorded long ago would be handed over as new. The old cursor is retired instead: this one
     * sync imports nothing and starts afresh.
     */
    @Test
    public void aVersionTwoCursorIsRetiredWhenTheListHoldsAPurchaseItLeftOut() {
        List<Trade> rows = Arrays.asList(
            new Trade(561, true, 1_000, 118, 118_000L),
            new Trade(20011, true, 1, 2_394_000_000L, 2_394_000_000L),
            new Trade(1513, false, 70_000, 1_100, 77_000_000L));

        GeHistoryCursorService.StoredCursor cursor =
            GeHistoryCursorService.decode("v2,1513|S|70000|77000000", rows);

        assertTrue(cursor.isEmpty());
        assertTrue(cursor.staleFormat);
        // Its own version reads the same list without a second thought.
        assertFalse(GeHistoryCursorService.decode("v3,1513|S|70000|77000000", rows).staleFormat);
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
        GeHistoryCursorService.StoredCursor cursor = GeHistoryCursorService.decode(raw, ORDINARY_ROWS);

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
            GeHistoryCursorService.decode("1513|S|70000|1100|77000000,561|B|1000|118|118000", ORDINARY_ROWS);

        assertTrue(cursor.isEmpty());
        assertTrue(cursor.staleFormat);
    }

    @Test
    public void nothingStoredIsNoCursorAndNotStale() {
        assertTrue(GeHistoryCursorService.decode(null, ORDINARY_ROWS).isEmpty());
        assertFalse(GeHistoryCursorService.decode(null, ORDINARY_ROWS).staleFormat);
        assertTrue(GeHistoryCursorService.decode("   ", ORDINARY_ROWS).isEmpty());
        assertFalse(GeHistoryCursorService.decode("   ", ORDINARY_ROWS).staleFormat);
        assertTrue(GeHistoryCursorService.decode("v3", ORDINARY_ROWS).isEmpty());
        assertFalse(GeHistoryCursorService.decode("v3", ORDINARY_ROWS).staleFormat);
        // A separator and nothing else is no version this code wrote.
        assertTrue(GeHistoryCursorService.decode(",", ORDINARY_ROWS).staleFormat);
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
