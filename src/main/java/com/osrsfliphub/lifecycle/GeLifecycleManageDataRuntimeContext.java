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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;

final class GeLifecycleManageDataRuntimeContext {
    final long accountwideKey;
    final int geHistoryGroupId;
    final int geHistoryContainerChildId;
    final Object localStatsLock;
    final Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount;
    final Map<Long, Long> localSessionStartByAccount;
    final Map<Long, LocalStatsCache> statsCacheByAccount;
    final Set<Long> loadedProfiles;
    final Map<Long, Long> loadedProfileFileMs;
    final Map<Long, String> legacyNameKeysByHash;
    final Map<Long, String> profileDisplayNames;
    final Supplier<FlipHubPanel> panelSupplier;
    final Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier;
    final Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier;
    final Supplier<Client> clientSupplier;
    final Supplier<GeHistoryWidgetReadService> geHistoryWidgetReadServiceSupplier;
    final Supplier<GeHistoryCursorService> geHistoryCursorServiceSupplier;
    final Supplier<GeHistoryWipeStateStore> geHistoryWipeStateStoreSupplier;
    final Supplier<ProfileStorageFacadeService> profileStorageFacadeServiceSupplier;
    final Supplier<LegacyLocalTradesStore> legacyLocalTradesStoreSupplier;
    final Supplier<LocalStatsCacheService> localStatsCacheServiceSupplier;
    final Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier;
    final Supplier<AccountwideSummaryUploader> accountwideSummaryUploaderSupplier;
    final Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier;
    final Supplier<ExecutorService> ioExecutorSupplier;
    final Consumer<Runnable> invokeOnClientThreadConsumer;
    final Consumer<String> pushGameMessageConsumer;
    final Consumer<String> showManageDataErrorConsumer;
    final Runnable updateProfileOptionsUiAction;
    final Runnable updateProfileHeaderAction;
    final Runnable triggerPanelRefreshAction;
    final Runnable triggerStatsRefreshAction;
    final Gson gson;
    final ConfigManager configManager;

    GeLifecycleManageDataRuntimeContext(
        long accountwideKey,
        int geHistoryGroupId,
        int geHistoryContainerChildId,
        Object localStatsLock,
        Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount,
        Map<Long, Long> localSessionStartByAccount,
        Map<Long, LocalStatsCache> statsCacheByAccount,
        Set<Long> loadedProfiles,
        Map<Long, Long> loadedProfileFileMs,
        Map<Long, String> legacyNameKeysByHash,
        Map<Long, String> profileDisplayNames,
        Supplier<FlipHubPanel> panelSupplier,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier,
        Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier,
        Supplier<Client> clientSupplier,
        Supplier<GeHistoryWidgetReadService> geHistoryWidgetReadServiceSupplier,
        Supplier<GeHistoryCursorService> geHistoryCursorServiceSupplier,
        Supplier<GeHistoryWipeStateStore> geHistoryWipeStateStoreSupplier,
        Supplier<ProfileStorageFacadeService> profileStorageFacadeServiceSupplier,
        Supplier<LegacyLocalTradesStore> legacyLocalTradesStoreSupplier,
        Supplier<LocalStatsCacheService> localStatsCacheServiceSupplier,
        Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier,
        Supplier<AccountwideSummaryUploader> accountwideSummaryUploaderSupplier,
        Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier,
        Supplier<ExecutorService> ioExecutorSupplier,
        Consumer<Runnable> invokeOnClientThreadConsumer,
        Consumer<String> pushGameMessageConsumer,
        Consumer<String> showManageDataErrorConsumer,
        Runnable updateProfileOptionsUiAction,
        Runnable updateProfileHeaderAction,
        Runnable triggerPanelRefreshAction,
        Runnable triggerStatsRefreshAction,
        Gson gson,
        ConfigManager configManager
    ) {
        this.accountwideKey = accountwideKey;
        this.geHistoryGroupId = geHistoryGroupId;
        this.geHistoryContainerChildId = geHistoryContainerChildId;
        this.localStatsLock = localStatsLock;
        this.localTradeDeltasByAccount = localTradeDeltasByAccount;
        this.localSessionStartByAccount = localSessionStartByAccount;
        this.statsCacheByAccount = statsCacheByAccount;
        this.loadedProfiles = loadedProfiles;
        this.loadedProfileFileMs = loadedProfileFileMs;
        this.legacyNameKeysByHash = legacyNameKeysByHash;
        this.profileDisplayNames = profileDisplayNames;
        this.panelSupplier = panelSupplier;
        this.profileSelectionPresentationFacadeServiceSupplier = profileSelectionPresentationFacadeServiceSupplier;
        this.localAccountSessionServiceSupplier = localAccountSessionServiceSupplier;
        this.clientSupplier = clientSupplier;
        this.geHistoryWidgetReadServiceSupplier = geHistoryWidgetReadServiceSupplier;
        this.geHistoryCursorServiceSupplier = geHistoryCursorServiceSupplier;
        this.geHistoryWipeStateStoreSupplier = geHistoryWipeStateStoreSupplier;
        this.profileStorageFacadeServiceSupplier = profileStorageFacadeServiceSupplier;
        this.legacyLocalTradesStoreSupplier = legacyLocalTradesStoreSupplier;
        this.localStatsCacheServiceSupplier = localStatsCacheServiceSupplier;
        this.localTradesRuntimeServiceSupplier = localTradesRuntimeServiceSupplier;
        this.accountwideSummaryUploaderSupplier = accountwideSummaryUploaderSupplier;
        this.uploadBackfillDispatchServiceSupplier = uploadBackfillDispatchServiceSupplier;
        this.ioExecutorSupplier = ioExecutorSupplier;
        this.invokeOnClientThreadConsumer = invokeOnClientThreadConsumer;
        this.pushGameMessageConsumer = pushGameMessageConsumer;
        this.showManageDataErrorConsumer = showManageDataErrorConsumer;
        this.updateProfileOptionsUiAction = updateProfileOptionsUiAction;
        this.updateProfileHeaderAction = updateProfileHeaderAction;
        this.triggerPanelRefreshAction = triggerPanelRefreshAction;
        this.triggerStatsRefreshAction = triggerStatsRefreshAction;
        this.gson = gson;
        this.configManager = configManager;
    }
}
