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

final class GeLifecyclePluginConstants {
    static final int MAX_BATCH_SIZE = 200;
    static final int MAX_BACKFILL_PROFILE_COUNT = 16;
    static final int MAX_PENDING_UPLOAD_EVENTS = 10_000;
    static final long BACKFILL_MIN_INTERVAL_MS = 60_000L;
    static final long BACKFILL_RETRY_INTERVAL_SECONDS = 90L;
    static final long BACKFILL_RETRY_MAX_INTERVAL_SECONDS = 15 * 60L;
    static final double BACKFILL_MATCH_SCORE_THRESHOLD = 0.45d;
    static final long ACCOUNTWIDE_UPLOAD_INTERVAL_SECONDS = 60L;
    static final long ACCOUNTWIDE_UPLOAD_MIN_INTERVAL_MS = 4_000L;
    static final long ACCOUNTWIDE_UPLOAD_RESYNC_INTERVAL_MS = 5 * 60_000L;
    static final String BACKFILLED_PROFILES_KEY = "backfilledProfilesV1";
    static final int DEFAULT_ITEMS_PAGE_SIZE = 10;
    static final int SUGGESTION_TEXT_COLOR = 0x800000;
    static final int SUGGESTION_HOVER_TEXT_COLOR = 0xFFFFFF;
    static final int SUGGESTION_TOP_Y = 2;
    static final int SUGGESTION_RIGHT_X = 8;
    static final int SUGGESTION_RIGHT_WIDTH_PADDING = 16;
    static final String WIKI_LATEST_URL = "https://prices.runescape.wiki/api/v1/osrs/latest";
    static final String WIKI_USER_AGENT = "FlipHub OSRS Plugin (contact: support@fliphub.app)";
    static final long WIKI_CACHE_TTL_MS = 2 * 60 * 1000;
    static final long WIKI_MIN_REFRESH_MS = 60_000L;
    static final long LOGIN_GRACE_MS = 60_000L;
    static final long SUGGESTION_UPDATE_INTERVAL_MS = 250L;
    static final String PRICE_SUGGESTION_WIDGET_NAME = "FlipHub Current Price";
    static final String LIMIT_SUGGESTION_WIDGET_NAME = "FlipHub Remaining Limit";
    static final String AFFORDABLE_LIMIT_SUGGESTION_WIDGET_NAME = "FlipHub Affordable Limit";
    static final int GE_OFFER_PRICE_VARBIT = 4398;
    static final int COINS_ITEM_ID = 995;
    static final long OFFER_POLL_INTERVAL_MS = 250L;
    static final int MAX_GE_LIMIT_LOOKUPS_PER_REQUEST = 24;
    static final long LOCAL_TRADES_LOAD_RETRY_MS = 1000L;
    static final long PROFILE_WATCH_DEBOUNCE_MS = 1000L;
    static final long ACCOUNTWIDE_KEY = 0L;
    static final long LOCAL_LIMIT_WINDOW_MS = 4L * 60L * 60L * 1000L;
    static final long LOCAL_LIMIT_FUTURE_TOLERANCE_MS = 5L * 60L * 1000L;
    static final String ACCOUNTWIDE_KEY_STRING = "accountwide";
    static final String PROFILE_SELECTION_MODE_KEY = "profileSelectionMode";
    static final String PROFILE_SELECTED_KEY = "selectedProfileKey";
    static final String PROFILE_DIR_NAME = "fliphub";
    static final String LEGACY_PROFILE_DIR_NAME = "fliphub-dev";
    static final String LEGACY_DEV_CONFIG_GROUP = FliphubConfigGroups.LEGACY_DEV_CONFIG_GROUP;
    static final int GE_HISTORY_GROUP_ID = 383;
    static final int GE_HISTORY_CONTAINER_CHILD_ID = 3;
    static final long GE_HISTORY_SYNC_WIDGET_SETTLE_MS = 2_000L;
    // GE history UI only shows ~42 entries; treat as <=45 for safety.
    static final int GE_HISTORY_CURSOR_MAX_TRADES = 45;
    static final int GE_HISTORY_CURSOR_MIN_MATCH = 6;
    static final int GE_HISTORY_CURSOR_ROLLOVER_MIN_LEN = 30;
    static final String WIPE_BARRIER_KEY_PREFIX = "wipeBarrierV1_";
    static final String GE_HISTORY_CURSOR_KEY_PREFIX = "geHistoryCursorV1_";
    static final String[] OFFER_STATUS_MARKERS = new String[] {
        "offer status",
        "you have bought",
        "you have sold",
        "bought a total",
        "sold a total"
    };
    static final int MAX_LOCAL_TRADES = 5000;
    static final long LOCAL_EVENT_BUCKET_MS = 600L;
    static final long DUPLICATE_TRADE_WINDOW_MS = 2_000L;
    static final String[] OFFER_SETUP_BLOCKERS = new String[] {
        "choose an item",
        "click the icon",
        "select an offer slot",
        "set up or view an offer"
    };
    static final String[] ITEM_NAME_EXCLUDES = new String[] {
        "offer status",
        "buy offer",
        "sell offer",
        "quantity",
        "price per item",
        "coins",
        "history",
        "you have"
    };

    private GeLifecyclePluginConstants() {
    }
}
