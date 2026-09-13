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
final class UploadEventDispatch {
    /** First wait after a failure the server might recover from. */
    private static final long UPLOAD_BACKOFF_INITIAL_MS = 5_000L;
    /** Longest the flush will wait before trying again. */
    private static final long UPLOAD_BACKOFF_MAX_MS = 5L * 60L * 1000L;
    private final UploadDiagnosticsState uploadState;
    private final int maxPendingUploadEvents = Const.MAX_PENDING_UPLOAD_EVENTS;
    private final int maxBatchSize = Const.MAX_BATCH_SIZE;

    @Inject
    UploadEventDispatch(PluginState pluginState) {
        this.uploadState = pluginState.getUploadState();
    }

    private boolean isClientLoggedIn() {
        return Access.plugin().runtimeUtilityServices.isClientLoggedIn(Access.plugin().client);
    }

    private void backOff() {
        if (uploadState != null) {
            uploadState.backOff(System.currentTimeMillis(), UPLOAD_BACKOFF_INITIAL_MS, UPLOAD_BACKOFF_MAX_MS);
        }
    }

    private void requeue(List<GeEvent> batch) {
        Access.plugin().runtimeUtilityServices.requeue(this, batch);
    }

    private SessionRefresh.Outcome attemptRefresh(String currentToken) {
        SessionRefresh service = Bridge.get(SessionRefresh.class);
        return service != null
            ? service.attemptRefresh(currentToken)
            : SessionRefresh.Outcome.UNAVAILABLE;
    }

    private void clearSession() {
        SessionRefresh service = Bridge.get(SessionRefresh.class);
        if (service != null) {
            service.clearSession();
        }
    }

    private boolean isPanelVisible() {
        return Access.plugin().runtimeUtilityServices.isPanelVisible(Access.plugin().panel);
    }

    private void updateProfileHeader() {
        Access.plugin().getProfileWorkflowService().updateProfileHeader();
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
        GeLifecyclePlugin plugin = Access.pluginOrNull();
        Panel panel = plugin != null ? plugin.panel : null;
        if (panel == null) {
            return;
        }
        // The state has always known how to describe itself; this used to pass null instead,
        // so "oldest events were dropped" and "session cleared" were built and thrown away and
        // the player was never told either.
        PluginConfig config = plugin.config;
        boolean linked = config != null
            && ApiStatusPolicy.hasCredentials(config.sessionToken(), config.signingSecret());
        panel.setUploadDiagnosticsTooltip(uploadState != null ? uploadState.buildTooltip(linked) : null);
    }

    /**
     * One last drain as the client closes. The queue lives only in memory, so anything still in
     * it when the process ends is gone: the events are already recorded locally, but the
     * website never hears about them, and once a profile is marked backfilled nothing will
     * resend them. Bounded, and stops the moment a pass makes no progress.
     */
    void flushPendingBeforeShutdown(ApiClient apiClient, PluginConfig config, Logger log, int maxBatches) {
        if (uploadState == null) {
            return;
        }
        // Whatever wait was in force, this is the last chance to use it up.
        uploadState.clearBackOff();
        for (int attempt = 0; attempt < maxBatches && uploadState.getPendingUploadEvents() > 0; attempt++) {
            int before = uploadState.getPendingUploadEvents();
            flushEvents(apiClient, config, log, false);
            if (uploadState.getPendingUploadEvents() >= before) {
                return;
            }
        }
    }

    void flushEvents(ApiClient apiClient, PluginConfig config, Logger log) {
        flushEvents(apiClient, config, log, true);
    }

