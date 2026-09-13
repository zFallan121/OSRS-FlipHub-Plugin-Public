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

/**
 * When a read of the history list may be acted on.
 *
 * <p>A cursor persisted from a partial read is the one mistake the sync cannot recover
 * from: the rows between the old cursor and the truncated one are never imported. So
 * an incomplete read is never acted on, however long the tab has been open, and a
 * complete one only once it has held still.
 */
public class GeHistoryAutoSyncStateServiceTest {
    private static final AutoSyncState.ReadVerdict WAIT =
        AutoSyncState.ReadVerdict.WAIT;
    private static final AutoSyncState.ReadVerdict SETTLED =
        AutoSyncState.ReadVerdict.SETTLED;
    private static final AutoSyncState.ReadVerdict GIVE_UP =
        AutoSyncState.ReadVerdict.GIVE_UP;

    private static final List<String> ROWS = Arrays.asList("561|B|1000|118000", "1513|S|70000|77000000");
    private static final List<String> MORE_ROWS =
        Arrays.asList("4151|B|1|2000000", "561|B|1000|118000", "1513|S|70000|77000000");

    @Test
    public void armAndDisarmControlPendingState() {
        AutoSyncState service = new AutoSyncState(2_000L);

        assertFalse(service.isPending());
        service.arm();
        assertTrue(service.isPending());
        service.disarm();
        assertFalse(service.isPending());
    }

    @Test
    public void anIncompleteReadIsWaitedOnHoweverLongTheTabHasBeenOpen() {
        // Five widgets is most of a row. Acting on it after two seconds used to
        // persist a cursor with nothing in it, over the top of a real one.
        AutoSyncState service = new AutoSyncState(2_000L, 20_000L);
        service.arm();
        service.noteHistoryVisible(1_000L);

        assertEquals(WAIT, service.observeRead(false, 5, Collections.emptyList(), 2_500L));
        assertEquals(WAIT, service.observeRead(false, 5, Collections.emptyList(), 3_000L));
        assertEquals(WAIT, service.observeRead(false, 5, Collections.emptyList(), 15_000L));
    }

    @Test
    public void aCompleteReadIsTrustedOnceItHasHeldStillForTheSettleWindow() {
        AutoSyncState service = new AutoSyncState(2_000L, 20_000L);
        service.arm();
        service.noteHistoryVisible(1_000L);

        assertEquals(WAIT, service.observeRead(true, 12, ROWS, 1_000L));
        assertEquals(WAIT, service.observeRead(true, 12, ROWS, 2_500L));
        assertEquals(SETTLED, service.observeRead(true, 12, ROWS, 3_000L));
    }

    @Test
    public void aReadThatIsStillChangingRestartsTheSettleWindow() {
        // Two rows, then a third arrives: the list is still being filled in, and the
        // clock starts again from the read that has the third row.
        AutoSyncState service = new AutoSyncState(2_000L, 20_000L);
        service.arm();
        service.noteHistoryVisible(1_000L);

        assertEquals(WAIT, service.observeRead(true, 12, ROWS, 1_000L));
        assertEquals(WAIT, service.observeRead(true, 12, ROWS, 2_000L));
        assertEquals(WAIT, service.observeRead(true, 18, MORE_ROWS, 2_500L));
        assertEquals(WAIT, service.observeRead(true, 18, MORE_ROWS, 4_000L));
        assertEquals(SETTLED, service.observeRead(true, 18, MORE_ROWS, 4_500L));
    }

    @Test
    public void hidingTheHistoryForgetsWhatWasRead() {
        AutoSyncState service = new AutoSyncState(2_000L, 20_000L);
        service.arm();
        service.noteHistoryVisible(1_000L);
        service.observeRead(true, 12, ROWS, 1_000L);
        assertEquals(SETTLED, service.observeRead(true, 12, ROWS, 3_500L));

        service.markHistoryHidden();
        service.noteHistoryVisible(5_000L);

        assertEquals(WAIT, service.observeRead(true, 12, ROWS, 5_000L));
        assertEquals(WAIT, service.observeRead(true, 12, ROWS, 6_500L));
        assertEquals(SETTLED, service.observeRead(true, 12, ROWS, 7_000L));
    }

    @Test
    public void aNonPositiveSettleWindowTrustsTheFirstCompleteReadButNeverAnIncompleteOne() {
        AutoSyncState service = new AutoSyncState(-1L, 20_000L);
        service.arm();
        service.noteHistoryVisible(1_000L);

        assertEquals(WAIT, service.observeRead(false, 5, Collections.emptyList(), 1_000L));
        assertEquals(SETTLED, service.observeRead(true, 12, ROWS, 1_000L));
    }

    @Test
    public void aReadThatNeverSettlesIsGivenUpOnAfterTheGiveUpWindow() {
        AutoSyncState service = new AutoSyncState(2_000L, 20_000L);
        service.arm();
        service.noteHistoryVisible(1_000L);

        assertEquals(WAIT, service.observeRead(false, 0, Collections.emptyList(), 1_000L));
        assertEquals(WAIT, service.observeRead(false, 0, Collections.emptyList(), 20_999L));
        assertEquals(GIVE_UP, service.observeRead(false, 0, Collections.emptyList(), 21_000L));
    }

    /**
     * A History tab left open while offers keep completing. The list changes each time, so
     * the sync must keep waiting rather than deciding the tab never loaded. On the old clock,
     * which ran from the moment the tab opened, the first unsettled read past twenty seconds
     * gave up and switched the sync off for the rest of the login.
     */
    @Test
    public void aListThatKeepsChangingIsStillLoading() {
        AutoSyncState service = new AutoSyncState(2_000L, 20_000L);
        service.arm();
        service.noteHistoryVisible(1_000L);

        assertEquals(WAIT, service.observeRead(true, 2, ROWS, 1_000L));
        // Well past the give-up window, but a new row has just arrived.
        assertEquals(WAIT, service.observeRead(true, 3, MORE_ROWS, 60_000L));
        // And it settles normally once it holds still.
        assertEquals(SETTLED, service.observeRead(true, 3, MORE_ROWS, 62_000L));
    }

    /**
     * A list that goes back to being unreadable is still given up on, and that half is still
     * measured from when the tab opened, because an unreadable list has no last change to
     * measure from.
     */
    @Test
    public void aListThatStopsBeingReadableIsStillGivenUpOn() {
        AutoSyncState service = new AutoSyncState(2_000L, 20_000L);
        service.arm();
        service.noteHistoryVisible(1_000L);

        assertEquals(WAIT, service.observeRead(true, 2, ROWS, 5_000L));
        assertEquals(WAIT, service.observeRead(false, 0, Collections.emptyList(), 6_000L));
        assertEquals(WAIT, service.observeRead(false, 0, Collections.emptyList(), 20_999L));
        assertEquals(GIVE_UP, service.observeRead(false, 0, Collections.emptyList(), 21_000L));
    }
}
