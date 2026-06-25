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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ProfileTradesLoaderTest {
    @Test
    public void loadUsesAccountwideDiskSnapshotForAccountwideKey() {
        TestHooks hooks = new TestHooks();
        hooks.profileDataByAccount.put(0L, profile("Accountwide", Arrays.asList(delta(1))));
        hooks.accountwideFromDisk = Arrays.asList(delta(2), delta(3));
        hooks.profileFile = Paths.get("C:/tmp/accountwide.json");
        hooks.profileFileMs = 1234L;
        ProfileTradesLoader loader = new ProfileTradesLoader(0L, hooks);

        ProfileTradesLoader.Result result = loader.load(0L, Collections.emptyMap(), 5000, 600L, 2000L);

        assertNotNull(result);
        assertEquals(2, result.deltas.size());
        assertEquals(2, result.deltas.get(0).itemId);
        assertEquals(1234L, result.profileFileModifiedMs);
    }

    @Test
    public void loadFallsBackToLegacyWhenProfileDeltasMissing() {
        TestHooks hooks = new TestHooks();
        hooks.profileDataByAccount.put(123L, profile("Main", Collections.emptyList()));
        hooks.legacyByAccount.put(123L, Arrays.asList(delta(42)));
        ProfileTradesLoader loader = new ProfileTradesLoader(0L, hooks);

        ProfileTradesLoader.Result result = loader.load(123L, Collections.emptyMap(), 5000, 600L, 2000L);

        assertNotNull(result);
        assertEquals(1, result.deltas.size());
        assertEquals(42, result.deltas.get(0).itemId);
    }

    @Test
    public void loadUsesLegacyNameAndLegacyDeltasForPlaceholderProfile() {
        TestHooks hooks = new TestHooks();
        hooks.profileDataByAccount.put(456L, profile("Profile 456", Arrays.asList(delta(10))));
        hooks.legacyByAccount.put(456L, Arrays.asList(delta(77)));
        hooks.legacyDisplayByKey.put("legacy-key", "Sips Potion");
        Map<Long, String> legacyKeys = new HashMap<>();
        legacyKeys.put(456L, "legacy-key");
        ProfileTradesLoader loader = new ProfileTradesLoader(0L, hooks);

        ProfileTradesLoader.Result result = loader.load(456L, legacyKeys, 5000, 600L, 2000L);

        assertNotNull(result);
        assertEquals(1, result.deltas.size());
        assertEquals(77, result.deltas.get(0).itemId);
        assertEquals("Sips Potion", result.resolvedDisplayName);
    }

    private static ProfileData profile(String name, List<LocalTradeDelta> deltas) {
        ProfileData profile = new ProfileData();
        profile.displayName = name;
        profile.deltas = deltas;
        return profile;
    }

    private static LocalTradeDelta delta(int itemId) {
        return new LocalTradeDelta(
            System.currentTimeMillis(),
            1,
            itemId,
            true,
            1,
            100L,
            "OFFER_UPDATED",
            100,
            false
        );
    }

    private static final class TestHooks implements ProfileTradesLoader.Hooks {
        private final Map<Long, ProfileData> profileDataByAccount = new HashMap<>();
        private final Map<Long, List<LocalTradeDelta>> legacyByAccount = new HashMap<>();
        private final Map<String, String> legacyDisplayByKey = new HashMap<>();
        private List<LocalTradeDelta> accountwideFromDisk = Collections.emptyList();
        private Path profileFile;
        private long profileFileMs;

        @Override
        public Path getProfileFile(long accountHash) {
            return profileFile;
        }

        @Override
        public long getProfileFileModifiedMs(Path file) {
            return profileFileMs;
        }

        @Override
        public ProfileData readProfileData(long accountHash) {
            return profileDataByAccount.get(accountHash);
        }

        @Override
        public List<LocalTradeDelta> buildAccountwideFromDisk() {
            return accountwideFromDisk;
        }

        @Override
        public List<LocalTradeDelta> readLegacyLocalTrades(long accountHash) {
            return legacyByAccount.get(accountHash);
        }

        @Override
        public boolean isPlaceholderDisplayName(String displayName) {
            if (displayName == null) {
                return true;
            }
            String trimmed = displayName.trim();
            return trimmed.isEmpty() || trimmed.startsWith("Profile ");
        }

        @Override
        public String displayNameFromLegacyKey(String legacyKey) {
            return legacyDisplayByKey.get(legacyKey);
        }

        @Override
        public String resolveLegacyDisplayNameForHash(long accountHash) {
            return null;
        }
    }
}
