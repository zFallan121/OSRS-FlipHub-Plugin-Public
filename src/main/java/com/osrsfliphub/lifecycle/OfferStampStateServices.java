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
import lombok.*;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.client.config.ConfigManager;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class OfferStampStateServices {
    private static final long LOGIN_GRACE_MS = 60_000L;

    private final PluginState state;
    // Providers, so a unit test can leave out whichever it does not exercise.
    private final Provider<ConfigManager> configManager;
    private final Provider<PluginConfig> config;
    private final Provider<OfferUpdateStampPersistence> persistence;
    private final Provider<OfferUpdateStamp> stamps;

    private volatile long offerUpdateStampsAccountKey = -1L;
    private volatile boolean offerUpdateStampsLoaded = false;
    @Getter
    private volatile long lastLoginMs;

    void resetForStartup() {
        offerUpdateStampsAccountKey = -1L;
        offerUpdateStampsLoaded = false;
        state.getOfferUpdateStamps().clear();
    }

    void migrateLegacyDevConfigIfNeeded() {
        ConfigManager manager = configManager.get();
        if (manager == null) {
            return;
        }
        migrateLegacyDevConfigValue(manager, "deviceId");
        migrateLegacyDevConfigValue(manager, "sessionToken");
        migrateLegacyDevConfigValue(manager, "signingSecret");
        migrateLegacyDevConfigValue(manager, "bookmarks");
        migrateLegacyDevConfigValue(manager, "hiddenItems");
    }

    void ensureDeviceId() {
        PluginConfig pluginConfig = config.get();
        ConfigManager manager = configManager.get();
        if (pluginConfig != null && manager != null && Str.isBlank(pluginConfig.deviceId())) {
            manager.setConfiguration(FliphubConfigGroups.CONFIG_GROUP, "deviceId", UUID.randomUUID().toString());
        }
    }

    void loadOfferUpdateTimesForCurrentAccount() {
        OfferUpdateStampPersistence store = persistence.get();
        if (store == null) {
            return;
        }
        OfferUpdateStampPersistence.LoadState loaded = store.loadForCurrentAccount(
            state.getOfferUpdateStamps(),
            offerUpdateStampsAccountKey,
            offerUpdateStampsLoaded
        );
        offerUpdateStampsAccountKey = loaded.accountKey;
        offerUpdateStampsLoaded = loaded.loaded;
    }

    void persistOfferUpdateTimes() {
        OfferUpdateStampPersistence store = persistence.get();
        if (store != null) {
            offerUpdateStampsAccountKey = store.persistForCurrentAccount(
                state.getOfferUpdateStamps(), offerUpdateStampsAccountKey);
        }
    }

    void trackOfferUpdate(int slot, OfferSnapshot prev, OfferSnapshot next) {
        OfferUpdateStamp service = stamps.get();
        if (service != null) {
            service.trackOfferUpdate(state.getOfferUpdateStamps(), slot, prev, next);
        }
    }

    boolean isWithinLoginGrace() {
        return lastLoginMs > 0 && System.currentTimeMillis() - lastLoginMs <= LOGIN_GRACE_MS;
    }

    boolean stampMatchesSnapshot(Stamp stamp, OfferSnapshot snapshot) {
        OfferUpdateStamp service = stamps.get();
        return service != null && service.stampMatchesSnapshot(stamp, snapshot);
    }

    long getOfferLastUpdateMs(int slot, GrandExchangeOffer offer) {
        loadOfferUpdateTimesForCurrentAccount();
        OfferUpdateStamp service = stamps.get();
        return service == null ? 0L : service.getOfferLastUpdateMs(state.getOfferUpdateStamps(), slot, offer);
    }

    void resetOfferUpdateStampsOnLogout() {
        resetForStartup();
    }

    void setLastLoginNow() {
        lastLoginMs = System.currentTimeMillis();
    }

    private void migrateLegacyDevConfigValue(ConfigManager manager, String key) {
        String current = manager.getConfiguration(FliphubConfigGroups.CONFIG_GROUP, key);
        if (Str.hasText(current)) {
            return;
        }
        String legacy = manager.getConfiguration(FliphubConfigGroups.LEGACY_DEV_CONFIG_GROUP, key);
        if (Str.isBlank(legacy)) {
            return;
        }
        try {
            manager.setConfiguration(FliphubConfigGroups.CONFIG_GROUP, key, legacy);
        } catch (RuntimeException ignored) {
            // The old value stays where it was, and the next start tries the copy again.
        }
    }
}
