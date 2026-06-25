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

public class ProfileSelectionResolverServiceTest {
    private static final long ACCOUNTWIDE_KEY = 0L;
    private static final String ACCOUNTWIDE_KEY_STRING = "accountwide";

    @Test
    public void resolveSelectedProfileKeyForUiUsesLoginState() {
        TestHooks hooks = new TestHooks();
        ProfileSelectionResolverService service = service(hooks);
        ProfileSelectionState state = new ProfileSelectionState(ACCOUNTWIDE_KEY_STRING);
        state.selectManual("hash_123");

        hooks.loggedIn = false;
        assertEquals("accountwide", service.resolveSelectedProfileKeyForUi(state));

        hooks.loggedIn = true;
        assertEquals("hash_123", service.resolveSelectedProfileKeyForUi(state));
    }

    @Test
    public void resolveSelectedProfileKeyUsesLoginStateAndAccountwideFallback() {
        TestHooks hooks = new TestHooks();
        ProfileSelectionResolverService service = service(hooks);
        ProfileSelectionState state = new ProfileSelectionState(ACCOUNTWIDE_KEY_STRING);
        state.selectManual("hash_456");

        hooks.loggedIn = false;
        assertEquals(ACCOUNTWIDE_KEY, service.resolveSelectedProfileKey(state));

        hooks.loggedIn = true;
        assertEquals(456L, service.resolveSelectedProfileKey(state));
    }

    @Test
    public void buildProfileKeyDelegatesToStateAndHandlesFallbackWhenStateMissing() {
        TestHooks hooks = new TestHooks();
        ProfileSelectionResolverService service = service(hooks);
        ProfileSelectionState state = new ProfileSelectionState(ACCOUNTWIDE_KEY_STRING);

        assertEquals("hash_789", service.buildProfileKey(state, 789L));
        assertEquals("accountwide", service.buildProfileKey(state, 0L));

        assertEquals("hash_999", service.buildProfileKey(null, 999L));
        assertEquals("accountwide", service.buildProfileKey(null, 0L));
    }

    private static ProfileSelectionResolverService service(TestHooks hooks) {
        return new ProfileSelectionResolverService(ACCOUNTWIDE_KEY, ACCOUNTWIDE_KEY_STRING, hooks);
    }

    private static final class TestHooks implements ProfileSelectionResolverService.Hooks {
        private boolean loggedIn;

        @Override
        public boolean isClientLoggedIn() {
            return loggedIn;
        }
    }
}
