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
import static org.junit.Assert.assertTrue;

public class LocalAccountSessionServiceTest {
    private static final long ACCOUNT = 42L;
    private static final long ACCOUNTWIDE = 0L;
    private static final long LOGIN_MS = 1_000_000L;

    /**
     * The client reports LOGGED_IN again after every world hop and loading screen. Restarting
     * the window there emptied the Session range while the session timer beside it kept
     * running, so a flipper who hopped saw their profit drop to zero mid-session.
     */
    @Test
    public void hoppingWorldsDoesNotRestartTheSessionWindow() {
        LocalAccountSessionService service = new LocalAccountSessionService(null);
        Map<Long, Long> starts = new HashMap<>();
        Object lock = new Object();

        service.startSessionIfAbsent(starts, lock, ACCOUNT, ACCOUNTWIDE, LOGIN_MS);
        service.startSessionIfAbsent(starts, lock, ACCOUNT, ACCOUNTWIDE, LOGIN_MS + 3_600_000L);

        assertEquals(Long.valueOf(LOGIN_MS), starts.get(ACCOUNT));
        assertEquals(Long.valueOf(LOGIN_MS), starts.get(ACCOUNTWIDE));
    }

    /** Reaching the login screen ends the session, so the next login starts a fresh one. */
    @Test
    public void loggingOutEndsTheSessionAndTheNextLoginStartsANewOne() {
        LocalAccountSessionService service = new LocalAccountSessionService(null);
        Map<Long, Long> starts = new HashMap<>();
        Object lock = new Object();
        service.startSessionIfAbsent(starts, lock, ACCOUNT, ACCOUNTWIDE, LOGIN_MS);

        service.clearLocalAccountSessionStarts(starts, lock);
        assertTrue(starts.isEmpty());

        service.startSessionIfAbsent(starts, lock, ACCOUNT, ACCOUNTWIDE, LOGIN_MS + 7_200_000L);
        assertEquals(Long.valueOf(LOGIN_MS + 7_200_000L), starts.get(ACCOUNT));
    }

    @Test
    public void anUnresolvedAccountStartsNothing() {
        LocalAccountSessionService service = new LocalAccountSessionService(null);
        Map<Long, Long> starts = new HashMap<>();

        service.startSessionIfAbsent(starts, new Object(), -1L, ACCOUNTWIDE, LOGIN_MS);

        assertTrue(starts.isEmpty());
    }
}
