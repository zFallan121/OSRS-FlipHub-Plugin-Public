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

final class GeLifecycleHistoryEventFactory {
    private final Supplier<ConfigManager> configManagerSupplier;
    private final Supplier<GeHistoryAutoSyncService> geHistoryAutoSyncServiceSupplier;
    private final Supplier<Client> clientSupplier;
    private final Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier;
    private final Consumer<String> pushGameMessageConsumer;
    private final Supplier<Logger> loggerSupplier;
    private final Map<Long, String> profileDisplayNames;
    private final Consumer<Runnable> executeAsyncConsumer;
    private final LongConsumer loadLocalTradesAsyncAction;
    private final Runnable persistProfileSelectionStateAction;
    private final Runnable updateProfileOptionsUiAction;
    private final Runnable updateProfileHeaderAction;
    private final Runnable loadOfferUpdateTimesForCurrentAccountAction;
    private final Map<Integer, OfferSnapshot> snapshots;
    private final OfferUpdateTracker trackOfferUpdateAction;
    private final Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier;
    private final LongSupplier resolveAccountHashSupplier;
    private final LongConsumer ensureLocalTradesLoadedAction;
    private final Supplier<OfferEventBuildService> offerEventBuildServiceSupplier;
    private final Map<Integer, OfferUpdateStamp> offerUpdateStamps;
    private final BooleanSupplier localTradesLoadedThisLoginSupplier;
    private final LongSupplier lastLoginMsSupplier;
    private final Predicate<GeEvent> normalizeOrSuppressDuplicateTradeEvent;
    private final IntConsumer clearRecentTradeEventAction;
    private final Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier;
    private final BiConsumer<GeEvent, Boolean> recordLocalTradeDeltaAction;
    private final Runnable scheduleRefreshSoonAction;


    GeLifecycleHistoryEventFactory(
        Supplier<ConfigManager> configManagerSupplier,
        Supplier<GeHistoryAutoSyncService> geHistoryAutoSyncServiceSupplier,
        Supplier<Client> clientSupplier,
        Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier,
        Consumer<String> pushGameMessageConsumer,
        Supplier<Logger> loggerSupplier,
        Map<Long, String> profileDisplayNames,
        Consumer<Runnable> executeAsyncConsumer,
        LongConsumer loadLocalTradesAsyncAction,
        Runnable persistProfileSelectionStateAction,
        Runnable updateProfileOptionsUiAction,
        Runnable updateProfileHeaderAction,
        Runnable loadOfferUpdateTimesForCurrentAccountAction,
        Map<Integer, OfferSnapshot> snapshots,
        OfferUpdateTracker trackOfferUpdateAction,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier,
        LongSupplier resolveAccountHashSupplier,
        LongConsumer ensureLocalTradesLoadedAction,
        Supplier<OfferEventBuildService> offerEventBuildServiceSupplier,
        Map<Integer, OfferUpdateStamp> offerUpdateStamps,
        BooleanSupplier localTradesLoadedThisLoginSupplier,
        LongSupplier lastLoginMsSupplier,
        Predicate<GeEvent> normalizeOrSuppressDuplicateTradeEvent,
        IntConsumer clearRecentTradeEventAction,
        Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier,
        BiConsumer<GeEvent, Boolean> recordLocalTradeDeltaAction,
        Runnable scheduleRefreshSoonAction
    ) {
        this.configManagerSupplier = configManagerSupplier;
        this.geHistoryAutoSyncServiceSupplier = geHistoryAutoSyncServiceSupplier;
        this.clientSupplier = clientSupplier;
        this.localAccountSessionServiceSupplier = localAccountSessionServiceSupplier;
        this.pushGameMessageConsumer = pushGameMessageConsumer;
        this.loggerSupplier = loggerSupplier;
        this.profileDisplayNames = profileDisplayNames;
        this.executeAsyncConsumer = executeAsyncConsumer;
        this.loadLocalTradesAsyncAction = loadLocalTradesAsyncAction;
        this.persistProfileSelectionStateAction = persistProfileSelectionStateAction;
        this.updateProfileOptionsUiAction = updateProfileOptionsUiAction;
        this.updateProfileHeaderAction = updateProfileHeaderAction;
        this.loadOfferUpdateTimesForCurrentAccountAction = loadOfferUpdateTimesForCurrentAccountAction;
        this.snapshots = snapshots;
        this.trackOfferUpdateAction = trackOfferUpdateAction;
        this.profileSelectionPresentationFacadeServiceSupplier = profileSelectionPresentationFacadeServiceSupplier;
        this.resolveAccountHashSupplier = resolveAccountHashSupplier;
        this.ensureLocalTradesLoadedAction = ensureLocalTradesLoadedAction;
        this.offerEventBuildServiceSupplier = offerEventBuildServiceSupplier;
        this.offerUpdateStamps = offerUpdateStamps;
        this.localTradesLoadedThisLoginSupplier = localTradesLoadedThisLoginSupplier;
        this.lastLoginMsSupplier = lastLoginMsSupplier;
        this.normalizeOrSuppressDuplicateTradeEvent = normalizeOrSuppressDuplicateTradeEvent;
        this.clearRecentTradeEventAction = clearRecentTradeEventAction;
        this.uploadEventDispatchFacadeServiceSupplier = uploadEventDispatchFacadeServiceSupplier;
        this.recordLocalTradeDeltaAction = recordLocalTradeDeltaAction;
        this.scheduleRefreshSoonAction = scheduleRefreshSoonAction;
    }

    GeHistoryWipeStateStore getGeHistoryWipeStateStore() {
        return PluginInjectorBridge.get(GeHistoryWipeStateStore.class);
    }

    GeHistoryAutoSyncStateService getGeHistoryAutoSyncStateService() {
        return PluginInjectorBridge.get(GeHistoryAutoSyncStateService.class);
    }

    GeHistoryAutoSyncCoordinatorService getGeHistoryAutoSyncCoordinatorService() {
        return PluginInjectorBridge.get(GeHistoryAutoSyncCoordinatorService.class);
    }

    ProfileLoginService getProfileLoginService() {
        return PluginInjectorBridge.get(ProfileLoginService.class);
    }

    GrandExchangeOfferChangedHandlerService getGrandExchangeOfferChangedHandlerService() {
        return PluginInjectorBridge.get(GrandExchangeOfferChangedHandlerService.class);
    }
}
