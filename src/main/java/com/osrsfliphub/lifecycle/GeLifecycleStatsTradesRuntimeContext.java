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
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;

final class GeLifecycleStatsTradesRuntimeContext {
    final Gson gson;
    final Map<Long, LocalStatsCache> statsCacheByAccount;
    final Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount;
    final Map<Long, Long> localSessionStartByAccount;
    final Object localStatsLock;
    final Map<Long, String> legacyNameKeysByHash;
    final Map<Long, Long> loadedProfileFileMs;
    final Map<Long, String> profileDisplayNames;
    final Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier;
    final Supplier<GeLifecycleItemServices> itemServicesSupplier;
    final Supplier<ProfileStorageFacadeService> profileStorageFacadeServiceSupplier;
    final Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier;
    final Supplier<AccountwideStatsAggregator> accountwideStatsAggregatorSupplier;
    final Supplier<GeLifecycleProfileWorkflowService> profileWorkflowServiceSupplier;
    final Supplier<Client> clientSupplier;
    final Supplier<StatsRange> currentStatsRangeSupplier;
    final Supplier<StatsItemSort> currentStatsSortSupplier;
    final Consumer<Runnable> invokeOnClientThreadAction;
    final LongConsumerWithScheduler executeOnSchedulerAction;
    final Runnable triggerStatsRefreshAction;
    final Runnable triggerPanelRefreshAction;
    final Supplier<BackfillUploader> backfillUploaderSupplier;
    final Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier;
    final Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier;
    final Supplier<LegacyLocalTradesFilterService> legacyLocalTradesFilterServiceSupplier;
    final Supplier<LegacyLocalTradesStore> legacyLocalTradesStoreSupplier;
    final Runnable markAccountwideUploadDirtyAction;
    final Runnable scheduleRefreshSoonAction;
    final ToLongFunction<Path> profileFileModifiedMsFn;
    final Supplier<ConfigManager> configManagerSupplier;

    GeLifecycleStatsTradesRuntimeContext(
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
        this.gson = gson;
        this.statsCacheByAccount = sharedState.getStatsCacheByAccount();
        this.localTradeDeltasByAccount = sharedState.getLocalTradeDeltasByAccount();
        this.localSessionStartByAccount = sharedState.getLocalSessionStartByAccount();
        this.localStatsLock = sharedState.getLocalStatsLock();
        this.legacyNameKeysByHash = sharedState.getLegacyNameKeysByHash();
        this.loadedProfileFileMs = sharedState.getLoadedProfileFileMs();
        this.profileDisplayNames = sharedState.getProfileDisplayNames();
        this.localTradesRuntimeServiceSupplier = localTradesRuntimeServiceSupplier;
        this.itemServicesSupplier = itemServicesSupplier;
        this.profileStorageFacadeServiceSupplier = profileStorageFacadeServiceSupplier;
        this.profileSelectionPresentationFacadeServiceSupplier = profileSelectionPresentationFacadeServiceSupplier;
        this.accountwideStatsAggregatorSupplier = accountwideStatsAggregatorSupplier;
        this.profileWorkflowServiceSupplier = profileWorkflowServiceSupplier;
        this.clientSupplier = clientSupplier;
        this.currentStatsRangeSupplier = currentStatsRangeSupplier;
        this.currentStatsSortSupplier = currentStatsSortSupplier;
        this.invokeOnClientThreadAction = invokeOnClientThreadAction;
        this.executeOnSchedulerAction = executeOnSchedulerAction;
        this.triggerStatsRefreshAction = triggerStatsRefreshAction;
        this.triggerPanelRefreshAction = triggerPanelRefreshAction;
        this.backfillUploaderSupplier = backfillUploaderSupplier;
        this.uploadEventDispatchFacadeServiceSupplier = uploadEventDispatchFacadeServiceSupplier;
        this.uploadBackfillDispatchServiceSupplier = uploadBackfillDispatchServiceSupplier;
        this.legacyLocalTradesFilterServiceSupplier = legacyLocalTradesFilterServiceSupplier;
        this.legacyLocalTradesStoreSupplier = legacyLocalTradesStoreSupplier;
        this.markAccountwideUploadDirtyAction = markAccountwideUploadDirtyAction;
        this.scheduleRefreshSoonAction = scheduleRefreshSoonAction;
        this.profileFileModifiedMsFn = profileFileModifiedMsFn;
        this.configManagerSupplier = configManagerSupplier;
    }
}
