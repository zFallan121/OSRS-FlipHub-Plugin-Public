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
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GameStateChangedHandlerServiceTest {
    @Test
    public void handlesLogoutState() {
        RecordingHooks hooks = new RecordingHooks();
        hooks.panelAvailable = true;
        GameStateChangedHandlerService service = new GameStateChangedHandlerService(hooks);

        service.handle(GameState.LOGIN_SCREEN);

        assertEquals(1, hooks.persistOfferUpdateTimesCount);
        assertEquals(1, hooks.resetOfferUpdateStampsCount);
        assertEquals(1, hooks.clearSnapshotsCount);
        assertEquals(1, hooks.disarmGeHistoryAutoSyncCount);
        assertEquals(1, hooks.clearRecentTradeDeduperCount);
        assertEquals(1, hooks.updateProfileOptionsUICount);
        assertEquals(1, hooks.updateProfileHeaderCount);
        assertEquals(0, hooks.armGeHistoryAutoSyncCount);
    }

    @Test
    public void handlesLoginWithoutSessionAndVisiblePanel() {
        RecordingHooks hooks = new RecordingHooks();
        hooks.sessionToken = false;
        hooks.panelVisible = true;
        hooks.scheduler = true;
        hooks.linked = true;
        hooks.linkInput = "  abc  ";
        GameStateChangedHandlerService service = new GameStateChangedHandlerService(hooks);

        service.handle(GameState.LOGGED_IN);

        assertEquals(1, hooks.armGeHistoryAutoSyncCount);
        assertEquals(1, hooks.setLastLoginNowCount);
        assertEquals(1, hooks.loadOfferUpdateTimesCount);
        assertEquals(1, hooks.updateLocalAccountSessionStartCount);
        assertEquals(1, hooks.updateProfileForLoginCount);
        assertEquals(1, hooks.primeOfferSnapshotsCount);
        assertEquals(1, hooks.resetLocalTradesLoadStateCount);
        assertEquals(1, hooks.scheduleLocalTradesLoadCount);
        assertEquals(1, hooks.refreshWikiLatestPricesCount);
        assertEquals(1, hooks.attemptLinkCount);
        assertEquals("abc", hooks.lastLinkInput);
        assertEquals(1, hooks.setPanelVisibleCount);
        assertEquals(1, hooks.triggerPanelRefreshCount);
        assertEquals(1, hooks.triggerStatsRefreshCount);
        assertEquals(1, hooks.requestBackfillAttemptCount);
    }

    @Test
    public void skipsLocalTradeLoadWhenSessionTokenPresent() {
        RecordingHooks hooks = new RecordingHooks();
        hooks.sessionToken = true;
        GameStateChangedHandlerService service = new GameStateChangedHandlerService(hooks);

        service.handle(GameState.LOGGED_IN);

        assertEquals(0, hooks.resetLocalTradesLoadStateCount);
        assertEquals(0, hooks.scheduleLocalTradesLoadCount);
        assertEquals(0, hooks.refreshWikiLatestPricesCount);
    }

    private static final class RecordingHooks implements GameStateChangedHandlerService.Hooks {
        private boolean panelAvailable;
        private boolean sessionToken;
        private boolean panelVisible;
        private boolean scheduler;
        private boolean linked;
        private String linkInput = "";
        private String lastLinkInput = "";

        private int persistOfferUpdateTimesCount;
        private int resetOfferUpdateStampsCount;
        private int clearSnapshotsCount;
        private int disarmGeHistoryAutoSyncCount;
        private int clearRecentTradeDeduperCount;
        private int updateProfileOptionsUICount;
        private int updateProfileHeaderCount;
        private int armGeHistoryAutoSyncCount;
        private int setLastLoginNowCount;
        private int loadOfferUpdateTimesCount;
        private int updateLocalAccountSessionStartCount;
        private int updateProfileForLoginCount;
        private int primeOfferSnapshotsCount;
        private int resetLocalTradesLoadStateCount;
        private int scheduleLocalTradesLoadCount;
        private int refreshWikiLatestPricesCount;
        private int attemptLinkCount;
        private int setPanelVisibleCount;
        private int triggerPanelRefreshCount;
        private int triggerStatsRefreshCount;
        private int requestBackfillAttemptCount;

        @Override
        public void persistOfferUpdateTimes() {
            persistOfferUpdateTimesCount++;
        }

        @Override
        public void resetOfferUpdateStamps() {
            resetOfferUpdateStampsCount++;
        }

        @Override
        public void clearSnapshots() {
            clearSnapshotsCount++;
        }

        @Override
        public void disarmGeHistoryAutoSync() {
            disarmGeHistoryAutoSyncCount++;
        }

        @Override
        public void clearRecentTradeDeduper() {
            clearRecentTradeDeduperCount++;
        }

        @Override
        public boolean isPanelAvailable() {
            return panelAvailable;
        }

        @Override
        public void updateProfileOptionsUI() {
            updateProfileOptionsUICount++;
        }

        @Override
        public void updateProfileHeader() {
            updateProfileHeaderCount++;
        }

        @Override
        public void armGeHistoryAutoSync() {
            armGeHistoryAutoSyncCount++;
        }

        @Override
        public void setLastLoginNow() {
            setLastLoginNowCount++;
        }

        @Override
        public void loadOfferUpdateTimesForCurrentAccount() {
            loadOfferUpdateTimesCount++;
        }

        @Override
        public void updateLocalAccountSessionStart() {
            updateLocalAccountSessionStartCount++;
        }

        @Override
        public void updateProfileForLogin() {
            updateProfileForLoginCount++;
        }

        @Override
        public void primeOfferSnapshots() {
            primeOfferSnapshotsCount++;
        }

        @Override
        public boolean hasSessionToken() {
            return sessionToken;
        }

        @Override
        public void resetLocalTradesLoadState() {
            resetLocalTradesLoadStateCount++;
        }

        @Override
        public void scheduleLocalTradesLoad() {
            scheduleLocalTradesLoadCount++;
        }

        @Override
        public void refreshWikiLatestPrices() {
            refreshWikiLatestPricesCount++;
        }

        @Override
        public String getLinkInput() {
            return linkInput;
        }

        @Override
        public void attemptLink(String linkInput) {
            attemptLinkCount++;
            lastLinkInput = linkInput;
        }

        @Override
        public boolean isPanelVisible() {
            return panelVisible;
        }

        @Override
        public void setPanelVisible(boolean visible) {
            setPanelVisibleCount++;
        }

        @Override
        public void triggerPanelRefresh() {
            triggerPanelRefreshCount++;
        }

        @Override
        public void triggerStatsRefresh() {
            triggerStatsRefreshCount++;
        }

        @Override
        public boolean hasScheduler() {
            return scheduler;
        }

        @Override
        public boolean isLinked() {
            return linked;
        }

        @Override
        public void requestBackfillAttempt(int delaySeconds, boolean forceRefresh) {
            requestBackfillAttemptCount++;
        }
    }
}
