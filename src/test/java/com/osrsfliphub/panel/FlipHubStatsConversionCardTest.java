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
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The words the expanded card puts in front of the player. Every heading is
 * spelled out rather than iconified, because the panel's font stack leads with
 * Inter and Inter has no hammer glyph to fall back from.
 */
public class FlipHubStatsConversionCardTest {
    private static final int GUARDIAN_BOOTS = 21733;

    private static StatsFlipInstance flip() {
        return new StatsFlipInstance(1042, 100L, 130L, 100L, 130L, 30L, 1, 1_000L);
    }

    private static StatsFlipInstance assembled() {
        ConversionRecipe recipe = new ConversionRecipe(
            ConversionKind.ASSEMBLE,
            "Guardian boots",
            Arrays.asList(new ConversionItem(21730, 1), new ConversionItem(11836, 1)),
            Collections.singletonList(new ConversionItem(GUARDIAN_BOOTS, 1)),
            0L
        );
        ConversionMatch match = new ConversionMatch(
            recipe,
            1L,
            1_214_230L,
            Arrays.asList(
                new ConversionMatch.Line(11836, 1L, 731_225L, false),
                new ConversionMatch.Line(21730, 1L, 483_005L, false)
            ),
            ConversionConfidence.CONFIRMED
        );
        return new StatsFlipInstance(
            GUARDIAN_BOOTS, 1_214_230L, 1_365_140L, 1_214_230L, 1_365_140L, 150_910L, 1, 3_000L, match);
    }

    /** A Dharok's set guessed broken, one helm sold so far: what the open break carries. */
    private static ConversionMatch unfinishedDharoksBreak() {
        ConversionRecipe recipe = new ConversionRecipe(
            ConversionKind.SET_BREAK,
            "Dharok's armour set",
            Collections.singletonList(new ConversionItem(12881, 1)),
            Arrays.asList(
                new ConversionItem(4716, 1), new ConversionItem(4720, 1),
                new ConversionItem(4722, 1), new ConversionItem(4718, 1)),
            0L
        );
        return new ConversionMatch(
            recipe, 1L, 7_400_000L,
            Collections.singletonList(new ConversionMatch.Line(4716, 1L, 1_050_000L, false)),
            ConversionConfidence.CONFIRMED,
            Collections.singletonList(new LocalTradeKey(2_000L, 2, 4716)),
            4242L);
    }

    @Test
    public void everyKindOfEntryIsCalledWhatItIs() {
        assertEquals("#3", FlipHubStatsItemCardBuilder.historyEntryLabel(flip(), 3));

        StatsFlipInstance filling = new StatsFlipInstance(1042, 100L, 130L, 100L, 130L, 30L, 1, 1_000L, null, true);
        assertEquals("In progress", FlipHubStatsItemCardBuilder.historyEntryLabel(filling, 3));

        ConversionRejection rejection = new ConversionRejection(GUARDIAN_BOOTS, "ASSEMBLE", "Guardian boots",
            3_000L, 9_000L, Collections.singletonList(new LocalTradeKey(3_000L, 3, GUARDIAN_BOOTS)));
        assertEquals("Dismissed",
            FlipHubStatsItemCardBuilder.historyEntryLabel(StatsFlipInstance.dismissed(rejection, 4242L), 3));

        // An unfinished break is not the next activity in the list and not a
        // zero: it is the guess the plugin cannot yet price, shown to be refused.
        StatsFlipInstance open = StatsFlipInstance.openBreak(12881, unfinishedDharoksBreak(), 2_000L);
        assertEquals("Unfinished", FlipHubStatsItemCardBuilder.historyEntryLabel(open, 3));
        assertEquals(ConversionKind.SET_BREAK, open.conversionKind);
        assertEquals(4242L, open.accountKey);
        assertEquals(1, open.conversionTrades.size());
        assertEquals(false, open.counted());
        assertEquals(0L, open.profitGp);
        assertEquals(0L, open.buyCostGp);
        assertEquals(0, open.quantity);
    }

