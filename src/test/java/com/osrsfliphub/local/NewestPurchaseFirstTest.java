package com.osrsfliphub;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Which purchase a sale takes, and what one flip is: the website's rules, so a player sees the
 * same figures linked or not (the owner's choices of 2 and 3 Oct 2026). A sale takes the newest
 * purchase made before it; a flip is one sell offer, however many pieces it sold in.
 */
public class NewestPurchaseFirstTest {
    private static final int BREW = 27629;

    /**
     * The owner's Forgotten brews on XP V, as stored. About 1,658 at 3,797 were left sitting in a
     * 4.1k listing; he bought 408 at 3,891, 4 at 2,333 and 130 at 3,050, then sold 2 at 3,590
     * (7,038 after tax). Those 2 came out of the 130: +938. The average of all he held booked them
     * at 3,767 (-497), the oldest stock at 3,797 (-556).
     */
    private static List<Delta> xpVBrews() {
        return Arrays.asList(
            new Delta(1789994557697L, 4, BREW, true, 2000, 7594000L, "OFFER_COMPLETED", 3797, false,
                1789993604725L, 1790066861482L),
            new Delta(1789994557705L, 10000, BREW, false, 254, 1019048L, "OFFER_COMPLETED", 4093, false,
                1789994557705L, 1789994557709L),
            new Delta(1790066937077L, 0, BREW, false, 20, 80240L, "OFFER_UPDATED", 4093, false,
                1790066901097L, 0L),
            new Delta(1790067289934L, 0, BREW, false, 68, 272816L, "OFFER_COMPLETED", 4093, false,
                1790067190331L, 1790067474768L),
            new Delta(1790080783604L, 0, BREW, true, 408, 1587528L, "OFFER_COMPLETED", 3891, false,
                1790077999251L, 1790117056474L),
            new Delta(1790729342304L, 7, BREW, true, 4, 9332L, "OFFER_COMPLETED", 2333, false,
                1790729342292L, 1790736666242L),
            new Delta(1790771993559L, 2, BREW, true, 130, 396500L, "OFFER_COMPLETED", 3050, false,
                1790771790119L, 1790855602390L),
            new Delta(1790855618574L, 2, BREW, false, 2, 7038L, "OFFER_COMPLETED", 3590, false,
                1790855608373L, 1790855629991L),
            new Delta(1790989001278L, 2, BREW, true, 9, 27747L, "OFFER_COMPLETED", 3083, false,
                1790988432978L, 1790989111698L));
    }

    @Test
    public void aSaleTakesTheNewestPurchaseMadeBeforeIt() {
        List<StatsFlipInstance> flips = new LocalFlipHistoryService().buildHistory(xpVBrews(), null).get(BREW);

        StatsFlipInstance last = flips.get(0);
        assertEquals(2, last.quantity);
        assertEquals(3_050L, last.buyPriceGp);
        assertEquals(938L, last.profitGp);
        // Not the 9 bought two days after it, though they are newer still.
        assertEquals(1790855629991L, last.completionTsMs);
    }

    /**
     * Slot 0 sold 20 and was cancelled, then a new offer at the same price sold 68. They are two
     * offers, placed six minutes apart: two flips, not one of 88.
     */
    @Test
    public void aSaleCancelledPartSoldAndListedAgainIsTwoFlips() {
        List<StatsFlipInstance> flips = new LocalFlipHistoryService().buildHistory(xpVBrews(), null).get(BREW);

        assertEquals(4, flips.size());
        assertEquals(68, flips.get(1).quantity);
        assertEquals(14_620L, flips.get(1).profitGp);
        assertEquals(20, flips.get(2).quantity);
        assertEquals(4_300L, flips.get(2).profitGp);
        assertFalse(flips.get(2).inProgress);
        assertEquals(254, flips.get(3).quantity);
        assertEquals(54_610L, flips.get(3).profitGp);
    }

    /** Both ledgers pair a sale the same way, or TOTAL PROFIT and the level-up check part. */
    @Test
    public void theStatsCachePairsAsTheHistoryDoes() {
        long history = 0L;
        for (StatsFlipInstance flip : new LocalFlipHistoryService().buildHistory(xpVBrews(), null).get(BREW)) {
            history += flip.profitGp;
        }
        assertEquals(74_468L, history);

        StatsCache rebuilt = new StatsCache();
        rebuilt.rebuild(xpVBrews());
        assertEquals(Long.valueOf(history), rebuilt.getSummary().total_profit_gp);

        List<Delta> inOrder = new ArrayList<>(xpVBrews());
        inOrder.sort(TradeDeltaUtils.replayOrder());
        StatsCache live = new StatsCache();
        for (Delta delta : inOrder) {
            assertTrue(live.applyDeltaInOrder(delta));
        }
        assertEquals(Long.valueOf(history), live.getSummary().total_profit_gp);
    }

    @Test
    public void aSaleTakesAllOfTheNewestPurchaseThenPartOfTheOneBefore() {
        List<Delta> deltas = Arrays.asList(
            delta(1_000L, 1, true, 10, 1_000L, "OFFER_COMPLETED", 100),
            delta(2_000L, 1, true, 5, 1_000L, "OFFER_COMPLETED", 200),
            delta(3_000L, 2, false, 8, 2_400L, "OFFER_COMPLETED", 300),
            delta(4_000L, 2, false, 7, 2_100L, "OFFER_COMPLETED", 300));

        List<StatsFlipInstance> flips = new LocalFlipHistoryService().buildHistory(deltas, null).get(BREW);

        // 5 at 200, then 3 at 100.
        assertEquals(1_300L, flips.get(1).buyCostGp);
        assertEquals(700L, flips.get(0).buyCostGp);
    }

    /** 3 bought for 100 coins: sold one at a time, they cost 33, 33 and 34, never 99 between them. */
    @Test
    public void aPurchaseSoldInPiecesCostsExactlyWhatItCost() {
        Lots lots = new Lots();
        lots.buy(3, 100L);

        assertEquals(33L, lots.take(1));
        assertEquals(33L, lots.take(1));
        assertEquals(34L, lots.take(1));
        assertEquals(0L, lots.qty);
        assertEquals(0L, lots.take(1));
    }

    /** A range shows the sales in it; the stock they took may have been bought before it. */
    @Test
    public void aSaleInARangeTakesTheNewestPurchaseEvenFromBeforeTheRange() {
        List<Delta> deltas = Arrays.asList(
            delta(1_000L, 1, true, 5, 500L, "OFFER_COMPLETED", 100),
            delta(2_000L, 1, true, 5, 1_000L, "OFFER_COMPLETED", 200),
            delta(9_000L, 2, false, 5, 1_500L, "OFFER_COMPLETED", 300));

        StatsFlipInstance flip = new LocalFlipHistoryService().buildHistory(deltas, 5_000L).get(BREW).get(0);
        assertEquals(1_000L, flip.buyCostGp);

        StatsCache cache = new StatsCache();
        cache.rebuild(deltas);
        assertEquals(Long.valueOf(500L), cache.buildSnapshotSince(5_000L).summary.total_profit_gp);
    }

    /**
     * One fill of an offer that was then cancelled, and a new offer at the same price on the same
     * slot: the slot's record of when each was placed tells them apart.
     */
    @Test
    public void aNewOfferAtTheSamePriceOnTheSameSlotIsANewFlip() {
        List<Delta> deltas = Arrays.asList(
            delta(1_000L, 1, true, 10, 1_000L, "OFFER_COMPLETED", 100),
            new Delta(2_000L, 3, BREW, false, 4, 520L, "OFFER_UPDATED", 130, false, 1_900L, 0L),
            new Delta(3_000L, 3, BREW, false, 6, 780L, "OFFER_COMPLETED", 130, false, 2_900L, 3_500L));

        List<StatsFlipInstance> flips = new LocalFlipHistoryService().buildHistory(deltas, null).get(BREW);

        assertEquals(2, flips.size());
        assertEquals(6, flips.get(0).quantity);
        assertEquals(4, flips.get(1).quantity);
        assertFalse(flips.get(1).inProgress);
    }

    /**
     * Fills the slot moved on from without a completion are stored as one record that ended. It
     * is a whole offer, and the next sale on that slot, however alike, is another.
     */
    @Test
    public void anOfferStoredAsEndedIsAFlipOfItsOwn() {
        List<Delta> deltas = Arrays.asList(
            delta(1_000L, 1, true, 10, 1_000L, "OFFER_COMPLETED", 100),
            new Delta(2_000L, 3, BREW, false, 4, 520L, "OFFER_UPDATED", 130, false, 0L, 2_500L),
            new Delta(3_000L, 3, BREW, false, 6, 780L, "OFFER_COMPLETED", 130, false, 0L, 3_500L));

        List<StatsFlipInstance> flips = new LocalFlipHistoryService().buildHistory(deltas, null).get(BREW);

        assertEquals(2, flips.size());
        assertEquals(2_500L, flips.get(1).completionTsMs);
    }

    /** A purchase on the slot ends the sale that was waiting there, even of the same item. */
    @Test
    public void aPurchaseOnTheSlotEndsTheSaleWaitingThere() {
        List<Delta> deltas = Arrays.asList(
            delta(1_000L, 1, true, 10, 1_000L, "OFFER_COMPLETED", 100),
            delta(2_000L, 3, false, 4, 520L, "OFFER_UPDATED", 130),
            delta(3_000L, 3, true, 5, 500L, "OFFER_COMPLETED", 100),
            delta(4_000L, 2, false, 2, 260L, "OFFER_COMPLETED", 130));

        Map<Integer, List<StatsFlipInstance>> history = new LocalFlipHistoryService().buildHistory(deltas, null);

        assertEquals(2, history.get(BREW).size());
        assertFalse(history.get(BREW).get(1).inProgress);
        assertEquals(4, history.get(BREW).get(1).quantity);
    }

    /** An offer still selling is one flip in progress, whatever number of fills it has had. */
    @Test
    public void theFillsOfAnOfferStillSellingAreOneFlipInProgress() {
        List<Delta> deltas = Arrays.asList(
            delta(1_000L, 1, true, 10, 1_000L, "OFFER_COMPLETED", 100),
            new Delta(2_000L, 3, BREW, false, 4, 520L, "OFFER_UPDATED", 130, false, 1_900L, 0L),
            new Delta(3_000L, 3, BREW, false, 3, 390L, "OFFER_UPDATED", 130, false, 1_900L, 0L));

        List<StatsFlipInstance> flips = new LocalFlipHistoryService().buildHistory(deltas, null).get(BREW);

        assertEquals(1, flips.size());
        assertEquals(7, flips.get(0).quantity);
        assertTrue(flips.get(0).inProgress);
    }

    /**
     * A sale left up while more is bought at another price is paired piece by piece while it sells,
     * and as one sale when it ends, because the plugin keeps one record per finished offer. The
     * website pairs each piece as it sold. Both ledgers agree at each stage, and once everything is
     * sold the totals agree with the website's.
     */
    @Test
    public void aSaleLeftUpWhileMoreIsBoughtIsPairedWholeWhenItEnds() {
        Delta dearer = delta(1_000L, 0, true, 10, 1_000L, "OFFER_COMPLETED", 100);
        Delta firstPiece = new Delta(2_000L, 2, BREW, false, 5, 650L, "OFFER_UPDATED", 130, false, 1_500L, 0L);
        Delta cheaper = new Delta(3_000L, 0, BREW, true, 10, 500L, "OFFER_COMPLETED", 50, false, 2_900L, 3_100L);
        Delta lastPiece = new Delta(4_000L, 2, BREW, false, 5, 650L, "OFFER_UPDATED", 130, false, 1_500L, 0L);
        Delta completion = new Delta(4_100L, 2, BREW, false, 0, 0L, "OFFER_COMPLETED", 130, false, 1_500L, 0L);
        LocalFlipHistoryService service = new LocalFlipHistoryService();

        List<Delta> selling = Arrays.asList(dearer, firstPiece, cheaper, lastPiece);
        StatsFlipInstance open = service.buildHistory(selling, null).get(BREW).get(0);
        assertTrue(open.inProgress);
        // 5 of the 100s, then 5 of the 50s.
        assertEquals(750L, open.buyCostGp);
        StatsCache whileSelling = new StatsCache();
        whileSelling.rebuild(selling);
        assertEquals(Long.valueOf(550L), whileSelling.getSummary().total_profit_gp);

        List<Delta> ended = TradeOfferCollapser.collapse(Arrays.asList(dearer, firstPiece, cheaper, lastPiece, completion));
        StatsFlipInstance flip = service.buildHistory(ended, null).get(BREW).get(0);
        assertFalse(flip.inProgress);
        assertEquals(10, flip.quantity);
        // All 10 of the 50s: the newest purchase before the sale ended.
        assertEquals(500L, flip.buyCostGp);
        StatsCache afterwards = new StatsCache();
        afterwards.rebuild(ended);
        assertEquals(Long.valueOf(800L), afterwards.getSummary().total_profit_gp);
    }

    private static Delta delta(long tsClientMs, int slot, boolean isBuy, int deltaQty, long deltaGp,
                               String eventType, int price) {
        return new Delta(tsClientMs, slot, BREW, isBuy, deltaQty, deltaGp, eventType, price, false);
    }
}
