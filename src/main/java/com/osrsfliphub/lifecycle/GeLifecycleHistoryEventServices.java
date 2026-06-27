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

import java.util.Map;
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

final class GeLifecycleHistoryEventServices {
    private final Supplier<ConfigManager> configManagerSupplier;
    private final String configGroup;
    private final String wipeBarrierKeyPrefix;
    private final String geHistoryCursorKeyPrefix;
    private final int geHistoryCursorMaxTrades;
    private final long geHistorySyncWidgetSettleMs;
    private final int geHistoryCursorMinMatch;
    private final int geHistoryCursorRolloverMinLen;
    private final Supplier<GeHistoryAutoSyncService> geHistoryAutoSyncServiceSupplier;
    private final Supplier<Client> clientSupplier;
    private final int geHistoryGroupId;
    private final int geHistoryContainerChildId;
    private final Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier;
    private final Consumer<String> pushGameMessage;
    private final Supplier<Logger> loggerSupplier;
    private final Map<Long, String> profileDisplayNames;
    private final Consumer<Runnable> executeAsync;
    private final LongConsumer loadLocalTradesAsync;
    private final Runnable persistProfileSelectionState;
    private final Runnable updateProfileOptionsUi;
    private final Runnable updateProfileHeader;
    private final Runnable loadOfferUpdateTimesForCurrentAccount;
    private final Map<Integer, OfferSnapshot> snapshots;
    private final OfferUpdateTracker trackOfferUpdate;
    private final Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier;
    private final LongSupplier resolveAccountHash;
    private final LongConsumer ensureLocalTradesLoaded;
    private final Supplier<OfferEventBuildService> offerEventBuildServiceSupplier;
    private final Map<Integer, OfferUpdateStamp> offerUpdateStamps;
    private final BooleanSupplier localTradesLoadedThisLoginSupplier;
    private final LongSupplier lastLoginMsSupplier;
    private final Predicate<GeEvent> normalizeOrSuppressDuplicateTradeEvent;
    private final IntConsumer clearRecentTradeEvent;
    private final Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier;
    private final BiConsumer<GeEvent, Boolean> recordLocalTradeDelta;
    private final Runnable scheduleRefreshSoon;

    private GeHistoryWipeStateStore geHistoryWipeStateStore;
    private GeHistoryCursorService geHistoryCursorService;
    private GeHistoryWidgetReadService geHistoryWidgetReadService;
    private GeHistoryAutoSyncStateService geHistoryAutoSyncStateService;
    private GeHistoryAutoSyncMessageService geHistoryAutoSyncMessageService;
    private GeHistoryWipeBaselineDecisionService geHistoryWipeBaselineDecisionService;
    private GeHistoryAutoSyncCoordinatorFactoryService geHistoryAutoSyncCoordinatorFactoryService;
    private GeHistoryAutoSyncCoordinatorService geHistoryAutoSyncCoordinatorService;
    private ProfileLoginService profileLoginService;
    private GrandExchangeOfferChangedHandlerService grandExchangeOfferChangedHandlerService;

