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
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SessionRefreshServiceTest {
    @Test
    public void attemptRefreshSuccessUpdatesSessionAndSecret() throws Exception {
        TestHooks hooks = new TestHooks();
        hooks.refreshResponse = linkResponse("token-2", "secret-2");
        hooks.signingSecret = "sig-1";
        hooks.deviceId = "device-1";
        SessionRefreshService service = new SessionRefreshService(hooks);

        boolean refreshed = service.attemptRefresh("token-1");

        assertTrue(refreshed);
        assertEquals("token-1", hooks.lastRefreshToken);
        assertEquals("sig-1", hooks.lastRefreshSigningSecret);
        assertEquals("device-1", hooks.lastRefreshDeviceId);
        assertEquals(2, hooks.setConfigurationCalls.size());
        assertEquals("fliphub|sessionToken|token-2", hooks.setConfigurationCalls.get(0));
        assertEquals("fliphub|signingSecret|secret-2", hooks.setConfigurationCalls.get(1));
        assertEquals(0, hooks.resetUploadSnapshotCalls);
        assertEquals(0, hooks.resetBackfillRetryCalls);
    }

    @Test
    public void attemptRefreshSuccessWithoutSecretOnlyUpdatesSessionToken() throws Exception {
        TestHooks hooks = new TestHooks();
        hooks.refreshResponse = linkResponse("token-2", "");
        hooks.signingSecret = "";
        hooks.deviceId = "device-1";
        SessionRefreshService service = new SessionRefreshService(hooks);

        boolean refreshed = service.attemptRefresh("token-1");

        assertTrue(refreshed);
        assertEquals(1, hooks.setConfigurationCalls.size());
        assertEquals("fliphub|sessionToken|token-2", hooks.setConfigurationCalls.get(0));
    }

    @Test
    public void attemptRefreshUnauthorizedClearsSession() throws Exception {
        TestHooks hooks = new TestHooks();
        hooks.refreshException = new IOException("Refresh failed: 401");
        hooks.signingSecret = "sig-1";
        hooks.deviceId = "device-1";
        SessionRefreshService service = new SessionRefreshService(hooks);

        boolean refreshed = service.attemptRefresh("token-1");

        assertFalse(refreshed);
        assertEquals(2, hooks.setConfigurationCalls.size());
        assertEquals("fliphub|sessionToken|", hooks.setConfigurationCalls.get(0));
        assertEquals("fliphub|signingSecret|", hooks.setConfigurationCalls.get(1));
        assertEquals(1, hooks.resetUploadSnapshotCalls);
        assertEquals(1, hooks.resetBackfillRetryCalls);
        assertEquals(1, hooks.setUploadBlockedCalls);
        assertEquals("Session cleared. Event uploads paused until relinked.", hooks.lastBlockedReason);
    }

    @Test
    public void attemptRefreshApiExceptionUnauthorizedClearsSession() throws Exception {
        TestHooks hooks = new TestHooks();
        hooks.refreshException = new ApiClient.ApiException("Refresh failed", 403);
        hooks.signingSecret = "sig-1";
        hooks.deviceId = "device-1";
        SessionRefreshService service = new SessionRefreshService(hooks);

        boolean refreshed = service.attemptRefresh("token-1");

        assertFalse(refreshed);
        assertEquals(2, hooks.setConfigurationCalls.size());
        assertEquals("fliphub|sessionToken|", hooks.setConfigurationCalls.get(0));
        assertEquals("fliphub|signingSecret|", hooks.setConfigurationCalls.get(1));
        assertEquals(1, hooks.resetUploadSnapshotCalls);
        assertEquals(1, hooks.resetBackfillRetryCalls);
        assertEquals(1, hooks.setUploadBlockedCalls);
    }

    @Test
    public void attemptRefreshGenericFailureDoesNotClearSession() throws Exception {
        TestHooks hooks = new TestHooks();
        hooks.refreshException = new IOException("network down");
        hooks.signingSecret = "sig-1";
        hooks.deviceId = "device-1";
        SessionRefreshService service = new SessionRefreshService(hooks);

        boolean refreshed = service.attemptRefresh("token-1");

        assertFalse(refreshed);
        assertEquals(0, hooks.setConfigurationCalls.size());
        assertEquals(0, hooks.resetUploadSnapshotCalls);
        assertEquals(0, hooks.resetBackfillRetryCalls);
        assertEquals(0, hooks.setUploadBlockedCalls);
    }

    @Test
    public void attemptRefreshNumericNoiseDoesNotClearSession() throws Exception {
        TestHooks hooks = new TestHooks();
        hooks.refreshException = new IOException("network timeout after 4012ms");
        hooks.signingSecret = "sig-1";
        hooks.deviceId = "device-1";
        SessionRefreshService service = new SessionRefreshService(hooks);

        boolean refreshed = service.attemptRefresh("token-1");

        assertFalse(refreshed);
        assertEquals(0, hooks.setConfigurationCalls.size());
        assertEquals(0, hooks.resetUploadSnapshotCalls);
        assertEquals(0, hooks.resetBackfillRetryCalls);
        assertEquals(0, hooks.setUploadBlockedCalls);
    }

    @Test
    public void clearSessionAlwaysResetsStateAndBlocksUploads() {
        TestHooks hooks = new TestHooks();
        SessionRefreshService service = new SessionRefreshService(hooks);

        service.clearSession();

        assertEquals(2, hooks.setConfigurationCalls.size());
        assertEquals("fliphub|sessionToken|", hooks.setConfigurationCalls.get(0));
        assertEquals("fliphub|signingSecret|", hooks.setConfigurationCalls.get(1));
        assertEquals(1, hooks.resetUploadSnapshotCalls);
        assertEquals(1, hooks.resetBackfillRetryCalls);
        assertEquals(1, hooks.setUploadBlockedCalls);
    }

    private static ApiClient.LinkResponse linkResponse(String token, String secret) {
        ApiClient.LinkResponse response = new ApiClient.LinkResponse();
        response.session_token = token;
        response.signing_secret = secret;
        return response;
    }

    private static final class TestHooks implements SessionRefreshService.Hooks {
        private ApiClient.LinkResponse refreshResponse;
        private IOException refreshException;
        private String signingSecret;
        private String deviceId;
        private String lastRefreshToken;
        private String lastRefreshSigningSecret;
        private String lastRefreshDeviceId;
        private final List<String> setConfigurationCalls = new ArrayList<>();
        private int resetUploadSnapshotCalls;
        private int resetBackfillRetryCalls;
        private int setUploadBlockedCalls;
        private String lastBlockedReason;

        @Override
        public ApiClient.LinkResponse refreshSession(String currentToken, String signingSecret, String deviceId) throws IOException {
            lastRefreshToken = currentToken;
            lastRefreshSigningSecret = signingSecret;
            lastRefreshDeviceId = deviceId;
            if (refreshException != null) {
                throw refreshException;
            }
            return refreshResponse;
        }

        @Override
        public String getSigningSecret() {
            return signingSecret;
        }

        @Override
        public String getDeviceId() {
            return deviceId;
        }

        @Override
        public void setConfiguration(String group, String key, String value) {
            setConfigurationCalls.add(group + "|" + key + "|" + value);
        }

        @Override
        public void resetAccountwideUploadSnapshot() {
            resetUploadSnapshotCalls++;
        }

        @Override
        public void resetBackfillRetryState() {
            resetBackfillRetryCalls++;
        }

        @Override
        public void setUploadBlocked(String reason) {
            setUploadBlockedCalls++;
            lastBlockedReason = reason;
        }
    }
}
