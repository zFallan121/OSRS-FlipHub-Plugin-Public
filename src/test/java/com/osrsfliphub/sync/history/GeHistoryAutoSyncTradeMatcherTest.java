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
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * What the Grand Exchange history sync is allowed to import.
 *
 * <p>A trade that reaches the plugin twice is not a cosmetic problem. Both
 * copies are real to the ledger, so the profit doubles, and a conversion sees
 * two sets where the player bought one and records two activities off it.
 *
 * <p>The trap this suite exists for: a delta's {@code price} is the price the
 * offer was <em>listed</em> at, while the history widget reports what the trade
 * actually <em>made</em>. Those are different numbers for nearly every offer
 * ever filled, so matching one against the other declares everything missing.
 */
public class GeHistoryAutoSyncTradeMatcherTest {
    private static final int BLUE_DHIDE_SET = 12867;
    private static final int BLUE_DHIDE_BODY = 2499;
    private static final int BLUE_DHIDE_CHAPS = 2493;
    private static final int BLUE_DHIDE_VAMBRACES = 2487;
    private static final int NATURE_RUNE = 561;

    /** A trade as the client watched it happen: coins real, price as listed. */
    private static Delta live(int itemId, boolean isBuy, int qty, long gp, int listedPrice) {
        return new Delta(1_000L, 3, itemId, isBuy, qty, gp, "OFFER_UPDATED", listedPrice, false);
    }

    /** The same trade as the history widget reports it: coins net, price gross. */
    private static Trade history(int itemId, boolean isBuy, int qty, long netGp, int grossPrice) {
        return new Trade(itemId, isBuy, qty, grossPrice, netGp);
    }

    /** A fill of an offer placed at {@code offerStartMs} on {@code slot}, as the client watched it. */
    private static Delta fill(long tsMs, int slot, long offerStartMs, int itemId, boolean isBuy,
                                        int qty, long gp, int listedPrice) {
        return new Delta(tsMs, slot, itemId, isBuy, qty, gp, "OFFER_UPDATED", listedPrice, false,
            offerStartMs, 0L);
    }

    /** A finished offer as the store keeps it: one record, fills already folded in. */
    private static Delta completed(long tsMs, int slot, long offerStartMs, int itemId, boolean isBuy,
                                             int qty, long gp, int listedPrice) {
        return new Delta(tsMs, slot, itemId, isBuy, qty, gp, "OFFER_COMPLETED", listedPrice, false,
            offerStartMs, tsMs + 1L);
    }

    private static List<Trade> missing(List<Trade> historyTrades, List<Delta> deltas) {
        return AutoSyncTradeMatcher.planMissingTrades(historyTrades, deltas, AutoSyncTradeMatcher.LastSync.NONE)
            .missingTrades;
    }

    @Test
    public void aBuyThatFilledUnderItsOfferPriceIsNotImportedAgain() {
        // The trade from the report. The set was listed at 23,401 and filled at
        // 15,000, so the listed price matches nothing in the history - and the
        // set was imported a second time, which is what let one set break be
        // recorded as two.
        List<Delta> deltas = Collections.singletonList(
            live(BLUE_DHIDE_SET, true, 1, 15_000L, 23_401));
        List<Trade> historyTrades = Collections.singletonList(
            history(BLUE_DHIDE_SET, true, 1, 15_000L, 15_000));

        assertTrue(missing(historyTrades, deltas).isEmpty());
    }

    @Test
    public void aSellIsMatchedOnCoinsReceivedRatherThanOnTaxOrListing() {
        // The three pieces off that set. Each was listed at one price, filled at
        // a higher one, and the history reports the price before tax against the
        // coins after it. Only the coins agree, and they agree exactly.
        List<Delta> deltas = Arrays.asList(
            live(BLUE_DHIDE_BODY, false, 1, 4_900L, 4_013),
            live(BLUE_DHIDE_CHAPS, false, 1, 1_940L, 1_534),
            live(BLUE_DHIDE_VAMBRACES, false, 1, 1_261L, 1_000));
        List<Trade> historyTrades = Arrays.asList(
            history(BLUE_DHIDE_BODY, false, 1, 4_900L, 5_000),
            history(BLUE_DHIDE_CHAPS, false, 1, 1_940L, 1_979),
            history(BLUE_DHIDE_VAMBRACES, false, 1, 1_261L, 1_286));

        assertTrue(missing(historyTrades, deltas).isEmpty());
    }

