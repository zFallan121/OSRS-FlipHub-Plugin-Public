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
import javax.inject.*;

@Singleton
final class ProfileWipeDataService {

    private final ProfileStorage storage;
    private final RecipeFlipStore recipeFlipStore;
    private final long accountwideKey = Const.ACCOUNTWIDE_KEY;
    private final Object localStatsLock;
    private final Map<Long, List<Delta>> localTradeDeltasByAccount;
    private final Map<Long, Long> localSessionStartByAccount;
    private final Map<Long, StatsCache> statsCacheByAccount;
    private final Set<Long> loadedProfiles;
    private final Map<Long, Long> loadedProfileFileMs;
    private final Set<Long> unreadableProfiles;

    @Inject
    ProfileWipeDataService(
        PluginState pluginState,
        ProfileStorage storage,
        RecipeFlipStore recipeFlipStore
    ) {
        this.storage = storage;
        this.recipeFlipStore = recipeFlipStore;
        this.localStatsLock = pluginState.getLocalStatsLock();
        this.localTradeDeltasByAccount = pluginState.getLocalTradeDeltasByAccount();
        this.localSessionStartByAccount = pluginState.getLocalSessionStartByAccount();
        this.statsCacheByAccount = pluginState.getStatsCacheByAccount();
        this.loadedProfiles = pluginState.getLoadedProfiles();
        this.loadedProfileFileMs = pluginState.getLoadedProfileFileMs();
        this.unreadableProfiles = pluginState.getUnreadableProfiles();
    }

    /**
     * Clears one profile, on disk as well as in memory.
     *
     * @return whether the data is actually gone. A wipe that only emptied memory must never be
     *         reported as done: the player is told their history is deleted, the panel shows
     *         nothing, and the file comes back at the next restart.
     */
    boolean clearProfileDataForWipe(long accountKey, String displayName) {
        resetInMemoryProfileData(accountKey);
        return storage.writeProfileData(accountKey, new ArrayList<>());
    }

    /** @return whether the accountwide file was actually cleared; see clearProfileDataForWipe. */
    boolean clearAccountwideDataForWipe() {
        resetInMemoryProfileData(accountwideKey);
        return storage.writeProfileData(accountwideKey, new ArrayList<>());
    }

    private void resetInMemoryProfileData(long accountKey) {
        synchronized (localStatsLock) {
            localTradeDeltasByAccount.put(accountKey, new ArrayList<>());
            localSessionStartByAccount.remove(accountKey);
        }
        // Every account's, not this one's alone: stock this account recorded as moved to another
        // is in that other account's totals, and goes with the record below.
        statsCacheByAccount.clear();
        loadedProfiles.remove(accountKey);
        loadedProfileFileMs.remove(accountKey);
        recipeFlipStore.clear(accountKey);
        recipeFlipStore.wiped(accountKey, System.currentTimeMillis());
        // Asked for in so many words: a bad file may be written over now, and nothing else may.
        unreadableProfiles.remove(accountKey);
    }
}
