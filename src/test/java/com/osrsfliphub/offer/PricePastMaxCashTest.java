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
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.osrsfliphub;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * One item can trade above 2,147,483,647 since 30 Sep 2026 (the 3rd age axe at 8,351,000,000).
 * Where the price goes whole, and where it is capped on purpose.
 */
public class PricePastMaxCashTest {
    private static final long AXE = 8_351_000_000L;

    /** The saved slot position as the builds the hub served before this one declare it: an int price. */
    static final class OldStamp {
        int itemId;
        int price;
        int totalQty;
        int filledQty;
        boolean isBuy;
        long spentGp;
        long lastUpdateMs;
        long firstSeenMs;
        long completedMs;
        long lastEmptyMs;
    }

    private static OfferSnapshot offer(int slot, long price, int filled, long spent, String state) {
        return new OfferSnapshot(slot, 12426, price, 1, filled, spent, state, false);
    }

    /** What is uploaded as it happens carries the whole price. */
    @Test
    public void theUploadedEventCarriesThePriceWhole() {
        GeEvent event = GeEvent.createBase(offer(3, AXE, 1, AXE, "SOLD"), offer(3, AXE, 0, 0L, "SELLING"), "OFFER_COMPLETED");

        assertEquals(AXE, event.price);
    }

    /**
     * Two RuneLite windows on one PC, one still on an older build, share the saved slot positions.
     * The older build reads all eight in one go and treats a price it cannot hold as "none saved":
     * every slot's position gone, and every fill made while logged out then adopted uncounted.
     * So what is saved must stay readable as an int.
     */
    @Test
    public void anOlderBuildStillReadsEverySavedSlotPosition() {
        Gson gson = new Gson();
        Map<Integer, Stamp> stamps = new HashMap<>();
        for (int slot = 0; slot < 8; slot++) {
            stamps.put(slot, Stamp.fromSnapshot(new OfferSnapshot(slot, 4151, 1_000_000L + slot, 10, 3, 3_000_000L, "BUYING", true), 1_000L));
        }
        stamps.put(3, Stamp.fromSnapshot(offer(3, AXE, 0, 0L, "SELLING"), 1_000L));
        String saved = OfferUpdateStampStore.serialize(stamps, gson);

        Map<String, OldStamp> older;
        try {
            older = gson.fromJson(saved, new TypeToken<Map<String, OldStamp>>() {}.getType());
        } catch (JsonParseException ex) {
            older = new HashMap<>();
        }

        assertEquals(8, older.size());
        assertEquals(Integer.MAX_VALUE, older.get("3").price);
    }

    /** Saved and read back, the position still belongs to the offer it was saved for, and to no other. */
    @Test
    public void aSavedPositionStillMatchesItsOfferAfterARestart() {
        Gson gson = new Gson();
        Map<Integer, Stamp> stamps = new HashMap<>();
        stamps.put(3, Stamp.fromSnapshot(offer(3, AXE, 0, 0L, "SELLING"), 1_000L));
        Stamp read = OfferUpdateStampStore.parse(OfferUpdateStampStore.serialize(stamps, gson), gson, 0, 7).get(3);
        OfferUpdateStampRuleEvaluator rules = new OfferUpdateStampRuleEvaluator(() -> 2_000L, () -> false);

        assertTrue(rules.stampMatches(read, offer(3, AXE, 0, 0L, "SELLING")));
        assertFalse(rules.stampMatches(read, offer(3, 2_000_000_000L, 0, 0L, "SELLING")));
    }

    /**
     * A stored trade holds its price as an int, capped, and is sent later from the file at the cap,
     * which the website reads as a price it does not know. A price worked out from the coins would
     * have to match the offer's live fills exactly, and does not for a purchase that filled under
     * its price: 2,100,000,000 paid on a 2,200,000,000 listing, whose live fills carry 2,200,000,000.
     */
    @Test
    public void aStoredTradePastMaxCashIsSentLaterAtTheCap() {
        Delta underItsPrice = new Delta(5_000L, 1, 12426, true, 1, 2_100_000_000L, "OFFER_COMPLETED", Integer.MAX_VALUE,
            false);
        Delta bought = new Delta(5_000L, 1, 12426, true, 1, AXE, "OFFER_COMPLETED", Integer.MAX_VALUE, false);
        Delta sold = new Delta(6_000L, 1, 12426, false, 1, AXE - GeTax.MAX_TAX_PER_ITEM, "OFFER_COMPLETED", Integer.MAX_VALUE, false);

        assertEquals(Integer.MAX_VALUE, RecordSync.record(777L, underItsPrice, 301).price);
        assertEquals(Integer.MAX_VALUE, RecordSync.record(777L, bought, 301).price);
        GeEvent sale = RecordSync.record(777L, sold, 301);
        assertEquals(Integer.MAX_VALUE, sale.price);
        assertEquals("the coins as stored, whole", AXE - GeTax.MAX_TAX_PER_ITEM, sale.delta_gp);
    }

    /** A stored price below the cap is the price, whatever the coins say (a buy can fill under its price). */
    @Test
    public void aStoredPriceBelowTheCapIsSentAsStored() {
        Delta bought = new Delta(5_000L, 1, 4151, true, 10, 9_990L, "OFFER_COMPLETED", 1_000, false);

        assertEquals(1_000L, RecordSync.record(777L, bought, 301).price);
    }
}
