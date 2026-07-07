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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
final class LinkAttemptService {
    private static final Logger log = LoggerFactory.getLogger(LinkAttemptService.class);
    private static final String USERNAME = System.getProperty("user.name");
    private static final String PLUGIN_VERSION = "1.0.0";

    interface Hooks {
        boolean isClientLoggedIn();
        String currentDeviceId();
        ApiClient.LinkResponse linkDevice(String licenseKey, String deviceId) throws IOException;
        void persistLinkedSession(String sessionToken, String signingSecret);
        void resetAccountwideUploadSnapshot();
        void resetUploadDiagnosticsState();
        void updateUploadDiagnosticsUi();
        void requestBackfillAttempt(long delaySeconds, boolean resetBackoff);
        void scheduleAccountwideSync(long delaySeconds);
        void refreshPanelData();
        void updateProfileHeader();
        boolean isTimeoutException(Throwable ex);
        void logTimeout();
        void logFailure(Throwable ex);
        void executeIo(Runnable task);
        void scheduleRetry(Runnable task, long delaySeconds);
    }

    private static final long RETRY_DELAY_SECONDS = 5L;
    private static final long POST_LINK_BACKFILL_DELAY_SECONDS = 5L;
    private static final long POST_LINK_SYNC_DELAY_SECONDS = 6L;

    private final Hooks hooks;

    @Inject
    LinkAttemptService(Client client, PluginConfig config, ApiClient apiClient) {
        this(productionHooks(client, config, apiClient));
    }

    LinkAttemptService(Hooks hooks) {
        this.hooks = hooks;
    }

    private static ScheduledExecutorService scheduler() {
        return PluginAccess.plugin().scheduler;
    }

    private static UploadBackfillDispatchService uploadBackfillDispatch() {
        return PluginInjectorBridge.get(UploadBackfillDispatchService.class);
    }

    private static UploadEventDispatchFacadeService uploadEventDispatchFacade() {
        return PluginInjectorBridge.get(UploadEventDispatchFacadeService.class);
    }

    private static Hooks productionHooks(Client client, PluginConfig config, ApiClient apiClient) {
        return new Hooks() {
            @Override
            public boolean isClientLoggedIn() {
                return client != null && client.getGameState() == GameState.LOGGED_IN;
            }

            @Override
            public String currentDeviceId() {
                return config != null ? config.deviceId() : null;
            }

            @Override
            public ApiClient.LinkResponse linkDevice(String licenseKey, String deviceId) throws IOException {
                return apiClient != null ? apiClient.linkDevice(licenseKey, deviceId, USERNAME, PLUGIN_VERSION) : null;
            }

            @Override
            public void persistLinkedSession(String sessionToken, String signingSecret) {
                PluginInjectorBridge.get(LinkSessionConfigStore.class).persistLinkedSession(sessionToken, signingSecret);
            }

            @Override
            public void resetAccountwideUploadSnapshot() {
                AccountwideSummaryUploader uploader =
                    PluginAccess.plugin().getBackfillServices().getAccountwideSummaryUploader();
                if (uploader != null) {
                    uploader.resetUploadSnapshot();
                }
            }

            @Override
            public void resetUploadDiagnosticsState() {
                UploadEventDispatchFacadeService service = uploadEventDispatchFacade();
                if (service != null) {
                    service.resetStatus();
                }
            }

            @Override
            public void updateUploadDiagnosticsUi() {
                UploadEventDispatchFacadeService service = uploadEventDispatchFacade();
                if (service != null) {
                    service.updateUploadDiagnosticsUi();
                }
            }

            @Override
            public void requestBackfillAttempt(long delaySeconds, boolean resetBackoff) {
                ScheduledExecutorService scheduler = scheduler();
                UploadBackfillDispatchService dispatch = uploadBackfillDispatch();
                if (scheduler != null && dispatch != null) {
                    dispatch.requestBackfillAttempt(scheduler, delaySeconds, resetBackoff);
                }
            }

            @Override
            public void scheduleAccountwideSync(long delaySeconds) {
                ScheduledExecutorService scheduler = scheduler();
                UploadBackfillDispatchService dispatch = uploadBackfillDispatch();
                if (scheduler != null && dispatch != null) {
                    scheduler.schedule(dispatch::requestAccountwideSync, delaySeconds, TimeUnit.SECONDS);
                }
            }

            @Override
            public void refreshPanelData() {
                PluginAccess.plugin().refreshPanelData();
            }

            @Override
            public void updateProfileHeader() {
                PluginAccess.plugin().getProfileWorkflowService().updateProfileHeader();
            }

            @Override
            public boolean isTimeoutException(Throwable ex) {
                Throwable current = ex;
                while (current != null) {
                    if (current instanceof SocketTimeoutException) {
                        return true;
                    }
                    current = current.getCause();
                }
                return false;
            }

            @Override
            public void logTimeout() {
                if (log.isDebugEnabled()) {
                    log.debug("FlipHub link timed out");
                }
            }

            @Override
            public void logFailure(Throwable ex) {
                log.warn("FlipHub link failed", ex);
            }

            @Override
            public void executeIo(Runnable task) {
                PluginAccess.plugin().executeIo(task);
            }

            @Override
            public void scheduleRetry(Runnable task, long delaySeconds) {
                ScheduledExecutorService scheduler = scheduler();
                if (scheduler != null && task != null) {
                    scheduler.schedule(task, delaySeconds, TimeUnit.SECONDS);
                }
            }
        };
    }

    String resolveLinkInput(String licenseKey, String linkCode) {
        if (!isBlank(licenseKey)) {
            return licenseKey;
        }
        return linkCode;
    }

    void attemptLink(String licenseKey) {
        if (hooks == null) {
            return;
        }
        String normalized = normalize(licenseKey);
        if (isBlank(normalized)) {
            return;
        }
        if (!hooks.isClientLoggedIn()) {
            hooks.updateProfileHeader();
            return;
        }

        hooks.executeIo(() -> runLinkAttempt(normalized));
    }

    private void runLinkAttempt(String licenseKey) {
        try {
            String deviceId = hooks.currentDeviceId();
            ApiClient.LinkResponse response = hooks.linkDevice(licenseKey, deviceId);
            if (response != null && hasText(response.session_token) && hasText(response.signing_secret)) {
                hooks.persistLinkedSession(response.session_token, response.signing_secret);
                hooks.resetAccountwideUploadSnapshot();
                hooks.resetUploadDiagnosticsState();
                hooks.updateUploadDiagnosticsUi();
                hooks.requestBackfillAttempt(POST_LINK_BACKFILL_DELAY_SECONDS, true);
                hooks.scheduleAccountwideSync(POST_LINK_SYNC_DELAY_SECONDS);
                hooks.refreshPanelData();
            }
            hooks.updateProfileHeader();
        } catch (IOException | RuntimeException ex) {
            if (hooks.isTimeoutException(ex)) {
                hooks.logTimeout();
                hooks.updateProfileHeader();
                scheduleRetry(licenseKey);
                return;
            }
            hooks.updateProfileHeader();
            hooks.logFailure(ex);
        }
    }

    private void scheduleRetry(String licenseKey) {
        if (hooks == null || isBlank(licenseKey)) {
            return;
        }
        hooks.scheduleRetry(() -> attemptLink(licenseKey), RETRY_DELAY_SECONDS);
    }

    private boolean hasText(String value) {
        return !isBlank(value);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
