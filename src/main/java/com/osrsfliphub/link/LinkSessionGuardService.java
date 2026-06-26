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

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class LinkSessionGuardService {
    interface Hooks {
        String getSessionToken();
        String getSigningSecret();
    }

    static final class Credentials {
        final String sessionToken;
        final String signingSecret;

        Credentials(String sessionToken, String signingSecret) {
            this.sessionToken = sessionToken;
            this.signingSecret = signingSecret;
        }
    }

    private final Hooks hooks;

    @Inject
    LinkSessionGuardService(PluginConfig config) {
        this(new Hooks() {
            @Override
            public String getSessionToken() {
                return config != null ? config.sessionToken() : null;
            }

            @Override
            public String getSigningSecret() {
                return config != null ? config.signingSecret() : null;
            }
        });
    }

    LinkSessionGuardService(Hooks hooks) {
        this.hooks = hooks;
    }

    boolean hasSessionToken() {
        return !isBlank(readSessionToken());
    }

    boolean isLinked() {
        return !isBlank(readSessionToken()) && !isBlank(readSigningSecret());
    }

    Credentials resolveLinkedCredentials() {
        String token = normalize(readSessionToken());
        String secret = normalize(readSigningSecret());
        if (isBlank(token) || isBlank(secret)) {
            return null;
        }
        return new Credentials(token, secret);
    }

    private String readSessionToken() {
        return hooks != null ? hooks.getSessionToken() : null;
    }

    private String readSigningSecret() {
        return hooks != null ? hooks.getSigningSecret() : null;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
