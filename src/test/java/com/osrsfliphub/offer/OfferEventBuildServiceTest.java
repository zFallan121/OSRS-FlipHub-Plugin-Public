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

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OfferEventBuildServiceTest {
    @Test
    public void deriveIgnoresWhenOfferStaysEmpty() {
        OfferEventBuildService service = new OfferEventBuildService(new TestHooks());
        OfferSnapshot next = snapshot(0, offer(0, 0, 0, 0, 0, GrandExchangeOfferState.EMPTY), null);

        OfferEventBuildService.Result result = service.derive(
            new OfferEventBuildService.Input(null, next, null, false, false, 0L, 0)
        );

        assertTrue(result.shouldIgnore());
        assertTrue(result.shouldClearRecentSlot());
        assertNull(result.getEvent());
    }

    @Test
    public void deriveBuildsUpdatedEventFromProgressDelta() {
        OfferEventBuildService service = new OfferEventBuildService(new TestHooks());
        OfferSnapshot prev = snapshot(2, offer(560, 100, 10, 2, 200, GrandExchangeOfferState.BUYING), null);
        OfferSnapshot next = snapshot(2, offer(560, 100, 10, 5, 500, GrandExchangeOfferState.BUYING), prev);

        OfferEventBuildService.Result result = service.derive(
            new OfferEventBuildService.Input(prev, next, null, false, false, 0L, 301)
        );

        assertFalse(result.shouldIgnore());
        assertFalse(result.shouldClearRecentSlot());
        assertTrue(result.shouldScheduleRefresh());
        GeEvent event = result.getEvent();
        assertNotNull(event);
        assertEquals("OFFER_UPDATED", event.event_type);
        assertEquals(3, event.delta_qty);
        assertEquals(300L, event.delta_gp);
        assertEquals(Integer.valueOf(301), event.world);
    }

    @Test
    public void deriveNormalizesSellDeltaToPostTax() {
        OfferEventBuildService service = new OfferEventBuildService(new TestHooks());
        OfferSnapshot prev = snapshot(2, offer(1513, 1100, 10, 2, 2200, GrandExchangeOfferState.SELLING), null);
        OfferSnapshot next = snapshot(2, offer(1513, 1100, 10, 5, 5500, GrandExchangeOfferState.SELLING), prev);

        OfferEventBuildService.Result result = service.derive(
            new OfferEventBuildService.Input(prev, next, null, false, false, 0L, 301)
        );

        assertFalse(result.shouldIgnore());
        GeEvent event = result.getEvent();
        assertNotNull(event);
        assertEquals("OFFER_UPDATED", event.event_type);
        assertEquals(3, event.delta_qty);
        assertEquals(3234L, event.delta_gp);
    }

    @Test
    public void deriveNormalizesSellDeltaWhenObservedGrossDiffersFromListedPrice() {
        OfferEventBuildService service = new OfferEventBuildService(new TestHooks());
        OfferSnapshot prev = snapshot(2, offer(536, 564, 1, 0, 0, GrandExchangeOfferState.SELLING), null);
        OfferSnapshot next = snapshot(2, offer(536, 564, 1, 1, 563, GrandExchangeOfferState.SOLD), prev);

        OfferEventBuildService.Result result = service.derive(
            new OfferEventBuildService.Input(prev, next, null, false, false, 0L, 301)
        );

        assertFalse(result.shouldIgnore());
        GeEvent event = result.getEvent();
        assertNotNull(event);
        assertEquals("OFFER_COMPLETED", event.event_type);
        assertEquals(1, event.delta_qty);
        assertEquals(552L, event.delta_gp);
    }

    @Test
    public void deriveKeepsSellDeltaWhenAlreadyNetAndListedPriceDiffers() {
        OfferEventBuildService service = new OfferEventBuildService(new TestHooks());
        OfferSnapshot prev = snapshot(2, offer(536, 564, 1, 0, 0, GrandExchangeOfferState.SELLING), null);
        OfferSnapshot next = snapshot(2, offer(536, 564, 1, 1, 552, GrandExchangeOfferState.SOLD), prev);

        OfferEventBuildService.Result result = service.derive(
            new OfferEventBuildService.Input(prev, next, null, false, false, 0L, 301)
        );

        assertFalse(result.shouldIgnore());
        GeEvent event = result.getEvent();
        assertNotNull(event);
        assertEquals("OFFER_COMPLETED", event.event_type);
        assertEquals(1, event.delta_qty);
        assertEquals(552L, event.delta_gp);
    }

    @Test
    public void deriveNormalizesLargeSellVarianceUsingObservedGross() {
        OfferEventBuildService service = new OfferEventBuildService(new TestHooks());
        OfferSnapshot prev = snapshot(3, offer(25404, 162_513, 1, 0, 0, GrandExchangeOfferState.SELLING), null);
        OfferSnapshot next = snapshot(3, offer(25404, 162_513, 1, 1, 161_857, GrandExchangeOfferState.SOLD), prev);

        OfferEventBuildService.Result result = service.derive(
            new OfferEventBuildService.Input(prev, next, null, false, false, 0L, 301)
        );

        assertFalse(result.shouldIgnore());
        GeEvent event = result.getEvent();
        assertNotNull(event);
        assertEquals("OFFER_COMPLETED", event.event_type);
        assertEquals(1, event.delta_qty);
        assertEquals(158_620L, event.delta_gp);
    }

    @Test
    public void deriveTreatsEmptyTransitionAsCompletionMarkerWhenNoRemainingProgress() {
        OfferEventBuildService service = new OfferEventBuildService(new TestHooks());
        OfferSnapshot prev = snapshot(1, offer(561, 210, 10, 10, 2100, GrandExchangeOfferState.SELLING), null);
        OfferSnapshot next = snapshot(1, offer(0, 0, 0, 0, 0, GrandExchangeOfferState.EMPTY), prev);

        OfferEventBuildService.Result result = service.derive(
            new OfferEventBuildService.Input(prev, next, null, false, false, 0L, 302)
        );

        assertFalse(result.shouldIgnore());
        assertTrue(result.shouldClearRecentSlot());
        assertTrue(result.shouldScheduleRefresh());
        GeEvent event = result.getEvent();
        assertNotNull(event);
        assertEquals("OFFER_COMPLETED", event.event_type);
        assertEquals(0, event.delta_qty);
        assertEquals(0L, event.delta_gp);
    }

    @Test
    public void deriveTreatsStateCompletionAsMarkerWhenNoRemainingProgress() {
        OfferEventBuildService service = new OfferEventBuildService(new TestHooks());
        OfferSnapshot prev = snapshot(1, offer(561, 210, 10, 10, 2100, GrandExchangeOfferState.SELLING), null);
        OfferSnapshot next = snapshot(1, offer(561, 210, 10, 10, 2100, GrandExchangeOfferState.SOLD), prev);

        OfferEventBuildService.Result result = service.derive(
            new OfferEventBuildService.Input(prev, next, null, false, false, 0L, 302)
        );

        assertFalse(result.shouldIgnore());
        assertFalse(result.shouldClearRecentSlot());
        assertTrue(result.shouldScheduleRefresh());
        GeEvent event = result.getEvent();
        assertNotNull(event);
        assertEquals("OFFER_COMPLETED", event.event_type);
        assertEquals(0, event.delta_qty);
        assertEquals(0L, event.delta_gp);
    }

    @Test
    public void deriveIgnoresBaselineDeltaWhenLocalTradesAlreadyLoadedOutsideGrace() {
        TestHooks hooks = new TestHooks();
        hooks.withinLoginGrace = false;
        OfferEventBuildService service = new OfferEventBuildService(hooks);
        OfferSnapshot next = snapshot(3, offer(995, 100, 10, 1, 100, GrandExchangeOfferState.BUYING), null);

        OfferEventBuildService.Result result = service.derive(
            new OfferEventBuildService.Input(null, next, null, true, true, 0L, 0)
        );

        assertTrue(result.shouldIgnore());
        assertFalse(result.shouldClearRecentSlot());
        assertNull(result.getEvent());
    }

    @Test
    public void deriveMarksBaselineSyntheticInLoginGraceAndSetsTimestamp() {
        TestHooks hooks = new TestHooks();
        hooks.withinLoginGrace = true;
        hooks.nowMs = 12_345L;
        OfferEventBuildService service = new OfferEventBuildService(hooks);
        OfferSnapshot next = snapshot(4, offer(995, 100, 10, 1, 0, GrandExchangeOfferState.BUYING), null);

        OfferEventBuildService.Result result = service.derive(
            new OfferEventBuildService.Input(null, next, null, true, false, 0L, 0)
        );

        assertFalse(result.shouldIgnore());
        assertTrue(result.isBaselineSynthetic());
        GeEvent event = result.getEvent();
        assertNotNull(event);
        assertEquals("OFFER_PLACED", event.event_type);
        assertEquals(12_345L, event.ts_client_ms);
        assertEquals(100L, event.delta_gp);
    }

    @Test
    public void deriveConvertsPlacedToUpdatedWhenBaselineStampUsed() {
        TestHooks hooks = new TestHooks();
        hooks.stampMatchesSnapshot = true;
        OfferEventBuildService service = new OfferEventBuildService(hooks);
        OfferSnapshot next = snapshot(5, offer(4151, 1000, 10, 1, 1000, GrandExchangeOfferState.BUYING), null);
        OfferUpdateStamp stamp = new OfferUpdateStamp(4151, 1000, 10, 0, true, 0L, 1L, 1L, 0L, 0L);

        OfferEventBuildService.Result result = service.derive(
            new OfferEventBuildService.Input(null, next, stamp, true, false, 0L, 0)
        );

        assertFalse(result.shouldIgnore());
        assertFalse(result.isBaselineSynthetic());
        assertEquals("OFFER_UPDATED", result.getEvent().event_type);
    }

    @Test
    public void deriveUsesDeterministicCompletionEventIdWhenStampHasFirstSeen() {
        OfferEventBuildService service = new OfferEventBuildService(new TestHooks());
        OfferSnapshot prev = snapshot(1, offer(1513, 1100, 10, 9, 9900, GrandExchangeOfferState.SELLING), null);
        OfferSnapshot next = snapshot(1, offer(1513, 1100, 10, 10, 11000, GrandExchangeOfferState.SOLD), prev);
        OfferUpdateStamp stamp = new OfferUpdateStamp(1513, 1100, 10, 9, false, 9900L, 2_000L, 1_000L, 0L, 0L);

        OfferEventBuildService.Result first = service.derive(
            new OfferEventBuildService.Input(prev, next, stamp, false, false, 0L, 301)
        );
        OfferEventBuildService.Result second = service.derive(
            new OfferEventBuildService.Input(prev, next, stamp, false, false, 0L, 301)
        );

        assertNotNull(first.getEvent());
        assertNotNull(second.getEvent());
        assertEquals("OFFER_COMPLETED", first.getEvent().event_type);
        assertEquals(first.getEvent().event_id, second.getEvent().event_id);
    }

    @Test
    public void deriveCompletionEventIdDiffersForDifferentFirstSeenStamp() {
        OfferEventBuildService service = new OfferEventBuildService(new TestHooks());
        OfferSnapshot prev = snapshot(1, offer(1513, 1100, 10, 9, 9900, GrandExchangeOfferState.SELLING), null);
        OfferSnapshot next = snapshot(1, offer(1513, 1100, 10, 10, 11000, GrandExchangeOfferState.SOLD), prev);
        OfferUpdateStamp firstStamp = new OfferUpdateStamp(1513, 1100, 10, 9, false, 9900L, 2_000L, 1_000L, 0L, 0L);
        OfferUpdateStamp secondStamp = new OfferUpdateStamp(1513, 1100, 10, 9, false, 9900L, 2_000L, 5_000L, 0L, 0L);

        OfferEventBuildService.Result first = service.derive(
            new OfferEventBuildService.Input(prev, next, firstStamp, false, false, 0L, 301)
        );
        OfferEventBuildService.Result second = service.derive(
            new OfferEventBuildService.Input(prev, next, secondStamp, false, false, 0L, 301)
        );

        assertNotNull(first.getEvent());
        assertNotNull(second.getEvent());
        assertEquals("OFFER_COMPLETED", first.getEvent().event_type);
        assertEquals("OFFER_COMPLETED", second.getEvent().event_type);
        assertTrue(first.getEvent().event_id != null && second.getEvent().event_id != null);
        assertFalse(first.getEvent().event_id.equals(second.getEvent().event_id));
    }

    private static OfferSnapshot snapshot(int slot, GrandExchangeOffer offer, OfferSnapshot prev) {
        return OfferSnapshot.fromOffer(slot, offer, prev);
    }

    private static GrandExchangeOffer offer(int itemId,
                                            int price,
                                            int totalQty,
                                            int soldQty,
                                            int spent,
                                            GrandExchangeOfferState state) {
        return (GrandExchangeOffer) Proxy.newProxyInstance(
            GrandExchangeOffer.class.getClassLoader(),
            new Class<?>[] {GrandExchangeOffer.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getItemId":
                        return itemId;
                    case "getPrice":
                        return price;
                    case "getTotalQuantity":
                        return totalQty;
                    case "getQuantitySold":
                        return soldQty;
                    case "getSpent":
                        return spent;
                    case "getState":
                        return state;
                    default:
                        return defaultValue(method);
                }
            }
        );
    }

    private static Object defaultValue(Method method) {
        Class<?> returnType = method.getReturnType();
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return 0;
    }

    private static final class TestHooks implements OfferEventBuildService.Hooks {
        private boolean stampMatchesSnapshot;
        private long baselineTimestamp;
        private boolean withinLoginGrace;
        private boolean hasRecentLocalBuy;
        private long nowMs = System.currentTimeMillis();

        @Override
        public boolean stampMatchesSnapshot(OfferUpdateStamp stamp, OfferSnapshot snapshot) {
            return stampMatchesSnapshot;
        }

        @Override
        public long resolveBaselineTradeTimestamp(OfferUpdateStamp stamp, long lastLoginMs) {
            return baselineTimestamp;
        }

        @Override
        public boolean isWithinLoginGrace() {
            return withinLoginGrace;
        }

        @Override
        public boolean hasRecentLocalBuy(int itemId, long nowMs) {
            return hasRecentLocalBuy;
        }

        @Override
        public long nowMs() {
            return nowMs;
        }
    }
}
