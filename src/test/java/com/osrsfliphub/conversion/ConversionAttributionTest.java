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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A breakdown must belong to the units it describes.
 *
 * <p>An "assembled from" block that does not add up to the cost basis beside it
 * is worse than no block at all: it is a false attribution on a real trade, and
 * the whole design is built on not doing that.
 */
public class ConversionAttributionTest {
    private static final int CORE = 21730;
    private static final int BANDOS_BOOTS = 11836;
    private static final int GUARDIAN_BOOTS = 21733;
    private static final int DHAROKS_HELM = 4716;
    private static final int DHAROKS_HELM_0 = 4880;
    private static final int DHAROKS_PLATEBODY = 4720;
    private static final int DHAROKS_PLATELEGS = 4722;
    private static final int DHAROKS_GREATAXE = 4718;
    private static final int DHAROKS_ARMOUR_SET = 12881;

    private static final int ARMADYL_GODSWORD = 11802;
    private static final int GODSWORD_BLADE = 11690;
    private static final int ARMADYL_HILT = 11810;
    private static final int GODSWORD_SHARD_1 = 11818;
    private static final int GODSWORD_SHARD_2 = 11820;
    private static final int GODSWORD_SHARD_3 = 11822;
    private static final int BANDOS_CHESTPLATE = 11832;
    private static final int BANDOSIAN_COMPONENTS = 26394;
    private static final int BLUE_DHIDE_SET = 13264;
    private static final int BLUE_DHIDE_BODY = 2499;
    private static final int BLUE_DHIDE_CHAPS = 2493;
    private static final int BLUE_DHIDE_VAMBRACES = 2487;

    private static final List<ConversionItem> DHAROKS_PIECES = Arrays.asList(
        new ConversionItem(DHAROKS_HELM, 1),
        new ConversionItem(DHAROKS_PLATEBODY, 1),
        new ConversionItem(DHAROKS_PLATELEGS, 1),
        new ConversionItem(DHAROKS_GREATAXE, 1));

    private static ConversionRecipe dharoksSetBreak() {
        return new ConversionRecipe(
            ConversionKind.SET_BREAK,
            "Dharok's armour set",
            Collections.singletonList(new ConversionItem(DHAROKS_ARMOUR_SET, 1)),
            DHAROKS_PIECES,
            0L);
    }

    private static LocalFlipHistoryService serviceFor(ConversionRecipe recipe) {
        return new LocalFlipHistoryService(
            new ConversionLedger(new ConversionRecipeIndex(Collections.singletonList(recipe))));
    }

    private static LocalFlipHistoryService service() {
        ConversionRecipe recipe = new ConversionRecipe(
            ConversionKind.ASSEMBLE,
            "Guardian boots",
            Arrays.asList(new ConversionItem(CORE, 1), new ConversionItem(BANDOS_BOOTS, 1)),
            Collections.singletonList(new ConversionItem(GUARDIAN_BOOTS, 1)),
            0L);
        return new LocalFlipHistoryService(
            new ConversionLedger(new ConversionRecipeIndex(Collections.singletonList(recipe))));
    }

    private static LocalTradeDelta buy(long tsMs, int slot, int itemId, int qty, long gp) {
        return new LocalTradeDelta(tsMs, slot, itemId, true, qty, gp, "OFFER_COMPLETED", (int) (gp / qty), false);
    }

    private static LocalTradeDelta sell(long tsMs, int slot, int itemId, int qty, long gp, int unitPrice) {
        return new LocalTradeDelta(tsMs, slot, itemId, false, qty, gp, "OFFER_COMPLETED", unitPrice, false);
    }

    private static List<StatsFlipInstance> oldestFirst(Map<Integer, List<StatsFlipInstance>> byItem) {
        List<StatsFlipInstance> history = byItem.get(GUARDIAN_BOOTS);
        java.util.List<StatsFlipInstance> copy = new java.util.ArrayList<>(history);
        Collections.reverse(copy);
        return copy;
    }

