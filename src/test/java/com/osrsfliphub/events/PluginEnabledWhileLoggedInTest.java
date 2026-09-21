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
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Switching the plugin on with the player already in the game.
 *
 * <p>RuneLite delivers events only to plugins that are running, and it never replays the
 * login for one enabled afterwards. Everything the plugin arranges at login was therefore
 * skipped for the most ordinary way anybody turns it on: log in, open the plugin list, tick
 * the box. The session clock stayed at zero so the Session range showed nothing, and nobody
 * had asked the client for the Smithing level so repairs were priced at the full NPC rate,
 * both until the player happened to log out and back in again.
 */
public class PluginEnabledWhileLoggedInTest {
    private static final long ACCOUNT_HASH = 987654321L;
    private static final int SMITHING_LEVEL = 70;

    private GameState gameState = GameState.LOGGED_IN;
    /** Somewhere of this test's own. Without it the login path writes into the real profiles. */
    private Path storeDir;

    @After
    public void tearDown() throws IOException {
        Bridge.set(null);
        Access.set(null);
        if (storeDir != null && Files.exists(storeDir)) {
            try (Stream<Path> paths = Files.walk(storeDir)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) {
                    Files.deleteIfExists(path);
                }
            }
            storeDir = null;
        }
    }

    @Test
    public void startingWithTheGameAlreadyRunningStartsTheSession() {
        GeLifecyclePlugin plugin = wire();

        handler().catchUpWithAnAlreadyRunningGame();

        assertTrue("the session clock never started", plugin.sessionStartMs > 0L);
    }

    /** At the login screen there is nothing to catch up with. */
    @Test
    public void startingAtTheLoginScreenChangesNothing() {
        gameState = GameState.LOGIN_SCREEN;
        GeLifecyclePlugin plugin = wire();

        handler().catchUpWithAnAlreadyRunningGame();

        assertEquals(0L, plugin.sessionStartMs);
    }

    /** Running twice must not restart the clock, since a world hop comes through the same door. */
    @Test
    public void catchingUpTwiceKeepsTheFirstSessionStart() {
        GeLifecyclePlugin plugin = wire();

        handler().catchUpWithAnAlreadyRunningGame();
        long firstStart = plugin.sessionStartMs;
        handler().catchUpWithAnAlreadyRunningGame();

        assertEquals(firstStart, plugin.sessionStartMs);
    }

    private GeLifecyclePlugin wire() {
        try {
            storeDir = Files.createTempDirectory("plugin-enabled-while-logged-in");
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        Client client = client();
        PluginConfig config = config();
        PluginState state = new PluginState();
        GeLifecyclePlugin plugin = new GeLifecyclePlugin();
        plugin.client = client;
        Access.set(plugin);
        Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(Client.class).toInstance(client);
                bind(PluginConfig.class).toInstance(config);
                bind(PluginState.class).toInstance(state);
                // Reaches ConfigManager through Guice otherwise, which pulls in most of RuneLite.
                bind(OfferStampStateServices.class).toInstance(stampState(state));
                bind(ProfileStore.class).toInstance(
                    new ProfileStore(new Gson(), "fliphub", "fliphub-dev", storeDir));
                // Same reason: it already treats an absent config manager as "nothing stored".
                bind(ProfileSelectionPersistence.class)
                    .toInstance(new ProfileSelectionPersistence(null));
                bind(BookmarkState.class)
                    .toInstance(new BookmarkState(null, config, state));
                // Loading a profile's trades is handed an item lookup in its constructor, so there
                // has to be one. With no item manager behind it, it looks nothing up.
                bind(ItemLookup.class).toInstance(new ItemLookup(null, null, state));
                // The rest of the login work needs services this test does not stand up. Each
                // of these is already guarded for absence on the path being exercised.
                bind(LinkStatus.class).toProvider(Providers.of(null));
                bind(LinkAttempt.class).toProvider(Providers.of(null));
                bind(WikiPrice.class).toProvider(Providers.of(null));
                bind(PanelRefresh.class).toProvider(Providers.of(null));
            }
        });
        Bridge.set(injector);
        return plugin;
    }

    private static OfferStampStateServices stampState(PluginState state) {
        OfferUpdateStamp stampService = new OfferUpdateStamp();
        return new OfferStampStateServices(
            FliphubConfigGroups.CONFIG_GROUP,
            FliphubConfigGroups.LEGACY_DEV_CONFIG_GROUP,
            Const.LOGIN_GRACE_MS,
            state.getOfferUpdateStamps(),
            () -> null,
            () -> null,
            () -> null,
            () -> stampService
        );
    }

    private static GameStateChangedHandler handler() {
        return Bridge.get(GameStateChangedHandler.class);
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
                        return ACCOUNT_HASH;
                    case "getRealSkillLevel":
                        return args != null && args.length == 1 && args[0] == Skill.SMITHING
                            ? SMITHING_LEVEL : 1;
                    case "toString":
                        return "client-stub";
                    default:
                        return defaultValue(method);
                }
            }
        );
    }

    private static PluginConfig config() {
        return (PluginConfig) Proxy.newProxyInstance(
            PluginConfig.class.getClassLoader(),
            new Class<?>[] {PluginConfig.class},
            (proxy, method, args) -> {
                if ("toString".equals(method.getName())) {
                    return "config-stub";
                }
                return defaultValue(method);
            }
        );
    }

    private static Object defaultValue(Method method) {
        Class<?> type = method.getReturnType();
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0.0d;
        }
        if (type == float.class) {
            return 0.0f;
        }
        if (type == void.class) {
            return null;
        }
        return 0;
    }
}
