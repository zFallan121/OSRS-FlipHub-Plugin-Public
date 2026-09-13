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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.GrandExchangeOfferState;

@Singleton
final class BackfillUploader {
    /**
     * What became of one batch, and therefore whether trying again can help.
     *
     * <p>A boolean could not tell "the server is having a moment" from "the server will never
     * take this". Both read as failure, so a batch the server had permanently refused was
     * retried every ninety seconds for as long as the client stayed open, and each cycle
     * re-sent every batch before it.
     */
    enum Outcome {
        /** Accepted. */
        SENT,
        /** Nobody refused it; the server could not be reached or is overloaded. */
        RETRY,
        /** The server refused this content. Sending the same events again changes nothing. */
        TERMINAL,
        /**
         * The session is dead and needs a relink. This says nothing about the events, so the
         * profile must not be written off; it used to be marked backfilled here, and after
         * the player relinked its remaining trades were never uploaded.
         */
        SESSION_DEAD
    }

    @Inject
    BackfillUploader() {
    }

    private SessionRefresh.Outcome attemptRefresh(String currentToken) {
        SessionRefresh service = Bridge.get(SessionRefresh.class);
        return service != null
            ? service.attemptRefresh(currentToken)
            : SessionRefresh.Outcome.UNAVAILABLE;
    }

    private void clearSession() {
        Bridge.get(SessionRefresh.class).clearSession();
    }

    private void setUploadBlocked(String reason) {
        UploadEventDispatch service = Bridge.get(UploadEventDispatch.class);
        if (service != null) {
            service.markBlocked(reason);
        }
    }

    private void recordUploadAttempt() {
        UploadEventDispatch service = Bridge.get(UploadEventDispatch.class);
        if (service != null) {
            service.markAttempt();
        }
    }

    private void recordUploadSuccess(int uploadedCount, int statusCode) {
        UploadEventDispatch service = Bridge.get(UploadEventDispatch.class);
        if (service != null) {
            service.markSuccess(uploadedCount, statusCode);
        }
    }

    private void recordUploadFailure(Integer statusCode, String errorMessage, boolean dropped, int droppedCount) {
        UploadEventDispatch service = Bridge.get(UploadEventDispatch.class);
        if (service != null) {
            service.markFailure(statusCode, errorMessage, dropped, droppedCount);
        }
    }

    GeEvent buildBackfillEvent(long profileKey, Delta delta, Integer world) {
        if (delta == null || delta.itemId <= 0 || delta.tsClientMs <= 0) {
            return null;
        }
        boolean isCompletion = "OFFER_COMPLETED".equals(delta.eventType);
        if (delta.deltaQty <= 0 && !isCompletion) {
            return null;
        }
        String signature = profileKey + "|" + delta.tsClientMs + "|" + delta.slot + "|" + delta.itemId + "|"
            + delta.isBuy + "|" + delta.deltaQty + "|" + delta.deltaGp + "|" + delta.price + "|"
            + (delta.eventType != null ? delta.eventType : "");
        GeEvent event = new GeEvent();
        event.event_id = UUID.nameUUIDFromBytes(signature.getBytes(StandardCharsets.UTF_8)).toString();
        event.event_type = Str.hasText(delta.eventType)
            ? delta.eventType
            : "OFFER_UPDATED";
        event.ts_client_ms = delta.tsClientMs;
        event.slot = Math.max(0, delta.slot);
        event.item_id = delta.itemId;
        event.is_buy = delta.isBuy;
        int qty = Math.max(0, delta.deltaQty);
        long deltaGp = Math.max(0L, delta.deltaGp);
        int fallbackPrice = qty > 0 ? (int) Math.max(1L, deltaGp / Math.max(1, qty)) : 1;
        event.price = delta.price > 0 ? delta.price : fallbackPrice;
        event.total_qty = qty;
        event.filled_qty = qty;
        event.spent_gp = deltaGp;
        event.delta_qty = qty;
        event.delta_gp = deltaGp;
        if ("OFFER_COMPLETED".equals(event.event_type)) {
            event.state = delta.isBuy ? GrandExchangeOfferState.BOUGHT.name() : GrandExchangeOfferState.SOLD.name();
        } else {
            event.state = delta.isBuy ? GrandExchangeOfferState.BUYING.name() : GrandExchangeOfferState.SELLING.name();
        }
        event.prev_state = null;
        event.world = world;
        event.schema_version = 1;
        return event;
    }

