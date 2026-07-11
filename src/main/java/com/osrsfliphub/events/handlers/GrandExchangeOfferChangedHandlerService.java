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
    private final Client client;
    private final PluginState state;

    @Inject
    GrandExchangeOfferChangedHandlerService(Client client, PluginState state) {
        this.client = client;
        this.state = state;
    }

    private static GeLifecycleOfferStampStateServices offerStampState() {
        return PluginAccess.plugin().getOfferStampStateServices();
    }

    private static RecentTradeDeduper recentTradeDeduper() {
        return PluginInjectorBridge.get(RecentTradeDeduper.class);
    }

    private boolean hasSessionToken() {
        ProfileSelectionPresentationFacadeService service =
            PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class);
        return service != null && service.hasSessionToken();
    }

    private long resolveAccountHash() {
        LocalTradeSessionFacadeService service = PluginInjectorBridge.get(LocalTradeSessionFacadeService.class);
        return service != null ? service.resolveAccountHash() : -1L;
    }

    private void clearRecentTradeEvent(int slot) {
        RecentTradeDeduper deduper = recentTradeDeduper();
        if (deduper != null) {
            deduper.clearSlot(slot);
        }
    }

    void handle(GrandExchangeOfferChanged event) {
        if (event == null) {
            return;
        }
        offerStampState().loadOfferUpdateTimesForCurrentAccount();
        GrandExchangeOffer offer = event.getOffer();
        int slot = event.getSlot();

        OfferSnapshot previous = state.getSnapshots().get(slot);
        OfferSnapshot next = OfferSnapshot.fromOffer(slot, offer, previous);
        state.getSnapshots().put(slot, next);
        offerStampState().trackOfferUpdate(slot, previous, next);

        boolean hasSessionToken = hasSessionToken();
        if (!hasSessionToken) {
            long accountKey = resolveAccountHash();
            if (accountKey > 0) {
                PluginAccess.plugin().getLocalTradesRuntimeService().ensureLocalTradesLoaded(accountKey);
            }
        }

        OfferEventBuildService.Result result = PluginInjectorBridge.get(OfferEventBuildService.class).derive(
            new OfferEventBuildService.Input(
                previous,
                next,
                state.getOfferUpdateStamps().get(slot),
                !hasSessionToken,
                PluginAccess.plugin().localTradesLoadedThisLogin,
                offerStampState().getLastLoginMs(),
                client != null ? client.getWorld() : 0
            )
        );
        if (result.shouldIgnore()) {
            if (result.shouldClearRecentSlot()) {
                clearRecentTradeEvent(slot);
            }
            return;
        }

        GeEvent geEvent = result.getEvent();
        RecentTradeDeduper deduper = recentTradeDeduper();
        if (deduper != null && deduper.normalizeOrSuppress(geEvent)) {
            if (result.shouldClearRecentSlot()) {
                clearRecentTradeEvent(slot);
            }
            return;
        }

        UploadEventDispatchFacadeService uploadFacade =
            PluginInjectorBridge.get(UploadEventDispatchFacadeService.class);
        if (uploadFacade != null) {
            uploadFacade.enqueueEvent(geEvent);
        }
        PluginInjectorBridge.get(LocalTradeDeltaRecorder.class).record(geEvent, result.isBaselineSynthetic());
        if (result.shouldClearRecentSlot()) {
            clearRecentTradeEvent(slot);
        }
        if (result.shouldScheduleRefresh()) {
            GeLifecyclePlugin plugin = PluginAccess.plugin();
            PanelRefreshCoordinator coordinator = plugin.getPanelRefreshCoordinator();
            if (coordinator != null) {
                coordinator.scheduleRefreshSoon(plugin.scheduler);
            }
        }
    }
}
