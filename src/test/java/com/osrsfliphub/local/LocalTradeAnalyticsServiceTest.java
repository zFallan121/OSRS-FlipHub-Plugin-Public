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
        TradeAnalytics service = new TradeAnalytics(
            LIMIT_WINDOW_MS,
            FUTURE_TOLERANCE_MS,
            BUCKET_MS
        );
        List<Delta> snapshot = Arrays.asList(
            delta(1_000L, 1, 560, true, 10, 1_000L, "OFFER_UPDATED", 100, false),
            delta(1_500L, 1, 560, true, 5, 600L, "OFFER_UPDATED", 120, false),
            delta(1_700L, 1, 560, false, 5, 650L, "OFFER_UPDATED", 130, false),
            delta(1_900L, 1, 560, false, 0, 0L, "OFFER_COMPLETED", 140, false),
            delta(2_000L, 1, 560, false, 2, 200L, "OFFER_UPDATED", 0, false)
        );

        Map<Integer, TradeInfo> infoMap = service.buildLocalTradeInfo(snapshot);

        TradeInfo info = infoMap.get(560);
        assertEquals(Long.valueOf(120L), info.lastBuyPrice);
        assertEquals(Long.valueOf(1_500L), info.lastBuyTs);
        assertEquals(Long.valueOf(140L), info.lastSellPrice);
        assertEquals(Long.valueOf(1_900L), info.lastSellTs);
    }

    /**
     * A 3rd age pickaxe bought for 2,394,000,000 and sold for 2,400,000,000. The trade file holds
     * a price up to max cash and no further, so both are saved at 2,147,483,647; the coins that
     * changed hands are saved whole, and the price is read back from those. The sale's coins are
     * what was left after its 5,000,000 tax.
     */
    @Test
    public void aTradePastMaxCashShowsItsRealPrice() {
        TradeInfo info = lastPrices(
            delta(1_000L, 1, 20011, true, 1, 2_394_000_000L, "OFFER_COMPLETED", Integer.MAX_VALUE, false),
            delta(2_000L, 1, 20011, false, 1, 2_395_000_000L, "OFFER_COMPLETED", Integer.MAX_VALUE, false));

        assertEquals(Long.valueOf(2_394_000_000L), info.lastBuyPrice);
        assertEquals(Long.valueOf(2_400_000_000L), info.lastSellPrice);
    }

    /** Two bought in one offer for 4,790,000,000: each cost half of it. */
    @Test
    public void aTradePastMaxCashOfSeveralItemsShowsThePriceOfOne() {
        TradeInfo info = lastPrices(
            delta(1_000L, 1, 20011, true, 2, 4_790_000_000L, "OFFER_COMPLETED", Integer.MAX_VALUE, false),
            delta(2_000L, 1, 20011, false, 2, 4_780_000_000L, "OFFER_COMPLETED", Integer.MAX_VALUE, false));

        assertEquals(Long.valueOf(2_395_000_000L), info.lastBuyPrice);
        assertEquals(Long.valueOf(2_395_000_000L), info.lastSellPrice);
    }

    /** Offers at exactly max cash were common before prices could pass it, and read back the same. */
    @Test
    public void aTradeAtExactlyMaxCashShowsMaxCash() {
        TradeInfo info = lastPrices(
            delta(1_000L, 1, 20011, true, 1, 2_147_483_647L, "OFFER_COMPLETED", Integer.MAX_VALUE, false),
            delta(2_000L, 1, 20011, false, 1, 2_142_483_647L, "OFFER_COMPLETED", Integer.MAX_VALUE, false));

        assertEquals(Long.valueOf(2_147_483_647L), info.lastBuyPrice);
        assertEquals(Long.valueOf(2_147_483_647L), info.lastSellPrice);
    }

    /** An item the game does not tax has no tax to put back (13190 is the bond). */
    @Test
    public void anUntaxedSalePastMaxCashShowsItsCoinsAsThePrice() {
        TradeInfo info = lastPrices(
            delta(2_000L, 1, 13190, false, 1, 2_400_000_000L, "OFFER_COMPLETED", Integer.MAX_VALUE, false));

        assertEquals(Long.valueOf(2_400_000_000L), info.lastSellPrice);
    }

    /**
     * An offer past max cash that was cancelled before anything filled is saved with its capped
     * price and no coins. There is nothing to read a price from, so the last real one stays.
     */
    @Test
    public void anOfferPastMaxCashThatFilledNothingLeavesTheLastPriceAlone() {
        TradeInfo info = lastPrices(
            delta(1_000L, 1, 20011, true, 1, 2_394_000_000L, "OFFER_COMPLETED", Integer.MAX_VALUE, false),
            delta(2_000L, 1, 20011, true, 0, 0L, "OFFER_COMPLETED", Integer.MAX_VALUE, false));

        assertEquals(Long.valueOf(2_394_000_000L), info.lastBuyPrice);
        assertEquals(Long.valueOf(1_000L), info.lastBuyTs);
    }

    /** Under max cash nothing changes: the price shown is the one the offer was listed at. */
    @Test
    public void aTradeUnderMaxCashKeepsItsListedPrice() {
        TradeInfo info = lastPrices(
            delta(1_000L, 1, 560, true, 10, 1_000L, "OFFER_COMPLETED", 105, false));

        assertEquals(Long.valueOf(105L), info.lastBuyPrice);
    }

    /**
     * What the card shows has to survive the trade file, which is where the price is cut down:
     * saved, read back, and still 2,394,000,000 and 2,400,000,000.
     */
    @Test
    public void aTradePastMaxCashStillShowsItsRealPriceAfterAReload() throws Exception {
        java.nio.file.Path baseDir = java.nio.file.Files.createTempDirectory("last-price-past-max-cash");
        try {
            ProfileStore store = new ProfileStore(new com.google.gson.Gson(), "fliphub", "fliphub-dev", baseDir);
            store.writeProfileData(123L, 0L, "Zezima", Arrays.asList(
                delta(1_000L, 1, 20011, true, 1, 2_394_000_000L, "OFFER_COMPLETED", Integer.MAX_VALUE, false),
                delta(2_000L, 1, 20011, false, 1, 2_395_000_000L, "OFFER_COMPLETED", Integer.MAX_VALUE, false)));

            List<Delta> reloaded = store.readProfileData(123L, 0L).deltas;
            TradeInfo info = lastPrices(reloaded.toArray(new Delta[0]));

            assertEquals(Long.valueOf(2_394_000_000L), info.lastBuyPrice);
            assertEquals(Long.valueOf(2_400_000_000L), info.lastSellPrice);
        } finally {
            try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(baseDir)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }

    private static TradeInfo lastPrices(Delta... deltas) {
        TradeAnalytics service = new TradeAnalytics(LIMIT_WINDOW_MS, FUTURE_TOLERANCE_MS, BUCKET_MS);
        return service.buildLocalTradeInfo(Arrays.asList(deltas)).get(deltas[0].itemId);
    }

    @Test
    public void buildLocalLimitInfoDedupesAndFiltersInvalidRows() {
        TradeAnalytics service = new TradeAnalytics(
            LIMIT_WINDOW_MS,
            FUTURE_TOLERANCE_MS,
            BUCKET_MS
        );
        long nowMs = 20_000_000L;
        long firstBuyTs = nowMs - 60_000L;
        List<Delta> snapshot = Arrays.asList(
            delta(firstBuyTs, 2, 4151, true, 8, 8_000L, "OFFER_UPDATED", 1_000, false),
            delta(firstBuyTs, 2, 4151, true, 8, 8_000L, "OFFER_UPDATED", 1_000, false),
            delta(nowMs + FUTURE_TOLERANCE_MS + 1L, 2, 4151, true, 4, 4_000L, "OFFER_UPDATED", 1_000, false),
            delta(firstBuyTs + 500L, 2, 4151, true, 3, 3_000L, "OFFER_UPDATED", 1_000, true)
        );

        Map<Integer, LimitInfo> infoMap = service.buildLocalLimitInfo(snapshot, nowMs);

        LimitInfo info = infoMap.get(4151);
        assertEquals(8L, info.buyQty);
        assertEquals(Long.valueOf(firstBuyTs), info.firstBuyTs);
    }

    /**
     * The window belongs to the first purchase, not to the clock. Buying at 10:00 and again at
     * 12:00 opens one window that closes at 14:00, so by 15:00 the whole limit is back even
     * though the second buy is only three hours old.
     */
    @Test
    public void buysInsideAWindowThatHasSinceExpiredNoLongerCount() {
        TradeAnalytics service = new TradeAnalytics(
            LIMIT_WINDOW_MS,
            FUTURE_TOLERANCE_MS,
            BUCKET_MS
        );
        long nowMs = 20_000_000L;
        long fiveHoursAgo = nowMs - (5L * 60L * 60L * 1000L);
        long threeHoursAgo = nowMs - (3L * 60L * 60L * 1000L);
        List<Delta> snapshot = Arrays.asList(
            delta(fiveHoursAgo, 2, 4151, true, 100, 100_000L, "OFFER_UPDATED", 1_000, false),
            delta(threeHoursAgo, 2, 4151, true, 50, 50_000L, "OFFER_UPDATED", 1_000, false)
        );

        assertTrue(service.buildLocalLimitInfo(snapshot, nowMs).isEmpty());
    }

    /** While the window is open every buy inside it counts, and the reset is the window's. */
    @Test
    public void buysInsideAnOpenWindowAreSummedAgainstItsFirstPurchase() {
        TradeAnalytics service = new TradeAnalytics(
            LIMIT_WINDOW_MS,
            FUTURE_TOLERANCE_MS,
            BUCKET_MS
        );
        long nowMs = 20_000_000L;
        long threeHoursAgo = nowMs - (3L * 60L * 60L * 1000L);
        long oneHourAgo = nowMs - (60L * 60L * 1000L);
        List<Delta> snapshot = Arrays.asList(
            delta(threeHoursAgo, 2, 4151, true, 100, 100_000L, "OFFER_UPDATED", 1_000, false),
            delta(oneHourAgo, 3, 4151, true, 50, 50_000L, "OFFER_UPDATED", 1_100, false)
        );

        LimitInfo info = service.buildLocalLimitInfo(snapshot, nowMs).get(4151);

        assertEquals(150L, info.buyQty);
        assertEquals(Long.valueOf(threeHoursAgo), info.firstBuyTs);
    }

    /**
     * Trades replayed from the in-game history carry a timestamp invented at import time, so
     * they cannot say when a limit was spent and must not consume one.
     */
    @Test
    public void tradesReplayedFromGameHistoryDoNotConsumeTheLimit() {
        TradeAnalytics service = new TradeAnalytics(
            LIMIT_WINDOW_MS,
            FUTURE_TOLERANCE_MS,
            BUCKET_MS
        );
        long nowMs = 20_000_000L;
        int syntheticSlot = Const.GE_HISTORY_SYNTHETIC_SLOT_START + 3;
        List<Delta> snapshot = Arrays.asList(
            delta(nowMs - 1_000L, syntheticSlot, 4151, true, 100, 100_000L, "OFFER_UPDATED", 1_000, false)
        );

        assertTrue(service.buildLocalLimitInfo(snapshot, nowMs).isEmpty());
    }

    @Test
    public void copySnapshotReturnsNewList() {
        TradeAnalytics service = new TradeAnalytics(
            LIMIT_WINDOW_MS,
            FUTURE_TOLERANCE_MS,
            BUCKET_MS
        );
        List<Delta> original = Arrays.asList(
            delta(1_000L, 1, 560, true, 1, 100L, "OFFER_UPDATED", 100, false)
        );

        List<Delta> copy = service.copySnapshot(original);

        assertEquals(1, copy.size());
        assertNotSame(original, copy);
    }

    private static Delta delta(long tsClientMs, int slot, int itemId, boolean isBuy, int deltaQty,
                                         long deltaGp, String eventType, int price, boolean baselineSynthetic) {
        return new Delta(
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
