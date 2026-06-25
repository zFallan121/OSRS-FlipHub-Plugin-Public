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
    @Test
    public void getOrBuildBuildsCacheFromSnapshot() {
        Map<Long, LocalStatsCache> cacheMap = new ConcurrentHashMap<>();
        Map<Long, List<LocalTradeDelta>> deltasByAccount = new HashMap<>();
        Object lock = new Object();
        long accountKey = 123L;
        List<LocalTradeDelta> deltas = new ArrayList<>();
        deltas.add(new LocalTradeDelta(1000L, 1, 4151, true, 1, 100L, "OFFER_UPDATED", 100, false));
        deltas.add(new LocalTradeDelta(2000L, 1, 4151, false, 1, 120L, "OFFER_COMPLETED", 120, false));
        deltasByAccount.put(accountKey, deltas);
        LocalStatsCacheService service = new LocalStatsCacheService(cacheMap, deltasByAccount, lock);

        LocalStatsCache cache = service.getOrBuild(accountKey);

        assertNotNull(cache);
        StatsSummary summary = cache.getSummary();
        assertEquals(Integer.valueOf(1), summary.fill_count);
        assertEquals(Long.valueOf(20L), summary.total_profit_gp);
    }

    @Test
    public void applyDeltaRebuildsWhenOutOfOrder() {
        Map<Long, LocalStatsCache> cacheMap = new ConcurrentHashMap<>();
        Map<Long, List<LocalTradeDelta>> deltasByAccount = new HashMap<>();
        Object lock = new Object();
        long accountKey = 456L;
        List<LocalTradeDelta> deltas = new ArrayList<>();
        LocalTradeDelta buy = new LocalTradeDelta(1000L, 1, 4151, true, 1, 100L, "OFFER_UPDATED", 100, false);
        LocalTradeDelta sell = new LocalTradeDelta(2000L, 1, 4151, false, 1, 120L, "OFFER_COMPLETED", 120, false);
        deltas.add(buy);
        deltasByAccount.put(accountKey, deltas);
        LocalStatsCacheService service = new LocalStatsCacheService(cacheMap, deltasByAccount, lock);

        service.getOrBuild(accountKey);
        deltas.add(sell);
        service.applyDelta(accountKey, sell);
        StatsSummary inOrder = cacheMap.get(accountKey).getSummary();
        assertEquals(Integer.valueOf(1), inOrder.fill_count);

        LocalTradeDelta outOfOrderBuy = new LocalTradeDelta(500L, 1, 4151, true, 1, 90L, "OFFER_UPDATED", 90, false);
        deltas.add(outOfOrderBuy);
        service.applyDelta(accountKey, outOfOrderBuy);
        StatsSummary rebuilt = cacheMap.get(accountKey).getSummary();
        assertEquals(Integer.valueOf(1), rebuilt.fill_count);
    }
}
