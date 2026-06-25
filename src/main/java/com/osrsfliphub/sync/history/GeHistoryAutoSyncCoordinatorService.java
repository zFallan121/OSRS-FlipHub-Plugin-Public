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
import java.util.List;
import net.runelite.api.widgets.Widget;

final class GeHistoryAutoSyncCoordinatorService {
    static final class HistorySnapshot {
        final boolean visible;
        final Widget[] widgets;

        HistorySnapshot(boolean visible, Widget[] widgets) {
            this.visible = visible;
            this.widgets = widgets;
        }
    }

    interface Hooks {
        boolean isClientLoggedIn();
        long resolveLocalAccountKey();
        HistorySnapshot readHistorySnapshot();
        long nowMs();
        boolean hasCompleteWidgetGroups(Widget[] historyWidgets);
        List<GeHistoryTrade> parseHistoryTrades(Widget[] historyWidgets);
        List<String> buildCursorSignatures(List<GeHistoryTrade> trades);
        int computeCursorOverlap(List<String> currentCursor, List<String> storedCursor);
        GeHistoryWipeBaselineDecisionService.Decision decideWipeBaseline(List<String> currentCursor,
                                                                         List<String> storedCursor,
                                                                         int parsedTradesCount,
                                                                         int overlap);
        String baselineSetMessage(int cursorSize);
        String baselineMismatchMessage();
        String syncResultMessage(int addedTrades);
        boolean isWipeBarrierArmed(long accountKey);
        List<String> loadCursor(long accountKey);
        void persistCursor(long accountKey, List<String> cursor);
        GeHistoryAutoSyncService.SyncResult sync(long accountKey, List<GeHistoryTrade> eligibleTrades);
        void pushGameMessage(String message);
        void logAddedTrades(int addedTrades, int parsedTrades, long accountKey);
    }

    private final GeHistoryAutoSyncStateService autoSyncState;
    private final Hooks hooks;

    GeHistoryAutoSyncCoordinatorService(GeHistoryAutoSyncStateService autoSyncState, Hooks hooks) {
        this.autoSyncState = autoSyncState;
        this.hooks = hooks;
    }

    void attemptAutoSync() {
        if (autoSyncState == null || hooks == null) {
            return;
        }
        if (!autoSyncState.isPending() || !hooks.isClientLoggedIn()) {
            return;
        }
        long accountKey = hooks.resolveLocalAccountKey();
        if (accountKey <= 0) {
            return;
        }

        HistorySnapshot snapshot = hooks.readHistorySnapshot();
        if (snapshot == null || !snapshot.visible) {
            autoSyncState.markHistoryHidden();
            return;
        }

        long nowMs = hooks.nowMs();
        autoSyncState.noteHistoryVisible(nowMs);
        Widget[] historyWidgets = snapshot.widgets;
        boolean widgetsIncomplete = !hooks.hasCompleteWidgetGroups(historyWidgets);
        if (autoSyncState.shouldWaitForSettle(widgetsIncomplete, nowMs)) {
            return;
        }

        List<GeHistoryTrade> historyTrades = hooks.parseHistoryTrades(historyWidgets);
        if (historyTrades == null) {
            historyTrades = new ArrayList<>();
        }
        List<GeHistoryTrade> eligibleTrades = historyTrades;
        List<String> currentCursor = hooks.buildCursorSignatures(historyTrades);
        List<String> storedCursor = hooks.loadCursor(accountKey);
        int overlap = hooks.computeCursorOverlap(currentCursor, storedCursor);
        boolean wipeBarrierArmed = hooks.isWipeBarrierArmed(accountKey);
        if (wipeBarrierArmed) {
            GeHistoryWipeBaselineDecisionService.Decision decision = hooks.decideWipeBaseline(
                currentCursor,
                storedCursor,
                historyTrades.size(),
                overlap
            );

            if (decision.outcome == GeHistoryWipeBaselineDecisionService.Outcome.SET_BASELINE) {
                hooks.persistCursor(accountKey, currentCursor);
                autoSyncState.disarm();
                hooks.pushGameMessage(hooks.baselineSetMessage(currentCursor != null ? currentCursor.size() : 0));
                return;
            }

            if (decision.outcome == GeHistoryWipeBaselineDecisionService.Outcome.SKIP_MISMATCH) {
                autoSyncState.disarm();
                hooks.pushGameMessage(hooks.baselineMismatchMessage());
                return;
            }

            int newCount = decision.eligibleTradeCount;
            if (newCount <= 0) {
                eligibleTrades = new ArrayList<>();
            } else if (newCount >= historyTrades.size()) {
                eligibleTrades = historyTrades;
            } else {
                eligibleTrades = new ArrayList<>(historyTrades.subList(0, newCount));
            }
        } else {
            if (storedCursor == null || storedCursor.isEmpty()) {
                hooks.persistCursor(accountKey, currentCursor);
                autoSyncState.disarm();
                return;
            }
            if (overlap <= 0) {
                hooks.persistCursor(accountKey, currentCursor);
                autoSyncState.disarm();
                hooks.pushGameMessage(hooks.syncResultMessage(0));
                return;
            }
            int newCount = Math.max(0, historyTrades.size() - overlap);
            if (newCount <= 0) {
                eligibleTrades = new ArrayList<>();
            } else if (newCount < historyTrades.size()) {
                eligibleTrades = new ArrayList<>(historyTrades.subList(0, newCount));
            }
        }

        GeHistoryAutoSyncService.SyncResult result = hooks.sync(accountKey, eligibleTrades);
        autoSyncState.disarm();
        int addedTrades = result != null ? result.addedTrades : 0;
        int parsedTrades = result != null ? result.parsedTrades : 0;
        hooks.pushGameMessage(hooks.syncResultMessage(addedTrades));
        if (currentCursor != null) {
            hooks.persistCursor(accountKey, currentCursor);
        }
        if (addedTrades > 0) {
            hooks.logAddedTrades(addedTrades, parsedTrades, accountKey);
        }
    }
}
