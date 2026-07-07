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
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;

@Singleton
final class GeHistoryAutoSyncService {
    interface Hooks {
        void ensureProfileLoaded(long accountKey);
        void ensureLocalSessionStart(long accountKey, long tsClientMs);
        List<LocalTradeDelta> snapshotLocalTradeDeltas(long accountKey);
        void cacheItemName(int itemId);
        void appendTradeDeltaPair(long accountKey, long accountwideKey, LocalTradeDelta delta);
        void applyDeltaToStatsCache(long accountKey, LocalTradeDelta delta);
        GeEvent buildUploadEvent(long profileKey, LocalTradeDelta delta);
        void enqueueUploadEvent(GeEvent event);
        void requestEventFlush();
        void persistLocalTrades(long accountKey);
        void triggerStatsRefresh();
        void triggerPanelRefresh();
        long nowMs();
    }

    static final class SyncResult {
        final int parsedTrades;
        final int addedTrades;

        SyncResult(int parsedTrades, int addedTrades) {
            this.parsedTrades = parsedTrades;
            this.addedTrades = addedTrades;
        }
    }

    private static final int SYNTHETIC_SLOT_START = 10_000;
    private static final long SYNTHETIC_EVENT_SPACING_MS = 4L;

    private final long accountwideKey;
    private final Hooks hooks;

    @Inject
    GeHistoryAutoSyncService(Client client) {
        this(GeLifecyclePluginConstants.ACCOUNTWIDE_KEY, productionHooks(client));
    }

    GeHistoryAutoSyncService(long accountwideKey, Hooks hooks) {
        this.accountwideKey = accountwideKey;
        this.hooks = hooks;
    }

    private static GeLifecyclePlugin plugin() {
        return PluginAccess.plugin();
    }

    private static Hooks productionHooks(Client client) {
        return new Hooks() {
            @Override
            public void ensureProfileLoaded(long accountKey) {
                plugin().getLocalTradesRuntimeService().ensureProfileLoaded(accountKey);
            }

            @Override
            public void ensureLocalSessionStart(long accountKey, long tsClientMs) {
                LocalTradeSessionFacadeService service =
                    PluginInjectorBridge.get(LocalTradeSessionFacadeService.class);
                if (service != null) {
                    service.ensureLocalSessionStart(accountKey, tsClientMs);
                }
            }

            @Override
            public List<LocalTradeDelta> snapshotLocalTradeDeltas(long accountKey) {
                LocalTradeSessionFacadeService service =
                    PluginInjectorBridge.get(LocalTradeSessionFacadeService.class);
                return service != null ? service.snapshotLocalTradeDeltas(accountKey) : null;
            }

            @Override
            public void cacheItemName(int itemId) {
                ItemLookupService lookup =
                    PluginInjectorBridge.get(ItemLookupService.class);
                if (lookup != null) {
                    lookup.cacheItemName(itemId);
                }
            }

            @Override
            public void appendTradeDeltaPair(long accountKey, long accountwideKey, LocalTradeDelta delta) {
                plugin().getLocalTradesRuntimeService().appendTradeDeltaPair(accountKey, accountwideKey, delta);
            }

            @Override
            public void applyDeltaToStatsCache(long accountKey, LocalTradeDelta delta) {
                LocalStatsCacheService cacheService = PluginInjectorBridge.get(LocalStatsCacheService.class);
                if (cacheService != null) {
                    cacheService.applyDelta(accountKey, delta);
                }
            }

            @Override
            public GeEvent buildUploadEvent(long profileKey, LocalTradeDelta delta) {
                BackfillUploader uploader = PluginInjectorBridge.get(BackfillUploader.class);
                if (uploader == null || delta == null) {
                    return null;
                }
                return uploader.buildBackfillEvent(profileKey, delta, client != null ? client.getWorld() : null);
            }

            @Override
            public void enqueueUploadEvent(GeEvent event) {
                if (event == null) {
                    return;
                }
                UploadEventDispatchFacadeService service =
                    PluginInjectorBridge.get(UploadEventDispatchFacadeService.class);
                if (service != null) {
                    service.enqueueEvent(event);
                }
            }

            @Override
            public void requestEventFlush() {
                UploadBackfillDispatchService service =
                    PluginInjectorBridge.get(UploadBackfillDispatchService.class);
                if (service != null) {
                    service.requestEventFlush();
                }
            }

            @Override
            public void persistLocalTrades(long accountKey) {
                plugin().getLocalTradesRuntimeService().persistLocalTrades(accountKey);
            }

            @Override
            public void triggerStatsRefresh() {
                PanelRefreshCoordinator coordinator = plugin().getPanelRefreshCoordinator();
                if (coordinator != null) {
                    coordinator.triggerStatsRefresh(plugin().scheduler);
                }
            }

            @Override
            public void triggerPanelRefresh() {
                PanelRefreshCoordinator coordinator = plugin().getPanelRefreshCoordinator();
                if (coordinator != null) {
                    coordinator.triggerPanelRefresh(plugin().scheduler);
                }
            }

            @Override
            public long nowMs() {
                return System.currentTimeMillis();
            }
        };
    }

