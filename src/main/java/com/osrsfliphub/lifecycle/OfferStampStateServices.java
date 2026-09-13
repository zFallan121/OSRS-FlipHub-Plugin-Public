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

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.client.config.ConfigManager;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
final class OfferStampStateServices {
    private final String configGroup;
    private final String legacyDevConfigGroup;
    private final long loginGraceMs;
    private final Map<Integer, Stamp> offerUpdateStamps;
    private final Supplier<ConfigManager> configManagerSupplier;
    private final Supplier<PluginConfig> configSupplier;
    private final Supplier<OfferUpdateStampPersistence> offerUpdateStampPersistenceServiceSupplier;
    private final Supplier<OfferUpdateStamp> offerUpdateStampServiceSupplier;

    private volatile long offerUpdateStampsAccountKey = -1L;
    private volatile boolean offerUpdateStampsLoaded = false;
    @Getter
    private volatile long lastLoginMs;

    @Inject
    OfferStampStateServices(PluginState pluginState, ConfigManager configManager) {
        this(FliphubConfigGroups.CONFIG_GROUP,
            FliphubConfigGroups.LEGACY_DEV_CONFIG_GROUP,
            Const.LOGIN_GRACE_MS,
            pluginState.getOfferUpdateStamps(),
            () -> configManager,
            () -> Access.plugin().config,
            () -> Bridge.get(OfferUpdateStampPersistence.class),
            () -> Bridge.get(OfferUpdateStamp.class));
    }

    void resetForStartup() {
        offerUpdateStampsAccountKey = -1L;
        offerUpdateStampsLoaded = false;
        if (offerUpdateStamps != null) {
            offerUpdateStamps.clear();
        }
    }

    void migrateLegacyDevConfigIfNeeded() {
        ConfigManager configManager = resolveConfigManager();
        if (configManager == null) {
            return;
        }
        migrateLegacyDevConfigValue(configManager, "deviceId");
        migrateLegacyDevConfigValue(configManager, "sessionToken");
        migrateLegacyDevConfigValue(configManager, "signingSecret");
        migrateLegacyDevConfigValue(configManager, "bookmarks");
        migrateLegacyDevConfigValue(configManager, "hiddenItems");
    }

    void ensureDeviceId() {
        PluginConfig config = resolveConfig();
        ConfigManager configManager = resolveConfigManager();
        if (config == null || configManager == null) {
            return;
        }
        String deviceId = config.deviceId();
        if (deviceId == null || deviceId.trim().isEmpty()) {
            configManager.setConfiguration(configGroup, "deviceId", UUID.randomUUID().toString());
        }
    }

    void loadOfferUpdateTimesForCurrentAccount() {
        OfferUpdateStampPersistence persistence = resolveOfferUpdateStampPersistenceService();
        if (persistence == null || offerUpdateStamps == null) {
            return;
        }
        OfferUpdateStampPersistence.LoadState state = persistence.loadForCurrentAccount(
            offerUpdateStamps,
            offerUpdateStampsAccountKey,
            offerUpdateStampsLoaded
        );
        offerUpdateStampsAccountKey = state.accountKey;
        offerUpdateStampsLoaded = state.loaded;
    }

    void persistOfferUpdateTimes() {
        OfferUpdateStampPersistence persistence = resolveOfferUpdateStampPersistenceService();
        if (persistence == null || offerUpdateStamps == null) {
            return;
        }
        offerUpdateStampsAccountKey = persistence.persistForCurrentAccount(
            offerUpdateStamps,
            offerUpdateStampsAccountKey
        );
    }

    void trackOfferUpdate(int slot, OfferSnapshot prev, OfferSnapshot next) {
        OfferUpdateStamp service = resolveOfferUpdateStampService();
        if (service != null && offerUpdateStamps != null) {
            service.trackOfferUpdate(offerUpdateStamps, slot, prev, next);
        }
    }

    boolean isWithinLoginGrace() {
        if (lastLoginMs <= 0) {
            return false;
        }
        return System.currentTimeMillis() - lastLoginMs <= loginGraceMs;
    }

    boolean stampMatchesSnapshot(Stamp stamp, OfferSnapshot snapshot) {
        OfferUpdateStamp service = resolveOfferUpdateStampService();
        return service != null && service.stampMatchesSnapshot(stamp, snapshot);
    }

    long getOfferLastUpdateMs(int slot, GrandExchangeOffer offer) {
        loadOfferUpdateTimesForCurrentAccount();
        OfferUpdateStamp service = resolveOfferUpdateStampService();
        if (service == null || offerUpdateStamps == null) {
            return 0L;
        }
        return service.getOfferLastUpdateMs(offerUpdateStamps, slot, offer);
    }

    void resetOfferUpdateStampsOnLogout() {
        resetForStartup();
    }

    void setLastLoginNow() {
        lastLoginMs = System.currentTimeMillis();
    }

    private void migrateLegacyDevConfigValue(ConfigManager configManager, String key) {
        if (key == null || key.trim().isEmpty()) {
            return;
        }
        String current = configManager.getConfiguration(configGroup, key);
        if (current != null && !current.trim().isEmpty()) {
            return;
        }
        String legacy = configManager.getConfiguration(legacyDevConfigGroup, key);
        if (legacy == null || legacy.trim().isEmpty()) {
            return;
        }
        try {
            configManager.setConfiguration(configGroup, key, legacy);
        } catch (RuntimeException ignored) {
        }
    }

    private ConfigManager resolveConfigManager() {
        return configManagerSupplier != null ? configManagerSupplier.get() : null;
    }

    private PluginConfig resolveConfig() {
        return configSupplier != null ? configSupplier.get() : null;
    }

    private OfferUpdateStampPersistence resolveOfferUpdateStampPersistenceService() {
        return offerUpdateStampPersistenceServiceSupplier != null
            ? offerUpdateStampPersistenceServiceSupplier.get()
            : null;
    }

    private OfferUpdateStamp resolveOfferUpdateStampService() {
        return offerUpdateStampServiceSupplier != null ? offerUpdateStampServiceSupplier.get() : null;
    }
}
