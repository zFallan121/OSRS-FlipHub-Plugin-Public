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
import net.runelite.api.Client;

@Singleton
final class AutoSync {
    @RequiredArgsConstructor
    static final class SyncResult {
        final int parsedTrades;
        final int addedTrades;
    }

    private static final int SYNTHETIC_SLOT_START = Const.GE_HISTORY_SYNTHETIC_SLOT_START;
    private static final long SYNTHETIC_EVENT_SPACING_MS = 4L;

    private final long accountwideKey = Const.ACCOUNTWIDE_KEY;
    private final Client client;

    @Inject
    AutoSync(Client client) {
        this.client = client;
    }

    private void ensureLocalSessionStart(long accountKey, long tsClientMs) {
        TradeSession service = Bridge.get(TradeSession.class);
        if (service != null) {
            service.ensureLocalSessionStart(accountKey, tsClientMs);
        }
    }

    private void applyDeltaToStatsCache(long accountKey, Delta delta) {
        LocalStatsCacheService cacheService = Bridge.get(LocalStatsCacheService.class);
        if (cacheService != null) {
            cacheService.applyDelta(accountKey, delta);
        }
    }

    private GeEvent buildUploadEvent(long profileKey, Delta delta) {
        BackfillUploader uploader = Bridge.get(BackfillUploader.class);
        if (uploader == null || delta == null) {
            return null;
        }
        return uploader.buildBackfillEvent(profileKey, delta, client != null ? client.getWorld() : null);
    }

    private void enqueueUploadEvent(GeEvent event) {
        if (event == null) {
            return;
        }
        UploadEventDispatch service = Bridge.get(UploadEventDispatch.class);
        if (service != null) {
            service.enqueueEvent(event);
        }
    }

    private void cacheItemName(int itemId) {
        ItemLookup lookup = Bridge.get(ItemLookup.class);
        if (lookup != null) {
            lookup.cacheItemName(itemId);
        }
    }

    private List<Delta> snapshotLocalTradeDeltas(long accountKey) {
        TradeSession service = Bridge.get(TradeSession.class);
        return service != null ? service.snapshotLocalTradeDeltas(accountKey) : null;
    }

