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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;

final class GeLifecycleCoreFeatureRuntimeServices {
    private final GeLifecycleCoreFeatureRuntimeContext context;

    private GeLifecycleEventManageHistoryServices eventManageHistoryServices;
    private GeLifecycleStatsTradesServices statsTradesServices;
    private PanelRefreshCoordinator panelRefreshCoordinator;
    private GeLifecycleBackfillServices backfillServices;

    GeLifecycleCoreFeatureRuntimeServices(
        GeLifecycleSharedState sharedState,
        HiddenItemConfigStore hiddenItemConfigStore,
        Gson gson,
        OkHttpClient httpClient,
        GeLifecycleRuntimeUtilityServices runtimeUtilityServices,
        PanelRefreshCoordinatorFactoryService panelRefreshCoordinatorFactoryService,
        boolean hasItemManager,
        Supplier<PluginConfig> configSupplier,
        Supplier<ConfigManager> configManagerSupplier,
        Supplier<Client> clientSupplier,
        Supplier<ClientThread> clientThreadSupplier,
        Supplier<ApiClient> apiClientSupplier,
        Supplier<FlipHubPanel> panelSupplier,
        Supplier<ScheduledExecutorService> schedulerSupplier,
        Supplier<ExecutorService> ioExecutorSupplier,
        Supplier<GeLifecycleOfferStampStateServices> offerStampStateServicesSupplier,
        Supplier<GeLifecycleProfileSelectionServices> profileSelectionServicesSupplier,
        Supplier<GeLifecycleLinkServices> linkServicesSupplier,
        Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier,
        Supplier<GeLifecyclePanelDataRuntimeService> panelDataRuntimeServiceSupplier,
        Supplier<GeLifecycleItemServices> itemServicesSupplier,
        Supplier<BookmarkStateService> bookmarkStateServiceSupplier,
        Supplier<OfferEventBuildService> offerEventBuildServiceSupplier,
        Supplier<GeLifecycleProfileWorkflowService> profileWorkflowServiceSupplier,
        Supplier<LegacyLocalTradesFilterService> legacyLocalTradesFilterServiceSupplier,
        Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier,
        Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier,
        Supplier<StatsRange> currentStatsRangeSupplier,
        Supplier<StatsItemSort> currentStatsSortSupplier,
        BooleanSupplier panelVisibleSupplier,
        Consumer<Boolean> panelVisibleSetter,
        BooleanSupplier localTradesLoadedThisLoginSupplier,
        Runnable resetLocalTradesLoadStateAction,
        Runnable markAccountwideUploadDirtyAction,
        Consumer<Runnable> invokeOnClientThreadConsumer,
        LongConsumerWithScheduler executeOnSchedulerConsumer,
        Consumer<Runnable> executeAsyncConsumer,
        WebsiteStatsWipePluginHooks.WipeStatsInvoker wipeStatsInvoker,
        ToLongFunction<Path> profileFileModifiedMsFn,
        Consumer<String> showManageDataErrorConsumer,
        Supplier<Logger> loggerSupplier
    ) {
        this.context = new GeLifecycleCoreFeatureRuntimeContext(
            sharedState,
            hiddenItemConfigStore,
            gson,
            httpClient,
            runtimeUtilityServices,
            panelRefreshCoordinatorFactoryService,
            hasItemManager,
            configSupplier,
            configManagerSupplier,
            clientSupplier,
            clientThreadSupplier,
            apiClientSupplier,
            panelSupplier,
            schedulerSupplier,
            ioExecutorSupplier,
            offerStampStateServicesSupplier,
            profileSelectionServicesSupplier,
            linkServicesSupplier,
            localTradesRuntimeServiceSupplier,
            panelDataRuntimeServiceSupplier,
            itemServicesSupplier,
            bookmarkStateServiceSupplier,
            offerEventBuildServiceSupplier,
            profileWorkflowServiceSupplier,
            legacyLocalTradesFilterServiceSupplier,
            uploadEventDispatchFacadeServiceSupplier,
            uploadBackfillDispatchServiceSupplier,
            currentStatsRangeSupplier,
            currentStatsSortSupplier,
            panelVisibleSupplier,
            panelVisibleSetter,
            localTradesLoadedThisLoginSupplier,
            resetLocalTradesLoadStateAction,
            markAccountwideUploadDirtyAction,
            invokeOnClientThreadConsumer,
            executeOnSchedulerConsumer,
            executeAsyncConsumer,
            wipeStatsInvoker,
            profileFileModifiedMsFn,
            showManageDataErrorConsumer,
            loggerSupplier
        );
    }

