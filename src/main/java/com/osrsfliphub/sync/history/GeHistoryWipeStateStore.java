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
import java.util.List;

final class GeHistoryWipeStateStore {
    interface Hooks {
        String readConfiguration(String configGroup, String key);
        void writeConfiguration(String configGroup, String key, String value);
    }

    private final Hooks hooks;
    private final String configGroup;
    private final String wipeBarrierKeyPrefix;
    private final String cursorKeyPrefix;
    private final int maxCursorTrades;

    GeHistoryWipeStateStore(Hooks hooks,
                            String configGroup,
                            String wipeBarrierKeyPrefix,
                            String cursorKeyPrefix,
                            int maxCursorTrades) {
        this.hooks = hooks;
        this.configGroup = configGroup;
        this.wipeBarrierKeyPrefix = wipeBarrierKeyPrefix;
        this.cursorKeyPrefix = cursorKeyPrefix;
        this.maxCursorTrades = Math.max(1, maxCursorTrades);
    }

    boolean isWipeBarrierArmed(long accountKey) {
        if (hooks == null || accountKey <= 0) {
            return false;
        }
        String raw = hooks.readConfiguration(configGroup, wipeBarrierKeyPrefix + accountKey);
        return raw != null && !raw.trim().isEmpty();
    }

    void setWipeBarrierArmed(long accountKey, boolean armed) {
        if (hooks == null || accountKey <= 0) {
            return;
        }
        hooks.writeConfiguration(configGroup, wipeBarrierKeyPrefix + accountKey, armed ? "1" : "");
    }

    List<String> loadCursor(long accountKey) {
        List<String> cursor = new ArrayList<>();
        if (hooks == null || accountKey <= 0) {
            return cursor;
        }
        String raw = hooks.readConfiguration(configGroup, cursorKeyPrefix + accountKey);
        if (raw == null || raw.trim().isEmpty()) {
            return cursor;
        }
        String[] parts = raw.split(",");
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                cursor.add(trimmed);
            }
        }
        return cursor;
    }

    void persistCursor(long accountKey, List<String> cursor) {
        if (hooks == null || accountKey <= 0) {
            return;
        }
        if (cursor == null || cursor.isEmpty()) {
            hooks.writeConfiguration(configGroup, cursorKeyPrefix + accountKey, "");
            return;
        }
        int limit = Math.min(maxCursorTrades, cursor.size());
        String raw = String.join(",", cursor.subList(0, limit));
        hooks.writeConfiguration(configGroup, cursorKeyPrefix + accountKey, raw);
    }
}
