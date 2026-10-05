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
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The Paste line in the Grand Exchange item search: the player has copied "Abyssal whip", opens a
 * buy offer, and the search shows "Paste: Abyssal whip" in its top left corner.
 */
public class GeSearchPasteTest {
    private static final int ITEM_SEARCH = 14;
    private static final int PRICE_PROMPT = 30;
    private static final Object[] KEY_SCRIPT = {112, -2147483640, -2147483639, "What would you like to buy?"};

    private int inputMode = ITEM_SEARCH;
    private String searchText = "";
    private boolean settingOn = true;
    private Object[] keyScript = KEY_SCRIPT;
    private final List<Object[]> scriptsRun = new ArrayList<>();
    private final List<Runnable> clipboardReads = new ArrayList<>();
    private final FakeWidget searchWindow = new FakeWidget(null);

    @Test
    public void onlyTextThatNamesAnItemIsOffered() {
        assertEquals("Abyssal whip", GeSearchPaste.pasteText("  Abyssal whip\r\n", name -> true));
        assertNull(GeSearchPaste.pasteText("hunter2-my-password", name -> false));
        assertNull(GeSearchPaste.pasteText(null, name -> true));
        assertNull(GeSearchPaste.pasteText("   ", name -> true));
    }

    @Test
    public void oneOrTwoCopiedLettersAreNotOffered() {
        assertNull(GeSearchPaste.pasteText("ab", name -> true));
        assertEquals("abc", GeSearchPaste.pasteText("abc", name -> true));
    }

    @Test
    public void aNameLongerThanTheBoxIsCutToWhatCouldBeTyped() {
        // The game stops typing in this box at 25 characters; the item is checked as cut.
        List<String> checked = new ArrayList<>();
        String offered = GeSearchPaste.pasteText("Corrupted Tumeken's shadow (uncharged)", name -> {
            checked.add(name);
            return true;
        });
        assertEquals("Corrupted Tumeken's shado", offered);
        assertEquals(25, offered.length());
        assertEquals(List.of("Corrupted Tumeken's shado"), checked);
    }

    @Test
    public void theLineShowsInTheTopLeftOfTheSearch() {
        GeSearchPaste paste = paste();
        paste.text = "Abyssal whip";

        paste.update();

        FakeWidget line = searchWindow.children.get(0);
        assertEquals("Paste: Abyssal whip", line.text);
        assertFalse(line.hidden);
        assertEquals(WidgetPositionMode.ABSOLUTE_LEFT, line.xMode);
        assertEquals(WidgetPositionMode.ABSOLUTE_TOP, line.yMode);
        assertEquals(4, line.x);
        assertEquals(2, line.y);
        assertEquals(FontID.PLAIN_11, line.fontId);
        assertEquals("Paste", line.action);
    }

    @Test
    public void aClickFillsTheSearchBoxAndRunsTheBoxesOwnKeyScript() {
        GeSearchPaste paste = paste();
        paste.text = "Abyssal whip";
        paste.update();

        searchWindow.children.get(0).click();

        assertEquals("Abyssal whip", searchText);
        assertEquals(1, scriptsRun.size());
        assertArrayEquals(KEY_SCRIPT, scriptsRun.get(0));
    }

    @Test
    public void theLineGoesOnceTheBoxHoldsTheText() {
        GeSearchPaste paste = paste();
        paste.text = "Abyssal whip";
        paste.update();
        FakeWidget line = searchWindow.children.get(0);

        line.click();
        paste.update();
        assertTrue(line.hidden);

        // Typing something else brings it back, on the same line.
        searchText = "abyssal d";
        paste.update();
        assertFalse(line.hidden);
        assertEquals(1, searchWindow.children.size());
    }

    @Test
    public void nothingShowsWithNothingToPaste() {
        GeSearchPaste paste = paste();

        paste.update();

        assertTrue(searchWindow.children.isEmpty());
    }

    @Test
    public void nothingShowsOrFillsOutsideTheItemSearch() {
        GeSearchPaste paste = paste();
        paste.text = "Abyssal whip";
        paste.update();
        FakeWidget line = searchWindow.children.get(0);

        // The player picked an item and the price box opened in the same chat area. A click that
        // lands before the line is taken down must not put a name in a price box.
        inputMode = PRICE_PROMPT;
        searchText = "";
        line.click();
        assertEquals("", searchText);
        assertTrue(scriptsRun.isEmpty());

        paste.update();
        assertTrue(line.hidden);
        assertNull(paste.text);
    }

    @Test
    public void whatWasCopiedIsForgottenWhenTheSearchClosesAndReadAgainWhenItOpens() {
        GeSearchPaste paste = paste();
        paste.update();
        paste.text = "Abyssal whip";
        assertEquals(1, clipboardReads.size());

        searchWindow.hidden = true;
        paste.update();
        assertNull(paste.text);

        searchWindow.hidden = false;
        paste.update();
        assertEquals(2, clipboardReads.size());
    }

    @Test
    public void switchingThePluginOffTakesTheLineDown() {
        GeSearchPaste paste = paste();
        paste.text = "Abyssal whip";
        paste.update();

        paste.hide();

        assertTrue(searchWindow.children.get(0).hidden);
    }

    @Test
    public void theTickLooksAtTheLineOnlyNowAndThen() {
        GeSearchPaste paste = paste();
        paste.text = "Abyssal whip";

        paste.tick();
        assertEquals(1, searchWindow.children.size());

        // A second tick straight after does no work: the line stays although the search closed.
        searchWindow.children.get(0).hidden = false;
        inputMode = PRICE_PROMPT;
        paste.tick();
        assertFalse(searchWindow.children.get(0).hidden);
    }