    GeLifecycleEventManageHistoryServices getEventManageHistoryServices() {
        GeLifecycleEventManageHistoryServices services = eventManageHistoryServices;
        if (services != null) {
            return services;
        }
        services = GeLifecycleCoreFeatureFactory.createEventManageHistoryServices(
            context,
            this::getPanelRefreshCoordinator,
            this::getProfileWorkflowService,
            this::getOfferStampStateServices,
            this::getLinkServices,
            this::getBackfillServices,
            this::getStatsTradesServices,
            this::getLocalTradesRuntimeService,
            this::getProfileSelectionServices,
            this::getUploadBackfillDispatchService
        );
        eventManageHistoryServices = services;
        return services;
    }

    GeLifecycleStatsTradesServices getStatsTradesServices() {
        GeLifecycleStatsTradesServices services = statsTradesServices;
        if (services != null) {
            return services;
        }
        services = GeLifecycleCoreFeatureFactory.createStatsTradesServices(
            context,
            this::getBackfillServices,
            this::getPanelRefreshCoordinator,
            this::getLocalTradesRuntimeService,
            this::getProfileSelectionServices,
            this::getProfileWorkflowService
        );
        statsTradesServices = services;
        return services;
    }

    PanelRefreshCoordinator getPanelRefreshCoordinator() {
        PanelRefreshCoordinator coordinator = panelRefreshCoordinator;
        if (coordinator != null) {
            return coordinator;
        }
        coordinator = GeLifecycleCoreFeatureFactory.createPanelRefreshCoordinator(
            context,
            this::getProfileWorkflowService
        );
        panelRefreshCoordinator = coordinator;
        return coordinator;
    }

    GeLifecycleBackfillServices getBackfillServices() {
        GeLifecycleBackfillServices services = backfillServices;
        if (services != null) {
            return services;
        }
        services = GeLifecycleCoreFeatureFactory.createBackfillServices(
            context,
            this::getStatsTradesServices,
            this::getProfileSelectionServices,
            this::getLocalTradesRuntimeService,
            this::getPanelRefreshCoordinator,
            token -> fetchRemoteStatsSummary(token, null, true)
        );
        backfillServices = services;
        return services;
    }

    private ApiClient.StatsSummaryResponse fetchRemoteStatsSummary(String sessionToken, Long sinceMs, boolean allowRefresh) {
        GeLifecycleBackfillServices services = getBackfillServices();
        SessionRefreshService sessionRefreshService = services.getBackfillMarketServices().getSessionRefreshService();
        return context.runtimeUtilityServices.fetchRemoteStatsSummary(
            context.apiClientSupplier.get(),
            context.configSupplier.get(),
            sessionRefreshService,
            sessionToken,
            sinceMs,
            allowRefresh
        );
    }

    private GeLifecycleOfferStampStateServices getOfferStampStateServices() {
        return context.offerStampStateServicesSupplier.get();
    }

    private GeLifecycleProfileSelectionServices getProfileSelectionServices() {
        return context.profileSelectionServicesSupplier.get();
    }

    private GeLifecycleLinkServices getLinkServices() {
        return context.linkServicesSupplier.get();
    }

    private GeLifecycleLocalTradesRuntimeService getLocalTradesRuntimeService() {
        return context.localTradesRuntimeServiceSupplier.get();
    }

    private GeLifecycleProfileWorkflowService getProfileWorkflowService() {
        return context.profileWorkflowServiceSupplier.get();
    }

    private UploadBackfillDispatchService getUploadBackfillDispatchService() {
        return context.uploadBackfillDispatchServiceSupplier.get();
    }
}
