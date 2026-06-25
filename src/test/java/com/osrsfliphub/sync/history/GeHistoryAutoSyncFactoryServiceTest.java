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
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GeHistoryAutoSyncFactoryServiceTest {
    @Test
    public void createBuildsServiceThatUsesRuntimeHooksAndBackfillUploader() {
        GeHistoryAutoSyncFactoryService factory =
            new GeHistoryAutoSyncFactoryService(new BackfillUploader(null));
        TestRuntimeHooks hooks = new TestRuntimeHooks();
        GeHistoryAutoSyncService service = factory.create(0L, hooks);

        GeHistoryAutoSyncService.SyncResult result = service.sync(
            42L,
            Arrays.asList(new GeHistoryTrade(4151, true, 1, 100, 100L))
        );

        assertEquals(1, result.parsedTrades);
        assertEquals(1, result.addedTrades);
        assertEquals(2, hooks.uploadEvents.size());
        assertNotNull(hooks.uploadEvents.get(0).event_id);
        assertEquals(Integer.valueOf(321), hooks.uploadEvents.get(0).world);
        assertEquals(Arrays.asList(42L, 0L), hooks.persistCalls);
        assertEquals(1, hooks.flushRequests);
        assertTrue(hooks.statsRefreshCalls > 0);
        assertTrue(hooks.panelRefreshCalls > 0);
    }

    @Test
    public void createWithNullRuntimeHooksReturnsNoopService() {
        GeHistoryAutoSyncFactoryService factory =
            new GeHistoryAutoSyncFactoryService(new BackfillUploader(null));
        GeHistoryAutoSyncService service = factory.create(0L, null);

        GeHistoryAutoSyncService.SyncResult result = service.sync(
            42L,
            Arrays.asList(new GeHistoryTrade(4151, true, 1, 100, 100L))
        );

        assertEquals(1, result.parsedTrades);
        assertEquals(0, result.addedTrades);
    }

    private static final class TestRuntimeHooks implements GeHistoryAutoSyncFactoryService.RuntimeHooks {
        private final List<Long> persistCalls = new ArrayList<>();
        private final List<GeEvent> uploadEvents = new ArrayList<>();
        private int flushRequests;
        private int statsRefreshCalls;
        private int panelRefreshCalls;

        @Override
        public void ensureProfileLoaded(long accountKey) {
        }

        @Override
        public void ensureLocalSessionStart(long accountKey, long tsClientMs) {
        }

        @Override
        public List<LocalTradeDelta> snapshotLocalTradeDeltas(long accountKey) {
            return new ArrayList<>();
        }

        @Override
        public void cacheItemName(int itemId) {
        }

        @Override
        public void appendTradeDeltaPair(long accountKey, long accountwideKey, LocalTradeDelta delta) {
        }

        @Override
        public void applyDeltaToStatsCache(long accountKey, LocalTradeDelta delta) {
        }

        @Override
        public Integer resolveWorld() {
            return 321;
        }

        @Override
        public void enqueueUploadEvent(GeEvent event) {
            uploadEvents.add(event);
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
            return 1_700_000_000_000L;
        }
    }
}
