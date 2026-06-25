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
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LocalProfileTradesLoadServiceTest {
    @Test
    public void loadReturnsFalseWhenLoaderHasNoResult() {
        TestHooks hooks = new TestHooks();
        LocalProfileTradesLoadService service = new LocalProfileTradesLoadService(0L, hooks);

        boolean loaded = service.load(123L, true);

        assertFalse(loaded);
        assertEquals(0, hooks.setLocalTradeDeltasCalls);
    }

    @Test
    public void loadAppliesDeltasAndPersistsWhenRequested() {
        TestHooks hooks = new TestHooks();
        hooks.loadedResult = new ProfileTradesLoader.Result(
            listOf(delta(100L, 1), delta(200L, 2)),
            "Sips Potion",
            1234L
        );
        LocalProfileTradesLoadService service = new LocalProfileTradesLoadService(0L, hooks);

        boolean loaded = service.load(321L, true);

        assertTrue(loaded);
        assertEquals(1, hooks.putFileMsCalls);
        assertEquals(1, hooks.setLocalTradeDeltasCalls);
        assertEquals(1, hooks.rebuildStatsCacheCalls);
        assertEquals(1, hooks.putProfileDisplayNameCalls);
        assertEquals(2, hooks.cacheItemNameCalls);
        assertEquals(1, hooks.persistCalls);
        assertEquals(0, hooks.markDirtyCalls);
        assertEquals(1, hooks.scheduleRefreshCalls);
        assertEquals(1, hooks.triggerStatsRefreshCalls);
        assertNotNull(hooks.lastAppliedDeltas);
        assertEquals(2, hooks.lastAppliedDeltas.size());
    }

    @Test
    public void loadMarksDirtyWhenNotPersistingNonAccountwideProfile() {
        TestHooks hooks = new TestHooks();
        hooks.loadedResult = new ProfileTradesLoader.Result(listOf(delta(100L, 1)), "Name", 0L);
        LocalProfileTradesLoadService service = new LocalProfileTradesLoadService(0L, hooks);

        boolean loaded = service.load(999L, false);

        assertTrue(loaded);
        assertEquals(0, hooks.persistCalls);
        assertEquals(1, hooks.markDirtyCalls);
    }

    @Test
    public void loadDoesNotMarkDirtyForAccountwideWhenNotPersisting() {
        TestHooks hooks = new TestHooks();
        hooks.loadedResult = new ProfileTradesLoader.Result(listOf(delta(100L, 1)), "Accountwide", 0L);
        LocalProfileTradesLoadService service = new LocalProfileTradesLoadService(0L, hooks);

        boolean loaded = service.load(0L, false);

        assertTrue(loaded);
        assertEquals(0, hooks.persistCalls);
        assertEquals(0, hooks.markDirtyCalls);
    }

    private static List<LocalTradeDelta> listOf(LocalTradeDelta... deltas) {
        List<LocalTradeDelta> values = new ArrayList<>();
        if (deltas != null) {
            for (LocalTradeDelta delta : deltas) {
                values.add(delta);
            }
        }
        return values;
    }

    private static LocalTradeDelta delta(long tsClientMs, int itemId) {
        LocalTradeDelta delta = new LocalTradeDelta();
        delta.tsClientMs = tsClientMs;
        delta.slot = 0;
        delta.itemId = itemId;
        delta.isBuy = true;
        delta.deltaQty = 1;
        delta.deltaGp = 100L;
        delta.eventType = "OFFER_CHANGED";
        delta.price = 100;
        delta.baselineSynthetic = false;
        return delta;
    }

    private static final class TestHooks implements LocalProfileTradesLoadService.Hooks {
        private ProfileTradesLoader.Result loadedResult;
        private int putFileMsCalls;
        private int setLocalTradeDeltasCalls;
        private int rebuildStatsCacheCalls;
        private int putProfileDisplayNameCalls;
        private int cacheItemNameCalls;
        private int persistCalls;
        private int markDirtyCalls;
        private int scheduleRefreshCalls;
        private int triggerStatsRefreshCalls;
        private List<LocalTradeDelta> lastAppliedDeltas;

        @Override
        public ProfileTradesLoader.Result loadProfileTrades(long accountHash) {
            return loadedResult;
        }

        @Override
        public void putLoadedProfileFileMs(long accountHash, long fileMs) {
            putFileMsCalls++;
        }

        @Override
        public void setLocalTradeDeltas(long accountHash, List<LocalTradeDelta> deltas) {
            setLocalTradeDeltasCalls++;
            lastAppliedDeltas = deltas;
        }

        @Override
        public void rebuildStatsCache(long accountHash, List<LocalTradeDelta> deltas) {
            rebuildStatsCacheCalls++;
        }

        @Override
        public void putProfileDisplayName(long accountHash, String displayName) {
            putProfileDisplayNameCalls++;
        }

        @Override
        public void cacheItemName(int itemId) {
            cacheItemNameCalls++;
        }

        @Override
        public void persistLocalTrades(long accountHash) {
            persistCalls++;
        }

        @Override
        public void markAccountwideUploadDirty() {
            markDirtyCalls++;
        }

        @Override
        public void scheduleRefreshSoon() {
            scheduleRefreshCalls++;
        }

        @Override
        public void triggerStatsRefresh() {
            triggerStatsRefreshCalls++;
        }
    }
}
