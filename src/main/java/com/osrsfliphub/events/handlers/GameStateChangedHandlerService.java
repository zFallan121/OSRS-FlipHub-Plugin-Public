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
    interface Hooks {
        void persistOfferUpdateTimes();
        void resetOfferUpdateStamps();
        void clearSnapshots();
        void disarmGeHistoryAutoSync();
        void clearRecentTradeDeduper();
        boolean isPanelAvailable();
        void updateProfileOptionsUI();
        void updateProfileHeader();
        void armGeHistoryAutoSync();
        void setLastLoginNow();
        void loadOfferUpdateTimesForCurrentAccount();
        void updateLocalAccountSessionStart();
        void updateProfileForLogin();
        void primeOfferSnapshots();
        boolean hasSessionToken();
        void resetLocalTradesLoadState();
        void scheduleLocalTradesLoad();
        void refreshWikiLatestPrices();
        String getLinkInput();
        void attemptLink(String linkInput);
        boolean isPanelVisible();
        void setPanelVisible(boolean visible);
        void triggerPanelRefresh();
        void triggerStatsRefresh();
        boolean hasScheduler();
        boolean isLinked();
        void requestBackfillAttempt(int delaySeconds, boolean forceRefresh);
    }

    private final Hooks hooks;

    @Inject
    GameStateChangedHandlerService(PluginConfig config) {
        this(productionHooks(config));
    }

    GameStateChangedHandlerService(Hooks hooks) {
        this.hooks = hooks;
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

    private static Hooks productionHooks(PluginConfig config) {
        return new Hooks() {
            @Override
            public void persistOfferUpdateTimes() {
                offerStampState().persistOfferUpdateTimes();
            }

            @Override
            public void resetOfferUpdateStamps() {
                offerStampState().resetOfferUpdateStampsOnLogout();
            }

            @Override
            public void clearSnapshots() {
                PluginAccess.plugin().snapshots.clear();
            }

            @Override
            public void disarmGeHistoryAutoSync() {
                PluginInjectorBridge.get(GeHistoryAutoSyncStateService.class).disarm();
            }

            @Override
            public void clearRecentTradeDeduper() {
                RecentTradeDeduper deduper =
                    PluginInjectorBridge.get(RecentTradeDeduper.class);
                if (deduper != null) {
                    deduper.clearAll();
                }
            }

            @Override
            public boolean isPanelAvailable() {
                return PluginAccess.plugin().panel != null;
            }

            @Override
            public void updateProfileOptionsUI() {
                profileWorkflow().updateProfileOptionsUI();
            }

            @Override
            public void updateProfileHeader() {
                profileWorkflow().updateProfileHeader();
            }

            @Override
            public void armGeHistoryAutoSync() {
                PluginInjectorBridge.get(GeHistoryAutoSyncStateService.class).arm();
            }

            @Override
            public void setLastLoginNow() {
                offerStampState().setLastLoginNow();
            }

            @Override
            public void loadOfferUpdateTimesForCurrentAccount() {
                offerStampState().loadOfferUpdateTimesForCurrentAccount();
            }

            @Override
            public void updateLocalAccountSessionStart() {
                LocalTradeSessionFacadeService service =
                    PluginInjectorBridge.get(LocalTradeSessionFacadeService.class);
                if (service != null) {
                    service.updateLocalAccountSessionStart();
                }
            }

            @Override
            public void updateProfileForLogin() {
                profileWorkflow().updateProfileForLogin();
            }

            @Override
            public void primeOfferSnapshots() {
                profileWorkflow().primeOfferSnapshots();
            }

            @Override
            public boolean hasSessionToken() {
                ProfileSelectionPresentationFacadeService service = profileSelectionFacade();
                return service != null && service.hasSessionToken();
            }

            @Override
            public void resetLocalTradesLoadState() {
                GeLifecyclePlugin plugin = PluginAccess.plugin();
                plugin.localTradesLoadedThisLogin = false;
                plugin.localTradesLoadState.setLastAttemptMs(0L);
            }

            @Override
            public void scheduleLocalTradesLoad() {
                PluginAccess.plugin().getLocalTradesRuntimeService().scheduleLocalTradesLoad();
            }

            @Override
            public void refreshWikiLatestPrices() {
                WikiPriceService service = PluginInjectorBridge.get(WikiPriceService.class);
                if (service != null) {
                    service.refreshPrices();
                }
            }

            @Override
            public String getLinkInput() {
                LinkAttemptService linkAttemptService = PluginInjectorBridge.get(LinkAttemptService.class);
                if (linkAttemptService == null || config == null) {
                    return null;
                }
                return linkAttemptService.resolveLinkInput(config.licenseKey(), config.linkCode());
            }

            @Override
            public void attemptLink(String linkInput) {
                PluginInjectorBridge.get(LinkAttemptService.class).attemptLink(linkInput);
            }

            @Override
            public boolean isPanelVisible() {
                GeLifecyclePlugin plugin = PluginAccess.plugin();
                return plugin.runtimeUtilityServices.isPanelVisible(plugin.panel);
            }

            @Override
            public void setPanelVisible(boolean visible) {
                PluginAccess.plugin().panelVisible = visible;
            }

            @Override
            public void triggerPanelRefresh() {
                GeLifecyclePlugin plugin = PluginAccess.plugin();
                PanelRefreshCoordinator coordinator = plugin.getPanelRefreshCoordinator();
                if (coordinator != null) {
                    coordinator.triggerPanelRefresh(plugin.scheduler);
                }
            }

            @Override
            public void triggerStatsRefresh() {
                GeLifecyclePlugin plugin = PluginAccess.plugin();
                PanelRefreshCoordinator coordinator = plugin.getPanelRefreshCoordinator();
                if (coordinator != null) {
                    coordinator.triggerStatsRefresh(plugin.scheduler);
                }
            }

            @Override
            public boolean hasScheduler() {
                return PluginAccess.plugin().scheduler != null;
            }

            @Override
            public boolean isLinked() {
                ProfileSelectionPresentationFacadeService service = profileSelectionFacade();
                return service != null && service.isLinked();
            }

            @Override
            public void requestBackfillAttempt(int delaySeconds, boolean forceRefresh) {
                GeLifecyclePlugin plugin = PluginAccess.plugin();
                UploadBackfillDispatchService dispatch =
                    PluginInjectorBridge.get(UploadBackfillDispatchService.class);
                if (dispatch != null && plugin.scheduler != null) {
                    dispatch.requestBackfillAttempt(plugin.scheduler, delaySeconds, forceRefresh);
                }
            }
        };
    }

    void handle(GameState gameState) {
        if (hooks == null || gameState == null) {
            return;
        }

        if (gameState != GameState.LOGGED_IN) {
            hooks.persistOfferUpdateTimes();
            hooks.resetOfferUpdateStamps();
            hooks.clearSnapshots();
            hooks.disarmGeHistoryAutoSync();
            hooks.clearRecentTradeDeduper();
            if (hooks.isPanelAvailable()) {
                hooks.updateProfileOptionsUI();
                hooks.updateProfileHeader();
            }
            return;
        }

        hooks.armGeHistoryAutoSync();
        hooks.setLastLoginNow();
        hooks.loadOfferUpdateTimesForCurrentAccount();
        hooks.updateLocalAccountSessionStart();
        hooks.updateProfileForLogin();
        hooks.primeOfferSnapshots();

        if (!hooks.hasSessionToken()) {
            hooks.resetLocalTradesLoadState();
            hooks.scheduleLocalTradesLoad();
            hooks.refreshWikiLatestPrices();
        }

        String linkInput = hooks.getLinkInput();
        if (linkInput != null && !linkInput.trim().isEmpty()) {
            hooks.attemptLink(linkInput.trim());
        }

        boolean visible = hooks.isPanelVisible();
        hooks.setPanelVisible(visible);
        if (visible) {
            hooks.triggerPanelRefresh();
            hooks.triggerStatsRefresh();
        }

        if (hooks.hasScheduler() && hooks.isLinked()) {
            hooks.requestBackfillAttempt(8, true);
        }
    }
}
