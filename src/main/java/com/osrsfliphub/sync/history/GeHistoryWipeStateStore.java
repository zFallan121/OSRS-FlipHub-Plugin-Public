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
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

@Singleton
final class GeHistoryWipeStateStore {
    private final ConfigManager configManager;
    private final String configGroup = FliphubConfigGroups.CONFIG_GROUP;
    private final String wipeBarrierKeyPrefix = GeLifecyclePluginConstants.WIPE_BARRIER_KEY_PREFIX;
    private final String cursorKeyPrefix = GeLifecyclePluginConstants.GE_HISTORY_CURSOR_KEY_PREFIX;
    private final int maxCursorTrades = Math.max(1, GeLifecyclePluginConstants.GE_HISTORY_CURSOR_MAX_TRADES);

    @Inject
    GeHistoryWipeStateStore(ConfigManager configManager) {
        this.configManager = configManager;
    }

    private String readConfiguration(String group, String key) {
        return configManager != null ? configManager.getConfiguration(group, key) : null;
    }

    private void writeConfiguration(String group, String key, String value) {
        if (configManager != null) {
            configManager.setConfiguration(group, key, value);
        }
    }

    boolean isWipeBarrierArmed(long accountKey) {
        if (accountKey <= 0) {
            return false;
        }
        String raw = readConfiguration(configGroup, wipeBarrierKeyPrefix + accountKey);
        return raw != null && !raw.trim().isEmpty();
    }

    void setWipeBarrierArmed(long accountKey, boolean armed) {
        if (accountKey <= 0) {
            return;
        }
        writeConfiguration(configGroup, wipeBarrierKeyPrefix + accountKey, armed ? "1" : "");
    }

    List<String> loadCursor(long accountKey) {
        List<String> cursor = new ArrayList<>();
        if (accountKey <= 0) {
            return cursor;
        }
        String raw = readConfiguration(configGroup, cursorKeyPrefix + accountKey);
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
        if (accountKey <= 0) {
            return;
        }
        if (cursor == null || cursor.isEmpty()) {
            writeConfiguration(configGroup, cursorKeyPrefix + accountKey, "");
            return;
        }
        int limit = Math.min(maxCursorTrades, cursor.size());
        String raw = String.join(",", cursor.subList(0, limit));
        writeConfiguration(configGroup, cursorKeyPrefix + accountKey, raw);
    }
}
