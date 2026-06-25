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

import net.runelite.api.GameState;

final class GameStateChangedHandlerService {
    interface Hooks {
        void persistOfferUpdateTimes();
        void resetOfferUpdateStamps();
        void clearSnapshots();
        void disarmGeHistoryAutoSync();
        void clearRecentTradeDeduper();
        boolean isPanelAvailable();
        void updateProfileOptionsUI();
        void updateProfileHeader();
        void armGeHistoryAutoSync();
        void setLastLoginNow();
        void loadOfferUpdateTimesForCurrentAccount();
        void updateLocalAccountSessionStart();
        void updateProfileForLogin();
        void primeOfferSnapshots();
        boolean hasSessionToken();
        void resetLocalTradesLoadState();
        void scheduleLocalTradesLoad();
        void refreshWikiLatestPrices();
        String getLinkInput();
        void attemptLink(String linkInput);
        boolean isPanelVisible();
        void setPanelVisible(boolean visible);
        void triggerPanelRefresh();
        void triggerStatsRefresh();
        boolean hasScheduler();
        boolean isLinked();
        void requestBackfillAttempt(int delaySeconds, boolean forceRefresh);
    }

    private final Hooks hooks;

    GameStateChangedHandlerService(Hooks hooks) {
        this.hooks = hooks;
    }

    void handle(GameState gameState) {
        if (hooks == null || gameState == null) {
            return;
        }

        if (gameState != GameState.LOGGED_IN) {
            hooks.persistOfferUpdateTimes();
            hooks.resetOfferUpdateStamps();
            hooks.clearSnapshots();
            hooks.disarmGeHistoryAutoSync();
            hooks.clearRecentTradeDeduper();
            if (hooks.isPanelAvailable()) {
                hooks.updateProfileOptionsUI();
                hooks.updateProfileHeader();
            }
            return;
        }

        hooks.armGeHistoryAutoSync();
        hooks.setLastLoginNow();
        hooks.loadOfferUpdateTimesForCurrentAccount();
        hooks.updateLocalAccountSessionStart();
        hooks.updateProfileForLogin();
        hooks.primeOfferSnapshots();

        if (!hooks.hasSessionToken()) {
            hooks.resetLocalTradesLoadState();
            hooks.scheduleLocalTradesLoad();
            hooks.refreshWikiLatestPrices();
        }

        String linkInput = hooks.getLinkInput();
        if (linkInput != null && !linkInput.trim().isEmpty()) {
            hooks.attemptLink(linkInput.trim());
        }

        boolean visible = hooks.isPanelVisible();
        hooks.setPanelVisible(visible);
        if (visible) {
            hooks.triggerPanelRefresh();
            hooks.triggerStatsRefresh();
        }

        if (hooks.hasScheduler() && hooks.isLinked()) {
            hooks.requestBackfillAttempt(8, true);
        }
    }
}
