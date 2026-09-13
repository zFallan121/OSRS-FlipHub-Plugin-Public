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

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The player's veto over a recipe the plugin guessed.
 *
 * <p>The guessing is worth keeping - a set break is booked without anyone
 * pressing a button - but a wrong guess used to be invisible and permanent.
 * The trade in the brief: a Dharok's set bought for 7.4M to flip, one helm
 * sold out of the bank, and the set's own sale for 7.6M then dropped for want
 * of stock, because the helm sale had been taken for a break of the set. A
 * real 200,000 gone and 7.4M stranded. These cases are that trade corrected,
 * and everything a correction has to reach: both ledgers, the header they
 * reconcile into, the file they are rebuilt from, and the way back.
 */
public class ConversionRejectionTest {
    private static final long ACCOUNT = 4242L;
    private static final long OTHER_ACCOUNT = 9191L;
    private static final long ACCOUNTWIDE = Const.ACCOUNTWIDE_KEY;

    private static final int DHAROKS_HELM = 4716;
    private static final int DHAROKS_PLATEBODY = 4720;
    private static final int DHAROKS_PLATELEGS = 4722;
    private static final int DHAROKS_GREATAXE = 4718;
    private static final int DHAROKS_ARMOUR_SET = 12881;
    private static final int CORE = 21730;
    private static final int BANDOS_BOOTS = 11836;
    private static final int GUARDIAN_BOOTS = 21733;
    private static final int MAHOGANY = 6332;

    /** The helm sale as it is stored: first fill time, slot, item. */
    private static final TradeKey HELM_SALE = new TradeKey(2_000L, 2, DHAROKS_HELM);

    private static ConversionRecipe dharoksSetBreak() {
        return new ConversionRecipe(
            ConversionKind.SET_BREAK,
            "Dharok's armour set",
            Collections.singletonList(new ConversionItem(DHAROKS_ARMOUR_SET, 1)),
            Arrays.asList(
                new ConversionItem(DHAROKS_HELM, 1),
                new ConversionItem(DHAROKS_PLATEBODY, 1),
                new ConversionItem(DHAROKS_PLATELEGS, 1),
                new ConversionItem(DHAROKS_GREATAXE, 1)),
            0L);
    }

    private static ConversionRecipe guardianBoots() {
        return new ConversionRecipe(
            ConversionKind.ASSEMBLE,
            "Guardian boots",
            Arrays.asList(new ConversionItem(CORE, 1), new ConversionItem(BANDOS_BOOTS, 1)),
            Collections.singletonList(new ConversionItem(GUARDIAN_BOOTS, 1)),
            0L);
    }

    private static Ledger ledger(RejectionStore corrections, ConversionRecipe... recipes) {
        return new Ledger(new RecipeIndex(Arrays.asList(recipes)), corrections);
    }

    private static Delta buy(long tsMs, int slot, int itemId, int qty, long gp) {
        return new Delta(tsMs, slot, itemId, true, qty, gp, "OFFER_COMPLETED", (int) (gp / qty), false);
    }

    private static Delta sell(long tsMs, int slot, int itemId, int qty, long gp, int unitPrice) {
        return new Delta(tsMs, slot, itemId, false, qty, gp, "OFFER_COMPLETED", unitPrice, false);
    }

    /** The brief's trades, one stored record each. */
    private static List<Delta> ownersTrades() {
        return Arrays.asList(
            buy(1_000L, 1, DHAROKS_ARMOUR_SET, 1, 7_400_000L),
            sell(2_000L, 2, DHAROKS_HELM, 1, 1_050_000L, 1_071_428),
            sell(3_000L, 3, DHAROKS_ARMOUR_SET, 1, 7_600_000L, 7_755_102));
    }

    /** The same, with the other three pieces also sold out of the bank, so the guessed break completes. */
    private static List<Delta> ownersTradesWithEveryPieceSold() {
        return Arrays.asList(
            buy(1_000L, 1, DHAROKS_ARMOUR_SET, 1, 7_400_000L),
            sell(2_000L, 2, DHAROKS_HELM, 1, 1_050_000L, 1_071_428),
            sell(3_000L, 3, DHAROKS_PLATEBODY, 1, 2_050_000L, 2_091_836),
            sell(4_000L, 4, DHAROKS_PLATELEGS, 1, 1_550_000L, 1_581_632),
            sell(5_000L, 5, DHAROKS_GREATAXE, 1, 3_050_000L, 3_112_244),
            sell(6_000L, 6, DHAROKS_ARMOUR_SET, 1, 7_600_000L, 7_755_102));
    }

