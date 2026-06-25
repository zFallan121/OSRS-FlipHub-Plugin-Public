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
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.widgets.Widget;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class ChatboxPromptWidgetResolverServiceTest {
    private static final int FULL_INPUT = 1;
    private static final int TITLE = 2;
    private static final int FIRST_MESSAGE = 3;
    private static final int MESSAGE_LINES = 4;
    private static final int CONTAINER = 5;

    @Test
    public void resolvePromptWidgetReturnsCachedWhenValid() {
        TestHooks hooks = new TestHooks();
        ChatboxPromptWidgetResolverService service = service(hooks);
        Widget cached = widget("Enter price", false, null, null, null);

        Widget resolved = service.resolvePromptWidget(cached, true);

        assertSame(cached, resolved);
    }

    @Test
    public void resolvePromptWidgetUsesDirectPromptBeforeTreeLookup() {
        TestHooks hooks = new TestHooks();
        hooks.widgetsByComponent.put(FULL_INPUT, widget("Offer status", false, null, null, null));
        Widget titlePrompt = widget("Set a price for each item", false, null, null, null);
        hooks.widgetsByComponent.put(TITLE, titlePrompt);
        Widget messagePrompt = widget("Enter price", false, null, null, null);
        hooks.widgetsByComponent.put(MESSAGE_LINES, widget(null, false, new Widget[] {messagePrompt}, null, null));

        ChatboxPromptWidgetResolverService service = service(hooks);
        Widget resolved = service.resolvePromptWidget(null, true);

        assertSame(titlePrompt, resolved);
    }

    @Test
    public void resolvePromptWidgetFallsBackToMessageLinesThenContainer() {
        TestHooks hooks = new TestHooks();
        Widget messagePrompt = widget("How many do you wish to buy?", false, null, null, null);
        hooks.widgetsByComponent.put(MESSAGE_LINES, widget(null, false, new Widget[] {messagePrompt}, null, null));
        Widget containerPrompt = widget("set the quantity", false, null, null, null);
        hooks.widgetsByComponent.put(CONTAINER, widget(null, false, null, new Widget[] {containerPrompt}, null));

        ChatboxPromptWidgetResolverService service = service(hooks);
        Widget resolvedFromMessageLines = service.resolvePromptWidget(null, false);
        assertSame(messagePrompt, resolvedFromMessageLines);

        hooks.widgetsByComponent.put(MESSAGE_LINES, null);
        Widget resolvedFromContainer = service.resolvePromptWidget(null, false);
        assertSame(containerPrompt, resolvedFromContainer);
    }

    @Test
    public void resolvePromptWidgetReturnsNullWhenNoPromptFound() {
        TestHooks hooks = new TestHooks();
        hooks.widgetsByComponent.put(FULL_INPUT, widget("Offer status", false, null, null, null));
        hooks.widgetsByComponent.put(TITLE, widget("Offer status", false, null, null, null));
        hooks.widgetsByComponent.put(FIRST_MESSAGE, widget("Offer status", false, null, null, null));
        hooks.widgetsByComponent.put(MESSAGE_LINES, widget("Offer status", false, null, null, null));
        hooks.widgetsByComponent.put(CONTAINER, widget("Offer status", false, null, null, null));

        ChatboxPromptWidgetResolverService service = service(hooks);
        Widget resolved = service.resolvePromptWidget(null, true);

        assertNull(resolved);
    }

    private static ChatboxPromptWidgetResolverService service(TestHooks hooks) {
        return new ChatboxPromptWidgetResolverService(
            FULL_INPUT,
            TITLE,
            FIRST_MESSAGE,
            MESSAGE_LINES,
            CONTAINER,
            hooks
        );
    }

    private static Widget widget(String text,
                                 boolean hidden,
                                 Widget[] children,
                                 Widget[] dynamicChildren,
                                 Widget[] nestedChildren) {
        return (Widget) Proxy.newProxyInstance(
            Widget.class.getClassLoader(),
            new Class<?>[] {Widget.class},
            (proxy, method, args) -> handleWidgetMethod(text, hidden, children, dynamicChildren, nestedChildren, method)
        );
    }

    private static Object handleWidgetMethod(String text,
                                             boolean hidden,
                                             Widget[] children,
                                             Widget[] dynamicChildren,
                                             Widget[] nestedChildren,
                                             Method method) {
        switch (method.getName()) {
            case "getText":
                return text;
            case "isHidden":
                return hidden;
            case "getChildren":
                return children;
            case "getDynamicChildren":
                return dynamicChildren;
            case "getNestedChildren":
                return nestedChildren;
            default:
                return defaultValue(method);
        }
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

    private static final class TestHooks implements ChatboxPromptWidgetResolverService.Hooks {
        private final Map<Integer, Widget> widgetsByComponent = new HashMap<>();

        @Override
        public Widget getWidget(int componentId) {
            return widgetsByComponent.get(componentId);
        }
    }
}
