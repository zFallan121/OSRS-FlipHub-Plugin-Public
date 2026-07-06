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

@javax.inject.Singleton
final class GeHistoryAutoSyncMessageService {
    @javax.inject.Inject
    GeHistoryAutoSyncMessageService() {
    }

    String baselineSetMessage(int cursorSize) {
        int safeCursorSize = Math.max(0, cursorSize);
        return "FlipHub GE history sync: wipe baseline set (" + safeCursorSize + " trades).";
    }

    String baselineMismatchMessage() {
        return "FlipHub GE history sync: skipped (wipe baseline mismatch).";
    }

    String syncResultMessage(int addedTrades) {
        int safeAddedTrades = Math.max(0, addedTrades);
        if (safeAddedTrades <= 0) {
            return "FlipHub GE history sync: no new trades found.";
        }
        int eventsAdded = safeAddedTrades * 2;
        return "FlipHub GE history sync: "
            + safeAddedTrades
            + " trades synced ("
            + eventsAdded
            + " events added).";
    }
}
