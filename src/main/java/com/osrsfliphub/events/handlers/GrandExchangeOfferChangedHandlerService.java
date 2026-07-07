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

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.events.GrandExchangeOfferChanged;

@Singleton
final class GrandExchangeOfferChangedHandlerService {
    interface Hooks {
        void loadOfferUpdateTimesForCurrentAccount();
        OfferSnapshot getSnapshot(int slot);
        void putSnapshot(int slot, OfferSnapshot snapshot);
        void trackOfferUpdate(int slot, OfferSnapshot previous, OfferSnapshot next);
        boolean hasSessionToken();
        long resolveAccountHash();
        void ensureLocalTradesLoaded(long accountKey);
        OfferEventBuildService getOfferEventBuildService();
        OfferUpdateStamp getOfferUpdateStamp(int slot);
        boolean localTradesLoadedThisLogin();
        long lastLoginMs();
        int currentWorld();
        boolean normalizeOrSuppressDuplicateTradeEvent(GeEvent event);
        void clearRecentTradeEvent(int slot);
        void enqueueEvent(GeEvent event);
        void recordLocalTradeDelta(GeEvent event, boolean baselineSynthetic);
        void scheduleRefreshSoon();
    }

    private final Hooks hooks;

    @Inject
    GrandExchangeOfferChangedHandlerService(Client client, PluginState state) {
        this(productionHooks(client, state));
    }

    GrandExchangeOfferChangedHandlerService(Hooks hooks) {
        this.hooks = hooks;
    }

    private static GeLifecycleOfferStampStateServices offerStampState() {
        return PluginAccess.plugin().getOfferStampStateServices();
    }

    private static RecentTradeDeduper recentTradeDeduper() {
        return PluginInjectorBridge.get(RecentTradeDeduper.class);
    }

    private static Hooks productionHooks(Client client, PluginState state) {
        return new Hooks() {
            @Override
            public void loadOfferUpdateTimesForCurrentAccount() {
                offerStampState().loadOfferUpdateTimesForCurrentAccount();
            }

            @Override
            public OfferSnapshot getSnapshot(int slot) {
                return state.getSnapshots().get(slot);
            }

            @Override
            public void putSnapshot(int slot, OfferSnapshot snapshot) {
                state.getSnapshots().put(slot, snapshot);
            }

            @Override
            public void trackOfferUpdate(int slot, OfferSnapshot previous, OfferSnapshot next) {
                offerStampState().trackOfferUpdate(slot, previous, next);
            }

            @Override
            public boolean hasSessionToken() {
                ProfileSelectionPresentationFacadeService service = PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class);
                return service != null && service.hasSessionToken();
            }

            @Override
            public long resolveAccountHash() {
                LocalTradeSessionFacadeService service =
                    PluginInjectorBridge.get(LocalTradeSessionFacadeService.class);
                return service != null ? service.resolveAccountHash() : -1L;
            }

            @Override
            public void ensureLocalTradesLoaded(long accountKey) {
                PluginAccess.plugin().getLocalTradesRuntimeService().ensureLocalTradesLoaded(accountKey);
            }

            @Override
            public OfferEventBuildService getOfferEventBuildService() {
                return PluginInjectorBridge.get(OfferEventBuildService.class);
            }

            @Override
            public OfferUpdateStamp getOfferUpdateStamp(int slot) {
                return state.getOfferUpdateStamps().get(slot);
            }

            @Override
            public boolean localTradesLoadedThisLogin() {
                return PluginAccess.plugin().localTradesLoadedThisLogin;
            }

            @Override
            public long lastLoginMs() {
                return offerStampState().getLastLoginMs();
            }

            @Override
            public int currentWorld() {
                return client != null ? client.getWorld() : 0;
            }

            @Override
            public boolean normalizeOrSuppressDuplicateTradeEvent(GeEvent event) {
                RecentTradeDeduper deduper = recentTradeDeduper();
                return deduper != null && deduper.normalizeOrSuppress(event);
            }

            @Override
            public void clearRecentTradeEvent(int slot) {
                RecentTradeDeduper deduper = recentTradeDeduper();
                if (deduper != null) {
                    deduper.clearSlot(slot);
                }
            }

            @Override
            public void enqueueEvent(GeEvent event) {
                UploadEventDispatchFacadeService service =
                    PluginInjectorBridge.get(UploadEventDispatchFacadeService.class);
                if (service != null) {
                    service.enqueueEvent(event);
                }
            }

            @Override
            public void recordLocalTradeDelta(GeEvent event, boolean baselineSynthetic) {
                PluginInjectorBridge.get(LocalTradeDeltaRecorder.class)
                    .record(event, baselineSynthetic);
            }

            @Override
            public void scheduleRefreshSoon() {
                GeLifecyclePlugin plugin = PluginAccess.plugin();
                PanelRefreshCoordinator coordinator = plugin.getPanelRefreshCoordinator();
                if (coordinator != null) {
                    coordinator.scheduleRefreshSoon(plugin.scheduler);
                }
            }
        };
    }

    void handle(GrandExchangeOfferChanged event) {
        if (hooks == null || event == null) {
            return;
        }
        hooks.loadOfferUpdateTimesForCurrentAccount();
        GrandExchangeOffer offer = event.getOffer();
        int slot = event.getSlot();

        OfferSnapshot previous = hooks.getSnapshot(slot);
        OfferSnapshot next = OfferSnapshot.fromOffer(slot, offer, previous);
        hooks.putSnapshot(slot, next);
        hooks.trackOfferUpdate(slot, previous, next);

        boolean hasSessionToken = hooks.hasSessionToken();
        if (!hasSessionToken) {
            long accountKey = hooks.resolveAccountHash();
            if (accountKey > 0) {
                hooks.ensureLocalTradesLoaded(accountKey);
            }
        }

        OfferEventBuildService.Result result = hooks.getOfferEventBuildService().derive(
            new OfferEventBuildService.Input(
                previous,
                next,
                hooks.getOfferUpdateStamp(slot),
                !hasSessionToken,
                hooks.localTradesLoadedThisLogin(),
                hooks.lastLoginMs(),
                hooks.currentWorld()
            )
        );
        if (result.shouldIgnore()) {
            if (result.shouldClearRecentSlot()) {
                hooks.clearRecentTradeEvent(slot);
            }
            return;
        }

        GeEvent geEvent = result.getEvent();
        if (hooks.normalizeOrSuppressDuplicateTradeEvent(geEvent)) {
            if (result.shouldClearRecentSlot()) {
                hooks.clearRecentTradeEvent(slot);
            }
            return;
        }

        hooks.enqueueEvent(geEvent);
        hooks.recordLocalTradeDelta(geEvent, result.isBaselineSynthetic());
        if (result.shouldClearRecentSlot()) {
            hooks.clearRecentTradeEvent(slot);
        }
        if (result.shouldScheduleRefresh()) {
            hooks.scheduleRefreshSoon();
        }
    }
}
