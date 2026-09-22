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
import com.google.inject.util.Providers;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ItemManager;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Level-ups off real stored trades: what a live sale earns, whose it is, how many a pair of
 * sales earns, when the rank is named, and when the skills tab reads the total again.
 *
 * <p>Each sale is filed the way the recorder files one: the trade goes into the stored list,
 * then {@link RankUp#onSale} is told how far it moved the selling character's total.
 */
public class MerchantLevelUpTest {
    private static final long MAIN = 1111L;
    private static final long ALT = 2222L;

    // Level 36, where the Varrock Hustler begins.
    private static final long LINE = FlipLevel.profitFor(36);

    private final PluginState state = new PluginState();
    private final List<String> chat = Collections.synchronizedList(new ArrayList<>());
    private volatile GameState gameState = GameState.LOGIN_SCREEN;
    private volatile long accountHash = -1L;
    private volatile PluginConfig.MerchantLevelScope scope = PluginConfig.MerchantLevelScope.CHARACTER;
    private volatile boolean celebrating = true;
    private final Widget statsTab = FakeGame.make(InterfaceID.Stats.UNIVERSE, WidgetType.LAYER, 0, 0, 190, 261, null);
    private GeLifecyclePlugin plugin;
    private RankUp rankUp;
    private long clock = 10_000L;

    @Before
    public void setUp() throws Exception {
        Client client = FakeGame.stub(Client.class, (m, a) -> {
            switch (m.getName()) {
                case "getGameState":
                    return gameState;
                case "getAccountHash":
                    return accountHash;
                case "addChatMessage":
                    chat.add((String) a[2]);
                    return null;
                case "getWidget":
                    return a.length == 1 && Integer.valueOf(InterfaceID.Stats.UNIVERSE).equals(a[0]) ? statsTab : null;
                default:
                    return null;
            }
        });
        PluginConfig config = FakeGame.stub(PluginConfig.class, (m, a) ->
            "merchantLevelScope".equals(m.getName()) ? scope
                : "celebrateRankUps".equals(m.getName()) ? (Object) celebrating : null);
        plugin = new GeLifecyclePlugin();
        plugin.client = client;
        plugin.config = config;
        plugin.configManager = workingConfig();
        plugin.scheduler = Executors.newSingleThreadScheduledExecutor();
        plugin.clientThread = new ClientThread() {
            @Override
            public void invokeLater(Runnable r) {
                r.run();
            }
        };
        Access.set(plugin);
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(Client.class).toInstance(client);
                bind(PluginConfig.class).toInstance(config);
                bind(PluginState.class).toInstance(state);
                bind(Gson.class).toInstance(new Gson());
                bind(OkHttpClient.class).toInstance(new OkHttpClient());
                // The Profile tab's figures look item names up, which queues a job there that
                // nothing here needs done. The chat lines go through the plugin's own, above.
                bind(ClientThread.class).toInstance(queueOnly());
                bind(ConfigManager.class).toInstance(plugin.configManager);
                bind(ItemManager.class).toInstance(unbuilt(ItemManager.class));
                // Built without a RuneLite folder, so nothing on this computer is read or written.
                bind(ProfileStore.class).toInstance(unbuilt(ProfileStore.class));
                bind(WipeStateStore.class).toProvider(Providers.of(null));
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

    @Test
    public void aSaleOverALineEarnsTheLevel() throws Exception {
        flip(MAIN, LINE - 5_000);
        logIn(MAIN);
        buy(MAIN, 1);
        sell(MAIN, 1, 10_000);
        settle();
        assertEquals(1, said("level 36."));
        assertEquals(36, best("bestFlipLevel_" + MAIN));
    }

    /**
     * Two sales in the same tick are both filed before either is judged, so both judgements see
     * the total after the second. Taking only its own sale's change off that, each put the
     * "before" past the first sale, and the line the first sale crossed was seen by neither.
     */
    @Test
    public void twoSalesInOneTickStillCrossTheLineBetweenThem() throws Exception {
        flip(MAIN, LINE - 5_000);
        logIn(MAIN);
        buy(MAIN, 1);
        buy(MAIN, 2);
        assertEquals(LINE - 5_000, rankUp.levelProfit(MAIN));

        CountDownLatch go = hold();
        sell(MAIN, 1, 10_000);
        sell(MAIN, 2, 10_000);
        go.countDown();
        settle();
        assertEquals(LINE + 15_000, rankUp.levelProfit(MAIN));
        assertEquals("once, not missed and not twice", 1, said("level 36."));
        assertEquals(36, best("bestFlipLevel_" + MAIN));
    }

    /** Neither of a pair that crosses nothing can be made to pay out an older line. */
    @Test
    public void aPairOfSalesThatCrossesNothingSaysNothing() throws Exception {
        flip(MAIN, LINE + 5_000);
        logIn(MAIN);
        buy(MAIN, 1);
        buy(MAIN, 2);

        CountDownLatch go = hold();
        sell(MAIN, 1, 10_000);
        sell(MAIN, 2, 10_000);
        go.countDown();
        settle();
        assertTrue(chat.toString(), chat.isEmpty());
    }

    /**
     * A level crossed while Celebrate level-ups was off is the player's choice not to hear it.
     * The next sale, after it is turned back on, judges only its own ground.
     */
    @Test
    public void aLineCrossedWithCelebrationsOffIsNotPaidOutByTheNextSale() throws Exception {
        flip(MAIN, LINE - 5_000);
        logIn(MAIN);
        buy(MAIN, 1);
        buy(MAIN, 2);
        celebrating = false;
        sell(MAIN, 1, 10_000);
        settle();
        celebrating = true;
        sell(MAIN, 2, 10_000);
        settle();
        assertTrue(chat.toString(), chat.isEmpty());
    }

    /**
     * The fills a login finds waiting are filed in the very tick of the login, before the level
     * has noticed the character changed. They are the new character's, judged against its own
     * total and remembered under its own best.
     */
    @Test
    public void aSaleFiledAsTheCharacterChangesIsTheNewCharacters() throws Exception {
        flip(ALT, 50_000_000L);
        flip(MAIN, LINE - 5_000);
        buy(MAIN, 1);
        logIn(ALT);

        // MAIN logs in, and an offline fill is filed before the level's own tick has run.
        accountHash = MAIN;
        sell(MAIN, 1, 10_000);
        settle();
        assertEquals(1, said("level 36."));
        assertEquals(36, best("bestFlipLevel_" + MAIN));
        assertEquals(0, best("bestFlipLevel_" + ALT));
    }

    /** A sale that jumps several levels at once still names the rank it lands in. */
    @Test
    public void aJumpIntoANewRankNamesIt() throws Exception {
        flip(MAIN, 0L);
        logIn(MAIN);
        buy(MAIN, 1);
        sell(MAIN, 1, FlipLevel.profitFor(6));
        settle();
        assertEquals(1, said("level 6."));
        assertEquals(1, said("a Greenhorn."));
    }

    /** A rank already reached is not named again after a fall back through it. */
    @Test
    public void aRankAlreadyReachedIsNotNamedAgain() throws Exception {
        flip(MAIN, FlipLevel.profitFor(2));
        logIn(MAIN);
        buy(MAIN, 1);
        // Level 5 was reached before, and Greenhorn named then, at 3.
        plugin.configManager.setConfiguration(FliphubConfigGroups.CONFIG_GROUP, "bestFlipLevel_" + MAIN, 5);
        sell(MAIN, 1, FlipLevel.profitFor(6) - FlipLevel.profitFor(2));
        settle();
        assertEquals(1, said("level 6."));
        assertEquals(0, said("Greenhorn"));
    }

    /**
     * A GE history import, a recorded recipe or a wipe moves the total without a sale, and the
     * skills tab has to show it. It is read again whenever the tab comes on screen.
     */
    @Test
    public void theSkillsTabReadsTheTotalAgainEachTimeItOpens() throws Exception {
        scope = PluginConfig.MerchantLevelScope.ACCOUNTWIDE;
        flip(ALT, 50_000_000L);
        SkillTab tab = new SkillTab();
        statsTab.setHidden(true);
        logIn(ALT);
        prime(tab);
        assertEquals(50_000_000L, rankUp.profit);

        // An import, with the tab closed.
        flip(MAIN, 20_000_000L);
        prime(tab);
        assertEquals("nothing is read while the tab is shut", 50_000_000L, rankUp.profit);

        statsTab.setHidden(false);
        prime(tab);
        assertEquals(70_000_000L, rankUp.profit);
        // Open, it is not read again every tick.
        flip(ALT, 1_000_000L);
        prime(tab);
        assertEquals(70_000_000L, rankUp.profit);
    }

    // ---- trades ----------------------------------------------------------------------------

    /** Ten whips bought at 100M each, then sold for {@code profit} more than they cost. */
    private void flip(long character, long profit) {
        file(character, new Delta(tick(), 0, 4151, true, 10, 1_000_000_000L, "OFFER_COMPLETED", 100_000_000, false));
        file(character, new Delta(tick(), 0, 4151, false, 10, 1_000_000_000L + profit, "OFFER_COMPLETED",
            (int) ((1_000_000_000L + profit) / 10), false));
    }

    /** One of an item bought for 1M, not yet sold. */
    private void buy(long character, int item) {
        file(character, new Delta(tick(), item, 560 + item, true, 1, 1_000_000L, "OFFER_COMPLETED", 1_000_000, false));
    }

    /** That item sold for {@code gain} more, and the level told, as the recorder tells it. */
    private void sell(long character, int item, long gain) {
        file(character, new Delta(tick(), item, 560 + item, false, 1, 1_000_000L + gain, "OFFER_COMPLETED",
            (int) (1_000_000L + gain), false));
        rankUp.onSale(character, 0L, gain);
    }

    private void file(long character, Delta delta) {
        synchronized (state.getLocalStatsLock()) {
            state.getLocalTradeDeltasByAccount().computeIfAbsent(character, k -> new ArrayList<>()).add(delta);
            state.getLoadedProfiles().add(character);
            // What an import or the recorder does once the stored list has moved.
            state.getStatsCacheByAccount().clear();
        }
    }

    private long tick() {
        return clock += 1_000L;
    }

    private void logIn(long hash) throws Exception {
        accountHash = hash;
        gameState = GameState.LOGGED_IN;
        rankUp.tick();
        settle();
    }

    /** Holds the scheduler until the latch is let go, so what is handed it meanwhile queues. */
    private CountDownLatch hold() {
        CountDownLatch go = new CountDownLatch(1);
        plugin.scheduler.execute(() -> {
            try {
                go.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        return go;
    }

    /** Whatever has been handed the scheduler, finished. */
    private void settle() throws Exception {
        plugin.scheduler.submit(() -> { }).get();
    }

    private void prime(SkillTab tab) throws Exception {
        Method prime = SkillTab.class.getDeclaredMethod("prime", Client.class);
        prime.setAccessible(true);
        prime.invoke(tab, plugin.client);
        settle();
    }

    private int said(String words) {
        synchronized (chat) {
            return (int) chat.stream().filter(line -> line.contains(words)).count();
        }
    }

    private int best(String key) {
        return RankUp.best(key);
    }

    /**
     * A ConfigManager that keeps what it is given in memory, and no more: RuneLite's own, built
     * without a profile, a file or a server behind it.
     */
    private static ConfigManager workingConfig() throws Exception {
        ConfigManager manager = unbuilt(ConfigManager.class);
        Class<?> dataType = Class.forName("net.runelite.client.config.ConfigData");
        Object data = unbuilt(dataType);
        set(dataType, data, "properties", new ConcurrentHashMap<String, String>());
        set(dataType, data, "patchChanges", new HashMap<String, String>());
        set(ConfigManager.class, manager, "configProfile", data);
        Constructor<?> handler = Class.forName("net.runelite.client.config.ConfigInvocationHandler")
            .getDeclaredConstructor(ConfigManager.class);
        handler.setAccessible(true);
        set(ConfigManager.class, manager, "handler", handler.newInstance(manager));
        set(ConfigManager.class, manager, "eventBus", new EventBus());
        set(ConfigManager.class, manager, "serializers", new HashMap<>());
        return manager;
    }

    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /** A client thread that takes work and never runs it. */
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
