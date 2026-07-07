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
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;

final class GeLifecyclePluginRuntimeFactoryContext {
    final GeLifecycleSharedState sharedState;
    final HiddenItemConfigStore hiddenItemConfigStore;
    final BookmarkConfigStore bookmarkConfigStore;
    final OfferUpdateStampConfigStore offerUpdateStampConfigStore;
    final OfferUpdateStampLegacyMatcher offerUpdateStampLegacyMatcher;
    final UploadDiagnosticsState uploadState;
    final GeLifecycleRuntimeUtilityServices runtimeUtilityServices;
    final Gson gson;
    final OkHttpClient httpClient;

    final long accountwideKey;
    final int maxLocalTrades;
    final long localEventBucketMs;
    final long duplicateTradeWindowMs;
    final int maxPendingUploadEvents;
    final int maxBatchSize;
    final long backfillRetryIntervalSeconds;
    final long backfillRetryMaxIntervalSeconds;
    final long backfillMinIntervalMs;

    final Map<Integer, OfferUpdateStamp> offerUpdateStamps;
    final Map<String, Integer> itemNameLookupCache;
    final Map<Integer, String> itemNameCache;
    final Set<Integer> hiddenItems;
    final Map<Long, String> profileDisplayNames;
    final ProfileSelectionState profileSelection;
    final Map<Long, String> legacyNameKeysByHash;
    final Map<Long, Long> loadedProfileFileMs;

    final Object localStatsLock;
    final Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount;
    final Set<Long> loadedProfiles;
    final LocalTradesLoadCoordinator.State localTradesLoadState;

    final Supplier<PluginConfig> configSupplier;
    final Supplier<ConfigManager> configManagerSupplier;
    final Supplier<Client> clientSupplier;
    final Supplier<ClientThread> clientThreadSupplier;
    final Supplier<ItemManager> itemManagerSupplier;
    final Supplier<FlipHubPanel> panelSupplier;
    final Supplier<ScheduledExecutorService> schedulerSupplier;
    final Supplier<ExecutorService> ioExecutorSupplier;
    final Supplier<ApiClient> apiClientSupplier;
    final Supplier<Logger> loggerSupplier;

    final Supplier<PanelRefreshCoordinator> panelRefreshCoordinatorSupplier;
    final Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier;
    final Supplier<GeLifecycleBackfillServices> backfillServicesSupplier;
    final Supplier<GeLifecycleProfileWorkflowService> profileWorkflowServiceSupplier;
    final Supplier<GeLifecycleEventManageHistoryServices> eventManageHistoryServicesSupplier;
    final Supplier<GeLifecyclePanelDataRuntimeService> panelDataRuntimeServiceSupplier;
    final Supplier<OfferPreviewRuntimeFacadeService> offerPreviewRuntimeFacadeServiceSupplier;
    final Supplier<GeLifecycleOfferStampStateServices> offerStampStateServicesSupplier;
    final Supplier<GeLifecycleItemServices> itemServicesSupplier;
    final Supplier<BookmarkStateService> bookmarkStateServiceSupplier;
    final Supplier<OfferEventBuildService> offerEventBuildServiceSupplier;
    final Supplier<LegacyLocalTradesFilterService> legacyLocalTradesFilterServiceSupplier;
    final Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier;
    final Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier;

    final Supplier<Integer> offerPreviewItemIdSupplier;
    final Supplier<FlipHubItem> offerPreviewItemSupplier;
    final Consumer<Integer> offerPreviewItemIdSetter;
    final Consumer<FlipHubItem> offerPreviewItemSetter;

    final Supplier<String> currentQuerySupplier;
    final BooleanSupplier bookmarkFilterEnabledSupplier;
    final Supplier<Set<Integer>> bookmarkedItemsSupplier;
    final Supplier<Integer> currentPageSupplier;
    final Supplier<StatsRange> currentStatsRangeSupplier;
    final Supplier<StatsItemSort> currentStatsSortSupplier;
    final BooleanSupplier panelVisibleSupplier;
    final Consumer<Boolean> panelVisibleSetter;
    final BooleanSupplier localTradesLoadedThisLoginSupplier;
    final Runnable resetLocalTradesLoadStateAction;
    final Runnable markAccountwideUploadDirtyAction;
    final Runnable setLocalTradesLoadedThisLoginAction;

    final Consumer<Runnable> invokeOnClientThreadConsumer;
    final LongConsumerWithScheduler executeOnSchedulerConsumer;
    final Consumer<Runnable> executeAsyncConsumer;
    final Consumer<Runnable> executeIoConsumer;
    final ToLongFunction<Path> profileFileModifiedMsFn;
    final Consumer<String> showManageDataErrorConsumer;
    final Runnable refreshPanelDataAction;