    @Test
    public void combiningFourPiecesIntoASetIsAttributedToTheSet() {
        // N -> 1, the same shape as an assemble: four purchases, one sale of a
        // thing that was never bought, and a cost basis that is their sum.
        ConversionRecipe combine = new ConversionRecipe(
            ConversionKind.SET_COMBINE,
            "Dharok's armour set",
            DHAROKS_PIECES,
            Collections.singletonList(new ConversionItem(DHAROKS_ARMOUR_SET, 1)),
            0L);
        List<LocalTradeDelta> deltas = Arrays.asList(
            buy(1_000L, 1, DHAROKS_HELM, 1, 1_000_000L),
            buy(2_000L, 2, DHAROKS_PLATEBODY, 1, 2_000_000L),
            buy(3_000L, 3, DHAROKS_PLATELEGS, 1, 1_500_000L),
            buy(4_000L, 4, DHAROKS_GREATAXE, 1, 3_000_000L),
            sell(5_000L, 5, DHAROKS_ARMOUR_SET, 1, 8_000_000L, 8_163_265)
        );

        StatsFlipInstance activity =
            serviceFor(combine).buildHistory(deltas, null).get(DHAROKS_ARMOUR_SET).get(0);

        assertEquals(ConversionKind.SET_COMBINE, activity.conversionKind);
        assertEquals(7_500_000L, activity.buyCostGp);
        assertEquals(500_000L, activity.profitGp);
        assertEquals(4, activity.conversionLines.size());
    }

    @Test
    public void breakingASetIsOneActivityAgainstTheSet() {
        // 1 -> N. One purchase becomes four sellable pieces, and the four sales
        // are one activity, not four: the set is what was traded, and what it
        // made is the pieces added up less what it cost.
        List<LocalTradeDelta> deltas = Arrays.asList(
            buy(1_000L, 1, DHAROKS_ARMOUR_SET, 1, 7_400_000L),
            sell(2_000L, 2, DHAROKS_HELM, 1, 1_050_000L, 1_071_428),
            sell(3_000L, 3, DHAROKS_PLATEBODY, 1, 2_050_000L, 2_091_836),
            sell(4_000L, 4, DHAROKS_PLATELEGS, 1, 1_550_000L, 1_581_632),
            sell(5_000L, 5, DHAROKS_GREATAXE, 1, 3_050_000L, 3_112_244)
        );

        Map<Integer, List<StatsFlipInstance>> history =
            serviceFor(dharoksSetBreak()).buildHistory(deltas, null);

        StatsFlipInstance activity = history.get(DHAROKS_ARMOUR_SET).get(0);
        assertEquals(ConversionKind.SET_BREAK, activity.conversionKind);
        assertEquals(7_400_000L, activity.buyCostGp);
        assertEquals(7_700_000L, activity.sellRevenueGp);
        assertEquals(300_000L, activity.profitGp);
        assertEquals(1, activity.quantity);

        for (int pieceId : new int[]{DHAROKS_HELM, DHAROKS_PLATEBODY, DHAROKS_PLATELEGS, DHAROKS_GREATAXE}) {
            List<StatsFlipInstance> piece = history.get(pieceId);
            assertTrue("a piece off a set is not an activity of its own",
                piece == null || piece.isEmpty());
        }
    }

    @Test
    public void aBreakListsThePiecesItBecameAndWhatTheyFetched() {
        // The block under a break reads the other way round from an assemble:
        // these are what came out, and they add up to the revenue rather than
        // to the cost.
        List<LocalTradeDelta> deltas = Arrays.asList(
            buy(1_000L, 1, DHAROKS_ARMOUR_SET, 1, 7_400_000L),
            sell(2_000L, 2, DHAROKS_HELM, 1, 1_050_000L, 1_071_428),
            sell(3_000L, 3, DHAROKS_PLATEBODY, 1, 2_050_000L, 2_091_836),
            sell(4_000L, 4, DHAROKS_PLATELEGS, 1, 1_550_000L, 1_581_632),
            sell(5_000L, 5, DHAROKS_GREATAXE, 1, 3_050_000L, 3_112_244)
        );

        StatsFlipInstance activity =
            serviceFor(dharoksSetBreak()).buildHistory(deltas, null).get(DHAROKS_ARMOUR_SET).get(0);

        assertEquals(4, activity.conversionLines.size());
        long lines = 0L;
        for (ConversionMatch.Line line : activity.conversionLines) {
            lines += line.costGp;
        }
        assertEquals(activity.sellRevenueGp, lines);
        assertEquals(DHAROKS_HELM, activity.conversionLines.get(0).itemId);
        assertEquals(1_050_000L, activity.conversionLines.get(0).costGp);
    }

