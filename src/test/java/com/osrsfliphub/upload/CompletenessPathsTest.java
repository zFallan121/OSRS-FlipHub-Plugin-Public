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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.osrsfliphub.RecordSyncWorld.bought;
import static com.osrsfliphub.RecordSyncWorld.id;
import static com.osrsfliphub.RecordSyncWorld.ids;
import static com.osrsfliphub.RecordSyncWorld.records;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The ways a trade in the player's file used to fail to reach the website, each as it was found on
 * 2 October 2026 (86 of one player's 622 stored trades had never arrived), and what becomes of it
 * now. Every one of these began as a test that failed against the code of that day.
 *
 * <p>The live upload is as it was: a queue in memory, fill by fill, ids that cannot be made again.
 * What closes each loss is that the trade is also in the file, and a trade in the file is sent as a
 * record until the website confirms it ({@link RecordSync}). So these drive real offers through the
 * real handler, lose the live upload the way it was lost, and look for the record.
 */
public class CompletenessPathsTest {
    private static final long MAIN = 4242L;
    private static final long ALT = 777L;
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

    // ---- Path 1: the upload queue lives only in memory ----

    /**
     * A crash, a kill, a power cut: the queue is gone and the trade was only in the file, where
     * nothing sent it again. The queue is still in memory only. The file is what is sent from.
     */
    @Test
    public void aTradeStillQueuedWhenTheClientDiesReachesTheWebsiteAfterItReopens() {
        world.login(MAIN).buyTenWhips(2);
        assertTrue("its live events were waiting", world.state.getUploadState().getPendingUploadEvents() > 0);
        assertTrue("and nothing had been sent", world.website.batches.isEmpty());

        // The client dies here. What it reopens with is the file, and a queue that is empty.
        RecordSyncWorld reopened = world.again().login(MAIN);
        assertEquals(0, reopened.state.getUploadState().getPendingUploadEvents());
        reopened.age(MAIN, 5 * MINUTE);
        List<GeEvent> sent = records(reopened.settle());

        assertEquals(1, sent.size());
        assertEquals(10, sent.get(0).delta_qty);
        assertEquals(1_000_000L, sent.get(0).delta_gp);
        assertTrue("and the file says the website has it", reopened.stored(MAIN).get(0).uploadedMs > 0L);
    }

    /**
     * A trade made before the player links is queued and held, in memory, until the client closes.
     * After a restart and a link, it is sent from the file.
     */
    @Test
    public void tradesMadeBeforeLinkingReachTheWebsiteOnceLinkedEvenAfterARestart() {
        world.linked = false;
        world.login(MAIN).buyTenWhips(2);
        world.tick();
        assertTrue("while unlinked nothing is sent", world.website.batches.isEmpty());
        assertEquals("nor marked", 0L, world.stored(MAIN).get(0).uploadedMs);

        RecordSyncWorld restarted = world.again().login(MAIN);
        restarted.age(MAIN, 5 * MINUTE);
        List<GeEvent> sent = records(restarted.settle());

        assertEquals(1, sent.size());
        assertEquals(10, sent.get(0).delta_qty);
    }

    // ---- Path 2: a 4xx answer drops the batch, and the website answers a wrong clock with 400 ----

    /**
     * The website answers 400 when the PC clock is more than five minutes from its own, and any 4xx
     * but 401/403 drops the batch from the queue. It still does. The trades are not lost with it:
     * their records are sent once the website takes them.
     */
    @Test
    public void aBatchTheWebsiteAnsweredFourHundredIsSentAgainFromTheFile() {
        world.website.answer = events -> RecordSyncWorld.Website.status(400);
        world.login(MAIN).buyTenWhips(2);
        world.tick();
        assertEquals("the live batch is dropped, as it always was", 0, world.state.getUploadState().getPendingUploadEvents());
        assertEquals(1, world.website.batches.size());

        // The clock is put right, and a minute passes.
        world.website.answer = world.website::takeAll;
        world.age(MAIN, 5 * MINUTE);
        world.waitOutThePause();
        List<GeEvent> sent = records(world.settle());

        assertEquals("a clock five minutes off is not a verdict on the trade", 1, sent.size());
        assertEquals(10, sent.get(0).delta_qty);
        assertTrue(world.stored(MAIN).get(0).uploadedMs > 0L);
    }

    /**
     * The same 400 during the first upload of a character's history used to be final: the
     * character was marked as sent, with nothing sent. Nothing is written off now, for the
     * logged-in character or any other.
     */
    @Test
    public void aClockFiveMinutesOffDoesNotWriteACharactersHistoryOff() {
        world.store(MAIN, bought(30 * MINUTE, 1, 10), bought(20 * MINUTE, 2, 5));
        world.store(ALT, bought(25 * MINUTE, 1, 3));
        world.website.answer = events -> RecordSyncWorld.Website.status(400);

        world.login(MAIN).settle();

        assertEquals(0L, world.stored(MAIN).get(0).uploadedMs);
        assertEquals("no mark says the alt's history is there", 0L, world.mark(ALT));

        world.website.answer = world.website::takeAll;
        RecordSyncWorld nextSession = world.again().login(MAIN);
        assertEquals("all of it is sent the next time", 3, records(nextSession.settle()).size());
    }

    // ---- Path 3: identity. A live upload and a re-send from the file never share an id ----

    /**
     * Live, every fill has a random id and only the completion a name-based one; the stored record
     * is the whole offer, named from itself. They can never share an id, so a stored trade sent as
     * an ordinary trade was stored beside its own fills and counted twice. It is not sent as a
     * trade: it is sent as a record, which the website judges by the fills it holds and not by id.
     */
    @Test
    public void aStoredTradeIsSentAsARecordToBeJudgedNotAsATradeUnderANewId() {
        world.login(MAIN).buyTenWhips(2);
        world.tick();
        Set<String> liveIds = new HashSet<>();
        Set<String> liveTypes = new HashSet<>();
        for (GeEvent event : world.website.batches.get(0)) {
            liveIds.add(event.event_id);
            liveTypes.add(event.event_type);
        }
        assertFalse(liveTypes.contains("OFFER_RECORD"));

        world.age(MAIN, 5 * MINUTE);
        List<GeEvent> sent = records(world.settle());

        assertEquals(1, sent.size());
        assertFalse("no live event had this id, and none could", liveIds.contains(sent.get(0).event_id));
        assertEquals("so it does not go as a trade", "OFFER_RECORD", sent.get(0).event_type);
        assertEquals("the whole offer, to be set against its fills", 10, sent.get(0).delta_qty);
    }

    /** A fill's id is random and cannot be made again; the record that covers the fill has one that can. */
    @Test
    public void aRecordsIdCanBeMadeAgainThoughAFillsCannot() {
        world.login(MAIN).buyTenWhips(2);
        world.tick();
        GeEvent fill = null;
        for (GeEvent event : world.website.batches.get(0)) {
            if ("OFFER_UPDATED".equals(event.event_type)) {
                fill = event;
            }
        }
        assertEquals("a fill is still named at random", 4, UUID.fromString(fill.event_id).version());

        world.age(MAIN, 5 * MINUTE);
        GeEvent record = records(world.settle()).get(0);

        assertEquals("a name-based id", 3, UUID.fromString(record.event_id).version());
        assertEquals("made from the stored trade and nothing else", id(MAIN, world.stored(MAIN).get(0)), record.event_id);
    }

    /**
     * The history sync stored an imported row as one record and uploaded it as two events, a fill
     * and a completion, under ids that were neither the record's nor anything another computer had
     * sent: a trade the website already held from the computer it was made on was stored again.
     * The sync now queues nothing. The stored record is sent like any other, once, and the website
     * judges it against what it holds.
     */
    @Test
    public void anImportedTradeIsNotUploadedAsNewEventsButSentOnceAsItsStoredRecord() {
        world.login(MAIN);
        List<Trade> history = new ArrayList<>();
        history.add(new Trade(RecordSyncWorld.WHIP, true, 8, 5_033_000, 40_264_000L));

        AutoSync.SyncResult result = world.injector.getInstance(AutoSync.class)
            .sync(MAIN, history, AutoSyncTradeMatcher.LastSync.NONE);

        assertEquals(1, result.addedTrades);
        assertEquals("the sync itself uploads nothing", 0, world.state.getUploadState().getPendingUploadEvents());
        Delta stored = world.stored(MAIN).get(0);
        assertTrue("stored on a made-up slot", stored.slot >= Const.GE_HISTORY_SYNTHETIC_SLOT_START);

        world.age(MAIN, 5 * MINUTE);
        stored = world.stored(MAIN).get(0);
        List<List<GeEvent>> sent = world.settle();

        assertEquals(1, sent.size());
        assertEquals("one event for the row, not two", 1, sent.get(0).size());
        GeEvent record = sent.get(0).get(0);
        assertEquals("OFFER_RECORD", record.event_type);
        assertEquals("under the id of the record that is stored", id(MAIN, stored), record.event_id);
        assertEquals(stored.slot, record.slot);
        assertEquals(8, record.delta_qty);
        assertEquals(40_264_000L, record.delta_gp);
        assertTrue(world.stored(MAIN).get(0).uploadedMs > 0L);
        assertTrue("and never again", world.again().login(MAIN).settle().isEmpty());
    }

    // ---- Path 4: "already there" was inferred from totals, which cannot see a missing trade ----

    /**
     * The old catch-up compared the website's totals with the plugin's own and called a character
     * "already there" within 45%: one trade missing of three hundred is a third of a percent. Now
     * nothing is inferred. Each trade is confirmed or it is not, and the one that is not is sent.
     */
    @Test
    public void aCharacterTheWebsiteIsOneTradeShortOfIsSentThatTrade() {
        Delta[] trades = new Delta[300];
        for (int i = 0; i < trades.length; i++) {
            trades[i] = bought((60 + i) * MINUTE, i % 8, 1 + i % 3);
            trades[i].uploadedMs = RecordSyncWorld.now() - MINUTE;
        }
        Delta missing = trades[137];
        missing.uploadedMs = 0L;
        world.store(MAIN, trades);

        List<GeEvent> sent = records(world.login(MAIN).settle());

        assertEquals(1, sent.size());
        assertEquals(id(MAIN, missing), sent.get(0).event_id);
    }

    /** And the other side of it: once every trade is confirmed, nothing is sent, whatever any total says. */
    @Test
    public void onceEveryTradeIsConfirmedNothingIsSent() {
        Delta[] trades = {bought(30 * MINUTE, 1, 10), bought(20 * MINUTE, 2, 5)};
        world.store(MAIN, trades);
        world.store(ALT, bought(25 * MINUTE, 1, 3));
        assertEquals(3, records(world.login(MAIN).settle()).size());

        assertTrue(world.settle().isEmpty());
        assertTrue(world.again().login(MAIN).settle().isEmpty());
        assertEquals(ids(records(world.website.batches)).size(), world.website.held.size());
    }
}