    @Test
    public void aTradeMadeSomewhereElseIsStillImported() {
        // The whole point of the sync. Nothing was watched live, so every row of
        // the history is a trade the plugin has never seen.
        List<Trade> historyTrades = Arrays.asList(
            history(BLUE_DHIDE_SET, true, 1, 15_000L, 15_000),
            history(BLUE_DHIDE_BODY, false, 1, 4_900L, 5_000));

        List<Trade> imported = missing(historyTrades, new ArrayList<Delta>());

        assertEquals(2, imported.size());
    }

    @Test
    public void anOfferThatFilledInPiecesIsMatchedByTheWholeItAddsUpTo() {
        // One offer, three fills, one history row for the lot. The fills are
        // pooled by what a unit came to, so 300 nature runes at 118 cover a
        // history row of 300 at 118 however many events it took.
        List<Delta> deltas = Arrays.asList(
            live(NATURE_RUNE, true, 100, 11_800L, 125),
            live(NATURE_RUNE, true, 50, 5_900L, 125),
            live(NATURE_RUNE, true, 150, 17_700L, 125));
        List<Trade> historyTrades = Collections.singletonList(
            history(NATURE_RUNE, true, 300, 35_400L, 118));

        assertTrue(missing(historyTrades, deltas).isEmpty());
    }

    @Test
    public void onlyThePartOfAnOfferThatWasMissedIsImported() {
        // Half the offer was watched live and half was not. Importing the whole
        // row would double the half already recorded, so only the shortfall is
        // taken.
        List<Delta> deltas = Collections.singletonList(
            live(NATURE_RUNE, true, 100, 11_800L, 125));
        List<Trade> historyTrades = Collections.singletonList(
            history(NATURE_RUNE, true, 300, 35_400L, 118));

        List<Trade> imported = missing(historyTrades, deltas);

        assertEquals(1, imported.size());
        assertEquals(200, imported.get(0).quantity);
        assertEquals(23_600L, imported.get(0).totalGp);
    }

    @Test
    public void twoIdenticalTradesBothCountAndBothStayCounted() {
        // A player who buys the same thing twice at the same price has two
        // trades, and one live delta explains only one of them.
        List<Delta> deltas = Collections.singletonList(
            live(BLUE_DHIDE_SET, true, 1, 15_000L, 23_401));
        List<Trade> historyTrades = Arrays.asList(
            history(BLUE_DHIDE_SET, true, 1, 15_000L, 15_000),
            history(BLUE_DHIDE_SET, true, 1, 15_000L, 15_000));

        List<Trade> imported = missing(historyTrades, deltas);

        assertEquals(1, imported.size());
        assertEquals(BLUE_DHIDE_SET, imported.get(0).itemId);
    }
    @Test
    public void anOfferThatFilledAtTwoPricesAndIsStillKeptAsFillsIsNotImportedAgain() {
        // The finding. One offer for 10, filled 5 at 95 and 5 at 98 and cancelled
        // before the rest came in, so the store still has it as two fills. The
        // history shows it as one row of 10 for 965 - 96 each - which matches
        // neither fill's price, and the whole offer was imported a second time.
        // The fills are one offer's, so they are summed before anything is compared.
        List<Delta> deltas = Arrays.asList(
            fill(1_000L, 3, 500L, NATURE_RUNE, true, 5, 475L, 100),
            fill(2_000L, 3, 500L, NATURE_RUNE, true, 5, 490L, 100));
        List<Trade> historyTrades = Collections.singletonList(
            history(NATURE_RUNE, true, 10, 965L, 96));

        assertTrue(missing(historyTrades, deltas).isEmpty());
    }

    @Test
    public void anOfferWhoseCoinsDifferByARoundingIsNotImportedAgain() {
        // The stored offer made 969 coins for 10; the history shows 970 for the
        // same 10, its "each" price rounded to 97 and multiplied back out. The
        // unit prices land on 96 and 97, so neither exact rule sees them as one
        // offer. One coin per unit is the difference rounding can make.
        List<Delta> deltas = Collections.singletonList(
            completed(1_000L, 3, 500L, NATURE_RUNE, true, 10, 969L, 100));
        List<Trade> historyTrades = Collections.singletonList(
            history(NATURE_RUNE, true, 10, 970L, 97));

        assertTrue(missing(historyTrades, deltas).isEmpty());
    }

