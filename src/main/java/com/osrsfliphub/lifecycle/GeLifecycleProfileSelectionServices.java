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
    private ProfileUiCoordinatorFactoryService profileUiCoordinatorFactoryService;

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
        ProfileStore store = profileStore;
        if (store != null) {
            return store;
        }
        store = new ProfileStore(resolve(gsonSupplier), profileDirName, legacyProfileDirName);
        profileStore = store;
        return store;
    }

    LegacyLocalTradesStore getLegacyLocalTradesStore() {
        LegacyLocalTradesStore store = legacyLocalTradesStore;
        if (store != null) {
            return store;
        }
        store = new LegacyLocalTradesStore(resolve(configManagerSupplier), resolve(gsonSupplier), configGroup);
        legacyLocalTradesStore = store;
        return store;
    }

    ProfileStorageFacadeService getProfileStorageFacadeService() {
        ProfileStorageFacadeService service = profileStorageFacadeService;
        if (service != null) {
            return service;
        }
        service = new ProfileStorageFacadeService(
            accountwideKey,
            maxLocalTrades,
            new ProfileStorageFacadePluginHooks(
                this::getProfileStore,
                this::getLegacyLocalTradesStore,
                resolve(configManagerSupplier),
                resolve(gsonSupplier),
                geHistoryWipeStateStoreSupplier,
                legacyNameKeysByHash,
                profileDisplayNames,
                loadedProfileFileMs
            )
        );
        profileStorageFacadeService = service;
        return service;
    }

    AccountwideStatsAggregator getAccountwideStatsAggregator() {
        return PluginInjectorBridge.get(AccountwideStatsAggregator.class);
    }

    ProfilePresentationService getProfilePresentationService() {
        ProfilePresentationService service = profilePresentationService;
        if (service != null) {
            return service;
        }
        service = new ProfilePresentationService(
            accountwideKey,
            accountwideKeyString,
            new ProfilePresentationPluginHooks(
                this::getProfileSelectionPresentationFacadeService,
                displayName -> {
                    GeLifecycleLocalTradesRuntimeService localTradesRuntime = resolve(localTradesRuntimeServiceSupplier);
                    return localTradesRuntime != null && localTradesRuntime.isPlaceholderDisplayName(displayName);
                }
            )
        );
        profilePresentationService = service;
        return service;
    }

    ProfileCatalogService getProfileCatalogService() {
        ProfileCatalogService service = profileCatalogService;
        if (service != null) {
            return service;
        }
        service = new ProfileCatalogService(getProfileStore(), getLegacyLocalTradesStore());
        profileCatalogService = service;
        return service;
    }

    ProfileSelectionPersistenceService getProfileSelectionPersistenceService() {
        ProfileSelectionPersistenceService service = profileSelectionPersistenceService;
        if (service != null) {
            return service;
        }
        service = new ProfileSelectionPersistenceService(
            new ProfileSelectionPersistencePluginHooks(configManagerSupplier),
            configGroup,
            legacyDevConfigGroup,
            profileSelectedKey,
            profileSelectionModeKey
        );
        profileSelectionPersistenceService = service;
        return service;
    }

    ProfileSelectionResolverService getProfileSelectionResolverService() {
        ProfileSelectionResolverService service = profileSelectionResolverService;
        if (service != null) {
            return service;
        }
        service = new ProfileSelectionResolverService(
            accountwideKey,
            accountwideKeyString,
            () -> {
                Client client = resolve(clientSupplier);
                return client != null && client.getGameState() == GameState.LOGGED_IN;
            }
        );
        profileSelectionResolverService = service;
        return service;
    }

    ProfileSelectionPresentationFacadeService getProfileSelectionPresentationFacadeService() {
        ProfileSelectionPresentationFacadeService service = profileSelectionPresentationFacadeService;
        if (service != null) {
            return service;
        }
        service = new ProfileSelectionPresentationFacadeService(
            profileSelection,
            profileDisplayNames,
            legacyNameKeysByHash,
            new ProfileSelectionPresentationFacadePluginHooks(
                this::getProfileSelectionResolverService,
                this::getProfilePresentationService,
                this::getProfileCatalogService,
                this::getLegacyLocalTradesStore,
                localAccountSessionServiceSupplier,
                linkSessionGuardServiceSupplier,
                () -> {
                    LocalTradeSessionFacadeService facade = resolve(localTradeSessionFacadeServiceSupplier);
                    return facade != null ? facade.resolveAccountHash() : -1L;
                }
            )
        );
        profileSelectionPresentationFacadeService = service;
        return service;
    }

    ProfileUiCoordinator getProfileUiCoordinator() {
        ProfileUiCoordinator coordinator = profileUiCoordinator;
        if (coordinator != null) {
            return coordinator;
        }
        coordinator = getProfileUiCoordinatorFactoryService().create(
            new ProfileUiCoordinatorPluginHooks(
                panelSupplier,
                this::getProfileSelectionPresentationFacadeService,
                uploadEventDispatchFacadeServiceSupplier
            )
        );
        profileUiCoordinator = coordinator;
        return coordinator;
    }

    ProfileUiCoordinatorFactoryService getProfileUiCoordinatorFactoryService() {
        ProfileUiCoordinatorFactoryService service = profileUiCoordinatorFactoryService;
        if (service != null) {
            return service;
        }
        service = new ProfileUiCoordinatorFactoryService();
        profileUiCoordinatorFactoryService = service;
        return service;
    }

    private <T> T resolve(Supplier<T> supplier) {
        return supplier != null ? supplier.get() : null;
    }
}
