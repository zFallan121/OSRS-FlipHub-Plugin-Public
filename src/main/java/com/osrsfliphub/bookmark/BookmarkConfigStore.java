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

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

final class BookmarkConfigStore {
    private static final String BOOKMARKS_KEY = "bookmarks";
    private static final String BOOKMARKS_KEY_PREFIX = "bookmarks_";

    private final long accountwideKey;

    BookmarkConfigStore(long accountwideKey) {
        this.accountwideKey = accountwideKey;
    }

    long normalizeProfileKey(long profileKey) {
        return profileKey > 0 ? profileKey : accountwideKey;
    }

    boolean isAccountwide(long profileKey) {
        return normalizeProfileKey(profileKey) == accountwideKey;
    }

    boolean isBookmarksConfigKey(String configKey) {
        if (configKey == null || configKey.trim().isEmpty()) {
            return false;
        }
        String trimmed = configKey.trim();
        return BOOKMARKS_KEY.equals(trimmed) || trimmed.startsWith(BOOKMARKS_KEY_PREFIX);
    }

    Long parseProfileKey(String configKey) {
        if (configKey == null || configKey.trim().isEmpty()) {
            return null;
        }
        String trimmed = configKey.trim();
        if (BOOKMARKS_KEY.equals(trimmed)) {
            return accountwideKey;
        }
        if (!trimmed.startsWith(BOOKMARKS_KEY_PREFIX)) {
            return null;
        }
        String suffix = trimmed.substring(BOOKMARKS_KEY_PREFIX.length());
        if (suffix.isEmpty()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(suffix);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    String buildConfigKey(long profileKey) {
        long normalizedProfileKey = normalizeProfileKey(profileKey);
        return normalizedProfileKey == accountwideKey
            ? BOOKMARKS_KEY
            : BOOKMARKS_KEY_PREFIX + normalizedProfileKey;
    }

    Set<Integer> parseItemIds(String raw) {
        Set<Integer> parsed = new HashSet<>();
        if (raw == null || raw.trim().isEmpty()) {
            return parsed;
        }
        String[] parts = raw.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                int itemId = Integer.parseInt(trimmed);
                if (itemId > 0) {
                    parsed.add(itemId);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return parsed;
    }

    String serializeItemIds(Set<Integer> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return "";
        }
        return itemIds.stream()
            .sorted()
            .map(String::valueOf)
            .collect(Collectors.joining(","));
    }
}
