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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Bought on one account, handed to another of the player's own, sold there.
 *
 * <p>Every account keeps its own books, and the accountwide figure is those books added up, so
 * nothing ever matched the main's purchase with the alt's sale: the main held the stock for
 * ever and the alt's sales earned nothing. The player records the move; this is what both
 * accounts' books look like before it, after it, and once it has been forgotten again.
 *
 * <p>The figures throughout: the main buys 1,000 sharks at 800 (800,000). The alt sells them in
 * three offers at 950, which is 931 each after tax: 931,000 received, 131,000 made.
 */
public class MovedBetweenAccountsTest {
    private static final int SHARK = 385;
    private static final int BLADE = 11690;
    private static final int HILT = 11810;
    private static final int GODSWORD = 11802;
    private static final long MAIN = 7L;
    private static final long ALT = 8L;
    private static final long OTHER_ALT = 9L;

    private static Delta buy(long tsMs, int slot, int itemId, int qty, long gp) {
        return new Delta(tsMs, slot, itemId, true, qty, gp, "OFFER_COMPLETED", (int) (gp / qty), false);
    }

    private static Delta sell(long tsMs, int slot, int itemId, int qty, long grossUnit) {
        long net = grossUnit * qty - GeTax.forSale(itemId, (int) grossUnit, qty);
        return new Delta(tsMs, slot, itemId, false, qty, net, "OFFER_COMPLETED", (int) grossUnit, false);
    }

    private static RecipeFlip move(Delta purchase, int qty, long to, long recordedMs) {
        return new RecipeFlip(ConversionKind.TRANSFER, "Shark to Alt",
            Collections.singletonList(new RecipeFlip.Part(TradeKey.of(purchase), qty,
                RecipeFlipLedger.share(purchase.deltaGp, qty, purchase.deltaQty))),
            null, 0L, recordedMs, to, null);
    }

    private static final Delta BOUGHT = buy(1_000L, 1, SHARK, 1000, 800_000L);

    private static List<Delta> theAltsSales() {
        return Arrays.asList(
            sell(5_000L, 2, SHARK, 400, 950L),
            sell(6_000L, 2, SHARK, 300, 950L),
            sell(7_000L, 3, SHARK, 300, 950L));
    }

    private static long profit(Map<Integer, List<StatsFlipInstance>> history) {
        long total = 0L;
        for (List<StatsFlipInstance> list : history.values()) {
            for (StatsFlipInstance entry : list) {
                total += entry.profitGp;
            }
        }
        return total;
    }

    private static long historyProfit(RecipeFlipStore store, long account, List<Delta> deltas) {
        return profit(new LocalFlipHistoryService(store).buildHistory(deltas, null, account));
    }

    private static StatsSummary summary(RecipeFlipStore store, long account, List<Delta> deltas) {
        StatsCache cache = new StatsCache(account, store);
        cache.rebuild(deltas);
        return cache.getSummary();
    }

    // ---- what a move is ----

    @Test
    public void aMoveNamesPurchasesAndAnAccountAndNoSales() {
        assertTrue(move(BOUGHT, 1000, ALT, 9_000L).isUsable());

        RecipeFlip withASale = move(BOUGHT, 1000, ALT, 9_000L);
        withASale.outputs = Collections.singletonList(new RecipeFlip.Part(new TradeKey(5_000L, 2, SHARK), 1, null));
        assertFalse("a move has no sales: the other account's replay finds them", withASale.isUsable());

        RecipeFlip uncosted = move(BOUGHT, 1000, ALT, 9_000L);
        uncosted.inputs.get(0).gp = null;
        assertFalse("the other account could not price it", uncosted.isUsable());

        RecipeFlip aRecipeWithNoSale = move(BOUGHT, 1000, ALT, 9_000L);
        aRecipeWithNoSale.toAccount = null;
        assertFalse("without an account it is a recipe, and a recipe needs a sale", aRecipeWithNoSale.isUsable());
    }

    // ---- the account that bought ----

    @Test
    public void theMainsBooksLoseThePurchaseAndBookNothingForIt() {
        List<Delta> mains = Collections.singletonList(BOUGHT);

        RecipeFlipLedger.Result result =
            RecipeFlipLedger.apply(mains, Collections.singletonList(move(BOUGHT, 1000, ALT, 9_000L)));

        assertFalse(result.isEmpty());
        assertEquals(1000, result.claimedOn(BOUGHT));
        assertTrue("nothing was sold here, so there is nothing to show here", result.activities.isEmpty());
        assertEquals(0, left(result.remainingTrades(mains), SHARK));
    }

    /** How many of the item the ordinary replay still gets to see. A spent completion stays, at nothing. */
    private static int left(List<Delta> remaining, int itemId) {
        int quantity = 0;
        for (Delta delta : remaining) {
            if (delta.itemId == itemId && delta.isBuy) {
                quantity += delta.deltaQty;
            }
        }
        return quantity;
    }