    @Test
    public void anOfferOfTheSameSizeAtAGenuinelyDifferentPriceIsStillImported() {
        // Same item, same quantity, fifteen coins apart on ten units: a second
        // offer at a different price, not the first one rounded. The tolerance
        // must not swallow it.
        List<Delta> deltas = Collections.singletonList(
            completed(1_000L, 3, 500L, NATURE_RUNE, true, 10, 950L, 100));
        List<Trade> historyTrades = Collections.singletonList(
            history(NATURE_RUNE, true, 10, 965L, 96));

        List<Trade> imported = missing(historyTrades, deltas);

        assertEquals(1, imported.size());
        assertEquals(965L, imported.get(0).totalGp);
    }

    @Test
    public void twoOffersOnOneSlotAreNotSummedIntoOne() {
        // Five bought at 95 and, once that offer completed, five more at 98 on the
        // same slot. They are two offers and the history has two rows; a later row
        // of 10 for 965 is a third offer and must be imported.
        List<Delta> deltas = Arrays.asList(
            completed(1_000L, 3, 500L, NATURE_RUNE, true, 5, 475L, 100),
            completed(2_000L, 3, 900L, NATURE_RUNE, true, 5, 490L, 100));
        List<Trade> historyTrades = Arrays.asList(
            history(NATURE_RUNE, true, 10, 965L, 96),
            history(NATURE_RUNE, true, 5, 490L, 98),
            history(NATURE_RUNE, true, 5, 475L, 95));

        List<Trade> imported = missing(historyTrades, deltas);

        assertEquals(1, imported.size());
        assertEquals(10, imported.get(0).quantity);
    }

    @Test
    public void aRowExplainedOutrightKeepsItsOfferFromAnOlderRowsShortfall() {
        // The store holds one offer of 300 at 118, watched live; its row is the
        // newer of two. The older row, 400 at the same price, was never watched.
        // Matching in history order alone would let the older row eat the 300 and
        // import a shortfall of 100, then find the newer row's offer gone. Rows an
        // offer explains outright claim it first, so the older row imports as the
        // whole 400 it is.
        List<Delta> deltas = Collections.singletonList(
            completed(1_000L, 3, 500L, NATURE_RUNE, true, 300, 35_400L, 125));
        List<Trade> historyTrades = Arrays.asList(
            history(NATURE_RUNE, true, 300, 35_400L, 118),
            history(NATURE_RUNE, true, 400, 47_200L, 118));

        List<Trade> imported = missing(historyTrades, deltas);

        assertEquals(1, imported.size());
        assertEquals(400, imported.get(0).quantity);
        assertEquals(47_200L, imported.get(0).totalGp);
    }

    /**
     * Three real buys of the same item at the same price per unit: one of 100 that the
     * plugin was not running for, and two of 60 that it watched. All three are in the game's
     * history. The 100 is missing from the plugin and must be imported.
     *
     * <p>The pooling rule used to add the two 60s together, decide they covered the 100, and
     * declare that row already recorded. It was never imported, and the two real offers had
     * been spent explaining it, so one of them was then imported as a residual of the wrong
     * size. The totals happened to come out near enough that nothing looked wrong.
     */
    @Test
    public void twoSeparateOffersDoNotExplainAThirdLargerOne() {
        List<Delta> stored = Arrays.asList(
            completed(2_000L, 1, 1_900L, 561, true, 60, 12_000L, 200),
            completed(3_000L, 2, 2_900L, 561, true, 60, 12_000L, 200)
        );
        // Newest first, as the widget lists them.
        List<Trade> rows = Arrays.asList(
            history(561, true, 60, 12_000L, 200),
            history(561, true, 60, 12_000L, 200),
            history(561, true, 100, 20_000L, 200)
        );

        List<Trade> missing = missing(rows, stored);

        assertEquals(1, missing.size());
        assertEquals(100, missing.get(0).quantity);
        assertEquals(20_000L, missing.get(0).totalGp);
    }

    /**
     * One offer of 100 that the client watched as two fills on the same slot, and the store
     * kept them apart. That is the case pooling exists for, and it still works.
     */
    @Test
    public void oneOfferKeptAsSeparateFillsIsStillRecognised() {
        List<Delta> stored = Arrays.asList(
            fill(2_000L, 4, 1_900L, 561, true, 40, 8_000L, 200),
            fill(2_500L, 4, 1_900L, 561, true, 60, 12_000L, 200)
        );
        List<Trade> rows = Collections.singletonList(history(561, true, 100, 20_000L, 200));

        assertTrue(missing(rows, stored).isEmpty());
    }

