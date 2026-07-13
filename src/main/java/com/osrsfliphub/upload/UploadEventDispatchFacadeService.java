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
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;

@Singleton
final class UploadEventDispatchFacadeService {
    private final UploadDiagnosticsState uploadState;
    private final int maxPendingUploadEvents = GeLifecyclePluginConstants.MAX_PENDING_UPLOAD_EVENTS;
    private final int maxBatchSize = GeLifecyclePluginConstants.MAX_BATCH_SIZE;

    @Inject
    UploadEventDispatchFacadeService(PluginState pluginState) {
        this.uploadState = pluginState.getUploadState();
    }

    private boolean isClientLoggedIn() {
        return PluginAccess.plugin().runtimeUtilityServices.isClientLoggedIn(PluginAccess.plugin().client);
    }

    private void requeue(List<GeEvent> batch) {
        PluginAccess.plugin().runtimeUtilityServices.requeue(this, batch);
    }

    private boolean attemptRefresh(String currentToken) {
        SessionRefreshService service = PluginInjectorBridge.get(SessionRefreshService.class);
        return service != null && service.attemptRefresh(currentToken);
    }

    private void clearSession() {
        SessionRefreshService service = PluginInjectorBridge.get(SessionRefreshService.class);
        if (service != null) {
            service.clearSession();
        }
    }

    private boolean isPanelVisible() {
        return PluginAccess.plugin().runtimeUtilityServices.isPanelVisible(PluginAccess.plugin().panel);
    }

    private void updateProfileHeader() {
        PluginAccess.plugin().getProfileWorkflowService().updateProfileHeader();
    }

    void enqueueEvent(GeEvent event) {
        if (uploadState == null) {
            return;
        }
        uploadState.enqueueEvent(event, maxPendingUploadEvents);
    }

    void resetStatus() {
        if (uploadState == null) {
            return;
        }
        uploadState.resetStatus();
        updateUploadDiagnosticsUi();
    }

    void markBlocked(String reason) {
        if (uploadState == null) {
            return;
        }
        uploadState.markBlocked(reason);
        updateUploadDiagnosticsUi();
    }

    void markAttempt() {
        if (uploadState == null) {
            return;
        }
        uploadState.markAttempt();
        updateUploadDiagnosticsUi();
    }

    void markSuccess(int uploadedCount, int statusCode) {
        if (uploadState == null) {
            return;
        }
        uploadState.markSuccess(uploadedCount, statusCode);
        updateUploadDiagnosticsUi();
    }

    void markFailure(Integer statusCode, String errorMessage, boolean dropped, int droppedCount) {
        if (uploadState == null) {
            return;
        }
        uploadState.markFailure(statusCode, errorMessage, dropped, droppedCount);
        updateUploadDiagnosticsUi();
    }

    void updateUploadDiagnosticsUi() {
        GeLifecyclePlugin plugin = PluginAccess.pluginOrNull();
        FlipHubPanel panel = plugin != null ? plugin.panel : null;
        if (panel != null) {
            panel.setUploadDiagnosticsTooltip(null);
        }
    }

    void flushEvents(ApiClient apiClient, PluginConfig config, Logger log) {
        if (uploadState == null || apiClient == null || config == null || log == null) {
            return;
        }
        if (!isClientLoggedIn()) {
            return;
        }
        if (!config.enableFlipHubSync()) {
            updateProfileHeader();
            if (uploadState.getPendingUploadEvents() > 0) {
                markBlocked("FlipHub sync is disabled in the plugin settings. Pending uploads are buffered locally.");
            } else {
                updateUploadDiagnosticsUi();
            }
            return;
        }
        String sessionToken = config.sessionToken();
        String signingSecret = config.signingSecret();
        if (!ApiStatusPolicy.hasCredentials(sessionToken, signingSecret)) {
            updateProfileHeader();
            if (uploadState.getPendingUploadEvents() > 0) {
                markBlocked("Not linked. Pending uploads are buffered locally.");
            } else {
                updateUploadDiagnosticsUi();
            }
            return;
        }

        List<GeEvent> batch = dequeueBatch();
        if (batch.isEmpty()) {
            updateUploadDiagnosticsUi();
            return;
        }

        markAttempt();
        try {
            int status = apiClient.sendEvents(sessionToken, signingSecret, batch);
            if (ApiStatusPolicy.isAuthStatus(status)) {
                handleAuthFailure(apiClient, config, log, batch, sessionToken, status);
                return;
            }
            handlePrimaryStatus(status, log, batch);
        } catch (IOException | RuntimeException ex) {
            requeue(batch);
            String message = ex.getMessage() != null ? ex.getMessage() : "Unknown upload exception";
            markFailure(-1, "Upload exception: " + message + ". Events queued for retry.", false, 0);
        }
    }

