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

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.osrsfliphub.RecordSyncWorld.bought;
import static com.osrsfliphub.RecordSyncWorld.id;
import static com.osrsfliphub.RecordSyncWorld.ids;
import static com.osrsfliphub.RecordSyncWorld.now;
import static com.osrsfliphub.RecordSyncWorld.records;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Stored trades are sent to the website until it confirms it holds them ({@link RecordSync}).
 *
 * <p>Everything here runs the plugin's real services over real files in a temporary folder
 * ({@link RecordSyncWorld}): what a test asserts about a file, it reads back from the disk.
 */
public class RecordSyncTest {
    private static final long MAIN = 4242L;
    private static final long ALT = 777L;
    private static final long OTHER_ALT = 888L;
    private static final long MINUTE = 60_000L;

    private RecordSyncWorld world;

    @Before
    public void setUp() throws Exception {
        world = RecordSyncWorld.fresh();
    }

    @After
    public void tearDown() throws Exception {
        RecordSyncWorld.close(world);
    }

    // ---- what is sent ----

    /** The fields the website's validator and its record rule read, as the two sides agreed them. */
    @Test
    public void aStoredTradeIsSentAsTheRecordOfAFinishedOffer() {
        long first = now() - 30 * MINUTE;
        Delta sale = new Delta(first, 3, RecordSyncWorld.WHIP, false, 10, 1_176_000L, "OFFER_COMPLETED", 120_000, false,
            first - 9_000L, first + 4 * MINUTE);
        world.store(MAIN, sale);

        List<GeEvent> sent = records(world.login(MAIN).settle());

        assertEquals(1, sent.size());
        GeEvent event = sent.get(0);
        assertEquals("OFFER_RECORD", event.event_type);
        assertEquals("the record's first fill", first, event.ts_client_ms);
        assertEquals("when the offer ended, not its last fill and not its first", Long.valueOf(first + 4 * MINUTE), event.end_ms);
        assertEquals(3, event.slot);
        assertEquals(RecordSyncWorld.WHIP, event.item_id);
        assertFalse(event.is_buy);
        assertEquals("the listed price", 120_000L, event.price);
        assertEquals(10, event.delta_qty);
        assertEquals(10, event.total_qty);
        assertEquals(10, event.filled_qty);
        assertEquals("a sale's coins are net of tax, as stored", 1_176_000L, event.delta_gp);
        assertEquals(1_176_000L, event.spent_gp);
        assertEquals("SOLD", event.state);
        assertEquals("the character the record is filed under", GeEvent.characterId(MAIN), event.character_id);
        assertEquals("record", event.source);
        assertEquals(Integer.valueOf(301), event.world);
        assertEquals(1, event.schema_version);
        assertTrue(new Gson().toJson(event).contains("\"end_ms\":" + (first + 4 * MINUTE)));
    }

    /**
     * The world is game state, read on the game thread only. Records are built on the upload's
     * thread, so they carry the world as the game thread last saw it and never ask the game.
     */
    @Test
    public void theWorldIsTheOneTheGameThreadLastSaw() {
        world.store(MAIN, bought(30 * MINUTE, 1, 10), bought(20 * MINUTE, 2, 5));
        world.store(ALT, bought(40 * MINUTE, 1, 3));
        world.login(MAIN);
        world.worldAsked.set(0);

        List<GeEvent> sent = records(world.settle());
        world.sync().waiting();

        assertEquals(3, sent.size());
        for (GeEvent event : sent) {
            assertEquals(Integer.valueOf(301), event.world);
        }
        assertEquals("the game was not asked off its own thread", 0, world.worldAsked.get());
    }

    /**
     * The id is made from the record and from nothing else, so the same record is the same id every
     * time it is sent, from any window, after any restart: the website keeps it on what it stores.
     */
    @Test
    public void theIdIsTheRecordsOwnAndTheSameEveryTime() {
        long first = now() - 30 * MINUTE;
        Delta purchase = new Delta(first, 2, RecordSyncWorld.WHIP, true, 10, 1_000_000L, "OFFER_COMPLETED", 100_000,
            false, first - 9_000L, first + MINUTE);
        world.store(MAIN, purchase);
        String expected = UUID.nameUUIDFromBytes((MAIN + "|" + first + "|2|" + RecordSyncWorld.WHIP
            + "|true|10|1000000|100000|OFFER_COMPLETED").getBytes(StandardCharsets.UTF_8)).toString();
        // A website that confirms nothing, so the record is sent again.
        world.website.answer = events -> world.website.take(events, ids(events));

        GeEvent once = records(world.login(MAIN).settle()).get(0);
        world.waitOutThePause();
        GeEvent twice = records(world.settle()).get(0);
        GeEvent afterARestart = records(world.again().login(MAIN).settle()).get(0);

        assertEquals(expected, once.event_id);
        assertEquals(expected, twice.event_id);
        assertEquals(expected, afterARestart.event_id);
        assertEquals("a name-based id can be made again; a random one cannot", 3, UUID.fromString(expected).version());
    }

    /**
     * A fill the slot has since moved on from has no end of its own (an older file is full of
     * them). It is an offer that is over, so it is sent, ending where it began, which the website
     * reads as "no end".
     */
    @Test
    public void aFillTheSlotMovedOnFromIsSentEndingWhereItBegan() {
        long first = now() - 30 * MINUTE;
        // Then another offer on the same slot, at another price: the first is over.
        world.store(MAIN,
            new Delta(first, 2, RecordSyncWorld.WHIP, true, 4, 400_000L, "OFFER_UPDATED", 100_000, false),
            new Delta(first + 5 * MINUTE, 2, RecordSyncWorld.WHIP, true, 7, 630_000L, "OFFER_COMPLETED", 90_000, false,
                first + 4 * MINUTE, first + 6 * MINUTE));

        List<GeEvent> sent = records(world.login(MAIN).settle());

        assertEquals(2, sent.size());
        GeEvent event = sent.get(0);
        assertEquals(4, event.delta_qty);
        assertEquals(Long.valueOf(first), event.end_ms);
        assertEquals(first, event.ts_client_ms);
        assertEquals("BOUGHT", event.state);
    }

