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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LocalAccountSessionServiceTest {
    @Test
    public void resolveAccountHashReturnsNegativeWhenLoggedOut() {
        TestHooks hooks = new TestHooks();
        hooks.loggedIn = false;
        hooks.accountHash = 123L;
        LocalAccountSessionService service = new LocalAccountSessionService(hooks);

        long hash = service.resolveAccountHash();

        assertEquals(-1L, hash);
    }

    @Test
    public void resolveAccountHashReturnsNegativeWhenHookThrows() {
        TestHooks hooks = new TestHooks();
        hooks.loggedIn = true;
        hooks.throwOnReadAccountHash = true;
        LocalAccountSessionService service = new LocalAccountSessionService(hooks);

        long hash = service.resolveAccountHash();

        assertEquals(-1L, hash);
    }

    @Test
    public void resolveLocalAccountKeyPrefersAccountHashAndMergesOnce() {
        TestHooks hooks = new TestHooks();
        hooks.loggedIn = true;
        hooks.accountHash = 12345L;
        hooks.displayName = "TestUser";
        LocalAccountSessionService service = new LocalAccountSessionService(hooks);

        long first = service.resolveLocalAccountKey();
        long second = service.resolveLocalAccountKey();

        assertEquals(12345L, first);
        assertEquals(12345L, second);
        assertEquals(1, hooks.mergeCalls.size());
    }

    @Test
    public void resolveLocalAccountKeyFallsBackToNameKey() {
        TestHooks hooks = new TestHooks();
        hooks.loggedIn = true;
        hooks.accountHash = -1L;
        hooks.displayName = "TestUser";
        LocalAccountSessionService service = new LocalAccountSessionService(hooks);

        long key = service.resolveLocalAccountKey();

        long expected = Math.abs("testuser".hashCode());
        if (expected == 0L) {
            expected = 1L;
        }
        assertEquals(expected, key);
        assertEquals(0, hooks.mergeCalls.size());
    }

    @Test
    public void resolveDisplayNameReturnsNullWhenHookThrows() {
        TestHooks hooks = new TestHooks();
        hooks.throwOnReadDisplayName = true;
        LocalAccountSessionService service = new LocalAccountSessionService(hooks);

        String name = service.resolveDisplayName();

        assertEquals(null, name);
    }

    @Test
    public void resolveLimitAccountKeyUsesFallbackWhenProvided() {
        TestHooks hooks = new TestHooks();
        hooks.loggedIn = true;
        hooks.accountHash = 12345L;
        hooks.displayName = "TestUser";
        LocalAccountSessionService service = new LocalAccountSessionService(hooks);

        long resolved = service.resolveLimitAccountKey(777L);

        assertEquals(777L, resolved);
    }

    @Test
    public void updateLocalAccountSessionStartStoresCurrentAndAccountwideTimestamps() {
        TestHooks hooks = new TestHooks();
        hooks.loggedIn = true;
        hooks.accountHash = 555L;
        hooks.nowMs = 10_000L;
        LocalAccountSessionService service = new LocalAccountSessionService(hooks);
        Map<Long, Long> sessions = new HashMap<>();

        service.updateLocalAccountSessionStart(sessions, new Object(), 0L);

        assertEquals(Long.valueOf(10_000L), sessions.get(555L));
        assertEquals(Long.valueOf(10_000L), sessions.get(0L));
    }

    @Test
    public void ensureAndResolveSessionStartBehaveAsExpected() {
        TestHooks hooks = new TestHooks();
        LocalAccountSessionService service = new LocalAccountSessionService(hooks);
        Map<Long, Long> sessions = new HashMap<>();
        Object lock = new Object();

        service.ensureLocalSessionStart(sessions, lock, 42L, 1_000L);
        service.ensureLocalSessionStart(sessions, lock, 42L, 2_000L);
        long existing = service.resolveStatsSessionStartMs(sessions, lock, 42L, 3_000L);
        long missing = service.resolveStatsSessionStartMs(sessions, lock, 77L, 4_000L);
        long negative = service.resolveStatsSessionStartMs(sessions, lock, -1L, 5_000L);

        assertEquals(Long.valueOf(1_000L), sessions.get(42L));
        assertEquals(1_000L, existing);
        assertEquals(4_000L, missing);
        assertEquals(Long.valueOf(4_000L), sessions.get(77L));
        assertEquals(5_000L, negative);
    }

    private static final class TestHooks implements LocalAccountSessionService.Hooks {
        private boolean loggedIn;
        private long accountHash;
        private String displayName;
        private long nowMs = 1L;
        private boolean throwOnReadAccountHash;
        private boolean throwOnReadDisplayName;
        private final List<String> mergeCalls = new ArrayList<>();

        @Override
        public boolean isLoggedIn() {
            return loggedIn;
        }

        @Override
        public long readAccountHash() {
            if (throwOnReadAccountHash) {
                throw new IllegalStateException("hash read failed");
            }
            return accountHash;
        }

        @Override
        public String readDisplayName() {
            if (throwOnReadDisplayName) {
                throw new IllegalStateException("display name read failed");
            }
            return displayName;
        }

        @Override
        public long nowMs() {
            return nowMs;
        }

        @Override
        public void mergeLocalAccountData(long accountHash, long nameKey) {
            mergeCalls.add(accountHash + ":" + nameKey);
        }
    }
}
