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
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import org.slf4j.Logger;

final class GeLifecycleEventManageHistoryServices {
    private final GeLifecycleEventManageHistoryRuntimeServices runtimeServices;

    GeLifecycleEventManageHistoryServices(
        GeLifecycleSharedState sharedState,
        HiddenItemConfigStore hiddenItemConfigStore,
        Gson gson,
        Supplier<PluginConfig> configSupplier,
        Supplier<ConfigManager> configManagerSupplier,
        Supplier<Client> clientSupplier,
        Supplier<FlipHubPanel> panelSupplier,
        Supplier<ScheduledExecutorService> schedulerSupplier,
        Supplier<ExecutorService> ioExecutorSupplier,
        Consumer<Runnable> invokeOnClientThreadConsumer,
        Consumer<String> pushGameMessageConsumer,
        Consumer<String> showManageDataErrorConsumer,
        Runnable triggerStatsRefreshAction,
        Runnable triggerPanelRefreshAction,
        Runnable updateProfileOptionsUiAction,
        Runnable updateProfileHeaderAction,
        Runnable updateProfileForLoginAction,
        Runnable primeOfferSnapshotsAction,
        Runnable persistOfferUpdateTimesAction,
        Runnable resetOfferUpdateStampsAction,
        Runnable clearSnapshotsAction,
        Runnable setLastLoginNowAction,
        Runnable loadOfferUpdateTimesForCurrentAccountAction,
        Runnable resetLocalTradesLoadStateAction,
        Runnable scheduleLocalTradesLoadAction,
        Runnable persistProfileSelectionStateAction,
        BooleanSupplier isPanelVisibleSupplier,
        Consumer<Boolean> setPanelVisibleConsumer,
        BooleanSupplier localTradesLoadedThisLoginSupplier,
        LongSupplier lastLoginMsSupplier,
        Supplier<LinkAttemptService> linkAttemptServiceSupplier,
        Supplier<LinkSessionConfigStore> linkSessionConfigStoreSupplier,
        Supplier<AccountwideSummaryUploader> accountwideSummaryUploaderSupplier,
        Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier,
        Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier,
        Supplier<BookmarkStateService> bookmarkStateServiceSupplier,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier,
        Supplier<RecentTradeDeduper> recentTradeDeduperSupplier,
        Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier,
        Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier,
        Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier,
        Supplier<ProfileStorageFacadeService> profileStorageFacadeServiceSupplier,
        Supplier<LegacyLocalTradesStore> legacyLocalTradesStoreSupplier,
        Supplier<LocalStatsCacheService> localStatsCacheServiceSupplier,
        Supplier<WikiPriceService> wikiPriceServiceSupplier,
        Supplier<GeHistoryAutoSyncService> geHistoryAutoSyncServiceSupplier,
        Supplier<OfferEventBuildService> offerEventBuildServiceSupplier,
        Supplier<Logger> loggerSupplier,
        BiConsumer<GeEvent, Boolean> recordLocalTradeDeltaAction,
        Predicate<GeEvent> normalizeOrSuppressDuplicateTradeEvent,
        IntConsumer clearRecentTradeEventAction,
        Runnable scheduleRefreshSoonAction,
        BiConsumer<Integer, Boolean> requestBackfillAttemptAction,
        Consumer<Runnable> executeAsyncConsumer,
        LongConsumer loadLocalTradesAsyncAction,
        OfferUpdateTracker trackOfferUpdateAction,
        LongSupplier resolveAccountHashSupplier,
        LongConsumer ensureLocalTradesLoadedAction
    ) {
        this.runtimeServices = new GeLifecycleEventManageHistoryRuntimeServices(
            sharedState,
            hiddenItemConfigStore,
            gson,
            configSupplier,
            configManagerSupplier,
            clientSupplier,
            panelSupplier,
            schedulerSupplier,
            ioExecutorSupplier,
            invokeOnClientThreadConsumer,
            pushGameMessageConsumer,
            showManageDataErrorConsumer,
            triggerStatsRefreshAction,
            triggerPanelRefreshAction,
            updateProfileOptionsUiAction,
            updateProfileHeaderAction,
            updateProfileForLoginAction,
            primeOfferSnapshotsAction,
            persistOfferUpdateTimesAction,
            resetOfferUpdateStampsAction,
            clearSnapshotsAction,
            setLastLoginNowAction,
            loadOfferUpdateTimesForCurrentAccountAction,
            resetLocalTradesLoadStateAction,
            scheduleLocalTradesLoadAction,
            persistProfileSelectionStateAction,
            isPanelVisibleSupplier,
            setPanelVisibleConsumer,
            localTradesLoadedThisLoginSupplier,
            lastLoginMsSupplier,
            linkAttemptServiceSupplier,
            linkSessionConfigStoreSupplier,
            accountwideSummaryUploaderSupplier,
            uploadEventDispatchFacadeServiceSupplier,
            uploadBackfillDispatchServiceSupplier,
            bookmarkStateServiceSupplier,
            profileSelectionPresentationFacadeServiceSupplier,
            recentTradeDeduperSupplier,
            localTradeSessionFacadeServiceSupplier,
            localTradesRuntimeServiceSupplier,
            localAccountSessionServiceSupplier,
            profileStorageFacadeServiceSupplier,
            legacyLocalTradesStoreSupplier,
            localStatsCacheServiceSupplier,
            wikiPriceServiceSupplier,
            geHistoryAutoSyncServiceSupplier,
            offerEventBuildServiceSupplier,
            loggerSupplier,
            recordLocalTradeDeltaAction,
            normalizeOrSuppressDuplicateTradeEvent,
            clearRecentTradeEventAction,
            scheduleRefreshSoonAction,
            requestBackfillAttemptAction,
            executeAsyncConsumer,
            loadLocalTradesAsyncAction,
            trackOfferUpdateAction,
            resolveAccountHashSupplier,
            ensureLocalTradesLoadedAction
        );
    }

    GeLifecycleManageDataServices getManageDataServices() {
        return runtimeServices.getManageDataServices();
    }

    ManageDataDialogService getManageDataDialogService() {
        return runtimeServices.getManageDataDialogService();
    }

    GeHistoryWipeStateStore getGeHistoryWipeStateStore() {
        return runtimeServices.getGeHistoryWipeStateStore();
    }

    GeHistoryAutoSyncStateService getGeHistoryAutoSyncStateService() {
        return runtimeServices.getGeHistoryAutoSyncStateService();
    }

    GeHistoryAutoSyncCoordinatorService getGeHistoryAutoSyncCoordinatorService() {
        return runtimeServices.getGeHistoryAutoSyncCoordinatorService();
    }

    ProfileLoginService getProfileLoginService() {
        return runtimeServices.getProfileLoginService();
    }

    GrandExchangeOfferChangedHandlerService getGrandExchangeOfferChangedHandlerService() {
        return runtimeServices.getGrandExchangeOfferChangedHandlerService();
    }
}
