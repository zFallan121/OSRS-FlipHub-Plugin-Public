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

import java.lang.reflect.Proxy;
import java.util.function.BiFunction;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * What the plugin reads from the game about the offer being set up, as the game has answered
 * since the update of 30 Sep 2026. Found by the audit that followed two features going quiet.
 */
public class GameReadsAfterTheLongPriceUpdateTest {
    private static final int PRICE_PROMPT = 30;

    /** The game's offer-type value is one bit: set for a sell, clear for a buy and for no offer. */
    @Test
    public void theOfferTypeBitMeansSell() {
        assertEquals(Boolean.FALSE, offerType(1));
    }

    @Test
    public void aClearOfferTypeBitSaysNothingOnItsOwn() {
        assertNull(offerType(0));
    }

    @Test
    public void anOfferTypeTheGameNoLongerHasIsUnknownRatherThanAFailure() {
        assertNull(new OfferTypeResolver(deletedVarbits(GameState.LOGGED_IN), new OfferPreviewRuntime())
            .resolveOfferType());
    }

    @Test
    public void nothingIsAskedOfTheGameBeforeACharacterIsLoggedIn() {
        // At the login screen the client answers a varbit read with a NullPointerException. Asked
        // once a second, that was 7,707 stack traces in one evening's log.
        OfferPreviewItemResolver resolver = new OfferPreviewItemResolver(
            deletedVarbits(GameState.LOGIN_SCREEN), new OfferPreviewRuntime(), null);
        assertTrue(resolver.resolve().shouldClear());
    }

    @Test
    public void aDeletedVarbitClearsThePreviewRatherThanFailing() {
        OfferPreviewItemResolver resolver = new OfferPreviewItemResolver(
            deletedVarbits(GameState.LOGGED_IN), new OfferPreviewRuntime(), null);
        assertTrue(resolver.resolve().shouldClear());
    }

    @Test
    public void thePricePromptIsKnownByItsInputTypeWithoutSearchingTheChatbox() {
        Client client = fake(Client.class, (method, args) ->
            method.equals("getVarcIntValue") ? (Object) PRICE_PROMPT : null);
        ChatboxSuggestionRuntimeState state = new ChatboxSuggestionRuntimeState(
            client, new ChatboxPromptWidgetResolver(client), new ChatboxSuggestionWidgetFactory());
        assertTrue(state.isGeInputPromptActive());
    }

    private static Boolean offerType(int bit) {
        Client client = fake(Client.class, (method, args) -> {
            if (method.equals("getVarbitValue")) {
                return (Integer) args[0] == VarbitID.GE_NEWOFFER_TYPE ? bit : 0;
            }
            return null;
        });
        return new OfferTypeResolver(client, new OfferPreviewRuntime()).resolveOfferType();
    }

    /** A client that answers every varbit read the way it answers one for a varbit it lacks. */
    private static Client deletedVarbits(GameState state) {
        return fake(Client.class, (method, args) -> {
            switch (method) {
                case "getGameState":
                    return state;
                case "getVarbitValue":
                    throw new IndexOutOfBoundsException("Varbit " + args[0] + " does not exist");
                case "getVarpValue":
                    return 0;
                default:
                    return null;
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, BiFunction<String, Object[], Object> answers) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (proxy, method, args) -> answers.apply(method.getName(), args));
    }
}
