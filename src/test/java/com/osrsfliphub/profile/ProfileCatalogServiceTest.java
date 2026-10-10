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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.util.Filepath;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ProfileCatalogServiceTest {
    @Test
    public void loadProfilesMergesTheFilesAndTheNamesAlreadyKnown() throws Exception {
        withTemporaryFolder(folder -> {
            Gson gson = new Gson();
            ProfileStore profileStore = Folders.store(gson, folder);
            ProfileCatalog service = new ProfileCatalog(profileStore);

            writeProfile(gson, profileStore.getProfileFile(111L, Const.ACCOUNTWIDE_KEY), 111L, "Main Profile");
            writeProfile(gson, profileStore.getProfileFile(222L, Const.ACCOUNTWIDE_KEY), 222L, null);

            Map<Long, String> displayNames = new HashMap<>();
            displayNames.put(555L, "Existing");

            Map<Long, String> profiles = service.loadProfiles(displayNames);

            assertEquals("Main Profile", profiles.get(111L));
            assertEquals("Profile 222", profiles.get(222L));
            assertEquals("Existing", profiles.get(555L));
            assertEquals(3, profiles.size());
        });
    }

    @Test
    public void persistedPlaceholderNamesNeverOverwriteRealDisplayNames() throws Exception {
        withTemporaryFolder(folder -> {
            Gson gson = new Gson();
            ProfileStore profileStore = Folders.store(gson, folder);
            ProfileCatalog service = new ProfileCatalog(profileStore);

            // Older builds baked the placeholder into displayName on disk.
            writeProfile(gson, profileStore.getProfileFile(444L, Const.ACCOUNTWIDE_KEY), 444L, "Profile 444");

            Map<Long, String> displayNames = new HashMap<>();
            displayNames.put(444L, "Sips Potion");

            Map<Long, String> profiles = service.loadProfiles(displayNames);

            assertEquals("Sips Potion", profiles.get(444L));
            assertEquals("Sips Potion", displayNames.get(444L));
        });
    }

    /** A player with no folder yet has no characters to list, and listing them is not an error. */
    @Test
    public void aPlayerWithNoFolderYetHasNothingToList() throws Exception {
        withTemporaryFolder(parent -> {
            ProfileCatalog service = new ProfileCatalog(Folders.store(parent.resolve("osrs-fliphub")));

            assertEquals(new HashMap<Long, String>(), service.loadProfiles(new HashMap<>()));
            assertEquals(new HashMap<Long, String>(), service.listed(new HashMap<>()));
        });
    }

    private static void writeProfile(Gson gson, Filepath file, long hash, String displayName) throws IOException {
        ProfileData data = new ProfileData();
        data.accountHash = hash;
        data.displayName = displayName;
        data.updatedMs = System.currentTimeMillis();
        data.deltas = java.util.Collections.emptyList();
        Folders.write(file, gson.toJson(data));
    }

    /** The player's plugin folder, as RuneLite hands it over. */
    private static void withTemporaryFolder(ThrowingConsumer runnable) throws Exception {
        Path folder = Files.createTempDirectory("profile-catalog-test");
        try {
            runnable.run(folder);
        } finally {
            deleteRecursively(folder);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        }
    }

    @FunctionalInterface
    private interface ThrowingConsumer {
        void run(Path folder) throws Exception;
    }
}
