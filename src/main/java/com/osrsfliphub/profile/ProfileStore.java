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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.RuneLite;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

@Singleton
@Slf4j
@RequiredArgsConstructor
final class ProfileStore {
    private final Gson gson;
    private final String profileDirName;
    private final String legacyProfileDirName;
    private final Path runeliteDir;

    private final AtomicBoolean legacyProfilesMigrated = new AtomicBoolean(false);
    /**
     * One lock per profile file.
     *
     * <p>Several callers write these files and they do not all come through the same queue:
     * the coalescing writer on the IO pool, the flush at shutdown, the display-name stamp on
     * the game thread, and a wipe. Two of them writing one file at once used to interleave
     * through a shared scratch file and could publish half a document, which reads back as an
     * account with no history at all.
     */
    private final Map<Path, Object> fileLocks = new ConcurrentHashMap<>();

    @Inject
    ProfileStore(Gson gson) {
        this(gson, Const.PROFILE_DIR_NAME,
            Const.LEGACY_PROFILE_DIR_NAME, RuneLite.RUNELITE_DIR.toPath());
    }

    ProfileStore(Gson gson, String profileDirName, String legacyProfileDirName) {
        this(gson, profileDirName, legacyProfileDirName, RuneLite.RUNELITE_DIR.toPath());
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

    long writeProfileData(long accountHash, long accountwideKey, String displayName, List<Delta> deltas) {
        return writeProfileData(accountHash, accountwideKey, displayName, deltas, null);
    }

    long writeProfileData(long accountHash,
                          long accountwideKey,
                          String displayName,
                          List<Delta> deltas,
                          List<RecipeFlip> recipeFlips) {
        Path file = getProfileFile(accountHash, accountwideKey);
        if (file == null || gson == null) {
            return 0L;
        }
        ProfileData data = new ProfileData();
        data.accountHash = accountHash;
        data.displayName = displayName;
        data.deltas = deltas;
        data.recipeFlips = recipeFlips != null && !recipeFlips.isEmpty() ? recipeFlips : null;
        data.updatedMs = System.currentTimeMillis();
        Object fileLock = fileLocks.computeIfAbsent(file.toAbsolutePath(), key -> new Object());
        synchronized (fileLock) {
            return writeDocument(file, data);
        }
    }

    private long writeDocument(Path file, ProfileData data) {
        Path temp = null;
        try {
            String json = gson.toJson(data);
            // Written beside the target and moved into place, so a crash or a full disk
            // mid-write leaves the previous file intact rather than a truncated one. A
            // truncated file reads back as no history at all, which the loader would then
            // persist over the top of, losing the account permanently.
            // A name of this write's own. A shared one lets a second writer truncate the
            // scratch file that the first is about to move into place.
            temp = file.resolveSibling(file.getFileName() + "." + UUID.randomUUID() + ".tmp");
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            temp = null;
            // The document is on disk. A mtime of zero here only means the stamp could not
            // be read back, so report success with whatever stamp we got.
            return Math.max(0L, getProfileFileModifiedMs(file));
        } catch (IOException | RuntimeException ex) {
            // Negative means the trades are still only in memory. The caller has to keep the
            // account marked unsaved, or this silently becomes the moment the history was lost.
            // RuntimeException is caught too: serialising a large history can throw out of Gson,
            // and a throw here used to escape past the caller's bookkeeping, leaving the account
            // marked saved when nothing had been written.
            log.warn("Could not save trade history to {}", file, ex);
            return -1L;
        } finally {
            if (temp != null) {
                // The move never happened, so this is a half-written document nobody wants.
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // Nothing reads it; a stray scratch file is the lesser problem.
                }
            }
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
