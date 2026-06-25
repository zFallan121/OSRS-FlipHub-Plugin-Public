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
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;

final class GeLifecycleTickServices {
    private final Supplier<GeLifecycleSuggestionServices> suggestionServicesSupplier;
    private final Runnable attemptGeHistoryAutoSyncAction;
    private final BooleanSupplier panelVisibleSupplier;
    private final Runnable triggerPanelRefreshAction;
    private final Runnable triggerStatsRefreshAction;
    private final Supplier<Client> clientSupplier;
    private final Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier;
    private final Map<Long, String> profileDisplayNames;
    private final Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier;
    private final Supplier<ProfileStorageFacadeService> profileStorageFacadeServiceSupplier;
    private final Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier;
    private final Runnable updateProfileOptionsUiAction;
    private final Runnable updateProfileHeaderAction;

    GeLifecycleTickServices(
        Supplier<GeLifecycleSuggestionServices> suggestionServicesSupplier,
        Runnable attemptGeHistoryAutoSyncAction,
        BooleanSupplier panelVisibleSupplier,
        Runnable triggerPanelRefreshAction,
        Runnable triggerStatsRefreshAction,
        Supplier<Client> clientSupplier,
        Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier,
        Map<Long, String> profileDisplayNames,
        Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier,
        Supplier<ProfileStorageFacadeService> profileStorageFacadeServiceSupplier,
        Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier,
        Runnable updateProfileOptionsUiAction,
        Runnable updateProfileHeaderAction
    ) {
        this.suggestionServicesSupplier = suggestionServicesSupplier;
        this.attemptGeHistoryAutoSyncAction = attemptGeHistoryAutoSyncAction;
        this.panelVisibleSupplier = panelVisibleSupplier;
        this.triggerPanelRefreshAction = triggerPanelRefreshAction;
        this.triggerStatsRefreshAction = triggerStatsRefreshAction;
        this.clientSupplier = clientSupplier;
        this.localAccountSessionServiceSupplier = localAccountSessionServiceSupplier;
        this.profileDisplayNames = profileDisplayNames;
        this.localTradesRuntimeServiceSupplier = localTradesRuntimeServiceSupplier;
        this.profileStorageFacadeServiceSupplier = profileStorageFacadeServiceSupplier;
        this.localTradeSessionFacadeServiceSupplier = localTradeSessionFacadeServiceSupplier;
        this.updateProfileOptionsUiAction = updateProfileOptionsUiAction;
        this.updateProfileHeaderAction = updateProfileHeaderAction;
    }

    boolean handlePostClientTick(boolean panelVisible) {
        GeLifecycleSuggestionServices suggestionServices = resolve(suggestionServicesSupplier);
        if (suggestionServices != null) {
            suggestionServices.getChatboxSuggestionCycleService().update();
        }
        if (attemptGeHistoryAutoSyncAction != null) {
            attemptGeHistoryAutoSyncAction.run();
        }
        maybeStampCurrentProfileDisplayName();

        boolean visible = panelVisibleSupplier != null && panelVisibleSupplier.getAsBoolean();
        if (visible && !panelVisible) {
            if (triggerPanelRefreshAction != null) {
                triggerPanelRefreshAction.run();
            }
            if (triggerStatsRefreshAction != null) {
                triggerStatsRefreshAction.run();
            }
            return true;
        }
        if (!visible && panelVisible) {
            return false;
        }
        return panelVisible;
    }

    void maybeStampCurrentProfileDisplayName() {
        Client client = resolve(clientSupplier);
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
        LocalAccountSessionService accountSession = resolve(localAccountSessionServiceSupplier);
        if (accountSession == null) {
            return;
        }
        long accountKey = accountSession.resolveLocalAccountKey();
        if (accountKey <= 0) {
            return;
        }
        String existing = profileDisplayNames != null ? profileDisplayNames.get(accountKey) : null;
        GeLifecycleLocalTradesRuntimeService localTradesRuntime = resolve(localTradesRuntimeServiceSupplier);
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
        ProfileStorageFacadeService storage = resolve(profileStorageFacadeServiceSupplier);
        LocalTradeSessionFacadeService localTradeSessionFacade = resolve(localTradeSessionFacadeServiceSupplier);
        if (storage != null && localTradeSessionFacade != null) {
            storage.writeProfileData(accountKey, localTradeSessionFacade.snapshotLocalTradeDeltas(accountKey));
        }
        if (updateProfileOptionsUiAction != null) {
            updateProfileOptionsUiAction.run();
        }
        if (updateProfileHeaderAction != null) {
            updateProfileHeaderAction.run();
        }
    }

    private <T> T resolve(Supplier<T> supplier) {
        return supplier != null ? supplier.get() : null;
    }
}
