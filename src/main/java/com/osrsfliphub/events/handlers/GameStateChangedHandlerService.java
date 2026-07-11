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

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.GameState;

@Singleton
final class GameStateChangedHandlerService {
    private final PluginConfig config;

    @Inject
    GameStateChangedHandlerService(PluginConfig config) {
        this.config = config;
    }

    private static GeLifecycleOfferStampStateServices offerStampState() {
        return PluginAccess.plugin().getOfferStampStateServices();
    }

    private static GeLifecycleProfileWorkflowService profileWorkflow() {
        return PluginAccess.plugin().getProfileWorkflowService();
    }

    private static ProfileSelectionPresentationFacadeService profileSelectionFacade() {
        return PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class);
    }

    void handle(GameState gameState) {
        if (gameState == null) {
            return;
        }
        GeLifecyclePlugin plugin = PluginAccess.plugin();

        if (gameState != GameState.LOGGED_IN) {
            offerStampState().persistOfferUpdateTimes();
            offerStampState().resetOfferUpdateStampsOnLogout();
            plugin.snapshots.clear();
            PluginInjectorBridge.get(GeHistoryAutoSyncStateService.class).disarm();
            RecentTradeDeduper deduper = PluginInjectorBridge.get(RecentTradeDeduper.class);
            if (deduper != null) {
                deduper.clearAll();
            }
            if (plugin.panel != null) {
                profileWorkflow().updateProfileOptionsUI();
                profileWorkflow().updateProfileHeader();
            }
            return;
        }

        PluginInjectorBridge.get(GeHistoryAutoSyncStateService.class).arm();
        offerStampState().setLastLoginNow();
        offerStampState().loadOfferUpdateTimesForCurrentAccount();
        LocalTradeSessionFacadeService tradeSession = PluginInjectorBridge.get(LocalTradeSessionFacadeService.class);
        if (tradeSession != null) {
            tradeSession.updateLocalAccountSessionStart();
        }
        profileWorkflow().updateProfileForLogin();
        profileWorkflow().primeOfferSnapshots();

        ProfileSelectionPresentationFacadeService selectionFacade = profileSelectionFacade();
        if (selectionFacade == null || !selectionFacade.hasSessionToken()) {
            plugin.localTradesLoadedThisLogin = false;
            plugin.localTradesLoadState.setLastAttemptMs(0L);
            plugin.getLocalTradesRuntimeService().scheduleLocalTradesLoad();
            WikiPriceService wikiPrices = PluginInjectorBridge.get(WikiPriceService.class);
            if (wikiPrices != null) {
                wikiPrices.refreshPrices();
            }
        }

        LinkAttemptService linkAttemptService = PluginInjectorBridge.get(LinkAttemptService.class);
        String linkInput = linkAttemptService != null && config != null
            ? linkAttemptService.resolveLinkInput(config.licenseKey(), config.linkCode())
            : null;
        if (linkInput != null && !linkInput.trim().isEmpty()) {
            linkAttemptService.attemptLink(linkInput.trim());
        }

        boolean visible = plugin.runtimeUtilityServices.isPanelVisible(plugin.panel);
        plugin.panelVisible = visible;
        if (visible) {
            PanelRefreshCoordinator coordinator = plugin.getPanelRefreshCoordinator();
            if (coordinator != null) {
                coordinator.triggerPanelRefresh(plugin.scheduler);
                coordinator.triggerStatsRefresh(plugin.scheduler);
            }
        }

        if (plugin.scheduler != null && selectionFacade != null && selectionFacade.isLinked()) {
            UploadBackfillDispatchService dispatch = PluginInjectorBridge.get(UploadBackfillDispatchService.class);
            if (dispatch != null) {
                dispatch.requestBackfillAttempt(plugin.scheduler, 8, true);
            }
        }
    }
}
