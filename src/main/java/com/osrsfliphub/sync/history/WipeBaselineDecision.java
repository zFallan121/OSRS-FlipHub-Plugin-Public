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

/**
 * What a settled read of the history means for the cursor: set it, skip, or import
 * the rows above the overlap. On the ordinary path that is all there is to it. While
 * the wipe barrier is armed for the account the rules are stricter, because the cursor
 * the wipe set is the only thing standing between the rows the player just wiped and
 * a second import of every one of them.
 *
 * <p>Before either path: the in-game list never shrinks, so a read with fewer rows
 * than the stored cursor is a partial read that got past the settle rules, or a parser
 * that has started rejecting rows. Neither may become the cursor, on any path.
 */
@javax.inject.Singleton
final class WipeBaselineDecision {
    enum Outcome {
        SET_BASELINE,
        SKIP_MISMATCH,
        /** The read has fewer rows than the last sync saw: not trusted, nothing changes. */
        SKIP_SHORT_READ,
        PROCEED
    }

    static final class Decision {
        final Outcome outcome;
        final int eligibleTradeCount;
        private final boolean reconcilesWipe;

        private Decision(Outcome outcome, int eligibleTradeCount, boolean reconcilesWipe) {
            this.outcome = outcome;
            this.eligibleTradeCount = Math.max(0, eligibleTradeCount);
            this.reconcilesWipe = reconcilesWipe;
        }

        static Decision setBaseline() {
            return new Decision(Outcome.SET_BASELINE, 0, false);
        }

        static Decision skipMismatch() {
            return new Decision(Outcome.SKIP_MISMATCH, 0, false);
        }

        static Decision skipShortRead() {
            return new Decision(Outcome.SKIP_SHORT_READ, 0, false);
        }

        static Decision proceed(int eligibleTradeCount) {
            return proceed(eligibleTradeCount, false);
        }

        static Decision proceed(int eligibleTradeCount, boolean reconcilesWipe) {
            return new Decision(Outcome.PROCEED, eligibleTradeCount, reconcilesWipe);
        }

        /**
         * Whether acting on this decision is the end of what the wipe barrier was armed
         * for. The barrier exists to protect the cursor a wipe set until one sync has
         * reconciled the visible history against it. Once that sync has run, every row
         * the wipe could have exposed is either matched or imported and the cursor is
         * fresh, so the account is back in the ordinary state and the ordinary rules
         * serve it better: they refuse a zero overlap instead of importing on it, and
         * they do not sit on a low overlap for ever. Setting a baseline or skipping
         * reconciles nothing, so neither releases it.
         */
        boolean releasesWipeBarrier() {
            return outcome == Outcome.PROCEED && reconcilesWipe;
        }
    }

    private final int minMatchThreshold;
    private final int rolloverMinCursorLength;

    /** How many short reads in a row before the cursor is rewritten instead of trusted. */
    private final int shortReadsBeforeRebaseline;
    private int consecutiveShortReads;

    @javax.inject.Inject
    WipeBaselineDecision() {
        this(Const.GE_HISTORY_CURSOR_MIN_MATCH,
            Const.GE_HISTORY_CURSOR_ROLLOVER_MIN_LEN,
            Const.GE_HISTORY_SHORT_READS_BEFORE_REBASELINE);
    }

    WipeBaselineDecision(int minMatchThreshold, int rolloverMinCursorLength) {
        this(minMatchThreshold, rolloverMinCursorLength,
            Const.GE_HISTORY_SHORT_READS_BEFORE_REBASELINE);
    }

    WipeBaselineDecision(int minMatchThreshold, int rolloverMinCursorLength,
                                         int shortReadsBeforeRebaseline) {
        this.minMatchThreshold = Math.max(0, minMatchThreshold);
        this.rolloverMinCursorLength = Math.max(1, rolloverMinCursorLength);
        this.shortReadsBeforeRebaseline = Math.max(1, shortReadsBeforeRebaseline);
    }

    /** The wipe-barrier path. */
    Decision decide(List<String> currentCursor, List<String> storedCursor, int parsedTradesCount, int overlap) {
        return decide(true, currentCursor, storedCursor, parsedTradesCount, overlap);
    }

    Decision decide(boolean wipeBarrierArmed,
                    List<String> currentCursor,
                    List<String> storedCursor,
                    int parsedTradesCount,
                    int overlap) {
        int safeParsedTradesCount = Math.max(0, parsedTradesCount);
        if (storedCursor == null || storedCursor.isEmpty()) {
            return Decision.setBaseline();
        }
        if (cursorSize(currentCursor) < storedCursor.size()) {
            consecutiveShortReads++;
            if (consecutiveShortReads < shortReadsBeforeRebaseline) {
                // Normally a half-drawn list. Read it again next time rather than trusting it.
                return Decision.skipShortRead();
            }
            // It has been short every time for a while now, so the cursor is describing rows
            // that are not coming back. Without this the sync skipped on that comparison for
            // ever, with nothing in the interface able to clear it. Record where we are and
            // import nothing, which is what a first run does.
            consecutiveShortReads = 0;
            return Decision.setBaseline();
        }
        consecutiveShortReads = 0;

        int safeOverlap = Math.max(0, overlap);
        if (!wipeBarrierArmed) {
            // No overlap at all means every visible row is past the cursor, or the list
            // rolled over entirely. Either way the rows cannot be told from ones already
            // recorded live, so none are imported: the read becomes the new cursor and
            // the sync moves on from here.
            int eligible = safeOverlap > 0 ? Math.max(0, safeParsedTradesCount - safeOverlap) : 0;
            return Decision.proceed(eligible, false);
        }

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
        return Decision.proceed(eligibleTradeCount, true);
    }

    private int cursorSize(List<String> cursor) {
        return cursor != null ? cursor.size() : 0;
    }
}
