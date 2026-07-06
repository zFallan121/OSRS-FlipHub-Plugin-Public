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

final class GeLifecycleCoreFeatureRuntimeContext {
    final GeLifecycleSharedState sharedState;
    final HiddenItemConfigStore hiddenItemConfigStore;
    final Gson gson;
    final OkHttpClient httpClient;
    final GeLifecycleRuntimeUtilityServices runtimeUtilityServices;
    final PanelRefreshCoordinatorFactoryService panelRefreshCoordinatorFactoryService;
    final boolean hasItemManager;

    final Supplier<PluginConfig> configSupplier;
    final Supplier<ConfigManager> configManagerSupplier;
    final Supplier<Client> clientSupplier;
    final Supplier<ClientThread> clientThreadSupplier;
    final Supplier<ApiClient> apiClientSupplier;
    final Supplier<FlipHubPanel> panelSupplier;
    final Supplier<ScheduledExecutorService> schedulerSupplier;
    final Supplier<ExecutorService> ioExecutorSupplier;

    final Supplier<GeLifecycleOfferStampStateServices> offerStampStateServicesSupplier;
    final Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier;
    final Supplier<GeLifecyclePanelDataRuntimeService> panelDataRuntimeServiceSupplier;
    final Supplier<GeLifecycleItemServices> itemServicesSupplier;
    final Supplier<BookmarkStateService> bookmarkStateServiceSupplier;
    final Supplier<OfferEventBuildService> offerEventBuildServiceSupplier;
    final Supplier<GeLifecycleProfileWorkflowService> profileWorkflowServiceSupplier;
    final Supplier<LegacyLocalTradesFilterService> legacyLocalTradesFilterServiceSupplier;
    final Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier;
    final Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier;

    final Supplier<StatsRange> currentStatsRangeSupplier;
    final Supplier<StatsItemSort> currentStatsSortSupplier;
    final BooleanSupplier panelVisibleSupplier;
    final Consumer<Boolean> panelVisibleSetter;
    final BooleanSupplier localTradesLoadedThisLoginSupplier;
    final Runnable resetLocalTradesLoadStateAction;
    final Runnable markAccountwideUploadDirtyAction;

    final Consumer<Runnable> invokeOnClientThreadConsumer;
    final LongConsumerWithScheduler executeOnSchedulerConsumer;
    final Consumer<Runnable> executeAsyncConsumer;
    final WebsiteStatsWipePluginHooks.WipeStatsInvoker wipeStatsInvoker;
    final ToLongFunction<Path> profileFileModifiedMsFn;
    final Supplier<Logger> loggerSupplier;
    final Consumer<String> showManageDataErrorConsumer;

    GeLifecycleCoreFeatureRuntimeContext(
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
        this.sharedState = sharedState;
        this.hiddenItemConfigStore = hiddenItemConfigStore;
        this.gson = gson;
        this.httpClient = httpClient;
        this.runtimeUtilityServices = runtimeUtilityServices;
        this.panelRefreshCoordinatorFactoryService = panelRefreshCoordinatorFactoryService;
        this.hasItemManager = hasItemManager;
        this.configSupplier = configSupplier;
        this.configManagerSupplier = configManagerSupplier;
        this.clientSupplier = clientSupplier;
        this.clientThreadSupplier = clientThreadSupplier;
        this.apiClientSupplier = apiClientSupplier;
        this.panelSupplier = panelSupplier;
        this.schedulerSupplier = schedulerSupplier;
        this.ioExecutorSupplier = ioExecutorSupplier;
        this.offerStampStateServicesSupplier = offerStampStateServicesSupplier;
        this.localTradesRuntimeServiceSupplier = localTradesRuntimeServiceSupplier;
        this.panelDataRuntimeServiceSupplier = panelDataRuntimeServiceSupplier;
        this.itemServicesSupplier = itemServicesSupplier;
        this.bookmarkStateServiceSupplier = bookmarkStateServiceSupplier;
        this.offerEventBuildServiceSupplier = offerEventBuildServiceSupplier;
        this.profileWorkflowServiceSupplier = profileWorkflowServiceSupplier;
        this.legacyLocalTradesFilterServiceSupplier = legacyLocalTradesFilterServiceSupplier;
        this.uploadEventDispatchFacadeServiceSupplier = uploadEventDispatchFacadeServiceSupplier;
        this.uploadBackfillDispatchServiceSupplier = uploadBackfillDispatchServiceSupplier;
        this.currentStatsRangeSupplier = currentStatsRangeSupplier;
        this.currentStatsSortSupplier = currentStatsSortSupplier;
        this.panelVisibleSupplier = panelVisibleSupplier;
        this.panelVisibleSetter = panelVisibleSetter;
        this.localTradesLoadedThisLoginSupplier = localTradesLoadedThisLoginSupplier;
        this.resetLocalTradesLoadStateAction = resetLocalTradesLoadStateAction;
        this.markAccountwideUploadDirtyAction = markAccountwideUploadDirtyAction;
        this.invokeOnClientThreadConsumer = invokeOnClientThreadConsumer;
        this.executeOnSchedulerConsumer = executeOnSchedulerConsumer;
        this.executeAsyncConsumer = executeAsyncConsumer;
        this.wipeStatsInvoker = wipeStatsInvoker;
        this.profileFileModifiedMsFn = profileFileModifiedMsFn;
        this.showManageDataErrorConsumer = showManageDataErrorConsumer;
        this.loggerSupplier = loggerSupplier;
    }
}
