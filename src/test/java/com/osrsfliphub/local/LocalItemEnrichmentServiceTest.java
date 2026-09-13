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

public class LocalItemEnrichmentServiceTest {
    /**
     * The card used to take an exact two percent for the margin and a floored two percent for
     * ROI, neither of them capped. On a big-ticket item the uncapped tax swallowed most of the
     * margin, and the two numbers beside each other described different trades.
     */
    @Test
    public void marginOnABigTicketItemStopsAtTheFiveMillionTaxCap() {
        FlipHubItem item = itemPriced(4151, 1_400_000_000, 1_450_000_000);
        item.ge_limit_remaining = 8;

        new ItemEnrichment().applyMarginInfo(item);

        assertEquals(Integer.valueOf(45_000_000), item.margin);
        assertEquals(Long.valueOf(360_000_000L), item.margin_x_limit);
    }

    @Test
    public void marginAndRoiAgreeOnTheSameTax() {
        FlipHubItem item = itemPriced(561, 1_000, 1_049);

        new ItemEnrichment().applyMarginInfo(item);

        assertEquals(Integer.valueOf(29), item.margin);
        assertEquals(2.9d, item.roi_percent, 0.0001d);
    }

    /** Under fifty coins the tax rounds away, so the margin is the plain difference. */
    @Test
    public void aCheapItemIsNotTaxed() {
        FlipHubItem item = itemPriced(1511, 20, 45);

        new ItemEnrichment().applyMarginInfo(item);

        assertEquals(Integer.valueOf(25), item.margin);
    }

    private static FlipHubItem itemPriced(int itemId, int buy, int sell) {
        FlipHubItem item = new FlipHubItem();
        item.item_id = itemId;
        item.instabuy_price = buy;
        item.instasell_price = sell;
        return item;
    }
}