    /** Parts bought, boots sold, then the parts themselves sold on: 18,775 and 16,995 as plain flips. */
    private static List<Delta> guardianBootsTrades() {
        return Arrays.asList(
            buy(1_000L, 1, BANDOS_BOOTS, 1, 731_225L),
            buy(2_000L, 2, CORE, 1, 483_005L),
            sell(3_000L, 3, GUARDIAN_BOOTS, 1, 1_365_140L, 1_393_000),
            sell(4_000L, 4, BANDOS_BOOTS, 1, 750_000L, 765_306),
            sell(5_000L, 5, CORE, 1, 500_000L, 510_204));
    }

    /** What the player files from the brief's scenario: the helm sale was not a break of the set. */
    private static ConversionRejection helmWasNotBrokenOffTheSet() {
        return new ConversionRejection(DHAROKS_ARMOUR_SET, "SET_BREAK", "Dharok's armour set", 2_000L, 9_000L,
            Collections.singletonList(HELM_SALE));
    }

    /**
     * The Profile tab's derivation: the stats cache for the rows and the
     * header, the flip history for the entries, and the reconcile that makes
     * the history authoritative for what the header says.
     */
    private static final class ProfileTab {
        final Map<Integer, List<StatsFlipInstance>> history;
        final StatsSummary summary;
        final List<StatsItem> items;
        /** What the cache said on its own, before the history overwrote it. */
        final long cacheProfit;
        final Map<Integer, Long> cacheProfitByItem = new HashMap<>();

        ProfileTab(Ledger ledger, List<Delta> deltas, long accountKey) {
            history = new LocalFlipHistoryService(ledger).buildHistory(deltas, null, accountKey);
            StatsCache cache = new StatsCache(ledger, accountKey);
            cache.rebuild(deltas);
            summary = cache.getSummary();
            items = cache.getItems();
            cacheProfit = summary.total_profit_gp != null ? summary.total_profit_gp : 0L;
            for (StatsItem item : items) {
                cacheProfitByItem.put(item.item_id, item.total_profit_gp != null ? item.total_profit_gp : 0L);
            }
            StatsView.reconcileWithFlipHistory(summary, items, history);
        }

        long headerProfit() {
            return summary.total_profit_gp != null ? summary.total_profit_gp : 0L;
        }

        long rowsProfit() {
            long sum = 0L;
            for (StatsItem item : items) {
                sum += item.total_profit_gp != null ? item.total_profit_gp : 0L;
            }
            return sum;
        }

        StatsItem row(int itemId) {
            for (StatsItem item : items) {
                if (item.item_id == itemId) {
                    return item;
                }
            }
            return null;
        }

        /** Entries that count: everything but a dismissed guess or an unfinished break. */
        List<StatsFlipInstance> counted(int itemId) {
            List<StatsFlipInstance> out = new ArrayList<>();
            for (StatsFlipInstance instance : entries(itemId)) {
                if (instance.counted()) {
                    out.add(instance);
                }
            }
            return out;
        }

        List<StatsFlipInstance> dismissed(int itemId) {
            List<StatsFlipInstance> out = new ArrayList<>();
            for (StatsFlipInstance instance : entries(itemId)) {
                if (instance.dismissed != null) {
                    out.add(instance);
                }
            }
            return out;
        }

        List<StatsFlipInstance> openBreaks(int itemId) {
            List<StatsFlipInstance> out = new ArrayList<>();
            for (StatsFlipInstance instance : entries(itemId)) {
                if (instance.openBreak) {
                    out.add(instance);
                }
            }
            return out;
        }

        private List<StatsFlipInstance> entries(int itemId) {
            List<StatsFlipInstance> entries = history.get(itemId);
            return entries != null ? entries : Collections.emptyList();
        }
    }