    /**
     * While an offer is filling its fills are stored one by one, and folded into one record when
     * it ends. Sent as they stand, a fill would be judged, and perhaps stored, ahead of the live
     * fills still to come. Only an offer that has ended is sent, however long ago its fills were.
     */
    @Test
    public void theFillsOfAnOfferStillOpenAreNotSent() {
        long first = now() - 30 * MINUTE;
        Delta fill = new Delta(first, 2, RecordSyncWorld.WHIP, true, 4, 400_000L, "OFFER_UPDATED", 100_000, false,
            first - 5_000L, 0L);
        Delta more = new Delta(first + MINUTE, 2, RecordSyncWorld.WHIP, true, 3, 300_000L, "OFFER_UPDATED", 100_000,
            false, first - 5_000L, 0L);
        Delta theirs = new Delta(first, 5, RecordSyncWorld.WHIP, false, 2, 196_000L, "OFFER_UPDATED", 100_000, false,
            first - 9_000L, 0L);
        world.store(MAIN, bought(40 * MINUTE, 2, 1), fill, more);
        world.store(ALT, theirs);
        world.login(MAIN);
        // Each slot's saved position is still the offer those fills belong to.
        world.slotHolds(MAIN, 2, RecordSyncWorld.positionOf(fill));
        world.slotHolds(ALT, 5, RecordSyncWorld.positionOf(theirs));

        List<GeEvent> sent = records(world.settle());

        assertEquals("the finished offer before them, and nothing of the open ones", 1, sent.size());
        assertEquals(1, sent.get(0).delta_qty);
        assertEquals(0L, world.stored(MAIN).get(1).uploadedMs);
    }

    /**
     * An offer that finished or was cancelled while nothing was watching leaves its fills in the
     * file with no end of their own. What says the offer is over is its slot: once the saved
     * position is another offer, or the slot has been seen empty, or there is no position at all,
     * the fills are sent. For the logged-in character that is the live position; for any other,
     * the one its own window last saved.
     */
    @Test
    public void aFillWhoseSlotHasMovedOnIsSent() {
        long first = now() - 30 * MINUTE;
        Delta mine = new Delta(first, 2, RecordSyncWorld.WHIP, true, 4, 400_000L, "OFFER_UPDATED", 100_000, false,
            first - 5_000L, 0L);
        Delta emptied = new Delta(first, 3, RecordSyncWorld.WHIP, true, 5, 500_000L, "OFFER_UPDATED", 100_000, false,
            first - 5_000L, 0L);
        Delta theirs = new Delta(first, 5, RecordSyncWorld.WHIP, false, 2, 196_000L, "OFFER_UPDATED", 100_000, false,
            first - 9_000L, 0L);
        Delta noPosition = new Delta(first, 6, RecordSyncWorld.WHIP, false, 3, 294_000L, "OFFER_UPDATED", 100_000, false,
            first - 9_000L, 0L);
        world.store(MAIN, mine, emptied);
        world.store(ALT, theirs, noPosition);
        world.login(MAIN);
        // The slot holds an offer of the same item at the same price, placed later: another offer.
        Stamp later = RecordSyncWorld.positionOf(mine);
        later.firstSeenMs = first + 10 * MINUTE;
        world.slotHolds(MAIN, 2, later);
        Stamp seenEmpty = RecordSyncWorld.positionOf(emptied);
        seenEmpty.lastEmptyMs = first + 10 * MINUTE;
        world.slotHolds(MAIN, 3, seenEmpty);
        Stamp otherItem = RecordSyncWorld.positionOf(theirs);
        otherItem.itemId = 385;
        world.slotHolds(ALT, 5, otherItem);

        List<GeEvent> sent = records(world.settle());

        assertEquals(new HashSet<>(Arrays.asList(id(MAIN, mine), id(MAIN, emptied), id(ALT, theirs), id(ALT, noPosition))),
            ids(sent));
    }

    /** The same offer through the game: nothing while it fills, one record once it has finished. */
    @Test
    public void anOfferIsSentOnceItHasFinishedAndNotBefore() {
        world.login(MAIN);
        world.offer(2, 0, 0, 0L, GrandExchangeOfferState.EMPTY);
        world.offer(2, 10, 0, 0L, GrandExchangeOfferState.BUYING);
        world.offer(2, 10, 4, 400_000L, GrandExchangeOfferState.BUYING);
        world.settle();
        world.age(MAIN, 5 * MINUTE);

        assertTrue("its one fill is five minutes old, and the offer is still open", records(world.settle()).isEmpty());

        world.offer(2, 10, 10, 1_000_000L, GrandExchangeOfferState.BOUGHT);
        world.settle();
        world.age(MAIN, 5 * MINUTE);
        List<GeEvent> sent = records(world.settle());

        assertEquals(1, sent.size());
        assertEquals("the whole offer", 10, sent.get(0).delta_qty);
    }

    @Test
    public void aRecordThatSaysNothingWasTradedIsNeverSent() {
        long first = now() - 30 * MINUTE;
        world.store(MAIN,
            new Delta(first, 2, RecordSyncWorld.WHIP, true, 0, 0L, "OFFER_UPDATED", 100_000, false),
            new Delta(first + 1_000L, 3, RecordSyncWorld.WHIP, true, 5, 0L, "OFFER_UPDATED", 100_000, false),
            bought(20 * MINUTE, 4, 7));

        List<GeEvent> sent = records(world.login(MAIN).settle());

        assertEquals(1, sent.size());
        assertEquals(7, sent.get(0).delta_qty);
    }

    // ---- when ----

    /**
     * A record judged before its own live fills have arrived would be stored whole, and the fills
     * then stored beside it: the trade counted twice. So while anything is left on the live queue,
     * here a batch the website could not take, no record joins it.
     */
    @Test
    public void nothingIsSentWhileALiveTradeIsStillOnTheQueue() {
        world.store(MAIN, bought(10 * MINUTE, 1, 10));
        world.login(MAIN);
        world.queueLive("a-live-fill");
        world.sync().sweep();
        assertEquals("a fill queued and not yet sent: no record joins it", 1,
            world.state.getUploadState().getPendingUploadEvents());
        world.website.answer = events -> RecordSyncWorld.Website.status(503);

        world.tick();

        assertEquals("the live fill went back on the queue, and nothing joined it", 1,
            world.state.getUploadState().getPendingUploadEvents());
        world.sync().sweep();
        assertEquals("asked again with the queue as it is", 1, world.state.getUploadState().getPendingUploadEvents());

        world.website.answer = world.website::takeAll;
        world.state.getUploadState().clearBackOff();
        List<List<GeEvent>> sent = world.settle();

        assertEquals(2, sent.size());
        assertEquals("the live fill first, on its own", "a-live-fill", sent.get(0).get(0).event_id);
        assertEquals(1, sent.get(0).size());
        assertEquals("and only then the record", "OFFER_RECORD", sent.get(1).get(0).event_type);
    }

