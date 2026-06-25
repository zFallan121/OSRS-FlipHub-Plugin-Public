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
import net.runelite.api.GrandExchangeOffer;

final class OfferUpdateStampPersistenceService {
    static final class LoadState {
        final long accountKey;
        final boolean loaded;

        LoadState(long accountKey, boolean loaded) {
            this.accountKey = accountKey;
            this.loaded = loaded;
        }
    }

    interface Hooks {
        Gson gson();
        boolean hasConfigurationAccess();
        boolean isClientLoggedIn();
        long resolveCurrentAccountKey();
        GrandExchangeOffer[] currentOffers();
        String readConfiguration(String configGroup, String key);
        void writeConfiguration(String configGroup, String key, String value);
    }

    private static final int MIN_SLOT = 0;
    private static final int MAX_SLOT = 7;

    private final String configGroup;
    private final String legacyDevConfigGroup;
    private final OfferUpdateStampConfigStore configStore;
    private final OfferUpdateStampLegacyMatcher legacyMatcher;
    private final Hooks hooks;

    OfferUpdateStampPersistenceService(String configGroup,
                                       String legacyDevConfigGroup,
                                       OfferUpdateStampConfigStore configStore,
                                       OfferUpdateStampLegacyMatcher legacyMatcher,
                                       Hooks hooks) {
        this.configGroup = configGroup;
        this.legacyDevConfigGroup = legacyDevConfigGroup;
        this.configStore = configStore;
        this.legacyMatcher = legacyMatcher;
        this.hooks = hooks;
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
        GrandExchangeOffer[] offers = hooks != null ? hooks.currentOffers() : null;
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
        return hooks != null ? hooks.gson() : null;
    }

    private boolean hasConfigurationAccess() {
        return hooks != null && hooks.hasConfigurationAccess();
    }

    private boolean isClientLoggedIn() {
        return hooks != null && hooks.isClientLoggedIn();
    }

    private long resolveCurrentAccountKey() {
        return hooks != null ? hooks.resolveCurrentAccountKey() : -1L;
    }

    private String readConfiguration(String configGroup, String key) {
        return hooks != null ? hooks.readConfiguration(configGroup, key) : null;
    }

    private void writeConfiguration(String configGroup, String key, String value) {
        if (hooks != null) {
            hooks.writeConfiguration(configGroup, key, value);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