    SyncResult sync(long accountKey, List<GeHistoryTrade> historyTrades) {
        if (hooks == null || accountKey <= 0 || historyTrades == null || historyTrades.isEmpty()) {
            return new SyncResult(historyTrades != null ? historyTrades.size() : 0, 0);
        }

        List<GeHistoryTrade> validTrades = new ArrayList<>();
        for (GeHistoryTrade trade : historyTrades) {
            if (trade != null && trade.isValid()) {
                validTrades.add(trade);
            }
        }
        if (validTrades.isEmpty()) {
            return new SyncResult(historyTrades.size(), 0);
        }

        hooks.ensureProfileLoaded(accountKey);
        hooks.ensureProfileLoaded(accountwideKey);

        List<LocalTradeDelta> existingDeltas = hooks.snapshotLocalTradeDeltas(accountKey);
        Map<GeHistoryAutoSyncTradeMatcher.TradeSignature, Integer> existingCounts =
            GeHistoryAutoSyncTradeMatcher.buildObservedTradeCounts(existingDeltas);
        Map<GeHistoryAutoSyncTradeMatcher.BaseSignature, Integer> observedQtyByBase =
            GeHistoryAutoSyncTradeMatcher.buildObservedQuantityByBase(existingDeltas);
        GeHistoryAutoSyncTradeMatcher.SelectionPlan selectionPlan = GeHistoryAutoSyncTradeMatcher.planMissingTrades(
            validTrades,
            existingCounts,
            observedQtyByBase
        );
        List<GeHistoryTrade> missingTrades = selectionPlan.missingTrades;
        if (missingTrades.isEmpty()) {
            return new SyncResult(validTrades.size(), 0);
        }

        long nowMs = hooks.nowMs();
        long[] plannedUpdateTs = planSyntheticUpdateTimestamps(
            validTrades,
            selectionPlan,
            existingDeltas,
            nowMs
        );
        long firstSyntheticTs = findFirstSyntheticTs(validTrades, selectionPlan, plannedUpdateTs, nowMs);
        hooks.ensureLocalSessionStart(accountKey, firstSyntheticTs);
        hooks.ensureLocalSessionStart(accountwideKey, firstSyntheticTs);

        int addedTrades = 0;
        for (int i = validTrades.size() - 1; i >= 0; i--) {
            GeHistoryTrade trade = selectionPlan.missingTradeAt(i);
            if (trade == null) {
                continue;
            }
            long updateTsMs = plannedUpdateTs[i] > 0L
                ? plannedUpdateTs[i]
                : Math.max(1L, nowMs - ((long) (validTrades.size() - i + 1) * SYNTHETIC_EVENT_SPACING_MS * 2L));
            long completionTsMs = updateTsMs + SYNTHETIC_EVENT_SPACING_MS;
            int slot = SYNTHETIC_SLOT_START + addedTrades;
            LocalTradeDelta updateDelta = new LocalTradeDelta(
                updateTsMs,
                slot,
                trade.itemId,
                trade.isBuy,
                trade.quantity,
                trade.totalGp,
                "OFFER_UPDATED",
                trade.price,
                false
            );
            LocalTradeDelta completionDelta = new LocalTradeDelta(
                completionTsMs,
                slot,
                trade.itemId,
                trade.isBuy,
                0,
                0L,
                "OFFER_COMPLETED",
                trade.price,
                false
            );
            hooks.cacheItemName(trade.itemId);
            hooks.appendTradeDeltaPair(accountKey, accountwideKey, updateDelta);
            hooks.applyDeltaToStatsCache(accountKey, updateDelta);
            if (accountwideKey != accountKey) {
                hooks.applyDeltaToStatsCache(accountwideKey, updateDelta);
            }
            hooks.appendTradeDeltaPair(accountKey, accountwideKey, completionDelta);
            hooks.applyDeltaToStatsCache(accountKey, completionDelta);
            if (accountwideKey != accountKey) {
                hooks.applyDeltaToStatsCache(accountwideKey, completionDelta);
            }

            // Ensure GE-history-synced trades also flow through website event ingestion/flip pairing.
            GeEvent uploadUpdate = hooks.buildUploadEvent(accountKey, updateDelta);
            if (uploadUpdate != null) {
                hooks.enqueueUploadEvent(uploadUpdate);
            }
            GeEvent uploadCompletion = hooks.buildUploadEvent(accountKey, completionDelta);
            if (uploadCompletion != null) {
                hooks.enqueueUploadEvent(uploadCompletion);
            }
            addedTrades++;
        }

        hooks.persistLocalTrades(accountKey);
        hooks.persistLocalTrades(accountwideKey);
        hooks.requestEventFlush();
        hooks.triggerStatsRefresh();
        hooks.triggerPanelRefresh();
        return new SyncResult(validTrades.size(), addedTrades);
    }

