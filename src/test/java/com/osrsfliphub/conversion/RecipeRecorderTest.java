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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The recording screen builds and opens without a plugin behind it.
 *
 * <p>Thin on purpose. What the screen works out is worked out by {@link RecipeFlipLedger}, which
 * is tested directly; what this covers is the half that only fails when it is drawn - a Swing
 * layout that throws on assembly, or a service call made before its null guard. The panel is
 * built once at construction and never rebuilt, so a break here is a break that ships.
 */
public class RecipeRecorderTest {
    @Test
    public void theScreenBuildsAndOpensWithNoPluginBehindIt() {
        RecipeRecorder recorder = new RecipeRecorder(new UiStyler(), new PanelValueFormat(), () -> {
        });

        assertNotNull(recorder.view());

        // Every service comes through Bridge, which hands back null outside the plugin. Opening
        // has to survive that, because it is also what a player sees before they have logged in.
        recorder.open();

        assertTrue(recorder.view().getViewport().getView().getPreferredSize().height > 0);
    }

    private static final int BLADE = 11690;
    private static final int HILT = 11810;
    private static final int GODSWORD = 11802;

    private static Delta trade(long tsMs, int slot, int itemId, boolean isBuy, int qty, String event) {
        return new Delta(tsMs, slot, itemId, isBuy, qty, qty * 1000L, event, 1000, false);
    }

    /**
     * An offer still filling is stored as one record per fill, and the completion replaces that
     * whole run with a single record. A conversion built on one of those fills would therefore
     * name a trade that stops existing the moment the offer finishes - so the screen does not
     * offer them, and this is the test that says so.
     */
    @Test
    public void anOfferStillFillingIsNotSomethingAConversionCanBeBuiltFrom() {
        Delta finished = trade(1_000L, 1, BLADE, true, 1, "OFFER_COMPLETED");
        Delta firstFill = trade(2_000L, 2, HILT, true, 1, "OFFER_UPDATED");
        Delta secondFill = trade(2_500L, 2, HILT, true, 1, "OFFER_UPDATED");

        List<Delta> offered = RecipeRecorder.offerable(
            Arrays.asList(finished, firstFill, secondFill), RecipeFlipLedger.empty());

        assertEquals(1, offered.size());
        assertEquals(BLADE, offered.get(0).itemId);
    }

    /** What an earlier record has already spoken for is not on offer to a second one. */
    @Test
    public void aTradeAnEarlierRecordAlreadyClaimedIsNotOfferedAgain() {
        Delta blades = trade(1_000L, 1, BLADE, true, 4, "OFFER_COMPLETED");
        Delta hilt = trade(2_000L, 2, HILT, true, 1, "OFFER_COMPLETED");
        Delta sale = trade(3_000L, 3, GODSWORD, false, 1, "OFFER_COMPLETED");
        List<Delta> deltas = Arrays.asList(blades, hilt, sale);
        RecipeFlip flip = new RecipeFlip(ConversionKind.ASSEMBLE, "Armadyl godsword",
            Arrays.asList(new RecipeFlip.Part(TradeKey.of(blades), 1),
                new RecipeFlip.Part(TradeKey.of(hilt), 1)),
            Collections.singletonList(new RecipeFlip.Part(TradeKey.of(sale), 1)), 0L, 10L);

        List<Delta> offered = RecipeRecorder.offerable(
            deltas, RecipeFlipLedger.apply(deltas, Collections.singletonList(flip)));

        assertEquals("the hilt and the sale are spoken for; three blades are not",
            1, offered.size());
        assertEquals(BLADE, offered.get(0).itemId);
        assertEquals(3, offered.get(0).deltaQty);
    }

    /** Newest first, because a conversion is usually recorded soon after the trades that made it. */
    @Test
    public void theNewestTradeIsOfferedFirst() {
        Delta older = trade(1_000L, 1, BLADE, true, 1, "OFFER_COMPLETED");
        Delta newer = trade(9_000L, 2, HILT, true, 1, "OFFER_COMPLETED");

        List<Delta> offered = RecipeRecorder.offerable(
            Arrays.asList(older, newer), RecipeFlipLedger.empty());

        assertEquals(HILT, offered.get(0).itemId);
        assertEquals(BLADE, offered.get(1).itemId);
    }

    /** Two stored records of the same trade would be one row the ledger cannot tell apart. */
    @Test
    public void oneRowPerStoredTrade() {
        Delta first = trade(1_000L, 1, BLADE, true, 1, "OFFER_COMPLETED");
        Delta repeat = trade(1_000L, 1, BLADE, true, 1, "OFFER_COMPLETED");

        assertEquals(1, RecipeRecorder.offerable(
            Arrays.asList(first, repeat), RecipeFlipLedger.empty()).size());
    }
}
