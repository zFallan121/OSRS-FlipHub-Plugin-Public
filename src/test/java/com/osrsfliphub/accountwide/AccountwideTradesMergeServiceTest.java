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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class AccountwideTradesMergeServiceTest {
    @Test
    public void buildAccountwideFromDiskMergesSourcesAndDedupes() throws Exception {
        Path tempDir = Files.createTempDirectory("fliphub-merge-test");
        try {
            Path profilesDir = Files.createDirectory(tempDir.resolve("profiles"));
            Path legacyDir = Files.createDirectory(tempDir.resolve("legacy"));
            Path profileFile = Files.createFile(profilesDir.resolve("hash_111.json"));
            Path legacyFile = Files.createFile(legacyDir.resolve("hash_222.json"));

            LocalTradeDelta fromProfile = delta(100L, 1);
            LocalTradeDelta fromLegacyDir = delta(200L, 2);
            LocalTradeDelta fromLegacyConfig = delta(300L, 3);
            LocalTradeDelta duplicateProfile = delta(100L, 1);

            TestHooks hooks = new TestHooks();
            hooks.profilesDir = profilesDir;
            hooks.legacyProfilesDir = legacyDir;
            hooks.dataByFile.put(profileFile.toAbsolutePath(), profileData(fromProfile));
            hooks.dataByFile.put(legacyFile.toAbsolutePath(), profileData(fromLegacyDir));
            hooks.legacyCache.put(
                "k1",
                new Gson().toJson(listOf(duplicateProfile, fromLegacyConfig))
            );

            AccountwideTradesMergeService service = new AccountwideTradesMergeService(new Gson(), 10, hooks);
            List<LocalTradeDelta> merged = service.buildAccountwideFromDisk();

            assertNotNull(merged);
            assertEquals(3, merged.size());
            assertEquals(100L, merged.get(0).tsClientMs);
            assertEquals(200L, merged.get(1).tsClientMs);
            assertEquals(300L, merged.get(2).tsClientMs);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void buildAccountwideFromDiskTrimsToMaxLocalTrades() throws Exception {
        Path tempDir = Files.createTempDirectory("fliphub-merge-trim-test");
        try {
            Path profilesDir = Files.createDirectory(tempDir.resolve("profiles"));
            Path profileFile = Files.createFile(profilesDir.resolve("hash_111.json"));

            TestHooks hooks = new TestHooks();
            hooks.profilesDir = profilesDir;
            hooks.dataByFile.put(
                profileFile.toAbsolutePath(),
                profileData(delta(100L, 1), delta(200L, 2), delta(300L, 3))
            );

            AccountwideTradesMergeService service = new AccountwideTradesMergeService(new Gson(), 2, hooks);
            List<LocalTradeDelta> merged = service.buildAccountwideFromDisk();

            assertNotNull(merged);
            assertEquals(2, merged.size());
            assertEquals(200L, merged.get(0).tsClientMs);
            assertEquals(300L, merged.get(1).tsClientMs);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void buildAccountwideFromDiskSkipsInvalidJsonAndInvalidHashFiles() throws Exception {
        Path tempDir = Files.createTempDirectory("fliphub-merge-invalid-test");
        try {
            Path profilesDir = Files.createDirectory(tempDir.resolve("profiles"));
            Path validProfileFile = Files.createFile(profilesDir.resolve("hash_111.json"));
            Files.createFile(profilesDir.resolve("hash_invalid.json"));

            TestHooks hooks = new TestHooks();
            hooks.profilesDir = profilesDir;
            hooks.dataByFile.put(validProfileFile.toAbsolutePath(), profileData(delta(100L, 1)));
            hooks.legacyCache.put("k1", "{not valid json");

            AccountwideTradesMergeService service = new AccountwideTradesMergeService(new Gson(), 10, hooks);
            List<LocalTradeDelta> merged = service.buildAccountwideFromDisk();

            assertNotNull(merged);
            assertEquals(1, merged.size());
            assertEquals(100L, merged.get(0).tsClientMs);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    @Test
    public void buildAccountwideFromDiskReturnsNullWhenNoData() {
        TestHooks hooks = new TestHooks();
        AccountwideTradesMergeService service = new AccountwideTradesMergeService(new Gson(), 10, hooks);

        assertNull(service.buildAccountwideFromDisk());
    }

    private static ProfileData profileData(LocalTradeDelta... deltas) {
        ProfileData data = new ProfileData();
        data.deltas = listOf(deltas);
        return data;
    }

    private static List<LocalTradeDelta> listOf(LocalTradeDelta... deltas) {
        List<LocalTradeDelta> list = new ArrayList<>();
        if (deltas != null) {
            for (LocalTradeDelta delta : deltas) {
                list.add(delta);
            }
        }
        return list;
    }

    private static LocalTradeDelta delta(long ts, int itemId) {
        LocalTradeDelta delta = new LocalTradeDelta();
        delta.tsClientMs = ts;
        delta.slot = 0;
        delta.itemId = itemId;
        delta.isBuy = true;
        delta.deltaQty = 1;
        delta.deltaGp = 100L;
        delta.eventType = "OFFER_CHANGED";
        delta.price = 100;
        delta.baselineSynthetic = false;
        return delta;
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

    private static final class TestHooks implements AccountwideTradesMergeService.Hooks {
        private Path profilesDir;
        private Path legacyProfilesDir;
        private final Map<Path, ProfileData> dataByFile = new HashMap<>();
        private final Map<String, String> legacyCache = new HashMap<>();

        @Override
        public Path getProfilesDir() {
            return profilesDir;
        }

        @Override
        public Path getLegacyProfilesDir() {
            return legacyProfilesDir;
        }

        @Override
        public ProfileData readProfileData(Path file) {
            if (file == null) {
                return null;
            }
            return dataByFile.get(file.toAbsolutePath());
        }

        @Override
        public Map<String, String> getLegacyLocalTradesCache() {
            return legacyCache;
        }
    }
}
