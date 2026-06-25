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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LocalTradeDeltaRecorderTest {
    @Test
    public void recordPersistsProfileAndAccountwideDeltas() {
        TestHooks hooks = new TestHooks();
        hooks.resolvedAccountKey = 42L;
        LocalTradeDeltaRecorder recorder = new LocalTradeDeltaRecorder(0L, hooks);

        GeEvent event = event("OFFER_UPDATED", 2, 4151, true, 5, 5_000L, 1_000, 1_700_000_000_000L);

        boolean recorded = recorder.record(event, false);

        assertTrue(recorded);
        assertEquals(Arrays.asList(42L, 0L), hooks.ensureLoadedCalls);
        assertEquals(Arrays.asList(42L, 0L), hooks.ensureSessionStartKeys);
        assertEquals(2, hooks.persistCalls.size());
        assertEquals(Long.valueOf(42L), hooks.persistCalls.get(0));
        assertEquals(Long.valueOf(0L), hooks.persistCalls.get(1));
        assertEquals(1, hooks.appendCalls.size());
        AppendedCall appended = hooks.appendCalls.get(0);
        assertEquals(42L, appended.accountKey);
        assertEquals(0L, appended.accountwideKey);
        assertNotNull(appended.delta);
        assertEquals(2, appended.delta.slot);
        assertEquals(4151, appended.delta.itemId);
        assertEquals(5, appended.delta.deltaQty);
        assertEquals(5_000L, appended.delta.deltaGp);
        assertEquals("OFFER_UPDATED", appended.delta.eventType);
        assertEquals(2, hooks.applyCacheCalls.size());
        assertEquals(Long.valueOf(42L), hooks.applyCacheCalls.get(0));
        assertEquals(Long.valueOf(0L), hooks.applyCacheCalls.get(1));
        assertEquals(1, hooks.statsRefreshCalls);
        assertEquals(1, hooks.panelRefreshCalls);
    }

    @Test
    public void recordSkipsBaselineSynthetic() {
        TestHooks hooks = new TestHooks();
        hooks.resolvedAccountKey = 42L;
        LocalTradeDeltaRecorder recorder = new LocalTradeDeltaRecorder(0L, hooks);

        GeEvent event = event("OFFER_UPDATED", 1, 4151, true, 1, 1_000L, 1_000, 1_700_000_000_000L);

        boolean recorded = recorder.record(event, true);

        assertFalse(recorded);
        assertEquals(0, hooks.appendCalls.size());
        assertEquals(0, hooks.persistCalls.size());
        assertEquals(0, hooks.statsRefreshCalls);
        assertEquals(0, hooks.panelRefreshCalls);
    }

    @Test
    public void recordAllowsCompletionWithoutDelta() {
        TestHooks hooks = new TestHooks();
        hooks.resolvedAccountKey = 42L;
        LocalTradeDeltaRecorder recorder = new LocalTradeDeltaRecorder(0L, hooks);

        GeEvent event = event("OFFER_COMPLETED", 4, 561, false, 0, 0L, 200, 1_700_000_000_123L);

        boolean recorded = recorder.record(event, false);

        assertTrue(recorded);
        assertEquals(1, hooks.appendCalls.size());
        assertEquals("OFFER_COMPLETED", hooks.appendCalls.get(0).delta.eventType);
    }

    private static GeEvent event(String eventType, int slot, int itemId, boolean isBuy, int deltaQty,
                                 long deltaGp, int price, long tsClientMs) {
        GeEvent event = new GeEvent();
        event.event_type = eventType;
        event.slot = slot;
        event.item_id = itemId;
        event.is_buy = isBuy;
        event.delta_qty = deltaQty;
        event.delta_gp = deltaGp;
        event.price = price;
        event.ts_client_ms = tsClientMs;
        return event;
    }

    private static final class AppendedCall {
        private final long accountKey;
        private final long accountwideKey;
        private final LocalTradeDelta delta;

        private AppendedCall(long accountKey, long accountwideKey, LocalTradeDelta delta) {
            this.accountKey = accountKey;
            this.accountwideKey = accountwideKey;
            this.delta = delta;
        }
    }

    private static final class TestHooks implements LocalTradeDeltaRecorder.Hooks {
        private long resolvedAccountKey = -1L;
        private final List<Long> ensureLoadedCalls = new ArrayList<>();
        private final List<Long> ensureSessionStartKeys = new ArrayList<>();
        private final List<AppendedCall> appendCalls = new ArrayList<>();
        private final List<Long> applyCacheCalls = new ArrayList<>();
        private final List<Long> persistCalls = new ArrayList<>();
        private int statsRefreshCalls;
        private int panelRefreshCalls;

        @Override
        public long resolveLocalAccountKey() {
            return resolvedAccountKey;
        }

        @Override
        public void ensureProfileLoaded(long accountKey) {
            ensureLoadedCalls.add(accountKey);
        }

        @Override
        public void ensureLocalSessionStart(long accountKey, long tsClientMs) {
            ensureSessionStartKeys.add(accountKey);
        }

        @Override
        public void cacheItemName(int itemId) {
        }

        @Override
        public void appendTradeDeltaPair(long accountKey, long accountwideKey, LocalTradeDelta delta) {
            appendCalls.add(new AppendedCall(accountKey, accountwideKey, delta));
        }

        @Override
        public void applyDeltaToStatsCache(long accountKey, LocalTradeDelta delta) {
            applyCacheCalls.add(accountKey);
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
    }
}
