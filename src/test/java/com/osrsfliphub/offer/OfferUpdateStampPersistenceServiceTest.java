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
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OfferUpdateStampPersistenceServiceTest {
    private static final String CONFIG_GROUP = "fliphub";
    private static final String LEGACY_GROUP = "fliphub-dev";

    @Test
    public void loadLegacyGlobalReadsLegacyConfigKey() {
        OfferUpdateStampConfigStore store = new OfferUpdateStampConfigStore();
        TestHooks hooks = new TestHooks();
        hooks.putConfig(CONFIG_GROUP, store.legacyGlobalKey(), serialize(singleStampMap(0, stamp(4151, false)), hooks.gson));

        OfferUpdateStampPersistenceService service = new OfferUpdateStampPersistenceService(
            CONFIG_GROUP,
            LEGACY_GROUP,
            store,
            new OfferUpdateStampLegacyMatcher(),
            hooks
        );

        Map<Integer, OfferUpdateStamp> destination = new HashMap<>();
        destination.put(7, stamp(995, true));
        service.loadLegacyGlobal(destination);

        assertEquals(1, destination.size());
        assertEquals(4151, destination.get(0).itemId);
    }

    @Test
    public void loadForCurrentAccountUsesPerAccountConfigWhenPresent() {
        OfferUpdateStampConfigStore store = new OfferUpdateStampConfigStore();
        TestHooks hooks = new TestHooks();
        hooks.loggedIn = true;
        hooks.accountKey = 123L;
        hooks.putConfig(CONFIG_GROUP, store.perAccountKey(123L), serialize(singleStampMap(1, stamp(560, true)), hooks.gson));

        OfferUpdateStampPersistenceService service = new OfferUpdateStampPersistenceService(
            CONFIG_GROUP,
            LEGACY_GROUP,
            store,
            new OfferUpdateStampLegacyMatcher(),
            hooks
        );

        Map<Integer, OfferUpdateStamp> destination = new HashMap<>();
        OfferUpdateStampPersistenceService.LoadState state = service.loadForCurrentAccount(destination, -1L, false);

        assertEquals(123L, state.accountKey);
        assertTrue(state.loaded);
        assertEquals(1, destination.size());
        assertEquals(560, destination.get(1).itemId);
        assertTrue(hooks.writes.isEmpty());
    }

    @Test
    public void loadForCurrentAccountMigratesLegacyWhenMatchedAndPersists() {
        OfferUpdateStampConfigStore store = new OfferUpdateStampConfigStore();
        TestHooks hooks = new TestHooks();
        hooks.loggedIn = true;
        hooks.accountKey = 555L;
        hooks.currentOffers = new GrandExchangeOffer[] {
            offer(4151, GrandExchangeOfferState.SELLING)
        };
        hooks.putConfig(CONFIG_GROUP, store.legacyGlobalKey(), serialize(singleStampMap(0, stamp(4151, false)), hooks.gson));

        OfferUpdateStampPersistenceService service = new OfferUpdateStampPersistenceService(
            CONFIG_GROUP,
            LEGACY_GROUP,
            store,
            new OfferUpdateStampLegacyMatcher(),
            hooks
        );

        Map<Integer, OfferUpdateStamp> destination = new HashMap<>();
        OfferUpdateStampPersistenceService.LoadState state = service.loadForCurrentAccount(destination, -1L, false);

        assertEquals(555L, state.accountKey);
        assertTrue(state.loaded);
        assertEquals(1, destination.size());
        assertEquals(4151, destination.get(0).itemId);
        assertTrue(hooks.writes.containsKey(configSlot(CONFIG_GROUP, store.perAccountKey(555L))));
    }

    @Test
    public void loadForCurrentAccountMarksNotLoadedWhenConfigUnavailable() {
        OfferUpdateStampConfigStore store = new OfferUpdateStampConfigStore();
        TestHooks hooks = new TestHooks();
        hooks.loggedIn = true;
        hooks.accountKey = 42L;
        hooks.hasConfigAccess = false;

        OfferUpdateStampPersistenceService service = new OfferUpdateStampPersistenceService(
            CONFIG_GROUP,
            LEGACY_GROUP,
            store,
            new OfferUpdateStampLegacyMatcher(),
            hooks
        );

        Map<Integer, OfferUpdateStamp> destination = new HashMap<>();
        destination.put(0, stamp(4151, false));
        OfferUpdateStampPersistenceService.LoadState state = service.loadForCurrentAccount(destination, -1L, false);

        assertEquals(42L, state.accountKey);
        assertFalse(state.loaded);
        assertTrue(destination.isEmpty());
    }

    @Test
    public void persistForCurrentAccountResolvesAndWritesPerAccountKey() {
        OfferUpdateStampConfigStore store = new OfferUpdateStampConfigStore();
        TestHooks hooks = new TestHooks();
        hooks.loggedIn = true;
        hooks.accountKey = 777L;

        OfferUpdateStampPersistenceService service = new OfferUpdateStampPersistenceService(
            CONFIG_GROUP,
            LEGACY_GROUP,
            store,
            new OfferUpdateStampLegacyMatcher(),
            hooks
        );

        Map<Integer, OfferUpdateStamp> stamps = singleStampMap(2, stamp(561, false));
        long resolved = service.persistForCurrentAccount(stamps, -1L);

        assertEquals(777L, resolved);
        String persisted = hooks.writes.get(configSlot(CONFIG_GROUP, store.perAccountKey(777L)));
        assertTrue(persisted != null && persisted.contains("\"2\""));
    }

    private static String serialize(Map<Integer, OfferUpdateStamp> stamps, Gson gson) {
        return OfferUpdateStampStore.serialize(stamps, gson);
    }

    private static Map<Integer, OfferUpdateStamp> singleStampMap(int slot, OfferUpdateStamp stamp) {
        Map<Integer, OfferUpdateStamp> map = new HashMap<>();
        map.put(slot, stamp);
        return map;
    }

    private static OfferUpdateStamp stamp(int itemId, boolean isBuy) {
        return new OfferUpdateStamp(itemId, 100, 100, 0, isBuy, 0L, 1L, 1L, 0L, 0L);
    }

    private static GrandExchangeOffer offer(int itemId, GrandExchangeOfferState state) {
        return (GrandExchangeOffer) Proxy.newProxyInstance(
            GrandExchangeOffer.class.getClassLoader(),
            new Class<?>[] {GrandExchangeOffer.class},
            (proxy, method, args) -> handleOfferMethod(method, itemId, state)
        );
    }

    private static Object handleOfferMethod(Method method, int itemId, GrandExchangeOfferState state) {
        switch (method.getName()) {
            case "getItemId":
                return itemId;
            case "getState":
                return state;
            default:
                return defaultValue(method);
        }
    }

    private static Object defaultValue(Method method) {
        Class<?> returnType = method.getReturnType();
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return 0;
    }

    private static String configSlot(String group, String key) {
        return group + "::" + key;
    }

    private static final class TestHooks implements OfferUpdateStampPersistenceService.Hooks {
        private final Gson gson = new Gson();
        private final Map<String, String> reads = new HashMap<>();
        private final Map<String, String> writes = new HashMap<>();
        private boolean hasConfigAccess = true;
        private boolean loggedIn = false;
        private long accountKey = -1L;
        private GrandExchangeOffer[] currentOffers = new GrandExchangeOffer[0];

        @Override
        public Gson gson() {
            return gson;
        }

        @Override
        public boolean hasConfigurationAccess() {
            return hasConfigAccess;
        }

        @Override
        public boolean isClientLoggedIn() {
            return loggedIn;
        }

        @Override
        public long resolveCurrentAccountKey() {
            return accountKey;
        }

        @Override
        public GrandExchangeOffer[] currentOffers() {
            return currentOffers;
        }

        @Override
        public String readConfiguration(String configGroup, String key) {
            return reads.get(configSlot(configGroup, key));
        }

        @Override
        public void writeConfiguration(String configGroup, String key, String value) {
            writes.put(configSlot(configGroup, key), value);
        }

        private void putConfig(String configGroup, String key, String value) {
            reads.put(configSlot(configGroup, key), value);
        }
    }
}
