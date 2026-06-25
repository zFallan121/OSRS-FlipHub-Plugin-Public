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
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;

final class LegacyLocalTradesStore {
    private static final Type LOCAL_TRADES_LIST_TYPE = new TypeToken<List<LocalTradeDelta>>() {}.getType();

    private final ConfigManager configManager;
    private final Gson gson;
    private final String legacyConfigGroup;
    private final Path runeliteDir;
    private final Object cacheLock = new Object();
    private volatile Map<String, String> entries;

    LegacyLocalTradesStore(ConfigManager configManager, Gson gson, String legacyConfigGroup) {
        this(configManager, gson, legacyConfigGroup, RuneLite.RUNELITE_DIR.toPath());
    }

    LegacyLocalTradesStore(ConfigManager configManager, Gson gson, String legacyConfigGroup, Path runeliteDir) {
        this.configManager = configManager;
        this.gson = gson;
        this.legacyConfigGroup = legacyConfigGroup;
        this.runeliteDir = runeliteDir;
    }

    Map<String, String> getEntries() {
        Map<String, String> cache = entries;
        if (cache != null) {
            return cache;
        }
        synchronized (cacheLock) {
            cache = entries;
            if (cache != null) {
                return cache;
            }
            Map<String, String> next = new HashMap<>();
            loadLegacyLocalTradesFromProfiles(next);
            entries = next;
            return next;
        }
    }

    List<LocalTradeDelta> readLocalTrades(String key) {
        if (key == null || key.trim().isEmpty() || configManager == null || gson == null) {
            return null;
        }
        String trimmed = key.trim();
        Map<String, String> legacyEntries = getEntries();
        String raw = legacyEntries != null ? legacyEntries.get(trimmed) : null;
        if (raw == null || raw.trim().isEmpty()) {
            raw = configManager.getConfiguration(legacyConfigGroup, "localTrades." + trimmed);
        }
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return gson.fromJson(raw, LOCAL_TRADES_LIST_TYPE);
        } catch (JsonParseException ignored) {
            return null;
        }
    }

    String resolveDisplayNameForHash(long hash) {
        if (hash <= 0) {
            return null;
        }
        Map<String, String> cache = getEntries();
        if (cache == null || cache.isEmpty()) {
            return null;
        }
        String raw = cache.get("hash_" + hash);
        if (raw == null || raw.trim().isEmpty()) {
            raw = cache.get(String.valueOf(hash));
        }
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> entry : cache.entrySet()) {
            if (entry == null) {
                continue;
            }
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || value == null || !value.equals(raw)) {
                continue;
            }
            String normalized = key.trim().toLowerCase(Locale.US);
            if (normalized.startsWith("name_")) {
                String display = normalized.substring(5).trim();
                return display.isEmpty() ? null : display;
            }
        }
        return null;
    }

    static String displayNameFromLegacyKey(String legacyKey) {
        if (legacyKey == null) {
            return null;
        }
        String trimmed = legacyKey.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("name_")) {
            String display = trimmed.substring(5).trim();
            return display.isEmpty() ? null : display;
        }
        return null;
    }

    void mergeProfiles(Map<Long, String> profiles, Map<Long, String> legacyNameKeysByHash) {
        if (profiles == null) {
            return;
        }
        Map<String, String> cache = getEntries();
        if (cache == null || cache.isEmpty()) {
            return;
        }
        Map<String, String> nameValueToDisplay = new HashMap<>();
        Map<String, String> nameValueToKey = new HashMap<>();
        for (String key : cache.keySet()) {
            if (key == null || key.trim().isEmpty()) {
                continue;
            }
            String normalized = key.trim().toLowerCase(Locale.US);
            if (normalized.startsWith("name_")) {
                String display = normalized.substring(5).trim();
                if (display.isEmpty()) {
                    continue;
                }
                String raw = cache.get(key);
                if (raw != null && !raw.trim().isEmpty()) {
                    nameValueToDisplay.putIfAbsent(raw, display);
                    nameValueToKey.putIfAbsent(raw, normalized);
                }
            } else if (normalized.startsWith("hash_")) {
                mergeHashProfile(profiles, legacyNameKeysByHash, nameValueToDisplay, nameValueToKey, cache, key, normalized.substring(5));
            } else {
                mergeHashProfile(profiles, legacyNameKeysByHash, nameValueToDisplay, nameValueToKey, cache, key, normalized);
            }
        }
    }

    private void mergeHashProfile(Map<Long, String> profiles,
                                  Map<Long, String> legacyNameKeysByHash,
                                  Map<String, String> nameValueToDisplay,
                                  Map<String, String> nameValueToKey,
                                  Map<String, String> cache,
                                  String key,
                                  String hashPart) {
        try {
            long hash = Long.parseLong(hashPart);
            if (hash <= 0) {
                return;
            }
            String raw = cache.get(key);
            String display = raw != null ? nameValueToDisplay.get(raw) : null;
            if (display != null && !display.trim().isEmpty()) {
                String existing = profiles.get(hash);
                if (existing == null || existing.startsWith("Profile ")) {
                    profiles.put(hash, display);
                }
                String nameKey = nameValueToKey.get(raw);
                if (nameKey != null && legacyNameKeysByHash != null) {
                    legacyNameKeysByHash.putIfAbsent(hash, nameKey);
                }
            } else if (!profiles.containsKey(hash)) {
                profiles.put(hash, "Profile " + hash);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void loadLegacyLocalTradesFromProfiles(Map<String, String> target) {
        if (target == null) {
            return;
        }
        if (runeliteDir == null) {
            return;
        }
        Path profilesDir = runeliteDir.resolve("profiles2");
        if (Files.exists(profilesDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(profilesDir, "*.properties")) {
                for (Path path : stream) {
                    loadLegacyLocalTradesFromFile(target, path);
                }
            } catch (IOException ignored) {
            }
        }
        Path settingsFile = runeliteDir.resolve("settings.properties");
        loadLegacyLocalTradesFromFile(target, settingsFile);
    }

    private void loadLegacyLocalTradesFromFile(Map<String, String> target, Path path) {
        if (target == null || path == null || !Files.exists(path)) {
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException | IllegalArgumentException ignored) {
            return;
        }
        String prefix = legacyConfigGroup + ".localTrades.";
        for (Map.Entry<Object, Object> entry : props.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String key = String.valueOf(entry.getKey());
            if (!key.startsWith(prefix)) {
                continue;
            }
            String suffix = key.substring(prefix.length());
            String value = String.valueOf(entry.getValue());
            if (!suffix.isEmpty() && value != null && !value.trim().isEmpty()) {
                target.putIfAbsent(suffix, value);
            }
        }
    }
}
