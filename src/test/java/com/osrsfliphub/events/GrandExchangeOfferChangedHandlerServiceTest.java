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

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Providers;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Player;
import net.runelite.api.events.GrandExchangeOfferChanged;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Drives the real {@link GrandExchangeOfferChangedHandler#handle} end to end:
 * snapshot, stamp tracking, {@link OfferEventBuild#derive}, the deduper, the
 * upload queue and the local trade recorder are all the production classes. Only the
 * RuneLite client, the config and the persistence/UI edges are stubbed.
 */
public class GrandExchangeOfferChangedHandlerServiceTest {
    private static final long ACCOUNT_HASH = 4242L;
    private static final int WORLD = 301;
    private static final int SLOT = 2;
    private static final int ITEM = 4151;
    private static final int PRICE = 100;

    private PluginState state;
    private OfferStampStateServices stampState;
    private GeLifecyclePlugin plugin;
    private GrandExchangeOfferChangedHandler handler;
    private final List<GeEvent> uploads = new ArrayList<>();
    private volatile boolean linked = true;
    private volatile GameState gameState = GameState.LOGGED_IN;
    private volatile long accountHash = ACCOUNT_HASH;
    private volatile String playerName;

    @Before
    public void setUp() {
        state = new PluginState();
        OfferUpdateStamp stampService = new OfferUpdateStamp();
        stampState = new OfferStampStateServices(state, () -> null, () -> null, () -> null, () -> stampService);
        LocalTradesRuntime tradesRuntime = new LocalTradesRuntime(
            state, () -> null, () -> null, () -> null, () -> null, () -> null, () -> null, () -> new ProfileUi(null, null));
        Client client = client();
        PluginConfig config = config();
        plugin = new GeLifecyclePlugin();
        plugin.client = client;
        Access.set(plugin);
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(Client.class).toInstance(client);
                bind(PluginConfig.class).toInstance(config);
                bind(PluginState.class).toInstance(state);
                bind(OfferUpdateStamp.class).toInstance(stampService);
                bind(OfferStampStateServices.class).toInstance(stampState);
                bind(LocalTradesRuntime.class).toInstance(tradesRuntime);
                bind(ItemLookup.class).toProvider(Providers.<ItemLookup>of(null));
                bind(LocalStatsCacheService.class).toProvider(Providers.<LocalStatsCacheService>of(null));
                bind(PanelRefresh.class).toProvider(Providers.<PanelRefresh>of(null));
            }
        });
        Bridge.set(injector);
        handler = new GrandExchangeOfferChangedHandler(client, state);
    }

    @After
    public void tearDown() {
        Bridge.set(null);
        Access.set(null);
    }

    // ---- C-2: the stamp must be read before trackOfferUpdate advances it ----

    @Test
    public void loginReplayWithLowerPersistedStampProducesTheOfflineDelta() {
        long firstSeenMs = System.currentTimeMillis() - 60L * 60L * 1000L;
        persistStamp(4, 400L, firstSeenMs);
        stampState.setLastLoginNow();

        fire(GrandExchangeOfferState.BOUGHT, 10, 10, 1_000L);

        List<GeEvent> sent = uploads();
        assertEquals(1, sent.size());
        GeEvent event = sent.get(0);
        assertEquals("OFFER_COMPLETED", event.event_type);
        assertEquals(6, event.delta_qty);
        assertEquals(600L, event.delta_gp);

        List<Delta> recorded = recorded();
        assertEquals(1, recorded.size());
        assertEquals(6, recorded.get(0).deltaQty);
        assertEquals(600L, recorded.get(0).deltaGp);
    }

    /** A stored position is trusted the same whether or not the account is linked. */
    @Test
    public void unlinkedLoginReplayWithStampProducesTheOfflineDeltaToo() {
        linked = false;
        long firstSeenMs = System.currentTimeMillis() - 60L * 60L * 1000L;
        persistStamp(4, 400L, firstSeenMs);
        stampState.setLastLoginNow();

        fire(GrandExchangeOfferState.BOUGHT, 10, 10, 1_000L);

        List<GeEvent> sent = uploads();
        assertEquals(1, sent.size());
        assertEquals("OFFER_COMPLETED", sent.get(0).event_type);
        assertEquals(6, sent.get(0).delta_qty);
        assertEquals(600L, sent.get(0).delta_gp);
        List<Delta> recorded = recorded();
        assertEquals(1, recorded.size());
        assertEquals(6, recorded.get(0).deltaQty);
    }

    /**
     * Nothing was stored for this slot, so the reported fill is where the offer stands rather
     * than progress since it was last seen. Counting it would re-count whatever an earlier
     * session already recorded, so it is adopted silently instead.
     */
    @Test
    public void loginReplayWithoutAStampIsAdoptedAsTheNewPositionAndNotCounted() {
        stampState.setLastLoginNow();

        fire(GrandExchangeOfferState.BOUGHT, 10, 10, 1_000L);

        assertTrue(uploads().isEmpty());
        assertTrue(recorded().isEmpty());
    }

    /** Having adopted the position, the very next fill on that slot is measured from it. */
    @Test
    public void progressAfterAnAdoptedPositionCountsOnlyTheNewFill() {
        stampState.setLastLoginNow();
        fire(GrandExchangeOfferState.BUYING, 40, 100, 4_000L);
        assertTrue(uploads().isEmpty());

        fire(GrandExchangeOfferState.BUYING, 70, 100, 7_000L);

        List<GeEvent> sent = uploads();
        assertEquals(1, sent.size());
        assertEquals(30, sent.get(0).delta_qty);
        assertEquals(3_000L, sent.get(0).delta_gp);
    }

    @Test
    public void aLiveTradeSaysWhichCharacterMadeIt() {
        fire(GrandExchangeOfferState.EMPTY, 0, 0, 0L);
        fire(GrandExchangeOfferState.BUYING, 0, 10, 0L);

        assertEquals(GeEvent.characterId(ACCOUNT_HASH), uploads().get(0).character_id);
    }

    /**
     * About half of all characters have a negative account hash and are filed under their name's
     * key instead. Their live trades went up with no code at all, so the website paired them with
     * any character's purchases, while the same character's stored trades, recipes and moves were
     * sent under the name's key.
     */
    @Test
    public void aCharacterFiledUnderItsNameIsTaggedWithTheKeyItsTradesAreStoredUnder() {
        accountHash = -779575573390842518L;
        playerName = "Sips Potion";
        long nameKey = 13886278L;

        fire(GrandExchangeOfferState.EMPTY, 0, 0, 0L);
        fire(GrandExchangeOfferState.BUYING, 0, 10, 0L);
        fire(GrandExchangeOfferState.BOUGHT, 10, 10, 1_000L);

        List<GeEvent> sent = uploads();
        assertEquals(2, sent.size());
        assertEquals(GeEvent.characterId(nameKey), sent.get(0).character_id);
        assertEquals(GeEvent.characterId(nameKey), sent.get(1).character_id);
        synchronized (state.getLocalStatsLock()) {
            assertEquals(1, state.getLocalTradeDeltasByAccount().get(nameKey).size());
        }
    }

    @Test
    public void emptyToNewBuyOfferEmitsOfferPlaced() {
        fire(GrandExchangeOfferState.EMPTY, 0, 0, 0L);

        fire(GrandExchangeOfferState.BUYING, 0, 10, 0L);

        List<GeEvent> sent = uploads();
        assertEquals(1, sent.size());
        assertEquals("OFFER_PLACED", sent.get(0).event_type);
        assertEquals(0, sent.get(0).delta_qty);
        assertTrue(recorded().isEmpty());
    }

    /**
     * Enabling the plugin while an offer is already part filled is the same situation as a
     * login with no stored position: how much of that fill is new is unknowable, so it is
     * adopted rather than guessed at.
     */
    @Test
    public void anOfferFirstSeenAlreadyFilledIsAdoptedRatherThanCounted() {
        fire(GrandExchangeOfferState.BUYING, 50, 50, 5_000L);

        assertTrue(uploads().isEmpty());
        assertTrue(recorded().isEmpty());
    }

    // ---- H-1: a completion that fills the remainder is not a duplicate of the partial fill ----

    /**
     * The upload queue sees the offer as the pipeline reported it, chunk by chunk. Locally
     * the completion folds the offer into one record holding its totals, timed at the first
     * fill and marked with when it completed.
     */
    @Test
    public void partialFillThenCompletionKeepsTheCompletionsQuantityAndCoins() {
        fire(GrandExchangeOfferState.EMPTY, 0, 0, 0L);
        fire(GrandExchangeOfferState.BUYING, 0, 10, 0L);
        fire(GrandExchangeOfferState.BUYING, 4, 10, 400L);

        fire(GrandExchangeOfferState.BOUGHT, 10, 10, 1_000L);

        List<GeEvent> sent = uploads();
        assertEquals(3, sent.size());
        assertEquals("OFFER_UPDATED", sent.get(1).event_type);
        assertEquals(4, sent.get(1).delta_qty);
        GeEvent completion = sent.get(2);
        assertEquals("OFFER_COMPLETED", completion.event_type);
        assertEquals(6, completion.delta_qty);
        assertEquals(600L, completion.delta_gp);

        List<Delta> recorded = recorded();
        assertEquals(1, recorded.size());
        Delta offer = recorded.get(0);
        assertEquals("OFFER_COMPLETED", offer.eventType);
        assertEquals(10, offer.deltaQty);
        assertEquals(1_000L, offer.deltaGp);
        assertEquals(sent.get(1).ts_client_ms, offer.tsClientMs);
        assertEquals(completion.ts_client_ms, offer.endMs);
        assertTrue(offer.offerStartMs > 0);
    }

    /**
     * 25,000 logs asked for, 21,780 bought, the rest cancelled. The cancel ends the offer as surely
     * as a last fill would, so what it bought is stored as one finished trade. Left as loose fills,
     * it could never be recorded as moved to another account or used in a recipe. Found by the
     * final audit, 22 Sep 2026.
     */
    @Test
    public void aCancelledOfferIsStoredAsOneFinishedTradeTheRecorderCanOffer() {
        fire(GrandExchangeOfferState.EMPTY, 0, 0, 0L);
        fire(GrandExchangeOfferState.BUYING, 0, 10, 0L);
        fire(GrandExchangeOfferState.BUYING, 3, 10, 300L);
        fire(GrandExchangeOfferState.BUYING, 4, 10, 400L);

        fire(GrandExchangeOfferState.CANCELLED_BUY, 4, 10, 400L);

        List<Delta> recorded = recorded();
        assertEquals(1, recorded.size());
        Delta trade = recorded.get(0);
        assertEquals("OFFER_COMPLETED", trade.eventType);
        assertEquals(4, trade.deltaQty);
        assertEquals(400L, trade.deltaGp);
        assertTrue("it is known to be over", trade.endMs > 0);
        assertEquals(1, RecipeRecorder.offerable(recorded, RecipeFlipLedger.empty(), new java.util.HashSet<>()).size());
        assertEquals("the website is told what happened, as before", "OFFER_ABORTED",
            uploads().get(uploads().size() - 1).event_type);
    }

    @Test
    public void anOfferCancelledBeforeAnyFillStoresNothing() {
        fire(GrandExchangeOfferState.EMPTY, 0, 0, 0L);
        fire(GrandExchangeOfferState.BUYING, 0, 10, 0L);

        fire(GrandExchangeOfferState.CANCELLED_BUY, 0, 10, 0L);

        assertTrue(recorded().isEmpty());
    }

    /** Until the completion arrives, every chunk is its own record: the offer counts as it fills. */
    @Test
    public void anOfferStillFillingIsStoredFillByFillUntilItCompletes() {
        fire(GrandExchangeOfferState.EMPTY, 0, 0, 0L);
        fire(GrandExchangeOfferState.BUYING, 0, 10, 0L);
        fire(GrandExchangeOfferState.BUYING, 3, 10, 300L);
        fire(GrandExchangeOfferState.BUYING, 7, 10, 700L);

        List<Delta> filling = recorded();
        assertEquals(2, filling.size());
        assertEquals(3, filling.get(0).deltaQty);
        assertEquals(4, filling.get(1).deltaQty);
        assertEquals("OFFER_UPDATED", filling.get(1).eventType);
        assertEquals(0L, filling.get(1).endMs);

        fire(GrandExchangeOfferState.BOUGHT, 10, 10, 1_000L);

        List<Delta> done = recorded();
        assertEquals(1, done.size());
        assertEquals(10, done.get(0).deltaQty);
        assertEquals(1_000L, done.get(0).deltaGp);
    }

    /**
     * 5 filled, then BOUGHT 10/10: the completion's chunk is the same size as the update's.
     * The local append used to take that for a repeat and drop it, losing the second chunk.
     */
    @Test
    public void equalSizedChunksReachTheUploadQueueIntact() {
        fire(GrandExchangeOfferState.EMPTY, 0, 0, 0L);
        fire(GrandExchangeOfferState.BUYING, 0, 10, 0L);
        fire(GrandExchangeOfferState.BUYING, 5, 10, 500L);

        fire(GrandExchangeOfferState.BOUGHT, 10, 10, 1_000L);

        List<GeEvent> sent = uploads();
        assertEquals(3, sent.size());
        GeEvent completion = sent.get(2);
        assertEquals("OFFER_COMPLETED", completion.event_type);
        assertEquals(5, completion.delta_qty);
        assertEquals(500L, completion.delta_gp);

        List<Delta> recorded = recorded();
        assertEquals(1, recorded.size());
        assertEquals(10, recorded.get(0).deltaQty);
        assertEquals(1_000L, recorded.get(0).deltaGp);
    }

    /**
     * On the way out of LOGGED_IN the client empties all eight slots and reports each one.
     * Treating those as collections diffed them against the still-live snapshots and could
     * emit a completion, once per hop, for an offer that had not finished.
     */
    @Test
    public void theClientsSlotSweepOnLogoutIsNotATrade() {
        fire(GrandExchangeOfferState.EMPTY, 0, 0, 0L);
        fire(GrandExchangeOfferState.BUYING, 0, 10, 0L);
        // Filled but never collected, so the slot still holds it when the player logs out.
        fire(GrandExchangeOfferState.BOUGHT, 10, 10, 1_000L);
        int before = uploads().size();
        int recordedBefore = recorded().size();

        // A filled offer normally sits uncollected for far longer than the deduper's two
        // second window, so by logout it is no longer recognised as a repeat. Dropping the
        // slot's memory is how that gap looks to the handler.
        Bridge.get(RecentTradeDeduper.class).clearSlot(SLOT);
        gameState = GameState.LOGIN_SCREEN;
        fire(GrandExchangeOfferState.EMPTY, 0, 0, 0L);

        assertEquals(before, uploads().size());
        assertEquals(recordedBefore, recorded().size());
    }

    // ---- M-2: a slot reused for another offer with no EMPTY between is a baseline transition ----

    /**
     * The stored position belongs to the offer that used to hold this slot, so it says nothing
     * about the one there now. The new offer is adopted at the level it is first seen at, and
     * what matters is that none of the old offer's progress is ever credited to it.
     */
    @Test
    public void aReusedSlotIsAdoptedAndLaterFillsCreditOnlyTheNewItem() {
        int itemA = 4151;
        int itemB = 560;
        fire(SLOT, itemA, 7, GrandExchangeOfferState.EMPTY, 0, 0, 0L);
        fire(SLOT, itemA, 7, GrandExchangeOfferState.BUYING, 0, 5, 0L);
        fire(SLOT, itemA, 7, GrandExchangeOfferState.BOUGHT, 5, 5, 35L);

        // The EMPTY for item A was dropped; item B is first seen already 20/100 filled.
        fire(SLOT, itemB, 7, GrandExchangeOfferState.BUYING, 20, 100, 140L);
        assertEquals(2, uploads().size());

        fire(SLOT, itemB, 7, GrandExchangeOfferState.BUYING, 50, 100, 350L);

        List<GeEvent> sent = uploads();
        assertEquals(3, sent.size());
        GeEvent forB = sent.get(2);
        assertEquals(itemB, forB.item_id);
        assertEquals(30, forB.delta_qty);
        assertEquals(210L, forB.delta_gp);

        List<Delta> recorded = recorded();
        assertEquals(2, recorded.size());
        assertEquals(itemA, recorded.get(0).itemId);
        assertEquals(5, recorded.get(0).deltaQty);
        assertEquals(itemB, recorded.get(1).itemId);
        assertEquals(30, recorded.get(1).deltaQty);
        assertEquals(210L, recorded.get(1).deltaGp);
    }

    // ---- M-3: the collect after a completion is paired with it by a deterministic id ----

    @Test
    public void collectAfterCompletionSharesTheCompletionsDeterministicId() {
        fire(GrandExchangeOfferState.EMPTY, 0, 0, 0L);
        fire(GrandExchangeOfferState.BUYING, 0, 10, 0L);
        fire(GrandExchangeOfferState.BUYING, 4, 10, 400L);
        fire(GrandExchangeOfferState.BOUGHT, 10, 10, 1_000L);
        // The player collects well after the deduper's 2 s window has expired.
        Bridge.get(RecentTradeDeduper.class).clearSlot(SLOT);

        fire(GrandExchangeOfferState.EMPTY, 0, 0, 0L);

        List<GeEvent> sent = uploads();
        assertEquals(4, sent.size());
        GeEvent completion = sent.get(2);
        GeEvent collect = sent.get(3);
        assertEquals("OFFER_COMPLETED", completion.event_type);
        assertEquals("OFFER_COMPLETED", collect.event_type);
        assertEquals(0, collect.delta_qty);
        assertEquals(0L, collect.delta_gp);
        assertEquals(completion.event_id, collect.event_id);
        assertNotEquals(sent.get(1).event_id, collect.event_id);

        // Locally the offer is already one completed record; the collect closes nothing
        // and carries nothing, so it adds nothing.
        List<Delta> recorded = recorded();
        assertEquals(1, recorded.size());
        assertEquals("OFFER_COMPLETED", recorded.get(0).eventType);
        assertEquals(10, recorded.get(0).deltaQty);
    }

    // ---- helpers ----

    private void fire(GrandExchangeOfferState offerState, int filled, int total, long spent) {
        fire(SLOT, ITEM, PRICE, offerState, filled, total, spent);
    }

    private void fire(int slot, int itemId, int price, GrandExchangeOfferState offerState, int filled, int total, long spent) {
        GrandExchangeOfferChanged event = new GrandExchangeOfferChanged();
        event.setSlot(slot);
        event.setOffer(offer(itemId, price, total, filled, spent, offerState));
        handler.handle(event);
    }

    private void persistStamp(int filledQty, long spentGp, long firstSeenMs) {
        state.getOfferUpdateStamps().put(SLOT,
            new Stamp(ITEM, PRICE, 10, filledQty, true, spentGp, firstSeenMs, firstSeenMs, 0L, 0L));
    }

    private List<GeEvent> uploads() {
        GeEvent event;
        while ((event = state.getUploadState().dequeueEvent()) != null) {
            uploads.add(event);
        }
        return uploads;
    }

    private List<Delta> recorded() {
        synchronized (state.getLocalStatsLock()) {
            List<Delta> deltas = state.getLocalTradeDeltasByAccount().get(ACCOUNT_HASH);
            return deltas != null ? new ArrayList<>(deltas) : new ArrayList<>();
        }
    }

    private Client client() {
        return (Client) Proxy.newProxyInstance(
            Client.class.getClassLoader(),
            new Class<?>[] {Client.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getGameState":
                        return gameState;
                    case "getAccountHash":
                        return accountHash;
                    case "getLocalPlayer":
                        return playerName != null ? player(playerName) : null;
                    case "getWorld":
                        return WORLD;
                    case "toString":
                        return "client-stub";
                    default:
                        return defaultValue(method);
                }
            }
        );
    }

    private static Player player(String name) {
        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[] {Player.class},
            (proxy, method, args) -> "getName".equals(method.getName()) ? name : defaultValue(method)
        );
    }

    private PluginConfig config() {
        return (PluginConfig) Proxy.newProxyInstance(
            PluginConfig.class.getClassLoader(),
            new Class<?>[] {PluginConfig.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "enableFlipHubSync":
                        return linked;
                    case "sessionToken":
                        return linked ? "session-token" : "";
                    case "signingSecret":
                        return linked ? "signing-secret" : "";
                    case "toString":
                        return "config-stub";
                    default:
                        return defaultValue(method);
                }
            }
        );
    }

    private static GrandExchangeOffer offer(int itemId,
                                            int price,
                                            int totalQty,
                                            int filledQty,
                                            long spentGp,
                                            GrandExchangeOfferState offerState) {
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
                        return filledQty;
                    case "getSpent":
                        return (int) spentGp;
                    case "getState":
                        return offerState;
                    case "toString":
                        return "offer-stub";
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
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == double.class) {
            return 0d;
        }
        if (returnType == float.class) {
            return 0f;
        }
        if (returnType == char.class) {
            return '\0';
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        return 0;
    }
}
