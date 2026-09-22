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

final class Const {
    static final int MAX_BATCH_SIZE = 200;
    static final int MAX_BACKFILL_PROFILE_COUNT = 16;
    static final int DEFAULT_ITEMS_PAGE_SIZE = 10;
    static final long SUGGESTION_UPDATE_INTERVAL_MS = 250L;
    static final long PROFILE_WATCH_DEBOUNCE_MS = 1000L;
    static final long ACCOUNTWIDE_KEY = 0L;
    static final long LOCAL_LIMIT_WINDOW_MS = 4L * 60L * 60L * 1000L;
    static final String ACCOUNTWIDE_KEY_STRING = "accountwide";
    static final int GE_HISTORY_GROUP_ID = 383;
    static final int GE_HISTORY_CONTAINER_CHILD_ID = 3;
    // GE history UI only shows ~42 entries; treat as <=45 for safety.
    static final int GE_HISTORY_CURSOR_MAX_TRADES = 45;
    /**
     * Slot number the GE-history sync starts numbering replayed trades from.
     * Real Grand Exchange slots are single digits, so a delta at or above this
     * was reconstructed from the history widget - which carries no timestamps,
     * meaning the delta's own timestamp was made up at replay time and is not
     * evidence of when anything happened.
     */
    static final int GE_HISTORY_SYNTHETIC_SLOT_START = 10_000;
    /** Taken off every moment in it when it is read back: a fill reaches the plugin a tick after the game. */
    static final long GE_HISTORY_SYNCED_SINCE_SLACK_MS = 60_000L;
    static final String[] OFFER_STATUS_MARKERS = new String[] {
        "offer status",
        "you have bought",
        "you have sold",
        "bought a total",
        "sold a total"
    };
    static final long LOCAL_EVENT_BUCKET_MS = 600L;
    static final long DUPLICATE_TRADE_WINDOW_MS = 2_000L;

    private Const() {
    }
}