    @Test
    public void aBreakWaitsForItsLastPiece() {
        // Three pieces sold and one still in the bank. The set's profit is not
        // known yet - the last piece could go for anything - so no figure is
        // reported rather than a loss the activity is only partway through.
        // What is reported is that the break is open: an entry with no
        // numbers that counts for nothing, so a wrong guess can be refused.
        List<LocalTradeDelta> deltas = Arrays.asList(
            buy(1_000L, 1, DHAROKS_ARMOUR_SET, 1, 7_400_000L),
            sell(2_000L, 2, DHAROKS_HELM, 1, 1_050_000L, 1_071_428),
            sell(3_000L, 3, DHAROKS_PLATEBODY, 1, 2_050_000L, 2_091_836),
            sell(4_000L, 4, DHAROKS_PLATELEGS, 1, 1_550_000L, 1_581_632)
        );

        Map<Integer, List<StatsFlipInstance>> history =
            serviceFor(dharoksSetBreak()).buildHistory(deltas, null);

        List<StatsFlipInstance> activity = history.get(DHAROKS_ARMOUR_SET);
        assertEquals("an unfinished break is shown, once, on the set", 1, activity.size());
        StatsFlipInstance open = activity.get(0);
        assertTrue("and counts for nothing", open.openBreak && !open.counted());
        assertEquals(0L, open.profitGp);
        assertEquals(0L, open.buyCostGp);
        assertEquals(0L, open.sellRevenueGp);
        assertEquals(0, open.quantity);
        assertEquals(ConversionKind.SET_BREAK, open.conversionKind);
        // Placed at the latest piece sale, keyed on every piece sale so far.
        assertEquals(4_000L, open.completionTsMs);
        assertEquals(3, open.conversionTrades.size());
        List<StatsFlipInstance> helm = history.get(DHAROKS_HELM);
        assertTrue("a piece of it still reports nothing", helm == null || helm.isEmpty());
    }

    @Test
    public void breakingASetAtALossIsOneLossOnTheSet() {
        // The trade in the report: a blue dragonhide set bought for 15,000 and
        // broken into three pieces that sold for 8,101 between them. One set was
        // bought, so there is one activity, and sets carry a premium, so it is a
        // loss.
        ConversionRecipe setBreak = new ConversionRecipe(
            ConversionKind.SET_BREAK,
            "Blue dragonhide set",
            Collections.singletonList(new ConversionItem(BLUE_DHIDE_SET, 1)),
            Arrays.asList(
                new ConversionItem(BLUE_DHIDE_BODY, 1),
                new ConversionItem(BLUE_DHIDE_CHAPS, 1),
                new ConversionItem(BLUE_DHIDE_VAMBRACES, 1)),
            0L);
        List<LocalTradeDelta> deltas = Arrays.asList(
            buy(1_000L, 1, BLUE_DHIDE_SET, 1, 15_000L),
            sell(2_000L, 2, BLUE_DHIDE_BODY, 1, 4_900L, 5_000),
            sell(3_000L, 3, BLUE_DHIDE_VAMBRACES, 1, 1_261L, 1_286),
            sell(4_000L, 4, BLUE_DHIDE_CHAPS, 1, 1_940L, 1_979)
        );

        Map<Integer, List<StatsFlipInstance>> history =
            serviceFor(setBreak).buildHistory(deltas, null);

        List<StatsFlipInstance> activities = history.get(BLUE_DHIDE_SET);
        assertEquals("one set bought, one activity", 1, activities.size());
        StatsFlipInstance activity = activities.get(0);
        assertEquals(ConversionKind.SET_BREAK, activity.conversionKind);
        assertEquals(15_000L, activity.buyCostGp);
        assertEquals(8_101L, activity.sellRevenueGp);
        assertEquals(8_101L - 15_000L, activity.profitGp);
        assertEquals(1, activity.quantity);

        for (int pieceId : new int[]{BLUE_DHIDE_BODY, BLUE_DHIDE_CHAPS, BLUE_DHIDE_VAMBRACES}) {
            List<StatsFlipInstance> piece = history.get(pieceId);
            assertTrue("no card of its own for a piece", piece == null || piece.isEmpty());
        }
    }

