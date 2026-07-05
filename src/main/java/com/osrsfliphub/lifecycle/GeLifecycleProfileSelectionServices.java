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
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.config.ConfigManager;

final class GeLifecycleProfileSelectionServices {
    private final long accountwideKey;
    private final String accountwideKeyString;
    private final int maxLocalTrades;
    private final String configGroup;
    private final String legacyDevConfigGroup;
    private final String profileSelectedKey;
    private final String profileSelectionModeKey;
    private final String profileDirName;
    private final String legacyProfileDirName;
    private final ProfileSelectionState profileSelection;
    private final Map<Long, String> profileDisplayNames;
    private final Map<Long, String> legacyNameKeysByHash;
    private final Map<Long, Long> loadedProfileFileMs;
    private final Supplier<Gson> gsonSupplier;
    private final Supplier<ConfigManager> configManagerSupplier;
    private final Supplier<Client> clientSupplier;
    private final Supplier<GeHistoryWipeStateStore> geHistoryWipeStateStoreSupplier;
    private final Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier;
    private final Supplier<LocalStatsCacheService> localStatsCacheServiceSupplier;
    private final Supplier<LocalStatsSnapshotService> localStatsSnapshotServiceSupplier;
    private final Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier;
    private final Supplier<LinkSessionGuardService> linkSessionGuardServiceSupplier;
    private final Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier;
    private final Supplier<FlipHubPanel> panelSupplier;
    private final Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier;

    private ProfileStore profileStore;
    private LegacyLocalTradesStore legacyLocalTradesStore;
    private ProfileStorageFacadeService profileStorageFacadeService;
    private AccountwideStatsAggregator accountwideStatsAggregator;
    private ProfilePresentationService profilePresentationService;
    private ProfileCatalogService profileCatalogService;
    private ProfileSelectionPersistenceService profileSelectionPersistenceService;
    private ProfileSelectionResolverService profileSelectionResolverService;
    private ProfileSelectionPresentationFacadeService profileSelectionPresentationFacadeService;
    private ProfileUiCoordinator profileUiCoordinator;

    GeLifecycleProfileSelectionServices(
        long accountwideKey,
        String accountwideKeyString,
        int maxLocalTrades,
        String configGroup,
        String legacyDevConfigGroup,
        String profileSelectedKey,
        String profileSelectionModeKey,
        String profileDirName,
        String legacyProfileDirName,
        ProfileSelectionState profileSelection,
        Map<Long, String> profileDisplayNames,
        Map<Long, String> legacyNameKeysByHash,
        Map<Long, Long> loadedProfileFileMs,
        Supplier<Gson> gsonSupplier,
        Supplier<ConfigManager> configManagerSupplier,
        Supplier<Client> clientSupplier,
        Supplier<GeHistoryWipeStateStore> geHistoryWipeStateStoreSupplier,
        Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier,
        Supplier<LocalStatsCacheService> localStatsCacheServiceSupplier,
        Supplier<LocalStatsSnapshotService> localStatsSnapshotServiceSupplier,
        Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier,
        Supplier<LinkSessionGuardService> linkSessionGuardServiceSupplier,
        Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier,
        Supplier<FlipHubPanel> panelSupplier,
        Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier
    ) {
        this.accountwideKey = accountwideKey;
        this.accountwideKeyString = accountwideKeyString;
        this.maxLocalTrades = maxLocalTrades;
        this.configGroup = configGroup;
        this.legacyDevConfigGroup = legacyDevConfigGroup;
        this.profileSelectedKey = profileSelectedKey;
        this.profileSelectionModeKey = profileSelectionModeKey;
        this.profileDirName = profileDirName;
        this.legacyProfileDirName = legacyProfileDirName;
        this.profileSelection = profileSelection;
        this.profileDisplayNames = profileDisplayNames;
        this.legacyNameKeysByHash = legacyNameKeysByHash;
        this.loadedProfileFileMs = loadedProfileFileMs;
        this.gsonSupplier = gsonSupplier;
        this.configManagerSupplier = configManagerSupplier;
        this.clientSupplier = clientSupplier;
        this.geHistoryWipeStateStoreSupplier = geHistoryWipeStateStoreSupplier;
        this.localTradesRuntimeServiceSupplier = localTradesRuntimeServiceSupplier;
        this.localStatsCacheServiceSupplier = localStatsCacheServiceSupplier;
        this.localStatsSnapshotServiceSupplier = localStatsSnapshotServiceSupplier;
        this.localAccountSessionServiceSupplier = localAccountSessionServiceSupplier;
        this.linkSessionGuardServiceSupplier = linkSessionGuardServiceSupplier;
        this.localTradeSessionFacadeServiceSupplier = localTradeSessionFacadeServiceSupplier;
        this.panelSupplier = panelSupplier;
        this.uploadEventDispatchFacadeServiceSupplier = uploadEventDispatchFacadeServiceSupplier;
    }

    ProfileStore getProfileStore() {
        return PluginInjectorBridge.get(ProfileStore.class);
    }

    LegacyLocalTradesStore getLegacyLocalTradesStore() {
        return PluginInjectorBridge.get(LegacyLocalTradesStore.class);
    }

    ProfileStorageFacadeService getProfileStorageFacadeService() {
        return PluginInjectorBridge.get(ProfileStorageFacadeService.class);
    }

    AccountwideStatsAggregator getAccountwideStatsAggregator() {
        return PluginInjectorBridge.get(AccountwideStatsAggregator.class);
    }

    ProfilePresentationService getProfilePresentationService() {
        return PluginInjectorBridge.get(ProfilePresentationService.class);
    }

    ProfileCatalogService getProfileCatalogService() {
        return PluginInjectorBridge.get(ProfileCatalogService.class);
    }

    ProfileSelectionPersistenceService getProfileSelectionPersistenceService() {
        return PluginInjectorBridge.get(ProfileSelectionPersistenceService.class);
    }

    ProfileSelectionResolverService getProfileSelectionResolverService() {
        return PluginInjectorBridge.get(ProfileSelectionResolverService.class);
    }

    ProfileSelectionPresentationFacadeService getProfileSelectionPresentationFacadeService() {
        return PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class);
    }

    ProfileUiCoordinator getProfileUiCoordinator() {
        return PluginInjectorBridge.get(ProfileUiCoordinator.class);
    }

    private <T> T resolve(Supplier<T> supplier) {
        return supplier != null ? supplier.get() : null;
    }
}
