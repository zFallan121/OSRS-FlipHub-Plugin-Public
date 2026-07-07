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

import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;

@Singleton
final class GeLifecycleTickServices {

    @Inject
    GeLifecycleTickServices() {
    }

    boolean handlePostClientTick(boolean panelVisible) {
        ChatboxSuggestionCycleService suggestionCycleService =
            PluginInjectorBridge.get(ChatboxSuggestionCycleService.class);
        if (suggestionCycleService != null) {
            suggestionCycleService.update();
        }
        GeHistoryAutoSyncCoordinatorService autoSync =
            PluginInjectorBridge.get(GeHistoryAutoSyncCoordinatorService.class);
        if (autoSync != null) {
            autoSync.attemptAutoSync();
        }
        maybeStampCurrentProfileDisplayName();

        GeLifecyclePlugin plugin = PluginAccess.plugin();
        boolean visible = plugin.runtimeUtilityServices.isPanelVisible(plugin.panel);
        if (visible && !panelVisible) {
            PanelRefreshCoordinator coordinator = PluginInjectorBridge.get(PanelRefreshCoordinator.class);
            plugin.runtimeUtilityServices.triggerPanelRefresh(coordinator, plugin.scheduler);
            plugin.runtimeUtilityServices.triggerStatsRefresh(coordinator, plugin.scheduler);
            return true;
        }
        if (!visible && panelVisible) {
            return false;
        }
        return panelVisible;
    }

    void maybeStampCurrentProfileDisplayName() {
        GeLifecyclePlugin plugin = PluginAccess.plugin();
        Client client = plugin.client;
        if (client == null || client.getGameState() != GameState.LOGGED_IN) {
            return;
        }
        if (client.getLocalPlayer() == null) {
            return;
        }
        String name = client.getLocalPlayer().getName();
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        String trimmed = name.trim();
        LocalAccountSessionService accountSession =
            PluginInjectorBridge.get(LocalAccountSessionService.class);
        if (accountSession == null) {
            return;
        }
        long accountKey = accountSession.resolveLocalAccountKey();
        if (accountKey <= 0) {
            return;
        }
        Map<Long, String> profileDisplayNames = plugin.profileDisplayNames;
        String existing = profileDisplayNames != null ? profileDisplayNames.get(accountKey) : null;
        GeLifecycleLocalTradesRuntimeService localTradesRuntime =
            PluginInjectorBridge.get(GeLifecycleLocalTradesRuntimeService.class);
        if (existing != null
            && localTradesRuntime != null
            && !localTradesRuntime.isPlaceholderDisplayName(existing)
            && existing.trim().equalsIgnoreCase(trimmed)) {
            return;
        }

        if (profileDisplayNames != null) {
            profileDisplayNames.put(accountKey, trimmed);
        }
        if (localTradesRuntime != null) {
            localTradesRuntime.ensureProfileLoaded(accountKey);
        }
        ProfileStorageFacadeService storage =
            PluginInjectorBridge.get(ProfileStorageFacadeService.class);
        LocalTradeSessionFacadeService localTradeSessionFacade =
            PluginInjectorBridge.get(LocalTradeSessionFacadeService.class);
        if (storage != null && localTradeSessionFacade != null) {
            storage.writeProfileData(accountKey, localTradeSessionFacade.snapshotLocalTradeDeltas(accountKey));
        }
        GeLifecycleProfileWorkflowService profileWorkflow =
            PluginInjectorBridge.get(GeLifecycleProfileWorkflowService.class);
        if (profileWorkflow != null) {
            profileWorkflow.updateProfileOptionsUI();
            profileWorkflow.updateProfileHeader();
        }
    }
}
