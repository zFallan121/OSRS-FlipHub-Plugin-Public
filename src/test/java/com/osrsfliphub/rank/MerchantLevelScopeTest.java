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
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The Merchant level setting, read against characters' real stored trades.
 *
 * <p>The main has made 300M and the alt 50M: levels 63 and 45 on their own, 65 together.
 */
public class MerchantLevelScopeTest {
    private static final long MAIN = 1111L;
    private static final long ALT = 2222L;

    private final PluginState state = new PluginState();
    private volatile GameState gameState = GameState.LOGIN_SCREEN;
    private volatile long accountHash = -1L;
    private volatile String name;
    private volatile PluginConfig.MerchantLevelScope scope = PluginConfig.MerchantLevelScope.CHARACTER;
    private GeLifecyclePlugin plugin;
    private RankUp rankUp;

    @Before
    public void setUp() {
        flip(MAIN, 1_300_000_000L);
        flip(ALT, 1_050_000_000L);
        Player player = stub(Player.class, method -> "getName".equals(method) ? name : null);
        Client client = stub(Client.class, method -> "getGameState".equals(method) ? gameState
            : "getAccountHash".equals(method) ? (Object) accountHash
            : "getLocalPlayer".equals(method) && name != null ? player : null);
        PluginConfig config = stub(PluginConfig.class, method -> "merchantLevelScope".equals(method) ? scope : null);
        plugin = new GeLifecyclePlugin();
        plugin.client = client;
        plugin.config = config;
        plugin.scheduler = Executors.newSingleThreadScheduledExecutor();
        Access.set(plugin);
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(Client.class).toInstance(client);
                bind(PluginConfig.class).toInstance(config);
                bind(PluginState.class).toInstance(state);
                bind(Gson.class).toInstance(new Gson());
                bind(OkHttpClient.class).toInstance(new OkHttpClient());
                bind(ClientThread.class).toInstance(queueOnly());
                bind(ConfigManager.class).toInstance(unbuilt(ConfigManager.class));
                bind(ItemManager.class).toInstance(unbuilt(ItemManager.class));
                // Built without a RuneLite folder, so the only characters are the ones filed
                // here and nothing on this computer is read or written.
                bind(ProfileStore.class).toInstance(unbuilt(ProfileStore.class));
                bind(WipeStateStore.class).toProvider(com.google.inject.util.Providers.of(null));
            }
        });
        Bridge.set(injector);
        rankUp = injector.getInstance(RankUp.class);
    }

    @After
    public void tearDown() {
        plugin.scheduler.shutdownNow();
        Bridge.set(null);
        Access.set(null);
    }

    /** Twelve players already have a level. The setting must not move anyone's on its own. */
    @Test
    public void theLevelIsEveryCharactersUntilThePlayerSaysOtherwise() {
        assertEquals(PluginConfig.MerchantLevelScope.ACCOUNTWIDE, new PluginConfig() { }.merchantLevelScope());
    }

    @Test
    public void accountwideAddsEveryCharacterUp() throws Exception {
        scope = PluginConfig.MerchantLevelScope.ACCOUNTWIDE;
        logIn(ALT);

        assertEquals(350_000_000L, rankUp.levelProfit());
        assertEquals(65, FlipLevel.levelFor(rankUp.levelProfit()));
    }

    @Test
    public void perCharacterReadsOnlyTheOneLoggedIn() throws Exception {
        logIn(ALT);
        assertEquals(50_000_000L, rankUp.levelProfit());
        assertEquals(45, FlipLevel.levelFor(rankUp.levelProfit()));

        logIn(MAIN);
        assertEquals(63, FlipLevel.levelFor(rankUp.levelProfit()));
    }

    /**
     * The game gives many characters a NEGATIVE account hash, and the plugin files those under a
     * key made from the name instead. Asking for the hash alone found no one, so the level fell
     * back to every character's -- which is how this first shipped, showing 59 on a level-45 alt.
     */
    @Test
    public void aCharacterWithANegativeHashIsFoundByItsName() throws Exception {
        long filedAs = Math.abs("xp v".hashCode());
        flip(filedAs, 1_020_000_000L);
        name = "XP V";

        logIn(-8_445_759_109_730_976_879L);

        assertEquals(20_000_000L, rankUp.levelProfit());
    }

    /**
     * A real item's trades, with a sell offer cancelled after 872 sold. The level is read from
     * the same figure TOTAL PROFIT shows, so the skills tab hover never names a number the player
     * can find nowhere else -- and that figure keeps the cancelled offer's sale.
     */
    @Test
    public void theLevelCountsWhatTotalProfitShows() throws Exception {
        long sips = 3333L;
        List<Delta> deltas = new ArrayList<>();
        deltas.add(trade(1778395448517L, 7, true, 2000, 7560000L, "OFFER_COMPLETED", 3780, 1778501021877L));
        deltas.add(trade(1778501140288L, 7, false, 872, 3560376L, "OFFER_UPDATED", 4166, 1778571287819L));
        deltas.add(trade(1778571287809L, 10002, true, 1562, 5502926L, "OFFER_COMPLETED", 3523, 1778571287813L));
        deltas.add(trade(1778650101747L, 5, true, 438, 1543074L, "OFFER_UPDATED", 3523, 0L));
        deltas.add(trade(1780213209262L, 4, true, 131, 393786L, "OFFER_UPDATED", 3006, 1780229477403L));
        deltas.add(trade(1780298593142L, 10000, true, 1869, 5618214L, "OFFER_COMPLETED", 3006, 1780298593146L));
        deltas.add(trade(1782114145777L, 10003, false, 659, 2282334L, "OFFER_COMPLETED", 3533, 1782114145781L));
        deltas.add(trade(1782348768465L, 2, false, 1051, 3810926L, "OFFER_COMPLETED", 3700, 1782721076977L));
        deltas.add(trade(1782459364184L, 10000, false, 1809, 6559434L, "OFFER_COMPLETED", 3700, 1782459364188L));
        // Slot 7 later sells something else, at no profit. That is what used to drop the 872.
        deltas.add(new Delta(1778600000000L, 3, 561, true, 1, 100L, "OFFER_COMPLETED", 100, false, 0L, 0L));
        deltas.add(new Delta(1778700000000L, 7, 561, false, 1, 100L, "OFFER_COMPLETED", 100, false, 0L, 0L));
        state.getLocalTradeDeltasByAccount().put(sips, deltas);
        state.getLoadedProfiles().add(sips);

        logIn(sips);

        assertEquals(1_060_937L, rankUp.levelProfit());
        assertEquals("the stats cache agrees", 1_060_937L, RankUp.lifetimeProfit(sips));
        scope = PluginConfig.MerchantLevelScope.ACCOUNTWIDE;
        assertEquals(350_000_000L + 1_060_937L, rankUp.levelProfit());
    }

    /** Nothing else has to ask: logging in on another character moves the level by itself. */
    @Test
    public void switchingCharacterMovesTheLevelOnItsOwn() throws Exception {
        logIn(ALT);
        assertEquals(50_000_000L, rankUp.profit);

        logIn(MAIN);
        assertEquals(300_000_000L, rankUp.profit);
    }

    /** Logged out, the Profile tab goes on showing the character last played, not everyone's. */
    @Test
    public void aLogoutKeepsTheLastCharacter() throws Exception {
        logIn(ALT);

        gameState = GameState.LOGIN_SCREEN;
        accountHash = -1L;
        tick();
        assertEquals(50_000_000L, rankUp.levelProfit());
    }

    /** Before anyone has logged in there is no one character to read, so it is everyone's. */
    @Test
    public void beforeAnyLoginItIsEveryonesTotal() throws Exception {
        tick();

        assertEquals(350_000_000L, rankUp.levelProfit());
    }

    /**
     * Each character keeps its own best, or an alt at 45 would stay quiet until it passed the
     * main's best. Accountwide keeps the one key every existing player already has.
     */
    @Test
    public void eachCharacterRemembersItsOwnBestLevel() throws Exception {
        logIn(ALT);
        assertEquals("bestFlipLevel_" + ALT, rankUp.bestKey(RankUp.BEST_KEY));
        assertEquals("bestFlipPrestige_" + ALT, rankUp.bestKey(RankUp.BEST_PRESTIGE_KEY));

        logIn(MAIN);
        assertEquals("bestFlipLevel_" + MAIN, rankUp.bestKey(RankUp.BEST_KEY));

        scope = PluginConfig.MerchantLevelScope.ACCOUNTWIDE;
        assertEquals("bestFlipLevel", rankUp.bestKey(RankUp.BEST_KEY));
        assertEquals("bestFlipPrestige", rankUp.bestKey(RankUp.BEST_PRESTIGE_KEY));
    }

    /** Both short enough that the settings panel shows the setting's whole name beside them. */
    @Test
    public void theDropdownReadsInPlainWords() {
        assertEquals("Accountwide", PluginConfig.MerchantLevelScope.ACCOUNTWIDE.toString());
        assertEquals("Per character", PluginConfig.MerchantLevelScope.CHARACTER.toString());
    }

    private void logIn(long hash) throws Exception {
        accountHash = hash;
        gameState = GameState.LOGGED_IN;
        tick();
    }

    /** One client tick, then whatever it handed the scheduler, finished. */
    private void tick() throws Exception {
        rankUp.tick();
        plugin.scheduler.submit(() -> { }).get();
    }

    /** Ten whips bought at 100M each, then sold for {@code sold} after tax. */
    private void flip(long character, long sold) {
        List<Delta> deltas = new ArrayList<>();
        deltas.add(new Delta(1_000L, 0, 4151, true, 10, 1_000_000_000L, "OFFER_COMPLETED", 100_000_000, false));
        deltas.add(new Delta(2_000L, 0, 4151, false, 10, sold, "OFFER_COMPLETED", (int) (sold / 10), false));
        state.getLocalTradeDeltasByAccount().put(character, deltas);
        // Marked loaded, so nothing goes looking for this character's file.
        state.getLoadedProfiles().add(character);
    }

    private static Delta trade(long ts, int slot, boolean buy, int qty, long gp, String event, int price, long endMs) {
        return new Delta(ts, slot, 27629, buy, qty, gp, event, price, false, 0L, endMs);
    }

    /** An interface answering the named methods, and nothing (false, zero, null) for the rest. */
    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, java.util.function.Function<String, Object> answers) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            Object answer = answers.apply(method.getName());
            if (answer != null) {
                return answer;
            }
            Class<?> returns = method.getReturnType();
            if (returns == boolean.class) {
                return false;
            }
            if (returns == long.class) {
                return 0L;
            }
            if (returns == int.class) {
                return 0;
            }
            if (returns == double.class) {
                return 0d;
            }
            return null;
        });
    }

    /**
     * A client thread that takes work and never runs it. The Profile tab's figures look item
     * names up, which queues a job there; nothing here needs the job done.
     */
    private static ClientThread queueOnly() {
        ClientThread thread = unbuilt(ClientThread.class);
        try {
            Field invokes = ClientThread.class.getDeclaredField("invokes");
            invokes.setAccessible(true);
            invokes.set(thread, new java.util.concurrent.ConcurrentLinkedQueue<>());
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("could not stand in for the client thread", ex);
        }
        return thread;
    }

    /** One of the client's own classes, allocated without running the constructor it cannot satisfy here. */
    @SuppressWarnings("unchecked")
    private static <T> T unbuilt(Class<T> type) {
        try {
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Field handle = unsafeType.getDeclaredField("theUnsafe");
            handle.setAccessible(true);
            Object unsafe = handle.get(null);
            return (T) unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, type);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("could not stand in for " + type.getSimpleName(), ex);
        }
    }
}