    @Test
    public void theSetSaleIsLostUntilTheHelmSaleIsRejected() {
        RejectionStore corrections = new RejectionStore();
        Ledger ledger = ledger(corrections, dharoksSetBreak());

        // Before: the helm sale is taken for a break, the set is consumed by
        // it, and the set's own sale then finds nothing to match. The break
        // never completes, so nothing at all is reported - not even the loss.
        ProfileTab before = new ProfileTab(ledger, ownersTrades(), ACCOUNT);
        assertEquals(0L, before.headerProfit());
        assertTrue(before.counted(DHAROKS_ARMOUR_SET).isEmpty());
        assertNull(before.row(DHAROKS_ARMOUR_SET));

        assertTrue(corrections.add(ACCOUNT, helmWasNotBrokenOffTheSet()));

        // After: the set was never broken, so it is still in stock when it sells.
        ProfileTab after = new ProfileTab(ledger, ownersTrades(), ACCOUNT);
        List<StatsFlipInstance> counted = after.counted(DHAROKS_ARMOUR_SET);
        assertEquals(1, counted.size());
        StatsFlipInstance flip = counted.get(0);
        assertNull("an ordinary flip, not an activity", flip.conversionKind);
        assertEquals("the whole set, intact", 7_400_000L, flip.buyCostGp);
        assertEquals(7_600_000L, flip.sellRevenueGp);
        assertEquals(200_000L, flip.profitGp);
        assertEquals(1, flip.quantity);
        assertEquals(200_000L, after.headerProfit());
        assertEquals(Long.valueOf(200_000L), after.row(DHAROKS_ARMOUR_SET).total_profit_gp);
        // The helm sale stands on its own: no stock behind it, so no flip and no break.
        assertTrue(after.counted(DHAROKS_HELM).isEmpty());
    }

    @Test
    public void anUnfinishedBreakIsShownOnTheSetAndCountsForNothing() {
        RejectionStore corrections = new RejectionStore();
        Ledger ledger = ledger(corrections, dharoksSetBreak());

        ProfileTab tab = new ProfileTab(ledger, ownersTrades(), ACCOUNT);

        // One piece sold of four: the break is open, and the entry says so
        // where the finished activity would be filed - on the set, not the
        // helm - carrying the sale the guess rests on.
        List<StatsFlipInstance> open = tab.openBreaks(DHAROKS_ARMOUR_SET);
        assertEquals(1, open.size());
        StatsFlipInstance entry = open.get(0);
        assertEquals(ConversionKind.SET_BREAK, entry.conversionKind);
        assertEquals("Dharok's armour set", entry.conversionName);
        assertEquals(Collections.singletonList(HELM_SALE), entry.conversionTrades);
        assertEquals(ACCOUNT, entry.accountKey);
        assertEquals("placed at the piece sale", 2_000L, entry.completionTsMs);
        assertTrue(tab.openBreaks(DHAROKS_HELM).isEmpty());
        assertTrue(tab.counted(DHAROKS_HELM).isEmpty());
        assertTrue(tab.counted(DHAROKS_ARMOUR_SET).isEmpty());

        // No figures on it: nothing the totals could take, not zeroes standing
        // in for numbers it does not have.
        assertFalse(entry.counted());
        assertEquals(0L, entry.profitGp);
        assertEquals(0L, entry.buyCostGp);
        assertEquals(0L, entry.sellRevenueGp);
        assertEquals(0L, entry.taxGp);
        assertEquals(0, entry.quantity);

        // And it moves no total: header, rows, count and tax are what the cache
        // said with nothing behind the set at all.
        assertEquals(0L, tab.cacheProfit);
        assertEquals(0L, tab.headerProfit());
        assertEquals(tab.headerProfit(), tab.rowsProfit());
        assertNull("the cache keeps no row for the set", tab.row(DHAROKS_ARMOUR_SET));
        assertEquals(Integer.valueOf(0), tab.summary.fill_count);
        assertEquals(Long.valueOf(0L), tab.summary.tax_paid_gp);

        // Under a range that starts after the piece sale it is out of view,
        // as a finished break at that time would be.
        Map<Integer, List<StatsFlipInstance>> ranged =
            new LocalFlipHistoryService(ledger).buildHistory(ownersTrades(), 2_500L, ACCOUNT);
        assertNull(ranged.get(DHAROKS_ARMOUR_SET));
    }

