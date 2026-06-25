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

import java.util.List;
import java.util.function.Predicate;
import org.slf4j.Logger;

final class UploadEventDispatchFacadeService {
    interface Hooks {
        boolean isClientLoggedIn();
        void requeue(List<GeEvent> batch);
        boolean attemptRefresh(String currentToken);
        void clearSession();
        boolean isPanelVisible();
        void updateProfileHeader();
        void clearUploadDiagnosticsTooltip();
    }

    private final UploadDiagnosticsState uploadState;
    private final int maxPendingUploadEvents;
    private final int maxBatchSize;
    private final Hooks hooks;

    UploadEventDispatchFacadeService(UploadDiagnosticsState uploadState,
                                     int maxPendingUploadEvents,
                                     int maxBatchSize,
                                     Hooks hooks) {
        this.uploadState = uploadState;
        this.maxPendingUploadEvents = maxPendingUploadEvents;
        this.maxBatchSize = maxBatchSize;
        this.hooks = hooks;
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
        if (hooks != null) {
            hooks.clearUploadDiagnosticsTooltip();
        }
    }

    void flushEvents(ApiClient apiClient, PluginConfig config, Logger log) {
        if (uploadState == null) {
            return;
        }
        Predicate<String> refreshAttempt = token -> hooks != null && hooks.attemptRefresh(token);
        EventUploader.flushEvents(
            apiClient,
            config,
            maxBatchSize,
            new EventUploaderRuntimeHooks(
                () -> hooks != null && hooks.isClientLoggedIn(),
                uploadState::getPendingUploadEvents,
                uploadState::dequeueEvent,
                batch -> {
                    if (hooks != null) {
                        hooks.requeue(batch);
                    }
                },
                refreshAttempt,
                () -> {
                    if (hooks != null) {
                        hooks.clearSession();
                    }
                },
                () -> hooks != null && hooks.isPanelVisible(),
                () -> {
                    if (hooks != null) {
                        hooks.updateProfileHeader();
                    }
                },
                this::markBlocked,
                this::markAttempt,
                this::markSuccess,
                this::markFailure,
                this::updateUploadDiagnosticsUi
            ),
            log
        );
    }
}
