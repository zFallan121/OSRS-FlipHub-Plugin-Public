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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A stored trade says when the website confirmed that it holds it ({@link Delta#uploadedMs}). That
 * is one more thing written to the player's file, so it is proved the way a saving change has to
 * be: saved to a real file, read back, compared - and read and rewritten by a build that has never
 * heard of it, because two builds on one computer share these files.
 */
public class ConfirmedRecordsFileTest {
    private static final long ACCOUNT = 4242L;
    private static final long ACCOUNTWIDE = 0L;
    private static final long CONFIRMED_AT = 1_759_400_000_000L;

    private Path baseDir;
    private ProfileStore store;

    @Before
    public void setUp() throws IOException {
        baseDir = Files.createTempDirectory("confirmed-records");
        store = new ProfileStore(new Gson(), "fliphub", "fliphub-dev", baseDir);
    }

    @After
    public void tearDown() throws IOException {
        try (Stream<Path> paths = Files.walk(baseDir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    public void aConfirmationIsStillOnTheRecordAfterASaveAndAReload() {
        List<Delta> trades = trades();
        trades.get(0).uploadedMs = CONFIRMED_AT;
        trades.get(2).uploadedMs = CONFIRMED_AT + 5L;

        store.writeProfileData(ACCOUNT, ACCOUNTWIDE, "Zezima", trades);
        List<Delta> read = store.readProfileData(ACCOUNT, ACCOUNTWIDE).deltas;

        assertEquals(3, read.size());
        assertEquals(CONFIRMED_AT, read.get(0).uploadedMs);
        assertEquals("the one the website has not confirmed stays that way", 0L, read.get(1).uploadedMs);
        assertEquals(CONFIRMED_AT + 5L, read.get(2).uploadedMs);
        for (int i = 0; i < 3; i++) {
            assertSameTrade(trades.get(i), read.get(i));
        }
    }

    /** Every file on every player's computer today: no record in it is confirmed, and every one still reads. */
    @Test
    public void aFileWrittenBeforeTheFieldReadsWithNothingConfirmed() throws IOException {
        Path file = store.getProfileFile(ACCOUNT, ACCOUNTWIDE);
        Files.createDirectories(file.getParent());
        Files.writeString(file, new Gson().toJson(olderDocument(trades())), StandardCharsets.UTF_8);

        List<Delta> read = store.readProfileData(ACCOUNT, ACCOUNTWIDE).deltas;

        assertEquals(3, read.size());
        for (int i = 0; i < 3; i++) {
            assertSameTrade(trades().get(i), read.get(i));
            assertEquals(0L, read.get(i).uploadedMs);
        }
    }

    /**
     * The Plugin Hub build and a development build on one computer, or a player who goes back a
     * version: the older build reads the file through a record that has no such field and writes
     * what it read. Every trade must come through whole. The confirmations do not, which costs one
     * more upload of each record and nothing else: the website answers that it has them.
     */
    @Test
    public void anOlderBuildReadsWhatThisBuildWroteAndWritesEveryTradeBack() throws IOException {
        List<Delta> trades = trades();
        for (Delta trade : trades) {
            trade.uploadedMs = CONFIRMED_AT;
        }
        store.writeProfileData(ACCOUNT, ACCOUNTWIDE, "Zezima", trades);
        Path file = store.getProfileFile(ACCOUNT, ACCOUNTWIDE);
        Gson gson = new Gson();

        OlderProfileData older = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), OlderProfileData.class);
        assertEquals("the older build reads every record", 3, older.deltas.size());
        assertEquals(4151, older.deltas.get(0).itemId);
        assertEquals(1_000_000L, older.deltas.get(0).deltaGp);
        assertEquals(9_000L, older.deltas.get(1).endMs);
        Files.writeString(file, gson.toJson(older), StandardCharsets.UTF_8);

        List<Delta> read = store.readProfileData(ACCOUNT, ACCOUNTWIDE).deltas;
        assertEquals(3, read.size());
        for (int i = 0; i < 3; i++) {
            assertSameTrade(trades.get(i), read.get(i));
            assertEquals("the older build could not keep it, so the record is sent again", 0L, read.get(i).uploadedMs);
        }
        assertFalse(Files.readString(file, StandardCharsets.UTF_8).contains("uploadedMs"));
    }

    /**
     * A sale with no tax on it (an item under 50 coins, a bond) is copied at every load by the step
     * that turns an old gross figure into what was received. The copy keeps the confirmation, or
     * the record would be sent to the website again after every restart.
     */
    @Test
    public void aRecordCopiedAtLoadKeepsItsConfirmation() {
        Delta untaxed = new Delta(5_000L, 3, 1931, false, 10, 400L, "OFFER_UPDATED", 40, false);
        untaxed.uploadedMs = CONFIRMED_AT;

        List<Delta> loaded = TradeDeltaUtils.dedupeLocalTrades(new ArrayList<>(Arrays.asList(untaxed)),
            Const.LOCAL_EVENT_BUCKET_MS, Const.DUPLICATE_TRADE_WINDOW_MS);

        assertEquals(1, loaded.size());
        assertEquals(400L, loaded.get(0).deltaGp);
        assertEquals(CONFIRMED_AT, loaded.get(0).uploadedMs);
    }

    /** A finished offer already stored as one record is the same record after a load, confirmation and all. */
    @Test
    public void aLoadLeavesAStoredOffersConfirmationAlone() {
        List<Delta> trades = trades();
        trades.get(1).uploadedMs = CONFIRMED_AT;

        List<Delta> loaded = TradeDeltaUtils.dedupeLocalTrades(trades,
            Const.LOCAL_EVENT_BUCKET_MS, Const.DUPLICATE_TRADE_WINDOW_MS);

        assertEquals(3, loaded.size());
        assertEquals(0L, loaded.get(0).uploadedMs);
        assertEquals(CONFIRMED_AT, loaded.get(1).uploadedMs);
    }

    /**
     * Fills folded into one record at load are a new record with a new id, whatever the website
     * said of the fills: it is sent, and the website answers that the fills it holds cover it.
     */
    @Test
    public void fillsFoldedIntoOneRecordAreARecordTheWebsiteHasNotConfirmed() {
        Delta first = new Delta(1_000L, 2, 4151, true, 4, 400_000L, "OFFER_UPDATED", 100_000, false, 900L, 0L);
        Delta second = new Delta(60_000L, 2, 4151, true, 6, 600_000L, "OFFER_COMPLETED", 100_000, false, 900L, 0L);
        first.uploadedMs = CONFIRMED_AT;
        second.uploadedMs = CONFIRMED_AT;

        List<Delta> loaded = TradeDeltaUtils.dedupeLocalTrades(new ArrayList<>(Arrays.asList(first, second)),
            Const.LOCAL_EVENT_BUCKET_MS, Const.DUPLICATE_TRADE_WINDOW_MS);

        assertEquals(1, loaded.size());
        assertEquals(10, loaded.get(0).deltaQty);
        assertEquals(0L, loaded.get(0).uploadedMs);
    }

    private static List<Delta> trades() {
        List<Delta> trades = new ArrayList<>();
        trades.add(new Delta(1_000L, 1, 4151, true, 10, 1_000_000L, "OFFER_COMPLETED", 100_000, false, 900L, 2_000L));
        trades.add(new Delta(8_000L, 2, 4151, false, 10, 1_176_000L, "OFFER_COMPLETED", 120_000, false, 7_000L, 9_000L));
        trades.add(new Delta(20_000L, Const.GE_HISTORY_SYNTHETIC_SLOT_START, 385, true, 100, 80_000L,
            "OFFER_COMPLETED", 800, false, 20_000L, 20_004L));
        return trades;
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
        assertEquals(expected.baselineSynthetic, actual.baselineSynthetic);
        assertEquals(expected.offerStartMs, actual.offerStartMs);
        assertEquals(expected.endMs, actual.endMs);
    }

    private static OlderProfileData olderDocument(List<Delta> trades) {
        OlderProfileData data = new OlderProfileData();
        data.accountHash = ACCOUNT;
        data.displayName = "Zezima";
        data.deltas = new ArrayList<>();
        for (Delta trade : trades) {
            OlderDelta older = new OlderDelta();
            older.tsClientMs = trade.tsClientMs;
            older.slot = trade.slot;
            older.itemId = trade.itemId;
            older.isBuy = trade.isBuy;
            older.deltaQty = trade.deltaQty;
            older.deltaGp = trade.deltaGp;
            older.eventType = trade.eventType;
            older.price = trade.price;
            older.baselineSynthetic = trade.baselineSynthetic;
            older.offerStartMs = trade.offerStartMs;
            older.endMs = trade.endMs;
            data.deltas.add(older);
        }
        assertTrue(data.deltas.size() == trades.size());
        return data;
    }

    /** The document as the builds before this one model it. */
    private static final class OlderProfileData {
        long accountHash;
        String displayName;
        List<OlderDelta> deltas;
        long updatedMs;
    }

    /** A stored trade as the builds before this one model it: every field but the confirmation. */
    private static final class OlderDelta {
        long tsClientMs;
        int slot;
        int itemId;
        boolean isBuy;
        int deltaQty;
        long deltaGp;
        String eventType;
        int price;
        boolean baselineSynthetic;
        long offerStartMs;
        long endMs;
    }
}