    /**
     * A batch leaves the queue before it is sent, so for as long as the website takes to answer
     * the queue reads empty with those trades still on their way. Asked at that very moment, no
     * record is sent.
     */
    @Test
    public void nothingIsSentWhileABatchIsOnItsWay() {
        world.store(MAIN, bought(10 * MINUTE, 1, 10));
        world.login(MAIN);
        world.queueLive("a-live-fill");
        int[] queuedWhileInFlight = {-1};
        world.website.answer = events -> {
            assertEquals("the queue reads empty", 0, world.state.getUploadState().getPendingUploadEvents());
            world.sync().sweep();
            queuedWhileInFlight[0] = world.state.getUploadState().getPendingUploadEvents();
            return world.website.takeAll(events);
        };

        world.tick();

        assertEquals("nothing joined the queue while the batch was in the air", 0, queuedWhileInFlight[0]);
        assertEquals(1, world.website.batches.size());
        assertEquals("answered, the tick that sent it lets the record follow", 1,
            world.state.getUploadState().getPendingUploadEvents());
    }

    /** After a failure the upload waits before trying again. While it waits, whatever is queued, no record is sent. */
    @Test
    public void nothingIsSentWhileTheUploadIsWaitingToRetry() {
        world.store(MAIN, bought(10 * MINUTE, 1, 10));
        world.login(MAIN);
        world.state.getUploadState().backOff(now(), 5_000L, 5_000L);

        world.sync().sweep();
        assertEquals(0, world.state.getUploadState().getPendingUploadEvents());

        world.state.getUploadState().clearBackOff();
        world.sync().sweep();
        assertEquals(1, world.state.getUploadState().getPendingUploadEvents());
    }

    /**
     * Another character may be logged in on another window right now, with its fills waiting in
     * that window's queue, which this one cannot see. Its trades wait a quarter of an hour; the
     * logged-in character's, whose queue this is, a minute.
     */
    @Test
    public void anotherCharactersTradeWaitsAQuarterOfAnHour() {
        Delta fiveMinutes = bought(5 * MINUTE, 1, 5);
        Delta twentyMinutes = bought(20 * MINUTE, 2, 20);
        world.store(ALT, twentyMinutes, fiveMinutes);
        world.store(MAIN, bought(5 * MINUTE, 3, 3));

        List<GeEvent> sent = records(world.login(MAIN).settle());

        assertEquals(new HashSet<>(Arrays.asList(id(ALT, twentyMinutes), id(MAIN, world.stored(MAIN).get(0)))), ids(sent));
        assertEquals("the mark waits at the trade not yet sent", fiveMinutes.closedAtMs(), world.mark(ALT));
    }

    /** A minute for its live fills to land, counted from when the offer ended, not from its first fill. */
    @Test
    public void aTradeWhoseOfferEndedUnderAMinuteAgoWaits() {
        long first = now() - 30 * MINUTE;
        Delta longOffer = new Delta(first, 1, RecordSyncWorld.WHIP, true, 10, 1_000_000L, "OFFER_COMPLETED", 100_000,
            false, first, now() - 20_000L);
        world.store(MAIN, longOffer, bought(21_000L, 2, 3), bought(5 * MINUTE, 3, 4));

        List<GeEvent> sent = records(world.login(MAIN).settle());

        assertEquals(1, sent.size());
        assertEquals("only the one that ended minutes ago", 4, sent.get(0).delta_qty);
    }

    @Test
    public void theOldestGoFirstInBatchesOfTwoHundred() {
        Delta[] trades = new Delta[450];
        for (int i = 0; i < trades.length; i++) {
            // Stored newest first, to show it is the sweep that puts them in order.
            trades[i] = bought((60 + i) * MINUTE, i % 8, 1 + i % 5);
        }
        world.store(MAIN, trades);

        List<List<GeEvent>> sent = world.login(MAIN).settle();

        assertEquals(3, sent.size());
        assertEquals(200, sent.get(0).size());
        assertEquals(200, sent.get(1).size());
        assertEquals(50, sent.get(2).size());
        long previous = 0L;
        for (GeEvent event : records(sent)) {
            assertTrue("oldest first", event.ts_client_ms > previous);
            previous = event.ts_client_ms;
        }
        for (Delta trade : world.stored(MAIN)) {
            assertTrue(trade.uploadedMs > 0L);
        }
    }

    /** Before the player links, nothing is sent, nothing is written and nothing is remembered. */
    @Test
    public void anUnlinkedPlayerSeesNoChangeAtAll() throws Exception {
        world.linked = false;
        world.store(MAIN, bought(10 * MINUTE, 1, 10));
        world.store(ALT, bought(20 * MINUTE, 1, 10));
        String main = world.text(MAIN);
        String alt = world.text(ALT);

        List<List<GeEvent>> sent = world.login(MAIN).settle();

        assertTrue(sent.isEmpty());
        assertEquals(0, world.state.getUploadState().getPendingUploadEvents());
        assertEquals(main, world.text(MAIN));
        assertEquals(alt, world.text(ALT));
        assertEquals(0L, world.mark(ALT));
        assertTrue(world.configManager.getConfigurationKeys(FliphubConfigGroups.CONFIG_GROUP + ".records").isEmpty());
    }

    /** At the login screen there is no logged-in character, so nobody's file is this window's to write. */
    @Test
    public void nothingIsSentFromTheLoginScreen() {
        world.store(MAIN, bought(30 * MINUTE, 1, 10));
        world.login(MAIN).logout();

        world.sync().sweep();

        assertEquals(0, world.state.getUploadState().getPendingUploadEvents());
    }

    /**
     * The player logs out with three records on their way, and the answer lands after. This
     * window is logged in as nobody now, and another may already be logged in as that character
     * and writing its file: the file is left alone, and what was confirmed is written when this
     * window is logged in as that character again.
     */
    @Test
    public void anAnswerThatLandsAfterALogoutWritesNoFile() throws Exception {
        world.store(MAIN, bought(30 * MINUTE, 1, 10), bought(20 * MINUTE, 2, 5), bought(10 * MINUTE, 3, 1));
        String main = world.text(MAIN);
        world.login(MAIN).tick();
        assertEquals("the three are queued", 3, world.state.getUploadState().getPendingUploadEvents());
        world.website.answer = events -> {
            world.logout();
            return world.website.takeAll(events);
        };

        world.tick();
        world.tick();

        assertEquals("the website confirmed all three", 3, world.state.getUploadState().confirmed.size());
        assertEquals(main, world.text(MAIN));
        assertEquals("and nothing is queued at the login screen", 0, world.state.getUploadState().getPendingUploadEvents());

        world.website.answer = world.website::takeAll;
        assertTrue("logged in again, nothing needs sending", world.login(MAIN).settle().isEmpty());
        for (Delta trade : world.stored(MAIN)) {
            assertTrue(trade.uploadedMs > 0L);
        }
    }

