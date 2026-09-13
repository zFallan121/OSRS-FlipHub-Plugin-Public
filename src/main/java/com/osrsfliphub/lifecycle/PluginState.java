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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Singleton holder for the plugin's shared mutable state (caches, in-memory
 * collections, and the small config-backed stores). Injected into the services
 * that need it, replacing the long list of map/store constructor arguments that
 * the old hand-rolled DI threaded through every factory.
 */
@Singleton
final class PluginState {
    private final Map<Integer, OfferSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<Integer, Stamp> offerUpdateStamps = new ConcurrentHashMap<>();
    private final Set<Integer> bookmarkedItems = ConcurrentHashMap.newKeySet();
    private final Set<Integer> hiddenItems = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> itemNameLookupCache = new ConcurrentHashMap<>();
    private final Map<Integer, String> itemNameCache = new ConcurrentHashMap<>();
    private final Set<Long> loadedProfiles = ConcurrentHashMap.newKeySet();
    private final Map<Long, Long> loadedProfileFileMs = new ConcurrentHashMap<>();
    private final Map<Long, Long> selfWrittenProfileFileMs = new ConcurrentHashMap<>();
    private final Map<Long, String> profileDisplayNames = new ConcurrentHashMap<>();
    private final Map<Long, StatsCache> statsCacheByAccount = new ConcurrentHashMap<>();
    private final Object localStatsLock = new Object();
    private final Map<Long, List<Delta>> localTradeDeltasByAccount = new HashMap<>();
    private final Map<Long, Long> localSessionStartByAccount = new HashMap<>();
    private final TradesLoad.State localTradesLoadState = new TradesLoad.State();

    private final ProfileSelectionState profileSelection =
        new ProfileSelectionState(Const.ACCOUNTWIDE_KEY_STRING);
    private final BookmarkConfigStore bookmarkConfigStore =
        new BookmarkConfigStore(Const.ACCOUNTWIDE_KEY);
    private final HiddenItemConfigStore hiddenItemConfigStore = new HiddenItemConfigStore();
    private final OfferUpdateStampConfigStore offerUpdateStampConfigStore = new OfferUpdateStampConfigStore();
    private final OfferUpdateStampLegacyMatcher offerUpdateStampLegacyMatcher = new OfferUpdateStampLegacyMatcher();
    private final UploadDiagnosticsState uploadState = new UploadDiagnosticsState();

    @Inject
    PluginState() {
    }

    Map<Integer, OfferSnapshot> getSnapshots() {
        return snapshots;
    }

    Map<Integer, Stamp> getOfferUpdateStamps() {
        return offerUpdateStamps;
    }

    Set<Integer> getBookmarkedItems() {
        return bookmarkedItems;
    }

    Set<Integer> getHiddenItems() {
        return hiddenItems;
    }

    Map<String, Integer> getItemNameLookupCache() {
        return itemNameLookupCache;
    }

    Map<Integer, String> getItemNameCache() {
        return itemNameCache;
    }

    Set<Long> getLoadedProfiles() {
        return loadedProfiles;
    }

    /**
     * The modification stamp of the last write this process made to each profile file.
     *
     * <p>The file watcher cannot otherwise tell its own writes from somebody else's. Every
     * write woke the watcher, which reloaded the file a second later and replaced the live
     * in-memory list with what was on disk - discarding any fill recorded in between.</p>
     */
    Map<Long, Long> getSelfWrittenProfileFileMs() {
        return selfWrittenProfileFileMs;
    }

    Map<Long, Long> getLoadedProfileFileMs() {
        return loadedProfileFileMs;
    }

    Map<Long, String> getProfileDisplayNames() {
        return profileDisplayNames;
    }

    Map<Long, StatsCache> getStatsCacheByAccount() {
        return statsCacheByAccount;
    }

    Object getLocalStatsLock() {
        return localStatsLock;
    }

    Map<Long, List<Delta>> getLocalTradeDeltasByAccount() {
        return localTradeDeltasByAccount;
    }

    Map<Long, Long> getLocalSessionStartByAccount() {
        return localSessionStartByAccount;
    }

    TradesLoad.State getLocalTradesLoadState() {
        return localTradesLoadState;
    }

    ProfileSelectionState getProfileSelection() {
        return profileSelection;
    }

    BookmarkConfigStore getBookmarkConfigStore() {
        return bookmarkConfigStore;
    }

    HiddenItemConfigStore getHiddenItemConfigStore() {
        return hiddenItemConfigStore;
    }

    OfferUpdateStampConfigStore getOfferUpdateStampConfigStore() {
        return offerUpdateStampConfigStore;
    }

    OfferUpdateStampLegacyMatcher getOfferUpdateStampLegacyMatcher() {
        return offerUpdateStampLegacyMatcher;
    }

    UploadDiagnosticsState getUploadState() {
        return uploadState;
    }
}