    @Test
    public void takingAGodswordApartCreditsBothHalves() {
        // The other 1 -> N shape, and the one a flipper meets most often: a
        // godsword bought whole, taken apart, and the blade and the hilt sold
        // separately. Taking it apart is the trade, so the godsword is where it
        // is filed - never half on the blade and half on the hilt.
        ConversionRecipe disassemble = new ConversionRecipe(
            ConversionKind.DISASSEMBLE,
            "Armadyl godsword",
            Collections.singletonList(new ConversionItem(ARMADYL_GODSWORD, 1)),
            Arrays.asList(
                new ConversionItem(GODSWORD_BLADE, 1),
                new ConversionItem(ARMADYL_HILT, 1)),
            0L);
        List<LocalTradeDelta> deltas = Arrays.asList(
            buy(1_000L, 1, ARMADYL_GODSWORD, 1, 14_000_000L),
            sell(2_000L, 2, GODSWORD_BLADE, 1, 2_100_000L, 2_142_857),
            sell(3_000L, 3, ARMADYL_HILT, 1, 12_400_000L, 12_653_061)
        );

        Map<Integer, List<StatsFlipInstance>> history =
            serviceFor(disassemble).buildHistory(deltas, null);

        StatsFlipInstance activity = history.get(ARMADYL_GODSWORD).get(0);
        assertEquals(ConversionKind.DISASSEMBLE, activity.conversionKind);
        assertEquals(14_000_000L, activity.buyCostGp);
        assertEquals(14_500_000L, activity.sellRevenueGp);
        assertEquals(500_000L, activity.profitGp);
        assertEquals(2, activity.conversionLines.size());
        assertTrue(history.get(GODSWORD_BLADE) == null || history.get(GODSWORD_BLADE).isEmpty());
        assertTrue(history.get(ARMADYL_HILT) == null || history.get(ARMADYL_HILT).isEmpty());
    }

    @Test
    public void aSetBreakWithNoSetInStockDoesNotBlockTheRepairBesideIt() {
        // Every Barrows piece is produced by two routes: repaired from its
        // broken self, or broken out of a set. Two satisfiable routes must claim
        // neither - but a route with nothing to run it on is not a second
        // answer, and must not make the real one look ambiguous.
        LocalFlipHistoryService service = new LocalFlipHistoryService(new ConversionLedger(
            new ConversionRecipeIndex(Arrays.asList(dharoksHelmRepair(), dharoksSetBreak()))));
        List<LocalTradeDelta> deltas = Arrays.asList(
            buy(1_000L, 1, DHAROKS_HELM_0, 1, 800_000L),
            sell(2_000L, 2, DHAROKS_HELM, 1, 1_050_000L, 1_071_428)
        );

        StatsFlipInstance activity = service.buildHistory(deltas, null).get(DHAROKS_HELM).get(0);

        assertEquals(ConversionKind.REPAIR, activity.conversionKind);
        assertEquals(860_000L, activity.buyCostGp);
        assertEquals(190_000L, activity.profitGp);
    }

