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

import java.util.*;
import javax.inject.*;
import lombok.RequiredArgsConstructor;
import net.runelite.api.*;
import net.runelite.api.widgets.Widget;
import static com.osrsfliphub.Const.ACCOUNTWIDE_KEY;
import static com.osrsfliphub.Const.GE_HISTORY_CONTAINER_CHILD_ID;
import static com.osrsfliphub.Const.GE_HISTORY_GROUP_ID;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class ProfileWipe {
    private final long accountwideKey = ACCOUNTWIDE_KEY;
    private final PluginState pluginState;
    private final AccountSession accountSession;
    private final GeHistoryCursorService geHistoryCursorService;
    private final ProfileSelectionPresentation selectionPresentation;
    private final WipeStateStore wipeStateStore;
    private final ProfileWipeDataService dataService;
    private final LocalTradesRuntime localTradesRuntime;
    private final ProfileWorkflow workflow;
    private final PanelRefresh panelRefresh;
    private final Client client;

    private List<Trade> tryParseCurrentGeHistoryTrades() {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return null;
        }
        Widget historyContainer = client.getWidget(GE_HISTORY_GROUP_ID, GE_HISTORY_CONTAINER_CHILD_ID);
        if (historyContainer == null || historyContainer.isHidden()) {
            return null;
        }
        return WidgetParser.tryParseReadyTrades(historyContainer.getDynamicChildren());
    }

    private String resolveProfileDisplayName(long accountKey) {
        Map<Long, String> names = pluginState.getProfileDisplayNames();
        return names != null ? names.get(accountKey) : null;
    }

    private void setProfileDisplayName(long accountKey, String displayName) {
        Map<Long, String> names = pluginState.getProfileDisplayNames();
        if (names == null || accountKey <= 0 || displayName == null) {
            return;
        }
        String trimmed = displayName.trim();
        if (!trimmed.isEmpty()) {
            names.put(accountKey, trimmed);
        }
    }

    /** @return whether the profile is actually gone from disk, not merely from memory. */
    private boolean clearProfileData(long accountKey, String displayName) {
        return dataService.clearProfileDataForWipe(accountKey, displayName);
    }

    /** @return whether the accountwide file is actually gone from disk. */
    private boolean clearAccountwideData() {
        return dataService.clearAccountwideDataForWipe();
    }

    /**
     * Told to the player when a wipe emptied memory but could not empty the file. Saying
     * "cleared" there is the worst outcome: they believe the history is destroyed, the panel
     * agrees, and it all returns at the next restart.
     */
    private void reportWipeWriteFailure() {
        workflow.showManageDataError("Your history was cleared on screen but could not be deleted from disk. "
            + "Check that the FlipHub folder is writable, then wipe again.");
        pushGameMessage("FlipHub wipe failed: the history could not be deleted from disk.");
    }

    private void refreshUiAfterWipe() {
        workflow.updateProfileOptionsUI();
        workflow.updateProfileHeader();
        GeLifecyclePlugin plugin = Access.plugin();
        plugin.runtimeUtilityServices.triggerPanelRefresh(panelRefresh, plugin.scheduler);
        plugin.runtimeUtilityServices.triggerStatsRefresh(panelRefresh, plugin.scheduler);
    }

    private void pushGameMessage(String message) {
        GeLifecyclePlugin plugin = Access.plugin();
        plugin.runtimeUtilityServices.pushGameMessage(plugin.client, message);
    }

    void wipeSingleLocalProfile(long accountKey, String displayName) {
        if (accountKey <= 0) {
            return;
        }

        long currentAccountKey = accountSession.resolveLocalAccountKey();
        List<Trade> history = tryParseCurrentGeHistoryTrades();
        if (history == null) {
            workflow.showManageDataError("Open the GE History tab and wait for it to load, then try again.");
            pushGameMessage("FlipHub wipe failed: GE History tab not ready.");
            return;
        }

        wipeStateStore.setWipeBarrierArmed(accountKey, true);
        if (accountKey == currentAccountKey) {
            wipeStateStore.persistCursor(accountKey, geHistoryCursorService.buildCursorSignatures(history));
        } else {
            wipeStateStore.persistCursor(accountKey, new ArrayList<>());
        }

        String trimmedDisplayName = displayName != null ? displayName.trim() : "";
        if (!trimmedDisplayName.isEmpty()) {
            setProfileDisplayName(accountKey, trimmedDisplayName);
        }

        boolean cleared = clearProfileData(accountKey, displayName);

        // Ensure accountwide view reflects the wipe immediately.
        localTradesRuntime.loadLocalTradesForAccount(accountKey, false);
        localTradesRuntime.loadLocalTradesForAccount(accountwideKey, true);
        refreshUiAfterWipe();
        Access.plugin().markAccountwideUploadDirty();

        if (!cleared) {
            reportWipeWriteFailure();
            return;
        }

        String label = !trimmedDisplayName.isEmpty()
            ? trimmedDisplayName : ProfileDisplayNames.placeholderFor(accountKey);
        pushGameMessage("FlipHub local wipe: cleared history for " + label + ".");
    }

    void wipeAllLocalProfiles() {
        long currentAccountKey = accountSession.resolveLocalAccountKey();
        List<Trade> history = tryParseCurrentGeHistoryTrades();
        if (history == null) {
            workflow.showManageDataError("Open the GE History tab and wait for it to load, then try again.");
            pushGameMessage("FlipHub wipe failed: GE History tab not ready.");
            return;
        }
        List<String> baselineCursor = geHistoryCursorService.buildCursorSignatures(history);

        Map<Long, String> profiles = selectionPresentation.loadProfilesFromDisk();
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

        boolean allCleared = true;
        for (Long key : keys) {
            if (key == null || key <= 0) {
                continue;
            }
            wipeStateStore.setWipeBarrierArmed(key, true);
            if (key == currentAccountKey) {
                wipeStateStore.persistCursor(key, baselineCursor);
            } else {
                wipeStateStore.persistCursor(key, new ArrayList<>());
            }

            allCleared &= clearProfileData(key, resolveProfileDisplayName(key));
        }

        allCleared &= clearAccountwideData();

        // Reload accountwide after the wipe so the UI updates immediately.
        localTradesRuntime.loadLocalTradesForAccount(accountwideKey, true);
        refreshUiAfterWipe();
        Access.plugin().markAccountwideUploadDirty();
        if (!allCleared) {
            reportWipeWriteFailure();
            return;
        }
        pushGameMessage("FlipHub local wipe: cleared history for all profiles.");
    }
}