    @Test
    public void whatWasNotMovedStaysTheMainsToSell() {
        Delta mainsOwnSale = sell(8_000L, 4, SHARK, 400, 950L);
        List<Delta> mains = Arrays.asList(BOUGHT, mainsOwnSale);
        RecipeFlipStore store = new RecipeFlipStore();
        store.add(MAIN, move(BOUGHT, 600, ALT, 9_000L));

        // 400 left at 800 each, sold at 931 after tax.
        assertEquals(400 * 131L, historyProfit(store, MAIN, mains));
        assertEquals(Long.valueOf(400 * 131L), summary(store, MAIN, mains).total_profit_gp);
    }

    @Test
    public void aSaleCannotBeMoved() {
        Delta sale = sell(5_000L, 2, SHARK, 400, 950L);

        RecipeFlipLedger.Result result = RecipeFlipLedger.apply(
            Collections.singletonList(sale), Collections.singletonList(move(sale, 400, ALT, 9_000L)));

        assertTrue(result.isEmpty());
    }

    @Test
    public void onePurchaseCanFeedARecipeAndAMove() {
        Delta blades = buy(1_000L, 1, BLADE, 2, 8_000_000L);
        Delta hilt = buy(2_000L, 2, HILT, 1, 11_000_000L);
        Delta sale = sell(3_000L, 3, GODSWORD, 1, 18_500_000L);
        List<Delta> mains = Arrays.asList(blades, hilt, sale);
        RecipeFlip recipe = new RecipeFlip(ConversionKind.ASSEMBLE, "Armadyl godsword",
            Arrays.asList(new RecipeFlip.Part(TradeKey.of(blades), 1, null),
                new RecipeFlip.Part(TradeKey.of(hilt), 1, null)),
            Collections.singletonList(new RecipeFlip.Part(TradeKey.of(sale), 1, null)), 0L, 8_000L, null, null);

        RecipeFlipLedger.Result result =
            RecipeFlipLedger.apply(mains, Arrays.asList(recipe, move(blades, 1, ALT, 9_000L)));

        assertEquals(2, result.claimedOn(blades));
        assertEquals(1, result.activities.size());
        assertEquals(0, left(result.remainingTrades(mains), BLADE));
    }

    // ---- the account it went to ----

    @Test
    public void theAltReceivesThePurchaseAsItWasMade() {
        RecipeFlipStore store = new RecipeFlipStore();
        store.add(MAIN, move(BOUGHT, 600, ALT, 9_000L));

        List<Delta> received = RecipeFlipLedger.received(store, ALT, new java.util.HashSet<>());

        assertEquals(1, received.size());
        Delta delta = received.get(0);
        assertEquals("when it was bought, which is before any sale of it", 1_000L, delta.tsClientMs);
        assertEquals(1, delta.slot);
        assertEquals(SHARK, delta.itemId);
        assertTrue(delta.isBuy);
        assertEquals(600, delta.deltaQty);
        assertEquals(480_000L, delta.deltaGp);
        assertEquals(800, delta.price);
        assertTrue("nobody else receives it", RecipeFlipLedger.received(store, OTHER_ALT, new java.util.HashSet<>()).isEmpty());
        assertTrue("least of all the account that gave it", RecipeFlipLedger.received(store, MAIN, new java.util.HashSet<>()).isEmpty());
        assertTrue(RecipeFlipLedger.received(store, Const.ACCOUNTWIDE_KEY, new java.util.HashSet<>()).isEmpty());
    }

    @Test
    public void beforeTheMoveTheAltsSalesEarnNothing() {
        RecipeFlipStore store = new RecipeFlipStore();

        assertEquals(0L, historyProfit(store, ALT, theAltsSales()));
        assertEquals(Long.valueOf(0L), summary(store, ALT, theAltsSales()).total_profit_gp);
    }

    @Test
    public void afterItEverySaleTheAltMadeFlipsAtWhatTheMainPaid_inBothLedgers() {
        RecipeFlipStore store = new RecipeFlipStore();
        store.add(MAIN, move(BOUGHT, 1000, ALT, 9_000L));

        assertEquals(131_000L, historyProfit(store, ALT, theAltsSales()));
        StatsSummary alt = summary(store, ALT, theAltsSales());
        assertEquals(Long.valueOf(131_000L), alt.total_profit_gp);
        assertEquals(Long.valueOf(800_000L), alt.total_cost_gp);
        assertEquals(Integer.valueOf(3), alt.fill_count);

        // The accountwide figure is every account's added up. The main's share of it is nothing.
        List<Delta> mains = Collections.singletonList(BOUGHT);
        assertEquals(0L, historyProfit(store, MAIN, mains));
        assertEquals(Long.valueOf(0L), summary(store, MAIN, mains).total_profit_gp);
    }

