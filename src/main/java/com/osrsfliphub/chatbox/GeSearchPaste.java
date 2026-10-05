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

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import javax.inject.*;
import javax.swing.SwingUtilities;
import lombok.RequiredArgsConstructor;
import net.runelite.api.*;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.*;
import net.runelite.client.game.ItemManager;

/**
 * Offers what the player has copied as a "Paste: ..." line in the top left of the Grand Exchange
 * item search. A click puts the text in the search box as if it had been typed; choosing the item
 * is still the player's own click.
 *
 * <p>Only text that names a Grand Exchange item is ever shown, so a copied password or message
 * stays off the screen. Nothing read from the clipboard is stored or sent anywhere.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class GeSearchPaste {
    // The chatbox input type of the item search, and the most characters its box lets you type.
    private static final int ITEM_SEARCH = 14;
    private static final int LONGEST = 25;
    private static final long READ_INTERVAL_MS = 1000L;

    private final Client client;
    private final PluginConfig config;
    private final ItemManager itemManager;
    private final ChatboxSuggestionRuntimeState runtimeState;
    private final ChatboxSuggestionWidgetFactory widgetFactory;
    // Reading the clipboard can wait on another program, so it is kept off the game thread.
    Executor reader = SwingUtilities::invokeLater;
    // What a click would paste, or null while the clipboard holds nothing that names an item.
    volatile String text;
    private Widget line;
    private long lastUpdateMs;
    private long lastReadMs;

    /** Every client tick, on the game thread; the line itself is looked at four times a second. */
    void tick() {
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastUpdateMs >= Const.SUGGESTION_UPDATE_INTERVAL_MS) {
            lastUpdateMs = nowMs;
            update();
        }
    }

    void update() {
        Widget container = runtimeState.getChatboxContainer();
        if (!config.enableSearchPaste() || container == null || container.isHidden()
            || client.getVarcIntValue(VarClientID.MESLAYERMODE) != ITEM_SEARCH) {
            // Closed: forget what was copied, and read afresh the moment the search next opens.
            text = null;
            lastReadMs = 0L;
            hide();
            return;
        }
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastReadMs >= READ_INTERVAL_MS) {
            lastReadMs = nowMs;
            reader.execute(this::readClipboard);
        }
        String paste = text;
        if (paste == null || paste.equalsIgnoreCase(client.getVarcStrValue(VarClientID.MESLAYERINPUT))) {
            hide();
            return;
        }
        line = widgetFactory.ensurePasteWidget(container, line, this::paste);
        String label = "Paste: " + paste;
        boolean changed = ChatboxSuggestionPresentation.applySuggestionTextAndWidth(line, label);
        // No wider than its words, so a click on the search's own title beside it is not a paste.
        FontTypeFace font = line.getFont();
        if (font != null) {
            line.setOriginalWidth(font.getTextWidth(label));
        }
        if (line.isSelfHidden()) {
            line.setHidden(false);
            changed = true;
        }
        if (changed) {
            line.revalidate();
        }
    }

    /** Also run when the plugin is switched off, so the line does not outlive it. */
    void hide() {
        if (line != null && !line.isSelfHidden()) {
            line.setHidden(true);
            line.revalidate();
        }
    }

    /** The line's click, on the game thread. */
    private void paste() {
        String paste = text;
        Widget input = client.getWidget(ComponentID.CHATBOX_FULL_INPUT);
        Object[] onKey = input == null ? null : input.getOnKeyListener();
        if (paste == null || onKey == null
            || client.getVarcIntValue(VarClientID.MESLAYERMODE) != ITEM_SEARCH) {
            return;
        }
        client.setVarcStrValue(VarClientID.MESLAYERINPUT, paste);
        // The box's own key script, run with no key pressed: it redraws the box and starts the
        // search, as it does after a typed letter.
        client.runScript(onKey);
    }

    private void readClipboard() {
        String copied = null;
        try {
            copied = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
        } catch (Exception ignored) {
            // Nothing copied, not text, or another program is holding the clipboard.
        }
        text = pasteText(copied, name -> !itemManager.search(name).isEmpty());
    }

    /** What a click would put in the search box for this copied text, or null to offer nothing. */
    static String pasteText(String copied, Predicate<String> namesAnItem) {
        String line = copied == null ? "" : copied.trim();
        if (line.length() > LONGEST) {
            line = line.substring(0, LONGEST);
        }
        return line.length() >= 3 && namesAnItem.test(line) ? line : null;
    }
}
