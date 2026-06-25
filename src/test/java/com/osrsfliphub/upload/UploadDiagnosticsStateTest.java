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
import static org.junit.Assert.assertTrue;

public class UploadDiagnosticsStateTest {
    @Test
    public void enqueueEventCapsQueueAndTracksDrops() {
        UploadDiagnosticsState state = new UploadDiagnosticsState();

        state.enqueueEvent(sampleEvent("a"), 2);
        state.enqueueEvent(sampleEvent("b"), 2);
        state.enqueueEvent(sampleEvent("c"), 2);

        assertEquals(2, state.getPendingUploadEvents());
        String tooltip = state.buildTooltip(true);
        assertTrue(tooltip.contains("Dropped events: 1"));
    }

    @Test
    public void markSuccessUpdatesTooltip() {
        UploadDiagnosticsState state = new UploadDiagnosticsState();

        state.markAttempt();
        state.markSuccess(5, 200);

        String tooltip = state.buildTooltip(true);
        assertTrue(tooltip.contains("Uploaded events: 5"));
        assertTrue(tooltip.contains("Last status: 200"));
        assertTrue(tooltip.contains("Last error: none"));
    }

    private GeEvent sampleEvent(String id) {
        GeEvent event = new GeEvent();
        event.event_id = id;
        event.event_type = "OFFER_UPDATED";
        event.ts_client_ms = System.currentTimeMillis();
        event.slot = 1;
        event.item_id = 4151;
        event.is_buy = true;
        event.price = 1000;
        event.delta_qty = 1;
        event.delta_gp = 1000L;
        return event;
    }
}