    @Test
    public void thePanelGetsARowForTheSetSoTheUnfinishedBreakCanBeReached() {
        // The cache has no row for the set - its cost is stranded in the break
        // - so without one the entry would have no card to sit on and no
        // control to click. The row is nothing but a place for it.
        RejectionStore corrections = new RejectionStore();
        ProfileTab tab = new ProfileTab(ledger(corrections, dharoksSetBreak()), ownersTrades(), ACCOUNT);
        assertTrue(tab.items.isEmpty());

        List<StatsItem> panelRows = new ArrayList<>(tab.items);
        StatsView.addRowsForUncountedEntries(panelRows, tab.history, StatsItemSort.COMPLETION);

        assertEquals(1, panelRows.size());
        StatsItem row = panelRows.get(0);
        assertEquals(DHAROKS_ARMOUR_SET, row.item_id);
        assertEquals(Long.valueOf(0L), row.total_profit_gp);
        assertEquals(Long.valueOf(0L), row.total_cost_gp);
        assertEquals(Integer.valueOf(0), row.fill_count);
        assertEquals(Integer.valueOf(0), row.total_qty);
        assertEquals(Long.valueOf(2_000L), row.last_sell_ts_ms);
    }

    @Test
    public void theOwnersSetSaleIsRecoveredByRejectingTheUnfinishedBreak() {
        RejectionStore corrections = new RejectionStore();
        Ledger ledger = ledger(corrections, dharoksSetBreak());

        // Before: 0 profit. The helm sale was taken for a break of the set,
        // the set's own sale then found nothing to match, and 7.4M sits
        // stranded in a break that will never finish. The entry to correct it
        // from is on the set's card.
        ProfileTab before = new ProfileTab(ledger, ownersTrades(), ACCOUNT);
        assertEquals(0L, before.headerProfit());
        assertTrue(before.counted(DHAROKS_ARMOUR_SET).isEmpty());
        assertEquals(1, before.openBreaks(DHAROKS_ARMOUR_SET).size());
        StatsFlipInstance open = before.openBreaks(DHAROKS_ARMOUR_SET).get(0);

        // Rejected from that entry, exactly as the panel's control does it:
        // the correction is keyed on the helm sale, filed against the set.
        ConversionRejection rejection = ConversionRejection.of(open, 9_000L);
        assertNotNull(rejection);
        assertEquals(DHAROKS_ARMOUR_SET, rejection.itemId);
        assertEquals(ConversionKind.SET_BREAK, rejection.kindOrNull());
        assertEquals(Collections.singletonList(HELM_SALE), rejection.trades());
        assertTrue(corrections.add(open.accountKey, rejection));

        // After: the helm sale is refused a recipe on replay, so the set is
        // never broken and is still in stock when its own sale comes.
        ProfileTab after = new ProfileTab(ledger, ownersTrades(), ACCOUNT);
        assertTrue(after.openBreaks(DHAROKS_ARMOUR_SET).isEmpty());
        List<StatsFlipInstance> counted = after.counted(DHAROKS_ARMOUR_SET);
        assertEquals(1, counted.size());
        StatsFlipInstance flip = counted.get(0);
        assertNull("an ordinary flip of the whole set", flip.conversionKind);
        assertEquals(7_400_000L, flip.buyCostGp);
        assertEquals(7_600_000L, flip.sellRevenueGp);
        assertEquals(200_000L, flip.profitGp);
        assertEquals(1, flip.quantity);
        // Both ledgers agree, so the rows still sum to the header.
        assertEquals(200_000L, after.cacheProfit);
        assertEquals(200_000L, after.headerProfit());
        assertEquals(after.headerProfit(), after.rowsProfit());
        assertEquals(Long.valueOf(200_000L), after.row(DHAROKS_ARMOUR_SET).total_profit_gp);
        assertEquals(Integer.valueOf(1), after.summary.fill_count);
        assertTrue("the helm sale stands on its own", after.counted(DHAROKS_HELM).isEmpty());
        // The correction stands where the guess was, so it can be taken back.
        assertEquals(1, after.dismissed(DHAROKS_ARMOUR_SET).size());
        assertEquals(ConversionKind.SET_BREAK, after.dismissed(DHAROKS_ARMOUR_SET).get(0).dismissed.kindOrNull());
    }

