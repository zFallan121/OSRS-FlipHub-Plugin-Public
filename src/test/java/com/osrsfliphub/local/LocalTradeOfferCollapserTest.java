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
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/** The rule for which fills are one offer's, on the live path and on the load path. */
public class LocalTradeOfferCollapserTest {
    private static final int SLOT = 2;
    private static final int ITEM = 4151;
    private static final int PRICE = 100;

    // ---- live: append ----

    @Test
    public void aCompletionFoldsItsOffersFillsIntoOneRecordTimedAtTheFirstFill() {
        List<Delta> stored = new ArrayList<>();
        stored.add(fill(1_000L, SLOT, ITEM, 3, 7L));
        stored.add(fill(2_000L, SLOT, ITEM, 4, 7L));
        stored.add(fill(3_000L, SLOT, ITEM, 3, 7L));

        TradeOfferCollapser.Outcome outcome =
            TradeOfferCollapser.append(stored, completion(3_600L, SLOT, ITEM, 0, 7L));

        assertEquals(TradeOfferCollapser.Outcome.COLLAPSED, outcome);
        assertEquals(1, stored.size());
        Delta offer = stored.get(0);
        assertEquals("OFFER_COMPLETED", offer.eventType);
        assertEquals(10, offer.deltaQty);
        assertEquals(1_000L, offer.deltaGp);
        assertEquals(1_000L, offer.tsClientMs);
        assertEquals(3_600L, offer.endMs);
        assertEquals(7L, offer.offerStartMs);
    }

    @Test
    public void aCompletionCarryingItsOwnRemainderAddsItToTheFills() {
        List<Delta> stored = new ArrayList<>();
        stored.add(fill(1_000L, SLOT, ITEM, 4, 7L));

        TradeOfferCollapser.append(stored, completion(2_000L, SLOT, ITEM, 6, 7L));

        assertEquals(1, stored.size());
        assertEquals(10, stored.get(0).deltaQty);
        assertEquals(1_000L, stored.get(0).deltaGp);
    }

    @Test
    public void aCompletionWithNoFillsBeforeItIsStoredAsTheWholeOffer() {
        List<Delta> stored = new ArrayList<>();

        TradeOfferCollapser.Outcome outcome =
            TradeOfferCollapser.append(stored, completion(2_000L, SLOT, ITEM, 10, 7L));

        assertEquals(TradeOfferCollapser.Outcome.APPENDED, outcome);
        assertEquals(1, stored.size());
        assertEquals(2_000L, stored.get(0).endMs);
    }

    @Test
    public void theCollectAfterACollapsedOfferIsNotStored() {
        List<Delta> stored = new ArrayList<>();
        stored.add(fill(1_000L, SLOT, ITEM, 10, 7L));
        TradeOfferCollapser.append(stored, completion(1_600L, SLOT, ITEM, 0, 7L));

        TradeOfferCollapser.Outcome outcome =
            TradeOfferCollapser.append(stored, completion(60_000L, SLOT, ITEM, 0, 0L));

        assertEquals(TradeOfferCollapser.Outcome.DROPPED, outcome);
        assertEquals(1, stored.size());
        assertEquals(1_600L, stored.get(0).endMs);
    }

    /** Fills of other slots sit between an offer's fills and never get in the way. */
    @Test
    public void fillsOnOtherSlotsAreLeftWhereTheyAre() {
        List<Delta> stored = new ArrayList<>();
        stored.add(fill(1_000L, SLOT, ITEM, 3, 7L));
        Delta other = fill(1_500L, 5, 560, 20, 9L);
        stored.add(other);
        stored.add(fill(2_000L, SLOT, ITEM, 7, 7L));

        TradeOfferCollapser.append(stored, completion(2_600L, SLOT, ITEM, 0, 7L));

        assertEquals(2, stored.size());
        assertSame(other, stored.get(0));
        assertEquals(10, stored.get(1).deltaQty);
    }

    // ---- the boundary ----