    private void handleAuthFailure(ApiClient apiClient,
                                   PluginConfig config,
                                   Logger log,
                                   List<GeEvent> batch,
                                   String currentToken,
                                   int initialStatus) throws IOException {
        boolean refreshed = attemptRefresh(currentToken);
        if (refreshed) {
            String refreshedToken = config.sessionToken();
            String refreshedSecret = config.signingSecret();
            if (ApiStatusPolicy.hasCredentials(refreshedToken, refreshedSecret)) {
                int retryStatus = apiClient.sendEvents(refreshedToken, refreshedSecret, batch);
                handleRetryStatus(retryStatus, log, batch);
                return;
            }
        }

        requeue(batch);
        markFailure(initialStatus, "Session refresh did not recover auth. Events queued for retry.", false, 0);
        if (refreshed) {
            log.warn("FlipHub refresh did not recover event upload auth; clearing session to force relink");
        }
        clearSession();
        if (isPanelVisible()) {
            updateProfileHeader();
        }
    }

    private void handleRetryStatus(int retryStatus, Logger log, List<GeEvent> batch) {
        if (retryStatus < 400) {
            updateProfileHeader();
            markSuccess(batch.size(), retryStatus);
            return;
        }
        if (ApiStatusPolicy.isAuthStatus(retryStatus)) {
            log.warn("FlipHub event upload unauthorized after refresh; clearing session to force relink");
            clearSession();
            if (isPanelVisible()) {
                updateProfileHeader();
            }
        }
        if (ApiStatusPolicy.isRetryableUploadStatus(retryStatus) || ApiStatusPolicy.isAuthStatus(retryStatus)) {
            requeue(batch);
            markFailure(
                retryStatus,
                "Upload rejected with status " + retryStatus + ". Events queued for retry.",
                false,
                0
            );
            return;
        }
        log.warn("FlipHub event upload failed after refresh with status {} (dropping {} events)",
            retryStatus, batch.size());
        markFailure(
            retryStatus,
            "Upload failed with status " + retryStatus + ". Events were dropped.",
            true,
            batch.size()
        );
    }

    private void handlePrimaryStatus(int status, Logger log, List<GeEvent> batch) {
        if (ApiStatusPolicy.isRetryableUploadStatus(status)) {
            requeue(batch);
            markFailure(
                status,
                "Upload failed with status " + status + ". Events queued for retry.",
                false,
                0
            );
            return;
        }
        if (status >= 400) {
            log.warn("FlipHub event upload failed with status {} (dropping {} events)", status, batch.size());
            markFailure(
                status,
                "Upload failed with status " + status + ". Events were dropped.",
                true,
                batch.size()
            );
            return;
        }
        updateProfileHeader();
        markSuccess(batch.size(), status);
    }

    private List<GeEvent> dequeueBatch() {
        List<GeEvent> batch = new ArrayList<>(maxBatchSize);
        while (batch.size() < maxBatchSize) {
            GeEvent event = uploadState.dequeueEvent();
            if (event == null) {
                break;
            }
            batch.add(event);
        }
        return batch;
    }
}
