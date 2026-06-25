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
import net.runelite.api.Client;
import net.runelite.api.VarClientInt;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChatboxSuggestionRuntimeStateServiceTest {
    @Test
    public void isGeInputPromptActiveWhenInputTypeIsSeven() {
        TestHooks hooks = new TestHooks();
        hooks.inputType = 7;
        ChatboxSuggestionRuntimeStateService service = new ChatboxSuggestionRuntimeStateService(hooks);

        assertTrue(service.isGeInputPromptActive());
    }

    @Test
    public void isGeInputPromptActiveWhenPromptTextVisibleWithNonSevenInputType() {
        TestHooks hooks = new TestHooks();
        hooks.inputType = 14;
        hooks.widgets.put(ComponentID.CHATBOX_TITLE, widget("Set a price for each item:", false));
        ChatboxSuggestionRuntimeStateService service = new ChatboxSuggestionRuntimeStateService(hooks);

        assertTrue(service.isGeInputPromptActive());
    }

    @Test
    public void isGeInputPromptInactiveWhenNoPromptWidgetsAvailable() {
        TestHooks hooks = new TestHooks();
        hooks.inputType = 14;
        hooks.widgets.put(ComponentID.CHATBOX_TITLE, widget("Search item", false));
        ChatboxSuggestionRuntimeStateService service = new ChatboxSuggestionRuntimeStateService(hooks);

        assertFalse(service.isGeInputPromptActive());
    }

    private static Widget widget(String text, boolean hidden) {
        return (Widget) Proxy.newProxyInstance(
            Widget.class.getClassLoader(),
            new Class<?>[] {Widget.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getText":
                        return text;
                    case "isHidden":
                        return hidden;
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

    private static final class TestHooks implements ChatboxSuggestionRuntimeStateService.Hooks {
        private final Map<Integer, Widget> widgets = new HashMap<>();
        private int inputType;
        private final Client client = (Client) Proxy.newProxyInstance(
            Client.class.getClassLoader(),
            new Class<?>[] {Client.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getVarcIntValue":
                        int key = (int) args[0];
                        return key == VarClientInt.INPUT_TYPE ? inputType : 0;
                    case "getWidget":
                        Integer componentId = (Integer) args[0];
                        return widgets.get(componentId);
                    default:
                        return defaultValue(method);
                }
            }
        );

        @Override
        public Client getClient() {
            return client;
        }

        @Override
        public ChatboxSuggestionWidgetFactoryService getWidgetFactoryService() {
            return null;
        }

        @Override
        public ChatboxPromptWidgetResolverService getPromptWidgetResolverService() {
            return null;
        }
    }
}
