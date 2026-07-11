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
    private final long accountwideKey = ACCOUNTWIDE_KEY;

    @Inject
    LocalProfileWipeService() {
    }

    private long resolveLocalAccountKey() {
        LocalAccountSessionService service = PluginInjectorBridge.get(LocalAccountSessionService.class);
        return service != null ? service.resolveLocalAccountKey() : -1L;
    }

    private List<GeHistoryTrade> tryParseCurrentGeHistoryTrades() {
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

    private List<String> buildGeHistoryCursorSignatures(List<GeHistoryTrade> trades) {
        GeHistoryCursorService service = PluginInjectorBridge.get(GeHistoryCursorService.class);
        return service != null ? service.buildCursorSignatures(trades) : null;
    }

    private Map<Long, String> loadProfilesFromDisk() {
        ProfileSelectionPresentationFacadeService service =
            PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class);
        return service != null ? service.loadProfilesFromDisk() : null;
    }

    private String resolveProfileDisplayName(long accountKey) {
        Map<Long, String> names = PluginAccess.plugin().profileDisplayNames;
        return names != null ? names.get(accountKey) : null;
    }

    private void setProfileDisplayName(long accountKey, String displayName) {
        Map<Long, String> names = PluginAccess.plugin().profileDisplayNames;
        if (names == null || accountKey <= 0 || displayName == null) {
            return;
        }
        String trimmed = displayName.trim();
        if (!trimmed.isEmpty()) {
            names.put(accountKey, trimmed);
        }
    }

    private void setWipeBarrierArmed(long accountKey, boolean armed) {
        GeHistoryWipeStateStore store = PluginInjectorBridge.get(GeHistoryWipeStateStore.class);
        if (store != null) {
            store.setWipeBarrierArmed(accountKey, armed);
        }
    }

    private void persistGeHistoryCursor(long accountKey, List<String> cursor) {
        GeHistoryWipeStateStore store = PluginInjectorBridge.get(GeHistoryWipeStateStore.class);
        if (store != null) {
            store.persistCursor(accountKey, cursor);
        }
    }

    private void clearProfileData(long accountKey, String displayName, boolean clearLegacyTradeCache) {
        ProfileWipeDataService service = PluginInjectorBridge.get(ProfileWipeDataService.class);
        if (service != null) {
            service.clearProfileDataForWipe(accountKey, displayName, clearLegacyTradeCache);
        }
    }

    private void clearAccountwideData() {
        ProfileWipeDataService service = PluginInjectorBridge.get(ProfileWipeDataService.class);
        if (service != null) {
            service.clearAccountwideDataForWipe();
        }
    }

    private void clearAllLegacyLocalTrades() {
        ProfileWipeDataService service = PluginInjectorBridge.get(ProfileWipeDataService.class);
        if (service != null) {
            service.clearAllLegacyLocalTrades();
        }
    }

    private void loadLocalTradesForAccount(long accountKey, boolean forceReload) {
        GeLifecycleLocalTradesRuntimeService localTradesRuntime =
            PluginInjectorBridge.get(GeLifecycleLocalTradesRuntimeService.class);
        if (localTradesRuntime != null) {
            localTradesRuntime.loadLocalTradesForAccount(accountKey, forceReload);
        }
    }

    private void refreshUiAfterWipe() {
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

    private void markAccountwideUploadDirty() {
        PluginAccess.plugin().markAccountwideUploadDirty();
    }

    private void pushGameMessage(String message) {
        GeLifecyclePlugin plugin = PluginAccess.plugin();
        plugin.runtimeUtilityServices.pushGameMessage(plugin.client, message);
    }

    private void showError(String message) {
        GeLifecycleProfileWorkflowService profileWorkflow =
            PluginInjectorBridge.get(GeLifecycleProfileWorkflowService.class);
        if (profileWorkflow != null) {
            profileWorkflow.showManageDataError(message);
        }
    }

    void wipeSingleLocalProfile(long accountKey, String displayName) {
        if (accountKey <= 0) {
            return;
        }

        long currentAccountKey = resolveLocalAccountKey();
        List<GeHistoryTrade> history = tryParseCurrentGeHistoryTrades();
        if (history == null) {
            showError("Open the GE History tab and wait for it to load, then try again.");
            pushGameMessage("FlipHub wipe failed: GE History tab not ready.");
            return;
        }

        setWipeBarrierArmed(accountKey, true);
        if (accountKey == currentAccountKey) {
            persistGeHistoryCursor(accountKey, buildGeHistoryCursorSignatures(history));
        } else {
            persistGeHistoryCursor(accountKey, new ArrayList<>());
        }

        String trimmedDisplayName = displayName != null ? displayName.trim() : "";
        if (!trimmedDisplayName.isEmpty()) {
            setProfileDisplayName(accountKey, trimmedDisplayName);
        }

        clearProfileData(accountKey, displayName, true);

        // Ensure accountwide view reflects the wipe immediately.
        loadLocalTradesForAccount(accountKey, false);
        loadLocalTradesForAccount(accountwideKey, true);
        refreshUiAfterWipe();
        markAccountwideUploadDirty();

        String label = !trimmedDisplayName.isEmpty() ? trimmedDisplayName : ("Profile " + accountKey);
        pushGameMessage("FlipHub local wipe: cleared history for " + label + ".");
    }

    void wipeAllLocalProfiles() {
        long currentAccountKey = resolveLocalAccountKey();
        List<GeHistoryTrade> history = tryParseCurrentGeHistoryTrades();
        if (history == null) {
            showError("Open the GE History tab and wait for it to load, then try again.");
            pushGameMessage("FlipHub wipe failed: GE History tab not ready.");
            return;
        }
        List<String> baselineCursor = buildGeHistoryCursorSignatures(history);

        Map<Long, String> profiles = loadProfilesFromDisk();
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
            setWipeBarrierArmed(key, true);
            if (key == currentAccountKey) {
                persistGeHistoryCursor(key, baselineCursor);
            } else {
                persistGeHistoryCursor(key, new ArrayList<>());
            }

            clearProfileData(key, resolveProfileDisplayName(key), false);
        }

        clearAccountwideData();
        clearAllLegacyLocalTrades();

        // Reload accountwide after the wipe so the UI updates immediately.
        loadLocalTradesForAccount(accountwideKey, true);
        refreshUiAfterWipe();
        markAccountwideUploadDirty();
        pushGameMessage("FlipHub local wipe: cleared history for all profiles.");
    }
}
