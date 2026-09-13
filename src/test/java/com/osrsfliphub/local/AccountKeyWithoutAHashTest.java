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
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A character with no account hash still has an account key.
 *
 * <p>{@code getAccountHash} answers -1 for a login with no Jagex account attached, so the key
 * every trade is filed under falls back to one made from the display name. Anything that wants
 * to find a player's trades has to ask for that same key: the recipe recorder asked for the hash
 * alone and told a logged-in player to log in, because their trades were all under the other one.
 */
public class AccountKeyWithoutAHashTest {
    private static final String NAME = "Zezima";

    @Test
    public void aLoginWithNoAccountHashStillResolvesToAKey() {
        AccountSession session = new AccountSession(client(-1L, NAME));

        long key = session.resolveLocalAccountKey();

        assertTrue("no hash must not mean no account", key > 0);
        assertEquals("and it is the key made from the name",
            Math.abs(NAME.toLowerCase(java.util.Locale.US).hashCode()), key);
    }

    /** With a hash, that is the key; the name is only ever the fallback. */
    @Test
    public void anAccountHashWinsWhenThereIsOne() {
        assertEquals(4242L, new AccountSession(client(4242L, NAME)).resolveLocalAccountKey());
    }

    @Test
    public void loggedOutHasNoKeyAtAll() {
        assertEquals(-1L, new AccountSession(client(-1L, null)).resolveLocalAccountKey());
    }

    private Client client(long accountHash, String displayName) {
        Player player = displayName == null ? null : (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[] {Player.class},
            (proxy, method, args) ->
                "getName".equals(method.getName()) ? displayName : defaultValue(method));
        return (Client) Proxy.newProxyInstance(
            Client.class.getClassLoader(),
            new Class<?>[] {Client.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getGameState":
                        return displayName == null ? GameState.LOGIN_SCREEN : GameState.LOGGED_IN;
                    case "getAccountHash":
                        return accountHash;
                    case "getLocalPlayer":
                        return player;
                    case "toString":
                        return "client-stub";
                    default:
                        return defaultValue(method);
                }
            });
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
            return 0d;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == void.class) {
            return null;
        }
        return 0;
    }
}
