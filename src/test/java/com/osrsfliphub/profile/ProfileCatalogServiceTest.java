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
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ProfileCatalogServiceTest {
    @Test
    public void loadProfilesMergesDevLegacyAndConfigSources() throws Exception {
        withTemporaryHome(() -> {
            Gson gson = new Gson();
            Path runeliteDir = Path.of(System.getProperty("user.home"), ".runelite");
            ProfileStore profileStore = new ProfileStore(gson, "fliphub-dev", "fliphub", runeliteDir);
            LegacyLocalTradesStore legacyStore = new LegacyLocalTradesStore(null, gson, "fliphub", runeliteDir);
            ProfileCatalogService service = new ProfileCatalogService(profileStore, legacyStore);

            Path devDir = profileStore.getProfilesDir();
            Path legacyDir = profileStore.getLegacyProfilesDir();
            assertTrue(devDir != null && legacyDir != null);

            writeProfile(gson, devDir.resolve("hash_111.json"), 111L, "Main Profile");
            writeProfile(gson, devDir.resolve("hash_222.json"), 222L, null);
            writeProfile(gson, legacyDir.resolve("hash_333.json"), 333L, "Legacy File");

            Path settingsFile = Path.of(System.getProperty("user.home"), ".runelite", "settings.properties");
            Files.writeString(
                settingsFile,
                "fliphub.localTrades.name_notari=shared\n"
                    + "fliphub.localTrades.hash_444=shared\n",
                StandardCharsets.UTF_8
            );

            Map<Long, String> displayNames = new HashMap<>();
            displayNames.put(555L, "Existing");
            Map<Long, String> legacyNameKeys = new HashMap<>();

            Map<Long, String> profiles = service.loadProfiles(displayNames, legacyNameKeys);

            assertEquals("Main Profile", profiles.get(111L));
            assertEquals("Profile 222", profiles.get(222L));
            assertEquals("Legacy File", profiles.get(333L));
            assertEquals("notari", profiles.get(444L));
            assertEquals("Existing", profiles.get(555L));
            assertEquals("notari", displayNames.get(444L));
            assertEquals("name_notari", legacyNameKeys.get(444L));
        });
    }

    private static void writeProfile(Gson gson, Path file, long hash, String displayName) throws IOException {
        ProfileData data = new ProfileData();
        data.accountHash = hash;
        data.displayName = displayName;
        data.updatedMs = System.currentTimeMillis();
        data.deltas = java.util.Collections.emptyList();
        Files.createDirectories(file.getParent());
        Files.writeString(file, gson.toJson(data), StandardCharsets.UTF_8);
    }

    private static void withTemporaryHome(ThrowingRunnable runnable) throws Exception {
        String previousHome = System.getProperty("user.home");
        Path tempHome = Files.createTempDirectory("profile-catalog-test-home");
        try {
            Files.createDirectories(tempHome.resolve(".runelite"));
            System.setProperty("user.home", tempHome.toString());
            runnable.run();
        } finally {
            if (previousHome != null) {
                System.setProperty("user.home", previousHome);
            } else {
                System.clearProperty("user.home");
            }
            deleteRecursively(tempHome);
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
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
