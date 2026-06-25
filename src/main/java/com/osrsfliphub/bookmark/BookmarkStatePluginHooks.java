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

import java.util.function.Supplier;
import net.runelite.client.config.ConfigManager;

final class BookmarkStatePluginHooks implements BookmarkStateService.Hooks {
    private final Supplier<ConfigManager> configManagerSupplier;
    private final Supplier<PluginConfig> configSupplier;
    private final BookmarkConfigStore configStore;
    private final Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier;
    private final Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier;
    private final String configGroup;

    BookmarkStatePluginHooks(
        Supplier<ConfigManager> configManagerSupplier,
        Supplier<PluginConfig> configSupplier,
        BookmarkConfigStore configStore,
        Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier,
        Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier,
        String configGroup
    ) {
        this.configManagerSupplier = configManagerSupplier;
        this.configSupplier = configSupplier;
        this.configStore = configStore;
        this.localAccountSessionServiceSupplier = localAccountSessionServiceSupplier;
        this.localTradeSessionFacadeServiceSupplier = localTradeSessionFacadeServiceSupplier;
        this.configGroup = configGroup;
    }

    @Override
    public String readBookmarksForProfile(long normalizedProfileKey) {
        if (configStore == null) {
            return "";
        }
        ConfigManager configManager = resolveConfigManager();
        if (configManager == null) {
            PluginConfig config = configSupplier != null ? configSupplier.get() : null;
            return configStore.isAccountwide(normalizedProfileKey) && config != null
                ? config.bookmarks()
                : "";
        }
        return configManager.getConfiguration(configGroup, configStore.buildConfigKey(normalizedProfileKey));
    }

    @Override
    public void persistBookmarksForProfile(long normalizedProfileKey, String serializedBookmarkIds) {
        ConfigManager configManager = resolveConfigManager();
        if (configManager == null || configStore == null) {
            return;
        }
        configManager.setConfiguration(
            configGroup,
            configStore.buildConfigKey(normalizedProfileKey),
            serializedBookmarkIds
        );
    }

    @Override
    public Long resolveActiveProfileKey() {
        LocalAccountSessionService localAccountSessionService = localAccountSessionServiceSupplier != null
            ? localAccountSessionServiceSupplier.get()
            : null;
        if (localAccountSessionService != null) {
            long localAccountKey = localAccountSessionService.resolveLocalAccountKey();
            if (localAccountKey > 0) {
                return localAccountKey;
            }
        }
        LocalTradeSessionFacadeService localTradeSessionFacadeService = localTradeSessionFacadeServiceSupplier != null
            ? localTradeSessionFacadeServiceSupplier.get()
            : null;
        if (localTradeSessionFacadeService != null) {
            long accountHash = localTradeSessionFacadeService.resolveAccountHash();
            if (accountHash > 0) {
                return accountHash;
            }
        }
        return null;
    }

    private ConfigManager resolveConfigManager() {
        return configManagerSupplier != null ? configManagerSupplier.get() : null;
    }
}
