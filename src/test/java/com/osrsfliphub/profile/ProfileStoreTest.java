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
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ProfileStoreTest {
    @Test
    public void writeProfileDataRoundTripsAndLeavesNoTemporaryFileBehind() throws Exception {
        Path baseDir = Files.createTempDirectory("profile-store-write");
        try {
            ProfileStore store = new ProfileStore(new Gson(), "fliphub", "fliphub-dev", baseDir);
            List<Delta> deltas = new ArrayList<>();
            deltas.add(new Delta(1000L, 1, 4151, true, 5, 500L, "OFFER_UPDATED", 100, false));

            long writtenMs = store.writeProfileData(123L, 0L, "Zezima", deltas);

            assertTrue(writtenMs > 0);
            ProfileData read = store.readProfileData(123L, 0L);
            assertNotNull(read);
            assertEquals(1, read.deltas.size());
            assertEquals(4151, read.deltas.get(0).itemId);
            try (java.util.stream.Stream<Path> files =
                     Files.list(baseDir.resolve("fliphub").resolve("profiles"))) {
                assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
            }
        } finally {
            deleteRecursively(baseDir);
        }
    }

    /**
     * The loader tells "this account has no trades" from "this file could not be read" using
     * exactly these two signals, and only overwrites the file in the first case.
     */
    @Test
    public void aCorruptProfileFileIsDistinguishableFromAMissingOne() throws Exception {
        Path baseDir = Files.createTempDirectory("profile-store-corrupt");
        try {
            ProfileStore store = new ProfileStore(new Gson(), "fliphub", "fliphub-dev", baseDir);
            Path file = store.getProfileFile(123L, 0L);
            assertEquals(0L, store.getProfileFileModifiedMs(file));

            Files.writeString(file, "{\"deltas\":[{\"itemId\"", StandardCharsets.UTF_8);

            assertNull(store.readProfileData(file));
            assertTrue(store.getProfileFileModifiedMs(file) > 0);
        } finally {
            deleteRecursively(baseDir);
        }
    }

    @Test
    public void parseAccountKeyFromProfileFileParsesValidHashFile() {
        ProfileStore store = new ProfileStore(new Gson(), "fliphub-dev", "fliphub");

        long parsed = store.parseAccountKeyFromProfileFile(Path.of("hash_123.json"));

        assertEquals(123L, parsed);
    }

    @Test
    public void parseAccountKeyFromProfileFileRejectsInvalidFiles() {
        ProfileStore store = new ProfileStore(new Gson(), "fliphub-dev", "fliphub");

        assertEquals(-1L, store.parseAccountKeyFromProfileFile(null));
        assertEquals(-1L, store.parseAccountKeyFromProfileFile(Path.of("accountwide.json")));
        assertEquals(-1L, store.parseAccountKeyFromProfileFile(Path.of("hash_invalid.json")));
        assertEquals(-1L, store.parseAccountKeyFromProfileFile(Path.of("hash_0.json")));
        assertEquals(-1L, store.parseAccountKeyFromProfileFile(Path.of("profile_123.json")));
    }

    @Test
    public void readProfileDataReturnsNullForInvalidJson() throws Exception {
        Path baseDir = Files.createTempDirectory("profile-store-invalid-json");
        try {
            Path file = baseDir.resolve("hash_123.json");
            Files.writeString(file, "{not valid json", StandardCharsets.UTF_8);
            ProfileStore store = new ProfileStore(new Gson(), "fliphub-dev", "fliphub");

            ProfileData data = store.readProfileData(file);

            assertNull(data);
        } finally {
            deleteRecursively(baseDir);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
        }
    }
}