    GeLifecycleHistoryEventServices(
        Supplier<ConfigManager> configManagerSupplier,
        String configGroup,
        String wipeBarrierKeyPrefix,
        String geHistoryCursorKeyPrefix,
        int geHistoryCursorMaxTrades,
        long geHistorySyncWidgetSettleMs,
        int geHistoryCursorMinMatch,
        int geHistoryCursorRolloverMinLen,
        Supplier<GeHistoryAutoSyncService> geHistoryAutoSyncServiceSupplier,
        Supplier<Client> clientSupplier,
        int geHistoryGroupId,
        int geHistoryContainerChildId,
        Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier,
        Consumer<String> pushGameMessage,
        Supplier<Logger> loggerSupplier,
        Map<Long, String> profileDisplayNames,
        Consumer<Runnable> executeAsync,
        LongConsumer loadLocalTradesAsync,
        Runnable persistProfileSelectionState,
        Runnable updateProfileOptionsUi,
        Runnable updateProfileHeader,
        Runnable loadOfferUpdateTimesForCurrentAccount,
        Map<Integer, OfferSnapshot> snapshots,
        OfferUpdateTracker trackOfferUpdate,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier,
        LongSupplier resolveAccountHash,
        LongConsumer ensureLocalTradesLoaded,
        Supplier<OfferEventBuildService> offerEventBuildServiceSupplier,
        Map<Integer, OfferUpdateStamp> offerUpdateStamps,
        BooleanSupplier localTradesLoadedThisLoginSupplier,
        LongSupplier lastLoginMsSupplier,
        Predicate<GeEvent> normalizeOrSuppressDuplicateTradeEvent,
        IntConsumer clearRecentTradeEvent,
        Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier,
        BiConsumer<GeEvent, Boolean> recordLocalTradeDelta,
        Runnable scheduleRefreshSoon
    ) {
        this.configManagerSupplier = configManagerSupplier;
        this.configGroup = configGroup;
        this.wipeBarrierKeyPrefix = wipeBarrierKeyPrefix;
        this.geHistoryCursorKeyPrefix = geHistoryCursorKeyPrefix;
        this.geHistoryCursorMaxTrades = geHistoryCursorMaxTrades;
        this.geHistorySyncWidgetSettleMs = geHistorySyncWidgetSettleMs;
        this.geHistoryCursorMinMatch = geHistoryCursorMinMatch;
        this.geHistoryCursorRolloverMinLen = geHistoryCursorRolloverMinLen;
        this.geHistoryAutoSyncServiceSupplier = geHistoryAutoSyncServiceSupplier;
        this.clientSupplier = clientSupplier;
        this.geHistoryGroupId = geHistoryGroupId;
        this.geHistoryContainerChildId = geHistoryContainerChildId;
        this.localAccountSessionServiceSupplier = localAccountSessionServiceSupplier;
        this.pushGameMessage = pushGameMessage;
        this.loggerSupplier = loggerSupplier;
        this.profileDisplayNames = profileDisplayNames;
        this.executeAsync = executeAsync;
        this.loadLocalTradesAsync = loadLocalTradesAsync;
        this.persistProfileSelectionState = persistProfileSelectionState;
        this.updateProfileOptionsUi = updateProfileOptionsUi;
        this.updateProfileHeader = updateProfileHeader;
        this.loadOfferUpdateTimesForCurrentAccount = loadOfferUpdateTimesForCurrentAccount;
        this.snapshots = snapshots;
        this.trackOfferUpdate = trackOfferUpdate;
        this.profileSelectionPresentationFacadeServiceSupplier = profileSelectionPresentationFacadeServiceSupplier;
        this.resolveAccountHash = resolveAccountHash;
        this.ensureLocalTradesLoaded = ensureLocalTradesLoaded;
        this.offerEventBuildServiceSupplier = offerEventBuildServiceSupplier;
        this.offerUpdateStamps = offerUpdateStamps;
        this.localTradesLoadedThisLoginSupplier = localTradesLoadedThisLoginSupplier;
        this.lastLoginMsSupplier = lastLoginMsSupplier;
        this.normalizeOrSuppressDuplicateTradeEvent = normalizeOrSuppressDuplicateTradeEvent;
        this.clearRecentTradeEvent = clearRecentTradeEvent;
        this.uploadEventDispatchFacadeServiceSupplier = uploadEventDispatchFacadeServiceSupplier;
        this.recordLocalTradeDelta = recordLocalTradeDelta;
        this.scheduleRefreshSoon = scheduleRefreshSoon;
    }

    GeHistoryWipeStateStore getGeHistoryWipeStateStore() {
        return PluginInjectorBridge.get(GeHistoryWipeStateStore.class);
    }

