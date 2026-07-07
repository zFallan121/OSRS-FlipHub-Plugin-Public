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

    private GeHistoryAutoSyncService geHistoryAutoSyncService;
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
        LongConsumerWithScheduler executeOnSchedulerAction,
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

    LocalStatsCacheService getLocalStatsCacheService() {
        return PluginInjectorBridge.get(LocalStatsCacheService.class);
    }

    LocalStatsSnapshotService getLocalStatsSnapshotService() {
        return PluginInjectorBridge.get(LocalStatsSnapshotService.class);
    }

    LocalAccountSessionService getLocalAccountSessionService() {
        return PluginInjectorBridge.get(LocalAccountSessionService.class);
    }

    LocalTradeSessionFacadeService getLocalTradeSessionFacadeService() {
        return PluginInjectorBridge.get(LocalTradeSessionFacadeService.class);
    }

    LocalStatsViewService getLocalStatsViewService() {
        return PluginInjectorBridge.get(LocalStatsViewService.class);
    }

    LocalTradesLoadCoordinator getLocalTradesLoadCoordinator() {
        return PluginInjectorBridge.get(LocalTradesLoadCoordinator.class);
    }

    LocalAccountMergeService getLocalAccountMergeService() {
        return PluginInjectorBridge.get(LocalAccountMergeService.class);
    }

    LocalTradeDeltaRecorder getLocalTradeDeltaRecorder() {
        return PluginInjectorBridge.get(LocalTradeDeltaRecorder.class);
    }

    GeHistoryAutoSyncService getGeHistoryAutoSyncService() {
        return PluginInjectorBridge.get(GeHistoryAutoSyncService.class);
    }

    AccountwideProfileKeyCollector getAccountwideProfileKeyCollector() {
        return PluginInjectorBridge.get(AccountwideProfileKeyCollector.class);
    }

    LocalProfileTradesLoadService getLocalProfileTradesLoadService() {
        return PluginInjectorBridge.get(LocalProfileTradesLoadService.class);
    }

    BackfilledProfilesStore getBackfilledProfilesStore() {
        return PluginInjectorBridge.get(BackfilledProfilesStore.class);
    }

    OfferStampFallbackBuilder getOfferStampFallbackBuilder() {
        return PluginInjectorBridge.get(OfferStampFallbackBuilder.class);
    }
}
