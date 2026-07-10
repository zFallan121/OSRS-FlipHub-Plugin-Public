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
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.client.config.ConfigManager;

@Singleton
final class OfferUpdateStampPersistenceService {
    static final class LoadState {
        final long accountKey;
        final boolean loaded;

        LoadState(long accountKey, boolean loaded) {
            this.accountKey = accountKey;
            this.loaded = loaded;
        }
    }

    private static final int MIN_SLOT = 0;
    private static final int MAX_SLOT = 7;

    private final String configGroup = FliphubConfigGroups.CONFIG_GROUP;
    private final String legacyDevConfigGroup = FliphubConfigGroups.LEGACY_DEV_CONFIG_GROUP;
    private final OfferUpdateStampConfigStore configStore;
    private final OfferUpdateStampLegacyMatcher legacyMatcher;
    private final Gson gson;
    private final ConfigManager configManager;
    private final Client client;

    @Inject
    OfferUpdateStampPersistenceService(Gson gson, ConfigManager configManager, Client client, PluginState state) {
        this.configStore = state.getOfferUpdateStampConfigStore();
        this.legacyMatcher = state.getOfferUpdateStampLegacyMatcher();
        this.gson = gson;
        this.configManager = configManager;
        this.client = client;
    }

    void loadLegacyGlobal(Map<Integer, OfferUpdateStamp> destination) {
        if (destination == null) {
            return;
        }
        destination.clear();
        Gson gson = gson();
        if (gson == null || configStore == null) {
            return;
        }
        String raw = readConfiguration(configGroup, configStore.legacyGlobalKey());
        destination.putAll(OfferUpdateStampStore.parse(raw, gson, MIN_SLOT, MAX_SLOT));
    }

    LoadState loadForCurrentAccount(Map<Integer, OfferUpdateStamp> destination,
                                    long loadedAccountKey,
                                    boolean loaded) {
        if (destination == null) {
            return new LoadState(loadedAccountKey, loaded);
        }
        if (!isClientLoggedIn()) {
            return new LoadState(loadedAccountKey, loaded);
        }
        long accountKey = resolveCurrentAccountKey();
        if (accountKey <= 0) {
            return new LoadState(loadedAccountKey, loaded);
        }
        if (loaded && loadedAccountKey == accountKey) {
            return new LoadState(loadedAccountKey, loaded);
        }

        destination.clear();
        Gson gson = gson();
        if (gson == null || configStore == null || !hasConfigurationAccess()) {
            return new LoadState(accountKey, false);
        }

        String perAccountKey = configStore.perAccountKey(accountKey);
        String raw = readConfiguration(configGroup, perAccountKey);
        boolean migrated = false;

        if (isBlank(raw)) {
            migrated = tryLoadMatchedLegacy(destination, readConfiguration(configGroup, configStore.legacyGlobalKey()), gson);
            if (!migrated) {
                migrated = tryLoadMatchedLegacy(
                    destination,
                    readConfiguration(legacyDevConfigGroup, configStore.legacyGlobalKey()),
                    gson
                );
            }
        } else {
            destination.putAll(OfferUpdateStampStore.parse(raw, gson, MIN_SLOT, MAX_SLOT));
        }

        if (migrated && !destination.isEmpty()) {
            persistForAccount(destination, accountKey, gson);
        }
        return new LoadState(accountKey, true);
    }

    long persistForCurrentAccount(Map<Integer, OfferUpdateStamp> stamps, long knownAccountKey) {
        Gson gson = gson();
        if (gson == null || configStore == null || !hasConfigurationAccess()) {
            return knownAccountKey;
        }
        long accountKey = knownAccountKey;
        if (accountKey <= 0 && isClientLoggedIn()) {
            accountKey = resolveCurrentAccountKey();
        }
        if (accountKey <= 0) {
            return knownAccountKey;
        }
        persistForAccount(stamps, accountKey, gson);
        return accountKey;
    }

    private boolean tryLoadMatchedLegacy(Map<Integer, OfferUpdateStamp> destination, String raw, Gson gson) {
        Map<Integer, OfferUpdateStamp> legacy = OfferUpdateStampStore.parse(raw, gson, MIN_SLOT, MAX_SLOT);
        if (legacy.isEmpty() || legacyMatcher == null) {
            return false;
        }
        GrandExchangeOffer[] offers = client != null ? client.getGrandExchangeOffers() : null;
        if (!legacyMatcher.matchesCurrentOffers(legacy, offers)) {
            return false;
        }
        destination.putAll(legacy);
        return true;
    }

    private void persistForAccount(Map<Integer, OfferUpdateStamp> stamps, long accountKey, Gson gson) {
        if (accountKey <= 0 || configStore == null || !hasConfigurationAccess()) {
            return;
        }
        String json = OfferUpdateStampStore.serialize(stamps, gson);
        writeConfiguration(configGroup, configStore.perAccountKey(accountKey), json);
    }

    private Gson gson() {
        return gson;
    }

    private boolean hasConfigurationAccess() {
        return configManager != null;
    }

    private boolean isClientLoggedIn() {
        return client != null && client.getGameState() == GameState.LOGGED_IN;
    }

    private long resolveCurrentAccountKey() {
        LocalTradeSessionFacadeService localTradeSessionFacadeService =
            PluginInjectorBridge.get(LocalTradeSessionFacadeService.class);
        if (localTradeSessionFacadeService != null) {
            long accountHash = localTradeSessionFacadeService.resolveAccountHash();
            if (accountHash > 0) {
                return accountHash;
            }
        }
        LocalAccountSessionService localAccountSessionService =
            PluginInjectorBridge.get(LocalAccountSessionService.class);
        return localAccountSessionService != null ? localAccountSessionService.resolveLocalAccountKey() : -1L;
    }

    private String readConfiguration(String configGroup, String key) {
        return configManager != null ? configManager.getConfiguration(configGroup, key) : null;
    }

    private void writeConfiguration(String configGroup, String key, String value) {
        if (configManager != null) {
            configManager.setConfiguration(configGroup, key, value);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
