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
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OfferUpdateStampServiceTest {
    @Test
    public void trackOfferUpdateCreatesStampForNewSnapshot() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 5_000L;
        OfferUpdateStampService service = new OfferUpdateStampService(hooks);
        Map<Integer, OfferUpdateStamp> stamps = new HashMap<>();
        OfferSnapshot next = snapshot(0, offer(560, 210, 100, 10, 0, GrandExchangeOfferState.BUYING), null);

        service.trackOfferUpdate(stamps, 0, null, next);

        OfferUpdateStamp stamp = stamps.get(0);
        assertTrue(stamp != null);
        assertEquals(560, stamp.itemId);
        assertEquals(5_000L, stamp.lastUpdateMs);
        assertEquals(1, hooks.persistCalls);
    }

    @Test
    public void trackOfferUpdateClearsStampWhenOfferBecomesEmptyAfterCompletion() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 6_000L;
        OfferUpdateStampService service = new OfferUpdateStampService(hooks);
        Map<Integer, OfferUpdateStamp> stamps = new HashMap<>();
        stamps.put(0, new OfferUpdateStamp(560, 210, 100, 10, true, 2_100L, 4_000L, 4_000L, 0L, 0L));

        OfferSnapshot prev = snapshot(0, offer(560, 210, 100, 100, 21_000, GrandExchangeOfferState.SOLD), null);
        OfferSnapshot next = snapshot(0, offer(0, 0, 0, 0, 0, GrandExchangeOfferState.EMPTY), prev);
        service.trackOfferUpdate(stamps, 0, prev, next);

        assertTrue(!stamps.containsKey(0));
        assertEquals(1, hooks.persistCalls);
    }

    @Test
    public void trackOfferUpdatePreservesStampForPartialCancelledOfferWhenSlotBecomesEmpty() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 7_000L;
        hooks.withinLoginGrace = true;
        OfferUpdateStampService service = new OfferUpdateStampService(hooks);
        Map<Integer, OfferUpdateStamp> stamps = new HashMap<>();
        stamps.put(0, new OfferUpdateStamp(1513, 1_100, 70_000, 64_000, false, 70_000_000L, 4_000L, 4_000L, 0L, 0L));

        OfferSnapshot prev = snapshot(
            0,
            offer(1513, 1_100, 70_000, 64_000, 70_000_000, GrandExchangeOfferState.CANCELLED_SELL),
            null
        );
        OfferSnapshot next = snapshot(0, offer(0, 0, 0, 0, 0, GrandExchangeOfferState.EMPTY), prev);

        service.trackOfferUpdate(stamps, 0, prev, next);

        OfferUpdateStamp stamp = stamps.get(0);
        assertTrue(stamp != null);
        assertEquals(7_000L, stamp.lastEmptyMs);
    }

    @Test
    public void trackOfferUpdateClearsStampForPartialCancelledOfferOutsideLoginGrace() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 7_500L;
        hooks.withinLoginGrace = false;
        OfferUpdateStampService service = new OfferUpdateStampService(hooks);
        Map<Integer, OfferUpdateStamp> stamps = new HashMap<>();
        stamps.put(0, new OfferUpdateStamp(1513, 1_100, 70_000, 64_000, false, 70_000_000L, 4_000L, 4_000L, 0L, 0L));

        OfferSnapshot prev = snapshot(
            0,
            offer(1513, 1_100, 70_000, 64_000, 70_000_000, GrandExchangeOfferState.CANCELLED_SELL),
            null
        );
        OfferSnapshot next = snapshot(0, offer(0, 0, 0, 0, 0, GrandExchangeOfferState.EMPTY), prev);

        service.trackOfferUpdate(stamps, 0, prev, next);

        assertTrue(!stamps.containsKey(0));
    }

    @Test
    public void trackOfferUpdatePreservesStampForPartialCompletedStateWhenSlotBecomesEmpty() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 8_000L;
        hooks.withinLoginGrace = true;
        OfferUpdateStampService service = new OfferUpdateStampService(hooks);
        Map<Integer, OfferUpdateStamp> stamps = new HashMap<>();
        stamps.put(0, new OfferUpdateStamp(4740, 108, 11_000, 1_971, true, 212_868L, 5_000L, 5_000L, 0L, 0L));

        OfferSnapshot prev = snapshot(0, offer(4740, 108, 11_000, 1_971, 212_868, GrandExchangeOfferState.BOUGHT), null);
        OfferSnapshot next = snapshot(0, offer(0, 0, 0, 0, 0, GrandExchangeOfferState.EMPTY), prev);

        service.trackOfferUpdate(stamps, 0, prev, next);

        OfferUpdateStamp stamp = stamps.get(0);
        assertTrue(stamp != null);
        assertEquals(8_000L, stamp.lastEmptyMs);
    }

    @Test
    public void trackOfferUpdateClearsStampForPartialCompletedStateOutsideLoginGrace() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 8_500L;
        hooks.withinLoginGrace = false;
        OfferUpdateStampService service = new OfferUpdateStampService(hooks);
        Map<Integer, OfferUpdateStamp> stamps = new HashMap<>();
        stamps.put(0, new OfferUpdateStamp(4740, 108, 11_000, 1_971, true, 212_868L, 5_000L, 5_000L, 0L, 0L));

        OfferSnapshot prev = snapshot(0, offer(4740, 108, 11_000, 1_971, 212_868, GrandExchangeOfferState.BOUGHT), null);
        OfferSnapshot next = snapshot(0, offer(0, 0, 0, 0, 0, GrandExchangeOfferState.EMPTY), prev);

        service.trackOfferUpdate(stamps, 0, prev, next);

        assertTrue(!stamps.containsKey(0));
    }

    @Test
    public void trackOfferUpdateDoesNotMarkCompletedForPartialBoughtState() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 8_750L;
        hooks.withinLoginGrace = true;
        OfferUpdateStampService service = new OfferUpdateStampService(hooks);
        Map<Integer, OfferUpdateStamp> stamps = new HashMap<>();
        OfferUpdateStamp original = new OfferUpdateStamp(4740, 108, 11_000, 1_970, true, 212_760L, 5_000L, 5_000L, 0L, 0L);
        stamps.put(0, original);

        OfferSnapshot prev = snapshot(0, offer(4740, 108, 11_000, 1_970, 212_760, GrandExchangeOfferState.BUYING), null);
        OfferSnapshot next = snapshot(0, offer(4740, 108, 11_000, 1_971, 212_868, GrandExchangeOfferState.BOUGHT), prev);

        service.trackOfferUpdate(stamps, 0, prev, next);

        OfferUpdateStamp stamp = stamps.get(0);
        assertTrue(stamp != null);
        assertEquals(0L, stamp.completedMs);
    }

    @Test
    public void getOfferLastUpdateMsDoesNotMarkCompletedForPartialSoldState() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 8_760L;
        hooks.withinLoginGrace = true;
        OfferUpdateStampService service = new OfferUpdateStampService(hooks);
        Map<Integer, OfferUpdateStamp> stamps = new HashMap<>();

        long updatedMs = service.getOfferLastUpdateMs(
            stamps,
            0,
            offer(4740, 108, 11_000, 1_971, 212_868, GrandExchangeOfferState.SOLD)
        );

        OfferUpdateStamp stamp = stamps.get(0);
        assertEquals(8_760L, updatedMs);
        assertTrue(stamp != null);
        assertEquals(0L, stamp.completedMs);
    }

    @Test
    public void trackOfferUpdateCreatesNewStampForEmptySlotReuseOutsideLoginGrace() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 9_000L;
        hooks.withinLoginGrace = false;
        OfferUpdateStampService service = new OfferUpdateStampService(hooks);
        Map<Integer, OfferUpdateStamp> stamps = new HashMap<>();
        OfferUpdateStamp original = new OfferUpdateStamp(1513, 1_100, 70_000, 0, false, 0L, 4_000L, 4_000L, 0L, 7_000L);
        stamps.put(0, original);

        OfferSnapshot prev = snapshot(0, offer(0, 0, 0, 0, 0, GrandExchangeOfferState.EMPTY), null);
        OfferSnapshot next = snapshot(0, offer(1513, 1_105, 5_000, 0, 0, GrandExchangeOfferState.SELLING), prev);

        service.trackOfferUpdate(stamps, 0, prev, next);

        OfferUpdateStamp stamp = stamps.get(0);
        assertTrue(stamp != null);
        assertTrue(stamp != original);
        assertEquals(9_000L, stamp.lastUpdateMs);
        assertEquals(9_000L, stamp.firstSeenMs);
    }

    @Test
    public void trackOfferUpdateCreatesNewStampForEmptySlotReuseDuringLoginGraceWithoutRecordedEmpty() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 9_100L;
        hooks.withinLoginGrace = true;
        OfferUpdateStampService service = new OfferUpdateStampService(hooks);
        Map<Integer, OfferUpdateStamp> stamps = new HashMap<>();
        OfferUpdateStamp original = new OfferUpdateStamp(1513, 1_100, 70_000, 0, false, 0L, 4_000L, 4_000L, 0L, 0L);
        stamps.put(0, original);

        OfferSnapshot prev = snapshot(0, offer(0, 0, 0, 0, 0, GrandExchangeOfferState.EMPTY), null);
        OfferSnapshot next = snapshot(0, offer(1513, 1_100, 70_000, 0, 0, GrandExchangeOfferState.SELLING), prev);

        service.trackOfferUpdate(stamps, 0, prev, next);

        OfferUpdateStamp stamp = stamps.get(0);
        assertTrue(stamp != null);
        assertTrue(stamp != original);
        assertEquals(9_100L, stamp.lastUpdateMs);
        assertEquals(9_100L, stamp.firstSeenMs);
    }

    @Test
    public void getOfferLastUpdateMsCreatesNewStampForEmptySlotReuseOutsideLoginGrace() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 9_500L;
        hooks.withinLoginGrace = false;
        OfferUpdateStampService service = new OfferUpdateStampService(hooks);
        Map<Integer, OfferUpdateStamp> stamps = new HashMap<>();
        OfferUpdateStamp original = new OfferUpdateStamp(1513, 1_100, 70_000, 0, false, 0L, 4_000L, 4_000L, 0L, 7_000L);
        stamps.put(0, original);

        long updatedMs = service.getOfferLastUpdateMs(
            stamps,
            0,
            offer(1513, 1_105, 5_000, 0, 0, GrandExchangeOfferState.SELLING)
        );

        OfferUpdateStamp stamp = stamps.get(0);
        assertEquals(9_500L, updatedMs);
        assertTrue(stamp != null);
        assertTrue(stamp != original);
        assertEquals(9_500L, stamp.lastUpdateMs);
        assertEquals(9_500L, stamp.firstSeenMs);
    }

    @Test
    public void getOfferLastUpdateMsPreservesStampForEmptySlotReuseDuringLoginGrace() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 9_750L;
        hooks.withinLoginGrace = true;
        OfferUpdateStampService service = new OfferUpdateStampService(hooks);
        Map<Integer, OfferUpdateStamp> stamps = new HashMap<>();
        OfferUpdateStamp original = new OfferUpdateStamp(1513, 1_100, 70_000, 0, false, 0L, 4_000L, 4_000L, 0L, 7_000L);
        stamps.put(0, original);

        long updatedMs = service.getOfferLastUpdateMs(
            stamps,
            0,
            offer(1513, 1_100, 70_000, 0, 0, GrandExchangeOfferState.SELLING)
        );

        OfferUpdateStamp stamp = stamps.get(0);
        assertEquals(4_000L, updatedMs);
        assertTrue(stamp == original);
        assertEquals(4_000L, stamp.lastUpdateMs);
    }

    @Test
    public void trackOfferUpdateCreatesNewStampWhenOfferSideChangesDuringLoginGrace() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 9_900L;
        hooks.withinLoginGrace = true;
        OfferUpdateStampService service = new OfferUpdateStampService(hooks);
        Map<Integer, OfferUpdateStamp> stamps = new HashMap<>();
        OfferUpdateStamp original = new OfferUpdateStamp(1513, 1_100, 70_000, 5_000, true, 5_500_000L, 4_000L, 4_000L, 0L, 0L);
        stamps.put(0, original);

        OfferSnapshot next = snapshot(0, offer(1513, 1_100, 70_000, 0, 0, GrandExchangeOfferState.SELLING), null);

        service.trackOfferUpdate(stamps, 0, null, next);

        OfferUpdateStamp stamp = stamps.get(0);
        assertTrue(stamp != null);
        assertTrue(stamp != original);
        assertTrue(!stamp.isBuy);
        assertEquals(9_900L, stamp.lastUpdateMs);
        assertEquals(9_900L, stamp.firstSeenMs);
    }

    @Test
    public void getOfferLastUpdateMsCreatesNewStampWhenOfferSideChangesDuringLoginGrace() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 9_950L;
        hooks.withinLoginGrace = true;
        OfferUpdateStampService service = new OfferUpdateStampService(hooks);
        Map<Integer, OfferUpdateStamp> stamps = new HashMap<>();
        OfferUpdateStamp original = new OfferUpdateStamp(1513, 1_100, 70_000, 5_000, true, 5_500_000L, 4_000L, 4_000L, 0L, 0L);
        stamps.put(0, original);

        long updatedMs = service.getOfferLastUpdateMs(
            stamps,
            0,
            offer(1513, 1_100, 70_000, 0, 0, GrandExchangeOfferState.SELLING)
        );

        OfferUpdateStamp stamp = stamps.get(0);
        assertEquals(9_950L, updatedMs);
        assertTrue(stamp != null);
        assertTrue(stamp != original);
        assertTrue(!stamp.isBuy);
        assertEquals(9_950L, stamp.lastUpdateMs);
        assertEquals(9_950L, stamp.firstSeenMs);
    }

    @Test
    public void trackOfferUpdateCreatesNewStampWhenMetadataDiffersDuringLoginGrace() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 9_975L;
        hooks.withinLoginGrace = true;
        OfferUpdateStampService service = new OfferUpdateStampService(hooks);
        Map<Integer, OfferUpdateStamp> stamps = new HashMap<>();
        OfferUpdateStamp original = new OfferUpdateStamp(1513, 1_100, 70_000, 5_000, true, 5_500_000L, 4_000L, 4_000L, 0L, 0L);
        stamps.put(0, original);

        OfferSnapshot next = snapshot(0, offer(1513, 1_150, 5_000, 0, 0, GrandExchangeOfferState.BUYING), null);

        service.trackOfferUpdate(stamps, 0, null, next);

        OfferUpdateStamp stamp = stamps.get(0);
        assertTrue(stamp != null);
        assertTrue(stamp != original);
        assertTrue(stamp.isBuy);
        assertEquals(1_150, stamp.price);
        assertEquals(5_000, stamp.totalQty);
        assertEquals(9_975L, stamp.lastUpdateMs);
        assertEquals(9_975L, stamp.firstSeenMs);
    }

    @Test
    public void getOfferLastUpdateMsCreatesNewStampWhenMetadataDiffersDuringLoginGrace() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 9_980L;
        hooks.withinLoginGrace = true;
        OfferUpdateStampService service = new OfferUpdateStampService(hooks);
        Map<Integer, OfferUpdateStamp> stamps = new HashMap<>();
        OfferUpdateStamp original = new OfferUpdateStamp(1513, 1_100, 70_000, 5_000, true, 5_500_000L, 4_000L, 4_000L, 0L, 0L);
        stamps.put(0, original);

        long updatedMs = service.getOfferLastUpdateMs(
            stamps,
            0,
            offer(1513, 1_150, 5_000, 0, 0, GrandExchangeOfferState.BUYING)
        );

        OfferUpdateStamp stamp = stamps.get(0);
        assertEquals(9_980L, updatedMs);
        assertTrue(stamp != null);
        assertTrue(stamp != original);
        assertTrue(stamp.isBuy);
        assertEquals(1_150, stamp.price);
        assertEquals(5_000, stamp.totalQty);
        assertEquals(9_980L, stamp.lastUpdateMs);
        assertEquals(9_980L, stamp.firstSeenMs);
    }

    @Test
    public void getOfferLastUpdateMsPreservesStampWhenMetadataIncompleteDuringLoginGrace() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 9_985L;
        hooks.withinLoginGrace = true;
        OfferUpdateStampService service = new OfferUpdateStampService(hooks);
        Map<Integer, OfferUpdateStamp> stamps = new HashMap<>();
        OfferUpdateStamp original = new OfferUpdateStamp(1513, 1_100, 70_000, 5_000, true, 5_500_000L, 4_000L, 4_000L, 0L, 0L);
        stamps.put(0, original);

        long updatedMs = service.getOfferLastUpdateMs(
            stamps,
            0,
            offer(1513, 0, 0, 0, 0, GrandExchangeOfferState.BUYING)
        );

        OfferUpdateStamp stamp = stamps.get(0);
        assertEquals(4_000L, updatedMs);
        assertTrue(stamp == original);
        assertEquals(4_000L, stamp.lastUpdateMs);
        assertEquals(1_100, stamp.price);
        assertEquals(70_000, stamp.totalQty);
    }

    @Test
    public void trackOfferUpdateCreatesNewStampWhenProgressRegressesOutsideLoginGrace() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 9_990L;
        hooks.withinLoginGrace = false;
        OfferUpdateStampService service = new OfferUpdateStampService(hooks);
        Map<Integer, OfferUpdateStamp> stamps = new HashMap<>();
        OfferUpdateStamp original = new OfferUpdateStamp(1513, 1_100, 70_000, 5_000, true, 5_500_000L, 4_000L, 4_000L, 0L, 0L);
        stamps.put(0, original);

        OfferSnapshot next = snapshot(0, offer(1513, 1_100, 70_000, 1_000, 1_100_000, GrandExchangeOfferState.BUYING), null);

        service.trackOfferUpdate(stamps, 0, null, next);

        OfferUpdateStamp stamp = stamps.get(0);
        assertTrue(stamp != null);
        assertTrue(stamp != original);
        assertEquals(1_000, stamp.filledQty);
        assertEquals(1_100_000L, stamp.spentGp);
        assertEquals(9_990L, stamp.lastUpdateMs);
        assertEquals(9_990L, stamp.firstSeenMs);
    }

    @Test
    public void getOfferLastUpdateMsCreatesNewStampWhenProgressRegressesOutsideLoginGrace() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 9_995L;
        hooks.withinLoginGrace = false;
        OfferUpdateStampService service = new OfferUpdateStampService(hooks);
        Map<Integer, OfferUpdateStamp> stamps = new HashMap<>();
        OfferUpdateStamp original = new OfferUpdateStamp(1513, 1_100, 70_000, 5_000, true, 5_500_000L, 4_000L, 4_000L, 0L, 0L);
        stamps.put(0, original);

        long updatedMs = service.getOfferLastUpdateMs(
            stamps,
            0,
            offer(1513, 1_100, 70_000, 1_000, 1_100_000, GrandExchangeOfferState.BUYING)
        );

        OfferUpdateStamp stamp = stamps.get(0);
        assertEquals(9_995L, updatedMs);
        assertTrue(stamp != null);
        assertTrue(stamp != original);
        assertEquals(1_000, stamp.filledQty);
        assertEquals(1_100_000L, stamp.spentGp);
        assertEquals(9_995L, stamp.lastUpdateMs);
        assertEquals(9_995L, stamp.firstSeenMs);
    }

    @Test
    public void trackOfferUpdateCreatesNewStampWhenProgressDropsToZeroOutsideLoginGrace() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 9_996L;
        hooks.withinLoginGrace = false;
        OfferUpdateStampService service = new OfferUpdateStampService(hooks);
        Map<Integer, OfferUpdateStamp> stamps = new HashMap<>();
        OfferUpdateStamp original = new OfferUpdateStamp(1513, 1_100, 70_000, 5_000, true, 5_500_000L, 4_000L, 4_000L, 0L, 0L);
        stamps.put(0, original);

        OfferSnapshot next = snapshot(0, offer(1513, 1_100, 70_000, 0, 0, GrandExchangeOfferState.BUYING), null);

        service.trackOfferUpdate(stamps, 0, null, next);

        OfferUpdateStamp stamp = stamps.get(0);
        assertTrue(stamp != null);
        assertTrue(stamp != original);
        assertEquals(0, stamp.filledQty);
        assertEquals(0L, stamp.spentGp);
        assertEquals(9_996L, stamp.lastUpdateMs);
        assertEquals(9_996L, stamp.firstSeenMs);
    }

    @Test
    public void getOfferLastUpdateMsCreatesNewStampWhenProgressDropsToZeroOutsideLoginGrace() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 9_997L;
        hooks.withinLoginGrace = false;
        OfferUpdateStampService service = new OfferUpdateStampService(hooks);
        Map<Integer, OfferUpdateStamp> stamps = new HashMap<>();
        OfferUpdateStamp original = new OfferUpdateStamp(1513, 1_100, 70_000, 5_000, true, 5_500_000L, 4_000L, 4_000L, 0L, 0L);
        stamps.put(0, original);

        long updatedMs = service.getOfferLastUpdateMs(
            stamps,
            0,
            offer(1513, 1_100, 70_000, 0, 0, GrandExchangeOfferState.BUYING)
        );

        OfferUpdateStamp stamp = stamps.get(0);
        assertEquals(9_997L, updatedMs);
        assertTrue(stamp != null);
        assertTrue(stamp != original);
        assertEquals(0, stamp.filledQty);
        assertEquals(0L, stamp.spentGp);
        assertEquals(9_997L, stamp.lastUpdateMs);
        assertEquals(9_997L, stamp.firstSeenMs);
    }

    @Test
    public void getOfferLastUpdateMsPreservesStampWhenProgressDropsToZeroDuringLoginGrace() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 9_998L;
        hooks.withinLoginGrace = true;
        OfferUpdateStampService service = new OfferUpdateStampService(hooks);
        Map<Integer, OfferUpdateStamp> stamps = new HashMap<>();
        OfferUpdateStamp original = new OfferUpdateStamp(1513, 1_100, 70_000, 5_000, true, 5_500_000L, 4_000L, 4_000L, 0L, 0L);
        stamps.put(0, original);

        long updatedMs = service.getOfferLastUpdateMs(
            stamps,
            0,
            offer(1513, 1_100, 70_000, 0, 0, GrandExchangeOfferState.BUYING)
        );

        OfferUpdateStamp stamp = stamps.get(0);
        assertEquals(4_000L, updatedMs);
        assertTrue(stamp == original);
        assertEquals(5_000, stamp.filledQty);
        assertEquals(5_500_000L, stamp.spentGp);
        assertEquals(4_000L, stamp.lastUpdateMs);
    }

    @Test
    public void getOfferLastUpdateMsReturnsCompletionDisplayTimestampForCompletedOffer() {
        TestHooks hooks = new TestHooks();
        hooks.nowMs = 10_000L;
        OfferUpdateStampService service = new OfferUpdateStampService(hooks);
        Map<Integer, OfferUpdateStamp> stamps = new HashMap<>();
        GrandExchangeOffer offer = offer(560, 210, 100, 100, 21_000, GrandExchangeOfferState.SOLD);

        long updatedMs = service.getOfferLastUpdateMs(stamps, 0, offer);

        assertEquals(10_000L, updatedMs);
        assertTrue(stamps.containsKey(0));
        assertEquals(1, hooks.persistCalls);
    }

    @Test
    public void resolveBaselineTradeTimestampReturnsStampTimeOnlyWhenBeforeLogin() {
        OfferUpdateStampService service = new OfferUpdateStampService(new TestHooks());
        OfferUpdateStamp stamp = new OfferUpdateStamp(560, 210, 100, 10, true, 2_100L, 2_000L, 1_000L, 0L, 0L);

        assertEquals(1_000L, service.resolveBaselineTradeTimestamp(stamp, 3_000L));
        assertEquals(0L, service.resolveBaselineTradeTimestamp(stamp, 500L));
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

    private static final class TestHooks implements OfferUpdateStampService.Hooks {
        private long nowMs = System.currentTimeMillis();
        private boolean withinLoginGrace;
        private int persistCalls;

        @Override
        public long nowMs() {
            return nowMs;
        }

        @Override
        public boolean isWithinLoginGrace() {
            return withinLoginGrace;
        }

        @Override
        public void persistOfferUpdateTimes() {
            persistCalls++;
        }
    }
}
