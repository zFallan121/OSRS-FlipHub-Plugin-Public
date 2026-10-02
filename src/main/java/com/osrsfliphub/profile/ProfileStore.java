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

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

@Singleton
@Slf4j
@RequiredArgsConstructor
final class ProfileStore {
    private static final String PROFILE_DIR_NAME = "fliphub";
    private static final String LEGACY_PROFILE_DIR_NAME = "fliphub-dev";
    /**
     * Another folder under .runelite for the trade files, for the development client only (it
     * sets this before starting). It runs newer code than the Plugin Hub build, and the two
     * sharing one folder let the older one rewrite files holding what it could not read: a
     * recorded move was lost that way on 22 Sep 2026.
     */
    static final String DATA_DIR_PROPERTY = "fliphub.dataDir";
    /** Every member of the document {@link ProfileData} has a field for. */
    static final Set<String> KNOWN = Set.of("accountHash", "displayName", "deltas", "updatedMs", "recipeFlips");

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
    /**
     * What each file held that this build has no field for, from its last read, written back as
     * it was read. See {@link #keep}.
     */
    private final Map<Path, Kept> kept = new ConcurrentHashMap<>();

    private static final class Kept {
        /** Every record as read, by {@link #recordKey}. */
        final Map<String, JsonElement> records = new HashMap<>();
        final JsonObject unknown = new JsonObject();
    }

    @Inject
    ProfileStore(Gson gson) {
        this(gson, dataDirName(), LEGACY_PROFILE_DIR_NAME, RuneLite.RUNELITE_DIR.toPath());
    }

    private static String dataDirName() {
        String name = System.getProperty(DATA_DIR_PROPERTY);
        return name != null && name.matches("\\w[\\w.-]*") ? name : PROFILE_DIR_NAME;
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
            // Left to the write, which reports it: a folder that cannot be made is a save that fails.
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
        if (file == null || !Files.exists(file)) {
            return null;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            if (Str.isBlank(json)) {
                return null;
            }
            ProfileData data = gson.fromJson(json, ProfileData.class);
            keep(file, json);
            return data;
        } catch (IOException | JsonParseException ignored) {
            return null;
        }
    }

    /**
     * Holds on to what a newer build wrote that this one cannot represent - a record of a kind it
     * does not know, a field it does not know on a record, a member of the document it does not
     * know - so that {@link #writeDocument} puts it back. Every build used to rebuild the file from
     * its own fields alone, and an older client beside a newer one erased a recorded move that way.
     * Only builds from this one on are protected: an older one still rewrites what it cannot read.
     *
     * <p>A second pass over the text rather than a tree of the whole document: the trades are most
     * of it, and only skipped here.
     */
    private void keep(Path file, String json) {
        Path key = file.toAbsolutePath().normalize();
        try {
            Kept read = new Kept();
            JsonReader in = new JsonReader(new StringReader(json));
            // As the read through the model was, or a file it took would be refused here.
            in.setLenient(true);
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                if ("recipeFlips".equals(name) && in.peek() == JsonToken.BEGIN_ARRAY) {
                    for (JsonElement raw : (JsonArray) gson.fromJson(in, JsonArray.class)) {
                        RecipeFlip flip = raw.isJsonObject() ? gson.fromJson(raw, RecipeFlip.class) : null;
                        if (flip != null) {
                            read.records.put(recordKey(flip), raw);
                        }
                    }
                } else if (KNOWN.contains(name)) {
                    in.skipValue();
                } else {
                    read.unknown.add(name, gson.fromJson(in, JsonElement.class));
                }
            }
            kept.put(key, read);
        } catch (IOException | RuntimeException ex) {
            // The file read fine through the model, so it stays readable; only what this build
            // cannot represent is written back as this build sees it, as every build used to.
            kept.remove(key);
        }
    }

    /** A record is never changed once made, so what it names and when it was made identify it. */
    private static String recordKey(RecipeFlip flip) {
        return flip.recordedMs + "|" + flip.trades() + "|" + flip.voided + "|" + flip.toAccount;
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
        if (file == null) {
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

    /**
     * The document, with what the last read of this file held that this build has no field for:
     * each record exactly as it was read, and the document's unknown members. A record made since
     * is written by this build; one removed since is not brought back.
     */
    private String withKept(Path file, ProfileData data) {
        Kept read = kept.get(file.toAbsolutePath().normalize());
        List<RecipeFlip> flips = data.recipeFlips;
        if (read == null) {
            return gson.toJson(data);
        }
        data.recipeFlips = null;
        String json = gson.toJson(data);
        data.recipeFlips = flips;
        JsonObject tail = new JsonObject();
        if (flips != null) {
            JsonArray array = new JsonArray();
            for (RecipeFlip flip : flips) {
                JsonElement raw = read.records.get(recordKey(flip));
                array.add(raw != null ? raw : gson.toJsonTree(flip));
            }
            tail.add("recipeFlips", array);
        }
        read.unknown.entrySet().forEach(member -> tail.add(member.getKey(), member.getValue()));
        // Written first: a member whose value is null is counted but not written, and splicing
        // nothing on would end the document ",}", which never reads again.
        String kept = gson.toJson(tail);
        if (kept.length() <= 2) {
            return json;
        }
        // The model always writes the account hash, so the document is never empty.
        return json.substring(0, json.lastIndexOf('}')) + "," + kept.substring(1);
    }

    private long writeDocument(Path file, ProfileData data) {
        Path temp = null;
        try {
            String json = withKept(file, data);
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
        } catch (IOException ex) {
            // A history left behind in the old folder looks, to the player, like a history lost.
            log.warn("FlipHub: could not copy the old profile folder into {}", devDir, ex);
        }
    }
}