    /**
     * @param onlyWhileLoggedIn the routine flush waits for a logged-in client, because the
     *                          events describe the account that is playing. The drain as the
     *                          client closes must not: a player who logs out to the lobby and
     *                          then quits used to have their queued trades thrown away, and
     *                          once a profile is marked backfilled nothing resends them.
     */
    private void flushEvents(ApiClient apiClient, PluginConfig config, Logger log,
                             boolean onlyWhileLoggedIn) {
        if (uploadState == null || apiClient == null || config == null || log == null) {
            return;
        }
        if (onlyWhileLoggedIn && !isClientLoggedIn()) {
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

        if (uploadState.isBackingOff(System.currentTimeMillis())) {
            // Still waiting out an earlier failure. The events stay queued.
            updateUploadDiagnosticsUi();
            return;
        }

        List<GeEvent> batch = dequeueBatch();
        if (batch.isEmpty()) {
            updateUploadDiagnosticsUi();
            return;
        }

        markAttempt();
        try {
            ApiClient.EventUploadResponse upload =
                apiClient.sendEventsDetailed(sessionToken, signingSecret, batch);
            int status = upload != null ? upload.status_code : -1;
            if (ApiStatusPolicy.isAuthStatus(status)) {
                handleAuthFailure(apiClient, config, log, batch, sessionToken, status);
                return;
            }
            handlePrimaryStatus(status, upload, log, batch);
        } catch (IOException | RuntimeException ex) {
            requeue(batch);
            backOff();
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
        SessionRefresh.Outcome outcome = attemptRefresh(currentToken);
        if (outcome == SessionRefresh.Outcome.REFRESHED) {
            String refreshedToken = config.sessionToken();
            String refreshedSecret = config.signingSecret();
            if (ApiStatusPolicy.hasCredentials(refreshedToken, refreshedSecret)) {
                ApiClient.EventUploadResponse retryUpload =
                    apiClient.sendEventsDetailed(refreshedToken, refreshedSecret, batch);
                handleRetryStatus(retryUpload != null ? retryUpload.status_code : -1,
                    retryUpload, log, batch);
                return;
            }
        }

        requeue(batch);
        if (outcome == SessionRefresh.Outcome.REJECTED) {
            // The server refused the session itself, so the link is genuinely dead and
            // clearSession has already said so. Retrying would only repeat the refusal.
            markFailure(initialStatus, "Session was rejected. Relink to resume uploads.", false, 0);
            log.warn("FlipHub session was rejected on refresh; relink required");
        } else {
            // Nobody refused anything: the refresh could not be completed. The credentials are
            // very probably still good, so they stay put and the batch waits for the next tick.
            backOff();
            markFailure(initialStatus, "Could not reach FlipHub to refresh the session. Events queued for retry.",
                false, 0);
        }
        if (isPanelVisible()) {
            updateProfileHeader();
        }
    }

    private void handleRetryStatus(int retryStatus, ApiClient.EventUploadResponse upload,
                                   Logger log, List<GeEvent> batch) {
        if (retryStatus < 400) {
            if (!ApiStatusPolicy.keptSomething(upload, batch.size())) {
                reportNothingKept(retryStatus, log, batch);
                return;
            }
            updateProfileHeader();
            // A run of failures can leave a five minute wait standing. This one worked.
            uploadState.clearBackOff();
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
            // Without this the batch was retried on every flush tick, two seconds apart, for
            // as long as the server kept saying no.
            backOff();
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

    private void handlePrimaryStatus(int status, ApiClient.EventUploadResponse upload,
                                     Logger log, List<GeEvent> batch) {
        if (ApiStatusPolicy.isRetryableUploadStatus(status)) {
            requeue(batch);
            backOff();
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
        if (!ApiStatusPolicy.keptSomething(upload, batch.size())) {
            reportNothingKept(status, log, batch);
            return;
        }
        updateProfileHeader();
        uploadState.clearBackOff();
        markSuccess(batch.size(), status);
    }

    /**
     * The server took the request and threw every event in it away. Sending the same events
     * again gets the same answer, so they are dropped rather than queued, and the player is
     * told, instead of being shown a success that did not happen.
     */
    private void reportNothingKept(int status, Logger log, List<GeEvent> batch) {
        log.warn("FlipHub accepted the request and rejected all {} events", batch.size());
        markFailure(
            status,
            "FlipHub rejected every event in that batch. They were not uploaded.",
            true,
            batch.size()
        );
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