    @Test
    public void aClickDoesNothingWhenTheBoxHasNoKeyScript() {
        GeSearchPaste paste = paste();
        paste.text = "Abyssal whip";
        paste.update();
        keyScript = null;

        searchWindow.children.get(0).click();

        assertEquals("", searchText);
        assertTrue(scriptsRun.isEmpty());
    }

    @Test
    public void theSettingTurnsItOffAndStopsTheClipboardBeingRead() {
        settingOn = false;
        GeSearchPaste paste = paste();
        paste.text = "Abyssal whip";

        paste.update();

        assertTrue(searchWindow.children.isEmpty());
        assertTrue(clipboardReads.isEmpty());
    }

    @Test
    public void theClipboardIsReadWhileTheSearchIsOpenButNotOnEveryPass() {
        GeSearchPaste paste = paste();

        paste.update();
        paste.update();
        paste.update();

        assertEquals(1, clipboardReads.size());
    }

    @Test
    public void theClipboardIsNotReadWhileTheSearchIsClosed() {
        GeSearchPaste paste = paste();
        searchWindow.hidden = true;
        paste.update();
        searchWindow.hidden = false;
        inputMode = PRICE_PROMPT;
        paste.update();

        assertTrue(clipboardReads.isEmpty());
    }

    private GeSearchPaste paste() {
        FakeWidget searchBox = new FakeWidget(null);
        Client client = fake(Client.class, (method, args) -> {
            switch (method) {
                case "getVarcIntValue":
                    return inputMode;
                case "getVarcStrValue":
                    return searchText;
                case "setVarcStrValue":
                    searchText = (String) args[1];
                    return null;
                case "runScript":
                    scriptsRun.add((Object[]) args[0]);
                    return null;
                case "getWidget":
                    int id = (Integer) args[0];
                    if (id == ComponentID.CHATBOX_CONTAINER) {
                        return searchWindow.widget;
                    }
                    return id == ComponentID.CHATBOX_FULL_INPUT ? searchBox.widget : null;
                default:
                    return null;
            }
        });
        searchBox.keyScript = () -> keyScript;
        PluginConfig config = fake(PluginConfig.class, (method, args) ->
            method.equals("enableSearchPaste") ? (Object) settingOn : null);
        ChatboxSuggestionWidgetFactory factory = new ChatboxSuggestionWidgetFactory();
        GeSearchPaste paste = new GeSearchPaste(client, config, null,
            new ChatboxSuggestionRuntimeState(client, new ChatboxPromptWidgetResolver(client), factory), factory);
        // Never the real clipboard in a test.
        paste.reader = clipboardReads::add;
        return paste;
    }

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, BiFunction<String, Object[], Object> answers) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (proxy, method, args) -> answers.apply(method.getName(), args));
    }

    /** As much of a game widget as the line and its window need. */
    private static final class FakeWidget {
        private final Widget widget;
        private final FakeWidget parent;
        private final List<FakeWidget> children = new ArrayList<>();
        private java.util.function.Supplier<Object[]> keyScript = () -> null;
        private String text = "";
        private String name;
        private String action;
        private boolean hidden;
        private int x;
        private int y;
        private int xMode;
        private int yMode;
        private int width;
        private int fontId;
        private JavaScriptCallback onClick;

        private FakeWidget(FakeWidget parent) {
            this.parent = parent;
            this.widget = fake(Widget.class, this::answer);
        }

        private void click() {
            onClick.run(null);
        }

        private Object answer(String method, Object[] args) {
            switch (method) {
                case "createChild":
                    FakeWidget child = new FakeWidget(this);
                    children.add(child);
                    return child.widget;
                case "getChildren":
                case "getNestedChildren":
                    return null;
                case "getDynamicChildren":
                    return children.stream().map(c -> c.widget).toArray(Widget[]::new);
                case "getParent":
                    return parent == null ? null : parent.widget;
                case "getId":
                case "getParentId":
                    return ComponentID.CHATBOX_CONTAINER;
                case "getType":
                    return net.runelite.api.widgets.WidgetType.TEXT;
                case "getOnKeyListener":
                    return keyScript.get();
                case "setOnOpListener":
                    onClick = (JavaScriptCallback) ((Object[]) args[0])[0];
                    return null;
                case "setAction":
                    action = (String) args[1];
                    return null;
                case "getText":
                    return text;
                case "setText":
                    text = (String) args[0];
                    return null;
                case "getName":
                    return name;
                case "setName":
                    name = (String) args[0];
                    return null;
                case "isHidden":
                    return hidden || (parent != null && parent.hidden);
                case "isSelfHidden":
                    return hidden;
                case "setHidden":
                    hidden = (Boolean) args[0];
                    return null;
                case "setOriginalX":
                    x = (Integer) args[0];
                    return null;
                case "setOriginalY":
                    y = (Integer) args[0];
                    return null;
                case "setXPositionMode":
                    xMode = (Integer) args[0];
                    return null;
                case "setYPositionMode":
                    yMode = (Integer) args[0];
                    return null;
                case "getOriginalWidth":
                    return width;
                case "setOriginalWidth":
                    width = (Integer) args[0];
                    return null;
                case "setFontId":
                    fontId = (Integer) args[0];
                    return null;
                case "setTextColor":
                case "setOriginalHeight":
                case "setWidthMode":
                case "setXTextAlignment":
                case "setYTextAlignment":
                    return null;
                default:
                    return null;
            }
        }
    }
}
