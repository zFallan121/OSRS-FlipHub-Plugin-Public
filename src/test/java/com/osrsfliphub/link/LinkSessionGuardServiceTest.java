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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LinkSessionGuardServiceTest {
    @Test
    public void hasSessionTokenAcceptsNonBlankTokenOnly() {
        TestHooks hooks = new TestHooks();
        LinkSessionGuardService service = new LinkSessionGuardService(hooks);

        hooks.sessionToken = null;
        assertFalse(service.hasSessionToken());

        hooks.sessionToken = "   ";
        assertFalse(service.hasSessionToken());

        hooks.sessionToken = "token-123";
        assertTrue(service.hasSessionToken());
    }

    @Test
    public void isLinkedRequiresBothTokenAndSigningSecret() {
        TestHooks hooks = new TestHooks();
        LinkSessionGuardService service = new LinkSessionGuardService(hooks);

        hooks.sessionToken = "token";
        hooks.signingSecret = null;
        assertFalse(service.isLinked());

        hooks.signingSecret = "   ";
        assertFalse(service.isLinked());

        hooks.signingSecret = "secret";
        assertTrue(service.isLinked());
    }

    @Test
    public void resolveLinkedCredentialsReturnsTrimmedValuesWhenComplete() {
        TestHooks hooks = new TestHooks();
        LinkSessionGuardService service = new LinkSessionGuardService(hooks);

        hooks.sessionToken = " token ";
        hooks.signingSecret = " secret ";
        LinkSessionGuardService.Credentials credentials = service.resolveLinkedCredentials();

        assertNotNull(credentials);
        assertEquals("token", credentials.sessionToken);
        assertEquals("secret", credentials.signingSecret);
    }

    @Test
    public void resolveLinkedCredentialsReturnsNullWhenAnyValueMissing() {
        TestHooks hooks = new TestHooks();
        LinkSessionGuardService service = new LinkSessionGuardService(hooks);

        hooks.sessionToken = "token";
        hooks.signingSecret = "";
        assertNull(service.resolveLinkedCredentials());

        hooks.sessionToken = null;
        hooks.signingSecret = "secret";
        assertNull(service.resolveLinkedCredentials());
    }

    private static final class TestHooks implements LinkSessionGuardService.Hooks {
        private String sessionToken;
        private String signingSecret;

        @Override
        public String getSessionToken() {
            return sessionToken;
        }

        @Override
        public String getSigningSecret() {
            return signingSecret;
        }
    }
}