    // ---- rows known to be newer than the last sync ----

    private static final int YEW_LONGBOW = 855;
    private static final long LAST_SYNC_MS = 50_000L;

    private static List<Trade> missingSince(List<Trade> historyTrades, List<Delta> deltas, long sinceMs) {
        return missingSince(historyTrades, deltas,
            new AutoSyncTradeMatcher.LastSync(sinceMs, Collections.<Integer, Long>emptyMap()));
    }

    private static List<Trade> missingSince(List<Trade> historyTrades, List<Delta> deltas,
                                            AutoSyncTradeMatcher.LastSync lastSync) {
        return AutoSyncTradeMatcher.planMissingTrades(historyTrades, deltas, lastSync).missingTrades;
    }

    @Test
    public void anOldSaleOfTheSameSizeAndCoinsDoesNotExplainTodaysSale() {
        // The report: 100 longbows sold a month ago with the plugin watching, 100 more sold
        // today on a phone for the same coins. The row is today's; the record is last month's.
        List<Delta> stored = Collections.singletonList(
            completed(1_000L, 3, 900L, YEW_LONGBOW, false, 100, 58_800L, 600));
        List<Trade> rows = Collections.singletonList(history(YEW_LONGBOW, false, 100, 58_800L, 600));

        assertEquals(1, missingSince(rows, stored, LAST_SYNC_MS).size());
    }

    @Test
    public void withNothingKnownAboutWhenTheRowsWereMadeEveryStoredTradeIsCompared() {
        // After a wipe, or on the first sync of an install that never stored the moment, a
        // row may be as old as anything stored, and the twin has to be allowed to be it.
        List<Delta> stored = Collections.singletonList(
            completed(1_000L, 3, 900L, YEW_LONGBOW, false, 100, 58_800L, 600));
        List<Trade> rows = Collections.singletonList(history(YEW_LONGBOW, false, 100, 58_800L, 600));

        assertTrue(missingSince(rows, stored, 0L).isEmpty());
        assertTrue(missing(rows, stored).isEmpty());
    }

    @Test
    public void aSaleRecordedSinceTheLastSyncStillExplainsItsRow() {
        List<Delta> stored = Collections.singletonList(
            completed(60_000L, 3, 59_000L, YEW_LONGBOW, false, 100, 58_800L, 600));
        List<Trade> rows = Collections.singletonList(history(YEW_LONGBOW, false, 100, 58_800L, 600));

        assertTrue(missingSince(rows, stored, LAST_SYNC_MS).isEmpty());
    }

    @Test
    public void ofTwoIdenticalRowsOnlyTheOneWatchedSinceTheLastSyncIsExplained() {
        // Sold 100 on the desktop today and 100 on a phone today, and once last month. Two rows,
        // one record since the sync: one row is that record, the other is the phone's.
        List<Delta> stored = Arrays.asList(
            completed(1_000L, 3, 900L, YEW_LONGBOW, false, 100, 58_800L, 600),
            completed(60_000L, 3, 59_000L, YEW_LONGBOW, false, 100, 58_800L, 600));
        List<Trade> rows = Arrays.asList(
            history(YEW_LONGBOW, false, 100, 58_800L, 600),
            history(YEW_LONGBOW, false, 100, 58_800L, 600));

        assertEquals(1, missingSince(rows, stored, LAST_SYNC_MS).size());
        assertTrue(missing(rows, stored).isEmpty());
    }

    @Test
    public void anOfferThatBeganBeforeTheLastSyncAndEndedAfterItIsStillOneOffer() {
        // 40 filled before the sync and 60 after. The row is all 100; dropping the early fill
        // would leave 60 to explain it and import the other 40 a second time.
        List<Delta> stored = Arrays.asList(
            fill(40_000L, 2, 39_000L, NATURE_RUNE, true, 40, 4_000L, 100),
            completed(60_000L, 2, 39_000L, NATURE_RUNE, true, 60, 6_000L, 100));
        List<Trade> rows = Collections.singletonList(history(NATURE_RUNE, true, 100, 10_000L, 100));

        assertTrue(missingSince(rows, stored, LAST_SYNC_MS).isEmpty());
    }

