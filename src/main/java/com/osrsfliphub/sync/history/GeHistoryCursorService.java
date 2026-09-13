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
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Where the last history sync got to, as the signatures of the rows it saw.
 */
@javax.inject.Singleton
final class GeHistoryCursorService {
    /**
     * The format of a stored cursor. Bump it whenever {@link #buildSignature} changes
     * what it writes, or the parser changes what a row's numbers come out as. Every
     * cursor stored under an older version is then ignored, and the account's next
     * sync sets a fresh baseline rather than reading the mismatch as a rollover.
     */
    static final int FORMAT_VERSION = 2;
    static final String FORMAT_TAG = "v" + FORMAT_VERSION;
    private static final String ROW_SEPARATOR = ",";

    /** A stored cursor as read back: its signatures, and whether one was refused for its format. */
    static final class StoredCursor {
        static final StoredCursor NONE = new StoredCursor(Collections.emptyList(), false);
        static final StoredCursor STALE = new StoredCursor(Collections.emptyList(), true);

        final List<String> signatures;
        /** True when something was stored, but in a format this code does not read. */
        final boolean staleFormat;

        private StoredCursor(List<String> signatures, boolean staleFormat) {
            this.signatures = Collections.unmodifiableList(signatures);
            this.staleFormat = staleFormat;
        }

        boolean isEmpty() {
            return signatures.isEmpty();
        }
    }

    private final int maxCursorTrades;

    @javax.inject.Inject
    GeHistoryCursorService() {
        this(GeLifecyclePluginConstants.GE_HISTORY_CURSOR_MAX_TRADES);
    }

    GeHistoryCursorService(int maxCursorTrades) {
        this.maxCursorTrades = Math.max(1, maxCursorTrades);
    }

    List<String> buildCursorSignatures(List<GeHistoryTrade> trades) {
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

    String buildSignature(GeHistoryTrade trade) {
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

    /** A stored cursor read back. Anything not written under {@link #FORMAT_TAG} is stale. */
    static StoredCursor decode(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return StoredCursor.NONE;
        }
        String[] parts = raw.split(ROW_SEPARATOR);
        if (parts.length == 0 || !FORMAT_TAG.equals(parts[0].trim())) {
            return StoredCursor.STALE;
        }
        List<String> signatures = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            String trimmed = parts[i] != null ? parts[i].trim() : "";
            if (!trimmed.isEmpty()) {
                signatures.add(trimmed);
            }
        }
        return new StoredCursor(signatures, false);
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
