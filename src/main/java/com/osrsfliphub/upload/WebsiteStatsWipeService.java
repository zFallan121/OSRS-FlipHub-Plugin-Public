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

final class WebsiteStatsWipeService {
    interface Hooks {
        boolean isLinked();
        LinkSessionGuardService.Credentials resolveLinkedCredentials();
        boolean hasIoExecutor();
        void executeIo(Runnable task);
        void runOnClientThread(Runnable task);
        ApiClient.WipeStatsResponse wipeWebsiteStats(String sessionToken, String signingSecret) throws Exception;
        void showError(String message);
        void pushGameMessage(String message);
        void triggerStatsRefresh();
    }

    private final Hooks hooks;

    WebsiteStatsWipeService(Hooks hooks) {
        this.hooks = hooks;
    }

    void wipeWebsiteStatsAsync() {
        if (hooks == null) {
            return;
        }
        if (!hooks.isLinked()) {
            hooks.showError("Website wipe is only available when linked.");
            return;
        }
        LinkSessionGuardService.Credentials credentials = hooks.resolveLinkedCredentials();
        if (credentials == null) {
            hooks.showError("Website wipe failed: missing link credentials. Try relinking.");
            return;
        }
        if (!hooks.hasIoExecutor()) {
            hooks.showError("Website wipe failed: IO executor is unavailable.");
            return;
        }

        hooks.executeIo(() -> {
            try {
                ApiClient.WipeStatsResponse response = hooks.wipeWebsiteStats(
                    credentials.sessionToken,
                    credentials.signingSecret
                );
                hooks.runOnClientThread(() -> {
                    if (response != null && response.status != null && response.status.equalsIgnoreCase("ok")) {
                        int events = response.deleted_trade_events != null ? response.deleted_trade_events : 0;
                        int lots = response.deleted_buy_lots != null ? response.deleted_buy_lots : 0;
                        int fills = response.deleted_flip_fills != null ? response.deleted_flip_fills : 0;
                        int summaries = response.deleted_accountwide_stats != null ? response.deleted_accountwide_stats : 0;
                        hooks.pushGameMessage(
                            "FlipHub website wipe: deleted " + events + " events, " + lots + " lots, "
                                + fills + " fills, " + summaries + " summaries."
                        );
                        hooks.triggerStatsRefresh();
                    } else {
                        hooks.pushGameMessage("FlipHub website wipe failed: unexpected response.");
                    }
                });
            } catch (Exception ex) {
                String msg = ex != null && ex.getMessage() != null ? ex.getMessage() : "unknown error";
                hooks.runOnClientThread(() -> hooks.pushGameMessage("FlipHub website wipe failed: " + msg));
            }
        });
    }
}