    @Test
    public void aHelmThatCouldHaveComeFromEitherRouteClaimsNeither() {
        // A broken helm and a set, both in stock, and a whole helm sold. Which
        // one the player used is unknowable, and either answer would move most
        // of a million gp onto the wrong item, so no activity is claimed at all.
        LocalFlipHistoryService service = new LocalFlipHistoryService(new ConversionLedger(
            new ConversionRecipeIndex(Arrays.asList(dharoksHelmRepair(), dharoksSetBreak()))));
        List<LocalTradeDelta> deltas = Arrays.asList(
            buy(1_000L, 1, DHAROKS_HELM_0, 1, 800_000L),
            buy(2_000L, 2, DHAROKS_ARMOUR_SET, 1, 7_400_000L),
            sell(3_000L, 3, DHAROKS_HELM, 1, 1_050_000L, 1_071_428)
        );

        List<StatsFlipInstance> helm = service.buildHistory(deltas, null).get(DHAROKS_HELM);

        assertTrue("two routes with stock for both claim neither",
            helm == null || helm.isEmpty());
    }

    private static ConversionRecipe dharoksHelmRepair() {
        return new ConversionRecipe(
            ConversionKind.REPAIR,
            "Dharok's helm",
            Collections.singletonList(new ConversionItem(DHAROKS_HELM_0, 1)),
            Collections.singletonList(new ConversionItem(DHAROKS_HELM, 1)),
            60_000L);
    }

    private static ConversionRecipe godswordBlade() {
        return new ConversionRecipe(
            ConversionKind.ASSEMBLE,
            "Godsword blade",
            Arrays.asList(
                new ConversionItem(GODSWORD_SHARD_1, 1),
                new ConversionItem(GODSWORD_SHARD_2, 1),
                new ConversionItem(GODSWORD_SHARD_3, 1)),
            Collections.singletonList(new ConversionItem(GODSWORD_BLADE, 1)),
            0L);
    }

    private static LocalTradeDelta fill(long tsMs, int slot, int itemId, int qty, long gp, int unitPrice) {
        return new LocalTradeDelta(tsMs, slot, itemId, false, qty, gp, "OFFER_UPDATED", unitPrice, false);
    }

    @Test
    public void anOfferThatSellsTwoAssembledUnitsOverTwoFillsIsStillAnAssemble() {
        // Six shards make two blades, sold as one offer that fills one at a
        // time. Each fill is explained by one run of the recipe and the offer
        // as a whole by two; the numbers were always right, but the activity
        // read as a plain flip, so the recipe filters missed it.
        List<LocalTradeDelta> deltas = Arrays.asList(
            buy(1_000L, 1, GODSWORD_SHARD_1, 2, 200_000L),
            buy(2_000L, 2, GODSWORD_SHARD_2, 2, 200_000L),
            buy(3_000L, 3, GODSWORD_SHARD_3, 2, 200_000L),
            fill(4_000L, 4, GODSWORD_BLADE, 1, 350_000L, 357_142),
            sell(5_000L, 4, GODSWORD_BLADE, 1, 350_000L, 357_142)
        );

        List<StatsFlipInstance> history = serviceFor(godswordBlade()).buildHistory(deltas, null).get(GODSWORD_BLADE);

        assertEquals(1, history.size());
        StatsFlipInstance activity = history.get(0);
        assertEquals(2, activity.quantity);
        assertEquals(600_000L, activity.buyCostGp);
        assertEquals(700_000L, activity.sellRevenueGp);
        assertEquals(100_000L, activity.profitGp);
        assertEquals(ConversionKind.ASSEMBLE, activity.conversionKind);
        assertEquals("Godsword blade", activity.conversionName);
        assertEquals(ConversionConfidence.CONFIRMED, activity.conversionConfidence);

        // One line per shard, each for both units, adding up to the cost basis.
        assertEquals(3, activity.conversionLines.size());
        long lineTotal = 0L;
        for (ConversionMatch.Line line : activity.conversionLines) {
            assertEquals(2L, line.quantity);
            lineTotal += line.costGp;
        }
        assertEquals(activity.buyCostGp, lineTotal);
    }

