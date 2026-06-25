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

public class ProfileLoginServiceTest {
    @Test
    public void handleLoginNoOpsForInvalidAccountHash() {
        TestHooks hooks = new TestHooks();
        ProfileLoginService service = new ProfileLoginService(hooks);
        ProfileSelectionState selection = new ProfileSelectionState("accountwide");

        service.handleLogin(selection, -1L, "TestUser");

        assertEquals(0, hooks.displayNameUpdates);
        assertEquals(0, hooks.loadRequests);
        assertEquals(0, hooks.persistCalls);
        assertEquals(0, hooks.optionsRefreshCalls);
        assertEquals(0, hooks.headerRefreshCalls);
    }

    @Test
    public void handleLoginAutoSelectionPersistsAndRefreshesUi() {
        TestHooks hooks = new TestHooks();
        ProfileLoginService service = new ProfileLoginService(hooks);
        ProfileSelectionState selection = new ProfileSelectionState("accountwide");

        service.handleLogin(selection, 123L, " TestUser ");

        assertEquals(1, hooks.displayNameUpdates);
        assertEquals(123L, hooks.lastDisplayAccountHash);
        assertEquals("TestUser", hooks.lastDisplayName);
        assertEquals(1, hooks.executeAsyncCalls);
        assertEquals(1, hooks.loadRequests);
        assertEquals(123L, hooks.lastLoadAccountHash);
        assertEquals(1, hooks.persistCalls);
        assertEquals(1, hooks.optionsRefreshCalls);
        assertEquals(1, hooks.headerRefreshCalls);
        assertEquals(123L, selection.resolveSelectedProfileKey(true, 0L));
    }

    @Test
    public void handleLoginManualSelectionDoesNotPersist() {
        TestHooks hooks = new TestHooks();
        ProfileLoginService service = new ProfileLoginService(hooks);
        ProfileSelectionState selection = new ProfileSelectionState("accountwide");
        selection.selectManual("hash_999");

        service.handleLogin(selection, 123L, "TestUser");

        assertEquals(0, hooks.persistCalls);
        assertEquals(999L, selection.resolveSelectedProfileKey(true, 0L));
        assertEquals(1, hooks.optionsRefreshCalls);
        assertEquals(1, hooks.headerRefreshCalls);
    }

    @Test
    public void handleLoginSkipsBlankDisplayName() {
        TestHooks hooks = new TestHooks();
        ProfileLoginService service = new ProfileLoginService(hooks);
        ProfileSelectionState selection = new ProfileSelectionState("accountwide");

        service.handleLogin(selection, 321L, "  ");

        assertEquals(0, hooks.displayNameUpdates);
        assertEquals(1, hooks.loadRequests);
        assertEquals(1, hooks.persistCalls);
    }

    private static final class TestHooks implements ProfileLoginService.Hooks {
        private int displayNameUpdates;
        private long lastDisplayAccountHash;
        private String lastDisplayName = "";
        private int executeAsyncCalls;
        private int loadRequests;
        private long lastLoadAccountHash;
        private int persistCalls;
        private int optionsRefreshCalls;
        private int headerRefreshCalls;

        @Override
        public void putProfileDisplayName(long accountHash, String displayName) {
            displayNameUpdates++;
            lastDisplayAccountHash = accountHash;
            lastDisplayName = displayName;
        }

        @Override
        public void executeAsync(Runnable task) {
            executeAsyncCalls++;
            if (task != null) {
                task.run();
            }
        }

        @Override
        public void loadLocalTradesAsync(long accountHash) {
            loadRequests++;
            lastLoadAccountHash = accountHash;
        }

        @Override
        public void persistProfileSelectionState() {
            persistCalls++;
        }

        @Override
        public void updateProfileOptionsUi() {
            optionsRefreshCalls++;
        }

        @Override
        public void updateProfileHeader() {
            headerRefreshCalls++;
        }
    }
}
