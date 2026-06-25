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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GeHistoryAutoSyncStateServiceTest {
    @Test
    public void armAndDisarmControlPendingState() {
        GeHistoryAutoSyncStateService service = new GeHistoryAutoSyncStateService(2_000L);

        assertFalse(service.isPending());
        service.arm();
        assertTrue(service.isPending());
        service.disarm();
        assertFalse(service.isPending());
    }

    @Test
    public void shouldWaitForSettleUsesVisibleSinceTime() {
        GeHistoryAutoSyncStateService service = new GeHistoryAutoSyncStateService(2_000L);

        service.arm();
        service.noteHistoryVisible(1_000L);

        assertTrue(service.shouldWaitForSettle(true, 2_500L));
        assertFalse(service.shouldWaitForSettle(true, 3_000L));
        assertFalse(service.shouldWaitForSettle(false, 1_500L));
    }

    @Test
    public void markHistoryHiddenResetsSettleWindow() {
        GeHistoryAutoSyncStateService service = new GeHistoryAutoSyncStateService(2_000L);

        service.arm();
        service.noteHistoryVisible(1_000L);
        assertFalse(service.shouldWaitForSettle(true, 3_500L));

        service.markHistoryHidden();
        service.noteHistoryVisible(5_000L);
        assertTrue(service.shouldWaitForSettle(true, 6_500L));
    }

    @Test
    public void nonPositiveSettleWindowNeverWaits() {
        GeHistoryAutoSyncStateService service = new GeHistoryAutoSyncStateService(-1L);

        service.arm();
        service.noteHistoryVisible(1_000L);
        assertFalse(service.shouldWaitForSettle(true, 1_000L));
    }
}
