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

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LinkAttemptServiceTest {
    @Test
    public void resolveLinkInputPrefersLicenseKey() {
        LinkAttemptService service = new LinkAttemptService(new TestHooks());

        assertEquals("abc", service.resolveLinkInput("abc", "def"));
        assertEquals("def", service.resolveLinkInput("   ", "def"));
        assertNull(service.resolveLinkInput(null, null));
    }

    @Test
    public void attemptLinkWhenLoggedOutOnlyUpdatesHeader() {
        TestHooks hooks = new TestHooks();
        hooks.clientLoggedIn = false;
        LinkAttemptService service = new LinkAttemptService(hooks);

        service.attemptLink("abc");

        assertEquals(1, hooks.updateProfileHeaderCalls);
        assertEquals(0, hooks.linkDeviceCalls);
        assertEquals(0, hooks.executeIoCalls);
    }

    @Test
    public void successfulLinkPersistsSessionAndSchedulesFollowUps() {
        TestHooks hooks = new TestHooks();
        hooks.clientLoggedIn = true;
        hooks.linkResponse = response("token-1", "secret-1");
        LinkAttemptService service = new LinkAttemptService(hooks);

        service.attemptLink("  my-key  ");

        assertEquals(1, hooks.executeIoCalls);
        assertEquals(1, hooks.linkDeviceCalls);
        assertEquals("my-key", hooks.lastLicenseKey);
        assertEquals("device-123", hooks.lastDeviceId);
        assertEquals("token-1", hooks.persistedSessionToken);
        assertEquals("secret-1", hooks.persistedSigningSecret);
        assertEquals(1, hooks.resetUploadSnapshotCalls);
        assertEquals(1, hooks.resetUploadDiagnosticsCalls);
        assertEquals(1, hooks.updateUploadDiagnosticsUiCalls);
        assertEquals(1, hooks.requestBackfillCalls);
        assertEquals(1, hooks.scheduleAccountwideSyncCalls);
        assertEquals(1, hooks.refreshPanelCalls);
        assertEquals(1, hooks.updateProfileHeaderCalls);
        assertEquals(0, hooks.scheduleRetryCalls);
        assertEquals(0, hooks.logFailureCalls);
    }

    @Test
    public void incompleteLinkResponseOnlyUpdatesHeader() {
        TestHooks hooks = new TestHooks();
        hooks.clientLoggedIn = true;
        hooks.linkResponse = response("token-1", null);
        LinkAttemptService service = new LinkAttemptService(hooks);

        service.attemptLink("key");

        assertEquals(1, hooks.linkDeviceCalls);
        assertNull(hooks.persistedSessionToken);
        assertEquals(0, hooks.resetUploadSnapshotCalls);
        assertEquals(0, hooks.requestBackfillCalls);
        assertEquals(0, hooks.scheduleAccountwideSyncCalls);
        assertEquals(0, hooks.refreshPanelCalls);
        assertEquals(1, hooks.updateProfileHeaderCalls);
    }

    @Test
    public void timeoutSchedulesRetryAndDoesNotLogFailure() {
        TestHooks hooks = new TestHooks();
        hooks.clientLoggedIn = true;
        hooks.throwException = new IOException("timeout");
        hooks.timeoutException = true;
        LinkAttemptService service = new LinkAttemptService(hooks);

        service.attemptLink("key");

        assertEquals(1, hooks.linkDeviceCalls);
        assertEquals(1, hooks.logTimeoutCalls);
        assertEquals(1, hooks.scheduleRetryCalls);
        assertEquals(1, hooks.updateProfileHeaderCalls);
        assertEquals(0, hooks.logFailureCalls);
    }

    @Test
    public void nonTimeoutFailureLogsWarning() {
        TestHooks hooks = new TestHooks();
        hooks.clientLoggedIn = true;
        hooks.throwException = new RuntimeException("boom");
        hooks.timeoutException = false;
        LinkAttemptService service = new LinkAttemptService(hooks);

        service.attemptLink("key");

        assertEquals(1, hooks.linkDeviceCalls);
        assertEquals(0, hooks.logTimeoutCalls);
        assertEquals(0, hooks.scheduleRetryCalls);
        assertEquals(1, hooks.updateProfileHeaderCalls);
        assertEquals(1, hooks.logFailureCalls);
    }

    private static ApiClient.LinkResponse response(String token, String secret) {
        ApiClient.LinkResponse response = new ApiClient.LinkResponse();
        response.session_token = token;
        response.signing_secret = secret;
        return response;
    }

    private static final class TestHooks implements LinkAttemptService.Hooks {
        private boolean clientLoggedIn = false;
        private String deviceId = "device-123";
        private ApiClient.LinkResponse linkResponse;
        private Exception throwException;
        private boolean timeoutException;

        private int linkDeviceCalls;
        private int persistLinkedSessionCalls;
        private int resetUploadSnapshotCalls;
        private int resetUploadDiagnosticsCalls;
        private int updateUploadDiagnosticsUiCalls;
        private int requestBackfillCalls;
        private int scheduleAccountwideSyncCalls;
        private int refreshPanelCalls;
        private int updateProfileHeaderCalls;
        private int logTimeoutCalls;
        private int logFailureCalls;
        private int executeIoCalls;
        private int scheduleRetryCalls;

        private String lastLicenseKey;
        private String lastDeviceId;
        private String persistedSessionToken;
        private String persistedSigningSecret;

        @Override
        public boolean isClientLoggedIn() {
            return clientLoggedIn;
        }

        @Override
        public String currentDeviceId() {
            return deviceId;
        }

        @Override
        public ApiClient.LinkResponse linkDevice(String licenseKey, String deviceId) throws IOException {
            linkDeviceCalls++;
            lastLicenseKey = licenseKey;
            lastDeviceId = deviceId;
            if (throwException != null) {
                if (throwException instanceof IOException) {
                    throw (IOException) throwException;
                }
                if (throwException instanceof RuntimeException) {
                    throw (RuntimeException) throwException;
                }
                throw new RuntimeException(throwException);
            }
            return linkResponse;
        }

        @Override
        public void persistLinkedSession(String sessionToken, String signingSecret) {
            persistLinkedSessionCalls++;
            persistedSessionToken = sessionToken;
            persistedSigningSecret = signingSecret;
        }

        @Override
        public void resetAccountwideUploadSnapshot() {
            resetUploadSnapshotCalls++;
        }

        @Override
        public void resetUploadDiagnosticsState() {
            resetUploadDiagnosticsCalls++;
        }

        @Override
        public void updateUploadDiagnosticsUi() {
            updateUploadDiagnosticsUiCalls++;
        }

        @Override
        public void requestBackfillAttempt(long delaySeconds, boolean resetBackoff) {
            requestBackfillCalls++;
        }

        @Override
        public void scheduleAccountwideSync(long delaySeconds) {
            scheduleAccountwideSyncCalls++;
        }

        @Override
        public void refreshPanelData() {
            refreshPanelCalls++;
        }

        @Override
        public void updateProfileHeader() {
            updateProfileHeaderCalls++;
        }

        @Override
        public boolean isTimeoutException(Throwable ex) {
            return timeoutException;
        }

        @Override
        public void logTimeout() {
            logTimeoutCalls++;
        }

        @Override
        public void logFailure(Throwable ex) {
            logFailureCalls++;
        }

        @Override
        public void executeIo(Runnable task) {
            executeIoCalls++;
            if (task != null) {
                task.run();
            }
        }

        @Override
        public void scheduleRetry(Runnable task, long delaySeconds) {
            scheduleRetryCalls++;
        }
    }
}