    @Test
    public void aSlotsPreviousOfferIsNeverFoldedIntoTheNext() {
        List<Delta> stored = new ArrayList<>();
        stored.add(fill(1_000L, SLOT, ITEM, 5, 7L));
        TradeOfferCollapser.append(stored, completion(1_600L, SLOT, ITEM, 0, 7L));
        // Same slot, same item, same price: the next offer, placed later.
        stored.add(fill(5_000L, SLOT, ITEM, 8, 11L));

        TradeOfferCollapser.append(stored, completion(5_600L, SLOT, ITEM, 0, 11L));

        assertEquals(2, stored.size());
        assertEquals(5, stored.get(0).deltaQty);
        assertEquals(8, stored.get(1).deltaQty);
        assertEquals(5_000L, stored.get(1).tsClientMs);
    }

    /**
     * The previous offer on the slot never got a completion (cancelled, or finished while
     * nothing was watching). Its fills are the same item at the same price, and only the
     * offer start tells them from the new offer's.
     */
    @Test
    public void twoOffersOfOneItemAtOnePriceOnOneSlotAreToldApartByWhenTheyWerePlaced() {
        List<Delta> stored = new ArrayList<>();
        stored.add(fill(1_000L, SLOT, ITEM, 5, 7L));
        stored.add(fill(2_000L, SLOT, ITEM, 5, 7L));
        stored.add(fill(9_000L, SLOT, ITEM, 8, 11L));

        TradeOfferCollapser.append(stored, completion(9_600L, SLOT, ITEM, 2, 11L));

        assertEquals(3, stored.size());
        assertEquals(5, stored.get(0).deltaQty);
        assertEquals(5, stored.get(1).deltaQty);
        Delta offer = stored.get(2);
        assertEquals(10, offer.deltaQty);
        assertEquals(9_000L, offer.tsClientMs);
        assertEquals(11L, offer.offerStartMs);
    }

    @Test
    public void aDifferentItemOrPriceOnTheSlotIsADifferentOffer() {
        List<Delta> stored = new ArrayList<>();
        stored.add(fill(1_000L, SLOT, ITEM, 5, 0L));
        stored.add(fill(2_000L, SLOT, 560, 5, 0L));
        stored.add(new Delta(3_000L, SLOT, ITEM, true, 5, 5L * 101L, "OFFER_UPDATED", 101, false));
        stored.add(fill(4_000L, SLOT, ITEM, 4, 0L));

        TradeOfferCollapser.append(stored, completion(4_600L, SLOT, ITEM, 0, 0L));

        assertEquals(4, stored.size());
        assertEquals(4, stored.get(3).deltaQty);
        assertEquals(4_000L, stored.get(3).tsClientMs);
    }

    /** Fills written before the start was tracked carry none, and match on the rest. */
    @Test
    public void fillsWithoutAStartAreMatchedOnSlotItemSideAndPrice() {
        List<Delta> stored = new ArrayList<>();
        stored.add(fill(1_000L, SLOT, ITEM, 6, 0L));
        stored.add(fill(2_000L, SLOT, ITEM, 4, 7L));

        TradeOfferCollapser.append(stored, completion(2_600L, SLOT, ITEM, 0, 7L));

        assertEquals(1, stored.size());
        assertEquals(10, stored.get(0).deltaQty);
        assertEquals(7L, stored.get(0).offerStartMs);
    }

    // ---- load: collapse ----

