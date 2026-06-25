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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LegacyLocalTradesStoreTest {
    @Test
    public void mergeProfilesReadsLegacyNameMappings() throws Exception {
        withTemporaryHome(
            "fliphub.localTrades.name_notari=shared\n"
                + "fliphub.localTrades.hash_123=shared\n",
            () -> {
                Path runeliteDir = Path.of(System.getProperty("user.home"), ".runelite");
                LegacyLocalTradesStore store = new LegacyLocalTradesStore(null, new Gson(), "fliphub", runeliteDir);
                Map<Long, String> profiles = new HashMap<>();
                Map<Long, String> legacyNameKeysByHash = new HashMap<>();

                store.mergeProfiles(profiles, legacyNameKeysByHash);

                assertEquals("notari", profiles.get(123L));
                assertEquals("name_notari", legacyNameKeysByHash.get(123L));
            }
        );
    }

    @Test
    public void resolveDisplayNameForHashReadsLegacyCache() throws Exception {
        withTemporaryHome(
            "fliphub.localTrades.name_sips=shared\n"
                + "fliphub.localTrades.456=shared\n",
            () -> {
                Path runeliteDir = Path.of(System.getProperty("user.home"), ".runelite");
                LegacyLocalTradesStore store = new LegacyLocalTradesStore(null, new Gson(), "fliphub", runeliteDir);
                assertEquals("sips", store.resolveDisplayNameForHash(456L));
                assertNull(store.resolveDisplayNameForHash(999L));
            }
        );
    }

    @Test
    public void displayNameFromLegacyKeyParsesPrefix() {
        assertEquals("ari", LegacyLocalTradesStore.displayNameFromLegacyKey("name_ari"));
        assertNull(LegacyLocalTradesStore.displayNameFromLegacyKey("hash_123"));
    }

    @Test
    public void mergeProfilesIgnoresInvalidHashKeys() throws Exception {
        withTemporaryHome(
            "fliphub.localTrades.name_notari=shared\n"
                + "fliphub.localTrades.hash_invalid=shared\n"
                + "fliphub.localTrades.hash_123=shared\n",
            () -> {
                Path runeliteDir = Path.of(System.getProperty("user.home"), ".runelite");
                LegacyLocalTradesStore store = new LegacyLocalTradesStore(null, new Gson(), "fliphub", runeliteDir);
                Map<Long, String> profiles = new HashMap<>();
                Map<Long, String> legacyNameKeysByHash = new HashMap<>();

                store.mergeProfiles(profiles, legacyNameKeysByHash);

                assertEquals("notari", profiles.get(123L));
                assertNull(profiles.get(-1L));
                assertEquals(1, profiles.size());
            }
        );
    }

    @Test
    public void mergeProfilesHandlesMalformedSettingsFile() throws Exception {
        withTemporaryHome(
            "fliphub.localTrades.hash_123=\\uZZZZ\n",
            () -> {
                Path runeliteDir = Path.of(System.getProperty("user.home"), ".runelite");
                LegacyLocalTradesStore store = new LegacyLocalTradesStore(null, new Gson(), "fliphub", runeliteDir);
                Map<Long, String> profiles = new HashMap<>();
                Map<Long, String> legacyNameKeysByHash = new HashMap<>();

                store.mergeProfiles(profiles, legacyNameKeysByHash);

                assertTrue(profiles.isEmpty());
                assertTrue(legacyNameKeysByHash.isEmpty());
            }
        );
    }

    private static void withTemporaryHome(String settingsContent, ThrowingRunnable testBody) throws Exception {
        String previousHome = System.getProperty("user.home");
        Path tempHome = Files.createTempDirectory("legacy-local-trades-home");
        try {
            Path runeliteDir = tempHome.resolve(".runelite");
            Files.createDirectories(runeliteDir);
            Files.writeString(
                runeliteDir.resolve("settings.properties"),
                settingsContent,
                StandardCharsets.UTF_8
            );
            System.setProperty("user.home", tempHome.toString());
            testBody.run();
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
