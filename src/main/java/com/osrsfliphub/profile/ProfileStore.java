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
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.inject.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

@Singleton
@Slf4j
@RequiredArgsConstructor
final class ProfileStore {
    /**
     * A folder of its own inside the plugin's for the trade files, for the development client only
     * (it sets this before starting). It runs newer code than the Plugin Hub build, and the two
     * sharing one folder let the older one rewrite files holding what it could not read: a
     * recorded move was lost that way on 22 Sep 2026.
     */
    static final String DATA_DIR_PROPERTY = "fliphub.dataDir";
    /** Every member of the document {@link ProfileData} has a field for. */
    static final Set<String> KNOWN = Set.of("accountHash", "displayName", "deltas", "updatedMs", "recipeFlips");

    private final Gson gson;
    /**
     * The folder RuneLite keeps for the plugin, or null while RuneLite cannot hand it over. Asked
     * for at each use and never remembered: null is "no folder yet", not "no history".
     */
    private final Supplier<Filepath> folder;

    /**
     * One lock per profile file.
     *
     * <p>Several callers write these files and they do not all come through the same queue:
     * the coalescing writer on the IO pool, the flush at shutdown, the display-name stamp on
     * the game thread, and a wipe. Two of them writing one file at once used to interleave
     * through a shared scratch file and could publish half a document, which reads back as an
     * account with no history at all.
     */
    private final Map<Filepath, Object> fileLocks = new ConcurrentHashMap<>();
    /**
     * What each file held that this build has no field for, from its last read, written back as
     * it was read. See {@link #keep}.
     */
    private final Map<Filepath, Kept> kept = new ConcurrentHashMap<>();

    private static final class Kept {
        /** Every record as read, by {@link #recordKey}. */
        final Map<String, JsonElement> records = new HashMap<>();
        final JsonObject unknown = new JsonObject();
    }

    /** The store Guice builds: its folder is the one the running plugin was handed. */
    @Inject
    ProfileStore(Gson gson) {
        this(gson, () -> {
            GeLifecyclePlugin plugin = Access.pluginOrNull();
            return plugin != null ? plugin.folder : null;
        });
    }

    Filepath getProfilesDir() {
        Filepath dir = folder.get();
        if (dir == null) {
            return null;
        }
        String name = System.getProperty(DATA_DIR_PROPERTY);
        if (name != null) {
            try {
                dir = dir.joinSegment(name);
            } catch (IllegalArgumentException ignored) {
                // Not a name RuneLite lets a folder have: saved where every player's files are.
            }
        }
        dir = dir.joinSegment("profiles");
        try {
            dir.createDirectories();
        } catch (IOException ignored) {
            // Left to the write, which reports it: a folder that cannot be made is a save that fails.
        }
        return dir;
    }

    Filepath getProfileFile(long accountHash, long accountwideKey) {
        Filepath dir = getProfilesDir();
        if (dir == null) {
            return null;
        }
        if (accountHash == accountwideKey) {
            return dir.joinSegment("accountwide.json");
        }
        return dir.joinSegment("hash_" + accountHash + ".json");
    }

    long getProfileFileModifiedMs(Filepath file) {
        if (file == null || !file.exists()) {
            return 0L;
        }
        try {
            return file.getLastModifiedTime().toMillis();
        } catch (IOException ignored) {
            return 0L;
        }
    }

    long parseAccountKeyFromProfileFile(Filepath file) {
        if (file == null) {
            return -1L;
        }
        String name = file.getFileName();
        if ("accountwide.json".equalsIgnoreCase(name)) {
            return -1L;
        }
        Long parsed = ProfileHashFileParser.parsePositiveHashFromProfileFileName(name);
        return parsed != null ? parsed : -1L;
    }

    ProfileData readProfileData(long accountHash, long accountwideKey) {
        Filepath file = getProfileFile(accountHash, accountwideKey);
        return readProfileData(file);
    }

    ProfileData readProfileData(Filepath file) {
        if (file == null || !file.exists()) {
            return null;
        }
        try {
            String json;
            try (InputStream in = file.openInputStream()) {
                // Decoded strictly, as it always was: bytes that are not UTF-8 are a file that
                // could not be read, and such a file is never written over.
                json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(in.readAllBytes())).toString();
            }
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
    private void keep(Filepath file, String json) {
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
            kept.put(file, read);
        } catch (IOException | RuntimeException ex) {
            // The file read fine through the model, so it stays readable; only what this build
            // cannot represent is written back as this build sees it, as every build used to.
            kept.remove(file);
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
        Filepath file = getProfileFile(accountHash, accountwideKey);
        if (file == null) {
            // No folder to save in. Answered as saved, with nothing written: "not saved" would keep
            // the list marked unsaved, and a list built while there was no folder to read from
            // would be flushed over the real file the moment the folder arrived.
            return 0L;
        }
        ProfileData data = new ProfileData();
        data.accountHash = accountHash;
        data.displayName = displayName;
        data.deltas = deltas;
        data.recipeFlips = recipeFlips != null && !recipeFlips.isEmpty() ? recipeFlips : null;
        data.updatedMs = System.currentTimeMillis();
        Object fileLock = fileLocks.computeIfAbsent(file, key -> new Object());
        synchronized (fileLock) {
            return writeDocument(file, data);
        }
    }

    /**
     * The document, with what the last read of this file held that this build has no field for:
     * each record exactly as it was read, and the document's unknown members. A record made since
     * is written by this build; one removed since is not brought back.
     */
    private String withKept(Filepath file, ProfileData data) {
        Kept read = kept.get(file);
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

    private long writeDocument(Filepath file, ProfileData data) {
        Filepath temp = null;
        try {
            String json = withKept(file, data);
            // Written beside the target and moved into place, so a crash or a full disk
            // mid-write leaves the previous file intact rather than a truncated one. A
            // truncated file reads back as no history at all, which the loader would then
            // persist over the top of, losing the account permanently.
            // A name of this write's own. A shared one lets a second writer truncate the
            // scratch file that the first is about to move into place.
            temp = file.getParent().joinSegment(file.getFileName() + "." + UUID.randomUUID() + ".tmp");
            temp.write(json);
            try {
                temp.moveTo(file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                // Only where the one step cannot be done at all. The plain replace is a delete and
                // then a rename: tried on a file that is merely busy, it would trade "not saved,
                // previous file whole" for a moment with no file.
                temp.moveTo(file, StandardCopyOption.REPLACE_EXISTING);
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
                    temp.deleteIfExists();
                } catch (IOException ignored) {
                    // Nothing reads it; a stray scratch file is the lesser problem.
                }
            }
        }
    }
}