    Outcome sendBatch(ApiClient apiClient, PluginConfig config, List<GeEvent> batch) {
        if (batch == null || batch.isEmpty() || apiClient == null || config == null) {
            return Outcome.RETRY;
        }
        if (!config.enableFlipHubSync()) {
            // Paused rather than refused: turning sync back on should resume where this left off.
            setUploadBlocked("Backfill paused: FlipHub sync is disabled in the plugin settings.");
            return Outcome.RETRY;
        }
        String sessionToken = config.sessionToken();
        String signingSecret = config.signingSecret();
        if (!ApiStatusPolicy.hasCredentials(sessionToken, signingSecret)) {
            setUploadBlocked("Backfill paused: plugin is not linked.");
            return Outcome.RETRY;
        }

        recordUploadAttempt();
        try {
            ApiClient.EventUploadResponse upload = apiClient.sendEventsDetailed(sessionToken, signingSecret, batch);
            int status = upload != null ? upload.status_code : 500;
            if (status < 400) {
                if (!isBackfillUploadUsable(upload, batch.size())) {
                    // The server took the request and threw the contents away. Sending the same
                    // events again would get the same answer.
                    recordUploadFailure(
                        status,
                        "Backfill upload rejected every event in the batch. Not retrying.",
                        true,
                        batch.size()
                    );
                    return Outcome.TERMINAL;
                }
                recordUploadSuccess(resolveBackfillUploadedCount(upload, batch.size()), status);
                return Outcome.SENT;
            }
            if (ApiStatusPolicy.isAuthStatus(status)) {
                SessionRefresh.Outcome outcome = attemptRefresh(sessionToken);
                if (outcome == SessionRefresh.Outcome.REFRESHED) {
                    String refreshedToken = config.sessionToken();
                    String refreshedSecret = config.signingSecret();
                    if (ApiStatusPolicy.hasCredentials(refreshedToken, refreshedSecret)) {
                        ApiClient.EventUploadResponse retryUpload =
                            apiClient.sendEventsDetailed(refreshedToken, refreshedSecret, batch);
                        int retryStatus = retryUpload != null ? retryUpload.status_code : 500;
                        if (retryStatus < 400) {
                            if (!isBackfillUploadUsable(retryUpload, batch.size())) {
                                recordUploadFailure(
                                    retryStatus,
                                    "Backfill upload rejected every event in the batch. Not retrying.",
                                    true,
                                    batch.size()
                                );
                                return Outcome.TERMINAL;
                            }
                            recordUploadSuccess(resolveBackfillUploadedCount(retryUpload, batch.size()), retryStatus);
                            return Outcome.SENT;
                        }
                        recordUploadFailure(retryStatus,
                            "Backfill upload failed with status " + retryStatus + ".", false, 0);
                        return classify(retryStatus);
                    }
                }
                // clearSession has already run if the server refused the session; a refresh
                // that simply could not be completed leaves the link intact to try again.
                recordUploadFailure(status, outcome == SessionRefresh.Outcome.REJECTED
                    ? "Backfill upload unauthorized. Relink to resume."
                    : "Backfill upload could not refresh the session. Will retry.", false, 0);
                // A refused session needs a relink, not another attempt; an unreachable one is
                // worth coming back to. Neither is a verdict on this profile's events.
                return outcome == SessionRefresh.Outcome.REJECTED
                    ? Outcome.SESSION_DEAD : Outcome.RETRY;
            }
            recordUploadFailure(status, "Backfill upload failed with status " + status + ".", false, 0);
            return classify(status);
        } catch (IOException | RuntimeException ex) {
            String message = ex != null && ex.getMessage() != null ? ex.getMessage() : "Unknown backfill exception";
            recordUploadFailure(-1, "Backfill upload exception: " + message, false, 0);
            return Outcome.RETRY;
        }
    }

    /**
     * A rate limit or a server error is worth waiting out. Any other refusal in the four
     * hundreds is a statement about the request itself, which will not improve by repeating it.
     */
    private static Outcome classify(int status) {
        return ApiStatusPolicy.isRetryableUploadStatus(status) ? Outcome.RETRY : Outcome.TERMINAL;
    }

    private boolean isBackfillUploadUsable(ApiClient.EventUploadResponse upload, int batchSize) {
        return ApiStatusPolicy.keptSomething(upload, batchSize);
    }

    private int resolveBackfillUploadedCount(ApiClient.EventUploadResponse upload, int batchSize) {
        if (upload == null || batchSize <= 0) {
            return Math.max(0, batchSize);
        }
        int accepted = upload.accepted != null ? Math.max(0, upload.accepted) : 0;
        int duplicates = upload.duplicates != null ? Math.max(0, upload.duplicates) : 0;
        int handled = accepted + duplicates;
        if (handled > 0) {
            return handled;
        }
        return Math.max(0, batchSize);
    }
}