    SyncResult sync(long accountKey, List<Trade> historyTrades) {
        if (accountKey <= 0 || historyTrades == null || historyTrades.isEmpty()) {
            return new SyncResult(historyTrades != null ? historyTrades.size() : 0, 0);
        }

        List<Trade> validTrades = new ArrayList<>();
        for (Trade trade : historyTrades) {
            if (trade != null && trade.isValid()) {
                validTrades.add(trade);
            }
        }
        if (validTrades.isEmpty()) {
            return new SyncResult(historyTrades.size(), 0);
        }

        Access.plugin().getLocalTradesRuntimeService().ensureProfileLoaded(accountKey);
        Access.plugin().getLocalTradesRuntimeService().ensureProfileLoaded(accountwideKey);

        List<Delta> existingDeltas = snapshotLocalTradeDeltas(accountKey);
        AutoSyncTradeMatcher.SelectionPlan selectionPlan =
            AutoSyncTradeMatcher.planMissingTrades(validTrades, existingDeltas);
        List<Trade> missingTrades = selectionPlan.missingTrades;
        if (missingTrades.isEmpty()) {
            return new SyncResult(validTrades.size(), 0);
        }

        long nowMs = System.currentTimeMillis();
        long[] plannedUpdateTs = planSyntheticUpdateTimestamps(
            validTrades,
            selectionPlan,
            existingDeltas,
            nowMs
        );
        long firstSyntheticTs = findFirstSyntheticTs(validTrades, selectionPlan, plannedUpdateTs, nowMs);
        ensureLocalSessionStart(accountKey, firstSyntheticTs);
        ensureLocalSessionStart(accountwideKey, firstSyntheticTs);

        int addedTrades = 0;
        for (int i = validTrades.size() - 1; i >= 0; i--) {
            Trade trade = selectionPlan.missingTradeAt(i);
            if (trade == null) {
                continue;
            }
            long updateTsMs = plannedUpdateTs[i] > 0L
                ? plannedUpdateTs[i]
                : Math.max(1L, nowMs - ((long) (validTrades.size() - i + 1) * SYNTHETIC_EVENT_SPACING_MS * 2L));
            long completionTsMs = updateTsMs + SYNTHETIC_EVENT_SPACING_MS;
            int slot = SYNTHETIC_SLOT_START + addedTrades;
            // The website is told about the trade the way the offer pipeline would have:
            // a fill, then a completion.
            Delta updateDelta = new Delta(
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
            Delta completionDelta = new Delta(
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
            // Locally the history row is a finished offer, and a finished offer is one
            // record: the fill and its completion, already folded together. The update's
            // timestamp keeps the row's place in the batch, which is the history's own order.
            Delta storedDelta = new Delta(
                updateTsMs,
                slot,
                trade.itemId,
                trade.isBuy,
                trade.quantity,
                trade.totalGp,
                "OFFER_COMPLETED",
                trade.price,
                false,
                updateTsMs,
                completionTsMs
            );
            cacheItemName(trade.itemId);
            if ((Access.plugin().getLocalTradesRuntimeService().appendTradeDeltaPair(accountKey, accountwideKey, storedDelta))
                != TradeOfferCollapser.Outcome.DROPPED) {
                applyDeltaToStatsCache(accountKey, storedDelta);
                if (accountwideKey != accountKey) {
                    applyDeltaToStatsCache(accountwideKey, storedDelta);
                }
            }

            // Ensure GE-history-synced trades also flow through website event ingestion/flip pairing.
            GeEvent uploadUpdate = buildUploadEvent(accountKey, updateDelta);
            if (uploadUpdate != null) {
                enqueueUploadEvent(uploadUpdate);
            }
            GeEvent uploadCompletion = buildUploadEvent(accountKey, completionDelta);
            if (uploadCompletion != null) {
                enqueueUploadEvent(uploadCompletion);
            }
            addedTrades++;
        }

        Access.plugin().getLocalTradesRuntimeService().persistLocalTrades(accountKey);
        Access.plugin().getLocalTradesRuntimeService().persistLocalTrades(accountwideKey);
        UploadBackfillDispatch dispatch = Bridge.get(UploadBackfillDispatch.class);
        if (dispatch != null) {
            dispatch.requestEventFlush();
        }
        PanelRefresh coordinator = Access.plugin().getPanelRefreshCoordinator();
        if (coordinator != null) {
            coordinator.triggerStatsRefresh(Access.plugin().scheduler);
            coordinator.triggerPanelRefresh(Access.plugin().scheduler);
        }
        return new SyncResult(validTrades.size(), addedTrades);
    }

    private long findFirstSyntheticTs(List<Trade> validTrades,
                                      AutoSyncTradeMatcher.SelectionPlan plan,
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
        List<Trade> validTrades,
        AutoSyncTradeMatcher.SelectionPlan plan,
        List<Delta> existingDeltas,
        long nowMs
    ) {
        int size = validTrades != null ? validTrades.size() : 0;
        long[] planned = new long[Math.max(0, size)];
        if (size == 0 || plan == null) {
            return planned;
        }

        Map<AutoSyncTradeMatcher.TradeSignature, Deque<Long>> existingTsBySignature =
            buildObservedTimestampQueues(existingDeltas);
        long anchorMinTs = Long.MAX_VALUE;
        for (int i = size - 1; i >= 0; i--) {
            if (plan.isMissing(i)) {
                continue;
            }
            AutoSyncTradeMatcher.TradeSignature signature =
                AutoSyncTradeMatcher.signatureForTrade(validTrades.get(i));
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
            AutoSyncTradeMatcher.TradeSignature signature =
                AutoSyncTradeMatcher.signatureForTrade(validTrades.get(i));
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

    private Map<AutoSyncTradeMatcher.TradeSignature, Deque<Long>> buildObservedTimestampQueues(
        List<Delta> deltas
    ) {
        Map<AutoSyncTradeMatcher.TradeSignature, List<Long>> grouped = new HashMap<>();
        if (deltas != null) {
            for (Delta delta : deltas) {
                AutoSyncTradeMatcher.TradeSignature signature =
                    AutoSyncTradeMatcher.signatureForDelta(delta);
                if (signature == null) {
                    continue;
                }
                grouped.computeIfAbsent(signature, key -> new ArrayList<>()).add(delta.tsClientMs);
            }
        }
        Map<AutoSyncTradeMatcher.TradeSignature, Deque<Long>> queues = new HashMap<>();
        for (Map.Entry<AutoSyncTradeMatcher.TradeSignature, List<Long>> entry : grouped.entrySet()) {
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