    @Test
    public void anOfferThatSellsOnlyPartOfAConversionIsNotLabelled() {
        // A chestplate becomes three components and one offer sells one of
        // them. The cost basis is a third of a chestplate, and no line can say
        // "one chestplate" over a third of its price - so no label, as before.
        ConversionRecipe disassemble = new ConversionRecipe(
            ConversionKind.DISASSEMBLE,
            "Break Bandos chestplate",
            Collections.singletonList(new ConversionItem(BANDOS_CHESTPLATE, 1)),
            Collections.singletonList(new ConversionItem(BANDOSIAN_COMPONENTS, 3)),
            0L);
        List<LocalTradeDelta> deltas = Arrays.asList(
            buy(1_000L, 1, BANDOS_CHESTPLATE, 1, 30_000_000L),
            sell(2_000L, 2, BANDOSIAN_COMPONENTS, 1, 11_000_000L, 11_224_489)
        );

        StatsFlipInstance activity =
            serviceFor(disassemble).buildHistory(deltas, null).get(BANDOSIAN_COMPONENTS).get(0);

        assertEquals(10_000_000L, activity.buyCostGp);
        assertNull("a third of a chestplate is not a chestplate", activity.conversionKind);
        assertEquals(0, activity.conversionLines.size());
    }

    @Test
    public void aPlainFlipAfterAnAssembleIsNotLabelledAssembled() {
        // Assemble one pair and sell it, then buy a pair outright and sell that.
        // The second sale is an ordinary flip and must say nothing about a recipe.
        List<LocalTradeDelta> deltas = Arrays.asList(
            buy(1_000L, 1, BANDOS_BOOTS, 1, 731_225L),
            buy(2_000L, 2, CORE, 1, 483_005L),
            sell(3_000L, 3, GUARDIAN_BOOTS, 1, 1_365_140L, 1_393_000),
            buy(4_000L, 4, GUARDIAN_BOOTS, 1, 2_325_000L),
            sell(5_000L, 5, GUARDIAN_BOOTS, 1, 2_450_000L, 2_500_000)
        );

        List<StatsFlipInstance> history = oldestFirst(service().buildHistory(deltas, null));

        assertEquals(2, history.size());
        assertEquals(ConversionKind.ASSEMBLE, history.get(0).conversionKind);
        assertEquals(1_214_230L, history.get(0).buyCostGp);

        StatsFlipInstance plainFlip = history.get(1);
        assertEquals(2_325_000L, plainFlip.buyCostGp);
        assertEquals(125_000L, plainFlip.profitGp);
        assertNull("a pair bought outright was not assembled", plainFlip.conversionKind);
        assertEquals(0, plainFlip.conversionLines.size());
    }

    @Test
    public void aSaleMixingBoughtAndMadeStockClaimsNeitherRecipe() {
        // One pair bought at 2,325,000 and one assembled for 1,214,230, sold
        // together. The cost basis is the blend of the two, so no breakdown can
        // honestly describe it - and a block that does not add up to the number
        // beside it is exactly the false attribution to avoid.
        List<LocalTradeDelta> deltas = Arrays.asList(
            buy(1_000L, 1, GUARDIAN_BOOTS, 1, 2_325_000L),
            buy(2_000L, 2, BANDOS_BOOTS, 1, 731_225L),
            buy(3_000L, 3, CORE, 1, 483_005L),
            sell(4_000L, 4, GUARDIAN_BOOTS, 2, 4_900_000L, 2_500_000)
        );

        Map<Integer, List<StatsFlipInstance>> history = service().buildHistory(deltas, null);
        StatsFlipInstance activity = history.get(GUARDIAN_BOOTS).get(0);

        assertEquals(2, activity.quantity);
        assertEquals(2_325_000L + 1_214_230L, activity.buyCostGp);
        assertNull("a blended cost basis cannot be explained by one recipe", activity.conversionKind);
    }
}