    @Test
    public void theAltsRowsStillSumToItsHeader() {
        RecipeFlipStore store = new RecipeFlipStore();
        store.add(MAIN, move(BOUGHT, 1000, ALT, 9_000L));
        StatsCache cache = new StatsCache(ALT, store);
        cache.rebuild(theAltsSales());
        StatsSummary summary = cache.getSummary();
        List<StatsItem> items = cache.getItems();

        StatsView.reconcileWithFlipHistory(summary, items,
            new LocalFlipHistoryService(store).buildHistory(theAltsSales(), null, ALT));

        assertEquals(1, items.size());
        assertEquals(Long.valueOf(131_000L), items.get(0).total_profit_gp);
        assertEquals(Long.valueOf(131_000L), summary.total_profit_gp);
    }

    @Test
    public void anAltThatHasTradedNothingYetStillSellsWhatItWasHanded() {
        // The cache of an account with no trades is built all the same, and its first sale is
        // applied to that cache as it stands.
        RecipeFlipStore store = new RecipeFlipStore();
        store.add(MAIN, move(BOUGHT, 1000, ALT, 9_000L));
        StatsCache cache = new StatsCache(ALT, store);
        cache.rebuild(new ArrayList<>());

        assertTrue(cache.applyDeltaInOrder(sell(5_000L, 2, SHARK, 1000, 950L)));

        assertEquals(Long.valueOf(131_000L), cache.getSummary().total_profit_gp);
    }

    // ---- what the alt was handed is the alt's ----

    @Test
    public void theAltCanMoveWhatItWasHandedOnToAnotherAlt() {
        RecipeFlipStore store = new RecipeFlipStore();
        store.add(MAIN, move(BOUGHT, 1000, ALT, 9_000L));
        // The alt's record names the purchase it was handed, which is the main's trade.
        store.add(ALT, move(BOUGHT, 1000, OTHER_ALT, 9_500L));

        assertEquals(131_000L, historyProfit(store, OTHER_ALT, theAltsSales()));
        assertEquals(Long.valueOf(131_000L), summary(store, OTHER_ALT, theAltsSales()).total_profit_gp);
        assertEquals("the alt no longer has them to sell", 0L, historyProfit(store, ALT, theAltsSales()));
        assertEquals(Long.valueOf(0L), summary(store, ALT, theAltsSales()).total_profit_gp);
    }

    @Test
    public void theAltCanMakeSomethingOfWhatItWasHanded() {
        // The main buys the blade and hands it over; the alt buys the hilt, makes the godsword
        // and sells it. Paid 15,000,000 between them, 18,130,000 received after tax.
        Delta blade = buy(1_000L, 1, BLADE, 1, 4_000_000L);
        Delta hilt = buy(2_000L, 2, HILT, 1, 11_000_000L);
        Delta sale = sell(3_000L, 3, GODSWORD, 1, 18_500_000L);
        List<Delta> alts = Arrays.asList(hilt, sale);
        RecipeFlipStore store = new RecipeFlipStore();
        RecipeFlip handed = new RecipeFlip(ConversionKind.TRANSFER, "Blade to Alt",
            Collections.singletonList(new RecipeFlip.Part(TradeKey.of(blade), 1, 4_000_000L)), null, 0L, 8_000L, ALT, null);
        store.add(MAIN, handed);
        store.add(ALT, new RecipeFlip(ConversionKind.ASSEMBLE, "Armadyl godsword",
            Arrays.asList(new RecipeFlip.Part(TradeKey.of(blade), 1, null), new RecipeFlip.Part(TradeKey.of(hilt), 1, null)),
            Collections.singletonList(new RecipeFlip.Part(TradeKey.of(sale), 1, null)), 0L, 9_000L, null, null));

        assertEquals(3_130_000L, historyProfit(store, ALT, alts));
        assertEquals(Long.valueOf(3_130_000L), summary(store, ALT, alts).total_profit_gp);

        // Forgotten on the main, the blade was never the alt's: the recipe stops applying.
        store.remove(MAIN, handed);
        assertTrue(RecipeFlipLedger.apply(alts, store, ALT).activities.isEmpty());
    }

    @Test
    public void theAltsRecordIsSentNamingThePurchaseItWasHanded() {
        RecipeFlipStore store = new RecipeFlipStore();
        store.add(MAIN, move(BOUGHT, 1000, ALT, 9_000L));
        RecipeFlip onward = move(BOUGHT, 1000, OTHER_ALT, 9_500L);

        List<GeEvent> events = RecipeUpload.parts(ALT, onward, new ArrayList<>(), store);

        assertEquals(1, events.size());
        GeEvent event = events.get(0);
        assertEquals(GeEvent.characterId(ALT), event.character_id);
        assertEquals(GeEvent.characterId(OTHER_ALT), event.recipe_to_character_id);
        assertEquals("the main's purchase", 1_000L, event.ts_client_ms);
        assertEquals(1, event.slot);
        assertEquals(1000, event.delta_qty);
        assertEquals(800_000L, event.delta_gp);
        assertTrue("before the main's file is read there is nothing to send, not half",
            RecipeUpload.parts(ALT, onward, new ArrayList<>(), new RecipeFlipStore()).isEmpty());
    }

