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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
final class GeHistoryAutoSyncCoordinatorService {
    private static final Logger log = LoggerFactory.getLogger(GeHistoryAutoSyncCoordinatorService.class);

    static final class HistorySnapshot {
        final boolean visible;
        final Widget[] widgets;

        HistorySnapshot(boolean visible, Widget[] widgets) {
            this.visible = visible;
            this.widgets = widgets;
        }
    }

    private final GeHistoryAutoSyncStateService autoSyncState;
    private final Client client;

    @Inject
    GeHistoryAutoSyncCoordinatorService(Client client) {
        this.autoSyncState = PluginInjectorBridge.get(GeHistoryAutoSyncStateService.class);
        this.client = client;
    }

    private HistorySnapshot readHistorySnapshot() {
        Widget historyContainer = client != null
            ? client.getWidget(GeLifecyclePluginConstants.GE_HISTORY_GROUP_ID,
                GeLifecyclePluginConstants.GE_HISTORY_CONTAINER_CHILD_ID)
            : null;
        if (historyContainer == null || historyContainer.isHidden()) {
            return new HistorySnapshot(false, null);
        }
        return new HistorySnapshot(true, historyContainer.getDynamicChildren());
    }

    private GeHistoryCursorService cursorService() {
        return PluginInjectorBridge.get(GeHistoryCursorService.class);
    }

    private GeHistoryAutoSyncMessageService messageService() {
        return PluginInjectorBridge.get(GeHistoryAutoSyncMessageService.class);
    }

    private GeHistoryWipeStateStore wipeStore() {
        return PluginInjectorBridge.get(GeHistoryWipeStateStore.class);
    }

    private List<String> buildCursorSignatures(List<GeHistoryTrade> trades) {
        GeHistoryCursorService service = cursorService();
        return service != null ? service.buildCursorSignatures(trades) : new ArrayList<>();
    }

    private String syncResultMessage(int addedTrades) {
        GeHistoryAutoSyncMessageService service = messageService();
        return service != null ? service.syncResultMessage(addedTrades) : "";
    }

    private void persistCursor(long accountKey, List<String> cursor) {
        GeHistoryWipeStateStore store = wipeStore();
        if (store != null) {
            store.persistCursor(accountKey, cursor);
        }
    }

    private void pushGameMessage(String message) {
        GeLifecyclePlugin plugin = PluginAccess.plugin();
        plugin.runtimeUtilityServices.pushGameMessage(plugin.client, message);
    }

    void attemptAutoSync() {
        if (autoSyncState == null) {
            return;
        }
        if (!autoSyncState.isPending() || client == null || client.getGameState() != GameState.LOGGED_IN) {
            return;
        }
        LocalAccountSessionService session = PluginInjectorBridge.get(LocalAccountSessionService.class);
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
        GeHistoryWidgetReadService widgetRead = PluginInjectorBridge.get(GeHistoryWidgetReadService.class);
        boolean widgetsIncomplete = widgetRead == null || !widgetRead.hasCompleteWidgetGroups(historyWidgets);
        if (autoSyncState.shouldWaitForSettle(widgetsIncomplete, nowMs)) {
            return;
        }

        List<GeHistoryTrade> historyTrades =
            widgetRead != null ? widgetRead.parseTrades(historyWidgets) : new ArrayList<>();
        if (historyTrades == null) {
            historyTrades = new ArrayList<>();
        }
        List<GeHistoryTrade> eligibleTrades = historyTrades;
        List<String> currentCursor = buildCursorSignatures(historyTrades);
        GeHistoryWipeStateStore wipeStore = wipeStore();
        List<String> storedCursor = wipeStore != null ? wipeStore.loadCursor(accountKey) : new ArrayList<>();
        GeHistoryCursorService cursorService = cursorService();
        int overlap = cursorService != null ? cursorService.computeOverlap(currentCursor, storedCursor) : 0;
        boolean wipeBarrierArmed = wipeStore != null && wipeStore.isWipeBarrierArmed(accountKey);
        if (wipeBarrierArmed) {
            GeHistoryWipeBaselineDecisionService decisionService =
                PluginInjectorBridge.get(GeHistoryWipeBaselineDecisionService.class);
            GeHistoryWipeBaselineDecisionService.Decision decision = decisionService != null
                ? decisionService.decide(currentCursor, storedCursor, historyTrades.size(), overlap)
                : GeHistoryWipeBaselineDecisionService.Decision.proceed(historyTrades.size());

            if (decision.outcome == GeHistoryWipeBaselineDecisionService.Outcome.SET_BASELINE) {
                persistCursor(accountKey, currentCursor);
                autoSyncState.disarm();
                GeHistoryAutoSyncMessageService messages = messageService();
                pushGameMessage(messages != null
                    ? messages.baselineSetMessage(currentCursor != null ? currentCursor.size() : 0) : "");
                return;
            }

            if (decision.outcome == GeHistoryWipeBaselineDecisionService.Outcome.SKIP_MISMATCH) {
                autoSyncState.disarm();
                GeHistoryAutoSyncMessageService messages = messageService();
                pushGameMessage(messages != null ? messages.baselineMismatchMessage() : "");
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
                persistCursor(accountKey, currentCursor);
                autoSyncState.disarm();
                return;
            }
            if (overlap <= 0) {
                persistCursor(accountKey, currentCursor);
                autoSyncState.disarm();
                pushGameMessage(syncResultMessage(0));
                return;
            }
            int newCount = Math.max(0, historyTrades.size() - overlap);
            if (newCount <= 0) {
                eligibleTrades = new ArrayList<>();
            } else if (newCount < historyTrades.size()) {
                eligibleTrades = new ArrayList<>(historyTrades.subList(0, newCount));
            }
        }

        GeHistoryAutoSyncService syncService = PluginInjectorBridge.get(GeHistoryAutoSyncService.class);
        GeHistoryAutoSyncService.SyncResult result = syncService != null
            ? syncService.sync(accountKey, eligibleTrades)
            : new GeHistoryAutoSyncService.SyncResult(eligibleTrades != null ? eligibleTrades.size() : 0, 0);
        autoSyncState.disarm();
        int addedTrades = result != null ? result.addedTrades : 0;
        int parsedTrades = result != null ? result.parsedTrades : 0;
        pushGameMessage(syncResultMessage(addedTrades));
        if (currentCursor != null) {
            persistCursor(accountKey, currentCursor);
        }
        if (addedTrades > 0) {
            log.info("GE history auto-sync added {} missing trades ({} parsed) for account {}",
                addedTrades, parsedTrades, accountKey);
        }
    }
}
