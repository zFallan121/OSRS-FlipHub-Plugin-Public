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

import com.google.gson.Gson;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.runelite.client.config.ConfigManager;

final class ProfileWipeDataPluginHooks implements ProfileWipeDataService.Hooks {
    private final Supplier<ProfileStorageFacadeService> profileStorageFacadeServiceSupplier;
    private final Gson gson;
    private final ConfigManager configManager;
    private final Map<Long, String> legacyNameKeysByHash;
    private final Supplier<LegacyLocalTradesStore> legacyLocalTradesStoreSupplier;

    ProfileWipeDataPluginHooks(
        Supplier<ProfileStorageFacadeService> profileStorageFacadeServiceSupplier,
        Gson gson,
        ConfigManager configManager,
        Map<Long, String> legacyNameKeysByHash,
        Supplier<LegacyLocalTradesStore> legacyLocalTradesStoreSupplier
    ) {
        this.profileStorageFacadeServiceSupplier = profileStorageFacadeServiceSupplier;
        this.gson = gson;
        this.configManager = configManager;
        this.legacyNameKeysByHash = legacyNameKeysByHash;
        this.legacyLocalTradesStoreSupplier = legacyLocalTradesStoreSupplier;
    }

    @Override
    public void writeProfileData(long accountKey, List<LocalTradeDelta> deltas) {
        profileStorageFacadeServiceSupplier.get().writeProfileData(accountKey, deltas);
    }

    @Override
    public Path getLegacyProfilesDir() {
        return profileStorageFacadeServiceSupplier.get().getLegacyProfilesDir();
    }

    @Override
    public Gson getGson() {
        return gson;
    }

    @Override
    public boolean hasConfigManager() {
        return configManager != null;
    }

    @Override
    public void clearLegacyLocalTradesConfigEntry(String suffix) {
        if (configManager == null || suffix == null || suffix.trim().isEmpty()) {
            return;
        }
        configManager.setConfiguration(
            FliphubConfigGroups.CONFIG_GROUP,
            "localTrades." + suffix.trim(),
            ""
        );
    }

    @Override
    public String getLegacyNameKey(long accountKey) {
        return legacyNameKeysByHash.get(accountKey);
    }

    @Override
    public Map<String, String> getLegacyLocalTradesEntries() {
        return legacyLocalTradesStoreSupplier.get().getEntries();
    }
}
