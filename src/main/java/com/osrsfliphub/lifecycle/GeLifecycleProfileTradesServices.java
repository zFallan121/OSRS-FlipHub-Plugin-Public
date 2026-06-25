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
import java.util.function.ToLongFunction;

final class GeLifecycleProfileTradesServices {
    private final long accountwideKey;
    private final int maxLocalTrades;
    private final long localEventBucketMs;
    private final long duplicateTradeWindowMs;
    private final Gson gson;
    private final Map<Long, String> legacyNameKeysByHash;
    private final Map<Long, Long> loadedProfileFileMs;
    private final Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount;
    private final Object localStatsLock;
    private final Map<Long, String> profileDisplayNames;
    private final Supplier<ProfileStorageFacadeService> profileStorageFacadeServiceSupplier;
    private final Supplier<LegacyLocalTradesFilterService> legacyLocalTradesFilterServiceSupplier;
    private final Supplier<LegacyLocalTradesStore> legacyLocalTradesStoreSupplier;
    private final Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier;
    private final Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier;
    private final Supplier<LocalStatsCacheService> localStatsCacheServiceSupplier;
    private final Supplier<GeLifecycleItemServices> itemServicesSupplier;
    private final Runnable markAccountwideUploadDirtyAction;
    private final Runnable scheduleRefreshSoonAction;
    private final Runnable triggerStatsRefreshAction;
    private final ToLongFunction<Path> profileFileModifiedMsFn;

    private AccountwideProfileKeyCollector accountwideProfileKeyCollector;
    private AccountwideTradesMergeService accountwideTradesMergeService;
    private LocalProfileTradesLoadService localProfileTradesLoadService;
    private ProfileTradesLoader profileTradesLoader;

    GeLifecycleProfileTradesServices(
        long accountwideKey,
        int maxLocalTrades,
        long localEventBucketMs,
        long duplicateTradeWindowMs,
        Gson gson,
        Map<Long, String> legacyNameKeysByHash,
        Map<Long, Long> loadedProfileFileMs,
        Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount,
        Object localStatsLock,
        Map<Long, String> profileDisplayNames,
        Supplier<ProfileStorageFacadeService> profileStorageFacadeServiceSupplier,
        Supplier<LegacyLocalTradesFilterService> legacyLocalTradesFilterServiceSupplier,
        Supplier<LegacyLocalTradesStore> legacyLocalTradesStoreSupplier,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier,
        Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier,
        Supplier<LocalStatsCacheService> localStatsCacheServiceSupplier,
        Supplier<GeLifecycleItemServices> itemServicesSupplier,
        Runnable markAccountwideUploadDirtyAction,
        Runnable scheduleRefreshSoonAction,
        Runnable triggerStatsRefreshAction,
        ToLongFunction<Path> profileFileModifiedMsFn
    ) {
        this.accountwideKey = accountwideKey;
        this.maxLocalTrades = maxLocalTrades;
        this.localEventBucketMs = localEventBucketMs;
        this.duplicateTradeWindowMs = duplicateTradeWindowMs;
        this.gson = gson;
        this.legacyNameKeysByHash = legacyNameKeysByHash;
        this.loadedProfileFileMs = loadedProfileFileMs;
        this.localTradeDeltasByAccount = localTradeDeltasByAccount;
        this.localStatsLock = localStatsLock;
        this.profileDisplayNames = profileDisplayNames;
        this.profileStorageFacadeServiceSupplier = profileStorageFacadeServiceSupplier;
        this.legacyLocalTradesFilterServiceSupplier = legacyLocalTradesFilterServiceSupplier;
        this.legacyLocalTradesStoreSupplier = legacyLocalTradesStoreSupplier;
        this.profileSelectionPresentationFacadeServiceSupplier = profileSelectionPresentationFacadeServiceSupplier;
        this.localTradesRuntimeServiceSupplier = localTradesRuntimeServiceSupplier;
        this.localStatsCacheServiceSupplier = localStatsCacheServiceSupplier;
        this.itemServicesSupplier = itemServicesSupplier;
        this.markAccountwideUploadDirtyAction = markAccountwideUploadDirtyAction;
        this.scheduleRefreshSoonAction = scheduleRefreshSoonAction;
        this.triggerStatsRefreshAction = triggerStatsRefreshAction;
        this.profileFileModifiedMsFn = profileFileModifiedMsFn;
    }

    AccountwideProfileKeyCollector getAccountwideProfileKeyCollector() {
        AccountwideProfileKeyCollector collector = accountwideProfileKeyCollector;
        if (collector != null) {
            return collector;
        }
        collector = new AccountwideProfileKeyCollector();
        accountwideProfileKeyCollector = collector;
        return collector;
    }

    AccountwideTradesMergeService getAccountwideTradesMergeService() {
        AccountwideTradesMergeService service = accountwideTradesMergeService;
        if (service != null) {
            return service;
        }
        service = new AccountwideTradesMergeService(
            gson,
            maxLocalTrades,
            new AccountwideTradesMergePluginHooks(
                profileStorageFacadeServiceSupplier,
                legacyLocalTradesFilterServiceSupplier,
                legacyLocalTradesStoreSupplier
            )
        );
        accountwideTradesMergeService = service;
        return service;
    }

    LocalProfileTradesLoadService getLocalProfileTradesLoadService() {
        LocalProfileTradesLoadService service = localProfileTradesLoadService;
        if (service != null) {
            return service;
        }
        service = new LocalProfileTradesLoadService(
            accountwideKey,
            new LocalProfileTradesLoadPluginHooks(
                () -> gson,
                this::getProfileTradesLoader,
                legacyNameKeysByHash,
                maxLocalTrades,
                localEventBucketMs,
                duplicateTradeWindowMs,
                loadedProfileFileMs,
                localTradeDeltasByAccount,
                localStatsLock,
                localStatsCacheServiceSupplier,
                profileDisplayNames,
                this::getItemLookupService,
                accountKey -> getLocalTradesRuntimeService().persistLocalTrades(accountKey),
                this::markAccountwideUploadDirty,
                this::scheduleRefreshSoon,
                this::triggerStatsRefresh
            )
        );
        localProfileTradesLoadService = service;
        return service;
    }

    ProfileTradesLoader getProfileTradesLoader() {
        ProfileTradesLoader loader = profileTradesLoader;
        if (loader != null) {
            return loader;
        }
        loader = new ProfileTradesLoader(
            accountwideKey,
            new ProfileTradesLoaderPluginHooks(
                profileStorageFacadeServiceSupplier,
                profileFileModifiedMsFn,
                this::getAccountwideTradesMergeService,
                displayName -> getLocalTradesRuntimeService().isPlaceholderDisplayName(displayName),
                profileSelectionPresentationFacadeServiceSupplier
            )
        );
        profileTradesLoader = loader;
        return loader;
    }

    private GeLifecycleLocalTradesRuntimeService getLocalTradesRuntimeService() {
        return localTradesRuntimeServiceSupplier.get();
    }

    private ItemLookupService getItemLookupService() {
        GeLifecycleItemServices services = itemServicesSupplier.get();
        return services != null ? services.getItemLookupService() : null;
    }

    private void markAccountwideUploadDirty() {
        if (markAccountwideUploadDirtyAction != null) {
            markAccountwideUploadDirtyAction.run();
        }
    }

    private void scheduleRefreshSoon() {
        if (scheduleRefreshSoonAction != null) {
            scheduleRefreshSoonAction.run();
        }
    }

    private void triggerStatsRefresh() {
        if (triggerStatsRefreshAction != null) {
            triggerStatsRefreshAction.run();
        }
    }
}
