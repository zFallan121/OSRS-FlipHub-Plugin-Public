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

import static com.osrsfliphub.GeLifecyclePluginConstants.*;

import com.google.gson.Gson;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;

final class GeLifecycleStatsTradesServices {
    private final GeLifecycleStatsTradesRuntimeContext context;
    private final GeLifecycleStatsTradesOps ops;

    private GeLifecycleLocalStatsServices localStatsServices;
    private GeHistoryAutoSyncService geHistoryAutoSyncService;
    private GeLifecycleProfileTradesServices profileTradesServices;
    private BackfilledProfilesStore backfilledProfilesStore;
    private OfferStampFallbackBuilder offerStampFallbackBuilder;

    GeLifecycleStatsTradesServices(
        Gson gson,
        GeLifecycleSharedState sharedState,
        Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier,
        Supplier<GeLifecycleItemServices> itemServicesSupplier,
        Supplier<ProfileStorageFacadeService> profileStorageFacadeServiceSupplier,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier,
        Supplier<AccountwideStatsAggregator> accountwideStatsAggregatorSupplier,
        Supplier<GeLifecycleProfileWorkflowService> profileWorkflowServiceSupplier,
        Supplier<Client> clientSupplier,
        Supplier<StatsRange> currentStatsRangeSupplier,
        Supplier<StatsItemSort> currentStatsSortSupplier,
        Consumer<Runnable> invokeOnClientThreadAction,
        LocalTradesLoadCoordinatorPluginHooks.LongConsumerWithScheduler executeOnSchedulerAction,
        Runnable triggerStatsRefreshAction,
        Runnable triggerPanelRefreshAction,
        Supplier<BackfillUploader> backfillUploaderSupplier,
        Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier,
        Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier,
        Supplier<LegacyLocalTradesFilterService> legacyLocalTradesFilterServiceSupplier,
        Supplier<LegacyLocalTradesStore> legacyLocalTradesStoreSupplier,
        Runnable markAccountwideUploadDirtyAction,
        Runnable scheduleRefreshSoonAction,
        ToLongFunction<Path> profileFileModifiedMsFn,
        Supplier<ConfigManager> configManagerSupplier
    ) {
        this.context = new GeLifecycleStatsTradesRuntimeContext(
            gson,
            sharedState,
            localTradesRuntimeServiceSupplier,
            itemServicesSupplier,
            profileStorageFacadeServiceSupplier,
            profileSelectionPresentationFacadeServiceSupplier,
            accountwideStatsAggregatorSupplier,
            profileWorkflowServiceSupplier,
            clientSupplier,
            currentStatsRangeSupplier,
            currentStatsSortSupplier,
            invokeOnClientThreadAction,
            executeOnSchedulerAction,
            triggerStatsRefreshAction,
            triggerPanelRefreshAction,
            backfillUploaderSupplier,
            uploadEventDispatchFacadeServiceSupplier,
            uploadBackfillDispatchServiceSupplier,
            legacyLocalTradesFilterServiceSupplier,
            legacyLocalTradesStoreSupplier,
            markAccountwideUploadDirtyAction,
            scheduleRefreshSoonAction,
            profileFileModifiedMsFn,
            configManagerSupplier
        );
        this.ops = new GeLifecycleStatsTradesOps(
            context.localTradesRuntimeServiceSupplier,
            context.itemServicesSupplier,
            this::getLocalTradeSessionFacadeService,
            this::getLocalStatsCacheService,
            context.markAccountwideUploadDirtyAction,
            context.scheduleRefreshSoonAction,
            context.triggerStatsRefreshAction,
            context.triggerPanelRefreshAction
        );
    }

    GeLifecycleLocalStatsServices getLocalStatsServices() {
        GeLifecycleLocalStatsServices services = localStatsServices;
        if (services != null) {
            return services;
        }
        services = new GeLifecycleLocalStatsServices(
            ACCOUNTWIDE_KEY,
            LOCAL_LIMIT_WINDOW_MS,
            LOCAL_LIMIT_FUTURE_TOLERANCE_MS,
            LOCAL_EVENT_BUCKET_MS,
            LOCAL_TRADES_LOAD_RETRY_MS,
            context.statsCacheByAccount,
            context.localTradeDeltasByAccount,
            context.localSessionStartByAccount,
            context.localStatsLock,
            context.localTradesRuntimeServiceSupplier,
            context.itemServicesSupplier,
            this::getAccountwideProfileKeyCollector,
            context.profileStorageFacadeServiceSupplier,
            context.profileSelectionPresentationFacadeServiceSupplier,
            context.accountwideStatsAggregatorSupplier,
            context.profileWorkflowServiceSupplier,
            context.clientSupplier,
            context.currentStatsRangeSupplier,
            context.currentStatsSortSupplier,
            context.invokeOnClientThreadAction,
            context.executeOnSchedulerAction,
            context.triggerStatsRefreshAction,
            context.triggerPanelRefreshAction
        );
        localStatsServices = services;
        return services;
    }

