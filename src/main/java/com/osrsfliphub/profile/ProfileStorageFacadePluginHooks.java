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
import java.util.Map;
import java.util.function.Supplier;
import net.runelite.client.config.ConfigManager;

final class ProfileStorageFacadePluginHooks implements ProfileStorageFacadeService.Hooks {
    private final Supplier<ProfileStore> profileStoreSupplier;
    private final Supplier<LegacyLocalTradesStore> legacyLocalTradesStoreSupplier;
    private final ConfigManager configManager;
    private final Gson gson;
    private final Supplier<GeHistoryWipeStateStore> geHistoryWipeStateStoreSupplier;
    private final Map<Long, String> legacyNameKeysByHash;
    private final Map<Long, String> profileDisplayNames;
    private final Map<Long, Long> loadedProfileFileMs;

    ProfileStorageFacadePluginHooks(
        Supplier<ProfileStore> profileStoreSupplier,
        Supplier<LegacyLocalTradesStore> legacyLocalTradesStoreSupplier,
        ConfigManager configManager,
        Gson gson,
        Supplier<GeHistoryWipeStateStore> geHistoryWipeStateStoreSupplier,
        Map<Long, String> legacyNameKeysByHash,
        Map<Long, String> profileDisplayNames,
        Map<Long, Long> loadedProfileFileMs
    ) {
        this.profileStoreSupplier = profileStoreSupplier;
        this.legacyLocalTradesStoreSupplier = legacyLocalTradesStoreSupplier;
        this.configManager = configManager;
        this.gson = gson;
        this.geHistoryWipeStateStoreSupplier = geHistoryWipeStateStoreSupplier;
        this.legacyNameKeysByHash = legacyNameKeysByHash;
        this.profileDisplayNames = profileDisplayNames;
        this.loadedProfileFileMs = loadedProfileFileMs;
    }

    @Override
    public ProfileStore getProfileStore() {
        return profileStoreSupplier != null ? profileStoreSupplier.get() : null;
    }

    @Override
    public LegacyLocalTradesStore getLegacyLocalTradesStore() {
        return legacyLocalTradesStoreSupplier != null ? legacyLocalTradesStoreSupplier.get() : null;
    }

    @Override
    public boolean isLegacyReadEnabled() {
        return configManager != null && gson != null;
    }

    @Override
    public boolean isWipeBarrierArmed(long accountHash) {
        GeHistoryWipeStateStore store = geHistoryWipeStateStoreSupplier != null
            ? geHistoryWipeStateStoreSupplier.get()
            : null;
        return store != null && store.isWipeBarrierArmed(accountHash);
    }

    @Override
    public String getLegacyNameKey(long accountHash) {
        return legacyNameKeysByHash != null ? legacyNameKeysByHash.get(accountHash) : null;
    }

    @Override
    public String getProfileDisplayName(long accountHash) {
        return profileDisplayNames != null ? profileDisplayNames.get(accountHash) : null;
    }

    @Override
    public void putLoadedProfileFileMs(long accountHash, long fileMs) {
        if (loadedProfileFileMs != null) {
            loadedProfileFileMs.put(accountHash, fileMs);
        }
    }
}