    // ---- what each answer does ----

    @Test
    public void aRecordTheWebsiteNamesIsMarkedAndOneItRefusesIsKeptToSendAgain() {
        Delta taken = bought(30 * MINUTE, 1, 10);
        Delta refused = bought(20 * MINUTE, 2, 5);
        world.store(MAIN, taken, refused);
        world.website.answer = events -> world.website.take(events, Collections.singleton(id(MAIN, refused)));

        assertEquals(2, records(world.login(MAIN).settle()).size());

        List<Delta> onDisk = world.stored(MAIN);
        assertTrue("confirmed, and written to the file", onDisk.get(0).uploadedMs > 0L);
        assertEquals("refused: not confirmed", 0L, onDisk.get(1).uploadedMs);
        assertTrue("nothing more this pass", records(world.settle()).isEmpty());

        world.waitOutThePause();
        List<GeEvent> again = records(world.settle());
        assertEquals("the refused one alone is sent again", Collections.singleton(id(MAIN, refused)), ids(again));
    }

    /**
     * A website from before records answers a batch as it always did: the counts, and no list. It
     * refused the records by their type. A success without the list confirms nothing.
     */
    @Test
    public void aSuccessThatNamesNoRecordsConfirmsNothing() {
        world.store(MAIN, bought(30 * MINUTE, 1, 10), bought(20 * MINUTE, 2, 5));
        world.website.answer = events -> RecordSyncWorld.Website.reply(200,
            "{\"status\":\"ok\",\"accepted\":" + events.size() + ",\"duplicates\":0,\"rejected\":0}");

        assertEquals(2, records(world.login(MAIN).settle()).size());

        for (Delta trade : world.stored(MAIN)) {
            assertEquals(0L, trade.uploadedMs);
        }
        assertTrue(world.state.getUploadState().confirmed.isEmpty());
        world.waitOutThePause();
        assertEquals("both are sent again", 2, records(world.settle()).size());
    }

    /** The same from the older website's other answer: every event in the batch rejected. */
    @Test
    public void aSuccessThatRejectedEveryEventConfirmsNothing() {
        world.store(MAIN, bought(30 * MINUTE, 1, 10));
        world.website.answer = events -> RecordSyncWorld.Website.reply(200,
            "{\"status\":\"ok\",\"accepted\":0,\"duplicates\":0,\"rejected\":" + events.size() + "}");

        world.login(MAIN).settle();

        assertEquals(0L, world.stored(MAIN).get(0).uploadedMs);
        world.waitOutThePause();
        assertEquals(1, records(world.settle()).size());
    }

    /** Only the ids under "confirmed" are taken; the ones under "rejected" never are. */
    @Test
    public void theIdsTheWebsiteRejectedAreNotMarked() {
        Delta refused = bought(30 * MINUTE, 1, 10);
        world.store(MAIN, refused);
        world.website.answer = events -> RecordSyncWorld.Website.reply(200,
            "{\"status\":\"ok\",\"accepted\":0,\"duplicates\":1,\"rejected\":1,"
                + "\"records\":{\"confirmed\":[],\"rejected\":[\"" + id(MAIN, refused) + "\"]}}");

        world.login(MAIN).settle();

        assertEquals(0L, world.stored(MAIN).get(0).uploadedMs);
        assertTrue(world.state.getUploadState().confirmed.isEmpty());
    }

    /**
     * The website may say more than the plugin reads: a note for its own owner that an account's
     * book needs rebuilding, whatever it adds next. What it confirms is still read from beside it.
     */
    @Test
    public void whatElseTheAnswerCarriesDoesNotStopAConfirmationBeingRead() {
        Delta trade = bought(30 * MINUTE, 1, 10);
        world.store(MAIN, trade);
        world.website.answer = events -> RecordSyncWorld.Website.reply(200,
            "{\"status\":\"ok\",\"accepted\":1,\"duplicates\":0,\"rejected\":0,\"ledger_rebuild_needed\":{\"records\":3},"
                + "\"records\":{\"ledger_rebuild_needed\":true,\"confirmed\":[\"" + id(MAIN, trade) + "\"],"
                + "\"rejected\":[],\"note\":{\"first_ms\":1}}}");

        world.login(MAIN).settle();

        assertTrue(world.stored(MAIN).get(0).uploadedMs > 0L);
    }

    /** An answer that is not the website's at all (a proxy's page, a list where ids should be) confirms nothing. */
    @Test
    public void anAnswerThatCannotBeReadConfirmsNothing() {
        world.store(MAIN, bought(30 * MINUTE, 1, 10));
        world.website.answer = events -> RecordSyncWorld.Website.reply(200,
            "{\"status\":\"ok\",\"accepted\":1,\"records\":{\"confirmed\":\"all of them\"}}");

        world.login(MAIN).settle();

        assertEquals(0L, world.stored(MAIN).get(0).uploadedMs);
        assertTrue(world.state.getUploadState().confirmed.isEmpty());
    }

    /** A success that names a record neither as confirmed nor as rejected has said nothing of it. */
    @Test
    public void aRecordTheAnswerDoesNotNameEitherWayIsNotConfirmed() {
        Delta named = bought(30 * MINUTE, 1, 10);
        Delta unnamed = bought(20 * MINUTE, 2, 5);
        world.store(MAIN, named, unnamed);
        world.website.answer = events -> RecordSyncWorld.Website.reply(200,
            "{\"status\":\"ok\",\"accepted\":2,\"duplicates\":0,\"rejected\":0,"
                + "\"records\":{\"confirmed\":[\"" + id(MAIN, named) + "\"],\"rejected\":[]}}");

        world.login(MAIN).settle();

        List<Delta> onDisk = world.stored(MAIN);
        assertTrue(onDisk.get(0).uploadedMs > 0L);
        assertEquals(0L, onDisk.get(1).uploadedMs);
        world.waitOutThePause();
        assertEquals(Collections.singleton(id(MAIN, unnamed)), ids(records(world.settle())));
    }

