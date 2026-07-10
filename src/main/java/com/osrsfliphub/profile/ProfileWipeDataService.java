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
import net.runelite.client.config.ConfigManager;

@Singleton
final class ProfileWipeDataService {
    private final long accountwideKey = GeLifecyclePluginConstants.ACCOUNTWIDE_KEY;
    private final Object localStatsLock;
    private final Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount;
    private final Map<Long, Long> localSessionStartByAccount;
    private final Map<Long, LocalStatsCache> statsCacheByAccount;
    private final Set<Long> loadedProfiles;
    private final Map<Long, Long> loadedProfileFileMs;
    private final Map<Long, String> legacyNameKeysByHash;
    private final Gson gson;
    private final ConfigManager configManager;

    @Inject
    ProfileWipeDataService(PluginState pluginState, Gson gson, ConfigManager configManager) {
        this.localStatsLock = pluginState.getLocalStatsLock();
        this.localTradeDeltasByAccount = pluginState.getLocalTradeDeltasByAccount();
        this.localSessionStartByAccount = pluginState.getLocalSessionStartByAccount();
        this.statsCacheByAccount = pluginState.getStatsCacheByAccount();
        this.loadedProfiles = pluginState.getLoadedProfiles();
        this.loadedProfileFileMs = pluginState.getLoadedProfileFileMs();
        this.legacyNameKeysByHash = pluginState.getLegacyNameKeysByHash();
        this.gson = gson;
        this.configManager = configManager;
    }

    private void writeProfileData(long accountKey, List<LocalTradeDelta> deltas) {
        PluginInjectorBridge.get(ProfileStorageFacadeService.class).writeProfileData(accountKey, deltas);
    }

    private void clearLegacyLocalTradesConfigEntry(String suffix) {
        if (configManager == null || suffix == null || suffix.trim().isEmpty()) {
            return;
        }
        configManager.setConfiguration(FliphubConfigGroups.CONFIG_GROUP, "localTrades." + suffix.trim(), "");
    }

    void clearProfileDataForWipe(long accountKey, String displayName, boolean clearLegacyTradeCache) {
        resetInMemoryProfileData(accountKey);
        List<LocalTradeDelta> emptyDeltas = new ArrayList<>();
        writeProfileData(accountKey, emptyDeltas);
        writeLegacyProfileDataIfPresent(accountKey, displayName, emptyDeltas);
        if (clearLegacyTradeCache) {
            clearLegacyLocalTradesForProfile(accountKey);
        }
    }

    void clearAccountwideDataForWipe() {
        resetInMemoryProfileData(accountwideKey);
        List<LocalTradeDelta> emptyDeltas = new ArrayList<>();
        writeProfileData(accountwideKey, emptyDeltas);
        writeLegacyProfileDataIfPresent(accountwideKey, "Accountwide", emptyDeltas);
    }

    void writeLegacyProfileDataIfPresent(long accountKey, String displayName, List<LocalTradeDelta> deltas) {
        if (gson == null) {
            return;
        }
        Path legacyDir = PluginInjectorBridge.get(ProfileStorageFacadeService.class).getLegacyProfilesDir();
        if (legacyDir == null || !Files.exists(legacyDir)) {
            return;
        }
        Path file = accountKey == accountwideKey
            ? legacyDir.resolve("accountwide.json")
            : legacyDir.resolve("hash_" + accountKey + ".json");
        if (!Files.exists(file)) {
            return;
        }
        ProfileData data = new ProfileData();
        data.accountHash = accountKey;
        data.displayName = displayName;
        data.deltas = deltas;
        data.updatedMs = System.currentTimeMillis();
        try {
            Files.writeString(file, gson.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    void clearLegacyLocalTradesForProfile(long accountKey) {
        if (configManager == null || accountKey <= 0) {
            return;
        }
        List<String> suffixes = new ArrayList<>();
        suffixes.add("hash_" + accountKey);
        suffixes.add(String.valueOf(accountKey));
        String legacyNameKey = legacyNameKeysByHash.get(accountKey);
        if (legacyNameKey != null && !legacyNameKey.trim().isEmpty()) {
            suffixes.add(legacyNameKey.trim());
        }
        for (String suffix : suffixes) {
            if (suffix == null || suffix.trim().isEmpty()) {
                continue;
            }
            clearLegacyLocalTradesConfigEntry(suffix.trim());
        }
    }

    void clearAllLegacyLocalTrades() {
        if (configManager == null) {
            return;
        }
        Map<String, String> entries = PluginInjectorBridge.get(LegacyLocalTradesStore.class).getEntries();
        if (entries == null || entries.isEmpty()) {
            return;
        }
        for (String suffix : entries.keySet()) {
            if (suffix == null || suffix.trim().isEmpty()) {
                continue;
            }
            clearLegacyLocalTradesConfigEntry(suffix.trim());
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
    }
}