    GeHistoryCursorService getGeHistoryCursorService() {
        GeHistoryCursorService service = geHistoryCursorService;
        if (service != null) {
            return service;
        }
        service = new GeHistoryCursorService(geHistoryCursorMaxTrades);
        geHistoryCursorService = service;
        return service;
    }

    GeHistoryWidgetReadService getGeHistoryWidgetReadService() {
        GeHistoryWidgetReadService service = geHistoryWidgetReadService;
        if (service != null) {
            return service;
        }
        service = new GeHistoryWidgetReadService();
        geHistoryWidgetReadService = service;
        return service;
    }

    GeHistoryAutoSyncStateService getGeHistoryAutoSyncStateService() {
        GeHistoryAutoSyncStateService service = geHistoryAutoSyncStateService;
        if (service != null) {
            return service;
        }
        service = new GeHistoryAutoSyncStateService(geHistorySyncWidgetSettleMs);
        geHistoryAutoSyncStateService = service;
        return service;
    }

    GeHistoryAutoSyncMessageService getGeHistoryAutoSyncMessageService() {
        GeHistoryAutoSyncMessageService service = geHistoryAutoSyncMessageService;
        if (service != null) {
            return service;
        }
        service = new GeHistoryAutoSyncMessageService();
        geHistoryAutoSyncMessageService = service;
        return service;
    }

    GeHistoryWipeBaselineDecisionService getGeHistoryWipeBaselineDecisionService() {
        GeHistoryWipeBaselineDecisionService service = geHistoryWipeBaselineDecisionService;
        if (service != null) {
            return service;
        }
        service = new GeHistoryWipeBaselineDecisionService(
            geHistoryCursorMinMatch,
            geHistoryCursorRolloverMinLen
        );
        geHistoryWipeBaselineDecisionService = service;
        return service;
    }

    GeHistoryAutoSyncCoordinatorFactoryService getGeHistoryAutoSyncCoordinatorFactoryService() {
        GeHistoryAutoSyncCoordinatorFactoryService service = geHistoryAutoSyncCoordinatorFactoryService;
        if (service != null) {
            return service;
        }
        service = new GeHistoryAutoSyncCoordinatorFactoryService(
            getGeHistoryWidgetReadService(),
            getGeHistoryCursorService(),
            getGeHistoryWipeBaselineDecisionService(),
            getGeHistoryAutoSyncMessageService(),
            getGeHistoryWipeStateStore(),
            geHistoryAutoSyncServiceSupplier != null ? geHistoryAutoSyncServiceSupplier.get() : null
        );
        geHistoryAutoSyncCoordinatorFactoryService = service;
        return service;
    }

    GeHistoryAutoSyncCoordinatorService getGeHistoryAutoSyncCoordinatorService() {
        GeHistoryAutoSyncCoordinatorService service = geHistoryAutoSyncCoordinatorService;
        if (service != null) {
            return service;
        }
        service = getGeHistoryAutoSyncCoordinatorFactoryService().create(
            getGeHistoryAutoSyncStateService(),
            new GeHistoryAutoSyncCoordinatorPluginHooks(
                clientSupplier,
                geHistoryGroupId,
                geHistoryContainerChildId,
                localAccountSessionServiceSupplier,
                pushGameMessage,
                loggerSupplier
            )
        );
        geHistoryAutoSyncCoordinatorService = service;
        return service;
    }

    ProfileLoginService getProfileLoginService() {
        ProfileLoginService service = profileLoginService;
        if (service != null) {
            return service;
        }
        service = new ProfileLoginService(
            new ProfileLoginPluginHooks(
                profileDisplayNames,
                executeAsync,
                loadLocalTradesAsync,
                persistProfileSelectionState,
                updateProfileOptionsUi,
                updateProfileHeader
            )
        );
        profileLoginService = service;
        return service;
    }

    GrandExchangeOfferChangedHandlerService getGrandExchangeOfferChangedHandlerService() {
        return PluginInjectorBridge.get(GrandExchangeOfferChangedHandlerService.class);
    }
}
