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
import static org.junit.Assert.assertTrue;

public class WebsiteStatsWipeServiceTest {
    @Test
    public void rejectsWhenNotLinked() {
        TestHooks hooks = new TestHooks();
        hooks.linked = false;
        WebsiteStatsWipeService service = new WebsiteStatsWipeService(hooks);

        service.wipeWebsiteStatsAsync();

        assertEquals(1, hooks.errorMessages.size());
        assertTrue(hooks.errorMessages.get(0).contains("only available when linked"));
        assertEquals(0, hooks.ioTasksRun);
    }

    @Test
    public void rejectsWhenCredentialsMissing() {
        TestHooks hooks = new TestHooks();
        hooks.linked = true;
        hooks.credentials = null;
        WebsiteStatsWipeService service = new WebsiteStatsWipeService(hooks);

        service.wipeWebsiteStatsAsync();

        assertEquals(1, hooks.errorMessages.size());
        assertTrue(hooks.errorMessages.get(0).contains("missing link credentials"));
        assertEquals(0, hooks.ioTasksRun);
    }

    @Test
    public void rejectsWhenIoExecutorUnavailable() {
        TestHooks hooks = new TestHooks();
        hooks.linked = true;
        hooks.credentials = new LinkSessionGuardService.Credentials("token", "secret");
        hooks.hasIoExecutor = false;
        WebsiteStatsWipeService service = new WebsiteStatsWipeService(hooks);

        service.wipeWebsiteStatsAsync();

        assertEquals(1, hooks.errorMessages.size());
        assertTrue(hooks.errorMessages.get(0).contains("IO executor is unavailable"));
        assertEquals(0, hooks.ioTasksRun);
    }

    @Test
    public void reportsSuccessAndTriggersStatsRefresh() {
        TestHooks hooks = new TestHooks();
        hooks.linked = true;
        hooks.credentials = new LinkSessionGuardService.Credentials("token", "secret");
        hooks.hasIoExecutor = true;
        ApiClient.WipeStatsResponse response = new ApiClient.WipeStatsResponse();
        response.status = "ok";
        response.deleted_trade_events = 10;
        response.deleted_buy_lots = 9;
        response.deleted_flip_fills = 8;
        response.deleted_accountwide_stats = 7;
        hooks.response = response;
        WebsiteStatsWipeService service = new WebsiteStatsWipeService(hooks);

        service.wipeWebsiteStatsAsync();

        assertEquals(1, hooks.ioTasksRun);
        assertEquals(1, hooks.clientThreadTasksRun);
        assertEquals(1, hooks.statsRefreshCalls);
        assertEquals(1, hooks.gameMessages.size());
        assertTrue(hooks.gameMessages.get(0).contains("deleted 10 events, 9 lots, 8 fills, 7 summaries"));
    }

    @Test
    public void reportsExceptionAsGameMessage() {
        TestHooks hooks = new TestHooks();
        hooks.linked = true;
        hooks.credentials = new LinkSessionGuardService.Credentials("token", "secret");
        hooks.hasIoExecutor = true;
        hooks.toThrow = new RuntimeException("boom");
        WebsiteStatsWipeService service = new WebsiteStatsWipeService(hooks);

        service.wipeWebsiteStatsAsync();

        assertEquals(1, hooks.ioTasksRun);
        assertEquals(1, hooks.clientThreadTasksRun);
        assertEquals(1, hooks.gameMessages.size());
        assertTrue(hooks.gameMessages.get(0).contains("boom"));
    }

    private static final class TestHooks implements WebsiteStatsWipeService.Hooks {
        private boolean linked;
        private LinkSessionGuardService.Credentials credentials;
        private boolean hasIoExecutor;
        private ApiClient.WipeStatsResponse response;
        private RuntimeException toThrow;
        private int ioTasksRun;
        private int clientThreadTasksRun;
        private int statsRefreshCalls;
        private final java.util.List<String> errorMessages = new java.util.ArrayList<>();
        private final java.util.List<String> gameMessages = new java.util.ArrayList<>();

        @Override
        public boolean isLinked() {
            return linked;
        }

        @Override
        public LinkSessionGuardService.Credentials resolveLinkedCredentials() {
            return credentials;
        }

        @Override
        public boolean hasIoExecutor() {
            return hasIoExecutor;
        }

        @Override
        public void executeIo(Runnable task) {
            ioTasksRun++;
            if (task != null) {
                task.run();
            }
        }

        @Override
        public void runOnClientThread(Runnable task) {
            clientThreadTasksRun++;
            if (task != null) {
                task.run();
            }
        }

        @Override
        public ApiClient.WipeStatsResponse wipeWebsiteStats(String sessionToken, String signingSecret) {
            if (toThrow != null) {
                throw toThrow;
            }
            return response;
        }

        @Override
        public void showError(String message) {
            errorMessages.add(message);
        }

        @Override
        public void pushGameMessage(String message) {
            gameMessages.add(message);
        }

        @Override
        public void triggerStatsRefresh() {
            statsRefreshCalls++;
        }
    }
}
