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

import javax.inject.*;
import lombok.RequiredArgsConstructor;
import net.runelite.api.*;
import net.runelite.api.events.GrandExchangeOfferChanged;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class GrandExchangeOfferChangedHandler {
    private final Client client;
    private final PluginState state;

    private boolean hasSessionToken() {
        ProfileSelectionPresentation service =
            Bridge.get(ProfileSelectionPresentation.class);
        return service != null && service.hasSessionToken();
    }

    private long resolveAccountHash() {
        TradeSession service = Bridge.get(TradeSession.class);
        return service != null ? service.resolveAccountHash() : -1L;
    }

    private void clearRecentTradeEvent(int slot) {
        RecentTradeDeduper deduper = Bridge.get(RecentTradeDeduper.class);
        if (deduper != null) {
            deduper.clearSlot(slot);
        }
    }

    void handle(GrandExchangeOfferChanged event) {
        if (event == null) {
            return;
        }
        GrandExchangeOffer offer = event.getOffer();
        // Leaving LOGGED_IN, the client empties every slot and reports each one. That is the
        // client tidying up, not offers being collected, so diffing it against the live
        // snapshots would invent a completion for a trade that never finished.
        if (offer != null && offer.getState() == GrandExchangeOfferState.EMPTY
            && client != null && client.getGameState() != GameState.LOGGED_IN) {
            return;
        }
        Access.plugin().getOfferStampStateServices().loadOfferUpdateTimesForCurrentAccount();
        int slot = event.getSlot();

        OfferSnapshot previous = state.getSnapshots().get(slot);
        OfferSnapshot next = OfferSnapshot.fromOffer(slot, offer, previous);
        // The stamp is the last fill level this slot was seen at. trackOfferUpdate advances it
        // to `next` in place, so the delta has to be derived from a copy taken before that.
        Stamp stampBeforeUpdate = Stamp.copyOf(state.getOfferUpdateStamps().get(slot));
        state.getSnapshots().put(slot, next);
        Access.plugin().getOfferStampStateServices().trackOfferUpdate(slot, previous, next);

        boolean hasSessionToken = hasSessionToken();
        if (!hasSessionToken) {
            long accountKey = resolveAccountHash();
            if (accountKey > 0) {
                Access.plugin().getLocalTradesRuntimeService().ensureLocalTradesLoaded(accountKey);
            }
        }

        OfferEventBuild.Result result = Bridge.get(OfferEventBuild.class).derive(
            new OfferEventBuild.Input(
                previous,
                next,
                stampBeforeUpdate,
                Access.plugin().localTradesLoadedThisLogin,
                Access.plugin().getOfferStampStateServices().getLastLoginMs(),
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
        RecentTradeDeduper deduper = Bridge.get(RecentTradeDeduper.class);
        if (deduper != null && deduper.normalizeOrSuppress(geEvent)) {
            if (result.shouldClearRecentSlot()) {
                clearRecentTradeEvent(slot);
            }
            return;
        }

        UploadEventDispatch uploadFacade =
            Bridge.get(UploadEventDispatch.class);
        if (uploadFacade != null) {
            // The key the trade is filed under below, not the bare account hash: that is negative
            // for about half of all characters, and theirs went up with no code at all while
            // their stored trades, recipes and moves were sent under the name's key.
            geEvent.character_id = GeEvent.characterId(Bridge.get(AccountSession.class).resolveLocalAccountKey());
            uploadFacade.enqueueEvent(geEvent);
        }
        // Read after tracking: a fill that opens a new offer on a reused slot has only now
        // been given its own stamp, and the stored record must name that offer, not the last.
        Stamp stampAfterUpdate = state.getOfferUpdateStamps().get(slot);
        long offerStartMs = stampAfterUpdate != null ? stampAfterUpdate.firstSeenMs : 0L;
        Bridge.get(TradeDeltaRecorder.class).record(geEvent, result.isBaselineSynthetic(), offerStartMs);
        if (result.shouldClearRecentSlot()) {
            clearRecentTradeEvent(slot);
        }
        if (result.shouldScheduleRefresh()) {
            GeLifecyclePlugin plugin = Access.plugin();
            PanelRefresh coordinator = plugin.getPanelRefreshCoordinator();
            if (coordinator != null) {
                coordinator.scheduleRefreshSoon(plugin.scheduler);
            }
        }
    }
}