    @Test
    public void anUnfinishedBreakMovesNoTotalBesideRealFlips() {
        // The owner's trades with an ordinary flip alongside. Header, rows and
        // the filter slice are the flip alone, the placeholder row the panel
        // adds for the set is nothing, and the rows still sum to the header.
        RejectionStore corrections = new RejectionStore();
        Ledger ledger = ledger(corrections, dharoksSetBreak());
        List<Delta> trades = new ArrayList<>(ownersTrades());
        trades.add(buy(4_000L, 4, MAHOGANY, 10, 12_000L));
        trades.add(sell(5_000L, 4, MAHOGANY, 10, 13_720L, 1_400));
        long mahoganyTax = GeTax.forSale(MAHOGANY, 1_400, 10);

        ProfileTab tab = new ProfileTab(ledger, trades, ACCOUNT);

        assertEquals(1, tab.openBreaks(DHAROKS_ARMOUR_SET).size());
        assertEquals(1_720L, tab.cacheProfit);
        assertEquals(1_720L, tab.headerProfit());
        assertEquals(Long.valueOf(12_000L), tab.summary.total_cost_gp);
        assertEquals(Long.valueOf(10L), tab.summary.total_qty);
        assertEquals(Integer.valueOf(1), tab.summary.fill_count);
        assertEquals(Long.valueOf(mahoganyTax), tab.summary.tax_paid_gp);
        assertEquals(tab.headerProfit(), tab.rowsProfit());
        assertNull(tab.row(DHAROKS_ARMOUR_SET));

        List<StatsItem> panelRows = new ArrayList<>(tab.items);
        StatsView.addRowsForUncountedEntries(panelRows, tab.history, StatsItemSort.COMPLETION);
        assertEquals(2, panelRows.size());
        long rows = 0L;
        for (StatsItem item : panelRows) {
            rows += item.total_profit_gp != null ? item.total_profit_gp : 0L;
        }
        assertEquals(tab.headerProfit(), rows);

        StatsRender.StatsProfitSlice slice =
            StatsRender.sliceActivities(tab.history, StatsRecipeFilter.ALL);
        assertEquals(1_720L, slice.profitGp);
        assertEquals(12_000L, slice.costGp);
        assertEquals(1, slice.count);
    }

    @Test
    public void aCompletedBreakIsRejectedFromTheEntryItself() {
        // With every piece sold the guessed break completes and shows on the
        // set as one activity, keyed on all four sales - so one correction
        // covers the lot, rather than moving the guess onto the next piece.
        RejectionStore corrections = new RejectionStore();
        Ledger ledger = ledger(corrections, dharoksSetBreak());

        ProfileTab before = new ProfileTab(ledger, ownersTradesWithEveryPieceSold(), ACCOUNT);
        StatsFlipInstance guess = before.counted(DHAROKS_ARMOUR_SET).get(0);
        assertEquals(ConversionKind.SET_BREAK, guess.conversionKind);
        assertEquals(300_000L, guess.profitGp);
        assertEquals(4, guess.conversionTrades.size());
        assertTrue(guess.conversionTrades.contains(HELM_SALE));
        assertEquals(ACCOUNT, guess.accountKey);
        assertEquals("the set's own sale is still dropped", 300_000L, before.headerProfit());

        ConversionRejection rejection = ConversionRejection.of(guess, 9_000L);
        assertNotNull(rejection);
        assertTrue(corrections.add(guess.accountKey, rejection));

        ProfileTab after = new ProfileTab(ledger, ownersTradesWithEveryPieceSold(), ACCOUNT);
        assertEquals(1, after.counted(DHAROKS_ARMOUR_SET).size());
        assertEquals(200_000L, after.counted(DHAROKS_ARMOUR_SET).get(0).profitGp);
        assertEquals(200_000L, after.headerProfit());
        for (int piece : new int[]{DHAROKS_HELM, DHAROKS_PLATEBODY, DHAROKS_PLATELEGS, DHAROKS_GREATAXE}) {
            assertTrue("a piece sold from the bank has no cost basis and is no flip", after.counted(piece).isEmpty());
        }
        // The correction stays visible where the guess was, and counts for nothing.
        List<StatsFlipInstance> dismissed = after.dismissed(DHAROKS_ARMOUR_SET);
        assertEquals(1, dismissed.size());
        assertEquals(ConversionKind.SET_BREAK, dismissed.get(0).dismissed.kindOrNull());
        assertEquals(0L, dismissed.get(0).profitGp);
        assertEquals(0, dismissed.get(0).quantity);
    }