    @Test
    public void collapseFoldsEachCompletedOfferAndDropsItsCollect() {
        List<Delta> loaded = Arrays.asList(
            fill(1_000L, 0, ITEM, 2, 0L),
            fill(1_000L + 60_000L, 0, ITEM, 8, 0L),
            completion(1_000L + 60_600L, 0, ITEM, 0, 0L),
            completion(1_000L + 120_000L, 0, ITEM, 0, 0L),
            sellFill(200_000L, 3, ITEM, 10, 0L),
            completion(200_600L, 3, ITEM, 0, 0L)
        );

        List<Delta> collapsed = TradeOfferCollapser.collapse(loaded);

        assertEquals(2, collapsed.size());
        Delta buy = collapsed.get(0);
        assertEquals(10, buy.deltaQty);
        assertEquals(1_000L, buy.tsClientMs);
        assertEquals(1_000L + 60_600L, buy.endMs);
        Delta sell = collapsed.get(1);
        assertEquals("OFFER_COMPLETED", sell.eventType);
        assertEquals(10, sell.deltaQty);
        assertEquals(200_600L, sell.endMs);
    }

    @Test
    public void collapseKeepsAnOfferStillFillingFillByFill() {
        Delta first = fill(1_000L, 0, ITEM, 2, 0L);
        Delta second = fill(2_000L, 0, ITEM, 3, 0L);

        List<Delta> collapsed = TradeOfferCollapser.collapse(Arrays.asList(first, second));

        assertEquals(2, collapsed.size());
        assertSame(first, collapsed.get(0));
        assertSame(second, collapsed.get(1));
    }

    @Test
    public void collapseFoldsARunTheSlotMovedOnFromIntoOneUpdate() {
        List<Delta> loaded = Arrays.asList(
            fill(1_000L, 0, ITEM, 2, 0L),
            fill(2_000L, 0, ITEM, 3, 0L),
            // Never completed; the slot then holds another item.
            fill(9_000L, 0, 560, 4, 0L),
            completion(9_600L, 0, 560, 0, 0L)
        );

        List<Delta> collapsed = TradeOfferCollapser.collapse(loaded);

        assertEquals(2, collapsed.size());
        Delta abandoned = collapsed.get(0);
        assertEquals("OFFER_UPDATED", abandoned.eventType);
        assertEquals(5, abandoned.deltaQty);
        assertEquals(1_000L, abandoned.tsClientMs);
        // It ended at its last fill: a sale among such fills is booked then.
        assertEquals(2_000L, abandoned.endMs);
        assertEquals(4, collapsed.get(1).deltaQty);
    }

    @Test
    public void collapseDropsACompletionThatClosesNothingAndCarriesNothing() {
        List<Delta> loaded = Arrays.asList(
            completion(1_000L, 0, ITEM, 0, 0L),
            fill(2_000L, 1, ITEM, 5, 0L)
        );

        List<Delta> collapsed = TradeOfferCollapser.collapse(loaded);

        assertEquals(1, collapsed.size());
        assertEquals(5, collapsed.get(0).deltaQty);
    }

    @Test
    public void collapseLeavesARecordAlreadyCollapsedAlone() {
        Delta offer = new Delta(1_000L, 0, ITEM, true, 10, 1_000L, "OFFER_COMPLETED", PRICE,
            false, 900L, 1_600L);

        List<Delta> collapsed = TradeOfferCollapser.collapse(Arrays.asList(offer));

        assertEquals(1, collapsed.size());
        assertSame(offer, collapsed.get(0));
    }

    // ---- helpers ----

    private static Delta fill(long ts, int slot, int itemId, int qty, long offerStartMs) {
        return new Delta(ts, slot, itemId, true, qty, (long) qty * PRICE, "OFFER_UPDATED", PRICE, false,
            offerStartMs, 0L);
    }

    private static Delta sellFill(long ts, int slot, int itemId, int qty, long offerStartMs) {
        return new Delta(ts, slot, itemId, false, qty, (long) qty * 128L, "OFFER_UPDATED", 130, false,
            offerStartMs, 0L);
    }

    private static Delta completion(long ts, int slot, int itemId, int qty, long offerStartMs) {
        int price = slot == 3 ? 130 : PRICE;
        boolean isBuy = slot != 3;
        long gp = isBuy ? (long) qty * price : (long) qty * 128L;
        return new Delta(ts, slot, itemId, isBuy, qty, gp, "OFFER_COMPLETED", price, false,
            offerStartMs, 0L);
    }
}
