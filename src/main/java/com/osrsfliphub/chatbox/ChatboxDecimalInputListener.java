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

import java.awt.event.KeyEvent;
import java.util.function.UnaryOperator;
import javax.inject.*;
import lombok.RequiredArgsConstructor;
import net.runelite.api.*;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.input.KeyListener;

/**
 * Lets a decimal amount be typed into a chatbox quantity prompt.
 *
 * <p>The game drops the decimal point key, so it is written into the prompt here instead. On Enter
 * the text is rewritten as a plain integer before the game reads it, which it does on its next
 * tick - so 9.4m leaves as 9400000 and the game never sees the point.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class ChatboxDecimalInputListener implements KeyListener {
    // MESLAYERMODE is the chatbox input type, and 7 is the one the game uses for every "enter an
    // amount" prompt: the Grand Exchange price and quantity boxes, bank withdraw-X, trade, coffers.
    private static final int INPUT_TYPE_AMOUNT_PROMPT = 7;

    private final Client client;
    private final ClientThread clientThread;
    private final PluginConfig config;

    @Override
    public void keyPressed(KeyEvent event) {
        if (event == null || !isDecimalAmountsEnabled()) {
            return;
        }
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.VK_ENTER) {
            rewriteInputText(ChatboxDecimalInput::toPlainAmount, false);
        } else if (keyCode == KeyEvent.VK_PERIOD || keyCode == KeyEvent.VK_DECIMAL) {
            rewriteInputText(ChatboxDecimalInput::withDecimalPoint, true);
        }
    }

    @Override
    public void keyReleased(KeyEvent event) {
    }

    @Override
    public void keyTyped(KeyEvent event) {
    }

    /**
     * The half of the gate that is safe to answer on the AWT thread: settings and wiring only,
     * no client state.
     */
    private boolean isDecimalAmountsEnabled() {
        return config != null
            && config.enableDecimalAmounts();
    }

    /**
     * Whether the chatbox is currently showing an "enter an amount" prompt.
     *
     * <p>Only ever called on the client thread. Key events arrive on the AWT thread, and reading
     * client state from there is both a plugin hub review point and a genuine race: the prompt
     * can close between the check and the write.</p>
     */

    /**
     * Reads what the prompt holds, converts it and writes it back, all on the client thread.
     *
     * <p>Key events arrive on the AWT thread, ahead of the game applying the keystrokes it has
     * already queued. Reading here and deferring only the write meant a fast "9.4" then "m"
     * could be read as "9", converted to 9, and then have the game append its own "m" to give
     * nine million instead of nine point four.
     *
     * @param redraw whether the prompt still has to show the new text. A converted amount is read
     *               by the game and the prompt closes, so only the typed decimal point needs it.
     */
    private void rewriteInputText(UnaryOperator<String> conversion, boolean redraw) {
        clientThread.invoke(() -> {
            if (client.getVarcIntValue(VarClientID.MESLAYERMODE) != INPUT_TYPE_AMOUNT_PROMPT) {
                return;
            }
            String converted = conversion.apply(client.getVarcStrValue(VarClientID.MESLAYERINPUT));
            if (converted == null) {
                return;
            }
            client.setVarcStrValue(VarClientID.MESLAYERINPUT, converted);
            if (redraw) {
                client.runScript(ScriptID.CHAT_TEXT_INPUT_REBUILD, "");
            }
        });
    }
}
