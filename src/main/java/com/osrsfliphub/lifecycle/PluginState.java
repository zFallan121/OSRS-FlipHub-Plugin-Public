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
import lombok.Getter;

/**
 * Singleton holder for the plugin's shared mutable state (caches, in-memory
 * collections, and the small config-backed stores). Injected into the services
 * that need it, replacing the long list of map/store constructor arguments that
 * the old hand-rolled DI threaded through every factory.
 */
@Singleton
final class PluginState {
    @Getter
    private final Map<Integer, OfferSnapshot> snapshots = new ConcurrentHashMap<>();
    @Getter
    private final Map<Integer, Stamp> offerUpdateStamps = new ConcurrentHashMap<>();
    @Getter
    private final Set<Integer> bookmarkedItems = ConcurrentHashMap.newKeySet();
    @Getter
    private final Set<Integer> hiddenItems = ConcurrentHashMap.newKeySet();
    @Getter
    private final Map<String, Integer> itemNameLookupCache = new ConcurrentHashMap<>();
    @Getter
    private final Map<Integer, String> itemNameCache = new ConcurrentHashMap<>();
    @Getter
    private final Set<Long> loadedProfiles = ConcurrentHashMap.newKeySet();
    @Getter
    private final Map<Long, Long> loadedProfileFileMs = new ConcurrentHashMap<>();
    @Getter
    private final Map<Long, Long> selfWrittenProfileFileMs = new ConcurrentHashMap<>();
    /** Accounts whose file would not parse. It is not written over: see ProfileStorage.writeProfileData. */
    @Getter
    private final Set<Long> unreadableProfiles = ConcurrentHashMap.newKeySet();
    @Getter
    private final Map<Long, String> profileDisplayNames = new ConcurrentHashMap<>();
    @Getter
    private final Map<Long, StatsCache> statsCacheByAccount = new ConcurrentHashMap<>();
    @Getter
    private final Object localStatsLock = new Object();
    private final Map<Long, List<Delta>> localTradeDeltasByAccount = new HashMap<>();
    @Getter
    private final Map<Long, Long> localSessionStartByAccount = new HashMap<>();
    @Getter
    private final TradesLoad.State localTradesLoadState = new TradesLoad.State();

    @Getter
    private final ProfileSelectionState profileSelection =
        new ProfileSelectionState(Const.ACCOUNTWIDE_KEY_STRING);
    @Getter
    private final BookmarkConfigStore bookmarkConfigStore =
        new BookmarkConfigStore(Const.ACCOUNTWIDE_KEY);
    @Getter
    private final OfferUpdateStampConfigStore offerUpdateStampConfigStore = new OfferUpdateStampConfigStore();
    @Getter
    private final OfferUpdateStampLegacyMatcher offerUpdateStampLegacyMatcher = new OfferUpdateStampLegacyMatcher();
    @Getter
    private final UploadDiagnosticsState uploadState = new UploadDiagnosticsState();

    @Inject
    PluginState() {
    }

    Map<Long, List<Delta>> getLocalTradeDeltasByAccount() {
        return localTradeDeltasByAccount;
    }
}
