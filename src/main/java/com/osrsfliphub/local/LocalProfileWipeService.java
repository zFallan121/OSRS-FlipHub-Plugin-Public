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

import static com.osrsfliphub.GeLifecyclePluginConstants.ACCOUNTWIDE_KEY;
import static com.osrsfliphub.GeLifecyclePluginConstants.GE_HISTORY_CONTAINER_CHILD_ID;
import static com.osrsfliphub.GeLifecyclePluginConstants.GE_HISTORY_GROUP_ID;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.widgets.Widget;

@Singleton
final class LocalProfileWipeService {
    interface Hooks {
        long resolveLocalAccountKey();
        List<GeHistoryTrade> tryParseCurrentGeHistoryTrades();
        List<String> buildGeHistoryCursorSignatures(List<GeHistoryTrade> trades);
        Map<Long, String> loadProfilesFromDisk();
        String resolveProfileDisplayName(long accountKey);
        void setProfileDisplayName(long accountKey, String displayName);
        void setWipeBarrierArmed(long accountKey, boolean armed);
        void persistGeHistoryCursor(long accountKey, List<String> cursor);
        void clearProfileData(long accountKey, String displayName, boolean clearLegacyTradeCache);
        void clearAccountwideData();
        void clearAllLegacyLocalTrades();
        void loadLocalTradesForAccount(long accountKey, boolean forceReload);
        void refreshUiAfterWipe();
        void markAccountwideUploadDirty();
        void pushGameMessage(String message);
        void showError(String message);
    }

    private final long accountwideKey;
    private final Hooks hooks;

    @Inject
    LocalProfileWipeService() {
        this(ACCOUNTWIDE_KEY, productionHooks());
    }

    LocalProfileWipeService(long accountwideKey, Hooks hooks) {
        this.accountwideKey = accountwideKey;
        this.hooks = hooks;
    }

