package com.osrsfliphub;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The website's 2-minute rule, in the plugin's own sums (the owner's yes, 3 Oct 2026): a sale that
 * finds too little stock is completed by a purchase of the item made up to two minutes after it,
 * oldest waiting sale first, at that purchase's cost, and only the rest of the purchase is held.
 * The history sync dates what it imports as late as it can, which is how a purchase lands just
 * after the sale it paid for.
 */
public class SaleWaitsForItsPurchaseTest {
    private static final int BREW = 27629;

    /** 6 held at 100; sell 10 at 130; a minute later buy 10 at 110; later sell 6 at 130. */
    private static List<Delta> shortSaleThenItsPurchase(long purchaseMs) {
        return Arrays.asList(
            delta(1_000L, 1, true, 6, 600L, 100),
            delta(10_000L, 2, false, 10, 1_300L, 130),
            delta(purchaseMs, 1, true, 10, 1_100L, 110),
            delta(200_000L, 2, false, 6, 780L, 130));
    }

    @Test
    public void aPurchaseJustAfterASaleCompletesIt() {
        List<StatsFlipInstance> flips = history(shortSaleThenItsPurchase(70_000L), null);

        StatsFlipInstance sale = flips.get(1);
        assertEquals(10, sale.quantity);
        // 6 at 100, then 4 of the purchase a minute later at 110.
        assertEquals(1_040L, sale.buyCostGp);
        assertEquals(1_300L, sale.sellRevenueGp);
        assertEquals(260L, sale.profitGp);
        // Tax on all 10 sold, the 4 completed later too: 2 a brew at 130.
        assertEquals(20L, sale.taxGp);
        // Only the other 6 were held: the next sale takes them at 110.
        assertEquals(660L, flips.get(0).buyCostGp);
        assertEquals(120L, flips.get(0).profitGp);
    }

    /** 16 bought for 1,700 and 16 sold: every unit and every coin is counted once. */
    @Test
    public void nothingIsCountedTwiceOrLost() {
        long qty = 0L;
        long cost = 0L;
        for (StatsFlipInstance flip : history(shortSaleThenItsPurchase(70_000L), null)) {
            qty += flip.quantity;
            cost += flip.buyCostGp;
        }
        assertEquals(16L, qty);
        assertEquals(1_700L, cost);
    }

    /** Two minutes to the millisecond, as on the website; a moment later the purchase is stock. */
    @Test
    public void theWaitIsTwoMinutes() {
        assertEquals(10, history(shortSaleThenItsPurchase(130_000L), null).get(1).quantity);

        List<StatsFlipInstance> late = history(shortSaleThenItsPurchase(130_001L), null);
        assertEquals(6, late.get(1).quantity);
        assertEquals(180L, late.get(1).profitGp);
        // All 10 were held, and the next sale takes 6 of them.
        assertEquals(660L, late.get(0).buyCostGp);
    }

    /**
     * The history lists a sale 8 ms before a purchase of the same item, nothing held: the case the
     * website's rule exists for. A flip, where the plugin used to count nothing.
     */
    @Test
    public void aSaleThatFoundNothingIsAFlipOnceItsPurchaseComes() {
        int synced = Const.GE_HISTORY_SYNTHETIC_SLOT_START;
        List<Delta> deltas = Arrays.asList(
            new Delta(10_000L, synced, BREW, false, 5, 650L, "OFFER_COMPLETED", 130, false),
            new Delta(10_008L, synced + 1, BREW, true, 5, 500L, "OFFER_COMPLETED", 100, false));

        List<StatsFlipInstance> flips = history(deltas, null);
        assertEquals(1, flips.size());
        assertEquals(5, flips.get(0).quantity);
        assertEquals(150L, flips.get(0).profitGp);
        assertEquals(10_000L, flips.get(0).completionTsMs);
        assertEquals(Long.valueOf(150L), rebuilt(deltas).getSummary().total_profit_gp);
    }

    /** Sold 4 at 130, then 4 at 140, nothing held; then 6 bought for 601 within two minutes. */
    @Test
    public void theOldestWaitingSaleIsCompletedFirst() {
        List<Delta> deltas = Arrays.asList(
            delta(10_000L, 2, false, 4, 520L, 130),
            delta(20_000L, 3, false, 4, 560L, 140),
            delta(30_000L, 1, true, 6, 601L, 100));

        List<StatsFlipInstance> flips = history(deltas, null);
        assertEquals(2, flips.size());
        assertEquals(4, flips.get(1).quantity);
        assertEquals(400L, flips.get(1).buyCostGp);
        // 2 of its 4: half of what it sold for, and the coin division left over.
        assertEquals(2, flips.get(0).quantity);
        assertEquals(201L, flips.get(0).buyCostGp);
        assertEquals(280L, flips.get(0).sellRevenueGp);
        assertEquals(Long.valueOf(199L), rebuilt(deltas).getSummary().total_profit_gp);
    }

    /** A sale the slot moved on from, cancelled part-sold, is still completed: a flip of its own. */
    @Test
    public void aSaleTheSlotMovedOnFromIsCompletedToo() {
        List<Delta> deltas = Arrays.asList(
            new Delta(10_000L, 2, BREW, false, 4, 520L, "OFFER_UPDATED", 130, false, 9_000L, 0L),
            delta(20_000L, 2, true, 4, 400L, 100));

        List<StatsFlipInstance> flips = history(deltas, null);
        assertEquals(1, flips.size());
        assertFalse(flips.get(0).inProgress);
        assertEquals(120L, flips.get(0).profitGp);
    }

    /** A sale before the range is left out, and the purchase in the range that completed it is not stock either. */
    @Test
    public void aSaleBeforeTheRangeCompletedInItIsLeftOut() {
        List<Delta> deltas = Arrays.asList(
            delta(90_000L, 2, false, 5, 650L, 130),
            delta(150_000L, 1, true, 5, 500L, 100),
            delta(200_000L, 2, false, 5, 650L, 130));

        assertTrue(history(deltas, 100_000L).isEmpty());
        assertEquals(Long.valueOf(0L), rebuilt(deltas).buildSnapshotSince(100_000L).summary.total_profit_gp);
    }

    /**
     * Inside one 600 ms bucket a live purchase is replayed first, so an imported one 0.4 s earlier
     * comes after it. The website takes them in time order, and the imported one completes the sale.
     */
    @Test
    public void aPurchaseReplayedAfterALaterOneStillCompletesTheSale() {
        List<Delta> deltas = Arrays.asList(
            delta(10_000L, 2, false, 5, 650L, 130),
            delta(130_100L, 1, true, 5, 600L, 120),
            new Delta(129_700L, Const.GE_HISTORY_SYNTHETIC_SLOT_START, BREW, true, 5, 500L, "OFFER_COMPLETED", 100,
                false));

        assertEquals(150L, history(deltas, null).get(0).profitGp);
    }

    /** An offer still selling is completed the same way, and stays in progress. */
    @Test
    public void aSaleStillSellingIsCompletedByAPurchaseToo() {
        List<Delta> deltas = Arrays.asList(
            new Delta(10_000L, 2, BREW, false, 4, 520L, "OFFER_UPDATED", 130, false, 9_000L, 0L),
            delta(40_000L, 1, true, 4, 400L, 100));

        List<StatsFlipInstance> flips = history(deltas, null);
        assertEquals(1, flips.size());
        assertTrue(flips.get(0).inProgress);
        assertEquals(4, flips.get(0).quantity);
        assertEquals(120L, flips.get(0).profitGp);
    }

    /** Both ledgers pair the same way, live, rebuilt and over a range, or TOTAL PROFIT and the level-up check part. */
    @Test
    public void theStatsCacheCompletesASaleAsTheHistoryDoes() {
        List<Delta> deltas = shortSaleThenItsPurchase(70_000L);
        assertEquals(Long.valueOf(380L), rebuilt(deltas).getSummary().total_profit_gp);

        StatsCache live = new StatsCache();
        for (Delta delta : deltas) {
            assertTrue(live.applyDeltaInOrder(delta));
        }
        assertEquals(Long.valueOf(380L), live.getSummary().total_profit_gp);

        // From 100,000: only the later sale, and still out of the 6 held at 110.
        assertEquals(120L, history(deltas, 100_000L).get(0).profitGp);
        assertEquals(Long.valueOf(120L), rebuilt(deltas).buildSnapshotSince(100_000L).summary.total_profit_gp);
    }

    /** A purchase that completes a sale is not stock bought then: the next sale's hold starts at the next purchase. */
    @Test
    public void unitsThatCompletedASaleWereNeverHeld() {
        List<Delta> deltas = Arrays.asList(
            delta(1_000L, 2, false, 5, 650L, 130),
            delta(61_000L, 1, true, 5, 500L, 100),
            delta(3_601_000L, 1, true, 5, 500L, 100),
            delta(7_201_000L, 2, false, 5, 650L, 130));

        // The second sale held its stock an hour, not the two since the first purchase.
        assertEquals(Long.valueOf(3_600_000L), rebuilt(deltas).getSummary().active_ms);

        // 10 bought: 5 complete the sale, 5 are held an hour and sold. The whole hour, not half of it.
        List<Delta> part = Arrays.asList(
            delta(1_000L, 2, false, 5, 650L, 130),
            delta(61_000L, 1, true, 10, 1_000L, 100),
            delta(3_661_000L, 2, false, 5, 650L, 130));
        assertEquals(Long.valueOf(3_600_000L), rebuilt(part).getSummary().active_ms);
    }

    private static List<StatsFlipInstance> history(List<Delta> deltas, Long sinceMs) {
        List<StatsFlipInstance> flips = new LocalFlipHistoryService().buildHistory(deltas, sinceMs).get(BREW);
        return flips != null ? flips : new ArrayList<>();
    }

    private static StatsCache rebuilt(List<Delta> deltas) {
        StatsCache cache = new StatsCache();
        cache.rebuild(deltas);
        return cache;
    }

    private static Delta delta(long tsClientMs, int slot, boolean isBuy, int deltaQty, long deltaGp, int price) {
        return new Delta(tsClientMs, slot, BREW, isBuy, deltaQty, deltaGp, "OFFER_COMPLETED", price, false);
    }
}
