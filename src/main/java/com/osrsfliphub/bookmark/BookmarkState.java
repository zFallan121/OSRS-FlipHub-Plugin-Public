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
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.*;
import net.runelite.client.config.ConfigManager;

@Singleton
final class BookmarkState {
    private final BookmarkConfigStore configStore;
    private final ConfigManager configManager;
    private final PluginConfig config;
    private final Map<Long, Set<Integer>> bookmarksByProfile = new ConcurrentHashMap<>();

    @Inject
    BookmarkState(ConfigManager configManager, PluginConfig config, PluginState state) {
        this.configStore = state.getBookmarkConfigStore();
        this.configManager = configManager;
        this.config = config;
    }

    /** What is stored for a profile, whose key is already normalised. */
    private String readBookmarks(long profileKey) {
        if (configManager == null) {
            // Only in tests, which build this without RuneLite's config store.
            return configStore.isAccountwide(profileKey) ? config.bookmarks() : "";
        }
        return configManager.getConfiguration(FliphubConfigGroups.CONFIG_GROUP, configStore.buildConfigKey(profileKey));
    }

    private void persistBookmarks(long profileKey, Set<Integer> bookmarkIds) {
        if (configManager != null) {
            configManager.setConfiguration(
                FliphubConfigGroups.CONFIG_GROUP, configStore.buildConfigKey(profileKey), ItemIdList.join(bookmarkIds));
        }
    }

    private Long resolveActiveProfileKey() {
        AccountSession localAccountSessionService =
            Bridge.get(AccountSession.class);
        if (localAccountSessionService != null) {
            long localAccountKey = localAccountSessionService.resolveLocalAccountKey();
            if (localAccountKey > 0) {
                return localAccountKey;
            }
        }
        TradeSession localTradeSessionFacadeService =
            Bridge.get(TradeSession.class);
        if (localTradeSessionFacadeService != null) {
            long accountHash = localTradeSessionFacadeService.resolveAccountHash();
            if (accountHash > 0) {
                return accountHash;
            }
        }
        return null;
    }

    void clearCache() {
        bookmarksByProfile.clear();
    }

    boolean isBookmarksConfigKey(String configKey) {
        return configStore.isBookmarksConfigKey(configKey);
    }

    void reloadFromConfigKey(String configKey) {
        Long profileKey = configStore.parseProfileKey(configKey);
        if (profileKey != null) {
            bookmarksByProfile.remove(profileKey);
        }
    }

    void loadSelectedBookmarks(long selectedProfileKey, Set<Integer> destination) {
        destination.clear();
        destination.addAll(getOrLoadBookmarksForProfile(selectedProfileKey));
    }

    BookmarkSync.ToggleResult toggleForSelected(long selectedProfileKey, int itemId) {
        long normalizedSelectedProfileKey = configStore.normalizeProfileKey(selectedProfileKey);
        if (normalizedSelectedProfileKey == BookmarkSync.ACCOUNTWIDE_KEY) {
            Set<Integer> accountwideBookmarks = getOrLoadBookmarksForProfile(BookmarkSync.ACCOUNTWIDE_KEY);
            Long activeProfileKey = resolveActiveProfileKey();
            Set<Integer> activeProfileBookmarks = activeProfileKey != null
                ? getOrLoadBookmarksForProfile(activeProfileKey)
                : null;
            BookmarkSync.ToggleResult result = BookmarkSync.toggleAccountwideBookmark(
                itemId,
                accountwideBookmarks,
                activeProfileBookmarks
            );
            if (result.accountwideChanged) {
                persistBookmarks(BookmarkSync.ACCOUNTWIDE_KEY, accountwideBookmarks);
            }
            if (activeProfileKey != null && result.mirroredProfileChanged) {
                persistBookmarks(activeProfileKey, activeProfileBookmarks);
            }
            return result;
        }

        Set<Integer> selectedBookmarks = getOrLoadBookmarksForProfile(normalizedSelectedProfileKey);
        Set<Integer> accountwideBookmarks = getOrLoadBookmarksForProfile(BookmarkSync.ACCOUNTWIDE_KEY);
        BookmarkSync.ToggleResult result = BookmarkSync.toggleBookmark(
            normalizedSelectedProfileKey,
            itemId,
            selectedBookmarks,
            accountwideBookmarks
        );
        if (result.selectedChanged) {
            persistBookmarks(normalizedSelectedProfileKey, selectedBookmarks);
        }
        if (result.accountwideChanged) {
            persistBookmarks(BookmarkSync.ACCOUNTWIDE_KEY, accountwideBookmarks);
        }
        return result;
    }

    private Set<Integer> getOrLoadBookmarksForProfile(long profileKey) {
        long normalizedProfileKey = configStore.normalizeProfileKey(profileKey);
        Set<Integer> cached = bookmarksByProfile.get(normalizedProfileKey);
        if (cached != null) {
            return cached;
        }
        Set<Integer> loaded = ConcurrentHashMap.newKeySet();
        loaded.addAll(ItemIdList.parse(readBookmarks(normalizedProfileKey)));
        Set<Integer> existing = bookmarksByProfile.putIfAbsent(normalizedProfileKey, loaded);
        return existing != null ? existing : loaded;
    }
}
