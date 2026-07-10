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
        if (uploadState == null) {
            return;
        }
        Predicate<String> refreshAttempt = this::attemptRefresh;
        EventUploader.flushEvents(
            apiClient,
            config,
            maxBatchSize,
            new EventUploaderRuntimeHooks(
                this::isClientLoggedIn,
                uploadState::getPendingUploadEvents,
                uploadState::dequeueEvent,
                this::requeue,
                refreshAttempt,
                this::clearSession,
                this::isPanelVisible,
                this::updateProfileHeader,
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
