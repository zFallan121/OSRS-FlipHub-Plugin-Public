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

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLHandshakeException;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * When a failed link is allowed to erase the player's licence key.
 *
 * <p>Only FlipHub itself saying no to the key is a reason to forget it. Every other failure
 * used to erase it as well, so starting the client before the network was up, or pasting a
 * key with "Enable FlipHub sync" still unticked, wiped the key the player had just found and
 * pasted. Getting it back means going to the website for it again.
 */
public class LinkKeyRetentionTest {
    /** The server answered and said the key is no good. Keeping it re-sends it forever. */
    @Test
    public void aKeyTheServerRejectsIsForgotten() {
        assertTrue(refused(400));
        assertTrue(refused(401));
        assertTrue(refused(403));
        assertTrue(refused(404));
        assertTrue(refused(422));
    }

    /** The server is busy or asking for patience. That says nothing about the key. */
    @Test
    public void aServerAskingForPatienceKeepsTheKey() {
        assertFalse(refused(408));
        assertFalse(refused(429));
    }

    /** The server broke. The key may well be perfect. */
    @Test
    public void aServerErrorKeepsTheKey() {
        assertFalse(refused(500));
        assertFalse(refused(502));
        assertFalse(refused(503));
    }

    /** Nobody answered at all: offline, no name resolution, no handshake, a reset. */
    @Test
    public void aNetworkThatIsNotWorkingKeepsTheKey() {
        assertFalse(ApiRefusedException.refusedTheRequest(new UnknownHostException("api.example")));
        assertFalse(ApiRefusedException.refusedTheRequest(new ConnectException("connection refused")));
        assertFalse(ApiRefusedException.refusedTheRequest(new SSLHandshakeException("handshake")));
        assertFalse(ApiRefusedException.refusedTheRequest(new SocketException("connection reset")));
        assertFalse(ApiRefusedException.refusedTheRequest(new SocketTimeoutException("read timed out")));
    }

    /**
     * The plugin declining to send at all, because the player has not ticked "Enable FlipHub
     * sync" yet. This wiped the key on the spot, which is the worst moment for it to happen.
     */
    @Test
    public void refusingToSendBecauseSyncIsOffKeepsTheKey() {
        assertFalse(ApiRefusedException.refusedTheRequest(
            new IllegalStateException("FlipHub sync is disabled.")));
    }

    /** The answer has to survive being wrapped, since that is how it reaches the handler. */
    @Test
    public void theAnswerSurvivesBeingWrapped() {
        assertTrue(ApiRefusedException.refusedTheRequest(
            new IOException("link", new ApiRefusedException(401, "Link failed: 401"))));
        assertFalse(ApiRefusedException.refusedTheRequest(
            new IOException("link", new ApiRefusedException(503, "Link failed: 503"))));
    }

    @Test
    public void nothingAtAllKeepsTheKey() {
        assertFalse(ApiRefusedException.refusedTheRequest(null));
    }

    private static boolean refused(int statusCode) {
        return ApiRefusedException.refusedTheRequest(
            new ApiRefusedException(statusCode, "Link failed: " + statusCode));
    }
}