    GeLifecyclePluginRuntimeFactoryContext(
        GeLifecycleSharedState sharedState,
        HiddenItemConfigStore hiddenItemConfigStore,
        BookmarkConfigStore bookmarkConfigStore,
        OfferUpdateStampConfigStore offerUpdateStampConfigStore,
        OfferUpdateStampLegacyMatcher offerUpdateStampLegacyMatcher,
        UploadDiagnosticsState uploadState,
        GeLifecycleRuntimeUtilityServices runtimeUtilityServices,
        Gson gson,
        OkHttpClient httpClient,
        long accountwideKey,
        int maxLocalTrades,
        long localEventBucketMs,
        long duplicateTradeWindowMs,
        int maxPendingUploadEvents,
        int maxBatchSize,
        long backfillRetryIntervalSeconds,
        long backfillRetryMaxIntervalSeconds,
        long backfillMinIntervalMs,
        Map<Integer, OfferUpdateStamp> offerUpdateStamps,
        Map<String, Integer> itemNameLookupCache,
        Map<Integer, String> itemNameCache,
        Set<Integer> hiddenItems,
        Map<Long, String> profileDisplayNames,
        ProfileSelectionState profileSelection,
        Map<Long, String> legacyNameKeysByHash,
        Map<Long, Long> loadedProfileFileMs,
        Object localStatsLock,
        Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount,
        Set<Long> loadedProfiles,
        LocalTradesLoadCoordinator.State localTradesLoadState,
        Supplier<PluginConfig> configSupplier,
        Supplier<ConfigManager> configManagerSupplier,
        Supplier<Client> clientSupplier,
        Supplier<ClientThread> clientThreadSupplier,
        Supplier<ItemManager> itemManagerSupplier,
        Supplier<FlipHubPanel> panelSupplier,
        Supplier<ScheduledExecutorService> schedulerSupplier,
        Supplier<ExecutorService> ioExecutorSupplier,
        Supplier<ApiClient> apiClientSupplier,
        Supplier<Logger> loggerSupplier,
        Supplier<PanelRefreshCoordinator> panelRefreshCoordinatorSupplier,
        Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier,
        Supplier<GeLifecycleBackfillServices> backfillServicesSupplier,
        Supplier<GeLifecycleProfileWorkflowService> profileWorkflowServiceSupplier,
        Supplier<GeLifecycleEventManageHistoryServices> eventManageHistoryServicesSupplier,
        Supplier<GeLifecyclePanelDataRuntimeService> panelDataRuntimeServiceSupplier,
        Supplier<OfferPreviewRuntimeFacadeService> offerPreviewRuntimeFacadeServiceSupplier,
        Supplier<GeLifecycleOfferStampStateServices> offerStampStateServicesSupplier,
        Supplier<GeLifecycleItemServices> itemServicesSupplier,
        Supplier<BookmarkStateService> bookmarkStateServiceSupplier,
        Supplier<OfferEventBuildService> offerEventBuildServiceSupplier,
        Supplier<LegacyLocalTradesFilterService> legacyLocalTradesFilterServiceSupplier,
        Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier,
        Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier,
        Supplier<Integer> offerPreviewItemIdSupplier,
        Supplier<FlipHubItem> offerPreviewItemSupplier,
        Consumer<Integer> offerPreviewItemIdSetter,
        Consumer<FlipHubItem> offerPreviewItemSetter,
        Supplier<String> currentQuerySupplier,
        BooleanSupplier bookmarkFilterEnabledSupplier,
        Supplier<Set<Integer>> bookmarkedItemsSupplier,
        Supplier<Integer> currentPageSupplier,
        Supplier<StatsRange> currentStatsRangeSupplier,
        Supplier<StatsItemSort> currentStatsSortSupplier,
        BooleanSupplier panelVisibleSupplier,
        Consumer<Boolean> panelVisibleSetter,
        BooleanSupplier localTradesLoadedThisLoginSupplier,
        Runnable resetLocalTradesLoadStateAction,
        Runnable markAccountwideUploadDirtyAction,
        Runnable setLocalTradesLoadedThisLoginAction,
        Consumer<Runnable> invokeOnClientThreadConsumer,
        LongConsumerWithScheduler executeOnSchedulerConsumer,
        Consumer<Runnable> executeAsyncConsumer,
        Consumer<Runnable> executeIoConsumer,
        ToLongFunction<Path> profileFileModifiedMsFn,
        Consumer<String> showManageDataErrorConsumer,
        Runnable refreshPanelDataAction
    ) {
        this.sharedState = sharedState;
        this.hiddenItemConfigStore = hiddenItemConfigStore;
        this.bookmarkConfigStore = bookmarkConfigStore;
        this.offerUpdateStampConfigStore = offerUpdateStampConfigStore;
        this.offerUpdateStampLegacyMatcher = offerUpdateStampLegacyMatcher;
        this.uploadState = uploadState;
        this.runtimeUtilityServices = runtimeUtilityServices;
        this.gson = gson;
        this.httpClient = httpClient;
        this.accountwideKey = accountwideKey;
        this.maxLocalTrades = maxLocalTrades;
        this.localEventBucketMs = localEventBucketMs;
        this.duplicateTradeWindowMs = duplicateTradeWindowMs;
        this.maxPendingUploadEvents = maxPendingUploadEvents;
        this.maxBatchSize = maxBatchSize;
        this.backfillRetryIntervalSeconds = backfillRetryIntervalSeconds;
        this.backfillRetryMaxIntervalSeconds = backfillRetryMaxIntervalSeconds;
        this.backfillMinIntervalMs = backfillMinIntervalMs;
        this.offerUpdateStamps = offerUpdateStamps;
        this.itemNameLookupCache = itemNameLookupCache;
        this.itemNameCache = itemNameCache;
        this.hiddenItems = hiddenItems;
        this.profileDisplayNames = profileDisplayNames;
        this.profileSelection = profileSelection;
        this.legacyNameKeysByHash = legacyNameKeysByHash;
        this.loadedProfileFileMs = loadedProfileFileMs;
        this.localStatsLock = localStatsLock;
        this.localTradeDeltasByAccount = localTradeDeltasByAccount;
        this.loadedProfiles = loadedProfiles;
        this.localTradesLoadState = localTradesLoadState;
        this.configSupplier = configSupplier;
        this.configManagerSupplier = configManagerSupplier;
        this.clientSupplier = clientSupplier;
        this.clientThreadSupplier = clientThreadSupplier;
        this.itemManagerSupplier = itemManagerSupplier;
        this.panelSupplier = panelSupplier;
        this.schedulerSupplier = schedulerSupplier;
        this.ioExecutorSupplier = ioExecutorSupplier;
        this.apiClientSupplier = apiClientSupplier;
        this.loggerSupplier = loggerSupplier;
        this.panelRefreshCoordinatorSupplier = panelRefreshCoordinatorSupplier;
        this.localTradesRuntimeServiceSupplier = localTradesRuntimeServiceSupplier;
        this.backfillServicesSupplier = backfillServicesSupplier;
        this.profileWorkflowServiceSupplier = profileWorkflowServiceSupplier;
        this.eventManageHistoryServicesSupplier = eventManageHistoryServicesSupplier;
        this.panelDataRuntimeServiceSupplier = panelDataRuntimeServiceSupplier;
        this.offerPreviewRuntimeFacadeServiceSupplier = offerPreviewRuntimeFacadeServiceSupplier;
        this.offerStampStateServicesSupplier = offerStampStateServicesSupplier;
        this.itemServicesSupplier = itemServicesSupplier;
        this.bookmarkStateServiceSupplier = bookmarkStateServiceSupplier;
        this.offerEventBuildServiceSupplier = offerEventBuildServiceSupplier;
        this.legacyLocalTradesFilterServiceSupplier = legacyLocalTradesFilterServiceSupplier;
        this.uploadEventDispatchFacadeServiceSupplier = uploadEventDispatchFacadeServiceSupplier;
        this.uploadBackfillDispatchServiceSupplier = uploadBackfillDispatchServiceSupplier;
        this.offerPreviewItemIdSupplier = offerPreviewItemIdSupplier;
        this.offerPreviewItemSupplier = offerPreviewItemSupplier;
        this.offerPreviewItemIdSetter = offerPreviewItemIdSetter;
        this.offerPreviewItemSetter = offerPreviewItemSetter;
        this.currentQuerySupplier = currentQuerySupplier;
        this.bookmarkFilterEnabledSupplier = bookmarkFilterEnabledSupplier;
        this.bookmarkedItemsSupplier = bookmarkedItemsSupplier;
        this.currentPageSupplier = currentPageSupplier;
        this.currentStatsRangeSupplier = currentStatsRangeSupplier;
        this.currentStatsSortSupplier = currentStatsSortSupplier;
        this.panelVisibleSupplier = panelVisibleSupplier;
        this.panelVisibleSetter = panelVisibleSetter;
        this.localTradesLoadedThisLoginSupplier = localTradesLoadedThisLoginSupplier;
        this.resetLocalTradesLoadStateAction = resetLocalTradesLoadStateAction;
        this.markAccountwideUploadDirtyAction = markAccountwideUploadDirtyAction;
        this.setLocalTradesLoadedThisLoginAction = setLocalTradesLoadedThisLoginAction;
        this.invokeOnClientThreadConsumer = invokeOnClientThreadConsumer;
        this.executeOnSchedulerConsumer = executeOnSchedulerConsumer;
        this.executeAsyncConsumer = executeAsyncConsumer;
        this.executeIoConsumer = executeIoConsumer;
        this.profileFileModifiedMsFn = profileFileModifiedMsFn;
        this.showManageDataErrorConsumer = showManageDataErrorConsumer;
        this.refreshPanelDataAction = refreshPanelDataAction;
    }
}
