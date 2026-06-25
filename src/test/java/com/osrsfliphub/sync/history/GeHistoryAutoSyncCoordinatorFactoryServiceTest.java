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

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.widgets.Widget;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class GeHistoryAutoSyncCoordinatorFactoryServiceTest {
    @Test
    public void createBuildsCoordinatorThatUsesInjectedServices() {
        GeHistoryWipeStateStoreTestHooks storeHooks = new GeHistoryWipeStateStoreTestHooks();
        GeHistoryWipeStateStore wipeStateStore = new GeHistoryWipeStateStore(
            storeHooks,
            "cfg",
            "wipe_",
            "cursor_",
            45
        );
        wipeStateStore.setWipeBarrierArmed(42L, true);

        CountingAutoSyncHooks autoSyncHooks = new CountingAutoSyncHooks();
        GeHistoryAutoSyncService autoSyncService = new GeHistoryAutoSyncService(0L, autoSyncHooks);

        GeHistoryAutoSyncCoordinatorFactoryService factory = new GeHistoryAutoSyncCoordinatorFactoryService(
            new GeHistoryWidgetReadService(),
            new GeHistoryCursorService(45),
            new GeHistoryWipeBaselineDecisionService(2, 3),
            new GeHistoryAutoSyncMessageService(),
            wipeStateStore,
            autoSyncService
        );

        GeHistoryAutoSyncStateService autoSyncState = new GeHistoryAutoSyncStateService(0L);
        autoSyncState.arm();

        RecordingRuntimeHooks runtimeHooks = new RecordingRuntimeHooks();
        GeHistoryAutoSyncCoordinatorService coordinator = factory.create(autoSyncState, runtimeHooks);
        coordinator.attemptAutoSync();

        assertEquals(1, runtimeHooks.gameMessageCount);
        assertEquals("FlipHub GE history sync: wipe baseline set (0 trades).", runtimeHooks.lastGameMessage);
        assertFalse(autoSyncState.isPending());
        assertEquals("", storeHooks.readConfiguration("cfg", "cursor_42"));
        assertEquals(0, autoSyncHooks.nowCalls);
    }

    private static final class RecordingRuntimeHooks implements GeHistoryAutoSyncCoordinatorFactoryService.RuntimeHooks {
        private int gameMessageCount;
        private String lastGameMessage = "";

        @Override
        public boolean isClientLoggedIn() {
            return true;
        }

        @Override
        public long resolveLocalAccountKey() {
            return 42L;
        }

        @Override
        public GeHistoryAutoSyncCoordinatorService.HistorySnapshot readHistorySnapshot() {
            return new GeHistoryAutoSyncCoordinatorService.HistorySnapshot(true, new Widget[6]);
        }

        @Override
        public long nowMs() {
            return 1_000L;
        }

        @Override
        public void pushGameMessage(String message) {
            gameMessageCount++;
            lastGameMessage = message != null ? message : "";
        }

        @Override
        public void logAddedTrades(int addedTrades, int parsedTrades, long accountKey) {
            // no-op
        }
    }

    private static final class GeHistoryWipeStateStoreTestHooks implements GeHistoryWipeStateStore.Hooks {
        private final Map<String, String> values = new HashMap<>();

        @Override
        public String readConfiguration(String configGroup, String key) {
            return values.get(configGroup + ":" + key);
        }

        @Override
        public void writeConfiguration(String configGroup, String key, String value) {
            values.put(configGroup + ":" + key, value);
        }
    }

    private static final class CountingAutoSyncHooks implements GeHistoryAutoSyncService.Hooks {
        private int nowCalls;

        @Override
        public void ensureProfileLoaded(long accountKey) {
        }

        @Override
        public void ensureLocalSessionStart(long accountKey, long tsClientMs) {
        }

        @Override
        public java.util.List<LocalTradeDelta> snapshotLocalTradeDeltas(long accountKey) {
            return new java.util.ArrayList<>();
        }

        @Override
        public void cacheItemName(int itemId) {
        }

        @Override
        public void appendTradeDeltaPair(long accountKey, long accountwideKey, LocalTradeDelta delta) {
        }

        @Override
        public void applyDeltaToStatsCache(long accountKey, LocalTradeDelta delta) {
        }

        @Override
        public GeEvent buildUploadEvent(long profileKey, LocalTradeDelta delta) {
            return null;
        }

        @Override
        public void enqueueUploadEvent(GeEvent event) {
        }

        @Override
        public void requestEventFlush() {
        }

        @Override
        public void persistLocalTrades(long accountKey) {
        }

        @Override
        public void triggerStatsRefresh() {
        }

        @Override
        public void triggerPanelRefresh() {
        }

        @Override
        public long nowMs() {
            nowCalls++;
            return 1_000L;
        }
    }
}
