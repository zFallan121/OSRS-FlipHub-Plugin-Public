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
import java.util.function.IntConsumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.runelite.api.Client;

final class GrandExchangeOfferChangedPluginHooks implements GrandExchangeOfferChangedHandlerService.Hooks {
    @FunctionalInterface
    interface OfferUpdateTracker {
        void track(int slot, OfferSnapshot previous, OfferSnapshot next);
    }

    private final Runnable loadOfferUpdateTimesForCurrentAccount;
    private final Map<Integer, OfferSnapshot> snapshots;
    private final OfferUpdateTracker trackOfferUpdate;
    private final Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier;
    private final LongSupplier resolveAccountHash;
    private final java.util.function.LongConsumer ensureLocalTradesLoaded;
    private final Supplier<OfferEventBuildService> offerEventBuildServiceSupplier;
    private final Map<Integer, OfferUpdateStamp> offerUpdateStamps;
    private final BooleanSupplier localTradesLoadedThisLogin;
    private final LongSupplier lastLoginMs;
    private final Supplier<Client> clientSupplier;
    private final Predicate<GeEvent> normalizeOrSuppressDuplicateTradeEvent;
    private final IntConsumer clearRecentTradeEvent;
    private final Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier;
    private final BiConsumer<GeEvent, Boolean> recordLocalTradeDelta;
    private final Runnable scheduleRefreshSoon;

    GrandExchangeOfferChangedPluginHooks(
        Runnable loadOfferUpdateTimesForCurrentAccount,
        Map<Integer, OfferSnapshot> snapshots,
        OfferUpdateTracker trackOfferUpdate,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier,
        LongSupplier resolveAccountHash,
        java.util.function.LongConsumer ensureLocalTradesLoaded,
        Supplier<OfferEventBuildService> offerEventBuildServiceSupplier,
        Map<Integer, OfferUpdateStamp> offerUpdateStamps,
        BooleanSupplier localTradesLoadedThisLogin,
        LongSupplier lastLoginMs,
        Supplier<Client> clientSupplier,
        Predicate<GeEvent> normalizeOrSuppressDuplicateTradeEvent,
        IntConsumer clearRecentTradeEvent,
        Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier,
        BiConsumer<GeEvent, Boolean> recordLocalTradeDelta,
        Runnable scheduleRefreshSoon
    ) {
        this.loadOfferUpdateTimesForCurrentAccount = loadOfferUpdateTimesForCurrentAccount;
        this.snapshots = snapshots;
        this.trackOfferUpdate = trackOfferUpdate;
        this.profileSelectionFacadeServiceSupplier = profileSelectionFacadeServiceSupplier;
        this.resolveAccountHash = resolveAccountHash;
        this.ensureLocalTradesLoaded = ensureLocalTradesLoaded;
        this.offerEventBuildServiceSupplier = offerEventBuildServiceSupplier;
        this.offerUpdateStamps = offerUpdateStamps;
        this.localTradesLoadedThisLogin = localTradesLoadedThisLogin;
        this.lastLoginMs = lastLoginMs;
        this.clientSupplier = clientSupplier;
        this.normalizeOrSuppressDuplicateTradeEvent = normalizeOrSuppressDuplicateTradeEvent;
        this.clearRecentTradeEvent = clearRecentTradeEvent;
        this.uploadEventDispatchFacadeServiceSupplier = uploadEventDispatchFacadeServiceSupplier;
        this.recordLocalTradeDelta = recordLocalTradeDelta;
        this.scheduleRefreshSoon = scheduleRefreshSoon;
    }

    @Override
    public void loadOfferUpdateTimesForCurrentAccount() {
        if (loadOfferUpdateTimesForCurrentAccount != null) {
            loadOfferUpdateTimesForCurrentAccount.run();
        }
    }

    @Override
    public OfferSnapshot getSnapshot(int slot) {
        return snapshots != null ? snapshots.get(slot) : null;
    }

    @Override
    public void putSnapshot(int slot, OfferSnapshot snapshot) {
        if (snapshots != null) {
            snapshots.put(slot, snapshot);
        }
    }

    @Override
    public void trackOfferUpdate(int slot, OfferSnapshot previous, OfferSnapshot next) {
        if (trackOfferUpdate != null) {
            trackOfferUpdate.track(slot, previous, next);
        }
    }

    @Override
    public boolean hasSessionToken() {
        ProfileSelectionPresentationFacadeService service = profileSelectionFacadeServiceSupplier != null
            ? profileSelectionFacadeServiceSupplier.get()
            : null;
        return service != null && service.hasSessionToken();
    }

    @Override
    public long resolveAccountHash() {
        return resolveAccountHash != null ? resolveAccountHash.getAsLong() : -1L;
    }

    @Override
    public void ensureLocalTradesLoaded(long accountKey) {
        if (ensureLocalTradesLoaded != null) {
            ensureLocalTradesLoaded.accept(accountKey);
        }
    }

    @Override
    public OfferEventBuildService getOfferEventBuildService() {
        return offerEventBuildServiceSupplier != null ? offerEventBuildServiceSupplier.get() : null;
    }

    @Override
    public OfferUpdateStamp getOfferUpdateStamp(int slot) {
        return offerUpdateStamps != null ? offerUpdateStamps.get(slot) : null;
    }

    @Override
    public boolean localTradesLoadedThisLogin() {
        return localTradesLoadedThisLogin != null && localTradesLoadedThisLogin.getAsBoolean();
    }

    @Override
    public long lastLoginMs() {
        return lastLoginMs != null ? lastLoginMs.getAsLong() : 0L;
    }

    @Override
    public int currentWorld() {
        Client client = clientSupplier != null ? clientSupplier.get() : null;
        return client != null ? client.getWorld() : 0;
    }

    @Override
    public boolean normalizeOrSuppressDuplicateTradeEvent(GeEvent event) {
        return normalizeOrSuppressDuplicateTradeEvent != null && normalizeOrSuppressDuplicateTradeEvent.test(event);
    }

    @Override
    public void clearRecentTradeEvent(int slot) {
        if (clearRecentTradeEvent != null) {
            clearRecentTradeEvent.accept(slot);
        }
    }

    @Override
    public void enqueueEvent(GeEvent event) {
        UploadEventDispatchFacadeService service = uploadEventDispatchFacadeServiceSupplier != null
            ? uploadEventDispatchFacadeServiceSupplier.get()
            : null;
        if (service != null) {
            service.enqueueEvent(event);
        }
    }

    @Override
    public void recordLocalTradeDelta(GeEvent event, boolean baselineSynthetic) {
        if (recordLocalTradeDelta != null) {
            recordLocalTradeDelta.accept(event, baselineSynthetic);
        }
    }

    @Override
    public void scheduleRefreshSoon() {
        if (scheduleRefreshSoon != null) {
            scheduleRefreshSoon.run();
        }
    }
}
