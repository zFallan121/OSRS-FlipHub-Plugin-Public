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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ProfilePresentationServiceTest {
    @Test
    public void resolveSelectedProfileLabelUsesLegacyFallback() {
        TestHooks hooks = new TestHooks();
        hooks.legacyByHash.put(123L, "sips");
        ProfilePresentationService service = new ProfilePresentationService(0L, "accountwide", hooks);
        Map<Long, String> displayNames = new HashMap<>();
        Map<Long, String> legacyNameKeys = new HashMap<>();

        String label = service.resolveSelectedProfileLabel(123L, displayNames, legacyNameKeys);

        assertEquals("sips", label);
        assertEquals("sips", displayNames.get(123L));
    }

    @Test
    public void buildProfileOptionsNormalizesLabelsAndIncludesAccountwide() {
        TestHooks hooks = new TestHooks();
        hooks.legacyByHash.put(200L, "notari");
        hooks.legacyFromKey.put("name_alt", "alt");
        ProfilePresentationService service = new ProfilePresentationService(0L, "accountwide", hooks);
        Map<Long, String> displayNames = new HashMap<>();
        Map<Long, String> legacyNameKeys = new HashMap<>();
        legacyNameKeys.put(300L, "name_alt");
        Map<Long, String> diskProfiles = new HashMap<>();
        diskProfiles.put(200L, "Profile 200");
        diskProfiles.put(300L, "Profile 300");

        List<FlipHubProfileOption> options = service.buildProfileOptions(
            displayNames,
            legacyNameKeys,
            diskProfiles,
            400L,
            "ari"
        );

        assertEquals("accountwide", options.get(0).key);
        assertEquals("Accountwide", options.get(0).label);
        assertTrue(options.stream().anyMatch(option -> "hash_200".equals(option.key) && "notari".equals(option.label)));
        assertTrue(options.stream().anyMatch(option -> "hash_300".equals(option.key) && "alt".equals(option.label)));
        assertTrue(options.stream().anyMatch(option -> "hash_400".equals(option.key) && "ari".equals(option.label)));
        assertEquals("notari", displayNames.get(200L));
        assertEquals("alt", displayNames.get(300L));
    }

    private static final class TestHooks implements ProfilePresentationService.Hooks {
        private final Map<Long, String> legacyByHash = new HashMap<>();
        private final Map<String, String> legacyFromKey = new HashMap<>();

        @Override
        public String resolveLegacyDisplayNameForHash(long hash) {
            return legacyByHash.get(hash);
        }

        @Override
        public String displayNameFromLegacyKey(String legacyKey) {
            return legacyFromKey.get(legacyKey);
        }

        @Override
        public String buildProfileKey(long accountHash) {
            return "hash_" + accountHash;
        }

        @Override
        public boolean isPlaceholderDisplayName(String displayName) {
            return displayName != null && displayName.startsWith("Profile ");
        }
    }
}

