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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Where the history sync stores that it left off: when it ran, and the offers still in a slot
 * then. Offers it rules out are not compared with the rows the next sync finds new. Set too
 * late, a trade already recorded is imported a second time; so every doubt here resolves to an
 * earlier moment, a kept slot, or NONE, which compares everything.
 */
public class GeHistorySyncedSinceTest {
    private static final long SLACK_MS = Const.GE_HISTORY_SYNCED_SINCE_SLACK_MS;
    private static final long NOW = 2_000_000L;

    private static Stamp offer(long placedMs, long emptiedMs) {
        Stamp stamp = new Stamp();
        stamp.firstSeenMs = placedMs;
        stamp.lastEmptyMs = emptiedMs;
        return stamp;
    }

    private static Map<Integer, Long> open(Object... slotAndPlaced) {
        Map<Integer, Long> out = new TreeMap<>();
        for (int i = 0; i < slotAndPlaced.length; i += 2) {
            out.put((Integer) slotAndPlaced[i], ((Number) slotAndPlaced[i + 1]).longValue());
        }
        return out;
    }

    // ---- what is remembered ----

    @Test
    public void theOffersStillInASlotAreRememberedWithoutMovingTheMoment() {
        Map<Integer, Stamp> stamps = new HashMap<>();
        stamps.put(0, offer(500L, 0L));
        stamps.put(6, offer(4_000L, 0L));

        AutoSyncTradeMatcher.LastSync at = AutoSyncTradeMatcher.LastSync.at(9_000L, stamps);

        assertEquals(9_000L, at.ms);
        assertEquals(open(0, 500L, 6, 4_000L), at.openOffers);
    }

    @Test
    public void anEmptiedSlotIsNotAnOpenOfferAndOneNeverTimedKeepsItsWholeSlot() {
        Map<Integer, Stamp> stamps = new HashMap<>();
        stamps.put(2, offer(500L, 800L));
        stamps.put(3, offer(0L, 0L));

        assertEquals(open(3, 0L), AutoSyncTradeMatcher.LastSync.at(9_000L, stamps).openOffers);
    }

    // ---- stored and read back ----

    @Test
    public void whatIsStoredIsReadBackEveryMomentALittleEarlier() {
        String stored = new AutoSyncTradeMatcher.LastSync(1_000_000L, open(0, 500_000L, 6, 0L)).encode();

        AutoSyncTradeMatcher.LastSync read = AutoSyncTradeMatcher.LastSync.decode(stored, NOW);

        assertEquals(1_000_000L - SLACK_MS, read.ms);
        assertEquals(open(0, 500_000L - SLACK_MS, 6, 0L), read.openOffers);
    }

    @Test
    public void aMomentStoredOnItsOwnStillReads() {
        AutoSyncTradeMatcher.LastSync read = AutoSyncTradeMatcher.LastSync.decode("1000000", NOW);

        assertEquals(1_000_000L - SLACK_MS, read.ms);
        assertTrue(read.openOffers.isEmpty());
    }

    @Test
    public void nothingStoredOrAnythingUnreadableIsNone() {
        for (String raw : new String[] {null, "", "soon", "0", "-5", "5000,x:1", "5000,3", "5000,3:"}) {
            assertSame(raw, AutoSyncTradeMatcher.LastSync.NONE, AutoSyncTradeMatcher.LastSync.decode(raw, NOW));
        }
    }

    @Test
    public void aMomentLaterThanNowMeansTheClockMovedAndIsNotTrusted() {
        assertSame(AutoSyncTradeMatcher.LastSync.NONE, AutoSyncTradeMatcher.LastSync.decode("3000000", NOW));
    }

    @Test
    public void aMomentInsideTheSlackStillCounts() {
        assertEquals(1L, AutoSyncTradeMatcher.LastSync.decode("30000", NOW).ms);
    }

    // ---- what it rules out ----

    @Test
    public void aTradeThatEndedBeforeTheLastSyncIsLeftOut() {
        AutoSyncTradeMatcher.LastSync lastSync = new AutoSyncTradeMatcher.LastSync(9_000L, open(0, 500L));

        assertTrue(lastSync.predates(8_000L, 3, 7_000L));
        assertFalse(lastSync.predates(9_500L, 3, 7_000L));
    }

    @Test
    public void onlyTheTradesOfAnOfferStillInItsSlotAreKept() {
        AutoSyncTradeMatcher.LastSync lastSync = new AutoSyncTradeMatcher.LastSync(9_000L, open(0, 500L, 3, 0L));

        assertFalse(lastSync.predates(8_000L, 0, 600L));    // the open offer's own fill
        assertTrue(lastSync.predates(400L, 0, 100L));       // an earlier offer on that slot
        assertFalse(lastSync.predates(400L, 3, 100L));      // a slot whose offer was never timed keeps it all
    }

    @Test
    public void aTradeTheSyncImportedIsLeftOutThoughItsTimeIsInsideTheSlack() {
        AutoSyncTradeMatcher.LastSync lastSync = new AutoSyncTradeMatcher.LastSync(9_000L, open());

        assertTrue(lastSync.predates(9_500L, Const.GE_HISTORY_SYNTHETIC_SLOT_START, 9_400L));
        assertTrue(lastSync.predates(9_000L + SLACK_MS, Const.GE_HISTORY_SYNTHETIC_SLOT_START, 9_400L));
    }

    /**
     * The moment is RuneLite config: written minutes after the trades file, and kept per RuneLite
     * profile. A client killed soon after a sync comes back with the old moment and the old cursor
     * beside a file holding what that sync imported. Those rows are above the old cursor again.
     */
    @Test
    public void aTradeImportedAfterTheMomentWasByASyncItNeverHeardOfAndStaysComparable() {
        AutoSyncTradeMatcher.LastSync lastSync = new AutoSyncTradeMatcher.LastSync(9_000L, open());

        assertFalse(lastSync.predates(9_001L + SLACK_MS, Const.GE_HISTORY_SYNTHETIC_SLOT_START, 9_400L));
    }

    @Test
    public void knowingNothingLeavesNothingOut() {
        AutoSyncTradeMatcher.LastSync none = AutoSyncTradeMatcher.LastSync.NONE;

        assertFalse(none.predates(1L, 3, 1L));
        assertFalse(none.predates(1L, Const.GE_HISTORY_SYNTHETIC_SLOT_START, 1L));
    }

    // ---- when it applies ----

    @Test
    public void itOnlyAppliesWhenEveryRowIsKnownToBeNewerThanIt() {
        AutoSyncTradeMatcher.LastSync stored = new AutoSyncTradeMatcher.LastSync(7_000L, open());

        assertSame(stored, AutoSyncCoordinator.sinceFor(false, 42, 42, stored));
        // After a wipe a rollover hands over the whole list, old rows and all.
        assertSame(AutoSyncTradeMatcher.LastSync.NONE, AutoSyncCoordinator.sinceFor(true, 42, 42, stored));
        // More rows than the cursor covers: the overlap says nothing about the rest.
        assertSame(AutoSyncTradeMatcher.LastSync.NONE, AutoSyncCoordinator.sinceFor(false, 46, 45, stored));
    }

    @Test
    public void aSyncHasLostItsPlaceOnlyWhenNothingLinedUpAndNothingWasEligible() {
        assertTrue(AutoSyncCoordinator.lostPlace(0, 0));
        // Every row lined up: there really is nothing new.
        assertFalse(AutoSyncCoordinator.lostPlace(42, 0));
        // A rollover after a wipe: nothing lined up, but the rows are being imported.
        assertFalse(AutoSyncCoordinator.lostPlace(0, 42));
    }
}
