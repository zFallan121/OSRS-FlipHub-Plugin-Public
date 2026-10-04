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
import java.util.*;
import javax.inject.*;
import net.runelite.api.Client;
import org.slf4j.Logger;

@Singleton
final class UploadEventDispatch {
    static final int MAX_PENDING_UPLOAD_EVENTS = 10_000;

    /** First wait after a failure the server might recover from. */
    private static final long UPLOAD_BACKOFF_INITIAL_MS = 5_000L;
    /** Longest the flush will wait before trying again. */
    private static final long UPLOAD_BACKOFF_MAX_MS = 5L * 60L * 1000L;
    private static final Logger log = GeLifecyclePlugin.log;
    private final Client client;
    private final UploadDiagnosticsState uploadState;
    /**
     * When the batch now on its way left the queue ({@link SiteFigures#uploaded}), by the clock
     * every trade's own time is told by ({@link GeEvent#createBase}).
     */
    private volatile long takenMs;

    @Inject
    UploadEventDispatch(PluginState pluginState, Client client) {
        this.client = client;
        this.uploadState = pluginState.getUploadState();
    }

    private void backOff() {
        uploadState.backOff(System.currentTimeMillis(), UPLOAD_BACKOFF_INITIAL_MS, UPLOAD_BACKOFF_MAX_MS);
    }

    private void requeue(List<GeEvent> batch) {
        batch.forEach(this::enqueueEvent);
    }

    private void updateProfileHeader() {
        Access.plugin().getProfileWorkflowService().updateProfileHeader();
    }

    void enqueueEvent(GeEvent event) {
        uploadState.enqueueEvent(event, MAX_PENDING_UPLOAD_EVENTS);
    }

    void resetStatus() {
        uploadState.resetStatus();
        updateUploadDiagnosticsUi();
    }

    void markBlocked(String reason) {
        uploadState.markBlocked(reason);
        updateUploadDiagnosticsUi();
    }

    void markAttempt() {
        uploadState.markAttempt();
        updateUploadDiagnosticsUi();
    }

    void markSuccess(int uploadedCount, int statusCode) {
        uploadState.markSuccess(uploadedCount, statusCode);
        updateUploadDiagnosticsUi();
        // What the website has just taken has moved its figures, and a linked plugin shows those.
        SiteFigures figures = Bridge.get(SiteFigures.class);
        if (figures != null) {
            try {
                figures.uploaded(takenMs);
            } catch (RuntimeException ex) {
                // The website has the batch. Whatever went wrong in asking for its figures must
                // not reach the flush, which would queue the batch again and send it twice.
            }
        }
    }

    void markFailure(Integer statusCode, String errorMessage, int droppedCount) {
        uploadState.markFailure(statusCode, errorMessage, droppedCount);
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
        panel.setUploadDiagnosticsTooltip(uploadState.buildTooltip(linked));
    }

    /**
     * One last drain as the client closes. The queue lives only in memory, so anything still in
     * it when the process ends is gone from it: the trades are recorded locally and are sent
     * from the file next session ({@link RecordSync}), but this saves the website the wait.
     * Bounded, and stops the moment a pass makes no progress.
     */
    void flushPendingBeforeShutdown(ApiClient apiClient, PluginConfig config, int maxBatches) {
        // Whatever wait was in force, this is the last chance to use it up.
        uploadState.clearBackOff();
        for (int attempt = 0; attempt < maxBatches && uploadState.getPendingUploadEvents() > 0; attempt++) {
            int before = uploadState.getPendingUploadEvents();
            flushEvents(apiClient, config, false);
            if (uploadState.getPendingUploadEvents() >= before) {
                return;
            }
        }
    }

    void flushEvents(ApiClient apiClient, PluginConfig config) {
        flushEvents(apiClient, config, true);
    }