    @Test
    public void aListOfPlainFlipsIsStillCalledFlipHistory() {
        List<StatsFlipInstance> history = Arrays.asList(flip(), flip());

        assertEquals("Flip history (2)", FlipHubStatsItemCardBuilder.historySectionTitle(history));
    }

    @Test
    public void oneConversionRenamesTheWholeSection() {
        // Mixed lists happen: an item bought and flipped once, assembled the
        // next time. "Flip history" is the wrong word for that list.
        List<StatsFlipInstance> history = Arrays.asList(flip(), assembled(), flip());

        assertEquals("Activity (3)", FlipHubStatsItemCardBuilder.historySectionTitle(history));
    }

    @Test
    public void everyDirectionHasWordsRatherThanAGlyph() {
        assertEquals("Assembled from", FlipHubStatsItemCardBuilder.conversionHeading(ConversionKind.ASSEMBLE));
        assertEquals("Repaired from", FlipHubStatsItemCardBuilder.conversionHeading(ConversionKind.REPAIR));
        assertEquals("Combined from", FlipHubStatsItemCardBuilder.conversionHeading(ConversionKind.SET_COMBINE));
        // The other way round: these are filed against the thing taken apart,
        // so the block lists what came out of it.
        assertEquals("Disassembled into", FlipHubStatsItemCardBuilder.conversionHeading(ConversionKind.DISASSEMBLE));
        assertEquals("Broken into", FlipHubStatsItemCardBuilder.conversionHeading(ConversionKind.SET_BREAK));
    }

    @Test
    public void anUnknownDirectionStillSaysSomethingTrue() {
        assertEquals("Made from", FlipHubStatsItemCardBuilder.conversionHeading(null));
    }

    @Test
    public void everyDirectionCanBeRefusedInItsOwnWords() {
        // The control withdraws a specific claim, so it repeats that claim:
        // "Not a recipe" would be true of all five and clear about none.
        assertEquals("Not assembled", FlipHubStatsItemCardBuilder.rejectLabel(ConversionKind.ASSEMBLE));
        assertEquals("Not repaired", FlipHubStatsItemCardBuilder.rejectLabel(ConversionKind.REPAIR));
        assertEquals("Not combined", FlipHubStatsItemCardBuilder.rejectLabel(ConversionKind.SET_COMBINE));
        assertEquals("Not disassembled", FlipHubStatsItemCardBuilder.rejectLabel(ConversionKind.DISASSEMBLE));
        assertEquals("Not broken up", FlipHubStatsItemCardBuilder.rejectLabel(ConversionKind.SET_BREAK));
        assertEquals("Not a recipe", FlipHubStatsItemCardBuilder.rejectLabel(null));
    }

    @Test
    public void aPlainFlipCarriesNoBreakdownForTheCardToDraw() {
        assertEquals(null, flip().conversionKind);
        assertEquals(0, flip().conversionLines.size());
    }

    @Test
    public void aWholeInputIsNamedWithNoPercentage() {
        assertEquals("NPC fee", FlipHubStatsItemCardBuilder.conversionLineLabel(
            new ConversionMatch.Line(0, 1L, 60_000L, true)));
        // No item name service in a unit test, so the id stands in for the name.
        // The shape of the label is what is being read here.
        assertEquals("Item 11836", FlipHubStatsItemCardBuilder.conversionLineLabel(
            new ConversionMatch.Line(11836, 1L, 731_225L, false)));
        assertEquals("Item 11836 x3", FlipHubStatsItemCardBuilder.conversionLineLabel(
            new ConversionMatch.Line(11836, 3L, 731_225L, false)));
    }

    @Test
    public void theBreakdownAddsUpToTheCostBasisTheCardShows() {
        StatsFlipInstance activity = assembled();

        long lines = 0L;
        for (ConversionMatch.Line line : activity.conversionLines) {
            lines += line.costGp;
        }
        assertEquals(activity.buyCostGp, lines);
    }
}
