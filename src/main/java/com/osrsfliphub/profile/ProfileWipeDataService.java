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

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
final class ProfileWipeDataService {

    private final long accountwideKey = Const.ACCOUNTWIDE_KEY;
    private final Object localStatsLock;
    private final Map<Long, List<Delta>> localTradeDeltasByAccount;
    private final Map<Long, Long> localSessionStartByAccount;
    private final Map<Long, StatsCache> statsCacheByAccount;
    private final Set<Long> loadedProfiles;
    private final Map<Long, Long> loadedProfileFileMs;
    private final Gson gson;

    @Inject
    ProfileWipeDataService(PluginState pluginState, Gson gson) {
        this.localStatsLock = pluginState.getLocalStatsLock();
        this.localTradeDeltasByAccount = pluginState.getLocalTradeDeltasByAccount();
        this.localSessionStartByAccount = pluginState.getLocalSessionStartByAccount();
        this.statsCacheByAccount = pluginState.getStatsCacheByAccount();
        this.loadedProfiles = pluginState.getLoadedProfiles();
        this.loadedProfileFileMs = pluginState.getLoadedProfileFileMs();
        this.gson = gson;
    }

    private boolean writeProfileData(long accountKey, List<Delta> deltas) {
        ProfileStorage storage = Bridge.get(ProfileStorage.class);
        return storage != null && storage.writeProfileData(accountKey, deltas);
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
        List<Delta> emptyDeltas = new ArrayList<>();
        boolean written = writeProfileData(accountKey, emptyDeltas);
        boolean legacyWritten = writeLegacyProfileDataIfPresent(accountKey, displayName, emptyDeltas);
        return written && legacyWritten;
    }

    /** @return whether the accountwide file was actually cleared; see clearProfileDataForWipe. */
    boolean clearAccountwideDataForWipe() {
        resetInMemoryProfileData(accountwideKey);
        List<Delta> emptyDeltas = new ArrayList<>();
        boolean written = writeProfileData(accountwideKey, emptyDeltas);
        boolean legacyWritten = writeLegacyProfileDataIfPresent(accountwideKey, "Accountwide", emptyDeltas);
        return written && legacyWritten;
    }

    /** @return whether the legacy copy was cleared, or true when there is no legacy copy. */
    boolean writeLegacyProfileDataIfPresent(long accountKey, String displayName, List<Delta> deltas) {
        if (gson == null) {
            return true;
        }
        ProfileStorage storage = Bridge.get(ProfileStorage.class);
        Path legacyDir = storage != null ? storage.getLegacyProfilesDir() : null;
        if (legacyDir == null || !Files.exists(legacyDir)) {
            return true;
        }
        Path file = accountKey == accountwideKey
            ? legacyDir.resolve("accountwide.json")
            : legacyDir.resolve("hash_" + accountKey + ".json");
        if (!Files.exists(file)) {
            return true;
        }
        ProfileData data = new ProfileData();
        data.accountHash = accountKey;
        data.displayName = displayName;
        data.deltas = deltas;
        data.updatedMs = System.currentTimeMillis();
        try {
            Files.writeString(file, gson.toJson(data), StandardCharsets.UTF_8);
            return true;
        } catch (IOException ex) {
            // The legacy copy is not the source of truth, but it still holds the history the
            // player asked to destroy, so a failure here is reported rather than swallowed.
            log.warn("FlipHub: could not clear the legacy profile copy at {}", file, ex);
            return false;
        }
    }

    private void resetInMemoryProfileData(long accountKey) {
        synchronized (localStatsLock) {
            localTradeDeltasByAccount.put(accountKey, new ArrayList<>());
            localSessionStartByAccount.remove(accountKey);
        }
        statsCacheByAccount.remove(accountKey);
        loadedProfiles.remove(accountKey);
        loadedProfileFileMs.remove(accountKey);
        RecipeFlipStore recorded = Bridge.get(RecipeFlipStore.class);
        if (recorded != null) {
            recorded.clear(accountKey);
        }
    }
}
