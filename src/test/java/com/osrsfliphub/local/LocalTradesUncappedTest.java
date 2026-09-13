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
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * The stored list used to be capped at 5,000 records, oldest dropped first. Profit is
 * derived by matching sales against earlier purchases, so a dropped purchase left every
 * later sale of that stock without its cost basis and moved the all-time totals as history
 * rolled. There is no cap now: the oldest purchase stays, however much follows it.
 */
public class LocalTradesUncappedTest {
    private static final int OLD_CAP = 5_000;
    private static final int ITEM = 4151;
    private static final int FILLER = 560;
    private static final long HOUR = 3_600_000L;

    /** A far larger history than the old cap: the first purchase, then a year of other trades. */
    private static List<Delta> aYearOfTrading() {
        List<Delta> records = new ArrayList<>();
        records.add(new Delta(0L, 0, ITEM, true, 10, 10_000_000L, "OFFER_COMPLETED", 1_000_000, false, 1L, 600L));
        for (int i = 1; i <= OLD_CAP + 500; i++) {
            long ts = i * HOUR;
            boolean isBuy = i % 2 == 1;
            long gp = isBuy ? 100L : 127L;
            records.add(new Delta(ts, 1 + (i % 7), FILLER, isBuy, 1, gp, "OFFER_COMPLETED", isBuy ? 100 : 130,
                false, ts - 10L, ts + 600L));
        }
        long saleTs = (OLD_CAP + 600) * HOUR;
        records.add(new Delta(saleTs, 0, ITEM, false, 10, 10_780_000L, "OFFER_COMPLETED", 1_100_000, false,
            saleTs - 10L, saleTs + 600L));
        return records;
    }

    @Test
    public void loadingKeepsTheOldestPurchaseAndPricesALaterSaleAgainstIt() {
        List<Delta> loaded = TradeDeltaUtils.dedupeLocalTrades(
            aYearOfTrading(),
            Const.LOCAL_EVENT_BUCKET_MS,
            Const.DUPLICATE_TRADE_WINDOW_MS);

        assertEquals(OLD_CAP + 502, loaded.size());
        assertEquals(0L, loaded.get(0).tsClientMs);
        assertEquals(ITEM, loaded.get(0).itemId);

        StatsCache cache = new StatsCache();
        cache.rebuild(loaded);
        Map<Integer, StatsItem> byItem = new java.util.HashMap<>();
        for (StatsItem item : cache.getItems()) {
            byItem.put(item.item_id, item);
        }
        // 10,780,000 back on the 10,000,000 the oldest purchase cost, not on nothing.
        assertEquals(Long.valueOf(10_000_000L), byItem.get(ITEM).total_cost_gp);
        assertEquals(Long.valueOf(780_000L), byItem.get(ITEM).total_profit_gp);

        List<StatsFlipInstance> flips = new LocalFlipHistoryService().buildHistory(loaded, null).get(ITEM);
        assertEquals(1, flips.size());
        assertEquals(10_000_000L, flips.get(0).buyCostGp);
        assertEquals(1_000_000L, flips.get(0).buyPriceGp);
    }

    @Test
    public void theLiveListIsNeverTrimmed() {
        PluginState state = new PluginState();
        LocalTradesRuntime runtime = new LocalTradesRuntime(
            Const.ACCOUNTWIDE_KEY,
            Const.LOCAL_EVENT_BUCKET_MS,
            Const.DUPLICATE_TRADE_WINDOW_MS,
            state.getLocalStatsLock(),
            state.getLocalTradeDeltasByAccount(),
            state.getLoadedProfiles(),
            state.getLocalTradesLoadState(),
            () -> null,
            () -> null,
            () -> false,
            () -> null,
            () -> null,
            () -> { },
            () -> null,
            () -> null,
            () -> null,
            () -> { },
            () -> { }
        );
        List<Delta> records = aYearOfTrading();
        Delta oldest = records.get(0);

        for (Delta record : records) {
            runtime.appendTradeDelta(42L, record);
        }

        List<Delta> stored = state.getLocalTradeDeltasByAccount().get(42L);
        assertEquals(OLD_CAP + 502, stored.size());
        assertSame(oldest, stored.get(0));
    }
}