    /**
     * 400, 413, 422: the website refused the request, so it stored nothing, whatever the request
     * held. The live events in it are dropped as they always were; the records are not confirmed
     * and go again, which is how a dropped live trade now reaches the website after all.
     */
    @Test
    public void aRefusedRequestConfirmsNothingAndItsRecordsAreSentAgain() {
        for (int status : new int[] {400, 413, 422}) {
            world.store(MAIN, bought(30 * MINUTE, 1, 10), bought(20 * MINUTE, 2, 5));
            // What a careless or hostile answer might claim: a refusal confirms nothing all the same.
            world.website.answer = events -> RecordSyncWorld.Website.reply(status,
                "{\"records\":{\"confirmed\":[\"" + events.get(0).event_id + "\"]}}");

            assertEquals(2, records(world.login(MAIN).settle()).size());

            assertEquals("the batch is dropped, as a refused batch always was", 0,
                world.state.getUploadState().getPendingUploadEvents());
            for (Delta trade : world.stored(MAIN)) {
                assertEquals("status " + status, 0L, trade.uploadedMs);
            }
            world.website.answer = world.website::takeAll;
            world.waitOutThePause();
            assertEquals("status " + status, 2, records(world.settle()).size());
            for (Delta trade : world.stored(MAIN)) {
                assertTrue("status " + status, trade.uploadedMs > 0L);
            }
            world = world.again();
        }
    }

    /** The website answers a request stamped more than five minutes from its own time with 400 and this reason. */
    @Test
    public void aClockFiveMinutesOffIsSaidInWords() {
        world.store(MAIN, bought(30 * MINUTE, 1, 10));
        world.website.answer = events -> RecordSyncWorld.Website.reply(400, "{\"error\":\"Timestamp out of range\"}");

        world.login(MAIN).settle();

        String told = world.state.getUploadState().buildTooltip(true);
        assertTrue(told, told.contains("Your PC clock is more than five minutes off; trades will be sent when it is right"));
        assertFalse(told, told.contains("Events were dropped"));
        assertEquals("and the trade is still to send", 0L, world.stored(MAIN).get(0).uploadedMs);
    }

    @Test
    public void anyOtherRefusalStillSaysItsStatus() {
        world.store(MAIN, bought(30 * MINUTE, 1, 10));
        world.website.answer = events -> RecordSyncWorld.Website.status(422);

        world.login(MAIN).settle();

        String told = world.state.getUploadState().buildTooltip(true);
        assertTrue(told, told.contains("Upload failed with status 422"));
    }

    /** 401 and 403: the session is refreshed and the same batch sent again, and it is that answer that counts. */
    @Test
    public void anExpiredSessionIsRefreshedAndTheSecondAnswerIsTheOneThatConfirms() {
        world.store(MAIN, bought(30 * MINUTE, 1, 10), bought(20 * MINUTE, 2, 5));
        AtomicInteger calls = new AtomicInteger();
        world.website.answer = events -> calls.getAndIncrement() == 0
            ? RecordSyncWorld.Website.status(401) : world.website.takeAll(events);

        List<List<GeEvent>> sent = world.login(MAIN).settle();

        assertEquals("refused once, then sent again after the refresh", 2, sent.size());
        assertEquals(ids(sent.get(0)), ids(sent.get(1)));
        for (Delta trade : world.stored(MAIN)) {
            assertTrue(trade.uploadedMs > 0L);
        }
    }

    /** 429, a 5xx, or no answer at all: the batch goes back on the queue and its records go again with it. */
    @Test
    public void aBatchTheWebsiteCouldNotTakeGoesAgainWithItsRecords() {
        for (int status : new int[] {429, 500, 503, -1}) {
            world.store(MAIN, bought(30 * MINUTE, 1, 10), bought(20 * MINUTE, 2, 5));
            world.website.answer = events -> {
                if (status < 0) {
                    throw new java.io.UncheckedIOException(new java.io.IOException("no route to host"));
                }
                return RecordSyncWorld.Website.status(status);
            };
            world.login(MAIN);

            world.tick();
            world.tick();

            assertEquals("status " + status + ": both are back on the queue", 2,
                world.state.getUploadState().getPendingUploadEvents());
            assertEquals(0L, world.stored(MAIN).get(0).uploadedMs);
            Set<String> first = ids(world.website.batches.get(0));

            world.website.answer = world.website::takeAll;
            world.state.getUploadState().clearBackOff();
            List<List<GeEvent>> sent = world.settle();

            assertEquals("status " + status + ": the same batch, once", 1, sent.size());
            assertEquals(first, ids(sent.get(0)));
            for (Delta trade : world.stored(MAIN)) {
                assertTrue("status " + status, trade.uploadedMs > 0L);
            }
            world = world.again();
        }
    }

    /**
     * A batch that confirms nothing ends the pass: with a clock that is wrong, every batch gets the
     * same refusal, and 5,000 stored trades would be 25 refused requests a minute for ever. One
     * batch, then a wait that doubles.
     */
    @Test
    public void aBatchThatConfirmsNothingEndsThePassAndTheWaitDoubles() {
        Delta[] trades = new Delta[450];
        for (int i = 0; i < trades.length; i++) {
            trades[i] = bought((60 + i) * MINUTE, i % 8, 1);
        }
        world.store(MAIN, trades);
        world.website.answer = events -> RecordSyncWorld.Website.status(400);

        List<List<GeEvent>> sent = world.login(MAIN).settle();

        assertEquals("one batch of the three, and no more", 1, sent.size());
        long wait = world.sync().nextPassMs - now();
        assertTrue("about a minute: " + wait, wait > 55_000L && wait <= 60_000L);

        world.waitOutThePause();
        assertEquals(1, world.settle().size());
        wait = world.sync().nextPassMs - now();
        assertTrue("about two minutes: " + wait, wait > 115_000L && wait <= 120_000L);

        world.website.answer = world.website::takeAll;
        world.waitOutThePause();
        assertEquals("once it is taken, the whole of it goes", 3, world.settle().size());
        assertTrue("and nothing waits any longer", world.sync().nextPassMs <= now());
    }

    /**
     * 250 old records the website refuses every time (their ids sit on another website account),
     * and 3 newer ones whose live upload was lost. A batch the website judged is no reason to stop,
     * whatever it refused: the 3 go in the same pass, and only the 250 wait to be sent again.
     */
    @Test
    public void recordsTheWebsiteRefusesDoNotHoldBackTheOnesAfterThem() {
        Delta[] trades = new Delta[253];
        Set<String> refused = new HashSet<>();
        for (int i = 0; i < 250; i++) {
            trades[i] = bought((300 + i) * MINUTE, i % 8, 1);
            refused.add(id(MAIN, trades[i]));
        }
        for (int i = 250; i < 253; i++) {
            trades[i] = bought((253 - i) * 10 * MINUTE, i % 8, 2);
        }
        world.store(MAIN, trades);
        world.website.answer = events -> world.website.take(events, refused);

        List<List<GeEvent>> sent = world.login(MAIN).settle();

        assertEquals("all 253 in one pass, in two batches", 2, sent.size());
        assertEquals(253, records(sent).size());
        int confirmed = 0;
        for (Delta trade : world.stored(MAIN)) {
            confirmed += trade.uploadedMs > 0L ? 1 : 0;
            assertEquals(refused.contains(id(MAIN, trade)), trade.uploadedMs == 0L);
        }
        assertEquals("the 3 are confirmed", 3, confirmed);
        assertEquals(250, world.sync().waiting());

        assertTrue("not on every tick", world.settle().isEmpty());
        long wait = world.sync().nextPassMs - now();
        assertTrue("after about a minute: " + wait, wait > 55_000L && wait <= 60_000L);
        world.waitOutThePause();
        assertEquals("the 250 again, and only they", refused, ids(records(world.settle())));
        wait = world.sync().nextPassMs - now();
        assertTrue("then after about two: " + wait, wait > 115_000L && wait <= 120_000L);
    }

