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

public class GeHistoryAutoSyncMessageServiceTest {
    @Test
    public void baselineSetMessageIncludesSafeTradeCount() {
        AutoSyncMessage service = new AutoSyncMessage();

        assertEquals(
            "FlipHub GE history sync: wipe baseline set (3 trades).",
            service.baselineSetMessage(3)
        );
        assertEquals(
            "FlipHub GE history sync: wipe baseline set (0 trades).",
            service.baselineSetMessage(-1)
        );
    }

    @Test
    public void baselineMismatchMessageMatchesExpectedText() {
        AutoSyncMessage service = new AutoSyncMessage();

        assertEquals(
            "FlipHub GE history sync: skipped (wipe baseline mismatch).",
            service.baselineMismatchMessage()
        );
    }

    @Test
    public void untrustedReadMessagesSayWhatWasSeen() {
        AutoSyncMessage service = new AutoSyncMessage();

        assertEquals(
            "FlipHub GE history sync: skipped (History tab never finished loading).",
            service.readIncompleteMessage()
        );
        assertEquals(
            "FlipHub GE history sync: skipped (read 30 trades, the last sync saw 42).",
            service.shortReadMessage(30, 42)
        );
        assertEquals(
            "FlipHub GE history sync: skipped (read 0 trades, the last sync saw 0).",
            service.shortReadMessage(-1, -1)
        );
    }

    @Test
    public void cursorFormatResetMessageIncludesSafeTradeCount() {
        AutoSyncMessage service = new AutoSyncMessage();

        assertEquals(
            "FlipHub GE history sync: stored cursor was from an older version, baseline reset (42 trades, nothing imported).",
            service.cursorFormatResetMessage(42)
        );
        assertEquals(
            "FlipHub GE history sync: stored cursor was from an older version, baseline reset (0 trades, nothing imported).",
            service.cursorFormatResetMessage(-3)
        );
    }

    @Test
    public void syncResultMessageHandlesAddedAndEmptyCases() {
        AutoSyncMessage service = new AutoSyncMessage();

        assertEquals(
            "FlipHub GE history sync: 4 trades synced (8 events added).",
            service.syncResultMessage(4)
        );
        assertEquals(
            "FlipHub GE history sync: no new trades found.",
            service.syncResultMessage(0)
        );
        assertEquals(
            "FlipHub GE history sync: no new trades found.",
            service.syncResultMessage(-5)
        );
    }
}
