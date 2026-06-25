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
import net.runelite.api.widgets.Widget;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ChatboxSuggestionCycleFactoryServiceTest {
    @Test
    public void createWithNullHooksProducesNoOpService() {
        ChatboxSuggestionCycleFactoryService factory = new ChatboxSuggestionCycleFactoryService();
        ChatboxSuggestionCycleService service = factory.create(null);
        service.update();
    }

    @Test
    public void updateDelegatesToRuntimeHooksWhenReady() {
        ChatboxSuggestionCycleFactoryService factory = new ChatboxSuggestionCycleFactoryService();
        TestRuntimeHooks hooks = new TestRuntimeHooks();
        hooks.clientLoggedIn = true;
        hooks.geInputPromptActive = true;
        hooks.chatboxInputVisible = true;
        hooks.suggestionDirty = true;
        hooks.geRootVisible = true;
        hooks.offerType = Boolean.TRUE;
        hooks.pricePromptWidget = widget();
        hooks.quantityPromptWidget = widget();

        ChatboxSuggestionCycleService service = factory.create(hooks);
        service.update();

        assertEquals(1, hooks.setLastSuggestionUpdateCalls);
        assertEquals(1, hooks.setSuggestionDirtyCalls);
        assertFalse(hooks.suggestionDirty);
        assertEquals(1, hooks.updatePriceSuggestionCalls);
        assertEquals(1, hooks.updateLimitSuggestionCalls);
        assertSame(hooks.pricePromptWidget, hooks.lastPricePromptWidget);
        assertSame(hooks.quantityPromptWidget, hooks.lastQuantityPromptWidget);
        assertTrue(hooks.lastOfferType);
        assertEquals(0, hooks.clearSuggestionsCalls);
        assertEquals(0, hooks.clearPromptWidgetCacheCalls);
    }

    @Test
    public void updateStillDelegatesWhenPromptsUnavailableButOfferContextExists() {
        ChatboxSuggestionCycleFactoryService factory = new ChatboxSuggestionCycleFactoryService();
        TestRuntimeHooks hooks = new TestRuntimeHooks();
        hooks.clientLoggedIn = true;
        hooks.geInputPromptActive = true;
        hooks.chatboxInputVisible = true;
        hooks.suggestionDirty = true;
        hooks.geRootVisible = true;
        hooks.offerType = Boolean.FALSE;
        hooks.pricePromptWidget = null;
        hooks.quantityPromptWidget = null;

        ChatboxSuggestionCycleService service = factory.create(hooks);
        service.update();

        assertEquals(0, hooks.clearSuggestionsCalls);
        assertEquals(1, hooks.updatePriceSuggestionCalls);
        assertEquals(1, hooks.updateLimitSuggestionCalls);
        assertEquals(1, hooks.setLastSuggestionUpdateCalls);
        assertEquals(1, hooks.setSuggestionDirtyCalls);
    }

    private static Widget widget() {
        return (Widget) Proxy.newProxyInstance(
            Widget.class.getClassLoader(),
            new Class<?>[] {Widget.class},
            (proxy, method, args) -> defaultValue(method.getReturnType())
        );
    }

    private static Object defaultValue(Class<?> returnType) {
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

    private static final class TestRuntimeHooks implements ChatboxSuggestionCycleFactoryService.RuntimeHooks {
        private boolean clientLoggedIn;
        private boolean geInputPromptActive;
        private boolean chatboxInputVisible;
        private boolean suggestionDirty;
        private boolean geRootVisible;
        private Boolean offerType;
        private Widget pricePromptWidget;
        private Widget quantityPromptWidget;

        private int setSuggestionDirtyCalls;
        private int setLastSuggestionUpdateCalls;
        private int clearSuggestionsCalls;
        private int clearPromptWidgetCacheCalls;
        private int updatePriceSuggestionCalls;
        private int updateLimitSuggestionCalls;
        private Widget lastPricePromptWidget;
        private Widget lastQuantityPromptWidget;
        private Boolean lastOfferType;

        @Override
        public boolean isClientLoggedIn() {
            return clientLoggedIn;
        }

        @Override
        public boolean isGeInputPromptActive() {
            return geInputPromptActive;
        }

        @Override
        public boolean isChatboxInputVisible() {
            return chatboxInputVisible;
        }

        @Override
        public boolean isSuggestionDirty() {
            return suggestionDirty;
        }

        @Override
        public void setSuggestionDirty(boolean dirty) {
            setSuggestionDirtyCalls++;
            suggestionDirty = dirty;
        }

        @Override
        public void setLastSuggestionUpdateMs(long timestampMs) {
            setLastSuggestionUpdateCalls++;
        }

        @Override
        public void clearSuggestions() {
            clearSuggestionsCalls++;
        }

        @Override
        public void clearPromptWidgetCache() {
            clearPromptWidgetCacheCalls++;
        }

        @Override
        public Widget getPricePromptWidget() {
            return pricePromptWidget;
        }

        @Override
        public Widget getQuantityPromptWidget() {
            return quantityPromptWidget;
        }

        @Override
        public boolean isGeRootVisible() {
            return geRootVisible;
        }

        @Override
        public Boolean resolveOfferType() {
            return offerType;
        }

        @Override
        public void updatePriceSuggestion(Widget promptWidget, Boolean isBuy) {
            updatePriceSuggestionCalls++;
            lastPricePromptWidget = promptWidget;
            lastOfferType = isBuy;
        }

        @Override
        public void updateLimitSuggestion(Widget promptWidget, Boolean isBuy) {
            updateLimitSuggestionCalls++;
            lastQuantityPromptWidget = promptWidget;
            lastOfferType = isBuy;
        }

        @Override
        public long nowMs() {
            return 123L;
        }
    }
}
