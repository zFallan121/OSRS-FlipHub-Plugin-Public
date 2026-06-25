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

import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.events.GrandExchangeOfferChanged;

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

    GrandExchangeOfferChangedHandlerService(Hooks hooks) {
        this.hooks = hooks;
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
