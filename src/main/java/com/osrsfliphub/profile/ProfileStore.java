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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.RuneLite;

@Singleton
final class ProfileStore {
    private final Gson gson;
    private final String profileDirName;
    private final String legacyProfileDirName;
    private final Path runeliteDir;
    private final AtomicBoolean legacyProfilesMigrated = new AtomicBoolean(false);

    @Inject
    ProfileStore(Gson gson) {
        this(gson, GeLifecyclePluginConstants.PROFILE_DIR_NAME,
            GeLifecyclePluginConstants.LEGACY_PROFILE_DIR_NAME, RuneLite.RUNELITE_DIR.toPath());
    }

    ProfileStore(Gson gson, String profileDirName, String legacyProfileDirName) {
        this(gson, profileDirName, legacyProfileDirName, RuneLite.RUNELITE_DIR.toPath());
    }

    ProfileStore(Gson gson, String profileDirName, String legacyProfileDirName, Path runeliteDir) {
        this.gson = gson;
        this.profileDirName = profileDirName;
        this.legacyProfileDirName = legacyProfileDirName;
        this.runeliteDir = runeliteDir;
    }

    Path getProfilesDir() {
        if (runeliteDir == null) {
            return null;
        }
        Path dir = runeliteDir.resolve(profileDirName).resolve("profiles");
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        migrateLegacyProfilesIfNeeded(dir);
        return dir;
    }

    Path getLegacyProfilesDir() {
        if (runeliteDir == null) {
            return null;
        }
        return runeliteDir.resolve(legacyProfileDirName).resolve("profiles");
    }

    Path getProfileFile(long accountHash, long accountwideKey) {
        Path dir = getProfilesDir();
        if (dir == null) {
            return null;
        }
        if (accountHash == accountwideKey) {
            return dir.resolve("accountwide.json");
        }
        return dir.resolve("hash_" + accountHash + ".json");
    }

    long getProfileFileModifiedMs(Path file) {
        if (file == null || !Files.exists(file)) {
            return 0L;
        }
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException ignored) {
            return 0L;
        }
    }

    long parseAccountKeyFromProfileFile(Path file) {
        if (file == null) {
            return -1L;
        }
        String name = file.getFileName().toString();
        if (name == null) {
            return -1L;
        }
        if ("accountwide.json".equalsIgnoreCase(name)) {
            return -1L;
        }
        Long parsed = ProfileHashFileParser.parsePositiveHashFromProfileFileName(name);
        return parsed != null ? parsed : -1L;
    }

    ProfileData readProfileData(long accountHash, long accountwideKey) {
        Path file = getProfileFile(accountHash, accountwideKey);
        return readProfileData(file);
    }

    ProfileData readProfileData(Path file) {
        if (file == null || !Files.exists(file) || gson == null) {
            return null;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            if (json == null || json.trim().isEmpty()) {
                return null;
            }
            return gson.fromJson(json, ProfileData.class);
        } catch (IOException | JsonParseException ignored) {
            return null;
        }
    }

    long writeProfileData(long accountHash, long accountwideKey, String displayName, List<LocalTradeDelta> deltas) {
        Path file = getProfileFile(accountHash, accountwideKey);
        if (file == null || gson == null) {
            return 0L;
        }
        ProfileData data = new ProfileData();
        data.accountHash = accountHash;
        data.displayName = displayName;
        data.deltas = deltas;
        data.updatedMs = System.currentTimeMillis();
        try {
            String json = gson.toJson(data);
            Files.writeString(file, json, StandardCharsets.UTF_8);
            return getProfileFileModifiedMs(file);
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private void migrateLegacyProfilesIfNeeded(Path devDir) {
        if (runeliteDir == null || devDir == null) {
            return;
        }
        if (!legacyProfilesMigrated.compareAndSet(false, true)) {
            return;
        }
        Path legacyDir = runeliteDir.resolve(legacyProfileDirName).resolve("profiles");
        if (!Files.exists(legacyDir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(legacyDir, "*.json")) {
            boolean devHasFiles = false;
            if (Files.exists(devDir)) {
                try (java.util.stream.Stream<Path> devStream = Files.list(devDir)) {
                    devHasFiles = devStream.findAny().isPresent();
                }
            }
            for (Path legacyFile : stream) {
                if (legacyFile == null) {
                    continue;
                }
                Path target = devDir.resolve(legacyFile.getFileName());
                if (Files.exists(target)) {
                    continue;
                }
                Files.copy(legacyFile, target);
                devHasFiles = true;
            }
            if (!devHasFiles) {
                legacyProfilesMigrated.set(false);
            }
        } catch (IOException ignored) {
        }
    }
}
