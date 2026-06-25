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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.widgets.Widget;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class GeHistoryAutoSyncCoordinatorServiceTest {
    @Test
    public void hiddenHistoryMarksHiddenAndDoesNotSync() {
        GeHistoryAutoSyncStateService state = new GeHistoryAutoSyncStateService(2_000L);
        state.arm();
        TestHooks hooks = new TestHooks();
        hooks.historySnapshot = new GeHistoryAutoSyncCoordinatorService.HistorySnapshot(false, null);
        GeHistoryAutoSyncCoordinatorService coordinator = new GeHistoryAutoSyncCoordinatorService(state, hooks);

        coordinator.attemptAutoSync();

        assertEquals(0, hooks.syncCalls);
        assertEquals(0, hooks.gameMessages.size());
        assertTrue(state.isPending());
    }

    @Test
    public void setBaselinePersistsCursorDisarmsAndShowsBaselineMessage() {
        GeHistoryAutoSyncStateService state = new GeHistoryAutoSyncStateService(2_000L);
        state.arm();
        TestHooks hooks = new TestHooks();
        hooks.wipeBarrierArmed = true;
        hooks.builtCursor = Arrays.asList("a", "b");
        hooks.loadedCursor = Collections.emptyList();
        hooks.decision = GeHistoryWipeBaselineDecisionService.Decision.setBaseline();
        GeHistoryAutoSyncCoordinatorService coordinator = new GeHistoryAutoSyncCoordinatorService(state, hooks);

        coordinator.attemptAutoSync();

        assertEquals(0, hooks.syncCalls);
        assertEquals(1, hooks.persistedCursors.size());
        assertEquals(Arrays.asList("a", "b"), hooks.persistedCursors.get(0).cursor);
        assertEquals(Arrays.asList("BASELINE:2"), hooks.gameMessages);
        assertFalse(state.isPending());
    }

    @Test
    public void skipMismatchDisarmsWithoutPersistingOrSyncing() {
        GeHistoryAutoSyncStateService state = new GeHistoryAutoSyncStateService(2_000L);
        state.arm();
        TestHooks hooks = new TestHooks();
        hooks.wipeBarrierArmed = true;
        hooks.decision = GeHistoryWipeBaselineDecisionService.Decision.skipMismatch();
        GeHistoryAutoSyncCoordinatorService coordinator = new GeHistoryAutoSyncCoordinatorService(state, hooks);

        coordinator.attemptAutoSync();

        assertEquals(0, hooks.syncCalls);
        assertEquals(0, hooks.persistedCursors.size());
        assertEquals(Arrays.asList("MISMATCH"), hooks.gameMessages);
        assertFalse(state.isPending());
    }

    @Test
    public void proceedSyncsSubsetPersistsCursorAndLogsWhenTradesAdded() {
        GeHistoryAutoSyncStateService state = new GeHistoryAutoSyncStateService(2_000L);
        state.arm();
        TestHooks hooks = new TestHooks();
        hooks.wipeBarrierArmed = true;
        hooks.builtCursor = Arrays.asList("c1", "c2");
        hooks.decision = GeHistoryWipeBaselineDecisionService.Decision.proceed(1);
        hooks.syncResult = new GeHistoryAutoSyncService.SyncResult(2, 1);
        GeHistoryAutoSyncCoordinatorService coordinator = new GeHistoryAutoSyncCoordinatorService(state, hooks);

        coordinator.attemptAutoSync();

        assertEquals(1, hooks.syncCalls);
        assertEquals(1, hooks.syncedTrades.size());
        assertSame(hooks.parsedTrades.get(0), hooks.syncedTrades.get(0));
        assertEquals(1, hooks.persistedCursors.size());
        assertEquals(Arrays.asList("SYNC:1"), hooks.gameMessages);
        assertEquals(1, hooks.logCalls);
        assertEquals(1, hooks.loggedAddedTrades);
        assertEquals(2, hooks.loggedParsedTrades);
        assertFalse(state.isPending());
    }

    @Test
    public void nonWipeWithoutStoredCursorSetsCursorBaselineAndSkipsSync() {
        GeHistoryAutoSyncStateService state = new GeHistoryAutoSyncStateService(2_000L);
        state.arm();
        TestHooks hooks = new TestHooks();
        hooks.wipeBarrierArmed = false;
        hooks.loadedCursor = Collections.emptyList();
        GeHistoryAutoSyncCoordinatorService coordinator = new GeHistoryAutoSyncCoordinatorService(state, hooks);

        coordinator.attemptAutoSync();

        assertEquals(0, hooks.syncCalls);
        assertEquals(1, hooks.persistedCursors.size());
        assertEquals(hooks.builtCursor, hooks.persistedCursors.get(0).cursor);
        assertTrue(hooks.gameMessages.isEmpty());
        assertFalse(state.isPending());
    }

    @Test
    public void nonWipeWithCursorOverlapSyncsNewHeadTradesOnly() {
        GeHistoryAutoSyncStateService state = new GeHistoryAutoSyncStateService(2_000L);
        state.arm();
        TestHooks hooks = new TestHooks();
        hooks.wipeBarrierArmed = false;
        hooks.parsedTrades = Arrays.asList(
            new GeHistoryTrade(100, true, 2, 50, 100L),
            new GeHistoryTrade(200, false, 3, 70, 205L),
            new GeHistoryTrade(300, true, 1, 90, 90L)
        );
        hooks.builtCursor = Arrays.asList("n1", "x", "y");
        hooks.loadedCursor = Arrays.asList("x", "y");
        hooks.cursorOverlap = 2;
        hooks.syncResult = new GeHistoryAutoSyncService.SyncResult(1, 1);
        GeHistoryAutoSyncCoordinatorService coordinator = new GeHistoryAutoSyncCoordinatorService(state, hooks);

        coordinator.attemptAutoSync();

        assertEquals(1, hooks.syncCalls);
        assertEquals(1, hooks.syncedTrades.size());
        assertSame(hooks.parsedTrades.get(0), hooks.syncedTrades.get(0));
        assertEquals(1, hooks.persistedCursors.size());
        assertEquals(hooks.builtCursor, hooks.persistedCursors.get(0).cursor);
        assertEquals(Arrays.asList("SYNC:1"), hooks.gameMessages);
        assertEquals(1, hooks.logCalls);
        assertFalse(state.isPending());
    }

    private static final class PersistCursorCall {
        private final long accountKey;
        private final List<String> cursor;

        private PersistCursorCall(long accountKey, List<String> cursor) {
            this.accountKey = accountKey;
            this.cursor = cursor;
        }
    }

    private static final class TestHooks implements GeHistoryAutoSyncCoordinatorService.Hooks {
        private boolean clientLoggedIn = true;
        private long localAccountKey = 42L;
        private GeHistoryAutoSyncCoordinatorService.HistorySnapshot historySnapshot =
            new GeHistoryAutoSyncCoordinatorService.HistorySnapshot(true, new Widget[6]);
        private long nowMs = 10_000L;
        private boolean completeWidgetGroups = true;
        private List<GeHistoryTrade> parsedTrades = Arrays.asList(
            new GeHistoryTrade(100, true, 2, 50, 100L),
            new GeHistoryTrade(200, false, 3, 70, 205L)
        );
        private List<String> builtCursor = Arrays.asList("x", "y");
        private List<String> loadedCursor = Arrays.asList("x");
        private int cursorOverlap;
        private GeHistoryWipeBaselineDecisionService.Decision decision =
            GeHistoryWipeBaselineDecisionService.Decision.proceed(2);
        private boolean wipeBarrierArmed;
        private GeHistoryAutoSyncService.SyncResult syncResult = new GeHistoryAutoSyncService.SyncResult(2, 0);
        private final List<PersistCursorCall> persistedCursors = new ArrayList<>();
        private final List<String> gameMessages = new ArrayList<>();
        private int syncCalls;
        private List<GeHistoryTrade> syncedTrades = new ArrayList<>();
        private int logCalls;
        private int loggedAddedTrades;
        private int loggedParsedTrades;

        @Override
        public boolean isClientLoggedIn() {
            return clientLoggedIn;
        }

        @Override
        public long resolveLocalAccountKey() {
            return localAccountKey;
        }

        @Override
        public GeHistoryAutoSyncCoordinatorService.HistorySnapshot readHistorySnapshot() {
            return historySnapshot;
        }

        @Override
        public long nowMs() {
            return nowMs;
        }

        @Override
        public boolean hasCompleteWidgetGroups(Widget[] historyWidgets) {
            return completeWidgetGroups;
        }

        @Override
        public List<GeHistoryTrade> parseHistoryTrades(Widget[] historyWidgets) {
            return parsedTrades;
        }

        @Override
        public List<String> buildCursorSignatures(List<GeHistoryTrade> trades) {
            return builtCursor;
        }

        @Override
        public int computeCursorOverlap(List<String> currentCursor, List<String> storedCursor) {
            return cursorOverlap;
        }

        @Override
        public GeHistoryWipeBaselineDecisionService.Decision decideWipeBaseline(List<String> currentCursor,
                                                                                List<String> storedCursor,
                                                                                int parsedTradesCount,
                                                                                int overlap) {
            return decision;
        }

        @Override
        public String baselineSetMessage(int cursorSize) {
            return "BASELINE:" + cursorSize;
        }

        @Override
        public String baselineMismatchMessage() {
            return "MISMATCH";
        }

        @Override
        public String syncResultMessage(int addedTrades) {
            return "SYNC:" + addedTrades;
        }

        @Override
        public boolean isWipeBarrierArmed(long accountKey) {
            return wipeBarrierArmed;
        }

        @Override
        public List<String> loadCursor(long accountKey) {
            return loadedCursor;
        }

        @Override
        public void persistCursor(long accountKey, List<String> cursor) {
            persistedCursors.add(new PersistCursorCall(accountKey, cursor));
        }

        @Override
        public GeHistoryAutoSyncService.SyncResult sync(long accountKey, List<GeHistoryTrade> eligibleTrades) {
            syncCalls++;
            syncedTrades = eligibleTrades != null ? new ArrayList<>(eligibleTrades) : new ArrayList<>();
            return syncResult;
        }

        @Override
        public void pushGameMessage(String message) {
            gameMessages.add(message);
        }

        @Override
        public void logAddedTrades(int addedTrades, int parsedTrades, long accountKey) {
            logCalls++;
            loggedAddedTrades = addedTrades;
            loggedParsedTrades = parsedTrades;
        }
    }
}
