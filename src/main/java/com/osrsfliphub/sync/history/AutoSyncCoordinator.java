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

import java.util.*;
import javax.inject.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.widgets.Widget;

@Singleton
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class AutoSyncCoordinator {

    @RequiredArgsConstructor
    static final class HistorySnapshot {
        final boolean visible;
        final Widget[] widgets;
    }

    private final AutoSyncState autoSyncState;
    private final Client client;
    private final GeHistoryCursorService geHistoryCursorService;
    private final AutoSyncMessage autoSyncMessage;
    private final WipeStateStore wipeStateStore;
    private final AccountSession accountSession;
    private final WipeBaselineDecision wipeBaselineDecision;
    private final AutoSync sync;
    private final PluginState pluginState;

    private HistorySnapshot readHistorySnapshot() {
        Widget historyContainer = client.getWidget(Const.GE_HISTORY_GROUP_ID,
                Const.GE_HISTORY_CONTAINER_CHILD_ID);
        if (historyContainer == null || historyContainer.isHidden()) {
            return new HistorySnapshot(false, null);
        }
        return new HistorySnapshot(true, historyContainer.getDynamicChildren());
    }

    private void pushGameMessage(String message) {
        GeLifecyclePlugin plugin = Access.plugin();
        plugin.runtimeUtilityServices.pushGameMessage(plugin.client, message);
    }

    /**
     * The last sync the rows handed over are all known to be newer than, or NONE when that is
     * not known of every one of them: after a wipe the whole list may be handed over, and a
     * list longer than the cursor covers has rows the overlap says nothing about.
     */
    static AutoSyncTradeMatcher.LastSync sinceFor(boolean wipeBarrierArmed, int rows, int cursorRows,
                                                 AutoSyncTradeMatcher.LastSync stored) {
        return wipeBarrierArmed || rows > cursorRows ? AutoSyncTradeMatcher.LastSync.NONE : stored;
    }

    /**
     * No row in common with the last sync, so none could be told from one already recorded and
     * none was eligible. That is not "nothing new", and the player is not told that it is.
     */
    static boolean lostPlace(int overlap, int eligibleTradeCount) {
        return overlap == 0 && eligibleTradeCount == 0;
    }

    /** The read becomes where the next sync starts from: its rows, when it was made, and what was still in a slot. */
    private void persistPlace(long accountKey, List<String> currentCursor, long nowMs) {
        wipeStateStore.persistCursor(accountKey, currentCursor);
        wipeStateStore.persistLastSync(accountKey,
            AutoSyncTradeMatcher.LastSync.at(nowMs, pluginState.getOfferUpdateStamps()));
    }

    /** What to say when the current read becomes the cursor: why, if the old one was refused. */
    private String baselineSetMessage(GeHistoryCursorService.StoredCursor stored, List<String> currentCursor) {
        int size = currentCursor != null ? currentCursor.size() : 0;
        return stored != null && stored.staleFormat
            ? autoSyncMessage.cursorFormatResetMessage(size)
            : autoSyncMessage.baselineSetMessage(size);
    }

    void attemptAutoSync() {
        if (!autoSyncState.isPending() || client.getGameState() != GameState.LOGGED_IN) {
            return;
        }
        long accountKey = accountSession.resolveLocalAccountKey();
        if (accountKey <= 0) {
            return;
        }

        HistorySnapshot snapshot = readHistorySnapshot();
        if (snapshot == null || !snapshot.visible) {
            autoSyncState.markHistoryHidden();
            return;
        }

        long nowMs = System.currentTimeMillis();
        autoSyncState.noteHistoryVisible(nowMs);
        Widget[] historyWidgets = snapshot.widgets;
        boolean widgetsComplete = WidgetParser.hasCompleteWidgetGroups(historyWidgets);
        List<Trade> historyTrades = widgetsComplete ? WidgetParser.parse(historyWidgets) : new ArrayList<>();
        List<String> currentCursor = geHistoryCursorService.buildCursorSignatures(historyTrades);
        int widgetCount = historyWidgets != null ? historyWidgets.length : 0;
        AutoSyncState.ReadVerdict verdict =
            autoSyncState.observeRead(widgetsComplete, widgetCount, currentCursor, nowMs);
        if (verdict == AutoSyncState.ReadVerdict.WAIT) {
            return;
        }
        if (verdict == AutoSyncState.ReadVerdict.GIVE_UP) {
            autoSyncState.disarm();
            pushGameMessage(autoSyncMessage.readIncompleteMessage());
            log.info("GE history auto-sync gave up on account {}: the history list never settled", accountKey);
            return;
        }

        GeHistoryCursorService.StoredCursor stored = wipeStateStore.loadCursor(accountKey);
        List<String> storedCursor = stored.signatures;
        int overlap = geHistoryCursorService.computeOverlap(currentCursor, storedCursor);
        boolean wipeBarrierArmed = wipeStateStore.isWipeBarrierArmed(accountKey);
        WipeBaselineDecision.Decision decision = wipeBaselineDecision.decide(
            wipeBarrierArmed, currentCursor, storedCursor, historyTrades.size(), overlap);

        switch (decision.outcome) {
            case SET_BASELINE:
                persistPlace(accountKey, currentCursor, nowMs);
                autoSyncState.disarm();
                if (wipeBarrierArmed || stored.staleFormat) {
                    pushGameMessage(baselineSetMessage(stored, currentCursor));
                }
                return;
            case SKIP_MISMATCH:
                autoSyncState.disarm();
                pushGameMessage(autoSyncMessage.baselineMismatchMessage());
                return;
            case SKIP_SHORT_READ:
                autoSyncState.disarm();
                pushGameMessage(autoSyncMessage.shortReadMessage(currentCursor.size(), storedCursor.size()));
                log.warn("GE history auto-sync skipped for account {}: read {} trades but the last sync saw {}",
                    accountKey, currentCursor.size(), storedCursor.size());
                return;
            default:
                break;
        }

        List<Trade> eligibleTrades = eligibleTrades(historyTrades, decision.eligibleTradeCount);
        AutoSyncTradeMatcher.LastSync lastSync = sinceFor(wipeBarrierArmed, historyTrades.size(),
            currentCursor.size(), wipeStateStore.loadLastSync(accountKey, nowMs));
        AutoSync.SyncResult result = sync.sync(accountKey, eligibleTrades, lastSync);
        autoSyncState.disarm();
        int addedTrades = result != null ? result.addedTrades : 0;
        int parsedTrades = result != null ? result.parsedTrades : 0;
        boolean lostPlace = lostPlace(overlap, decision.eligibleTradeCount);
        pushGameMessage(lostPlace
            ? autoSyncMessage.lostPlaceMessage()
            : autoSyncMessage.syncResultMessage(addedTrades));
        if (lostPlace) {
            log.info("GE history auto-sync lost its place for account {}: none of the last sync's rows are listed",
                accountKey);
        }
        // Read again: what was just imported may be dated after nowMs, and the next sync leaves
        // an imported trade out only when it is dated no later than this.
        persistPlace(accountKey, currentCursor, System.currentTimeMillis());
        if (decision.releasesWipeBarrier()) {
            wipeStateStore.setWipeBarrierArmed(accountKey, false);
            log.info("GE history wipe barrier released for account {} after a reconciling sync", accountKey);
        }
        if (addedTrades > 0) {
            log.info("GE history auto-sync added {} missing trades ({} parsed) for account {}",
                addedTrades, parsedTrades, accountKey);
        }
    }

    /** The newest {@code count} rows: the ones above the overlap with the last sync. */
    private static List<Trade> eligibleTrades(List<Trade> historyTrades, int count) {
        if (count <= 0) {
            return new ArrayList<>();
        }
        if (count >= historyTrades.size()) {
            return historyTrades;
        }
        return new ArrayList<>(historyTrades.subList(0, count));
    }
}