    LocalStatsCacheService getLocalStatsCacheService() {
        return getLocalStatsServices().getLocalStatsCacheService();
    }

    LocalStatsSnapshotService getLocalStatsSnapshotService() {
        return getLocalStatsServices().getLocalStatsSnapshotService();
    }

    LocalAccountSessionService getLocalAccountSessionService() {
        return getLocalStatsServices().getLocalAccountSessionService();
    }

    LocalTradeSessionFacadeService getLocalTradeSessionFacadeService() {
        return getLocalStatsServices().getLocalTradeSessionFacadeService();
    }

    LocalStatsViewService getLocalStatsViewService() {
        return getLocalStatsServices().getLocalStatsViewService();
    }

    LocalTradesLoadCoordinator getLocalTradesLoadCoordinator() {
        return getLocalStatsServices().getLocalTradesLoadCoordinator();
    }

    LocalAccountMergeService getLocalAccountMergeService() {
        return getLocalStatsServices().getLocalAccountMergeService();
    }

    LocalTradeDeltaRecorder getLocalTradeDeltaRecorder() {
        return getLocalStatsServices().getLocalTradeDeltaRecorder();
    }

    GeHistoryAutoSyncService getGeHistoryAutoSyncService() {
        GeHistoryAutoSyncService service = geHistoryAutoSyncService;
        if (service != null) {
            return service;
        }
        BackfillUploader uploader = context.backfillUploaderSupplier != null ? context.backfillUploaderSupplier.get() : null;
        service = new GeHistoryAutoSyncFactoryService(uploader).create(
            ACCOUNTWIDE_KEY,
            new GeHistoryAutoSyncPluginHooks(
                ops::ensureProfileLoaded,
                ops::ensureLocalSessionStart,
                ops::snapshotLocalTradeDeltas,
                ops::cacheItemName,
                ops::appendTradeDeltaPair,
                ops::applyDeltaToStatsCache,
                context.clientSupplier,
                context.uploadEventDispatchFacadeServiceSupplier,
                context.uploadBackfillDispatchServiceSupplier,
                ops::persistLocalTrades,
                ops::triggerStatsRefresh,
                ops::triggerPanelRefresh,
                System::currentTimeMillis
            )
        );
        geHistoryAutoSyncService = service;
        return service;
    }

    GeLifecycleProfileTradesServices getProfileTradesServices() {
        GeLifecycleProfileTradesServices services = profileTradesServices;
        if (services != null) {
            return services;
        }
        services = new GeLifecycleProfileTradesServices(
            ACCOUNTWIDE_KEY,
            MAX_LOCAL_TRADES,
            LOCAL_EVENT_BUCKET_MS,
            DUPLICATE_TRADE_WINDOW_MS,
            context.gson,
            context.legacyNameKeysByHash,
            context.loadedProfileFileMs,
            context.localTradeDeltasByAccount,
            context.localStatsLock,
            context.profileDisplayNames,
            context.profileStorageFacadeServiceSupplier,
            context.legacyLocalTradesFilterServiceSupplier,
            context.legacyLocalTradesStoreSupplier,
            context.profileSelectionPresentationFacadeServiceSupplier,
            context.localTradesRuntimeServiceSupplier,
            this::getLocalStatsCacheService,
            context.itemServicesSupplier,
            ops::markAccountwideUploadDirty,
            ops::scheduleRefreshSoon,
            ops::triggerStatsRefresh,
            context.profileFileModifiedMsFn
        );
        profileTradesServices = services;
        return services;
    }

    AccountwideProfileKeyCollector getAccountwideProfileKeyCollector() {
        return getProfileTradesServices().getAccountwideProfileKeyCollector();
    }

    LocalProfileTradesLoadService getLocalProfileTradesLoadService() {
        return getProfileTradesServices().getLocalProfileTradesLoadService();
    }

    BackfilledProfilesStore getBackfilledProfilesStore() {
        BackfilledProfilesStore store = backfilledProfilesStore;
        if (store != null) {
            return store;
        }
        store = new BackfilledProfilesStore(
            FliphubConfigGroups.CONFIG_GROUP,
            BACKFILLED_PROFILES_KEY,
            new BackfilledProfilesStorePluginHooks(context.configManagerSupplier)
        );
        backfilledProfilesStore = store;
        return store;
    }

    OfferStampFallbackBuilder getOfferStampFallbackBuilder() {
        OfferStampFallbackBuilder builder = offerStampFallbackBuilder;
        if (builder != null) {
            return builder;
        }
        builder = new OfferStampFallbackBuilder(new OfferStampFallbackPluginHooks(ops::getItemLookupService));
        offerStampFallbackBuilder = builder;
        return builder;
    }
}
