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
import org.slf4j.Logger;

final class EventUploader {
    private static final String UPLOAD_BLOCKED_UNLINKED =
        "Not linked. Pending uploads are buffered locally.";
    private static final String AUTH_RECOVERY_FAILED_MESSAGE =
        "Session refresh did not recover auth. Events queued for retry.";
    private static final String AUTH_RECOVERY_CLEAR_SESSION_LOG =
        "FlipHub refresh did not recover event upload auth; clearing session to force relink";

    interface Hooks {
        boolean isClientLoggedIn();
        int getPendingUploadEvents();
        GeEvent dequeueEvent();
        void requeue(List<GeEvent> batch);
        boolean attemptRefresh(String currentToken);
        void clearSession();
        boolean isPanelVisible();
        void updateProfileHeader();
        void setUploadBlocked(String reason);
        void recordUploadAttempt();
        void recordUploadSuccess(int uploadedCount, int statusCode);
        void recordUploadFailure(Integer statusCode, String errorMessage, boolean dropped, int droppedCount);
        void updateUploadDiagnosticsUi();
    }

    private EventUploader() {
    }

    static void flushEvents(ApiClient apiClient, PluginConfig config, int maxBatchSize, Hooks hooks, Logger log) {
        if (hooks == null || apiClient == null || config == null || log == null) {
            return;
        }
        if (!hooks.isClientLoggedIn()) {
            return;
        }
        String sessionToken = config.sessionToken();
        String signingSecret = config.signingSecret();
        if (!ApiStatusPolicy.hasCredentials(sessionToken, signingSecret)) {
            hooks.updateProfileHeader();
            if (hooks.getPendingUploadEvents() > 0) {
                hooks.setUploadBlocked(UPLOAD_BLOCKED_UNLINKED);
            } else {
                hooks.updateUploadDiagnosticsUi();
            }
            return;
        }

        List<GeEvent> batch = dequeueBatch(hooks, maxBatchSize);
        if (batch.isEmpty()) {
            hooks.updateUploadDiagnosticsUi();
            return;
        }

        hooks.recordUploadAttempt();
        try {
            int status = apiClient.sendEvents(sessionToken, signingSecret, batch);
            if (ApiStatusPolicy.isAuthStatus(status)) {
                handleAuthFailure(apiClient, config, hooks, log, batch, sessionToken, status);
                return;
            }
            handlePrimaryStatus(status, hooks, log, batch);
        } catch (IOException | RuntimeException ex) {
            hooks.requeue(batch);
            String message = ex.getMessage() != null ? ex.getMessage() : "Unknown upload exception";
            hooks.recordUploadFailure(-1, "Upload exception: " + message + ". Events queued for retry.", false, 0);
        }
    }

    private static void handleAuthFailure(ApiClient apiClient,
                                          PluginConfig config,
                                          Hooks hooks,
                                          Logger log,
                                          List<GeEvent> batch,
                                          String currentToken,
                                          int initialStatus) throws IOException {
        boolean refreshed = hooks.attemptRefresh(currentToken);
        if (refreshed) {
            String refreshedToken = config.sessionToken();
            String refreshedSecret = config.signingSecret();
            if (ApiStatusPolicy.hasCredentials(refreshedToken, refreshedSecret)) {
                int retryStatus = apiClient.sendEvents(refreshedToken, refreshedSecret, batch);
                handleRetryStatus(retryStatus, hooks, log, batch);
                return;
            }
        }

        hooks.requeue(batch);
        hooks.recordUploadFailure(initialStatus, AUTH_RECOVERY_FAILED_MESSAGE, false, 0);
        if (refreshed) {
            log.warn(AUTH_RECOVERY_CLEAR_SESSION_LOG);
        }
        hooks.clearSession();
        if (hooks.isPanelVisible()) {
            hooks.updateProfileHeader();
        }
    }

    private static void handleRetryStatus(int retryStatus, Hooks hooks, Logger log, List<GeEvent> batch) {
        if (retryStatus < 400) {
            hooks.updateProfileHeader();
            hooks.recordUploadSuccess(batch.size(), retryStatus);
            return;
        }
        if (ApiStatusPolicy.isAuthStatus(retryStatus)) {
            log.warn("FlipHub event upload unauthorized after refresh; clearing session to force relink");
            hooks.clearSession();
            if (hooks.isPanelVisible()) {
                hooks.updateProfileHeader();
            }
        }
        if (ApiStatusPolicy.isRetryableUploadStatus(retryStatus) || ApiStatusPolicy.isAuthStatus(retryStatus)) {
            hooks.requeue(batch);
            hooks.recordUploadFailure(
                retryStatus,
                "Upload rejected with status " + retryStatus + ". Events queued for retry.",
                false,
                0
            );
            return;
        }
        log.warn("FlipHub event upload failed after refresh with status {} (dropping {} events)",
            retryStatus, batch.size());
        hooks.recordUploadFailure(
            retryStatus,
            "Upload failed with status " + retryStatus + ". Events were dropped.",
            true,
            batch.size()
        );
    }

    private static void handlePrimaryStatus(int status, Hooks hooks, Logger log, List<GeEvent> batch) {
        if (ApiStatusPolicy.isRetryableUploadStatus(status)) {
            hooks.requeue(batch);
            hooks.recordUploadFailure(
                status,
                "Upload failed with status " + status + ". Events queued for retry.",
                false,
                0
            );
            return;
        }
        if (status >= 400) {
            log.warn("FlipHub event upload failed with status {} (dropping {} events)", status, batch.size());
            hooks.recordUploadFailure(
                status,
                "Upload failed with status " + status + ". Events were dropped.",
                true,
                batch.size()
            );
            return;
        }
        hooks.updateProfileHeader();
        hooks.recordUploadSuccess(batch.size(), status);
    }

    private static List<GeEvent> dequeueBatch(Hooks hooks, int maxBatchSize) {
        List<GeEvent> batch = new ArrayList<>(maxBatchSize);
        while (batch.size() < maxBatchSize) {
            GeEvent event = hooks.dequeueEvent();
            if (event == null) {
                break;
            }
            batch.add(event);
        }
        return batch;
    }
}
