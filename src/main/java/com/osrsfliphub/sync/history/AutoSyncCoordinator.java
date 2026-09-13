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
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.widgets.Widget;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
final class AutoSyncCoordinator {

    static final class HistorySnapshot {
        final boolean visible;
        final Widget[] widgets;

        HistorySnapshot(boolean visible, Widget[] widgets) {
            this.visible = visible;
            this.widgets = widgets;
        }
    }

    private final AutoSyncState autoSyncState;
    private final Client client;

    @Inject
    AutoSyncCoordinator(Client client) {
        this.autoSyncState = Bridge.get(AutoSyncState.class);
        this.client = client;
    }

    private HistorySnapshot readHistorySnapshot() {
        Widget historyContainer = client != null
            ? client.getWidget(Const.GE_HISTORY_GROUP_ID,
                Const.GE_HISTORY_CONTAINER_CHILD_ID)
            : null;
        if (historyContainer == null || historyContainer.isHidden()) {
            return new HistorySnapshot(false, null);
        }
        return new HistorySnapshot(true, historyContainer.getDynamicChildren());
    }

    private GeHistoryCursorService cursorService() {
        return Bridge.get(GeHistoryCursorService.class);
    }

    private AutoSyncMessage messageService() {
        return Bridge.get(AutoSyncMessage.class);
    }

    private WipeStateStore wipeStore() {
        return Bridge.get(WipeStateStore.class);
    }

    private List<String> buildCursorSignatures(List<Trade> trades) {
        GeHistoryCursorService service = cursorService();
        return service != null ? service.buildCursorSignatures(trades) : new ArrayList<>();
    }

    private String syncResultMessage(int addedTrades) {
        AutoSyncMessage service = messageService();
        return service != null ? service.syncResultMessage(addedTrades) : "";
    }

    private void persistCursor(long accountKey, List<String> cursor) {
        WipeStateStore store = wipeStore();
        if (store != null) {
            store.persistCursor(accountKey, cursor);
        }
    }

    private void pushGameMessage(String message) {
        GeLifecyclePlugin plugin = Access.plugin();
        plugin.runtimeUtilityServices.pushGameMessage(plugin.client, message);
    }

    /** What to say when the current read becomes the cursor: why, if the old one was refused. */
    private String baselineSetMessage(GeHistoryCursorService.StoredCursor stored, List<String> currentCursor) {
        AutoSyncMessage messages = messageService();
        if (messages == null) {
            return "";
        }
        int size = currentCursor != null ? currentCursor.size() : 0;
        return stored != null && stored.staleFormat
            ? messages.cursorFormatResetMessage(size)
            : messages.baselineSetMessage(size);
    }

    void attemptAutoSync() {
        if (autoSyncState == null) {
            return;
        }
        if (!autoSyncState.isPending() || client == null || client.getGameState() != GameState.LOGGED_IN) {
            return;
        }
        AccountSession session = Bridge.get(AccountSession.class);
        long accountKey = session != null ? session.resolveLocalAccountKey() : -1L;
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
        WidgetRead widgetRead = Bridge.get(WidgetRead.class);
        boolean widgetsComplete = widgetRead != null && widgetRead.hasCompleteWidgetGroups(historyWidgets);
        List<Trade> historyTrades = widgetsComplete ? widgetRead.parseTrades(historyWidgets) : null;
        if (historyTrades == null) {
            historyTrades = new ArrayList<>();
        }
        List<String> currentCursor = buildCursorSignatures(historyTrades);
        int widgetCount = historyWidgets != null ? historyWidgets.length : 0;
        AutoSyncState.ReadVerdict verdict =
            autoSyncState.observeRead(widgetsComplete, widgetCount, currentCursor, nowMs);
        if (verdict == AutoSyncState.ReadVerdict.WAIT) {
            return;
        }
        AutoSyncMessage messages = messageService();
        if (verdict == AutoSyncState.ReadVerdict.GIVE_UP) {
            autoSyncState.disarm();
            pushGameMessage(messages != null ? messages.readIncompleteMessage() : "");
            log.info("GE history auto-sync gave up on account {}: the history list never settled", accountKey);
            return;
        }

        WipeStateStore wipeStore = wipeStore();
        GeHistoryCursorService.StoredCursor stored = wipeStore != null
            ? wipeStore.loadCursor(accountKey)
            : GeHistoryCursorService.StoredCursor.NONE;
        List<String> storedCursor = stored.signatures;
        GeHistoryCursorService cursorService = cursorService();
        int overlap = cursorService != null ? cursorService.computeOverlap(currentCursor, storedCursor) : 0;
        boolean wipeBarrierArmed = wipeStore != null && wipeStore.isWipeBarrierArmed(accountKey);
        WipeBaselineDecision decisionService =
            Bridge.get(WipeBaselineDecision.class);
        WipeBaselineDecision.Decision decision = decisionService != null
            ? decisionService.decide(wipeBarrierArmed, currentCursor, storedCursor, historyTrades.size(), overlap)
            : WipeBaselineDecision.Decision.proceed(historyTrades.size());

        switch (decision.outcome) {
            case SET_BASELINE:
                persistCursor(accountKey, currentCursor);
                autoSyncState.disarm();
                if (wipeBarrierArmed || stored.staleFormat) {
                    pushGameMessage(baselineSetMessage(stored, currentCursor));
                }
                return;
            case SKIP_MISMATCH:
                autoSyncState.disarm();
                pushGameMessage(messages != null ? messages.baselineMismatchMessage() : "");
                return;
            case SKIP_SHORT_READ:
                autoSyncState.disarm();
                pushGameMessage(messages != null
                    ? messages.shortReadMessage(currentCursor.size(), storedCursor.size()) : "");
                log.warn("GE history auto-sync skipped for account {}: read {} trades but the last sync saw {}",
                    accountKey, currentCursor.size(), storedCursor.size());
                return;
            default:
                break;
        }

        List<Trade> eligibleTrades = eligibleTrades(historyTrades, decision.eligibleTradeCount);
        AutoSync syncService = Bridge.get(AutoSync.class);
        AutoSync.SyncResult result = syncService != null
            ? syncService.sync(accountKey, eligibleTrades)
            : new AutoSync.SyncResult(eligibleTrades.size(), 0);
        autoSyncState.disarm();
        int addedTrades = result != null ? result.addedTrades : 0;
        int parsedTrades = result != null ? result.parsedTrades : 0;
        pushGameMessage(syncResultMessage(addedTrades));
        persistCursor(accountKey, currentCursor);
        if (decision.releasesWipeBarrier() && wipeStore != null) {
            wipeStore.setWipeBarrierArmed(accountKey, false);
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
