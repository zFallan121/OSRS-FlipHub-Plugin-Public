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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class LocalStatsCacheServiceTest {
    /**
     * After an invalidation or a wipe there is no aggregate to add to. Callers store the
     * delta before applying it, so the account's whole history is on hand and the aggregate
     * has to be rebuilt from it - seeding a fresh cache with the one new delta would report
     * the latest fill as though it were everything the account had ever done.
     */
    @Test
    public void applyDeltaWithNoCachePresentRebuildsFromTheStoredHistory() {
        Map<Long, StatsCache> cacheMap = new ConcurrentHashMap<>();
        Map<Long, List<Delta>> deltasByAccount = new HashMap<>();
        Object lock = new Object();
        long accountKey = 789L;
        List<Delta> deltas = new ArrayList<>();
        Delta buy = new Delta(1000L, 1, 4151, true, 1, 100L, "OFFER_UPDATED", 100, false);
        Delta sell = new Delta(2000L, 1, 4151, false, 1, 120L, "OFFER_COMPLETED", 120, false);
        deltas.add(buy);
        deltas.add(sell);
        deltasByAccount.put(accountKey, deltas);
        LocalStatsCacheService service = new LocalStatsCacheService(cacheMap, deltasByAccount, lock);

        service.applyDelta(accountKey, sell);

        StatsSummary summary = cacheMap.get(accountKey).getSummary();
        assertEquals(Integer.valueOf(1), summary.fill_count);
        assertEquals(Long.valueOf(20L), summary.total_profit_gp);
    }

    @Test
    public void getOrBuildBuildsCacheFromSnapshot() {
        Map<Long, StatsCache> cacheMap = new ConcurrentHashMap<>();
        Map<Long, List<Delta>> deltasByAccount = new HashMap<>();
        Object lock = new Object();
        long accountKey = 123L;
        List<Delta> deltas = new ArrayList<>();
        deltas.add(new Delta(1000L, 1, 4151, true, 1, 100L, "OFFER_UPDATED", 100, false));
        deltas.add(new Delta(2000L, 1, 4151, false, 1, 120L, "OFFER_COMPLETED", 120, false));
        deltasByAccount.put(accountKey, deltas);
        LocalStatsCacheService service = new LocalStatsCacheService(cacheMap, deltasByAccount, lock);

        StatsCache cache = service.getOrBuild(accountKey);

        assertNotNull(cache);
        StatsSummary summary = cache.getSummary();
        assertEquals(Integer.valueOf(1), summary.fill_count);
        assertEquals(Long.valueOf(20L), summary.total_profit_gp);
    }

    @Test
    public void applyDeltaRebuildsWhenOutOfOrder() {
        Map<Long, StatsCache> cacheMap = new ConcurrentHashMap<>();
        Map<Long, List<Delta>> deltasByAccount = new HashMap<>();
        Object lock = new Object();
        long accountKey = 456L;
        List<Delta> deltas = new ArrayList<>();
        Delta buy = new Delta(1000L, 1, 4151, true, 1, 100L, "OFFER_UPDATED", 100, false);
        Delta sell = new Delta(2000L, 1, 4151, false, 1, 120L, "OFFER_COMPLETED", 120, false);
        deltas.add(buy);
        deltasByAccount.put(accountKey, deltas);
        LocalStatsCacheService service = new LocalStatsCacheService(cacheMap, deltasByAccount, lock);

        service.getOrBuild(accountKey);
        deltas.add(sell);
        service.applyDelta(accountKey, sell);
        StatsSummary inOrder = cacheMap.get(accountKey).getSummary();
        assertEquals(Integer.valueOf(1), inOrder.fill_count);

        Delta outOfOrderBuy = new Delta(500L, 1, 4151, true, 1, 90L, "OFFER_UPDATED", 90, false);
        deltas.add(outOfOrderBuy);
        service.applyDelta(accountKey, outOfOrderBuy);
        StatsSummary rebuilt = cacheMap.get(accountKey).getSummary();
        assertEquals(Integer.valueOf(1), rebuilt.fill_count);
    }
}