    private long findFirstSyntheticTs(List<GeHistoryTrade> validTrades,
                                      GeHistoryAutoSyncTradeMatcher.SelectionPlan plan,
                                      long[] plannedUpdateTs,
                                      long fallbackNowMs) {
        if (validTrades == null || plan == null || plannedUpdateTs == null) {
            return Math.max(1L, fallbackNowMs);
        }
        for (int i = validTrades.size() - 1; i >= 0; i--) {
            if (!plan.isMissing(i)) {
                continue;
            }
            long ts = i < plannedUpdateTs.length ? plannedUpdateTs[i] : 0L;
            if (ts > 0L) {
                return ts;
            }
        }
        return Math.max(1L, fallbackNowMs);
    }

    private long[] planSyntheticUpdateTimestamps(
        List<GeHistoryTrade> validTrades,
        GeHistoryAutoSyncTradeMatcher.SelectionPlan plan,
        List<LocalTradeDelta> existingDeltas,
        long nowMs
    ) {
        int size = validTrades != null ? validTrades.size() : 0;
        long[] planned = new long[Math.max(0, size)];
        if (size == 0 || plan == null) {
            return planned;
        }

        Map<GeHistoryAutoSyncTradeMatcher.TradeSignature, Deque<Long>> existingTsBySignature =
            buildObservedTimestampQueues(existingDeltas);
        long anchorMinTs = Long.MAX_VALUE;
        for (int i = size - 1; i >= 0; i--) {
            if (plan.isMissing(i)) {
                continue;
            }
            GeHistoryAutoSyncTradeMatcher.TradeSignature signature =
                GeHistoryAutoSyncTradeMatcher.signatureForTrade(validTrades.get(i));
            if (signature == null) {
                continue;
            }
            Deque<Long> queue = existingTsBySignature.get(signature);
            if (queue == null || queue.isEmpty()) {
                continue;
            }
            long candidate = queue.peekFirst();
            if (candidate > 0L && candidate < anchorMinTs) {
                anchorMinTs = candidate;
            }
        }

        long stepMs = SYNTHETIC_EVENT_SPACING_MS * 2L;
        long cursor;
        if (anchorMinTs != Long.MAX_VALUE) {
            cursor = Math.max(1L, anchorMinTs - ((long) (size + 4) * stepMs));
        } else {
            int missingCount = plan.missingTrades != null ? plan.missingTrades.size() : 0;
            cursor = Math.max(1L, nowMs - ((long) Math.max(1, missingCount + 2) * stepMs));
        }

        for (int i = size - 1; i >= 0; i--) {
            GeHistoryAutoSyncTradeMatcher.TradeSignature signature =
                GeHistoryAutoSyncTradeMatcher.signatureForTrade(validTrades.get(i));
            if (signature == null) {
                continue;
            }
            if (plan.isMissing(i)) {
                cursor = Math.max(1L, cursor + stepMs);
                planned[i] = cursor;
                continue;
            }
            Deque<Long> queue = existingTsBySignature.get(signature);
            if (queue == null || queue.isEmpty()) {
                continue;
            }
            long ts = queue.pollFirst();
            if (ts > 0L) {
                cursor = Math.max(cursor, ts);
            }
        }
        return planned;
    }

    private Map<GeHistoryAutoSyncTradeMatcher.TradeSignature, Deque<Long>> buildObservedTimestampQueues(
        List<LocalTradeDelta> deltas
    ) {
        Map<GeHistoryAutoSyncTradeMatcher.TradeSignature, List<Long>> grouped = new HashMap<>();
        if (deltas != null) {
            for (LocalTradeDelta delta : deltas) {
                GeHistoryAutoSyncTradeMatcher.TradeSignature signature =
                    GeHistoryAutoSyncTradeMatcher.signatureForDelta(delta);
                if (signature == null) {
                    continue;
                }
                grouped.computeIfAbsent(signature, key -> new ArrayList<>()).add(delta.tsClientMs);
            }
        }
        Map<GeHistoryAutoSyncTradeMatcher.TradeSignature, Deque<Long>> queues = new HashMap<>();
        for (Map.Entry<GeHistoryAutoSyncTradeMatcher.TradeSignature, List<Long>> entry : grouped.entrySet()) {
            List<Long> values = entry.getValue();
            if (values == null || values.isEmpty()) {
                continue;
            }
            Collections.sort(values);
            queues.put(entry.getKey(), new ArrayDeque<>(values));
        }
        return queues;
    }
}