    @Test
    public void aSaleTheLastSyncImportedDoesNotExplainTheNextOneMadeElsewhere() {
        // Sold 100 on a phone yesterday, imported by last night's sync and stamped a few
        // milliseconds before it - well inside the slack. Sold 100 more on the phone today.
        // The imported record was a row of that sync; it cannot also be a row above it.
        List<Delta> stored = Collections.singletonList(completed(
            LAST_SYNC_MS + 5_000L, Const.GE_HISTORY_SYNTHETIC_SLOT_START, 0L, YEW_LONGBOW, false, 100, 58_800L, 600));
        List<Trade> rows = Collections.singletonList(history(YEW_LONGBOW, false, 100, 58_800L, 600));

        assertEquals(1, missingSince(rows, stored, LAST_SYNC_MS).size());
    }

    @Test
    public void afterAWipeAnImportedTradeStillStopsItsRowBeingImportedAgain() {
        // Nothing is known about the rows then, and the imported record is the only thing
        // standing between its row and a second import.
        List<Delta> stored = Collections.singletonList(completed(
            1_000L, Const.GE_HISTORY_SYNTHETIC_SLOT_START, 0L, YEW_LONGBOW, false, 100, 58_800L, 600));
        List<Trade> rows = Collections.singletonList(history(YEW_LONGBOW, false, 100, 58_800L, 600));

        assertTrue(missingSince(rows, stored, 0L).isEmpty());
    }

    @Test
    public void aSellOfferLeftUpForMonthsDoesNotHoldEveryOtherTradeComparable() {
        // Seen in the real client: six sell offers sitting in their slots since June to August
        // pulled the cutoff back to June, so last month's sale still explained today's.
        List<Delta> stored = Collections.singletonList(
            completed(1_000L, 3, 900L, YEW_LONGBOW, false, 100, 58_800L, 600));
        List<Trade> rows = Collections.singletonList(history(YEW_LONGBOW, false, 100, 58_800L, 600));
        AutoSyncTradeMatcher.LastSync sellOfferUpSinceJune =
            new AutoSyncTradeMatcher.LastSync(LAST_SYNC_MS, Collections.singletonMap(0, 100L));

        assertEquals(1, missingSince(rows, stored, sellOfferUpSinceJune).size());
    }

    @Test
    public void anOfferStillInItsSlotAtTheLastSyncKeepsItsEarlierFillsComparable() {
        // 100 bought before the sync while the offer sat in slot 2; it finished while nothing was
        // watching, so the fill is all that was recorded. Its row arrives now and must find it.
        List<Delta> stored = Collections.singletonList(
            fill(40_000L, 2, 39_000L, NATURE_RUNE, true, 100, 10_000L, 100));
        List<Trade> rows = Collections.singletonList(history(NATURE_RUNE, true, 100, 10_000L, 100));

        assertTrue(missingSince(rows, stored,
            new AutoSyncTradeMatcher.LastSync(LAST_SYNC_MS, Collections.singletonMap(2, 39_000L))).isEmpty());
        // Not remembered as open, the fill would be left out and the 100 imported a second time.
        assertEquals(1, missingSince(rows, stored, LAST_SYNC_MS).size());
    }

    @Test
    public void anEarlierOfferOnThatSameSlotIsStillLeftOut() {
        // Slot 2 held last month's sale before today's offer went up in it. Only the open offer is kept.
        List<Delta> stored = Collections.singletonList(
            completed(1_000L, 2, 900L, YEW_LONGBOW, false, 100, 58_800L, 600));
        List<Trade> rows = Collections.singletonList(history(YEW_LONGBOW, false, 100, 58_800L, 600));

        assertEquals(1, missingSince(rows, stored,
            new AutoSyncTradeMatcher.LastSync(LAST_SYNC_MS, Collections.singletonMap(2, 39_000L))).size());
    }

    @Test
    public void aBuyIsHeldToTheSameRuleAsASale() {
        List<Delta> stored = Collections.singletonList(
            completed(1_000L, 3, 900L, NATURE_RUNE, true, 100, 10_000L, 100));
        List<Trade> rows = Collections.singletonList(history(NATURE_RUNE, true, 100, 10_000L, 100));

        assertEquals(1, missingSince(rows, stored, LAST_SYNC_MS).size());
    }
}
