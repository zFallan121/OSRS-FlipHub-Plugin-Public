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
import net.runelite.api.widgets.WidgetSizeMode;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ChatboxSuggestionPresentationServiceTest {
    @Test
    public void updatePriceSuggestionShowsAndReusesWidgetWhenUnchanged() {
        TestHooks hooks = new TestHooks();
        hooks.clientLoggedIn = true;
        hooks.chatboxContainer = widget(new WidgetState(false));
        hooks.offerPreviewItemId = 4151;
        hooks.offerPreviewItem = previewItem(4151, 123, 120, null);

        Widget prompt = promptWidget("How much do you wish to pay per item?");
        WidgetState priceState = new WidgetState(true);
        hooks.priceWidget = widget(priceState);

        ChatboxSuggestionPresentationService service = new ChatboxSuggestionPresentationService(hooks);
        service.updatePriceSuggestion(prompt, Boolean.TRUE);

        assertEquals("Current Buy Price: 123 gp", priceState.text);
        assertFalse(priceState.hidden);
        assertEquals(1, priceState.revalidateCalls);

        service.updatePriceSuggestion(prompt, Boolean.TRUE);
        assertEquals(1, priceState.revalidateCalls);
    }

    @Test
    public void updatePriceSuggestionShowsSellLabelWhenSellPrompt() {
        TestHooks hooks = new TestHooks();
        hooks.clientLoggedIn = true;
        hooks.chatboxContainer = widget(new WidgetState(false));
        hooks.offerPreviewItemId = 4151;
        hooks.offerPreviewItem = previewItem(4151, 123, 120, null);

        Widget prompt = promptWidget("How much do you wish to sell it for?");
        WidgetState priceState = new WidgetState(true);
        hooks.priceWidget = widget(priceState);

        ChatboxSuggestionPresentationService service = new ChatboxSuggestionPresentationService(hooks);
        service.updatePriceSuggestion(prompt, Boolean.FALSE);

        assertEquals("Current Sell Price: 120 gp", priceState.text);
        assertFalse(priceState.hidden);
        assertEquals(1, priceState.revalidateCalls);
    }

    @Test
    public void updatePriceSuggestionClearsWhenPromptWidgetUnavailable() {
        TestHooks hooks = new TestHooks();
        hooks.clientLoggedIn = true;
        hooks.chatboxContainer = widget(new WidgetState(false));
        hooks.offerPreviewItemId = 4151;
        hooks.offerPreviewItem = previewItem(4151, 123, 120, null);

        WidgetState priceState = new WidgetState(false);
        hooks.priceWidget = widget(priceState);
        hooks.priceWidgetValid = true;

        ChatboxSuggestionPresentationService service = new ChatboxSuggestionPresentationService(hooks);
        service.updatePriceSuggestion(null, Boolean.TRUE);

        assertTrue(priceState.hidden);
        assertEquals(1, priceState.revalidateCalls);
    }

    @Test
    public void updatePriceSuggestionClearsWhenQuantityPromptPassed() {
        TestHooks hooks = new TestHooks();
        hooks.clientLoggedIn = true;
        hooks.chatboxContainer = widget(new WidgetState(false));
        hooks.offerPreviewItemId = 4151;
        hooks.offerPreviewItem = previewItem(4151, 123, 120, null);

        WidgetState priceState = new WidgetState(false);
        hooks.priceWidget = widget(priceState);
        hooks.priceWidgetValid = true;

        ChatboxSuggestionPresentationService service = new ChatboxSuggestionPresentationService(hooks);
        service.updatePriceSuggestion(promptWidget("How many do you wish to buy?"), Boolean.TRUE);

        assertTrue(priceState.hidden);
        assertEquals(1, priceState.revalidateCalls);
    }

    @Test
    public void updatePriceSuggestionClearsWhenPreviewMismatched() {
        TestHooks hooks = new TestHooks();
        hooks.clientLoggedIn = true;
        hooks.chatboxContainer = widget(new WidgetState(false));
        hooks.offerPreviewItemId = 4151;
        hooks.offerPreviewItem = previewItem(4152, 123, 120, null);

        WidgetState priceState = new WidgetState(false);
        hooks.priceWidget = widget(priceState);
        hooks.priceWidgetValid = true;
        Widget existingWidget = hooks.priceWidget;

        ChatboxSuggestionPresentationService service = new ChatboxSuggestionPresentationService(hooks);
        service.updatePriceSuggestion(promptWidget("How much do you wish to pay per item?"), Boolean.TRUE);

        assertTrue(priceState.hidden);
        assertEquals(1, priceState.revalidateCalls);
        assertSame(existingWidget, hooks.priceWidget);
    }

    @Test
    public void updateLimitSuggestionUsesPreviewRemainingAndAffordable() {
        TestHooks hooks = new TestHooks();
        hooks.clientLoggedIn = true;
        hooks.chatboxContainer = widget(new WidgetState(false));
        hooks.offerPreviewItemId = 4151;
        hooks.offerPreviewItem = previewItem(4151, 123, 120, 60);
        hooks.affordableSuggestion = 25;

        WidgetState limitState = new WidgetState(true);
        WidgetState affordableState = new WidgetState(true);
        hooks.limitWidget = widget(limitState);
        hooks.affordableWidget = widget(affordableState);

        ChatboxSuggestionPresentationService service = new ChatboxSuggestionPresentationService(hooks);
        service.updateLimitSuggestion(promptWidget("How many do you wish to buy?"), Boolean.TRUE);

        assertEquals(1, hooks.cacheRemainingCalls);
        assertEquals(Integer.valueOf(4151), hooks.lastCachedRemainingItemId);
        assertEquals(Integer.valueOf(60), hooks.lastCachedRemaining);
        assertEquals(0, hooks.getThrottledRemainingCalls);

        assertEquals("Remaining GE limit: 60", limitState.text);
        assertEquals(WidgetSizeMode.ABSOLUTE, limitState.widthMode);
        assertFalse(limitState.hidden);
        assertEquals(1, limitState.revalidateCalls);

        assertEquals("Cash limit: 25", affordableState.text);
        assertEquals(WidgetSizeMode.ABSOLUTE, affordableState.widthMode);
        assertFalse(affordableState.hidden);
        assertEquals(1, affordableState.revalidateCalls);
    }

    @Test
    public void updateLimitSuggestionClearsWhenNoSuggestionAvailable() {
        TestHooks hooks = new TestHooks();
        hooks.clientLoggedIn = true;
        hooks.chatboxContainer = widget(new WidgetState(false));
        hooks.offerPreviewItemId = 4151;
        hooks.offerPreviewItem = previewItem(4151, 123, 120, null);
        hooks.throttledRemainingSuggestion = null;
        hooks.affordableSuggestion = null;

        WidgetState limitState = new WidgetState(false);
        WidgetState affordableState = new WidgetState(false);
        hooks.limitWidget = widget(limitState);
        hooks.affordableWidget = widget(affordableState);
        hooks.limitWidgetValid = true;
        hooks.affordableWidgetValid = true;

        ChatboxSuggestionPresentationService service = new ChatboxSuggestionPresentationService(hooks);
        service.updateLimitSuggestion(promptWidget("How many do you wish to buy?"), Boolean.TRUE);

        assertTrue(limitState.hidden);
        assertTrue(affordableState.hidden);
        assertEquals(1, hooks.clearRemainingLimitSuggestionCacheCalls);
        assertEquals(1, hooks.getThrottledRemainingCalls);
    }

    @Test
    public void updateLimitSuggestionClearsWhenQuantityPromptUnavailable() {
        TestHooks hooks = new TestHooks();
        hooks.clientLoggedIn = true;
        hooks.chatboxContainer = widget(new WidgetState(false));
        hooks.offerPreviewItemId = 4151;
        hooks.offerPreviewItem = previewItem(4151, 123, 120, 60);
        hooks.affordableSuggestion = 25;

        WidgetState limitState = new WidgetState(false);
        WidgetState affordableState = new WidgetState(false);
        hooks.limitWidget = widget(limitState);
        hooks.affordableWidget = widget(affordableState);
        hooks.limitWidgetValid = true;
        hooks.affordableWidgetValid = true;

        ChatboxSuggestionPresentationService service = new ChatboxSuggestionPresentationService(hooks);
        service.updateLimitSuggestion(null, Boolean.TRUE);

        assertTrue(limitState.hidden);
        assertTrue(affordableState.hidden);
        assertEquals(1, limitState.revalidateCalls);
        assertEquals(1, affordableState.revalidateCalls);
    }

    @Test
    public void updateLimitSuggestionClearsWhenPricePromptPassed() {
        TestHooks hooks = new TestHooks();
        hooks.clientLoggedIn = true;
        hooks.chatboxContainer = widget(new WidgetState(false));
        hooks.offerPreviewItemId = 4151;
        hooks.offerPreviewItem = previewItem(4151, 123, 120, 60);
        hooks.affordableSuggestion = 25;

        WidgetState limitState = new WidgetState(false);
        WidgetState affordableState = new WidgetState(false);
        hooks.limitWidget = widget(limitState);
        hooks.affordableWidget = widget(affordableState);
        hooks.limitWidgetValid = true;
        hooks.affordableWidgetValid = true;

        ChatboxSuggestionPresentationService service = new ChatboxSuggestionPresentationService(hooks);
        service.updateLimitSuggestion(promptWidget("How much do you wish to pay per item?"), Boolean.TRUE);

        assertTrue(limitState.hidden);
        assertTrue(affordableState.hidden);
        assertEquals(1, limitState.revalidateCalls);
        assertEquals(1, affordableState.revalidateCalls);
    }

    @Test
    public void clearLimitSuggestionDropsInvalidWidgetAndClearsCache() {
        TestHooks hooks = new TestHooks();
        hooks.chatboxContainer = widget(new WidgetState(false));
        hooks.limitWidget = widget(new WidgetState(true));
        hooks.limitWidgetValid = false;

        ChatboxSuggestionPresentationService service = new ChatboxSuggestionPresentationService(hooks);
        service.clearLimitSuggestion();

        assertNull(hooks.limitWidget);
        assertEquals(1, hooks.clearRemainingLimitSuggestionCacheCalls);
    }

    private static FlipHubItem previewItem(int itemId, Integer instabuy, Integer instasell, Integer remaining) {
        FlipHubItem item = new FlipHubItem();
        item.item_id = itemId;
        item.instabuy_price = instabuy;
        item.instasell_price = instasell;
        item.ge_limit_remaining = remaining;
        return item;
    }

    private static Widget widget(WidgetState state) {
        return (Widget) Proxy.newProxyInstance(
            Widget.class.getClassLoader(),
            new Class<?>[] {Widget.class},
            (proxy, method, args) -> handleWidgetMethod(state, method, args)
        );
    }

    private static Widget promptWidget(String text) {
        WidgetState state = new WidgetState(false);
        state.text = text;
        return widget(state);
    }

    private static Object handleWidgetMethod(WidgetState state, Method method, Object[] args) {
        switch (method.getName()) {
            case "isHidden":
                return state.hidden;
            case "setHidden":
                state.hidden = args != null && args.length > 0 && Boolean.TRUE.equals(args[0]);
                return null;
            case "getText":
                return state.text;
            case "setText":
                state.text = args != null && args.length > 0 ? String.valueOf(args[0]) : "";
                return null;
            case "getOriginalWidth":
                return state.originalWidth;
            case "setOriginalWidth":
                state.originalWidth = args != null && args.length > 0 ? (Integer) args[0] : 0;
                return null;
            case "setWidthMode":
                state.widthMode = args != null && args.length > 0 ? (Integer) args[0] : 0;
                return null;
            case "revalidate":
                state.revalidateCalls++;
                return null;
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

    private static final class WidgetState {
        private boolean hidden;
        private String text = "";
        private int originalWidth;
        private int widthMode;
        private int revalidateCalls;

        WidgetState(boolean hidden) {
            this.hidden = hidden;
        }
    }

    private static final class TestHooks implements ChatboxSuggestionPresentationService.Hooks {
        private boolean clientLoggedIn;
        private Widget chatboxContainer;
        private Integer offerPreviewItemId;
        private FlipHubItem offerPreviewItem;
        private Widget priceWidget;
        private Widget limitWidget;
        private Widget affordableWidget;
        private boolean priceWidgetValid = true;
        private boolean limitWidgetValid = true;
        private boolean affordableWidgetValid = true;
        private Integer throttledRemainingSuggestion;
        private Integer affordableSuggestion;
        private int getThrottledRemainingCalls;
        private int cacheRemainingCalls;
        private Integer lastCachedRemainingItemId;
        private Integer lastCachedRemaining;
        private int clearRemainingLimitSuggestionCacheCalls;

        @Override
        public boolean isClientLoggedIn() {
            return clientLoggedIn;
        }

        @Override
        public Widget getChatboxContainer() {
            return chatboxContainer;
        }

        @Override
        public Integer getOfferPreviewItemId() {
            return offerPreviewItemId;
        }

        @Override
        public FlipHubItem getOfferPreviewItem() {
            return offerPreviewItem;
        }

        @Override
        public Widget ensurePriceSuggestionWidget(Widget container) {
            return priceWidget;
        }

        @Override
        public Widget ensureLimitSuggestionWidget(Widget container) {
            return limitWidget;
        }

        @Override
        public Widget ensureAffordableLimitSuggestionWidget(Widget container) {
            return affordableWidget;
        }

        @Override
        public Widget getPriceSuggestionWidget() {
            return priceWidget;
        }

        @Override
        public void setPriceSuggestionWidget(Widget widget) {
            priceWidget = widget;
        }

        @Override
        public Widget getLimitSuggestionWidget() {
            return limitWidget;
        }

        @Override
        public void setLimitSuggestionWidget(Widget widget) {
            limitWidget = widget;
        }

        @Override
        public Widget getAffordableLimitSuggestionWidget() {
            return affordableWidget;
        }

        @Override
        public void setAffordableLimitSuggestionWidget(Widget widget) {
            affordableWidget = widget;
        }

        @Override
        public boolean isSuggestionWidgetValid(Widget container) {
            return priceWidgetValid;
        }

        @Override
        public boolean isLimitWidgetValid(Widget container) {
            return limitWidgetValid;
        }

        @Override
        public boolean isAffordableLimitWidgetValid(Widget container) {
            return affordableWidgetValid;
        }

        @Override
        public String formatPrice(int price) {
            return String.valueOf(price);
        }

        @Override
        public Integer getThrottledRemainingLimitSuggestion(int itemId) {
            getThrottledRemainingCalls++;
            return throttledRemainingSuggestion;
        }

        @Override
        public void cacheRemainingLimitSuggestion(int itemId, Integer remaining) {
            cacheRemainingCalls++;
            lastCachedRemainingItemId = itemId;
            lastCachedRemaining = remaining;
        }

        @Override
        public Integer computeAffordableLimitSuggestion(Integer remainingLimit) {
            return affordableSuggestion;
        }

        @Override
        public void clearRemainingLimitSuggestionCache() {
            clearRemainingLimitSuggestionCacheCalls++;
        }
    }
}
