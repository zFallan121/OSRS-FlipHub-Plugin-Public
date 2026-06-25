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
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ChatboxSuggestionWidgetsTest {
    @Test
    public void isPricePromptWidgetMatchesNormalizedPromptText() {
        Widget widget = widget("<col=ff0000>Set a price for each item</col>", false, 0, null,
            0, 0, null, null, null, null, 0, 0);

        assertTrue(ChatboxSuggestionWidgets.isPricePromptWidget(widget));
        assertFalse(ChatboxSuggestionWidgets.isQuantityPromptWidget(widget));
    }

    @Test
    public void isQuantityPromptWidgetMatchesQuantityPromptText() {
        Widget widget = widget("How many do you wish to buy?", false, 0, null,
            0, 0, null, null, null, null, 0, 0);

        assertTrue(ChatboxSuggestionWidgets.isQuantityPromptWidget(widget));
        assertFalse(ChatboxSuggestionWidgets.isPricePromptWidget(widget));
    }

    @Test
    public void isQuantityPromptWidgetMatchesSellQuantityPromptText() {
        Widget widget = widget("How many do you wish to sell?", false, 0, null,
            0, 0, null, null, null, null, 0, 0);

        assertTrue(ChatboxSuggestionWidgets.isQuantityPromptWidget(widget));
        assertFalse(ChatboxSuggestionWidgets.isPricePromptWidget(widget));
    }

    @Test
    public void isPricePromptWidgetMatchesHowMuchPromptText() {
        Widget widget = widget("How much do you wish to pay for each item?", false, 0, null,
            0, 0, null, null, null, null, 0, 0);

        assertTrue(ChatboxSuggestionWidgets.isPricePromptWidget(widget));
        assertFalse(ChatboxSuggestionWidgets.isQuantityPromptWidget(widget));
    }

    @Test
    public void findPromptWidgetTraversesWidgetTree() {
        Widget prompt = widget("Enter price", false, 0, null,
            0, 0, null, null, null, null, 0, 0);
        Widget wrapper = widget(null, false, 0, null,
            0, 0, null, new Widget[] {prompt}, null, null, 0, 0);
        Widget root = widget(null, false, 0, null,
            0, 0, null, null, new Widget[] {wrapper}, null, 0, 0);

        Widget found = ChatboxSuggestionWidgets.findPromptWidget(root, true);

        assertSame(prompt, found);
    }

    @Test
    public void findNamedTextWidgetFindsNestedSuggestionWidget() {
        Widget target = widget(null, false, WidgetType.TEXT, "FlipHub Current Price",
            0, 0, null, null, null, null, 0, 0);
        Widget wrapper = widget(null, false, 0, null,
            0, 0, null, null, null, new Widget[] {target}, 0, 0);
        Widget container = widget(null, false, 0, null,
            100, 0, null, new Widget[] {wrapper}, null, null, 0, 0);

        Widget found = ChatboxSuggestionWidgets.findNamedTextWidget(container, "FlipHub Current Price");

        assertNotNull(found);
        assertSame(target, found);
    }

    @Test
    public void isWidgetInParentAndComputeSuggestionYWorkAsExpected() {
        Widget child = widget(null, false, 0, null, 0, 12, null,
            null, null, null, 0, 0);
        Widget parent = widget(null, false, 0, null, 12, 0, null,
            new Widget[] {child}, null, null, 15, 0);

        assertTrue(ChatboxSuggestionWidgets.isWidgetInParent(parent, child));
        assertFalse(ChatboxSuggestionWidgets.isWidgetInParent(parent, null));
        assertNull(ChatboxSuggestionWidgets.findNamedTextWidget(parent, "missing"));
        assertEquals(10, ChatboxSuggestionWidgets.computeSuggestionY(parent, 10, 20));
        assertEquals(2, ChatboxSuggestionWidgets.computeSuggestionY(parent, -4, 20));
    }

    private static Widget widget(String text,
                                 boolean hidden,
                                 int type,
                                 String name,
                                 int id,
                                 int parentId,
                                 Widget parent,
                                 Widget[] children,
                                 Widget[] dynamicChildren,
                                 Widget[] nestedChildren,
                                 int originalHeight,
                                 int height) {
        return (Widget) Proxy.newProxyInstance(
            Widget.class.getClassLoader(),
            new Class<?>[] {Widget.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getText":
                        return text;
                    case "isHidden":
                        return hidden;
                    case "getType":
                        return type;
                    case "getName":
                        return name;
                    case "getId":
                        return id;
                    case "getParentId":
                        return parentId;
                    case "getParent":
                        return parent;
                    case "getChildren":
                        return children;
                    case "getDynamicChildren":
                        return dynamicChildren;
                    case "getNestedChildren":
                        return nestedChildren;
                    case "getOriginalHeight":
                        return originalHeight;
                    case "getHeight":
                        return height;
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
        if (returnType == char.class) {
            return '\0';
        }
        return 0;
    }
}