    // ---- where a confirmation is written down ----

    /** Saved, read back from the disk, compared: every trade as it was, each now with its confirmation. */
    @Test
    public void theLoggedInCharactersRecordsAreMarkedInItsOwnFile() {
        Delta[] trades = {bought(30 * MINUTE, 1, 10), bought(20 * MINUTE, 2, 5), bought(10 * MINUTE, 3, 1)};
        world.store(MAIN, trades);
        long before = now();

        world.login(MAIN).settle();

        List<Delta> onDisk = world.stored(MAIN);
        assertEquals(3, onDisk.size());
        for (int i = 0; i < 3; i++) {
            assertSameTrade(trades[i], onDisk.get(i));
            assertTrue("the moment it was confirmed", onDisk.get(i).uploadedMs >= before);
        }
    }

    @Test
    public void aConfirmedRecordIsNotSentAgainAfterARestart() {
        world.store(MAIN, bought(30 * MINUTE, 1, 10), bought(20 * MINUTE, 2, 5));
        world.login(MAIN).settle();

        RecordSyncWorld restarted = world.again().login(MAIN);

        assertTrue(restarted.settle().isEmpty());
    }

    /**
     * About half of all characters have no usable account hash and are filed under a key made from
     * their name. The logged-in character is that key, not the hash.
     */
    @Test
    public void aCharacterFiledUnderItsNameIsMarkedInItsOwnFile() throws Exception {
        long key = RecordSyncWorld.nameKey("Sips Potion");
        world.store(key, bought(30 * MINUTE, 1, 10));
        world.store(ALT, bought(20 * MINUTE, 1, 10));
        String alt = world.text(ALT);

        List<GeEvent> sent = records(world.login(-8_445_759_109_730_976_879L, "Sips Potion").settle());

        assertEquals(2, sent.size());
        assertTrue("its own file is the one marked", world.stored(key).get(0).uploadedMs > 0L);
        assertEquals("and no mark is kept for itself", 0L, world.mark(key));
        assertEquals(alt, world.text(ALT));
    }

    /**
     * Another window may be logged in as the other character and writing its file at this moment.
     * Its records are sent, and its file is exactly what it was, byte for byte.
     */
    @Test
    public void anotherCharactersRecordsAreSentAndItsFileIsNeverWritten() throws Exception {
        Delta last = bought(20 * MINUTE, 3, 1);
        world.store(ALT, bought(40 * MINUTE, 1, 10), bought(30 * MINUTE, 2, 5), last);
        world.store(MAIN, bought(25 * MINUTE, 1, 2));
        String alt = world.text(ALT);
        long written = java.nio.file.Files.getLastModifiedTime(world.file(ALT)).toMillis();

        List<GeEvent> sent = records(world.login(MAIN).settle());

        assertEquals("all four, whoever is logged in", 4, sent.size());
        assertEquals(alt, world.text(ALT));
        assertEquals(written, java.nio.file.Files.getLastModifiedTime(world.file(ALT)).toMillis());
        for (Delta trade : world.stored(ALT)) {
            assertEquals(0L, trade.uploadedMs);
        }
        assertEquals("every record of it that ended before this is confirmed", last.closedAtMs() + 1L, world.mark(ALT));
        assertTrue("and none of them is sent again this session", world.settle().isEmpty());
    }

    @Test
    public void anotherCharactersConfirmationsOutliveARestart() {
        world.store(ALT, bought(30 * MINUTE, 1, 10), bought(20 * MINUTE, 2, 5));
        world.login(MAIN).settle();

        RecordSyncWorld restarted = world.again().login(MAIN);

        assertTrue(restarted.settle().isEmpty());
    }

    /**
     * The mark says "everything before this is confirmed", so it can only move over an unbroken run
     * of confirmed records from the oldest. One the website refused stops it: after a restart that
     * record and everything after it are sent again, and nothing before it is.
     */
    @Test
    public void theMarkStopsAtTheOldestRecordTheWebsiteDidNotConfirm() throws Exception {
        Delta oldest = bought(50 * MINUTE, 1, 10);
        Delta refused = bought(40 * MINUTE, 2, 5);
        Delta newer = bought(30 * MINUTE, 3, 1);
        Delta newest = bought(20 * MINUTE, 4, 2);
        world.store(ALT, oldest, refused, newer, newest);
        String alt = world.text(ALT);
        world.website.answer = events -> world.website.take(events, Collections.singleton(id(ALT, refused)));

        assertEquals(4, records(world.login(MAIN).settle()).size());

        assertEquals("held at the refused record", refused.closedAtMs(), world.mark(ALT));
        world.waitOutThePause();
        assertEquals("this session remembers the two after it", Collections.singleton(id(ALT, refused)),
            ids(records(world.settle())));

        RecordSyncWorld restarted = world.again().login(MAIN);
        Set<String> sentAgain = ids(records(restarted.settle()));

        assertEquals(new HashSet<>(Arrays.asList(id(ALT, refused), id(ALT, newer), id(ALT, newest))), sentAgain);
        assertEquals("taken this time, so the mark passes them all", newest.closedAtMs() + 1L, restarted.mark(ALT));
        assertEquals(alt, restarted.text(ALT));
    }

    /** Two records that ended in the same millisecond, one refused: the mark must not cover the refused one. */
    @Test
    public void aRefusedRecordIsNotHiddenByOneThatEndedAtTheSameMoment() {
        Delta confirmed = bought(30 * MINUTE, 1, 10);
        Delta refused = new Delta(confirmed.tsClientMs, 2, RecordSyncWorld.WHIP, true, 5, 500_000L, "OFFER_COMPLETED",
            100_000, false, confirmed.offerStartMs, confirmed.endMs);
        world.store(ALT, confirmed, refused);
        world.website.answer = events -> world.website.take(events, Collections.singleton(id(ALT, refused)));
        world.login(MAIN).settle();

        RecordSyncWorld restarted = world.again().login(MAIN);

        assertTrue(ids(records(restarted.settle())).contains(id(ALT, refused)));
    }

