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
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GeHistoryAutoSyncServiceTest {
    @Test
    public void syncAddsOnlyTradesMissingFromCompletedHistory() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 1_700_000_100_000L;
        hooks.localDeltas = Arrays.asList(
            delta(1_000L, 2, 4151, true, 10, 10_000L, "OFFER_UPDATED", 1_000),
            delta(1_100L, 2, 4151, true, 0, 0L, "OFFER_COMPLETED", 1_000)
        );
        GeHistoryAutoSyncService service = new GeHistoryAutoSyncService(0L, hooks);
        List<GeHistoryTrade> trades = Arrays.asList(
            new GeHistoryTrade(4151, true, 10, 1_000, 10_000L),
            new GeHistoryTrade(561, false, 5, 120, 590L)
        );

        GeHistoryAutoSyncService.SyncResult result = service.sync(42L, trades);

        assertEquals(2, result.parsedTrades);
        assertEquals(1, result.addedTrades);
        assertEquals(2, hooks.appendCalls.size());
        assertEquals(2, hooks.uploadEvents.size());
        assertEquals(561, hooks.appendCalls.get(0).delta.itemId);
        assertEquals("OFFER_UPDATED", hooks.appendCalls.get(0).delta.eventType);
        assertEquals(590L, hooks.appendCalls.get(0).delta.deltaGp);
        assertEquals(120, hooks.appendCalls.get(0).delta.price);
        assertEquals("OFFER_COMPLETED", hooks.appendCalls.get(1).delta.eventType);
        assertEquals(0, hooks.appendCalls.get(1).delta.deltaQty);
        assertEquals(Arrays.asList(42L, 0L, 42L, 0L), hooks.applyCalls);
        assertEquals(Arrays.asList(42L, 0L), hooks.persistCalls);
        assertEquals(1, hooks.flushRequests);
    }

    @Test
    public void syncTreatsMatchingSplitUpdatesAsExistingTrades() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 1_700_000_200_000L;
        hooks.localDeltas = Arrays.asList(
            delta(1_000L, 3, 1513, true, 3, 300L, "OFFER_UPDATED", 100),
            delta(1_050L, 3, 1513, true, 2, 200L, "OFFER_UPDATED", 100),
            delta(1_100L, 3, 1513, true, 0, 0L, "OFFER_COMPLETED", 100)
        );
        GeHistoryAutoSyncService service = new GeHistoryAutoSyncService(0L, hooks);
        List<GeHistoryTrade> trades = Arrays.asList(
            new GeHistoryTrade(1513, true, 3, 100, 300L),
            new GeHistoryTrade(1513, true, 2, 100, 200L)
        );

        GeHistoryAutoSyncService.SyncResult result = service.sync(42L, trades);

        assertEquals(2, result.parsedTrades);
        assertEquals(0, result.addedTrades);
        assertEquals(0, hooks.appendCalls.size());
        assertEquals(0, hooks.uploadEvents.size());
        assertEquals(0, hooks.persistCalls.size());
        assertEquals(0, hooks.flushRequests);
    }

    @Test
    public void syncAddsRepeatedMatchingSignaturesByCountDifference() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 1_700_000_300_000L;
        hooks.localDeltas = Arrays.asList(
            delta(1_000L, 1, 560, true, 100, 10_000L, "OFFER_UPDATED", 100),
            delta(1_010L, 1, 560, true, 0, 0L, "OFFER_COMPLETED", 100)
        );
        GeHistoryAutoSyncService service = new GeHistoryAutoSyncService(0L, hooks);
        List<GeHistoryTrade> trades = Arrays.asList(
            new GeHistoryTrade(560, true, 100, 100, 10_000L),
            new GeHistoryTrade(560, true, 100, 100, 10_000L)
        );

        GeHistoryAutoSyncService.SyncResult result = service.sync(42L, trades);

        assertEquals(2, result.parsedTrades);
        assertEquals(1, result.addedTrades);
        assertEquals(2, hooks.appendCalls.size());
        assertEquals(2, hooks.uploadEvents.size());
        assertEquals(1, hooks.flushRequests);
    }

    @Test
    public void syncedBuyAndSellBehaveLikeNormalCompletedFlip() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 1_700_000_400_000L;
        GeHistoryAutoSyncService service = new GeHistoryAutoSyncService(0L, hooks);
        List<GeHistoryTrade> trades = Arrays.asList(
            new GeHistoryTrade(4151, true, 1, 100, 100L),
            new GeHistoryTrade(4151, false, 1, 120, 118L)
        );

        GeHistoryAutoSyncService.SyncResult result = service.sync(42L, trades);

        assertEquals(2, result.addedTrades);
        List<LocalTradeDelta> produced = new ArrayList<>();
        for (AppendCall call : hooks.appendCalls) {
            produced.add(call.delta);
            assertTrue(call.delta.slot >= 10_000);
        }
        assertEquals(4, hooks.uploadEvents.size());
        assertEquals(1, hooks.flushRequests);
        LocalStatsCache statsCache = new LocalStatsCache();
        statsCache.rebuild(produced);
        StatsSummary summary = statsCache.getSummary();
        assertEquals(Integer.valueOf(1), summary.fill_count);
        assertEquals(Long.valueOf(18L), summary.total_profit_gp);
    }

    @Test
    public void syncMatchesSellTradeByQuantityAndPriceWhenNetTotalsDrift() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 1_700_000_500_000L;
        hooks.localDeltas = Arrays.asList(
            // Existing local net can drift by 1gp from history due rounding/legacy writes.
            delta(1_000L, 4, 534, false, 1, 553L, "OFFER_UPDATED", 563),
            delta(1_100L, 4, 534, false, 0, 0L, "OFFER_COMPLETED", 563)
        );
        GeHistoryAutoSyncService service = new GeHistoryAutoSyncService(0L, hooks);
        List<GeHistoryTrade> trades = Arrays.asList(
            new GeHistoryTrade(534, false, 1, 563, 552L)
        );

        GeHistoryAutoSyncService.SyncResult result = service.sync(42L, trades);

        assertEquals(1, result.parsedTrades);
        assertEquals(0, result.addedTrades);
        assertEquals(0, hooks.appendCalls.size());
        assertEquals(0, hooks.uploadEvents.size());
        assertEquals(0, hooks.persistCalls.size());
        assertEquals(0, hooks.flushRequests);
    }

    @Test
    public void syncDoesNotReaddWhenLocalDeltaIsAggregatedButHistoryIsSplit() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 1_700_000_600_000L;
        hooks.localDeltas = Arrays.asList(
            delta(1_000L, 5, 532, false, 2, 624L, "OFFER_UPDATED", 313),
            delta(1_100L, 5, 532, false, 0, 0L, "OFFER_COMPLETED", 313)
        );
        GeHistoryAutoSyncService service = new GeHistoryAutoSyncService(0L, hooks);
        List<GeHistoryTrade> trades = Arrays.asList(
            new GeHistoryTrade(532, false, 1, 313, 312L),
            new GeHistoryTrade(532, false, 1, 313, 312L)
        );

        GeHistoryAutoSyncService.SyncResult result = service.sync(42L, trades);

        assertEquals(2, result.parsedTrades);
        assertEquals(0, result.addedTrades);
        assertEquals(0, hooks.appendCalls.size());
        assertEquals(0, hooks.uploadEvents.size());
        assertEquals(0, hooks.persistCalls.size());
        assertEquals(0, hooks.flushRequests);
    }

    @Test
    public void syncBackfillsMissingBuyBeforeExistingSellToRecoverPartialFlipProfit() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 1_700_000_700_000L;
        hooks.localDeltas = new ArrayList<>(Arrays.asList(
            // Existing local sell captured first (e.g. sold on this client after buying elsewhere).
            delta(10_000L, 7, 30000, false, 1, 12_350_000L, "OFFER_UPDATED", 13_000_000),
            delta(10_010L, 7, 30000, false, 0, 0L, "OFFER_COMPLETED", 13_000_000)
        ));
        GeHistoryAutoSyncService service = new GeHistoryAutoSyncService(0L, hooks);
        List<GeHistoryTrade> trades = Arrays.asList(
            // Newest first in GE history UI: sell row, then the older buy row.
            new GeHistoryTrade(30000, false, 1, 13_000_000, 12_350_000L),
            new GeHistoryTrade(30000, true, 1, 12_000_000, 12_000_000L)
        );

        GeHistoryAutoSyncService.SyncResult result = service.sync(42L, trades);

        assertEquals(2, result.parsedTrades);
        assertEquals(1, result.addedTrades);
        assertEquals(2, hooks.appendCalls.size());
        LocalTradeDelta buyUpdate = hooks.appendCalls.get(0).delta;
        assertEquals(true, buyUpdate.isBuy);
        assertTrue("history-synced buy must be placed before existing sell timestamp", buyUpdate.tsClientMs < 10_000L);

        // Rebuild from full persisted stream and verify the prior sell now matches against the backfilled buy.
        List<LocalTradeDelta> replay = new ArrayList<>(hooks.localDeltas);
        for (AppendCall call : hooks.appendCalls) {
            replay.add(call.delta);
        }
        LocalStatsCache statsCache = new LocalStatsCache();
        statsCache.rebuild(replay);
        StatsSummary summary = statsCache.getSummary();
        assertEquals(Integer.valueOf(1), summary.fill_count);
        assertEquals(Long.valueOf(350_000L), summary.total_profit_gp);
    }

    @Test
    public void syncBackfillsOnlyResidualQuantityWhenExistingLocalTradeAlreadyCoversPartOfHistoryRow() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 1_700_000_800_000L;
        hooks.localDeltas = new ArrayList<>(Arrays.asList(
            delta(10_000L, 7, 30_001, true, 25, 2_500L, "OFFER_UPDATED", 100),
            delta(10_010L, 7, 30_001, true, 0, 0L, "OFFER_COMPLETED", 100),
            delta(11_000L, 7, 30_001, false, 11, 1_298L, "OFFER_UPDATED", 120),
            delta(11_010L, 7, 30_001, false, 0, 0L, "OFFER_COMPLETED", 120)
        ));
        GeHistoryAutoSyncService service = new GeHistoryAutoSyncService(0L, hooks);
        List<GeHistoryTrade> trades = Arrays.asList(
            // Newest first in GE history UI.
            new GeHistoryTrade(30_001, false, 20, 120, 2_360L),
            new GeHistoryTrade(30_001, true, 25, 100, 2_500L)
        );

        GeHistoryAutoSyncService.SyncResult result = service.sync(42L, trades);

        assertEquals(2, result.parsedTrades);
        assertEquals(1, result.addedTrades);
        assertEquals(2, hooks.appendCalls.size());
        LocalTradeDelta syncedUpdate = hooks.appendCalls.get(0).delta;
        assertEquals(false, syncedUpdate.isBuy);
        assertEquals(9, syncedUpdate.deltaQty);
        assertEquals(1_062L, syncedUpdate.deltaGp);
        assertEquals(120, syncedUpdate.price);

        List<LocalTradeDelta> replay = new ArrayList<>(hooks.localDeltas);
        for (AppendCall call : hooks.appendCalls) {
            replay.add(call.delta);
        }
        Map<Integer, List<StatsFlipInstance>> history = new LocalFlipHistoryService().buildHistory(replay, null);
        List<StatsFlipInstance> flips = history.get(30_001);
        assertEquals(2, flips.size());

        int totalQty = 0;
        boolean sawEleven = false;
        boolean sawNine = false;
        for (StatsFlipInstance flip : flips) {
            totalQty += flip.quantity;
            if (flip.quantity == 11) {
                sawEleven = true;
            }
            if (flip.quantity == 9) {
                sawNine = true;
            }
        }
        assertEquals(20, totalQty);
        assertTrue(sawEleven);
        assertTrue(sawNine);
    }

    private static LocalTradeDelta delta(long tsClientMs,
                                         int slot,
                                         int itemId,
                                         boolean isBuy,
                                         int deltaQty,
                                         long deltaGp,
                                         String eventType,
                                         int price) {
        return new LocalTradeDelta(tsClientMs, slot, itemId, isBuy, deltaQty, deltaGp, eventType, price, false);
    }

    private static final class AppendCall {
        private final long accountKey;
        private final long accountwideKey;
        private final LocalTradeDelta delta;

        private AppendCall(long accountKey, long accountwideKey, LocalTradeDelta delta) {
            this.accountKey = accountKey;
            this.accountwideKey = accountwideKey;
            this.delta = delta;
        }
    }

    private static final class TestHooks implements GeHistoryAutoSyncService.Hooks {
        private long nowMs = 1_700_000_000_000L;
        private List<LocalTradeDelta> localDeltas = new ArrayList<>();
        private final List<Long> ensureLoadedCalls = new ArrayList<>();
        private final List<Long> ensureSessionCalls = new ArrayList<>();
        private final List<Integer> cachedItems = new ArrayList<>();
        private final List<AppendCall> appendCalls = new ArrayList<>();
        private final List<Long> applyCalls = new ArrayList<>();
        private final List<Long> persistCalls = new ArrayList<>();
        private final List<GeEvent> uploadEvents = new ArrayList<>();
        private int flushRequests;
        private int statsRefreshCalls;
        private int panelRefreshCalls;

        @Override
        public void ensureProfileLoaded(long accountKey) {
            ensureLoadedCalls.add(accountKey);
        }

        @Override
        public void ensureLocalSessionStart(long accountKey, long tsClientMs) {
            ensureSessionCalls.add(accountKey);
        }

        @Override
        public List<LocalTradeDelta> snapshotLocalTradeDeltas(long accountKey) {
            return localDeltas != null ? new ArrayList<>(localDeltas) : new ArrayList<>();
        }

        @Override
        public void cacheItemName(int itemId) {
            cachedItems.add(itemId);
        }

        @Override
        public void appendTradeDeltaPair(long accountKey, long accountwideKey, LocalTradeDelta delta) {
            appendCalls.add(new AppendCall(accountKey, accountwideKey, delta));
        }

        @Override
        public void applyDeltaToStatsCache(long accountKey, LocalTradeDelta delta) {
            applyCalls.add(accountKey);
        }

        @Override
        public GeEvent buildUploadEvent(long profileKey, LocalTradeDelta delta) {
            if (delta == null) {
                return null;
            }
            GeEvent event = new GeEvent();
            event.event_type = delta.eventType;
            event.item_id = delta.itemId;
            event.is_buy = delta.isBuy;
            event.delta_qty = delta.deltaQty;
            event.delta_gp = delta.deltaGp;
            return event;
        }

        @Override
        public void enqueueUploadEvent(GeEvent event) {
            if (event != null) {
                uploadEvents.add(event);
            }
        }

        @Override
        public void requestEventFlush() {
            flushRequests++;
        }

        @Override
        public void persistLocalTrades(long accountKey) {
            persistCalls.add(accountKey);
        }

        @Override
        public void triggerStatsRefresh() {
            statsRefreshCalls++;
        }

        @Override
        public void triggerPanelRefresh() {
            panelRefreshCalls++;
        }

        @Override
        public long nowMs() {
            return nowMs;
        }
    }
}