    @Test
    public void rejectingAnAssembleLeavesThePartsAsBought() {
        RejectionStore corrections = new RejectionStore();
        Ledger ledger = ledger(corrections, guardianBoots());

        ProfileTab before = new ProfileTab(ledger, guardianBootsTrades(), ACCOUNT);
        StatsFlipInstance guess = before.counted(GUARDIAN_BOOTS).get(0);
        assertEquals(ConversionKind.ASSEMBLE, guess.conversionKind);
        assertEquals(150_910L, guess.profitGp);
        assertEquals("the parts were consumed, so their later sales matched nothing",
            150_910L, before.headerProfit());

        assertTrue(corrections.add(ACCOUNT, ConversionRejection.of(guess, 9_000L)));

        // The boots sale stands on its own; the parts were never used and sell as what they are.
        ProfileTab after = new ProfileTab(ledger, guardianBootsTrades(), ACCOUNT);
        assertTrue(after.counted(GUARDIAN_BOOTS).isEmpty());
        assertEquals(18_775L, after.counted(BANDOS_BOOTS).get(0).profitGp);
        assertEquals(16_995L, after.counted(CORE).get(0).profitGp);
        assertEquals(18_775L + 16_995L, after.headerProfit());
        assertEquals(1, after.dismissed(GUARDIAN_BOOTS).size());
        assertEquals(ACCOUNT, after.dismissed(GUARDIAN_BOOTS).get(0).accountKey);
    }

    @Test
    public void restoringPutsTheAttributionBack() {
        RejectionStore corrections = new RejectionStore();
        Ledger ledger = ledger(corrections, guardianBoots());
        StatsFlipInstance guess = new ProfileTab(ledger, guardianBootsTrades(), ACCOUNT).counted(GUARDIAN_BOOTS).get(0);
        corrections.add(ACCOUNT, ConversionRejection.of(guess, 9_000L));
        StatsFlipInstance dismissed = new ProfileTab(ledger, guardianBootsTrades(), ACCOUNT).dismissed(GUARDIAN_BOOTS).get(0);

        // Undone from the entry that stands in for it, exactly as the panel would.
        assertTrue(corrections.remove(dismissed.accountKey, dismissed.dismissed));

        ProfileTab restored = new ProfileTab(ledger, guardianBootsTrades(), ACCOUNT);
        assertEquals(ConversionKind.ASSEMBLE, restored.counted(GUARDIAN_BOOTS).get(0).conversionKind);
        assertEquals(150_910L, restored.counted(GUARDIAN_BOOTS).get(0).profitGp);
        assertTrue(restored.dismissed(GUARDIAN_BOOTS).isEmpty());
        assertEquals(150_910L, restored.headerProfit());
        assertFalse("nothing left to remove", corrections.remove(ACCOUNT, dismissed.dismissed));
    }

    @Test
    public void bothLedgersAgreeOnARejectedTradeSoTheRowsStillSumToTheHeader() {
        // The header is overwritten from the history, and the rows from the
        // cache; if only one of the two honoured the correction the rows would
        // stop adding up to the number above them.
        RejectionStore corrections = new RejectionStore();
        Ledger ledger = ledger(corrections, dharoksSetBreak());
        corrections.add(ACCOUNT, helmWasNotBrokenOffTheSet());

        ProfileTab tab = new ProfileTab(ledger, ownersTrades(), ACCOUNT);
        assertEquals("the cache, on its own", 200_000L, tab.cacheProfit);
        assertEquals(Long.valueOf(200_000L), tab.cacheProfitByItem.get(DHAROKS_ARMOUR_SET));
        assertNull("the cache books no row for a sale with nothing behind it", tab.cacheProfitByItem.get(DHAROKS_HELM));
        assertEquals("the history, reconciled in", 200_000L, tab.headerProfit());
        assertEquals(tab.headerProfit(), tab.rowsProfit());
        assertEquals(Integer.valueOf(1), tab.summary.fill_count);
        assertEquals(Long.valueOf(1L), tab.summary.total_qty);

        // The cache keeps no placeholder for a dismissed guess - only the
        // history does, and reconcile counts it for nothing - so an item whose
        // only history is a dismissed guess has no row, and the sum still holds.
        RejectionStore bootsCorrections = new RejectionStore();
        Ledger bootsLedger = ledger(bootsCorrections, guardianBoots());
        StatsFlipInstance guess = new ProfileTab(bootsLedger, guardianBootsTrades(), ACCOUNT).counted(GUARDIAN_BOOTS).get(0);
        bootsCorrections.add(ACCOUNT, ConversionRejection.of(guess, 9_000L));
        ProfileTab boots = new ProfileTab(bootsLedger, guardianBootsTrades(), ACCOUNT);
        assertNull(boots.cacheProfitByItem.get(GUARDIAN_BOOTS));
        assertNull(boots.row(GUARDIAN_BOOTS));
        assertEquals(1, boots.dismissed(GUARDIAN_BOOTS).size());
        assertEquals(boots.headerProfit(), boots.rowsProfit());
        assertEquals(18_775L + 16_995L, boots.cacheProfit);
        assertEquals(Integer.valueOf(2), boots.summary.fill_count);
    }

