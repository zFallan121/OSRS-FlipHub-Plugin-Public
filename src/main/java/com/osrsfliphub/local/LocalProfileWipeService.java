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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    LocalProfileWipeService(long accountwideKey, Hooks hooks) {
        this.accountwideKey = accountwideKey;
        this.hooks = hooks;
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
