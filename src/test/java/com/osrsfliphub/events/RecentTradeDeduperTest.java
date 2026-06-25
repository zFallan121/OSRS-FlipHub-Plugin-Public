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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RecentTradeDeduperTest {
    @Test
    public void suppressesStrictDuplicateEvent() {
        RecentTradeDeduper deduper = new RecentTradeDeduper(600L, 2_000L);
        GeEvent first = event("OFFER_UPDATED", 10_000L, 1, 4151, true, 100, 5, 500L);
        GeEvent duplicate = event("OFFER_UPDATED", 10_100L, 1, 4151, true, 100, 5, 500L);

        assertFalse(deduper.normalizeOrSuppress(first));
        assertTrue(deduper.normalizeOrSuppress(duplicate));
    }

    @Test
    public void normalizesCompletionAfterUpdateInsteadOfSuppressing() {
        RecentTradeDeduper deduper = new RecentTradeDeduper(600L, 2_000L);
        GeEvent update = event("OFFER_UPDATED", 20_000L, 2, 561, false, 220, 10, 2_156L);
        GeEvent completion = event("OFFER_COMPLETED", 20_200L, 2, 561, false, 220, 10, 2_156L);

        assertFalse(deduper.normalizeOrSuppress(update));
        assertFalse(deduper.normalizeOrSuppress(completion));
        assertEquals(0, completion.delta_qty);
        assertEquals(0L, completion.delta_gp);
    }

    @Test
    public void suppressesRepeatedCompletionEvent() {
        RecentTradeDeduper deduper = new RecentTradeDeduper(600L, 2_000L);
        GeEvent first = event("OFFER_COMPLETED", 30_000L, 3, 995, false, 200, 7, 1_372L);
        GeEvent duplicate = event("OFFER_COMPLETED", 30_300L, 3, 995, false, 200, 7, 1_372L);

        assertFalse(deduper.normalizeOrSuppress(first));
        assertTrue(deduper.normalizeOrSuppress(duplicate));
    }

    @Test
    public void clearSlotResetsDuplicateTracking() {
        RecentTradeDeduper deduper = new RecentTradeDeduper(600L, 2_000L);
        GeEvent first = event("OFFER_UPDATED", 40_000L, 4, 9075, true, 400, 3, 1_200L);
        GeEvent same = event("OFFER_UPDATED", 40_050L, 4, 9075, true, 400, 3, 1_200L);

        assertFalse(deduper.normalizeOrSuppress(first));
        assertTrue(deduper.normalizeOrSuppress(same));
        deduper.clearSlot(4);
        assertFalse(deduper.normalizeOrSuppress(event("OFFER_UPDATED", 40_100L, 4, 9075, true, 400, 3, 1_200L)));
    }

    private GeEvent event(
        String type,
        long tsClientMs,
        int slot,
        int itemId,
        boolean isBuy,
        int price,
        int deltaQty,
        long deltaGp
    ) {
        GeEvent event = new GeEvent();
        event.event_type = type;
        event.ts_client_ms = tsClientMs;
        event.slot = slot;
        event.item_id = itemId;
        event.is_buy = isBuy;
        event.price = price;
        event.delta_qty = deltaQty;
        event.delta_gp = deltaGp;
        return event;
    }
}
