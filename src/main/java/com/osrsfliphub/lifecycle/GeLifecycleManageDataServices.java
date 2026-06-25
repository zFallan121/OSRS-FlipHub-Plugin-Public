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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;

final class GeLifecycleManageDataServices {
    private final GeLifecycleManageDataRuntimeContext context;

    private ManageDataCommandService manageDataCommandService;
    private ManageDataDialogService manageDataDialogService;
    private ProfileWipeDataService profileWipeDataService;
    private LocalProfileWipeService localProfileWipeService;
    private WebsiteStatsWipeService websiteStatsWipeService;

    GeLifecycleManageDataServices(
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
        WebsiteStatsWipePluginHooks.WipeStatsInvoker wipeStatsInvoker,
        Consumer<String> pushGameMessageConsumer,
        Consumer<String> showManageDataErrorConsumer,
        Runnable updateProfileOptionsUiAction,
        Runnable updateProfileHeaderAction,
        Runnable triggerPanelRefreshAction,
        Runnable triggerStatsRefreshAction,
        Gson gson,
        ConfigManager configManager
    ) {
        this.context = new GeLifecycleManageDataRuntimeContext(
            accountwideKey,
            geHistoryGroupId,
            geHistoryContainerChildId,
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
            configManager
        );
    }

    ManageDataCommandService getManageDataCommandService() {
        ManageDataCommandService service = manageDataCommandService;
        if (service != null) {
            return service;
        }
        service = new ManageDataCommandService();
        manageDataCommandService = service;
        return service;
    }

    ManageDataDialogService getManageDataDialogService() {
        ManageDataDialogService service = manageDataDialogService;
        if (service != null) {
            return service;
        }
        service = new ManageDataDialogService(
            context.accountwideKey,
            new ManageDataDialogPluginHooks(
                context.panelSupplier,
                context.profileSelectionPresentationFacadeServiceSupplier,
                this::getManageDataCommandService,
                this::showManageDataError,
                this::invokeOnClientThread,
                this::getLocalProfileWipeService,
                this::getWebsiteStatsWipeService
            )
        );
        manageDataDialogService = service;
        return service;
    }

    ProfileWipeDataService getProfileWipeDataService() {
        ProfileWipeDataService service = profileWipeDataService;
        if (service != null) {
            return service;
        }
        service = new ProfileWipeDataService(
            context.accountwideKey,
            context.localStatsLock,
            context.localTradeDeltasByAccount,
            context.localSessionStartByAccount,
            context.statsCacheByAccount,
            context.loadedProfiles,
            context.loadedProfileFileMs,
            new ProfileWipeDataPluginHooks(
                context.profileStorageFacadeServiceSupplier,
                context.gson,
                context.configManager,
                context.legacyNameKeysByHash,
                context.legacyLocalTradesStoreSupplier
            )
        );
        profileWipeDataService = service;
        return service;
    }

    LocalProfileWipeService getLocalProfileWipeService() {
        LocalProfileWipeService service = localProfileWipeService;
        if (service != null) {
            return service;
        }
        service = new LocalProfileWipeService(
            context.accountwideKey,
            new LocalProfileWipePluginHooks(
                context.localAccountSessionServiceSupplier,
                context.clientSupplier,
                context.geHistoryWidgetReadServiceSupplier,
                context.geHistoryGroupId,
                context.geHistoryContainerChildId,
                context.geHistoryCursorServiceSupplier,
                context.profileSelectionPresentationFacadeServiceSupplier,
                context.profileDisplayNames,
                context.geHistoryWipeStateStoreSupplier,
                this::getProfileWipeDataService,
                this::loadLocalTradesForAccount,
                this::refreshUiAfterLocalWipe,
                this::markAccountwideUploadDirtyAfterLocalWipe,
                this::pushGameMessage,
                this::showManageDataError
            )
        );
        localProfileWipeService = service;
        return service;
    }

    WebsiteStatsWipeService getWebsiteStatsWipeService() {
        WebsiteStatsWipeService service = websiteStatsWipeService;
        if (service != null) {
            return service;
        }
        service = new WebsiteStatsWipeService(
            new WebsiteStatsWipePluginHooks(
                context.profileSelectionPresentationFacadeServiceSupplier,
                context.ioExecutorSupplier,
                this::invokeOnClientThread,
                context.wipeStatsInvoker,
                this::showManageDataError,
                this::pushGameMessage,
                this::triggerStatsRefresh
            )
        );
        websiteStatsWipeService = service;
        return service;
    }

    private void refreshUiAfterLocalWipe() {
        if (context.updateProfileOptionsUiAction != null) {
            context.updateProfileOptionsUiAction.run();
        }
        if (context.updateProfileHeaderAction != null) {
            context.updateProfileHeaderAction.run();
        }
        if (context.triggerPanelRefreshAction != null) {
            context.triggerPanelRefreshAction.run();
        }
        if (context.triggerStatsRefreshAction != null) {
            context.triggerStatsRefreshAction.run();
        }
    }

    private void markAccountwideUploadDirtyAfterLocalWipe() {
        AccountwideSummaryUploader uploader = context.accountwideSummaryUploaderSupplier != null
            ? context.accountwideSummaryUploaderSupplier.get()
            : null;
        if (uploader != null) {
            uploader.markDirty();
        }
        ProfileSelectionPresentationFacadeService profileSelection = context.profileSelectionPresentationFacadeServiceSupplier != null
            ? context.profileSelectionPresentationFacadeServiceSupplier.get()
            : null;
        UploadBackfillDispatchService dispatch = context.uploadBackfillDispatchServiceSupplier != null
            ? context.uploadBackfillDispatchServiceSupplier.get()
            : null;
        if (profileSelection != null && profileSelection.isLinked() && dispatch != null) {
            dispatch.requestAccountwideSync();
        }
    }

    private void loadLocalTradesForAccount(long accountKey, boolean forceReload) {
        GeLifecycleLocalTradesRuntimeService localTradesRuntime = context.localTradesRuntimeServiceSupplier != null
            ? context.localTradesRuntimeServiceSupplier.get()
            : null;
        if (localTradesRuntime != null) {
            localTradesRuntime.loadLocalTradesForAccount(accountKey, forceReload);
        }
    }

    private void invokeOnClientThread(Runnable task) {
        if (task != null && context.invokeOnClientThreadConsumer != null) {
            context.invokeOnClientThreadConsumer.accept(task);
        }
    }

    private void pushGameMessage(String message) {
        if (context.pushGameMessageConsumer != null) {
            context.pushGameMessageConsumer.accept(message);
        }
    }

    private void showManageDataError(String message) {
        if (context.showManageDataErrorConsumer != null) {
            context.showManageDataErrorConsumer.accept(message);
        }
    }

    private void triggerStatsRefresh() {
        if (context.triggerStatsRefreshAction != null) {
            context.triggerStatsRefreshAction.run();
        }
    }
}