    /**
     * @param onlyWhileLoggedIn the routine flush waits for a logged-in client, because the
     *                          events describe the account that is playing. The drain as the
     *                          client closes must not: a player who logs out to the lobby and
     *                          then quits used to have their queued trades thrown away.
     */
    private void flushEvents(ApiClient apiClient, PluginConfig config, boolean onlyWhileLoggedIn) {
        if (apiClient == null || config == null) {
            return;
        }
        if (onlyWhileLoggedIn && !Access.loggedIn(client)) {
            return;
        }
        String sessionToken = config.sessionToken();
        String signingSecret = config.signingSecret();
        String blocked = !config.enableFlipHubSync()
            ? "FlipHub sync is disabled in the plugin settings. Pending uploads are buffered locally."
            : ApiStatusPolicy.hasCredentials(sessionToken, signingSecret) ? null
            : "Not linked. Pending uploads are buffered locally.";
        if (blocked != null) {
            updateProfileHeader();
            if (uploadState.getPendingUploadEvents() > 0) {
                markBlocked(blocked);
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

        // Raised before the batch leaves the queue and lowered only once it has been answered,
        // dropped or put back. In between the queue reads empty while its trades are still on
        // their way, and a stored trade sent then would be judged without them (RecordSync).
        uploadState.flushing.incrementAndGet();
        try {
            // Noted before the batch is taken, so that a sale whose event was made by then is in
            // it: an event is queued as soon as it is made, well before its trade is filed. Only one
            // flush runs at a time (UploadBackfillDispatch), but for the last ones as the client closes.
            takenMs = System.currentTimeMillis();
            List<GeEvent> batch = dequeueBatch();
            if (batch.isEmpty()) {
                updateUploadDiagnosticsUi();
                return;
            }

            markAttempt();
            try {
                ApiClient.EventUploadResponse upload =
                    apiClient.sendEventsDetailed(sessionToken, signingSecret, batch);
                // Not if a new link was made while it was on its way (RecordSync.linked).
                uploadState.answered(upload, sessionToken.equals(config.sessionToken()));
                int status = upload.status_code;
                if (ApiStatusPolicy.isAuthStatus(status)) {
                    handleAuthFailure(apiClient, config, batch, sessionToken, status);
                    return;
                }
                handlePrimaryStatus(status, upload, batch);
            } catch (IOException | RuntimeException ex) {
                requeue(batch);
                backOff();
                String message = ex.getMessage() != null ? ex.getMessage() : "Unknown upload exception";
                markFailure(-1, "Upload exception: " + message + ". Events queued for retry.", 0);
            }
        } finally {
            uploadState.flushing.decrementAndGet();
        }
    }

    private void handleAuthFailure(ApiClient apiClient,
                                   PluginConfig config,
                                   List<GeEvent> batch,
                                   String currentToken,
                                   int initialStatus) throws IOException {
        SessionRefresh.Outcome outcome = SessionRefresh.refreshOrUnavailable(currentToken);
        if (outcome == SessionRefresh.Outcome.REFRESHED) {
            String refreshedToken = config.sessionToken();
            String refreshedSecret = config.signingSecret();
            if (ApiStatusPolicy.hasCredentials(refreshedToken, refreshedSecret)) {
                ApiClient.EventUploadResponse retryUpload =
                    apiClient.sendEventsDetailed(refreshedToken, refreshedSecret, batch);
                uploadState.answered(retryUpload, refreshedToken.equals(config.sessionToken()));
                handleRetryStatus(retryUpload.status_code,
                    retryUpload, batch);
                return;
            }
        }

        requeue(batch);
        if (outcome == SessionRefresh.Outcome.REJECTED) {
            // The server refused the session itself, so the link is genuinely dead and
            // clearSession has already said so. Retrying would only repeat the refusal.
            markFailure(initialStatus, "Session was rejected. Relink to resume uploads.", 0);
            log.warn("FlipHub session was rejected on refresh; relink required");
        } else {
            // Nobody refused anything: the refresh could not be completed. The credentials are
            // very probably still good, so they stay put and the batch waits for the next tick.
            backOff();
            markFailure(initialStatus, "Could not reach FlipHub to refresh the session. Events queued for retry.",
                0);
        }
        if (Access.plugin().runtimeUtilityServices.isPanelVisible(Access.plugin().panel)) {
            updateProfileHeader();
        }
    }

    private void handleRetryStatus(int retryStatus, ApiClient.EventUploadResponse upload,
                                   List<GeEvent> batch) {
        if (retryStatus < 400) {
            if (!ApiStatusPolicy.keptSomething(upload, batch.size())) {
                reportNothingKept(retryStatus, batch);
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
            SessionRefresh.clearIfRunning();
            if (Access.plugin().runtimeUtilityServices.isPanelVisible(Access.plugin().panel)) {
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
                0);
            return;
        }
        log.warn("FlipHub event upload failed after refresh with status {} (dropping {} events)",
            retryStatus, batch.size());
        markFailure(
            retryStatus,
            "Upload failed with status " + retryStatus + ". Events were dropped.",
            batch.size());
    }

    private void handlePrimaryStatus(int status, ApiClient.EventUploadResponse upload,
                                     List<GeEvent> batch) {
        if (ApiStatusPolicy.isRetryableUploadStatus(status)) {
            requeue(batch);
            backOff();
            markFailure(
                status,
                "Upload failed with status " + status + ". Events queued for retry.",
                0);
            return;
        }
        if (status >= 400) {
            log.warn("FlipHub event upload failed with status {} (dropping {} events)", status, batch.size());
            // The website refuses a request stamped more than five minutes from its own time, and
            // "status 400" told the player nothing they could act on. The trades are not lost:
            // each is in the file, and is sent from there until the website confirms it.
            markFailure(
                status,
                "Timestamp out of range".equals(upload.error)
                    ? "Your PC clock is more than five minutes off; trades will be sent when it is right"
                    : "Upload failed with status " + status + ". Events were dropped.",
                batch.size());
            return;
        }
        if (!ApiStatusPolicy.keptSomething(upload, batch.size())) {
            reportNothingKept(status, batch);
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
    private void reportNothingKept(int status, List<GeEvent> batch) {
        log.warn("FlipHub accepted the request and rejected all {} events", batch.size());
        markFailure(
            status,
            "FlipHub rejected every event in that batch. They were not uploaded.",
            batch.size());
    }

    private List<GeEvent> dequeueBatch() {
        List<GeEvent> batch = new ArrayList<>(Const.MAX_BATCH_SIZE);
        while (batch.size() < Const.MAX_BATCH_SIZE) {
            GeEvent event = uploadState.dequeueEvent();
            if (event == null) {
                break;
            }
            batch.add(event);
        }
        return batch;
    }
}
