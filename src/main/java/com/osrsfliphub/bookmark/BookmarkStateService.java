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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

@Singleton
final class BookmarkStateService {
    private final BookmarkConfigStore configStore;
    private final ConfigManager configManager;
    private final PluginConfig config;
    private final Map<Long, Set<Integer>> bookmarksByProfile = new ConcurrentHashMap<>();

    @Inject
    BookmarkStateService(ConfigManager configManager, PluginConfig config, PluginState state) {
        this.configStore = state.getBookmarkConfigStore();
        this.configManager = configManager;
        this.config = config;
    }

    private String readBookmarksForProfile(long normalizedProfileKey) {
        if (configStore == null) {
            return "";
        }
        if (configManager == null) {
            return configStore.isAccountwide(normalizedProfileKey) && config != null
                ? config.bookmarks()
                : "";
        }
        return configManager.getConfiguration(
            FliphubConfigGroups.CONFIG_GROUP,
            configStore.buildConfigKey(normalizedProfileKey));
    }

    private void persistBookmarksForProfile(long normalizedProfileKey, String serializedBookmarkIds) {
        if (configManager == null || configStore == null) {
            return;
        }
        configManager.setConfiguration(
            FliphubConfigGroups.CONFIG_GROUP,
            configStore.buildConfigKey(normalizedProfileKey),
            serializedBookmarkIds);
    }

    private Long resolveActiveProfileKey() {
        LocalAccountSessionService localAccountSessionService =
            PluginInjectorBridge.get(LocalAccountSessionService.class);
        if (localAccountSessionService != null) {
            long localAccountKey = localAccountSessionService.resolveLocalAccountKey();
            if (localAccountKey > 0) {
                return localAccountKey;
            }
        }
        LocalTradeSessionFacadeService localTradeSessionFacadeService =
            PluginInjectorBridge.get(LocalTradeSessionFacadeService.class);
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
        return configStore != null && configStore.isBookmarksConfigKey(configKey);
    }

    void reloadFromConfigKey(String configKey) {
        if (configStore == null) {
            return;
        }
        Long profileKey = configStore.parseProfileKey(configKey);
        if (profileKey == null) {
            return;
        }
        bookmarksByProfile.remove(profileKey);
    }

    void loadSelectedBookmarks(long selectedProfileKey, Set<Integer> destination) {
        if (destination == null) {
            return;
        }
        destination.clear();
        destination.addAll(getOrLoadBookmarksForProfile(selectedProfileKey));
    }

    BookmarkSyncService.ToggleResult toggleForSelected(long selectedProfileKey, int itemId) {
        if (configStore == null) {
            return BookmarkSyncService.toggleBookmark(BookmarkSyncService.ACCOUNTWIDE_KEY, itemId, null, null);
        }
        long normalizedSelectedProfileKey = configStore.normalizeProfileKey(selectedProfileKey);
        if (normalizedSelectedProfileKey == BookmarkSyncService.ACCOUNTWIDE_KEY) {
            Set<Integer> accountwideBookmarks = getOrLoadBookmarksForProfile(BookmarkSyncService.ACCOUNTWIDE_KEY);
            Long activeProfileKey = resolveActiveProfileKey();
            Set<Integer> activeProfileBookmarks = activeProfileKey != null
                ? getOrLoadBookmarksForProfile(activeProfileKey)
                : null;
            BookmarkSyncService.ToggleResult result = BookmarkSyncService.toggleAccountwideBookmark(
                itemId,
                accountwideBookmarks,
                activeProfileBookmarks
            );
            if (result.accountwideChanged) {
                persistBookmarksForProfile(BookmarkSyncService.ACCOUNTWIDE_KEY, accountwideBookmarks);
            }
            if (activeProfileKey != null && result.mirroredProfileChanged) {
                persistBookmarksForProfile(activeProfileKey, activeProfileBookmarks);
            }
            return result;
        }

        Set<Integer> selectedBookmarks = getOrLoadBookmarksForProfile(normalizedSelectedProfileKey);
        Set<Integer> accountwideBookmarks = getOrLoadBookmarksForProfile(BookmarkSyncService.ACCOUNTWIDE_KEY);
        BookmarkSyncService.ToggleResult result = BookmarkSyncService.toggleBookmark(
            normalizedSelectedProfileKey,
            itemId,
            selectedBookmarks,
            accountwideBookmarks
        );
        if (result.selectedChanged) {
            persistBookmarksForProfile(normalizedSelectedProfileKey, selectedBookmarks);
        }
        if (result.accountwideChanged) {
            persistBookmarksForProfile(BookmarkSyncService.ACCOUNTWIDE_KEY, accountwideBookmarks);
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
        loaded.addAll(configStore.parseItemIds(readBookmarksConfig(normalizedProfileKey)));
        Set<Integer> existing = bookmarksByProfile.putIfAbsent(normalizedProfileKey, loaded);
        return existing != null ? existing : loaded;
    }

    private String readBookmarksConfig(long profileKey) {
        String raw = readBookmarksForProfile(configStore.normalizeProfileKey(profileKey));
        return raw != null ? raw : "";
    }

    private void persistBookmarksForProfile(long profileKey, Set<Integer> bookmarkIds) {
        long normalizedProfileKey = configStore.normalizeProfileKey(profileKey);
        persistBookmarksForProfile(normalizedProfileKey, configStore.serializeItemIds(bookmarkIds));
    }
}