    @Test
    public void theUploadedSummaryFollowsAndThePanelAloneGetsARowToRestoreFrom() {
        // The accountwide summary the site receives is the same snapshot and
        // history put through the same reconcile, so it follows the correction
        // by construction - and it carries no row that adds up to nothing. The
        // panel adds that row itself, because a dismissed guess has to sit on
        // a card to be undone.
        RejectionStore corrections = new RejectionStore();
        Ledger ledger = ledger(corrections, guardianBoots());
        StatsFlipInstance guess = new ProfileTab(ledger, guardianBootsTrades(), ACCOUNT).counted(GUARDIAN_BOOTS).get(0);
        corrections.add(ACCOUNT, ConversionRejection.of(guess, 9_000L));
        ProfileTab uploaded = new ProfileTab(ledger, guardianBootsTrades(), ACCOUNT);

        assertEquals(18_775L + 16_995L, uploaded.headerProfit());
        assertEquals(2, uploaded.items.size());
        assertNull(uploaded.row(GUARDIAN_BOOTS));

        List<StatsItem> panelRows = new ArrayList<>(uploaded.items);
        StatsView.addRowsForUncountedEntries(panelRows, uploaded.history, StatsItemSort.COMPLETION);
        assertEquals(3, panelRows.size());
        StatsItem placeholder = null;
        for (StatsItem item : panelRows) {
            if (item.item_id == GUARDIAN_BOOTS) {
                placeholder = item;
            }
        }
        assertNotNull(placeholder);
        assertEquals(Long.valueOf(0L), placeholder.total_profit_gp);
        assertEquals(Integer.valueOf(0), placeholder.fill_count);
        assertEquals(Long.valueOf(3_000L), placeholder.last_sell_ts_ms);
    }

    @Test
    public void aRejectionSurvivesAWriteAndReloadOfTheProfileFile() throws Exception {
        Path baseDir = Files.createTempDirectory("fliphub-corrections-roundtrip");
        try {
            ProfileStore store = new ProfileStore(new Gson(), "fliphub", "fliphub-dev", baseDir);
            List<ConversionRejection> filed = Collections.singletonList(helmWasNotBrokenOffTheSet());
            assertTrue(store.writeProfileData(ACCOUNT, ACCOUNTWIDE, "Zezima", ownersTrades(), filed) > 0L);

            ProfileData read = store.readProfileData(ACCOUNT, ACCOUNTWIDE);
            assertNotNull(read);
            assertEquals(3, read.deltas.size());
            assertEquals(1, read.rejectedConversions.size());
            assertEquals(Collections.singletonList(HELM_SALE), read.rejectedConversions.get(0).trades());
            assertEquals(ConversionKind.SET_BREAK, read.rejectedConversions.get(0).kindOrNull());

            // Loaded the way the profile loader loads it, into a fresh store.
            RejectionStore corrections = new RejectionStore();
            corrections.replace(ACCOUNT, read.rejectedConversions);
            assertTrue(corrections.isRejected(ACCOUNT, HELM_SALE));
            ProfileTab tab = new ProfileTab(ledger(corrections, dharoksSetBreak()), read.deltas, ACCOUNT);
            assertEquals(200_000L, tab.headerProfit());
        } finally {
            deleteRecursively(baseDir);
        }
    }

