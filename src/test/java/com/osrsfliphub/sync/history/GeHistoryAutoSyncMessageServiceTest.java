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
        GeHistoryAutoSyncMessageService service = new GeHistoryAutoSyncMessageService();

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
        GeHistoryAutoSyncMessageService service = new GeHistoryAutoSyncMessageService();

        assertEquals(
            "FlipHub GE history sync: skipped (wipe baseline mismatch).",
            service.baselineMismatchMessage()
        );
    }

    @Test
    public void syncResultMessageHandlesAddedAndEmptyCases() {
        GeHistoryAutoSyncMessageService service = new GeHistoryAutoSyncMessageService();

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
