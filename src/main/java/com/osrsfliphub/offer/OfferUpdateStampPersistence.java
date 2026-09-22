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
import javax.inject.*;
import lombok.RequiredArgsConstructor;
import net.runelite.api.*;
import net.runelite.client.config.ConfigManager;

@Singleton
final class OfferUpdateStampPersistence {
    @RequiredArgsConstructor
    static final class LoadState {
        final long accountKey;
        final boolean loaded;
    }

    private static final int MIN_SLOT = 0;
    private static final int MAX_SLOT = 7;

    private final TradeSession tradeSession;
    private final AccountSession accountSession;
    private final String configGroup = FliphubConfigGroups.CONFIG_GROUP;
    private final String legacyDevConfigGroup = FliphubConfigGroups.LEGACY_DEV_CONFIG_GROUP;
    private final OfferUpdateStampConfigStore configStore;
    private final OfferUpdateStampLegacyMatcher legacyMatcher;
    private final Gson gson;
    private final ConfigManager configManager;
    private final Client client;

    @Inject
    OfferUpdateStampPersistence(
        Gson gson,
        ConfigManager configManager,
        Client client,
        PluginState state,
        TradeSession tradeSession,
        AccountSession accountSession
    ) {
        this.tradeSession = tradeSession;
        this.accountSession = accountSession;
        this.configStore = state.getOfferUpdateStampConfigStore();
        this.legacyMatcher = state.getOfferUpdateStampLegacyMatcher();
        this.gson = gson;
        this.configManager = configManager;
        this.client = client;
    }

    LoadState loadForCurrentAccount(Map<Integer, Stamp> destination,
                                    long loadedAccountKey,
                                    boolean loaded) {
        if (destination == null) {
            return new LoadState(loadedAccountKey, loaded);
        }
        if (!Access.loggedIn(client)) {
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

        String perAccountKey = configStore.perAccountKey(accountKey);
        String raw = configManager.getConfiguration(configGroup, perAccountKey);
        boolean migrated = false;

        if (Str.isBlank(raw)) {
            migrated = tryLoadMatchedLegacy(
                destination, configManager.getConfiguration(configGroup, configStore.legacyGlobalKey()), gson);
            if (!migrated) {
                migrated = tryLoadMatchedLegacy(
                    destination,
                    configManager.getConfiguration(legacyDevConfigGroup, configStore.legacyGlobalKey()),
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

    long persistForCurrentAccount(Map<Integer, Stamp> stamps, long knownAccountKey) {
        long accountKey = knownAccountKey;
        if (accountKey <= 0 && Access.loggedIn(client)) {
            accountKey = resolveCurrentAccountKey();
        }
        if (accountKey <= 0) {
            return knownAccountKey;
        }
        persistForAccount(stamps, accountKey, gson);
        return accountKey;
    }

    private boolean tryLoadMatchedLegacy(Map<Integer, Stamp> destination, String raw, Gson gson) {
        Map<Integer, Stamp> legacy = OfferUpdateStampStore.parse(raw, gson, MIN_SLOT, MAX_SLOT);
        if (legacy.isEmpty()) {
            return false;
        }
        GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
        if (!legacyMatcher.matchesCurrentOffers(legacy, offers)) {
            return false;
        }
        destination.putAll(legacy);
        return true;
    }

    private void persistForAccount(Map<Integer, Stamp> stamps, long accountKey, Gson gson) {
        if (accountKey <= 0) {
            return;
        }
        String json = OfferUpdateStampStore.serialize(stamps, gson);
        configManager.setConfiguration(configGroup, configStore.perAccountKey(accountKey), json);
    }

    private long resolveCurrentAccountKey() {
        long accountHash = tradeSession.resolveAccountHash();
        if (accountHash > 0) {
            return accountHash;
        }
        return accountSession.resolveLocalAccountKey();
    }

}
