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
import net.runelite.client.config.ConfigManager;

@Singleton
final class ProfileSelectionPersistenceService {
    private final ConfigManager configManager;
    private final String configGroup = FliphubConfigGroups.CONFIG_GROUP;
    private final String legacyGroup = FliphubConfigGroups.LEGACY_DEV_CONFIG_GROUP;
    private final String selectedKeyName = GeLifecyclePluginConstants.PROFILE_SELECTED_KEY;
    private final String modeKeyName = GeLifecyclePluginConstants.PROFILE_SELECTION_MODE_KEY;

    @Inject
    ProfileSelectionPersistenceService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    private String readConfiguration(String group, String key) {
        return configManager != null ? configManager.getConfiguration(group, key) : null;
    }

    boolean load(ProfileSelectionState state) {
        if (configManager == null || state == null) {
            return false;
        }
        String storedKey = readConfiguration(configGroup, selectedKeyName);
        String mode = readConfiguration(configGroup, modeKeyName);
        boolean migratedFromLegacy = false;
        if (isBlank(storedKey) || isBlank(mode)) {
            String legacyKey = readConfiguration(legacyGroup, selectedKeyName);
            String legacyMode = readConfiguration(legacyGroup, modeKeyName);
            if (isBlank(storedKey) && !isBlank(legacyKey)) {
                storedKey = legacyKey.trim();
                migratedFromLegacy = true;
            }
            if (isBlank(mode) && !isBlank(legacyMode)) {
                mode = legacyMode.trim();
                migratedFromLegacy = true;
            }
        }
        state.restoreLoadedState(storedKey, mode);
        return migratedFromLegacy;
    }

    void persist(ProfileSelectionState state) {
        if (configManager == null || state == null) {
            return;
        }
        configManager.setConfiguration(configGroup, selectedKeyName, state.selectedProfileKeyForPersistence());
        configManager.setConfiguration(configGroup, modeKeyName, state.selectionModeForPersistence());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
