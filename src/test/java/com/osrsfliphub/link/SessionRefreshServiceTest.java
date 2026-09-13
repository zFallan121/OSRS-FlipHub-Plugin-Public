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
import java.net.SocketTimeoutException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Only an outright refusal should cost the user their link. Everything else is a moment of bad
 * network, and because the licence key is wiped once linking succeeds, wrongly clearing the
 * session means going and finding that key again.
 */
public class SessionRefreshServiceTest {
    private static final String CURRENT_TOKEN = "token-in-hand";

    @Test
    public void freshCredentialsAreARefresh() {
        ApiClient.LinkResponse response = new ApiClient.LinkResponse();
        response.session_token = "brand-new-token";
        response.signing_secret = "brand-new-secret";
        SessionRefresh service = serviceReturning(response, null, CURRENT_TOKEN);

        assertEquals(SessionRefresh.Outcome.REFRESHED, service.attemptRefresh(CURRENT_TOKEN));
    }

    @Test
    public void anOutrightRefusalIsARejection() {
        SessionRefresh service =
            serviceReturning(null, new ApiClient.ApiException("Refresh failed", 401), CURRENT_TOKEN);

        assertEquals(SessionRefresh.Outcome.REJECTED, service.attemptRefresh(CURRENT_TOKEN));
    }

    @Test
    public void aTimeoutIsNotARefusal() {
        SessionRefresh service =
            serviceReturning(null, new SocketTimeoutException("read timed out"), CURRENT_TOKEN);

        assertEquals(SessionRefresh.Outcome.UNAVAILABLE, service.attemptRefresh(CURRENT_TOKEN));
    }

    /** A bad gateway is the host having a moment, not the session being revoked. */
    @Test
    public void aServerErrorIsNotARefusal() {
        SessionRefresh service =
            serviceReturning(null, new ApiClient.ApiException("Refresh failed", 502), CURRENT_TOKEN);

        assertEquals(SessionRefresh.Outcome.UNAVAILABLE, service.attemptRefresh(CURRENT_TOKEN));
    }

    /** A success that carries no token cannot be acted on, but it is not a refusal either. */
    @Test
    public void aSuccessWithNoTokenInItIsNotARefusal() {
        SessionRefresh service = serviceReturning(new ApiClient.LinkResponse(), null, CURRENT_TOKEN);

        assertEquals(SessionRefresh.Outcome.UNAVAILABLE, service.attemptRefresh(CURRENT_TOKEN));
    }

    /**
     * Two uploads can hit an expired token at once. The second one to arrive finds the stored
     * token already replaced, and asking again with the stale one would invite a refusal that
     * would then throw away the new credentials.
     */
    @Test
    public void aTokenAnotherTaskAlreadyReplacedNeedsNoSecondRefresh() {
        SessionRefresh service = serviceReturning(
            null,
            new ApiClient.ApiException("Refresh failed", 401),
            "token-someone-else-already-fetched"
        );

        assertEquals(SessionRefresh.Outcome.REFRESHED, service.attemptRefresh(CURRENT_TOKEN));
    }

    private static SessionRefresh serviceReturning(ApiClient.LinkResponse response,
                                                          IOException failure,
                                                          String storedToken) {
        ApiClient apiClient = new ApiClient(null, null, null) {
            @Override
            public LinkResponse refreshSession(String sessionToken, String signingSecret, String deviceId)
                throws IOException {
                if (failure != null) {
                    throw failure;
                }
                return response;
            }
        };
        return new SessionRefresh(apiClient, configWithToken(storedToken), null);
    }

    private static PluginConfig configWithToken(String storedToken) {
        return new PluginConfig() {
            @Override
            public boolean enableFlipHubSync() {
                return true;
            }

            @Override
            public String sessionToken() {
                return storedToken;
            }

            @Override
            public String signingSecret() {
                return "secret";
            }

            @Override
            public String deviceId() {
                return "device";
            }
        };
    }
}
