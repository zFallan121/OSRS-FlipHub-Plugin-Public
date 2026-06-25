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

final class GeHistoryAutoSyncCoordinatorFactoryService {
    interface RuntimeHooks {
        boolean isClientLoggedIn();
        long resolveLocalAccountKey();
        GeHistoryAutoSyncCoordinatorService.HistorySnapshot readHistorySnapshot();
        long nowMs();
        void pushGameMessage(String message);
        void logAddedTrades(int addedTrades, int parsedTrades, long accountKey);
    }

    private final GeHistoryWidgetReadService widgetReadService;
    private final GeHistoryCursorService cursorService;
    private final GeHistoryWipeBaselineDecisionService wipeBaselineDecisionService;
    private final GeHistoryAutoSyncMessageService autoSyncMessageService;
    private final GeHistoryWipeStateStore wipeStateStore;
    private final GeHistoryAutoSyncService autoSyncService;

    GeHistoryAutoSyncCoordinatorFactoryService(GeHistoryWidgetReadService widgetReadService,
                                               GeHistoryCursorService cursorService,
                                               GeHistoryWipeBaselineDecisionService wipeBaselineDecisionService,
                                               GeHistoryAutoSyncMessageService autoSyncMessageService,
                                               GeHistoryWipeStateStore wipeStateStore,
                                               GeHistoryAutoSyncService autoSyncService) {
        this.widgetReadService = widgetReadService;
        this.cursorService = cursorService;
        this.wipeBaselineDecisionService = wipeBaselineDecisionService;
        this.autoSyncMessageService = autoSyncMessageService;
        this.wipeStateStore = wipeStateStore;
        this.autoSyncService = autoSyncService;
    }

    GeHistoryAutoSyncCoordinatorService create(GeHistoryAutoSyncStateService autoSyncState,
                                               RuntimeHooks runtimeHooks) {
        if (runtimeHooks == null) {
            return new GeHistoryAutoSyncCoordinatorService(autoSyncState, null);
        }
        return new GeHistoryAutoSyncCoordinatorService(
            autoSyncState,
            new GeHistoryAutoSyncCoordinatorService.Hooks() {
                @Override
                public boolean isClientLoggedIn() {
                    return runtimeHooks.isClientLoggedIn();
                }

                @Override
                public long resolveLocalAccountKey() {
                    return runtimeHooks.resolveLocalAccountKey();
                }

                @Override
                public GeHistoryAutoSyncCoordinatorService.HistorySnapshot readHistorySnapshot() {
                    return runtimeHooks.readHistorySnapshot();
                }

                @Override
                public long nowMs() {
                    return runtimeHooks.nowMs();
                }

                @Override
                public boolean hasCompleteWidgetGroups(Widget[] historyWidgets) {
                    return widgetReadService != null && widgetReadService.hasCompleteWidgetGroups(historyWidgets);
                }

                @Override
                public List<GeHistoryTrade> parseHistoryTrades(Widget[] historyWidgets) {
                    if (widgetReadService == null) {
                        return new ArrayList<>();
                    }
                    return widgetReadService.parseTrades(historyWidgets);
                }

                @Override
                public List<String> buildCursorSignatures(List<GeHistoryTrade> trades) {
                    if (cursorService == null) {
                        return new ArrayList<>();
                    }
                    return cursorService.buildCursorSignatures(trades);
                }

                @Override
                public int computeCursorOverlap(List<String> currentCursor, List<String> storedCursor) {
                    if (cursorService == null) {
                        return 0;
                    }
                    return cursorService.computeOverlap(currentCursor, storedCursor);
                }

                @Override
                public GeHistoryWipeBaselineDecisionService.Decision decideWipeBaseline(List<String> currentCursor,
                                                                                        List<String> storedCursor,
                                                                                        int parsedTradesCount,
                                                                                        int overlap) {
                    if (wipeBaselineDecisionService == null) {
                        return GeHistoryWipeBaselineDecisionService.Decision.proceed(parsedTradesCount);
                    }
                    return wipeBaselineDecisionService.decide(currentCursor, storedCursor, parsedTradesCount, overlap);
                }

                @Override
                public String baselineSetMessage(int cursorSize) {
                    if (autoSyncMessageService == null) {
                        return "";
                    }
                    return autoSyncMessageService.baselineSetMessage(cursorSize);
                }

                @Override
                public String baselineMismatchMessage() {
                    if (autoSyncMessageService == null) {
                        return "";
                    }
                    return autoSyncMessageService.baselineMismatchMessage();
                }

                @Override
                public String syncResultMessage(int addedTrades) {
                    if (autoSyncMessageService == null) {
                        return "";
                    }
                    return autoSyncMessageService.syncResultMessage(addedTrades);
                }

                @Override
                public boolean isWipeBarrierArmed(long accountKey) {
                    return wipeStateStore != null && wipeStateStore.isWipeBarrierArmed(accountKey);
                }

                @Override
                public List<String> loadCursor(long accountKey) {
                    if (wipeStateStore == null) {
                        return new ArrayList<>();
                    }
                    return wipeStateStore.loadCursor(accountKey);
                }

                @Override
                public void persistCursor(long accountKey, List<String> cursor) {
                    if (wipeStateStore != null) {
                        wipeStateStore.persistCursor(accountKey, cursor);
                    }
                }

                @Override
                public GeHistoryAutoSyncService.SyncResult sync(long accountKey, List<GeHistoryTrade> eligibleTrades) {
                    if (autoSyncService == null) {
                        return new GeHistoryAutoSyncService.SyncResult(
                            eligibleTrades != null ? eligibleTrades.size() : 0,
                            0
                        );
                    }
                    return autoSyncService.sync(accountKey, eligibleTrades);
                }

                @Override
                public void pushGameMessage(String message) {
                    runtimeHooks.pushGameMessage(message);
                }

                @Override
                public void logAddedTrades(int addedTrades, int parsedTrades, long accountKey) {
                    runtimeHooks.logAddedTrades(addedTrades, parsedTrades, accountKey);
                }
            }
        );
    }
}
