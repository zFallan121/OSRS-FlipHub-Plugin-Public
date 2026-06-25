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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class LocalTradeAnalyticsServiceTest {
    private static final long LIMIT_WINDOW_MS = 4L * 60L * 60L * 1000L;
    private static final long FUTURE_TOLERANCE_MS = 5L * 60L * 1000L;
    private static final long BUCKET_MS = 600L;

    @Test
    public void buildLocalTradeInfoUsesLatestBuyAndSell() {
        LocalTradeAnalyticsService service = new LocalTradeAnalyticsService(
            LIMIT_WINDOW_MS,
            FUTURE_TOLERANCE_MS,
            BUCKET_MS
        );
        List<LocalTradeDelta> snapshot = Arrays.asList(
            delta(1_000L, 1, 560, true, 10, 1_000L, "OFFER_UPDATED", 100, false),
            delta(1_500L, 1, 560, true, 5, 600L, "OFFER_UPDATED", 120, false),
            delta(1_700L, 1, 560, false, 5, 650L, "OFFER_UPDATED", 130, false),
            delta(1_900L, 1, 560, false, 0, 0L, "OFFER_COMPLETED", 140, false),
            delta(2_000L, 1, 560, false, 2, 200L, "OFFER_UPDATED", 0, false)
        );

        Map<Integer, LocalTradeInfo> infoMap = service.buildLocalTradeInfo(snapshot);

        LocalTradeInfo info = infoMap.get(560);
        assertEquals(Integer.valueOf(120), info.lastBuyPrice);
        assertEquals(Long.valueOf(1_500L), info.lastBuyTs);
        assertEquals(Integer.valueOf(140), info.lastSellPrice);
        assertEquals(Long.valueOf(1_900L), info.lastSellTs);
    }

    @Test
    public void buildLocalLimitInfoDedupesAndFiltersInvalidRows() {
        LocalTradeAnalyticsService service = new LocalTradeAnalyticsService(
            LIMIT_WINDOW_MS,
            FUTURE_TOLERANCE_MS,
            BUCKET_MS
        );
        long nowMs = 20_000_000L;
        long validTs = nowMs - 1_000L;
        List<LocalTradeDelta> snapshot = Arrays.asList(
            delta(validTs, 2, 4151, true, 8, 8_000L, "OFFER_UPDATED", 1_000, false),
            delta(validTs, 2, 4151, true, 8, 8_000L, "OFFER_UPDATED", 1_000, false),
            delta(nowMs - LIMIT_WINDOW_MS - 1L, 2, 4151, true, 4, 4_000L, "OFFER_UPDATED", 1_000, false),
            delta(nowMs + FUTURE_TOLERANCE_MS + 1L, 2, 4151, true, 4, 4_000L, "OFFER_UPDATED", 1_000, false),
            delta(validTs - 500L, 2, 4151, true, 3, 3_000L, "OFFER_UPDATED", 1_000, true)
        );

        Map<Integer, LocalLimitInfo> infoMap = service.buildLocalLimitInfo(snapshot, nowMs);

        LocalLimitInfo info = infoMap.get(4151);
        assertEquals(8L, info.buyQty);
        assertEquals(Long.valueOf(validTs), info.firstBuyTs);
    }

    @Test
    public void hasRecentLocalBuyRespectsItemAndWindow() {
        LocalTradeAnalyticsService service = new LocalTradeAnalyticsService(
            LIMIT_WINDOW_MS,
            FUTURE_TOLERANCE_MS,
            BUCKET_MS
        );
        long nowMs = 30_000_000L;
        List<LocalTradeDelta> snapshot = Arrays.asList(
            delta(nowMs - 2_000L, 1, 995, true, 2, 20_000L, "OFFER_UPDATED", 10_000, false),
            delta(nowMs - LIMIT_WINDOW_MS - 1L, 1, 995, true, 2, 20_000L, "OFFER_UPDATED", 10_000, false),
            delta(nowMs - 1_000L, 1, 995, false, 2, 20_000L, "OFFER_UPDATED", 10_000, false)
        );

        assertTrue(service.hasRecentLocalBuy(snapshot, 995, nowMs));
        assertFalse(service.hasRecentLocalBuy(snapshot, 561, nowMs));
    }

    @Test
    public void copySnapshotReturnsNewList() {
        LocalTradeAnalyticsService service = new LocalTradeAnalyticsService(
            LIMIT_WINDOW_MS,
            FUTURE_TOLERANCE_MS,
            BUCKET_MS
        );
        List<LocalTradeDelta> original = Arrays.asList(
            delta(1_000L, 1, 560, true, 1, 100L, "OFFER_UPDATED", 100, false)
        );

        List<LocalTradeDelta> copy = service.copySnapshot(original);

        assertEquals(1, copy.size());
        assertNotSame(original, copy);
    }

    private static LocalTradeDelta delta(long tsClientMs, int slot, int itemId, boolean isBuy, int deltaQty,
                                         long deltaGp, String eventType, int price, boolean baselineSynthetic) {
        return new LocalTradeDelta(
            tsClientMs,
            slot,
            itemId,
            isBuy,
            deltaQty,
            deltaGp,
            eventType,
            price,
            baselineSynthetic
        );
    }
}
