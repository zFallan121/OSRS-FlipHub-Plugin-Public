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
import javax.inject.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

@Singleton
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class WipeStateStore {
    private static final String WIPE_BARRIER_KEY_PREFIX = "wipeBarrierV1_";
    private static final String GE_HISTORY_CURSOR_KEY_PREFIX = "geHistoryCursorV1_";
    /** Under this key: where the last sync left off, as {@link AutoSyncTradeMatcher.LastSync} stores it. */
    private static final String GE_HISTORY_SYNCED_SINCE_KEY_PREFIX = "geHistorySyncedSinceV1_";

    private final ConfigManager configManager;
    private final String configGroup = FliphubConfigGroups.CONFIG_GROUP;
    private final String wipeBarrierKeyPrefix = WIPE_BARRIER_KEY_PREFIX;
    private final String cursorKeyPrefix = GE_HISTORY_CURSOR_KEY_PREFIX;
    private final int maxCursorTrades = Math.max(1, Const.GE_HISTORY_CURSOR_MAX_TRADES);

    boolean isWipeBarrierArmed(long accountKey) {
        if (accountKey <= 0) {
            return false;
        }
        String raw = configManager.getConfiguration(configGroup, wipeBarrierKeyPrefix + accountKey);
        return Str.hasText(raw);
    }

    void setWipeBarrierArmed(long accountKey, boolean armed) {
        if (accountKey <= 0) {
            return;
        }
        configManager.setConfiguration(configGroup, wipeBarrierKeyPrefix + accountKey, armed ? "1" : "");
        if (armed) {
            // Armed only by a wipe. When, so that what other accounts moved here before it goes too.
            configManager.setConfiguration(configGroup, "profileWipedMs_" + accountKey, System.currentTimeMillis());
        }
    }

    /**
     * When this account's history was last wiped, or null. A move recorded to it before then is
     * not counted: it lives in the other account's file, and the wipe could not take it from there.
     */
    Long wipedMs(long accountKey) {
        return configManager.getConfiguration(configGroup, "profileWipedMs_" + accountKey, Long.class);
    }

    /**
     * The account's stored cursor. One written by another format version comes back
     * empty and marked stale: the sync then re-baselines instead of comparing against
     * signatures that could never match.
     */
    GeHistoryCursorService.StoredCursor loadCursor(long accountKey) {
        if (accountKey <= 0) {
            return GeHistoryCursorService.StoredCursor.NONE;
        }
        String raw = configManager.getConfiguration(configGroup, cursorKeyPrefix + accountKey);
        GeHistoryCursorService.StoredCursor cursor = GeHistoryCursorService.decode(raw);
        if (cursor.staleFormat) {
            log.info("GE history cursor for account {} was written by another format version; treating as no cursor",
                accountKey);
        }
        return cursor;
    }

    /** Where the last sync left off; NONE when nothing is stored, which compares everything as before. */
    AutoSyncTradeMatcher.LastSync loadLastSync(long accountKey, long nowMs) {
        return AutoSyncTradeMatcher.LastSync.decode(
            configManager.getConfiguration(configGroup, GE_HISTORY_SYNCED_SINCE_KEY_PREFIX + accountKey), nowMs);
    }

    void persistLastSync(long accountKey, AutoSyncTradeMatcher.LastSync lastSync) {
        configManager.setConfiguration(
            configGroup, GE_HISTORY_SYNCED_SINCE_KEY_PREFIX + accountKey, lastSync.encode());
    }

    void persistCursor(long accountKey, List<String> cursor) {
        if (accountKey <= 0) {
            return;
        }
        if (cursor == null || cursor.isEmpty()) {
            configManager.setConfiguration(configGroup, cursorKeyPrefix + accountKey, "");
            return;
        }
        int limit = Math.min(maxCursorTrades, cursor.size());
        configManager.setConfiguration(configGroup, cursorKeyPrefix + accountKey,
            GeHistoryCursorService.encode(cursor.subList(0, limit)));
    }
}