    /**
     * Profile files are read one at a time, so the alt's file can be read before the main's. The
     * alt's onward move names the main's purchase and cannot be sent until the main's move is in:
     * it must not be counted as sent before then, and the next file read has to try every
     * account's records again, not only the one just read.
     */
    @Test
    public void aRecordIsSentOnceEverythingItNamesHasBeenRead() throws Exception {
        PluginState state = new PluginState();
        PluginConfig linked = (PluginConfig) java.lang.reflect.Proxy.newProxyInstance(
            PluginConfig.class.getClassLoader(), new Class<?>[] {PluginConfig.class},
            (proxy, method, args) -> "sessionToken".equals(method.getName()) ? "token"
                : method.getReturnType() == boolean.class ? Boolean.TRUE : null);
        UploadEventDispatch queue = new UploadEventDispatch(state, null);
        UploadBackfillDispatch flush = new UploadBackfillDispatch(null, queue, linked, null);
        // A flush already under way, so this one does not reach for the running plugin.
        java.lang.reflect.Field inFlight = UploadBackfillDispatch.class.getDeclaredField("flushInFlight");
        inFlight.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicBoolean) inFlight.get(flush)).set(true);
        RecipeFlipStore store = new RecipeFlipStore();
        RecipeUpload upload = new RecipeUpload(linked,
            new TradeSession(state, null, new TradeAnalytics(), null, null), store, queue, flush);

        store.add(ALT, move(BOUGHT, 1000, OTHER_ALT, 9_500L));
        upload.sendStored();
        assertEquals("the main's file is not read yet: nothing to send", 0,
            state.getUploadState().getPendingUploadEvents());

        store.add(MAIN, move(BOUGHT, 1000, ALT, 9_000L));
        state.getLocalTradeDeltasByAccount().put(MAIN, new ArrayList<>(Collections.singletonList(BOUGHT)));
        upload.sendStored();
        assertEquals("both moves, one part each", 2, state.getUploadState().getPendingUploadEvents());

        upload.sendStored();
        assertEquals("and nothing twice", 2, state.getUploadState().getPendingUploadEvents());
    }

    /** An upload that is linked while {@code linked[0]} says so, queueing into {@code state}. */
    private static RecipeUpload upload(PluginState state, RecipeFlipStore store, boolean[] linked) throws Exception {
        PluginConfig config = (PluginConfig) java.lang.reflect.Proxy.newProxyInstance(
            PluginConfig.class.getClassLoader(), new Class<?>[] {PluginConfig.class},
            (proxy, method, args) -> "sessionToken".equals(method.getName()) ? (linked[0] ? "token" : "")
                : method.getReturnType() == boolean.class ? Boolean.TRUE : null);
        UploadEventDispatch queue = new UploadEventDispatch(state, null);
        UploadBackfillDispatch flush = new UploadBackfillDispatch(null, queue, config, null);
        java.lang.reflect.Field inFlight = UploadBackfillDispatch.class.getDeclaredField("flushInFlight");
        inFlight.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicBoolean) inFlight.get(flush)).set(true);
        return new RecipeUpload(config, new TradeSession(state, null, new TradeAnalytics(), null, null), store, queue, flush);
    }

    private static List<GeEvent> sentSoFar(PluginState state) {
        List<GeEvent> out = new ArrayList<>();
        GeEvent event;
        while ((event = state.getUploadState().dequeueEvent()) != null) {
            out.add(event);
        }
        return out;
    }

    /**
     * Forgotten while not linked, the website was never told, and nothing was left to tell it later:
     * it kept the recipe for ever. What is left of a forgotten record is kept in the file and its
     * forgetting is sent again each session, the same way a record is. Found by the final audit.
     */
    @Test
    public void aRecordForgottenWhileNotLinkedIsForgottenOnTheWebsiteOnceLinked() throws Exception {
        PluginState state = new PluginState();
        state.getLocalTradeDeltasByAccount().put(MAIN, new ArrayList<>(Collections.singletonList(BOUGHT)));
        RecipeFlipStore store = new RecipeFlipStore();
        boolean[] linked = {false};
        RecipeUpload upload = upload(state, store, linked);
        RecipeFlip move = move(BOUGHT, 1000, ALT, 9_000L);
        store.add(MAIN, move);

        assertTrue(store.remove(MAIN, move));
        upload.sendDeleted(MAIN, move);
        assertTrue("not linked: nothing goes", sentSoFar(state).isEmpty());
        assertTrue("and it no longer counts", store.applicable(MAIN).isEmpty());
        assertTrue(RecipeFlipLedger.received(store, ALT, new java.util.HashSet<>()).isEmpty());

        // It survives the file, and a build from before this passes over it.
        String json = new Gson().toJson(store.snapshotForFile(MAIN));
        RecipeFlip[] read = new Gson().fromJson(json, RecipeFlip[].class);
        assertFalse(read[0].isUsable());
        RecipeFlipStore reloaded = new RecipeFlipStore();
        reloaded.replace(MAIN, Arrays.asList(read));

        linked[0] = true;
        RecipeUpload later = upload(state, reloaded, linked);
        later.sendStored();
        List<GeEvent> sent = sentSoFar(state);
        assertEquals(1, sent.size());
        assertEquals("RECIPE_VOID", sent.get(0).event_type);
        assertEquals("the id the website knows it by", RecipeUpload.recipeId(MAIN, move), sent.get(0).recipe_id);
        assertEquals(RecipeUpload.deleted(MAIN, move).event_id, sent.get(0).event_id);

        later.sendStored();
        assertTrue("once a session", sentSoFar(state).isEmpty());
    }

    /** Linked again, or to another website account: everything goes again, which the website takes as duplicates. */
    @Test
    public void relinkingSendsEveryRecordAgain() throws Exception {
        PluginState state = new PluginState();
        state.getLocalTradeDeltasByAccount().put(MAIN, new ArrayList<>(Collections.singletonList(BOUGHT)));
        RecipeFlipStore store = new RecipeFlipStore();
        store.add(MAIN, move(BOUGHT, 1000, ALT, 9_000L));
        RecipeUpload upload = upload(state, store, new boolean[] {true});
        upload.sendStored();
        assertEquals(1, sentSoFar(state).size());
        upload.sendStored();
        assertTrue(sentSoFar(state).isEmpty());

        upload.relinked();

        assertEquals(1, sentSoFar(state).size());
    }

    // ---- changing your mind ----

    @Test
    public void forgettingTheMovePutsBothBooksBack() {
        RecipeFlipStore store = new RecipeFlipStore();
        RecipeFlip move = move(BOUGHT, 1000, ALT, 9_000L);
        store.add(MAIN, move);

        assertTrue(store.remove(MAIN, move));

        assertTrue(RecipeFlipLedger.received(store, ALT, new java.util.HashSet<>()).isEmpty());
        assertEquals(0L, historyProfit(store, ALT, theAltsSales()));
        List<Delta> mains = Collections.singletonList(BOUGHT);
        assertTrue(RecipeFlipLedger.apply(mains, store, MAIN).isEmpty());
    }

    @Test
    public void halfToOneAltAndHalfToAnotherAreForgottenOneAtATime() {
        // Two records naming the same purchase. Told apart by when they were recorded, which is
        // also what tells them apart on the website.
        RecipeFlipStore store = new RecipeFlipStore();
        RecipeFlip toAlt = move(BOUGHT, 500, ALT, 9_000L);
        RecipeFlip toOther = move(BOUGHT, 500, OTHER_ALT, 9_500L);
        store.add(MAIN, toAlt);
        store.add(MAIN, toOther);

        assertTrue(store.remove(MAIN, toOther));

        assertEquals(1, store.applicable(MAIN).size());
        assertEquals(500, RecipeFlipLedger.received(store, ALT, new java.util.HashSet<>()).get(0).deltaQty);
        assertTrue(RecipeFlipLedger.received(store, OTHER_ALT, new java.util.HashSet<>()).isEmpty());
    }

    // ---- stock that is no longer the giver's to give (the final audit, 22 Sep 2026) ----

    /** Runs the check with each account's trades where the running plugin keeps them, so a move is judged where it was made. */
    private static void withTrades(Map<Long, List<Delta>> trades, Runnable check) {
        PluginState state = new PluginState();
        trades.forEach((account, list) -> state.getLocalTradeDeltasByAccount().put(account, new ArrayList<>(list)));
        TradeSession session = new TradeSession(state, null, new TradeAnalytics(), null, null);
        Bridge.set(com.google.inject.Guice.createInjector(binder -> binder.bind(TradeSession.class).toInstance(session)));
        try {
            check.run();
        } finally {
            Bridge.set(null);
        }
    }

    private static Map<Long, List<Delta>> trades(Object... accountThenTrades) {
        Map<Long, List<Delta>> out = new java.util.HashMap<>();
        for (int i = 0; i < accountThenTrades.length; i += 2) {
            @SuppressWarnings("unchecked")
            List<Delta> list = (List<Delta>) accountThenTrades[i + 1];
            out.put((Long) accountThenTrades[i], list);
        }
        return out;
    }

    @Test
    public void forgettingTheFirstOfTwoMovesTakesTheStockBackFromTheLastAccountToo() {
        // Main to alt, then alt on to the other alt, which sells. Forget the first: the purchase is
        // the main's again, so the alt had nothing to pass on and the other alt nothing to sell.
        // The other alt used to keep its 131,000 while the main held the same 1,000 again.
        RecipeFlipStore store = new RecipeFlipStore();
        RecipeFlip first = move(BOUGHT, 1000, ALT, 9_000L);
        store.add(MAIN, first);
        store.add(ALT, move(BOUGHT, 1000, OTHER_ALT, 9_500L));
        List<Delta> mains = Collections.singletonList(BOUGHT);
        withTrades(trades(MAIN, mains, ALT, new ArrayList<Delta>(), OTHER_ALT, theAltsSales()), () -> {
            assertEquals(131_000L, historyProfit(store, OTHER_ALT, theAltsSales()));

            store.remove(MAIN, first);

            assertTrue(RecipeFlipLedger.received(store, OTHER_ALT, new java.util.HashSet<>()).isEmpty());
            assertEquals(0L, historyProfit(store, OTHER_ALT, theAltsSales()));
            assertEquals(Long.valueOf(0L), summary(store, OTHER_ALT, theAltsSales()).total_profit_gp);
            assertEquals("the main holds them again", 1000, left(RecipeFlipLedger.apply(mains, store, MAIN)
                .remainingTrades(mains), SHARK));
        });
    }

    @Test
    public void aMoveOfStockAnOlderRecordAlreadyTookHandsOverNothing() {
        // Two records on the main both claim the whole purchase: a second client, or a file edited
        // by hand. The older one has it; the newer one hands the other alt nothing.
        RecipeFlipStore store = new RecipeFlipStore();
        store.add(MAIN, move(BOUGHT, 1000, ALT, 9_000L));
        store.add(MAIN, move(BOUGHT, 1000, OTHER_ALT, 9_500L));
        withTrades(trades(MAIN, Collections.singletonList(BOUGHT)), () -> {
            assertEquals(1000, RecipeFlipLedger.received(store, ALT, new java.util.HashSet<>()).get(0).deltaQty);
            assertTrue(RecipeFlipLedger.received(store, OTHER_ALT, new java.util.HashSet<>()).isEmpty());
        });
    }

    @Test
    public void twoLotsOfOnePurchaseHandedToOneAccountAreOneLotThatCanAllBeUsed() {
        // 400 and then 300 of the same purchase to the alt, and both moved on. The alt's two rows
        // shared the purchase's name, only the first was ever found, and the 300 moved on stayed on
        // the alt's books as well as the other alt's - which the website was sent at the wrong price.
        RecipeFlipStore store = new RecipeFlipStore();
        store.add(MAIN, move(BOUGHT, 400, ALT, 9_000L));
        store.add(MAIN, move(BOUGHT, 300, ALT, 9_100L));
        withTrades(trades(MAIN, Collections.singletonList(BOUGHT), ALT, new ArrayList<Delta>()), () -> {
            List<Delta> handed = RecipeFlipLedger.received(store, ALT, new java.util.HashSet<>());
            assertEquals(1, handed.size());
            assertEquals(700, handed.get(0).deltaQty);
            assertEquals(560_000L, handed.get(0).deltaGp);

            RecipeFlip onward400 = move(BOUGHT, 400, OTHER_ALT, 9_500L);
            onward400.inputs.get(0).gp = 320_000L;
            store.add(ALT, onward400);
            RecipeFlip onward300 = move(BOUGHT, 300, OTHER_ALT, 9_600L);
            onward300.inputs.get(0).gp = 240_000L;
            store.add(ALT, onward300);

            assertEquals("the alt has nothing left", 0,
                left(RecipeFlipLedger.apply(new ArrayList<>(), store, ALT).remainingTrades(handed), SHARK));
            assertEquals(700, RecipeFlipLedger.received(store, OTHER_ALT, new java.util.HashSet<>()).get(0).deltaQty);
            assertEquals("the website is sent the 400's share of the coins", 320_000L,
                RecipeUpload.parts(ALT, onward400, new ArrayList<>(), store).get(0).delta_gp);
        });
    }

    @Test
    public void wipingTheAccountThatWasHandedStockTakesItOffThatAccountsBooks() throws Exception {
        // The move lives in the main's file, where wiping the alt could not reach. It went on
        // handing the wiped alt 1,000 sharks it had no record of, and 1,000 more moved later at 900
        // were pooled with them at 850: 81,000 booked on a sale that made 31,000.
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("moved-then-wiped");
        RecipeFlipStore store = new RecipeFlipStore();
        store.add(MAIN, move(BOUGHT, 1000, ALT, 9_000L));
        ProfileStore files = new ProfileStore(new Gson(), "fliphub", "fliphub-dev", dir);
        Bridge.set(com.google.inject.Guice.createInjector(binder -> {
            binder.bind(ProfileStore.class).toInstance(files);
            binder.bind(RecipeFlipStore.class).toInstance(store);
        }));
        try {
            PluginState state = new PluginState();
            assertTrue(new ProfileWipeDataService(state, new Gson(), new ProfileStorage(state), store)
                .clearProfileDataForWipe(ALT, "Alt"));
        } finally {
            Bridge.set(null);
        }

        assertTrue(RecipeFlipLedger.received(store, ALT, new java.util.HashSet<>()).isEmpty());
        List<Delta> mains = Collections.singletonList(BOUGHT);
        assertEquals("the main gave them away all the same", 0,
            left(RecipeFlipLedger.apply(mains, store, MAIN).remainingTrades(mains), SHARK));

        Delta later = buy(2_000L, 2, SHARK, 1000, 900_000L);
        store.add(MAIN, move(later, 1000, ALT, System.currentTimeMillis() + 60_000L));
        List<Delta> handed = RecipeFlipLedger.received(store, ALT, new java.util.HashSet<>());
        assertEquals("what is moved to it after the wipe is its own", 1, handed.size());
        assertEquals(900_000L, handed.get(0).deltaGp);
    }

    /**
     * A character filed under its name's key, later under its account hash (AccountMerge folds its
     * trades across). Moves to the old key, and its own records, now count for the new key - and
     * stay filed under the old one, whose key their website ids are made from.
     */
    @Test
    public void aCharacterFoldedIntoItsHashKeepsWhatWasMovedToItsNameAndWhatItRecorded() {
        long nameKey = 13886278L;
        long hashKey = 4242L;
        RecipeFlipStore store = new RecipeFlipStore();
        store.add(MAIN, move(BOUGHT, 1000, nameKey, 9_000L));
        RecipeFlip onward = move(BOUGHT, 400, OTHER_ALT, 9_500L);
        store.add(nameKey, onward);

        store.fold(nameKey, hashKey);

        assertEquals(1000, RecipeFlipLedger.received(store, hashKey, new java.util.HashSet<>()).get(0).deltaQty);
        assertTrue("nothing is left at the old key", RecipeFlipLedger.received(store, nameKey, new java.util.HashSet<>()).isEmpty());
        assertEquals(Collections.singletonList(onward), store.applicable(hashKey));
        assertEquals("filed where it was, so its website id is unchanged", 1, store.snapshotForFile(nameKey).size());
        assertEquals(400 * 131L, historyProfit(store, OTHER_ALT, Collections.singletonList(sell(5_000L, 2, SHARK, 400, 950L))));
    }

    /**
     * The other way round: the folded character GAVE. Its purchase is filed under the hash key now,
     * so its move has to be judged there. Looked for under the old key it found no trades, and the
     * account it handed stock to lost it.
     */
    @Test
    public void aMoveMadeBeforeTheFoldStillHandsItsStockOver() {
        long nameKey = 13886278L;
        long hashKey = 4242L;
        RecipeFlipStore store = new RecipeFlipStore();
        store.add(nameKey, move(BOUGHT, 1000, OTHER_ALT, 9_000L));
        store.fold(nameKey, hashKey);

        withTrades(trades(hashKey, Collections.singletonList(BOUGHT)), () ->
            assertEquals(1000, RecipeFlipLedger.received(store, OTHER_ALT, new java.util.HashSet<>()).get(0).deltaQty));
    }

    /** Every coin of a split purchase goes somewhere: 1 of 3 bought for 1,000 was 333 moved and 666 kept. */
    @Test
    public void aSplitPurchaseLosesNoCoin() {
        Delta three = buy(1_000L, 1, SHARK, 3, 1_000L);
        List<Delta> mains = Collections.singletonList(three);
        RecipeFlip one = move(three, 1, ALT, 9_000L);

        RecipeFlipLedger.Result result = RecipeFlipLedger.apply(mains, Collections.singletonList(one));

        long kept = result.remainingTrades(mains).get(0).deltaGp;
        assertEquals(333L, (long) one.inputs.get(0).gp);
        assertEquals(1_000L, kept + one.inputs.get(0).gp);
    }

    @Test
    public void aRoundTripDoesNotGoRoundForEver() {
        RecipeFlipStore store = new RecipeFlipStore();
        store.add(MAIN, move(BOUGHT, 1000, ALT, 9_000L));
        store.add(ALT, move(BOUGHT, 1000, MAIN, 9_500L));
        withTrades(trades(MAIN, Collections.singletonList(BOUGHT)), () ->
            assertEquals(1000, RecipeFlipLedger.received(store, ALT, new java.util.HashSet<>()).get(0).deltaQty));
    }

    // ---- two clients, one folder ----

    /**
     * A record of a kind this build does not know, as a newer build would write one. Read, it used
     * to be dropped, and the next save wrote the file back without it: the newer build lost it.
     */
    @Test
    public void aRecordOfAKindThisBuildDoesNotKnowIsKeptButNeverUsed() throws Exception {
        String json = "[{\"kind\":\"SMELT\",\"name\":\"Bars\",\"inputs\":[{\"trade\":{\"tsMs\":1000,\"slot\":1,"
            + "\"itemId\":385},\"quantity\":1000}],\"outputs\":[{\"trade\":{\"tsMs\":5000,\"slot\":2,\"itemId\":385},"
            + "\"quantity\":400}],\"feeGp\":0,\"recordedMs\":9000,\"toAccount\":8}]";
        RecipeFlip[] read = new Gson().fromJson(json, RecipeFlip[].class);
        assertNull("the kind reads as nothing", read[0].kind);
        RecipeFlipStore store = new RecipeFlipStore();

        store.replace(MAIN, Arrays.asList(read));

        assertEquals("kept for the file", 1, store.snapshotForFile(MAIN).size());
        assertTrue("never applied", store.applicable(MAIN).isEmpty());
        assertTrue("never a move", RecipeFlipLedger.received(store, ALT, new java.util.HashSet<>()).isEmpty());
        // Its trades are all here, so nothing but the kind stands between it and the website.
        PluginState state = new PluginState();
        state.getLocalTradeDeltasByAccount().put(MAIN, new ArrayList<>(Arrays.asList(
            BOUGHT, sell(5_000L, 2, SHARK, 400, 950L))));
        upload(state, store, new boolean[] {true}).sendStored();
        assertTrue("never sent", sentSoFar(state).isEmpty());
    }

    @Test
    public void loadingAFileSaysWhetherAMoveCameOrWent() {
        RecipeFlipStore store = new RecipeFlipStore();
        List<RecipeFlip> aMove = Collections.singletonList(move(BOUGHT, 1000, ALT, 9_000L));

        assertFalse("nothing before, nothing now", store.replace(MAIN, new ArrayList<>()));
        assertTrue("another client recorded one", store.replace(MAIN, aMove));
        assertTrue("still there: the alt's totals still rest on it", store.replace(MAIN, aMove));
        assertTrue("another client forgot it", store.replace(MAIN, new ArrayList<>()));
        assertFalse(store.replace(MAIN, new ArrayList<>()));
    }

    @Test
    public void aMoveSurvivesTheProfileFileAndARecipeIsWrittenAsItAlwaysWas() {
        Gson gson = new Gson();
        ProfileData data = new ProfileData();
        data.recipeFlips = Collections.singletonList(move(BOUGHT, 600, ALT, 9_000L));

        ProfileData back = gson.fromJson(gson.toJson(data), ProfileData.class);

        RecipeFlip move = back.recipeFlips.get(0);
        assertTrue(move.isUsable());
        assertEquals(ConversionKind.TRANSFER, move.kind);
        assertEquals(Long.valueOf(ALT), move.toAccount);
        assertEquals(Long.valueOf(480_000L), move.inputs.get(0).gp);

        RecipeFlip recipe = new RecipeFlip(ConversionKind.ASSEMBLE, "x",
            Collections.singletonList(new RecipeFlip.Part(new TradeKey(1L, 1, BLADE), 1, null)),
            Collections.singletonList(new RecipeFlip.Part(new TradeKey(2L, 2, GODSWORD), 1, null)), 0L, 3L, null, null);
        String json = gson.toJson(recipe);
        assertFalse(json, json.contains("toAccount"));
        assertFalse(json, json.contains("\"gp\""));
    }

    // ---- telling the website ----

    @Test
    public void theWebsiteIsToldWhichPurchaseWentToWhichCharacter() {
        List<GeEvent> events =
            RecipeUpload.parts(MAIN, move(BOUGHT, 600, ALT, 9_000L), Collections.singletonList(BOUGHT), new RecipeFlipStore());

        assertEquals("one purchase is a whole move", 1, events.size());
        GeEvent event = events.get(0);
        assertEquals("RECIPE_IN", event.event_type);
        assertEquals("TRANSFER", event.recipe_kind);
        assertEquals(Integer.valueOf(1), event.recipe_parts);
        assertEquals(GeEvent.characterId(MAIN), event.character_id);
        assertEquals(GeEvent.characterId(ALT), event.recipe_to_character_id);
        assertEquals(SHARK, event.item_id);
        assertEquals(1, event.slot);
        assertEquals(1_000L, event.ts_client_ms);
        assertTrue(event.is_buy);
        assertEquals(600, event.delta_qty);
        assertEquals(480_000L, event.delta_gp);
        assertEquals(Long.valueOf(0L), event.recipe_fee_gp);
    }

    @Test
    public void aRecipeAndATradeSayNothingAboutAnotherCharacter() {
        Delta blade = buy(1_000L, 1, BLADE, 1, 4_000_000L);
        Delta sale = sell(3_000L, 3, GODSWORD, 1, 18_500_000L);
        RecipeFlip recipe = new RecipeFlip(ConversionKind.ASSEMBLE, "x",
            Collections.singletonList(new RecipeFlip.Part(TradeKey.of(blade), 1, null)),
            Collections.singletonList(new RecipeFlip.Part(TradeKey.of(sale), 1, null)), 0L, 9_000L, null, null);

        for (GeEvent event : RecipeUpload.parts(MAIN, recipe, Arrays.asList(blade, sale), new RecipeFlipStore())) {
            assertNull(event.recipe_to_character_id);
            assertFalse(new Gson().toJson(event).contains("recipe_to_character_id"));
        }
        assertFalse(new Gson().toJson(new GeEvent()).contains("recipe_to_character_id"));
    }

    @Test
    public void twoHalvesOfOnePurchaseAreTwoMovesToTheWebsiteToo() {
        String toAlt = RecipeUpload.recipeId(MAIN, move(BOUGHT, 500, ALT, 9_000L));
        String toOther = RecipeUpload.recipeId(MAIN, move(BOUGHT, 500, OTHER_ALT, 9_500L));

        assertFalse(toAlt.equals(toOther));
    }
}
