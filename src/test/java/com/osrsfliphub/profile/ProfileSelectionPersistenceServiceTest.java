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
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProfileSelectionPersistenceServiceTest {
    private static final String CONFIG_GROUP = "cfg";
    private static final String LEGACY_GROUP = "legacy";
    private static final String SELECTED_KEY = "selected";
    private static final String MODE_KEY = "mode";

    @Test
    public void loadUsesCurrentConfigWhenPresent() {
        TestHooks hooks = new TestHooks();
        hooks.put(CONFIG_GROUP, SELECTED_KEY, "hash_555");
        hooks.put(CONFIG_GROUP, MODE_KEY, "manual");
        ProfileSelectionState state = new ProfileSelectionState("accountwide");
        ProfileSelectionPersistenceService service = service(hooks);

        boolean migrated = service.load(state);

        assertFalse(migrated);
        assertEquals("hash_555", state.resolveSelectedProfileKeyForUi(true));
        assertFalse(state.updateForLogin(777L));
    }

    @Test
    public void loadMigratesFromLegacyWhenCurrentMissing() {
        TestHooks hooks = new TestHooks();
        hooks.put(LEGACY_GROUP, SELECTED_KEY, "hash_888");
        hooks.put(LEGACY_GROUP, MODE_KEY, "manual");
        ProfileSelectionState state = new ProfileSelectionState("accountwide");
        ProfileSelectionPersistenceService service = service(hooks);

        boolean migrated = service.load(state);

        assertTrue(migrated);
        assertEquals("hash_888", state.resolveSelectedProfileKeyForUi(true));
        assertFalse(state.updateForLogin(999L));
    }

    @Test
    public void loadKeepsExistingSelectionWhenStoredKeyMissingButAppliesMode() {
        TestHooks hooks = new TestHooks();
        hooks.put(CONFIG_GROUP, MODE_KEY, "auto");
        ProfileSelectionState state = new ProfileSelectionState("accountwide");
        state.selectManual("hash_111");
        ProfileSelectionPersistenceService service = service(hooks);

        boolean migrated = service.load(state);

        assertFalse(migrated);
        assertEquals("hash_111", state.resolveSelectedProfileKeyForUi(true));
        assertTrue(state.updateForLogin(222L));
        assertEquals("hash_222", state.resolveSelectedProfileKeyForUi(true));
    }

    @Test
    public void persistWritesSelectedKeyAndMode() {
        TestHooks hooks = new TestHooks();
        ProfileSelectionState state = new ProfileSelectionState("accountwide");
        state.selectManual("hash_777");
        ProfileSelectionPersistenceService service = service(hooks);

        service.persist(state);

        assertEquals("hash_777", hooks.get(CONFIG_GROUP, SELECTED_KEY));
        assertEquals("manual", hooks.get(CONFIG_GROUP, MODE_KEY));

        state.restoreLoadedState("hash_123", "auto");
        service.persist(state);
        assertEquals("hash_123", hooks.get(CONFIG_GROUP, SELECTED_KEY));
        assertEquals("auto", hooks.get(CONFIG_GROUP, MODE_KEY));
    }

    private static ProfileSelectionPersistenceService service(TestHooks hooks) {
        return new ProfileSelectionPersistenceService(
            hooks,
            CONFIG_GROUP,
            LEGACY_GROUP,
            SELECTED_KEY,
            MODE_KEY
        );
    }

    private static final class TestHooks implements ProfileSelectionPersistenceService.Hooks {
        private final Map<String, String> values = new HashMap<>();

        @Override
        public String readConfiguration(String configGroup, String key) {
            return values.get(configGroup + ":" + key);
        }

        @Override
        public void writeConfiguration(String configGroup, String key, String value) {
            values.put(configGroup + ":" + key, value);
        }

        void put(String configGroup, String key, String value) {
            values.put(configGroup + ":" + key, value);
        }

        String get(String configGroup, String key) {
            return values.get(configGroup + ":" + key);
        }
    }
}
