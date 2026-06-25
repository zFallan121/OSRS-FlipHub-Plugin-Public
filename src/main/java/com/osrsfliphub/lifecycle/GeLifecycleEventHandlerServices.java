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

import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class GeLifecycleEventHandlerServices {
    private final String configGroup;
    private final Supplier<LinkAttemptService> linkAttemptServiceSupplier;
    private final Supplier<PluginConfig> configSupplier;
    private final Supplier<LinkSessionConfigStore> linkSessionConfigStoreSupplier;
    private final Supplier<AccountwideSummaryUploader> accountwideSummaryUploaderSupplier;
    private final Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier;
    private final Supplier<FlipHubPanel> panelSupplier;
    private final Runnable updateProfileHeaderAction;
    private final Runnable triggerStatsRefreshAction;
    private final Supplier<BookmarkStateService> bookmarkStateServiceSupplier;
    private final Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier;
    private final Set<Integer> bookmarkedItems;
    private final HiddenItemConfigStore hiddenItemConfigStore;
    private final Set<Integer> hiddenItems;
    private final Runnable persistOfferUpdateTimesAction;
    private final Runnable resetOfferUpdateStampsAction;
    private final Runnable clearSnapshotsAction;
    private final Supplier<GeHistoryAutoSyncStateService> geHistoryAutoSyncStateServiceSupplier;
    private final Supplier<RecentTradeDeduper> recentTradeDeduperSupplier;
    private final Runnable updateProfileOptionsUiAction;
    private final Runnable setLastLoginNowAction;
    private final Runnable loadOfferUpdateTimesForCurrentAccountAction;
    private final Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier;
    private final Runnable updateProfileForLoginAction;
    private final Runnable primeOfferSnapshotsAction;
    private final Runnable resetLocalTradesLoadStateAction;
    private final Runnable scheduleLocalTradesLoadAction;
    private final Supplier<WikiPriceService> wikiPriceServiceSupplier;
    private final BooleanSupplier isPanelVisibleSupplier;
    private final Consumer<Boolean> setPanelVisibleConsumer;
    private final Runnable triggerPanelRefreshAction;
    private final Supplier<ScheduledExecutorService> schedulerSupplier;
    private final BiConsumer<Integer, Boolean> requestBackfillAttemptAction;

    private ConfigChangedHandlerService configChangedHandlerService;
    private GameStateChangedHandlerService gameStateChangedHandlerService;

    GeLifecycleEventHandlerServices(
        String configGroup,
        Supplier<LinkAttemptService> linkAttemptServiceSupplier,
        Supplier<PluginConfig> configSupplier,
        Supplier<LinkSessionConfigStore> linkSessionConfigStoreSupplier,
        Supplier<AccountwideSummaryUploader> accountwideSummaryUploaderSupplier,
        Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier,
        Supplier<FlipHubPanel> panelSupplier,
        Runnable updateProfileHeaderAction,
        Runnable triggerStatsRefreshAction,
        Supplier<BookmarkStateService> bookmarkStateServiceSupplier,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier,
        Set<Integer> bookmarkedItems,
        HiddenItemConfigStore hiddenItemConfigStore,
        Set<Integer> hiddenItems,
        Runnable persistOfferUpdateTimesAction,
        Runnable resetOfferUpdateStampsAction,
        Runnable clearSnapshotsAction,
        Supplier<GeHistoryAutoSyncStateService> geHistoryAutoSyncStateServiceSupplier,
        Supplier<RecentTradeDeduper> recentTradeDeduperSupplier,
        Runnable updateProfileOptionsUiAction,
        Runnable setLastLoginNowAction,
        Runnable loadOfferUpdateTimesForCurrentAccountAction,
        Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier,
        Runnable updateProfileForLoginAction,
        Runnable primeOfferSnapshotsAction,
        Runnable resetLocalTradesLoadStateAction,
        Runnable scheduleLocalTradesLoadAction,
        Supplier<WikiPriceService> wikiPriceServiceSupplier,
        BooleanSupplier isPanelVisibleSupplier,
        Consumer<Boolean> setPanelVisibleConsumer,
        Runnable triggerPanelRefreshAction,
        Supplier<ScheduledExecutorService> schedulerSupplier,
        BiConsumer<Integer, Boolean> requestBackfillAttemptAction
    ) {
        this.configGroup = configGroup;
        this.linkAttemptServiceSupplier = linkAttemptServiceSupplier;
        this.configSupplier = configSupplier;
        this.linkSessionConfigStoreSupplier = linkSessionConfigStoreSupplier;
        this.accountwideSummaryUploaderSupplier = accountwideSummaryUploaderSupplier;
        this.uploadEventDispatchFacadeServiceSupplier = uploadEventDispatchFacadeServiceSupplier;
        this.panelSupplier = panelSupplier;
        this.updateProfileHeaderAction = updateProfileHeaderAction;
        this.triggerStatsRefreshAction = triggerStatsRefreshAction;
        this.bookmarkStateServiceSupplier = bookmarkStateServiceSupplier;
        this.profileSelectionPresentationFacadeServiceSupplier = profileSelectionPresentationFacadeServiceSupplier;
        this.bookmarkedItems = bookmarkedItems;
        this.hiddenItemConfigStore = hiddenItemConfigStore;
        this.hiddenItems = hiddenItems;
        this.persistOfferUpdateTimesAction = persistOfferUpdateTimesAction;
        this.resetOfferUpdateStampsAction = resetOfferUpdateStampsAction;
        this.clearSnapshotsAction = clearSnapshotsAction;
        this.geHistoryAutoSyncStateServiceSupplier = geHistoryAutoSyncStateServiceSupplier;
        this.recentTradeDeduperSupplier = recentTradeDeduperSupplier;
        this.updateProfileOptionsUiAction = updateProfileOptionsUiAction;
        this.setLastLoginNowAction = setLastLoginNowAction;
        this.loadOfferUpdateTimesForCurrentAccountAction = loadOfferUpdateTimesForCurrentAccountAction;
        this.localTradeSessionFacadeServiceSupplier = localTradeSessionFacadeServiceSupplier;
        this.updateProfileForLoginAction = updateProfileForLoginAction;
        this.primeOfferSnapshotsAction = primeOfferSnapshotsAction;
        this.resetLocalTradesLoadStateAction = resetLocalTradesLoadStateAction;
        this.scheduleLocalTradesLoadAction = scheduleLocalTradesLoadAction;
        this.wikiPriceServiceSupplier = wikiPriceServiceSupplier;
        this.isPanelVisibleSupplier = isPanelVisibleSupplier;
        this.setPanelVisibleConsumer = setPanelVisibleConsumer;
        this.triggerPanelRefreshAction = triggerPanelRefreshAction;
        this.schedulerSupplier = schedulerSupplier;
        this.requestBackfillAttemptAction = requestBackfillAttemptAction;
    }

    ConfigChangedHandlerService getConfigChangedHandlerService() {
        ConfigChangedHandlerService service = configChangedHandlerService;
        if (service != null) {
            return service;
        }
        service = new ConfigChangedHandlerService(
            new ConfigChangedPluginHooks(
                configGroup,
                linkAttemptServiceSupplier,
                configSupplier,
                linkSessionConfigStoreSupplier,
                accountwideSummaryUploaderSupplier,
                uploadEventDispatchFacadeServiceSupplier,
                panelSupplier,
                updateProfileHeaderAction,
                triggerStatsRefreshAction,
                bookmarkStateServiceSupplier,
                profileSelectionPresentationFacadeServiceSupplier,
                bookmarkedItems,
                hiddenItemConfigStore,
                hiddenItems
            )
        );
        configChangedHandlerService = service;
        return service;
    }

    GameStateChangedHandlerService getGameStateChangedHandlerService() {
        GameStateChangedHandlerService service = gameStateChangedHandlerService;
        if (service != null) {
            return service;
        }
        service = new GameStateChangedHandlerService(
            new GameStateChangedPluginHooks(
                persistOfferUpdateTimesAction,
                resetOfferUpdateStampsAction,
                clearSnapshotsAction,
                geHistoryAutoSyncStateServiceSupplier,
                recentTradeDeduperSupplier,
                panelSupplier,
                updateProfileOptionsUiAction,
                updateProfileHeaderAction,
                setLastLoginNowAction,
                loadOfferUpdateTimesForCurrentAccountAction,
                localTradeSessionFacadeServiceSupplier,
                updateProfileForLoginAction,
                primeOfferSnapshotsAction,
                profileSelectionPresentationFacadeServiceSupplier,
                resetLocalTradesLoadStateAction,
                scheduleLocalTradesLoadAction,
                wikiPriceServiceSupplier,
                linkAttemptServiceSupplier,
                configSupplier,
                isPanelVisibleSupplier,
                setPanelVisibleConsumer,
                triggerPanelRefreshAction,
                triggerStatsRefreshAction,
                schedulerSupplier,
                requestBackfillAttemptAction
            )
        );
        gameStateChangedHandlerService = service;
        return service;
    }
}
