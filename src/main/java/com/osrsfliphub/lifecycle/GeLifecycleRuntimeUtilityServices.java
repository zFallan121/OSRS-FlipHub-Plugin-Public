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
import java.util.List;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;

final class GeLifecycleRuntimeUtilityServices {
    void scheduleRefreshSoon(PanelRefreshCoordinator coordinator, java.util.concurrent.ScheduledExecutorService scheduler) {
        if (coordinator != null) {
            coordinator.scheduleRefreshSoon(scheduler);
        }
    }

    void triggerPanelRefresh(PanelRefreshCoordinator coordinator, java.util.concurrent.ScheduledExecutorService scheduler) {
        if (coordinator != null) {
            coordinator.triggerPanelRefresh(scheduler);
        }
    }

    void triggerStatsRefresh(PanelRefreshCoordinator coordinator, java.util.concurrent.ScheduledExecutorService scheduler) {
        if (coordinator != null) {
            coordinator.triggerStatsRefresh(scheduler);
        }
    }

    ApiClient.StatsSummaryResponse fetchRemoteStatsSummary(
        ApiClient apiClient,
        PluginConfig config,
        SessionRefreshService sessionRefreshService,
        String sessionToken,
        Long sinceMs,
        boolean allowRefresh
    ) {
        if (apiClient == null || config == null || sessionRefreshService == null) {
            return null;
        }
        try {
            return apiClient.fetchStatsSummary(sessionToken, sinceMs, null);
        } catch (ApiClient.ApiException ex) {
            if (ApiStatusPolicy.isAuthStatus(ex.statusCode) && allowRefresh) {
                boolean refreshed = sessionRefreshService.attemptRefresh(sessionToken);
                if (refreshed) {
                    String refreshedToken = config.sessionToken();
                    if (ApiStatusPolicy.hasText(refreshedToken)) {
                        return fetchRemoteStatsSummary(
                            apiClient,
                            config,
                            sessionRefreshService,
                            refreshedToken,
                            sinceMs,
                            false
                        );
                    }
                }
                sessionRefreshService.clearSession();
            }
        } catch (IOException | RuntimeException ignored) {
        }
        return null;
    }

    void requeue(UploadEventDispatchFacadeService dispatchService, List<GeEvent> batch) {
        if (dispatchService == null || batch == null || batch.isEmpty()) {
            return;
        }
        for (GeEvent event : batch) {
            dispatchService.enqueueEvent(event);
        }
    }

    boolean isPanelVisible(FlipHubPanel panel) {
        return panel != null && panel.isShowing();
    }

    boolean isTimeoutException(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof java.net.SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    boolean isClientLoggedIn(Client client) {
        return client != null && client.getGameState() == GameState.LOGGED_IN;
    }

    boolean isClientFullyReady(Client client) {
        return isClientLoggedIn(client) && client.getLocalPlayer() != null;
    }

    void pushGameMessage(Client client, String message) {
        if (client == null || message == null || message.trim().isEmpty()) {
            return;
        }
        try {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
        } catch (RuntimeException ignored) {
        }
    }
}
