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

    @Inject
    GeHistoryAutoSyncCoordinatorService(Client client) {
        this(PluginAccess.plugin().getEventManageHistoryServices().getGeHistoryAutoSyncStateService(),
            productionHooks(client));
    }

    private static Hooks productionHooks(Client client) {
        return new Hooks() {
            @Override
            public boolean isClientLoggedIn() {
                return client != null && client.getGameState() == GameState.LOGGED_IN;
            }

            @Override
            public long resolveLocalAccountKey() {
                LocalAccountSessionService service =
                    PluginInjectorBridge.get(LocalAccountSessionService.class);
                return service != null ? service.resolveLocalAccountKey() : -1L;
            }

            @Override
            public HistorySnapshot readHistorySnapshot() {
                Widget historyContainer = client != null
                    ? client.getWidget(GeLifecyclePluginConstants.GE_HISTORY_GROUP_ID,
                        GeLifecyclePluginConstants.GE_HISTORY_CONTAINER_CHILD_ID)
                    : null;
                if (historyContainer == null || historyContainer.isHidden()) {
                    return new HistorySnapshot(false, null);
                }
                return new HistorySnapshot(true, historyContainer.getDynamicChildren());
            }

            @Override
            public long nowMs() {
                return System.currentTimeMillis();
            }

            @Override
            public boolean hasCompleteWidgetGroups(Widget[] historyWidgets) {
                GeHistoryWidgetReadService service = PluginInjectorBridge.get(GeHistoryWidgetReadService.class);
                return service != null && service.hasCompleteWidgetGroups(historyWidgets);
            }

            @Override
            public List<GeHistoryTrade> parseHistoryTrades(Widget[] historyWidgets) {
                GeHistoryWidgetReadService service = PluginInjectorBridge.get(GeHistoryWidgetReadService.class);
                return service != null ? service.parseTrades(historyWidgets) : new ArrayList<>();
            }

            @Override
            public List<String> buildCursorSignatures(List<GeHistoryTrade> trades) {
                GeHistoryCursorService service = PluginInjectorBridge.get(GeHistoryCursorService.class);
                return service != null ? service.buildCursorSignatures(trades) : new ArrayList<>();
            }

            @Override
            public int computeCursorOverlap(List<String> currentCursor, List<String> storedCursor) {
                GeHistoryCursorService service = PluginInjectorBridge.get(GeHistoryCursorService.class);
                return service != null ? service.computeOverlap(currentCursor, storedCursor) : 0;
            }

            @Override
            public GeHistoryWipeBaselineDecisionService.Decision decideWipeBaseline(List<String> currentCursor,
                                                                                    List<String> storedCursor,
                                                                                    int parsedTradesCount,
                                                                                    int overlap) {
                GeHistoryWipeBaselineDecisionService service = PluginInjectorBridge.get(GeHistoryWipeBaselineDecisionService.class);
                return service != null
                    ? service.decide(currentCursor, storedCursor, parsedTradesCount, overlap)
                    : GeHistoryWipeBaselineDecisionService.Decision.proceed(parsedTradesCount);
            }

            @Override
            public String baselineSetMessage(int cursorSize) {
                GeHistoryAutoSyncMessageService service = PluginInjectorBridge.get(GeHistoryAutoSyncMessageService.class);
                return service != null ? service.baselineSetMessage(cursorSize) : "";
            }

            @Override
            public String baselineMismatchMessage() {
                GeHistoryAutoSyncMessageService service = PluginInjectorBridge.get(GeHistoryAutoSyncMessageService.class);
                return service != null ? service.baselineMismatchMessage() : "";
            }

            @Override
            public String syncResultMessage(int addedTrades) {
                GeHistoryAutoSyncMessageService service = PluginInjectorBridge.get(GeHistoryAutoSyncMessageService.class);
                return service != null ? service.syncResultMessage(addedTrades) : "";
            }

            @Override
            public boolean isWipeBarrierArmed(long accountKey) {
                GeHistoryWipeStateStore store = PluginInjectorBridge.get(GeHistoryWipeStateStore.class);
                return store != null && store.isWipeBarrierArmed(accountKey);
            }

            @Override
            public List<String> loadCursor(long accountKey) {
                GeHistoryWipeStateStore store = PluginInjectorBridge.get(GeHistoryWipeStateStore.class);
                return store != null ? store.loadCursor(accountKey) : new ArrayList<>();
            }

            @Override
            public void persistCursor(long accountKey, List<String> cursor) {
                GeHistoryWipeStateStore store = PluginInjectorBridge.get(GeHistoryWipeStateStore.class);
                if (store != null) {
                    store.persistCursor(accountKey, cursor);
                }
            }

            @Override
            public GeHistoryAutoSyncService.SyncResult sync(long accountKey, List<GeHistoryTrade> eligibleTrades) {
                GeHistoryAutoSyncService service = PluginInjectorBridge.get(GeHistoryAutoSyncService.class);
                if (service == null) {
                    return new GeHistoryAutoSyncService.SyncResult(
                        eligibleTrades != null ? eligibleTrades.size() : 0, 0);
                }
                return service.sync(accountKey, eligibleTrades);
            }

            @Override
            public void pushGameMessage(String message) {
                GeLifecyclePlugin plugin = PluginAccess.plugin();
                plugin.runtimeUtilityServices.pushGameMessage(plugin.client, message);
            }

            @Override
            public void logAddedTrades(int addedTrades, int parsedTrades, long accountKey) {
                log.info("GE history auto-sync added {} missing trades ({} parsed) for account {}",
                    addedTrades, parsedTrades, accountKey);
            }
        };
    }

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