    /**
     * Window A is logged in as one character and window B as the other, on one computer. Each file
     * has exactly one writer. Both may send the other's records; the website answers the second
     * time that it has them.
     */
    @Test
    public void twoWindowsOnTwoCharactersEachWriteOnlyTheirOwnFile() throws Exception {
        world.store(MAIN, bought(30 * MINUTE, 1, 10));
        world.store(ALT, bought(20 * MINUTE, 1, 5));
        String altAsStored = world.text(ALT);

        world.login(MAIN).settle();
        String mainAsMarked = world.text(MAIN);
        assertEquals("window A did not touch the alt's file", altAsStored, world.text(ALT));

        RecordSyncWorld windowB = world.again();
        windowB.website.held.addAll(world.website.held);
        List<GeEvent> sent = records(windowB.login(ALT).settle());

        assertEquals("its own record, which no file says is confirmed", 1, sent.size());
        assertEquals(GeEvent.characterId(ALT), sent.get(0).character_id);
        assertTrue("window B marks its own file", windowB.stored(ALT).get(0).uploadedMs > 0L);
        assertEquals("and leaves the main's alone", mainAsMarked, windowB.text(MAIN));
    }

    // ---- two builds on one computer ----

    /**
     * An older build reads the file and writes it back without the confirmations. Every trade is
     * then sent once more, the website answers that it has them, and they are marked again: nothing
     * lost, nothing stored twice.
     */
    @Test
    public void aFileAnOlderBuildRewroteIsSentOnceMoreAndMarkedAgain() throws Exception {
        Delta[] trades = {bought(30 * MINUTE, 1, 10), bought(20 * MINUTE, 2, 5)};
        world.store(MAIN, trades);
        world.login(MAIN).settle();
        assertTrue(world.text(MAIN).contains("\"uploadedMs\":1"));
        assertEquals(2, world.website.held.size());

        String stripped = world.text(MAIN).replaceAll(",\"uploadedMs\":\\d+", "");
        java.nio.file.Files.writeString(world.file(MAIN), stripped, StandardCharsets.UTF_8);
        RecordSyncWorld newer = world.again();
        newer.website.held.addAll(world.website.held);
        List<GeEvent> sent = records(newer.login(MAIN).settle());

        assertEquals(2, sent.size());
        assertEquals("the website stored nothing new", 2, newer.website.held.size());
        List<Delta> onDisk = newer.stored(MAIN);
        for (int i = 0; i < 2; i++) {
            assertSameTrade(trades[i], onDisk.get(i));
            assertTrue(onDisk.get(i).uploadedMs > 0L);
        }
    }

    // ---- a new link ----

    /**
     * The player unlinks, or the session is rejected, and links again: perhaps to another website
     * account, which holds none of what the first confirmed. Every confirmation is forgotten, in
     * the files this window cannot write as much as in its own.
     */
    @Test
    public void aNewLinkSendsEverythingAgain() throws Exception {
        world.store(MAIN, bought(30 * MINUTE, 1, 10));
        world.store(ALT, bought(20 * MINUTE, 1, 5));
        world.login(MAIN).settle();
        assertTrue(world.settle().isEmpty());
        long firstMark = world.stored(MAIN).get(0).uploadedMs;
        String alt = world.text(ALT);
        Thread.sleep(5L);

        world.sync().linked();
        world.website.held.clear();
        // An answer takes longer than this to come back; a test's does not.
        Thread.sleep(5L);
        List<GeEvent> sent = records(world.settle());

        assertEquals("both characters' trades go to the newly linked account", 2, sent.size());
        assertEquals(2, world.website.held.size());
        assertTrue("confirmed again, by this link", world.stored(MAIN).get(0).uploadedMs > firstMark);
        assertEquals(alt, world.text(ALT));
        assertTrue(world.again().login(MAIN).settle().isEmpty());
    }

    /**
     * A new link is made while a batch is on its way, perhaps to another website account. The answer
     * speaks for the account the batch went to: nothing it confirms is taken, or those trades would
     * never reach the new one. They go again, under the new link.
     */
    @Test
    public void anAnswerToABatchSentBeforeANewLinkConfirmsNothing() throws Exception {
        world.store(MAIN, bought(30 * MINUTE, 1, 10));
        world.store(ALT, bought(40 * MINUTE, 1, 5));
        String main = world.text(MAIN);
        world.login(MAIN).tick();
        assertEquals(2, world.state.getUploadState().getPendingUploadEvents());
        world.website.answer = events -> {
            world.sync().linked();
            world.session = "another-session";
            return world.website.takeAll(events);
        };

        world.tick();

        assertTrue(world.state.getUploadState().confirmed.isEmpty());
        assertEquals(main, world.text(MAIN));
        assertEquals(0L, world.mark(ALT));

        world.website.answer = world.website::takeAll;
        assertEquals("both go to the new link", 2, records(world.settle()).size());
        assertTrue(world.stored(MAIN).get(0).uploadedMs > 0L);
        assertTrue(world.mark(ALT) > 0L);
    }

    // ---- what the player is told ----

    /**
     * "N trades not yet on the website" counts what is there to be sent and has not been
     * confirmed: all characters' together, or the one the Profile tab is showing. It reads 0 only
     * when the website has said it holds every one of them.
     */
    @Test
    public void theCountIsTheFinishedTradesTheWebsiteHasNotConfirmed() {
        long first = now() - 30 * MINUTE;
        Delta stillOpen = new Delta(first, 4, RecordSyncWorld.WHIP, true, 4, 400_000L, "OFFER_UPDATED", 100_000, false,
            first - 5_000L, 0L);
        world.store(MAIN, bought(30 * MINUTE, 1, 10), bought(20 * MINUTE, 2, 5),
            // Not among them: an offer that ended twenty seconds ago, and the fill of one still open.
            bought(21_000L, 3, 1), stillOpen);
        // Nor another character's trade until a quarter of an hour after it ended.
        world.store(ALT, bought(40 * MINUTE, 1, 3), bought(5 * MINUTE, 2, 7));
        // A website that confirms nothing.
        world.website.answer = events -> world.website.take(events, ids(events));
        world.login(MAIN);
        world.slotHolds(MAIN, 4, RecordSyncWorld.positionOf(stillOpen));
        world.settle();

        assertEquals("every character", 3, world.sync().waiting());
        world.state.getProfileSelection().selectManual("hash_" + MAIN);
        assertEquals("the logged-in character alone", 2, world.sync().waiting());
        world.state.getProfileSelection().selectManual("hash_" + ALT);
        assertEquals("the other character alone", 1, world.sync().waiting());

        world.website.answer = world.website::takeAll;
        world.waitOutThePause();
        world.settle();

        assertEquals(0, world.sync().waiting());
        world.state.getProfileSelection().selectManual(Const.ACCOUNTWIDE_KEY_STRING);
        assertEquals("the website holds every one", 0, world.sync().waiting());
    }

