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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;

final class GeLifecycleManageDataFactory {
    private final Object localStatsLock;
    private final Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount;
    private final Map<Long, Long> localSessionStartByAccount;
    private final Map<Long, LocalStatsCache> statsCacheByAccount;
    private final Set<Long> loadedProfiles;
    private final Map<Long, Long> loadedProfileFileMs;
    private final Map<Long, String> legacyNameKeysByHash;
    private final Map<Long, String> profileDisplayNames;
    private final Supplier<FlipHubPanel> panelSupplier;
    private final Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier;
    private final Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier;
    private final Supplier<Client> clientSupplier;
    private final Supplier<GeHistoryWidgetReadService> geHistoryWidgetReadServiceSupplier;
    private final Supplier<GeHistoryCursorService> geHistoryCursorServiceSupplier;
    private final Supplier<GeHistoryWipeStateStore> geHistoryWipeStateStoreSupplier;
    private final Supplier<ProfileStorageFacadeService> profileStorageFacadeServiceSupplier;
    private final Supplier<LegacyLocalTradesStore> legacyLocalTradesStoreSupplier;
    private final Supplier<LocalStatsCacheService> localStatsCacheServiceSupplier;
    private final Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier;
    private final Supplier<AccountwideSummaryUploader> accountwideSummaryUploaderSupplier;
    private final Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier;
    private final Supplier<ExecutorService> ioExecutorSupplier;
    private final Consumer<Runnable> invokeOnClientThreadConsumer;
    private final WebsiteStatsWipePluginHooks.WipeStatsInvoker wipeStatsInvoker;
    private final Consumer<String> pushGameMessageConsumer;
    private final Consumer<String> showManageDataErrorConsumer;
    private final Runnable updateProfileOptionsUiAction;
    private final Runnable updateProfileHeaderAction;
    private final Runnable triggerPanelRefreshAction;
    private final Runnable triggerStatsRefreshAction;
    private final Gson gson;
    private final Supplier<ConfigManager> configManagerSupplier;

    private GeLifecycleManageDataServices manageDataServices;

    GeLifecycleManageDataFactory(
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
        WebsiteStatsWipePluginHooks.WipeStatsInvoker wipeStatsInvoker,
        Consumer<String> pushGameMessageConsumer,
        Consumer<String> showManageDataErrorConsumer,
        Runnable updateProfileOptionsUiAction,
        Runnable updateProfileHeaderAction,
        Runnable triggerPanelRefreshAction,
        Runnable triggerStatsRefreshAction,
        Gson gson,
        Supplier<ConfigManager> configManagerSupplier
    ) {
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
        this.wipeStatsInvoker = wipeStatsInvoker;
        this.pushGameMessageConsumer = pushGameMessageConsumer;
        this.showManageDataErrorConsumer = showManageDataErrorConsumer;
        this.updateProfileOptionsUiAction = updateProfileOptionsUiAction;
        this.updateProfileHeaderAction = updateProfileHeaderAction;
        this.triggerPanelRefreshAction = triggerPanelRefreshAction;
        this.triggerStatsRefreshAction = triggerStatsRefreshAction;
        this.gson = gson;
        this.configManagerSupplier = configManagerSupplier;
    }

    GeLifecycleManageDataServices getManageDataServices() {
        GeLifecycleManageDataServices services = manageDataServices;
        if (services != null) {
            return services;
        }
        services = new GeLifecycleManageDataServices(
            ACCOUNTWIDE_KEY,
            GE_HISTORY_GROUP_ID,
            GE_HISTORY_CONTAINER_CHILD_ID,
            localStatsLock,
            localTradeDeltasByAccount,
            localSessionStartByAccount,
            statsCacheByAccount,
            loadedProfiles,
            loadedProfileFileMs,
            legacyNameKeysByHash,
            profileDisplayNames,
            panelSupplier,
            profileSelectionPresentationFacadeServiceSupplier,
            localAccountSessionServiceSupplier,
            clientSupplier,
            geHistoryWidgetReadServiceSupplier,
            geHistoryCursorServiceSupplier,
            geHistoryWipeStateStoreSupplier,
            profileStorageFacadeServiceSupplier,
            legacyLocalTradesStoreSupplier,
            localStatsCacheServiceSupplier,
            localTradesRuntimeServiceSupplier,
            accountwideSummaryUploaderSupplier,
            uploadBackfillDispatchServiceSupplier,
            ioExecutorSupplier,
            invokeOnClientThreadConsumer,
            wipeStatsInvoker,
            pushGameMessageConsumer,
            showManageDataErrorConsumer,
            updateProfileOptionsUiAction,
            updateProfileHeaderAction,
            triggerPanelRefreshAction,
            triggerStatsRefreshAction,
            gson,
            resolve(configManagerSupplier)
        );
        manageDataServices = services;
        return services;
    }

    private <T> T resolve(Supplier<T> supplier) {
        return supplier != null ? supplier.get() : null;
    }
}
