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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BackfilledProfilesStoreTest {
    private static final String CONFIG_GROUP = FliphubConfigGroups.CONFIG_GROUP;

    @Test
    public void loadParsesValidPositiveKeys() {
        TestHooks hooks = new TestHooks();
        hooks.values.put(CONFIG_GROUP + "|backfilledProfilesV1", "1, 2,invalid,-1,0,3");
        BackfilledProfilesStore store = new BackfilledProfilesStore(CONFIG_GROUP, "backfilledProfilesV1", hooks);

        Set<Long> keys = store.load();

        assertEquals(3, keys.size());
        assertTrue(keys.contains(1L));
        assertTrue(keys.contains(2L));
        assertTrue(keys.contains(3L));
    }

    @Test
    public void persistStoresSortedCommaSeparatedKeys() {
        TestHooks hooks = new TestHooks();
        BackfilledProfilesStore store = new BackfilledProfilesStore(CONFIG_GROUP, "backfilledProfilesV1", hooks);

        store.persist(new HashSet<>(Arrays.asList(9L, 3L, 7L)));

        assertEquals("3,7,9", hooks.values.get(CONFIG_GROUP + "|backfilledProfilesV1"));
    }

    @Test
    public void persistStoresEmptyStringWhenNoKeys() {
        TestHooks hooks = new TestHooks();
        BackfilledProfilesStore store = new BackfilledProfilesStore(CONFIG_GROUP, "backfilledProfilesV1", hooks);

        store.persist(new HashSet<>());

        assertEquals("", hooks.values.get(CONFIG_GROUP + "|backfilledProfilesV1"));
    }

    private static final class TestHooks implements BackfilledProfilesStore.Hooks {
        private final Map<String, String> values = new HashMap<>();

        @Override
        public String getConfiguration(String group, String key) {
            return values.get(group + "|" + key);
        }

        @Override
        public void setConfiguration(String group, String key, String value) {
            values.put(group + "|" + key, value);
        }
    }
}
