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

import java.util.concurrent.ExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class WebsiteStatsWipeService {
    @Inject
    WebsiteStatsWipeService() {
    }

    private ProfileSelectionPresentationFacadeService facade() {
        return PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class);
    }

    private void runOnClientThread(Runnable task) {
        if (task != null) {
            PluginAccess.plugin().invokeOnClientThread(task);
        }
    }

    private void showError(String message) {
        PluginAccess.plugin().getProfileWorkflowService().showManageDataError(message);
    }

    private void pushGameMessage(String message) {
        PluginAccess.plugin().runtimeUtilityServices.pushGameMessage(PluginAccess.plugin().client, message);
    }

    void wipeWebsiteStatsAsync() {
        ProfileSelectionPresentationFacadeService facade = facade();
        if (facade == null || !facade.isLinked()) {
            showError("Website wipe is only available when linked.");
            return;
        }
        LinkSessionGuardService.Credentials credentials = facade.resolveLinkedCredentials();
        if (credentials == null) {
            showError("Website wipe failed: missing link credentials. Try relinking.");
            return;
        }
        ExecutorService executor = PluginAccess.plugin().ioExecutor;
        if (executor == null) {
            showError("Website wipe failed: IO executor is unavailable.");
            return;
        }

        executor.execute(() -> {
            try {
                ApiClient.WipeStatsResponse response = PluginAccess.plugin().wipeWebsiteStats(
                    credentials.sessionToken,
                    credentials.signingSecret
                );
                runOnClientThread(() -> {
                    if (response != null && response.status != null && response.status.equalsIgnoreCase("ok")) {
                        int events = response.deleted_trade_events != null ? response.deleted_trade_events : 0;
                        int lots = response.deleted_buy_lots != null ? response.deleted_buy_lots : 0;
                        int fills = response.deleted_flip_fills != null ? response.deleted_flip_fills : 0;
                        int summaries = response.deleted_accountwide_stats != null ? response.deleted_accountwide_stats : 0;
                        pushGameMessage(
                            "FlipHub website wipe: deleted " + events + " events, " + lots + " lots, "
                                + fills + " fills, " + summaries + " summaries."
                        );
                        PanelRefreshCoordinator coordinator = PluginAccess.plugin().getPanelRefreshCoordinator();
                        if (coordinator != null) {
                            coordinator.triggerStatsRefresh(PluginAccess.plugin().scheduler);
                        }
                    } else {
                        pushGameMessage("FlipHub website wipe failed: unexpected response.");
                    }
                });
            } catch (Exception ex) {
                String msg = ex != null && ex.getMessage() != null ? ex.getMessage() : "unknown error";
                runOnClientThread(() -> pushGameMessage("FlipHub website wipe failed: " + msg));
            }
        });
    }
}
