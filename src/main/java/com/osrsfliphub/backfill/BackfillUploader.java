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
    @Inject
    BackfillUploader() {
    }

    private static UploadEventDispatchFacadeService uploadEventDispatchFacade() {
        return PluginInjectorBridge.get(UploadEventDispatchFacadeService.class);
    }

    private boolean attemptRefresh(String currentToken) {
        return PluginInjectorBridge.get(SessionRefreshService.class).attemptRefresh(currentToken);
    }

    private void clearSession() {
        PluginInjectorBridge.get(SessionRefreshService.class).clearSession();
    }

    private void setUploadBlocked(String reason) {
        UploadEventDispatchFacadeService service = uploadEventDispatchFacade();
        if (service != null) {
            service.markBlocked(reason);
        }
    }

    private void recordUploadAttempt() {
        UploadEventDispatchFacadeService service = uploadEventDispatchFacade();
        if (service != null) {
            service.markAttempt();
        }
    }

    private void recordUploadSuccess(int uploadedCount, int statusCode) {
        UploadEventDispatchFacadeService service = uploadEventDispatchFacade();
        if (service != null) {
            service.markSuccess(uploadedCount, statusCode);
        }
    }

    private void recordUploadFailure(Integer statusCode, String errorMessage, boolean dropped, int droppedCount) {
        UploadEventDispatchFacadeService service = uploadEventDispatchFacade();
        if (service != null) {
            service.markFailure(statusCode, errorMessage, dropped, droppedCount);
        }
    }

    GeEvent buildBackfillEvent(long profileKey, LocalTradeDelta delta, Integer world) {
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
        event.event_type = delta.eventType != null && !delta.eventType.trim().isEmpty()
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

    boolean sendBatch(ApiClient apiClient, PluginConfig config, List<GeEvent> batch) {
        if (batch == null || batch.isEmpty() || apiClient == null || config == null) {
            return false;
        }
        if (!config.enableFlipHubSync()) {
            setUploadBlocked("Backfill paused: FlipHub sync is disabled in the plugin settings.");
            return false;
        }
        String sessionToken = config.sessionToken();
        String signingSecret = config.signingSecret();
        if (!ApiStatusPolicy.hasCredentials(sessionToken, signingSecret)) {
            setUploadBlocked("Backfill paused: plugin is not linked.");
            return false;
        }

        recordUploadAttempt();
        try {
            ApiClient.EventUploadResponse upload = apiClient.sendEventsDetailed(sessionToken, signingSecret, batch);
            int status = upload != null ? upload.status_code : 500;
            if (status < 400) {
                if (!isBackfillUploadUsable(upload, batch.size())) {
                    recordUploadFailure(
                        status,
                        "Backfill upload rejected all events in batch.",
                        false,
                        0
                    );
                    return false;
                }
                recordUploadSuccess(resolveBackfillUploadedCount(upload, batch.size()), status);
                return true;
            }
            if (ApiStatusPolicy.isAuthStatus(status)) {
                boolean refreshed = attemptRefresh(sessionToken);
                if (refreshed) {
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
                                    "Backfill upload rejected all events in batch.",
                                    false,
                                    0
                                );
                                return false;
                            }
                            recordUploadSuccess(resolveBackfillUploadedCount(retryUpload, batch.size()), retryStatus);
                            return true;
                        }
                        recordUploadFailure(retryStatus,
                            "Backfill upload failed with status " + retryStatus + ".", false, 0);
                        return false;
                    }
                }
                clearSession();
                recordUploadFailure(status, "Backfill upload unauthorized.", false, 0);
                return false;
            }
            recordUploadFailure(status, "Backfill upload failed with status " + status + ".", false, 0);
            return false;
        } catch (IOException | RuntimeException ex) {
            String message = ex != null && ex.getMessage() != null ? ex.getMessage() : "Unknown backfill exception";
            recordUploadFailure(-1, "Backfill upload exception: " + message, false, 0);
            return false;
        }
    }

    private boolean isBackfillUploadUsable(ApiClient.EventUploadResponse upload, int batchSize) {
        if (upload == null || batchSize <= 0) {
            return false;
        }
        int accepted = upload.accepted != null ? Math.max(0, upload.accepted) : 0;
        int duplicates = upload.duplicates != null ? Math.max(0, upload.duplicates) : 0;
        int rejected = upload.rejected != null ? Math.max(0, upload.rejected) : 0;
        if (accepted + duplicates > 0) {
            return true;
        }
        return rejected <= 0;
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
