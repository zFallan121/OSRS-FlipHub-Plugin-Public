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
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

@Singleton
final class SessionRefreshService {
    private static final String DEFAULT_CONFIG_GROUP = FliphubConfigGroups.CONFIG_GROUP;
    private static final String SESSION_TOKEN_KEY = "sessionToken";
    private static final String SIGNING_SECRET_KEY = "signingSecret";
    private static final String SESSION_CLEARED_REASON =
        "Session cleared. Event uploads paused until relinked.";

    private final ApiClient apiClient;
    private final PluginConfig config;
    private final ConfigManager configManager;
    /** Serialises refreshes so two tasks on the IO pool cannot race with the same stale token. */
    private final Object refreshLock = new Object();

    @Inject
    SessionRefreshService(ApiClient apiClient, PluginConfig config, ConfigManager configManager) {
        this.apiClient = apiClient;
        this.config = config;
        this.configManager = configManager;
    }

    /**
     * What came of asking the server for a new session.
     */
    enum Outcome {
        /** New credentials are stored; retry the request. */
        REFRESHED,
        /** The server refused this session outright. Nothing but relinking will help. */
        REJECTED,
        /** Nobody said no; the server could not be reached or did not answer usefully. */
        UNAVAILABLE
    }

    Outcome attemptRefresh(String currentToken) {
        if (config == null || !config.enableFlipHubSync()) {
            return Outcome.UNAVAILABLE;
        }
        synchronized (refreshLock) {
            // Another task on the IO pool may have refreshed while this one waited. If the
            // stored token has moved on, that refresh is this one's answer too, and asking
            // again with the stale token would only invite a rejection.
            String storedToken = config.sessionToken();
            if (ApiStatusPolicy.hasText(storedToken) && !storedToken.equals(currentToken)) {
                return Outcome.REFRESHED;
            }
            try {
                ApiClient.LinkResponse response =
                    refreshSession(currentToken, config.signingSecret(), config.deviceId());
                if (response != null && response.session_token != null) {
                    // Secret first: a reader that sees the new token must not still be holding
                    // the old secret, or its signature will not verify.
                    if (response.signing_secret != null && !response.signing_secret.isEmpty()) {
                        setConfiguration(DEFAULT_CONFIG_GROUP, SIGNING_SECRET_KEY, response.signing_secret);
                    }
                    setConfiguration(DEFAULT_CONFIG_GROUP, SESSION_TOKEN_KEY, response.session_token);
                    return Outcome.REFRESHED;
                }
                // A 2xx with no token in it. Not a refusal, so hold on to what we have.
                return Outcome.UNAVAILABLE;
            } catch (ApiClient.ApiException ex) {
                if (ApiStatusPolicy.isAuthStatus(ex.statusCode)) {
                    clearSession();
                    return Outcome.REJECTED;
                }
                return Outcome.UNAVAILABLE;
            } catch (IOException | RuntimeException ex) {
                return Outcome.UNAVAILABLE;
            }
        }
    }

    void clearSession() {
        setConfiguration(DEFAULT_CONFIG_GROUP, SESSION_TOKEN_KEY, "");
        setConfiguration(DEFAULT_CONFIG_GROUP, SIGNING_SECRET_KEY, "");
        // Otherwise the account card keeps claiming "Linked" until something else repaints it.
        LinkStatusService linkStatus = PluginInjectorBridge.get(LinkStatusService.class);
        if (linkStatus != null) {
            linkStatus.refresh();
        }
        LinkSessionConfigStore store = PluginInjectorBridge.get(LinkSessionConfigStore.class);
        if (store != null) {
            store.flush();
        }
        AccountwideSummaryUploader uploader = PluginInjectorBridge.get(AccountwideSummaryUploader.class);
        if (uploader != null) {
            uploader.resetUploadSnapshot();
        }
        UploadBackfillDispatchService dispatch = PluginInjectorBridge.get(UploadBackfillDispatchService.class);
        if (dispatch != null) {
            dispatch.resetBackfillRetryState();
        }
        // The next link may be to a different website account, where "the first forty of this
        // profile are already there" is simply untrue and would skip them forever.
        AccountwideProfileBackfillService profileBackfill =
            PluginInjectorBridge.get(AccountwideProfileBackfillService.class);
        if (profileBackfill != null) {
            profileBackfill.clearResumePoints();
        }
        UploadEventDispatchFacadeService facade = PluginInjectorBridge.get(UploadEventDispatchFacadeService.class);
        if (facade != null) {
            facade.markBlocked(SESSION_CLEARED_REASON);
        }
    }

    private ApiClient.LinkResponse refreshSession(String currentToken, String signingSecret, String deviceId)
        throws IOException {
        if (apiClient == null) {
            throw new IllegalStateException("Refresh failed: api client unavailable");
        }
        return apiClient.refreshSession(currentToken, signingSecret, deviceId);
    }

    private void setConfiguration(String group, String key, String value) {
        if (configManager != null) {
            configManager.setConfiguration(group, key, value);
        }
    }
}
