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
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LocalFlipHistoryServiceTest {
    @Test
    public void buildsFlipInstancesForMatchedSells() {
        LocalFlipHistoryService service = new LocalFlipHistoryService();
        List<LocalTradeDelta> deltas = Arrays.asList(
            delta(1_000L, 1, 560, true, 10, 1_000L, "OFFER_UPDATED", 100, false),
            delta(2_000L, 1, 560, false, 4, 520L, "OFFER_UPDATED", 130, false),
            delta(2_100L, 1, 560, false, 0, 0L, "OFFER_COMPLETED", 130, false)
        );

        Map<Integer, List<StatsFlipInstance>> result = service.buildHistory(deltas, null);

        assertTrue(result.containsKey(560));
        assertEquals(1, result.get(560).size());
        StatsFlipInstance flip = result.get(560).get(0);
        assertEquals(560, flip.itemId);
        assertEquals(100L, flip.buyPriceGp);
        assertEquals(130L, flip.sellPriceGp);
        assertEquals(120L, flip.profitGp);
        assertEquals(4, flip.quantity);
        assertEquals(2_100L, flip.completionTsMs);
    }

    @Test
    public void rangeFilterIncludesInWindowSellsUsingOlderBuys() {
        LocalFlipHistoryService service = new LocalFlipHistoryService();
        List<LocalTradeDelta> deltas = Arrays.asList(
            delta(1_000L, 1, 561, true, 10, 1_000L, "OFFER_UPDATED", 100, false),
            delta(2_000L, 1, 561, false, 5, 700L, "OFFER_COMPLETED", 140, false),
            delta(5_000L, 1, 561, false, 5, 800L, "OFFER_COMPLETED", 160, false)
        );

        Map<Integer, List<StatsFlipInstance>> result = service.buildHistory(deltas, 4_000L);

        assertTrue(result.containsKey(561));
        assertEquals(1, result.get(561).size());
        StatsFlipInstance flip = result.get(561).get(0);
        assertEquals(100L, flip.buyPriceGp);
        assertEquals(160L, flip.sellPriceGp);
        assertEquals(300L, flip.profitGp);
        assertEquals(5, flip.quantity);
        assertEquals(5_000L, flip.completionTsMs);
    }

    @Test
    public void multipleSellUpdatesCollapseToSingleHistoryEntryOnCompletion() {
        LocalFlipHistoryService service = new LocalFlipHistoryService();
        List<LocalTradeDelta> deltas = Arrays.asList(
            delta(1_000L, 2, 6332, true, 10, 20_000L, "OFFER_UPDATED", 2_000, false),
            delta(2_000L, 2, 6332, false, 3, 6_300L, "OFFER_UPDATED", 2_100, false),
            delta(3_000L, 2, 6332, false, 4, 8_400L, "OFFER_UPDATED", 2_100, false),
            delta(4_000L, 2, 6332, false, 3, 6_300L, "OFFER_COMPLETED", 2_100, false)
        );

        Map<Integer, List<StatsFlipInstance>> result = service.buildHistory(deltas, null);

        assertTrue(result.containsKey(6332));
        assertEquals(1, result.get(6332).size());
        StatsFlipInstance flip = result.get(6332).get(0);
        assertEquals(10, flip.quantity);
        assertEquals(2_000L, flip.buyPriceGp);
        assertEquals(2_100L, flip.sellPriceGp);
        assertEquals(1_000L, flip.profitGp);
        assertEquals(4_000L, flip.completionTsMs);
    }

    @Test
    public void sellPriceUsesFloorDivisionToAvoidRoundingUp() {
        LocalFlipHistoryService service = new LocalFlipHistoryService();
        List<LocalTradeDelta> deltas = Arrays.asList(
            delta(1_000L, 3, 536, true, 2, 1_152L, "OFFER_UPDATED", 576, false),
            delta(2_000L, 3, 536, false, 2, 1_105L, "OFFER_UPDATED", 563, false),
            delta(2_100L, 3, 536, false, 0, 0L, "OFFER_COMPLETED", 563, false)
        );

        Map<Integer, List<StatsFlipInstance>> result = service.buildHistory(deltas, null);

        assertTrue(result.containsKey(536));
        assertEquals(1, result.get(536).size());
        StatsFlipInstance flip = result.get(536).get(0);
        assertEquals(576L, flip.buyPriceGp);
        assertEquals(552L, flip.sellPriceGp);
    }

    private static LocalTradeDelta delta(long tsClientMs, int slot, int itemId, boolean isBuy, int deltaQty,
                                         long deltaGp, String eventType, int price, boolean baselineSynthetic) {
        return new LocalTradeDelta(
            tsClientMs,
            slot,
            itemId,
            isBuy,
            deltaQty,
            deltaGp,
            eventType,
            price,
            baselineSynthetic
        );
    }
}