    /** A trade the website refused keeps the count honest: it is not there, and the count says so. */
    @Test
    public void aRefusedTradeStaysInTheCount() {
        Delta refused = bought(20 * MINUTE, 2, 5);
        world.store(MAIN, bought(30 * MINUTE, 1, 10), refused);
        world.website.answer = events -> world.website.take(events, Collections.singleton(id(MAIN, refused)));

        world.login(MAIN).settle();

        assertEquals(1, world.sync().waiting());
    }

    @Test
    public void thereIsNoCountForAPlayerWhoIsNotLinked() {
        world.store(MAIN, bought(30 * MINUTE, 1, 10));
        world.login(MAIN).settle();
        world.sync().linked();
        assertEquals("linked, and nothing confirmed by this link yet", 1, world.sync().waiting());

        world.linked = false;

        assertEquals(0, world.sync().waiting());
    }

    /**
     * An id is worked out for every record the website has not confirmed: tens of milliseconds for
     * tens of thousands, while a trade being stored waits for the stats lock. So the records are
     * copied and the lock let go first, and what is read while they are gone through (here the
     * other character's mark, read beside its records) is read without it.
     */
    @Test
    public void theRecordsAreGoneThroughWithoutHoldingTheStatsLock() {
        world.store(MAIN, bought(30 * MINUTE, 1, 10));
        world.store(ALT, bought(40 * MINUTE, 1, 3));
        world.login(MAIN);
        List<Boolean> lockHeld = new java.util.ArrayList<>();
        RecordSyncWorld.configRead = key -> {
            if (String.valueOf(key).endsWith(RecordSync.MARK_KEY + ALT)) {
                lockHeld.add(Thread.holdsLock(world.state.getLocalStatsLock()));
            }
        };

        world.sync().sweep();
        world.sync().waiting();

        assertEquals("read once by the sweep and once by the count", 2, lockHeld.size());
        assertFalse(lockHeld.contains(true));
    }

    /** Counting is only looking: it marks no file and moves no mark. */
    @Test
    public void countingWritesNothing() throws Exception {
        world.store(MAIN, bought(30 * MINUTE, 1, 10));
        world.store(ALT, bought(40 * MINUTE, 1, 3));
        world.login(MAIN);
        // Read from their files, and then confirmed by an answer whose sweep has not run yet.
        world.tick();
        world.state.getUploadState().confirmed.add(id(MAIN, world.stored(MAIN).get(0)));
        world.state.getUploadState().confirmed.add(id(ALT, world.stored(ALT).get(0)));
        String main = world.text(MAIN);

        assertEquals("both are confirmed in memory", 0, world.sync().waiting());

        assertEquals(main, world.text(MAIN));
        assertEquals(0L, world.mark(ALT));
    }

    // ---- wipes ----

    /**
     * "Wipe website statistics" empties the website on purpose. Every confirmation stays, in the
     * files and in the marks, or the trades just wiped would all be sent straight back.
     */
    @Test
    public void aWebsiteWipeKeepsEveryConfirmation() throws Exception {
        world.store(MAIN, bought(30 * MINUTE, 1, 10));
        world.store(ALT, bought(20 * MINUTE, 1, 5));
        world.login(MAIN).settle();
        long altMark = world.mark(ALT);
        String main = world.text(MAIN);
        assertTrue(altMark > 0L);

        world.injector.getInstance(WebsiteStatsWipe.class).wipeWebsiteStatsAsync();
        // What the wipe does once the website has answered, it does on the game thread.
        world.runGameThread();

        assertTrue("the website was wiped", world.website.held.isEmpty());
        assertTrue("and nothing is sent back to it", world.settle().isEmpty());
        assertEquals(main, world.text(MAIN));
        assertEquals(altMark, world.mark(ALT));
        assertTrue("nor after a restart", world.again().login(MAIN).settle().isEmpty());
    }

    /**
     * Wiping a character on this computer empties its file. The mark kept for it goes too: it
     * spoke of the trades now gone, and a trade stored afterwards with an earlier time must not be
     * taken as confirmed.
     */
    @Test
    public void aLocalWipeForgetsTheMarkOfTheCharacterWipedAndNoOther() {
        world.store(ALT, bought(40 * MINUTE, 1, 5));
        world.store(OTHER_ALT, bought(40 * MINUTE, 1, 5));
        world.login(MAIN).settle();
        long otherMark = world.mark(OTHER_ALT);
        assertTrue(world.mark(ALT) > 0L && otherMark > 0L);

        world.injector.getInstance(WipeStateStore.class).setWipeBarrierArmed(ALT, true);

        assertEquals(0L, world.mark(ALT));
        assertEquals(otherMark, world.mark(OTHER_ALT));
        // Its history is found again, dated before the old mark, and is sent.
        Delta found = bought(60 * MINUTE, 2, 9);
        world.store(ALT, found);
        RecordSyncWorld restarted = world.again().login(MAIN);
        assertEquals(Collections.singleton(id(ALT, found)), ids(records(restarted.settle())));
    }

    /** A file the other character's own window marked is believed only if it was marked since this link. */
    @Test
    public void aConfirmationInAnotherCharactersFileFromBeforeTheLinkDoesNotCount() throws Exception {
        Delta marked = bought(20 * MINUTE, 1, 5);
        marked.uploadedMs = now() - 5 * MINUTE;
        world.store(ALT, marked);
        world.login(MAIN);
        assertTrue("marked by its own window, so not sent", world.settle().isEmpty());

        world.sync().linked();

        assertEquals(1, records(world.settle()).size());
    }

    private static void assertSameTrade(Delta expected, Delta actual) {
        assertEquals(expected.tsClientMs, actual.tsClientMs);
        assertEquals(expected.slot, actual.slot);
        assertEquals(expected.itemId, actual.itemId);
        assertEquals(expected.isBuy, actual.isBuy);
        assertEquals(expected.deltaQty, actual.deltaQty);
        assertEquals(expected.deltaGp, actual.deltaGp);
        assertEquals(expected.eventType, actual.eventType);
        assertEquals(expected.price, actual.price);
        assertEquals(expected.offerStartMs, actual.offerStartMs);
        assertEquals(expected.endMs, actual.endMs);
    }
}
