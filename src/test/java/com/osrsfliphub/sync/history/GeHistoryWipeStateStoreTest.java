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
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GeHistoryWipeStateStoreTest {
    private static final String CFG = "cfg";
    private static final String BARRIER = "wipeBarrierV1_";
    private static final String CURSOR = "geHistoryCursorV1_";

    @Test
    public void wipeBarrierReadAndWriteWorkForValidAccountKeys() {
        TestHooks hooks = new TestHooks();
        GeHistoryWipeStateStore store = new GeHistoryWipeStateStore(hooks, CFG, BARRIER, CURSOR, 45);

        assertFalse(store.isWipeBarrierArmed(123L));
        store.setWipeBarrierArmed(123L, true);
        assertTrue(store.isWipeBarrierArmed(123L));
        assertEquals("1", hooks.get(CFG, BARRIER + "123"));

        store.setWipeBarrierArmed(123L, false);
        assertFalse(store.isWipeBarrierArmed(123L));
        assertEquals("", hooks.get(CFG, BARRIER + "123"));
    }

    @Test
    public void invalidAccountKeysDoNotReadOrWriteState() {
        TestHooks hooks = new TestHooks();
        GeHistoryWipeStateStore store = new GeHistoryWipeStateStore(hooks, CFG, BARRIER, CURSOR, 45);

        store.setWipeBarrierArmed(0L, true);
        store.persistCursor(-1L, Arrays.asList("a", "b"));

        assertTrue(hooks.values.isEmpty());
        assertFalse(store.isWipeBarrierArmed(0L));
        assertTrue(store.loadCursor(-5L).isEmpty());
    }

    @Test
    public void loadCursorParsesCommaSeparatedValuesWithTrim() {
        TestHooks hooks = new TestHooks();
        hooks.put(CFG, CURSOR + "42", " a , ,b, c ");
        GeHistoryWipeStateStore store = new GeHistoryWipeStateStore(hooks, CFG, BARRIER, CURSOR, 45);

        List<String> cursor = store.loadCursor(42L);
        assertEquals(Arrays.asList("a", "b", "c"), cursor);
    }

    @Test
    public void persistCursorWritesEmptyOrLimitedJoinedValues() {
        TestHooks hooks = new TestHooks();
        GeHistoryWipeStateStore store = new GeHistoryWipeStateStore(hooks, CFG, BARRIER, CURSOR, 2);

        store.persistCursor(77L, null);
        assertEquals("", hooks.get(CFG, CURSOR + "77"));

        store.persistCursor(77L, Arrays.asList("a", "b", "c"));
        assertEquals("a,b", hooks.get(CFG, CURSOR + "77"));
    }

    private static final class TestHooks implements GeHistoryWipeStateStore.Hooks {
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
