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

import java.util.*;

/**
 * Where the last history sync got to, as the signatures of the rows it saw.
 *
 * <p>A signature is a row's item, side, quantity and coins - the four numbers read
 * straight off the widget. Not its unit price: the parser derives that, and for a
 * sale it is inferred back through the tax, so it moves whenever that inference
 * does. A cursor built on it stopped matching the moment the parser was touched,
 * and a cursor that matches nothing reads as "every row is new" - which after a
 * wipe means importing every row the player just wiped.
 *
 * <p>For the same reason the stored form carries {@link #FORMAT_VERSION}. A cursor
 * this code cannot read is no cursor at all, and no cursor re-baselines without
 * importing anything. The config key it is stored under does not change with it;
 * the value says what it is.
 */
@javax.inject.Singleton
final class GeHistoryCursorService {
    /**
     * The format of a stored cursor. Bump it whenever {@link #buildSignature} changes
     * what it writes, or the parser changes what a row's numbers come out as. A cursor
     * stored under an older version is then ignored, and the account's next sync sets a
     * fresh baseline rather than reading the mismatch as a rollover.
     *
     * <p>The one older version still read is 2, which {@link #decode} admits beside 3 because
     * the two differ only in the rows version 2 left out ({@link #unreadBefore}). That is true
     * of 3 and of no later version: the bump to 4 takes the exception out of {@link #decode}.
     */
    static final int FORMAT_VERSION = 3;
    static final String FORMAT_TAG = "v" + FORMAT_VERSION;
    private static final String ROW_SEPARATOR = ",";

    /** A stored cursor as read back: its signatures, and whether one was refused for its format. */
    static final class StoredCursor {
        static final StoredCursor NONE = new StoredCursor(Collections.emptyList(), false, false);
        static final StoredCursor STALE = new StoredCursor(Collections.emptyList(), true, false);

        final List<String> signatures;
        /** True when something was stored, but in a format this code does not read. */
        final boolean staleFormat;
        /** True when version 2 wrote it: its rows are the list's without those in {@link #unreadBefore}. */
        final boolean version2;

        private StoredCursor(List<String> signatures, boolean staleFormat, boolean version2) {
            this.signatures = Collections.unmodifiableList(signatures);
            this.staleFormat = staleFormat;
            this.version2 = version2;
        }

        /**
         * The list as this cursor's version read it, which is what the cursor lines up against.
         * Version 2's has a gap wherever the list holds a purchase it could not read; lined up
         * against the whole list the two match in the wrong place, or nowhere.
         */
        List<Trade> listed(List<Trade> trades) {
            List<Trade> listed = new ArrayList<>(trades);
            if (version2) {
                listed.removeIf(GeHistoryCursorService::unreadBefore);
            }
            return listed;
        }

        boolean isEmpty() {
            return signatures.isEmpty();
        }
    }

    private final int maxCursorTrades;

    @javax.inject.Inject
    GeHistoryCursorService() {
        this(Const.GE_HISTORY_CURSOR_MAX_TRADES);
    }

    GeHistoryCursorService(int maxCursorTrades) {
        this.maxCursorTrades = Math.max(1, maxCursorTrades);
    }

    List<String> buildCursorSignatures(List<Trade> trades) {
        List<String> signatures = new ArrayList<>();
        if (trades == null || trades.isEmpty()) {
            return signatures;
        }
        int limit = Math.min(maxCursorTrades, trades.size());
        for (int i = 0; i < limit; i++) {
            String signature = buildSignature(trades.get(i));
            if (signature != null) {
                signatures.add(signature);
            }
        }
        return signatures;
    }

    String buildSignature(Trade trade) {
        if (trade == null || !trade.isValid()) {
            return null;
        }
        return trade.itemId
            + "|" + (trade.isBuy ? "B" : "S")
            + "|" + trade.quantity
            + "|" + trade.totalGp;
    }

    /** The stored form of a cursor: the format tag, then the signatures. Empty stays empty. */
    static String encode(List<String> signatures) {
        if (signatures == null || signatures.isEmpty()) {
            return "";
        }
        return FORMAT_TAG + ROW_SEPARATOR + String.join(ROW_SEPARATOR, signatures);
    }

    /**
     * A purchase version 2 could not read, and so left out of its cursors: its price, cut to 32
     * bits, came out as nothing or less. 2,394,000,000 does; 9,199,000,000 does not, and a sale
     * never did.
     *
     * <p>The price meant is the one worked out from the row's coins, which is the only one
     * either version has for a purchase: the game writes "=&nbsp;N&nbsp;each" with non-breaking
     * spaces (script 1645), and the parser's pattern for it has never matched them. Should that
     * pattern ever be made to match, this stops being what version 2 did.
     */
    static boolean unreadBefore(Trade trade) {
        return trade.isBuy && (int) trade.price <= 0;
    }

    /**
     * A stored cursor read back. Anything not written under {@link #FORMAT_TAG} is stale, but for
     * one written under version 2: the two versions write the same thing for every row both
     * list, so it is kept, marked, and lined up against the list as version 2 read it
     * ({@link StoredCursor#listed}). Nobody's place is lost to the update. It used to be retired
     * whenever the list held a purchase version 2 had left out, wherever that purchase sat: a
     * pickaxe bought on a phone since the last sync cost the player that purchase and every
     * other trade made since, all of them below the fresh baseline for good.
     */
    static StoredCursor decode(String raw) {
        if (Str.isBlank(raw)) {
            return StoredCursor.NONE;
        }
        String[] parts = raw.split(ROW_SEPARATOR);
        String tag = parts.length > 0 ? parts[0].trim() : "";
        boolean version2 = "v2".equals(tag);
        if (!FORMAT_TAG.equals(tag) && !version2) {
            return StoredCursor.STALE;
        }
        List<String> signatures = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            String trimmed = parts[i] != null ? parts[i].trim() : "";
            if (!trimmed.isEmpty()) {
                signatures.add(trimmed);
            }
        }
        return new StoredCursor(signatures, false, version2);
    }

    int computeOverlap(List<String> currentCursor, List<String> storedCursor) {
        if (currentCursor == null || storedCursor == null || currentCursor.isEmpty() || storedCursor.isEmpty()) {
            return 0;
        }
        int max = Math.min(currentCursor.size(), storedCursor.size());
        for (int len = max; len >= 1; len--) {
            boolean match = true;
            int start = currentCursor.size() - len;
            for (int i = 0; i < len; i++) {
                if (!Objects.equals(currentCursor.get(start + i), storedCursor.get(i))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return len;
            }
        }
        return 0;
    }
}
