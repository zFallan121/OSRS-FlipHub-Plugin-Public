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

import java.util.List;

@javax.inject.Singleton
final class GeHistoryWipeBaselineDecisionService {
    enum Outcome {
        SET_BASELINE,
        SKIP_MISMATCH,
        PROCEED
    }

    static final class Decision {
        final Outcome outcome;
        final int eligibleTradeCount;

        private Decision(Outcome outcome, int eligibleTradeCount) {
            this.outcome = outcome;
            this.eligibleTradeCount = Math.max(0, eligibleTradeCount);
        }

        static Decision setBaseline() {
            return new Decision(Outcome.SET_BASELINE, 0);
        }

        static Decision skipMismatch() {
            return new Decision(Outcome.SKIP_MISMATCH, 0);
        }

        static Decision proceed(int eligibleTradeCount) {
            return new Decision(Outcome.PROCEED, eligibleTradeCount);
        }
    }

    private final int minMatchThreshold;
    private final int rolloverMinCursorLength;

    @javax.inject.Inject
    GeHistoryWipeBaselineDecisionService() {
        this(GeLifecyclePluginConstants.GE_HISTORY_CURSOR_MIN_MATCH,
            GeLifecyclePluginConstants.GE_HISTORY_CURSOR_ROLLOVER_MIN_LEN);
    }

    GeHistoryWipeBaselineDecisionService(int minMatchThreshold, int rolloverMinCursorLength) {
        this.minMatchThreshold = Math.max(0, minMatchThreshold);
        this.rolloverMinCursorLength = Math.max(1, rolloverMinCursorLength);
    }

    Decision decide(List<String> currentCursor, List<String> storedCursor, int parsedTradesCount, int overlap) {
        int safeParsedTradesCount = Math.max(0, parsedTradesCount);
        if (storedCursor == null || storedCursor.isEmpty()) {
            return Decision.setBaseline();
        }

        int safeOverlap = Math.max(0, overlap);
        int minMatch = Math.min(minMatchThreshold, storedCursor.size());
        boolean rollover = safeOverlap == 0
            && storedCursor.size() >= rolloverMinCursorLength
            && cursorSize(currentCursor) >= rolloverMinCursorLength;

        if (!rollover && safeOverlap < minMatch) {
            return Decision.skipMismatch();
        }

        int eligibleTradeCount = rollover
            ? safeParsedTradesCount
            : Math.max(0, safeParsedTradesCount - safeOverlap);
        return Decision.proceed(eligibleTradeCount);
    }

    private int cursorSize(List<String> cursor) {
        return cursor != null ? cursor.size() : 0;
    }
}
