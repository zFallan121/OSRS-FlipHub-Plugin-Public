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

final class SessionRefreshFactoryService {
    interface RuntimeHooks {
        ApiClient.LinkResponse refreshSession(String currentToken, String signingSecret, String deviceId) throws IOException;
        String getSigningSecret();
        String getDeviceId();
        void setConfiguration(String group, String key, String value);
        void resetAccountwideUploadSnapshot();
        void resetBackfillRetryState();
        void setUploadBlocked(String reason);
    }

    SessionRefreshService create(RuntimeHooks runtimeHooks) {
        if (runtimeHooks == null) {
            return new SessionRefreshService(null);
        }
        return new SessionRefreshService(new SessionRefreshService.Hooks() {
            @Override
            public ApiClient.LinkResponse refreshSession(String currentToken, String signingSecret, String deviceId)
                throws IOException {
                return runtimeHooks.refreshSession(currentToken, signingSecret, deviceId);
            }

            @Override
            public String getSigningSecret() {
                return runtimeHooks.getSigningSecret();
            }

            @Override
            public String getDeviceId() {
                return runtimeHooks.getDeviceId();
            }

            @Override
            public void setConfiguration(String group, String key, String value) {
                runtimeHooks.setConfiguration(group, key, value);
            }

            @Override
            public void resetAccountwideUploadSnapshot() {
                runtimeHooks.resetAccountwideUploadSnapshot();
            }

            @Override
            public void resetBackfillRetryState() {
                runtimeHooks.resetBackfillRetryState();
            }

            @Override
            public void setUploadBlocked(String reason) {
                runtimeHooks.setUploadBlocked(reason);
            }
        });
    }
}