    @Test
    public void aProfileFileWrittenBeforeCorrectionsExistedLoadsCleanly() throws Exception {
        Path baseDir = Files.createTempDirectory("fliphub-corrections-legacy");
        try {
            ProfileStore store = new ProfileStore(new Gson(), "fliphub", "fliphub-dev", baseDir);
            Path file = store.getProfileFile(ACCOUNT, ACCOUNTWIDE);
            String legacy = "{\"accountHash\":4242,\"displayName\":\"Zezima\",\"updatedMs\":1,\"deltas\":["
                + "{\"tsClientMs\":1000,\"slot\":1,\"itemId\":12881,\"isBuy\":true,\"deltaQty\":1,"
                + "\"deltaGp\":7400000,\"eventType\":\"OFFER_COMPLETED\",\"price\":7400000}]}";
            Files.writeString(file, legacy, StandardCharsets.UTF_8);

            ProfileData read = store.readProfileData(file);
            assertNotNull(read);
            assertEquals(1, read.deltas.size());
            assertNull("no field, no corrections", read.rejectedConversions);
            RejectionStore corrections = new RejectionStore();
            corrections.replace(ACCOUNT, read.rejectedConversions);
            assertTrue(corrections.applicable(ACCOUNT).isEmpty());
            assertFalse(corrections.isRejected(ACCOUNT, HELM_SALE));

            // And a file with nothing to say is written the way it always was.
            assertTrue(store.writeProfileData(ACCOUNT, ACCOUNTWIDE, "Zezima", read.deltas, new ArrayList<>()) > 0L);
            assertFalse(Files.readString(file, StandardCharsets.UTF_8).contains("rejectedConversions"));
        } finally {
            deleteRecursively(baseDir);
        }
    }

    @Test
    public void theKeyOutlivesTheCollapseOfAnOpenOffer() {
        // Rejected while the boots offer was still filling: one fill and the
        // completion are two stored records. On completion they collapse into
        // one, stamped with the first fill's time, which is what the key names.
        RejectionStore corrections = new RejectionStore();
        Ledger ledger = ledger(corrections, guardianBoots());
        List<Delta> open = Arrays.asList(
            buy(1_000L, 1, BANDOS_BOOTS, 1, 731_225L),
            buy(2_000L, 2, CORE, 1, 483_005L),
            new Delta(3_000L, 3, GUARDIAN_BOOTS, false, 1, 1_365_140L, "OFFER_UPDATED", 1_393_000, false),
            new Delta(3_500L, 3, GUARDIAN_BOOTS, false, 0, 0L, "OFFER_COMPLETED", 1_393_000, false));

        StatsFlipInstance guess = new ProfileTab(ledger, open, ACCOUNT).counted(GUARDIAN_BOOTS).get(0);
        assertEquals(ConversionKind.ASSEMBLE, guess.conversionKind);
        assertEquals(Collections.singletonList(new TradeKey(3_000L, 3, GUARDIAN_BOOTS)), guess.conversionTrades);
        assertTrue(corrections.add(ACCOUNT, ConversionRejection.of(guess, 9_000L)));

        List<Delta> collapsed = TradeOfferCollapser.collapse(new ArrayList<>(open));
        assertEquals(3, collapsed.size());
        ProfileTab after = new ProfileTab(ledger, collapsed, ACCOUNT);
        assertTrue(after.counted(GUARDIAN_BOOTS).isEmpty());
        assertEquals(1, after.dismissed(GUARDIAN_BOOTS).size());
    }

    @Test
    public void theAccountwideReplayHonoursEveryProfilesCorrections() {
        RejectionStore corrections = new RejectionStore();
        Ledger ledger = ledger(corrections, dharoksSetBreak());
        corrections.add(ACCOUNT, helmWasNotBrokenOffTheSet());

        // The pooled accountwide replay holds every profile's trades, so it is
        // answered by every profile's corrections; another character's own
        // replay is not.
        assertTrue(corrections.isRejected(ACCOUNTWIDE, HELM_SALE));
        assertFalse(corrections.isRejected(OTHER_ACCOUNT, HELM_SALE));
        ProfileTab pooled = new ProfileTab(ledger, ownersTrades(), ACCOUNTWIDE);
        assertEquals(200_000L, pooled.headerProfit());
        assertEquals(1, pooled.dismissed(DHAROKS_ARMOUR_SET).size());

        // A correction filed from the accountwide view reaches the profile's own replay too.
        RejectionStore fromThePool = new RejectionStore();
        fromThePool.add(ACCOUNTWIDE, helmWasNotBrokenOffTheSet());
        assertTrue(fromThePool.isRejected(ACCOUNT, HELM_SALE));
        assertTrue("filed once, however many files it reaches", fromThePool.applicable(ACCOUNT).size() == 1);
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        }
    }
}
