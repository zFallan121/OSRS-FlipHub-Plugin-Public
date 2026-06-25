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
import java.util.Map;
import java.util.Set;

final class GeLifecycleSharedState {
    private final Object localStatsLock;
    private final Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount;
    private final Map<Long, Long> localSessionStartByAccount;
    private final Map<Long, LocalStatsCache> statsCacheByAccount;
    private final Set<Long> loadedProfiles;
    private final Map<Long, Long> loadedProfileFileMs;
    private final Map<Long, String> legacyNameKeysByHash;
    private final Map<Long, String> profileDisplayNames;
    private final Set<Integer> bookmarkedItems;
    private final Set<Integer> hiddenItems;
    private final Map<Integer, OfferSnapshot> snapshots;
    private final Map<Integer, OfferUpdateStamp> offerUpdateStamps;

    GeLifecycleSharedState(
        Object localStatsLock,
        Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount,
        Map<Long, Long> localSessionStartByAccount,
        Map<Long, LocalStatsCache> statsCacheByAccount,
        Set<Long> loadedProfiles,
        Map<Long, Long> loadedProfileFileMs,
        Map<Long, String> legacyNameKeysByHash,
        Map<Long, String> profileDisplayNames,
        Set<Integer> bookmarkedItems,
        Set<Integer> hiddenItems,
        Map<Integer, OfferSnapshot> snapshots,
        Map<Integer, OfferUpdateStamp> offerUpdateStamps
    ) {
        this.localStatsLock = localStatsLock;
        this.localTradeDeltasByAccount = localTradeDeltasByAccount;
        this.localSessionStartByAccount = localSessionStartByAccount;
        this.statsCacheByAccount = statsCacheByAccount;
        this.loadedProfiles = loadedProfiles;
        this.loadedProfileFileMs = loadedProfileFileMs;
        this.legacyNameKeysByHash = legacyNameKeysByHash;
        this.profileDisplayNames = profileDisplayNames;
        this.bookmarkedItems = bookmarkedItems;
        this.hiddenItems = hiddenItems;
        this.snapshots = snapshots;
        this.offerUpdateStamps = offerUpdateStamps;
    }

    Object getLocalStatsLock() {
        return localStatsLock;
    }

    Map<Long, List<LocalTradeDelta>> getLocalTradeDeltasByAccount() {
        return localTradeDeltasByAccount;
    }

    Map<Long, Long> getLocalSessionStartByAccount() {
        return localSessionStartByAccount;
    }

    Map<Long, LocalStatsCache> getStatsCacheByAccount() {
        return statsCacheByAccount;
    }

    Set<Long> getLoadedProfiles() {
        return loadedProfiles;
    }

    Map<Long, Long> getLoadedProfileFileMs() {
        return loadedProfileFileMs;
    }

    Map<Long, String> getLegacyNameKeysByHash() {
        return legacyNameKeysByHash;
    }

    Map<Long, String> getProfileDisplayNames() {
        return profileDisplayNames;
    }

    Set<Integer> getBookmarkedItems() {
        return bookmarkedItems;
    }

    Set<Integer> getHiddenItems() {
        return hiddenItems;
    }

    Map<Integer, OfferSnapshot> getSnapshots() {
        return snapshots;
    }

    Map<Integer, OfferUpdateStamp> getOfferUpdateStamps() {
        return offerUpdateStamps;
    }
}
