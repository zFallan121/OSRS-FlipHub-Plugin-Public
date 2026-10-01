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

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.lang.reflect.Proxy;
import java.util.function.BiFunction;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The prompts as the game has opened them since 30 Sep 2026: a quantity is still input mode 7,
 * but a Grand Exchange price is the new mode 30, which takes 19 digits and a T for trillion.
 */
public class ChatboxDecimalInputListenerTest {
    private static final int QUANTITY_PROMPT = 7;
    private static final int PRICE_PROMPT = 30;
    private static final int ITEM_SEARCH = 14;

    private String promptText;
    private boolean redrawn;

    @Test
    public void aDecimalPriceIsHandedToTheGameAsAPlainNumber() {
        promptText = "9.4m";
        press(PRICE_PROMPT, KeyEvent.VK_ENTER);
        assertEquals("9400000", promptText);
    }

    @Test
    public void thePointCanBeTypedIntoThePricePrompt() {
        promptText = "9";
        press(PRICE_PROMPT, KeyEvent.VK_PERIOD);
        assertEquals("9.", promptText);
        assertTrue(redrawn);
    }

    @Test
    public void aDecimalQuantityStillWorks() {
        promptText = "1.2k";
        press(QUANTITY_PROMPT, KeyEvent.VK_ENTER);
        assertEquals("1200", promptText);
    }

    @Test
    public void aPromptThatIsNotAnAmountIsLeftAlone() {
        promptText = "1.2k";
        press(ITEM_SEARCH, KeyEvent.VK_ENTER);
        assertEquals("1.2k", promptText);
        press(ITEM_SEARCH, KeyEvent.VK_PERIOD);
        assertEquals("1.2k", promptText);
        assertFalse(redrawn);
    }

    private void press(int inputMode, int keyCode) {
        Client client = fake(Client.class, (method, args) -> {
            switch (method) {
                case "getVarcIntValue":
                    return inputMode;
                case "getVarcStrValue":
                    return promptText;
                case "setVarcStrValue":
                    promptText = (String) args[1];
                    return null;
                case "runScript":
                    redrawn = true;
                    return null;
                default:
                    return null;
            }
        });
        PluginConfig config = fake(PluginConfig.class, (method, args) ->
            method.equals("enableDecimalAmounts") ? Boolean.TRUE : null);
        ClientThread now = new ClientThread() {
            @Override
            public void invoke(Runnable r) {
                r.run();
            }
        };
        new ChatboxDecimalInputListener(client, now, config).keyPressed(
            new KeyEvent(new Component() { }, KeyEvent.KEY_PRESSED, 0L, 0, keyCode, KeyEvent.CHAR_UNDEFINED));
    }

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, BiFunction<String, Object[], Object> answers) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (proxy, method, args) -> answers.apply(method.getName(), args));
    }
}
