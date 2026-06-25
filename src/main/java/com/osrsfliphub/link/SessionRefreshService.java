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

final class SessionRefreshService {
    private static final String DEFAULT_CONFIG_GROUP = FliphubConfigGroups.CONFIG_GROUP;
    private static final String SESSION_TOKEN_KEY = "sessionToken";
    private static final String SIGNING_SECRET_KEY = "signingSecret";
    private static final String SESSION_CLEARED_REASON =
        "Session cleared. Event uploads paused until relinked.";

    interface Hooks {
        ApiClient.LinkResponse refreshSession(String currentToken, String signingSecret, String deviceId) throws IOException;
        String getSigningSecret();
        String getDeviceId();
        void setConfiguration(String group, String key, String value);
        void resetAccountwideUploadSnapshot();
        void resetBackfillRetryState();
        void setUploadBlocked(String reason);
    }

    private final Hooks hooks;

    SessionRefreshService(Hooks hooks) {
        this.hooks = hooks;
    }

    boolean attemptRefresh(String currentToken) {
        if (hooks == null) {
            return false;
        }
        try {
            String signingSecret = hooks.getSigningSecret();
            String deviceId = hooks.getDeviceId();
            ApiClient.LinkResponse response = hooks.refreshSession(currentToken, signingSecret, deviceId);
            if (response != null && response.session_token != null) {
                hooks.setConfiguration(DEFAULT_CONFIG_GROUP, SESSION_TOKEN_KEY, response.session_token);
                if (response.signing_secret != null && !response.signing_secret.isEmpty()) {
                    hooks.setConfiguration(DEFAULT_CONFIG_GROUP, SIGNING_SECRET_KEY, response.signing_secret);
                }
                return true;
            }
        } catch (IOException | RuntimeException ex) {
            if (ApiStatusPolicy.isAuthorizationFailure(ex)) {
                clearSession();
            }
        }
        return false;
    }

    void clearSession() {
        if (hooks == null) {
            return;
        }
        hooks.setConfiguration(DEFAULT_CONFIG_GROUP, SESSION_TOKEN_KEY, "");
        hooks.setConfiguration(DEFAULT_CONFIG_GROUP, SIGNING_SECRET_KEY, "");
        hooks.resetAccountwideUploadSnapshot();
        hooks.resetBackfillRetryState();
        hooks.setUploadBlocked(SESSION_CLEARED_REASON);
    }
}