    private static Hooks productionHooks() {
        return new Hooks() {
            @Override
            public long resolveLocalAccountKey() {
                LocalAccountSessionService service = PluginInjectorBridge.get(LocalAccountSessionService.class);
                return service != null ? service.resolveLocalAccountKey() : -1L;
            }

            @Override
            public List<GeHistoryTrade> tryParseCurrentGeHistoryTrades() {
                Client client = PluginAccess.plugin().client;
                if (client == null || client.getGameState() != GameState.LOGGED_IN) {
                    return null;
                }
                Widget historyContainer = client.getWidget(GE_HISTORY_GROUP_ID, GE_HISTORY_CONTAINER_CHILD_ID);
                if (historyContainer == null || historyContainer.isHidden()) {
                    return null;
                }
                GeHistoryWidgetReadService service = PluginInjectorBridge.get(GeHistoryWidgetReadService.class);
                return service != null ? service.tryParseReadyTrades(historyContainer.getDynamicChildren()) : null;
            }

            @Override
            public List<String> buildGeHistoryCursorSignatures(List<GeHistoryTrade> trades) {
                GeHistoryCursorService service = PluginInjectorBridge.get(GeHistoryCursorService.class);
                return service != null ? service.buildCursorSignatures(trades) : null;
            }

            @Override
            public Map<Long, String> loadProfilesFromDisk() {
                ProfileSelectionPresentationFacadeService service =
                    PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class);
                return service != null ? service.loadProfilesFromDisk() : null;
            }

            @Override
            public String resolveProfileDisplayName(long accountKey) {
                Map<Long, String> names = PluginAccess.plugin().profileDisplayNames;
                return names != null ? names.get(accountKey) : null;
            }

            @Override
            public void setProfileDisplayName(long accountKey, String displayName) {
                Map<Long, String> names = PluginAccess.plugin().profileDisplayNames;
                if (names == null || accountKey <= 0 || displayName == null) {
                    return;
                }
                String trimmed = displayName.trim();
                if (!trimmed.isEmpty()) {
                    names.put(accountKey, trimmed);
                }
            }

            @Override
            public void setWipeBarrierArmed(long accountKey, boolean armed) {
                GeHistoryWipeStateStore store = PluginInjectorBridge.get(GeHistoryWipeStateStore.class);
                if (store != null) {
                    store.setWipeBarrierArmed(accountKey, armed);
                }
            }

            @Override
            public void persistGeHistoryCursor(long accountKey, List<String> cursor) {
                GeHistoryWipeStateStore store = PluginInjectorBridge.get(GeHistoryWipeStateStore.class);
                if (store != null) {
                    store.persistCursor(accountKey, cursor);
                }
            }

            @Override
            public void clearProfileData(long accountKey, String displayName, boolean clearLegacyTradeCache) {
                ProfileWipeDataService service = PluginInjectorBridge.get(ProfileWipeDataService.class);
                if (service != null) {
                    service.clearProfileDataForWipe(accountKey, displayName, clearLegacyTradeCache);
                }
            }

            @Override
            public void clearAccountwideData() {
                ProfileWipeDataService service = PluginInjectorBridge.get(ProfileWipeDataService.class);
                if (service != null) {
                    service.clearAccountwideDataForWipe();
                }
            }

            @Override
            public void clearAllLegacyLocalTrades() {
                ProfileWipeDataService service = PluginInjectorBridge.get(ProfileWipeDataService.class);
                if (service != null) {
                    service.clearAllLegacyLocalTrades();
                }
            }

            @Override
            public void loadLocalTradesForAccount(long accountKey, boolean forceReload) {
                GeLifecycleLocalTradesRuntimeService localTradesRuntime =
                    PluginInjectorBridge.get(GeLifecycleLocalTradesRuntimeService.class);
                if (localTradesRuntime != null) {
                    localTradesRuntime.loadLocalTradesForAccount(accountKey, forceReload);
                }
            }

            @Override
            public void refreshUiAfterWipe() {
                GeLifecycleProfileWorkflowService profileWorkflow =
                    PluginInjectorBridge.get(GeLifecycleProfileWorkflowService.class);
                if (profileWorkflow != null) {
                    profileWorkflow.updateProfileOptionsUI();
                    profileWorkflow.updateProfileHeader();
                }
                GeLifecyclePlugin plugin = PluginAccess.plugin();
                PanelRefreshCoordinator coordinator = PluginInjectorBridge.get(PanelRefreshCoordinator.class);
                plugin.runtimeUtilityServices.triggerPanelRefresh(coordinator, plugin.scheduler);
                plugin.runtimeUtilityServices.triggerStatsRefresh(coordinator, plugin.scheduler);
            }

            @Override
            public void markAccountwideUploadDirty() {
                PluginAccess.plugin().markAccountwideUploadDirty();
            }

            @Override
            public void pushGameMessage(String message) {
                GeLifecyclePlugin plugin = PluginAccess.plugin();
                plugin.runtimeUtilityServices.pushGameMessage(plugin.client, message);
            }

            @Override
            public void showError(String message) {
                GeLifecycleProfileWorkflowService profileWorkflow =
                    PluginInjectorBridge.get(GeLifecycleProfileWorkflowService.class);
                if (profileWorkflow != null) {
                    profileWorkflow.showManageDataError(message);
                }
            }
        };
    }

    void wipeSingleLocalProfile(long accountKey, String displayName) {
        if (accountKey <= 0 || hooks == null) {
            return;
        }

        long currentAccountKey = hooks.resolveLocalAccountKey();
        List<GeHistoryTrade> history = hooks.tryParseCurrentGeHistoryTrades();
        if (history == null) {
            hooks.showError("Open the GE History tab and wait for it to load, then try again.");
            hooks.pushGameMessage("FlipHub wipe failed: GE History tab not ready.");
            return;
        }

        hooks.setWipeBarrierArmed(accountKey, true);
        if (accountKey == currentAccountKey) {
            hooks.persistGeHistoryCursor(accountKey, hooks.buildGeHistoryCursorSignatures(history));
        } else {
            hooks.persistGeHistoryCursor(accountKey, new ArrayList<>());
        }

        String trimmedDisplayName = displayName != null ? displayName.trim() : "";
        if (!trimmedDisplayName.isEmpty()) {
            hooks.setProfileDisplayName(accountKey, trimmedDisplayName);
        }

        hooks.clearProfileData(accountKey, displayName, true);

        // Ensure accountwide view reflects the wipe immediately.
        hooks.loadLocalTradesForAccount(accountKey, false);
        hooks.loadLocalTradesForAccount(accountwideKey, true);
        hooks.refreshUiAfterWipe();
        hooks.markAccountwideUploadDirty();

        String label = !trimmedDisplayName.isEmpty() ? trimmedDisplayName : ("Profile " + accountKey);
        hooks.pushGameMessage("FlipHub local wipe: cleared history for " + label + ".");
    }

    void wipeAllLocalProfiles() {
        if (hooks == null) {
            return;
        }

        long currentAccountKey = hooks.resolveLocalAccountKey();
        List<GeHistoryTrade> history = hooks.tryParseCurrentGeHistoryTrades();
        if (history == null) {
            hooks.showError("Open the GE History tab and wait for it to load, then try again.");
            hooks.pushGameMessage("FlipHub wipe failed: GE History tab not ready.");
            return;
        }
        List<String> baselineCursor = hooks.buildGeHistoryCursorSignatures(history);

        Map<Long, String> profiles = hooks.loadProfilesFromDisk();
        Set<Long> keys = new HashSet<>();
        if (profiles != null) {
            for (Long key : profiles.keySet()) {
                if (key != null && key > 0) {
                    keys.add(key);
                }
            }
        }
        if (currentAccountKey > 0) {
            keys.add(currentAccountKey);
        }

        for (Long key : keys) {
            if (key == null || key <= 0) {
                continue;
            }
            hooks.setWipeBarrierArmed(key, true);
            if (key == currentAccountKey) {
                hooks.persistGeHistoryCursor(key, baselineCursor);
            } else {
                hooks.persistGeHistoryCursor(key, new ArrayList<>());
            }

            hooks.clearProfileData(key, hooks.resolveProfileDisplayName(key), false);
        }

        hooks.clearAccountwideData();
        hooks.clearAllLegacyLocalTrades();

        // Reload accountwide after the wipe so the UI updates immediately.
        hooks.loadLocalTradesForAccount(accountwideKey, true);
        hooks.refreshUiAfterWipe();
        hooks.markAccountwideUploadDirty();
        hooks.pushGameMessage("FlipHub local wipe: cleared history for all profiles.");
    }
}
