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

import java.util.Locale;
import net.runelite.client.config.ConfigManager;

final class ProfileSelectionState {
    private final String accountwideKeyString;
    private volatile String selectedProfileKey;
    private volatile boolean manualSelection;

    ProfileSelectionState(String accountwideKeyString) {
        this.accountwideKeyString = accountwideKeyString != null ? accountwideKeyString : "accountwide";
        this.selectedProfileKey = this.accountwideKeyString;
        this.manualSelection = false;
    }

    boolean load(ConfigManager configManager,
                 String configGroup,
                 String legacyGroup,
                 String selectedKeyName,
                 String modeKeyName) {
        if (configManager == null) {
            return false;
        }
        String storedKey = configManager.getConfiguration(configGroup, selectedKeyName);
        String mode = configManager.getConfiguration(configGroup, modeKeyName);
        boolean migratedFromLegacy = false;
        if ((storedKey == null || storedKey.trim().isEmpty()) || (mode == null || mode.trim().isEmpty())) {
            String legacyKey = configManager.getConfiguration(legacyGroup, selectedKeyName);
            String legacyMode = configManager.getConfiguration(legacyGroup, modeKeyName);
            if ((storedKey == null || storedKey.trim().isEmpty()) && legacyKey != null && !legacyKey.trim().isEmpty()) {
                storedKey = legacyKey.trim();
                migratedFromLegacy = true;
            }
            if ((mode == null || mode.trim().isEmpty()) && legacyMode != null && !legacyMode.trim().isEmpty()) {
                mode = legacyMode.trim();
                migratedFromLegacy = true;
            }
        }
        restoreLoadedState(storedKey, mode);
        return migratedFromLegacy;
    }

    void persist(ConfigManager configManager, String configGroup, String selectedKeyName, String modeKeyName) {
        if (configManager == null) {
            return;
        }
        configManager.setConfiguration(configGroup, selectedKeyName, selectedProfileKeyForPersistence());
        configManager.setConfiguration(configGroup, modeKeyName, selectionModeForPersistence());
    }

    void selectManual(String profileKey) {
        if (profileKey == null || profileKey.trim().isEmpty()) {
            return;
        }
        manualSelection = true;
        selectedProfileKey = profileKey.trim();
    }

    boolean updateForLogin(long accountHash) {
        if (manualSelection || accountHash <= 0) {
            return false;
        }
        selectedProfileKey = buildProfileKey(accountHash);
        return true;
    }

    String resolveSelectedProfileKeyForUi(boolean loggedIn) {
        if (!loggedIn) {
            return accountwideKeyString;
        }
        String key = selectedProfileKey;
        if (key == null || key.trim().isEmpty()) {
            return accountwideKeyString;
        }
        String normalized = key.trim().toLowerCase(Locale.US);
        if (accountwideKeyString.equals(normalized)) {
            return accountwideKeyString;
        }
        Long parsed = parseProfileHash(normalized);
        if (parsed == null) {
            return accountwideKeyString;
        }
        return "hash_" + parsed;
    }

    long resolveSelectedProfileKey(boolean loggedIn, long accountwideKey) {
        if (!loggedIn) {
            return accountwideKey;
        }
        String key = selectedProfileKey;
        if (key == null || key.trim().isEmpty()) {
            return accountwideKey;
        }
        String normalized = key.trim().toLowerCase(Locale.US);
        if (accountwideKeyString.equals(normalized)) {
            return accountwideKey;
        }
        Long parsed = parseProfileHash(normalized);
        if (parsed == null) {
            return accountwideKey;
        }
        return parsed;
    }

    String buildProfileKey(long accountHash) {
        if (accountHash <= 0) {
            return accountwideKeyString;
        }
        return "hash_" + accountHash;
    }

    void restoreLoadedState(String storedKey, String mode) {
        if (storedKey != null && !storedKey.trim().isEmpty()) {
            selectedProfileKey = storedKey.trim();
        }
        manualSelection = "manual".equalsIgnoreCase(mode);
    }

    String selectedProfileKeyForPersistence() {
        String key = selectedProfileKey;
        if (key == null || key.trim().isEmpty()) {
            return accountwideKeyString;
        }
        return key.trim();
    }

    String selectionModeForPersistence() {
        return manualSelection ? "manual" : "auto";
    }

    private Long parseProfileHash(String normalizedKey) {
        if (normalizedKey == null || normalizedKey.isEmpty()) {
            return null;
        }
        String numericPart = normalizedKey.startsWith("hash_")
            ? normalizedKey.substring(5)
            : normalizedKey;
        if (numericPart.isEmpty()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(numericPart);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
