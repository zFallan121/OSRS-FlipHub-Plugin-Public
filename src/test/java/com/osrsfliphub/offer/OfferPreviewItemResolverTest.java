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
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.widgets.Widget;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OfferPreviewItemResolverTest {
    @Test
    public void resolveClearsWhenSetupOfferIsBlocked() {
        TestHooks hooks = new TestHooks();
        hooks.geRoot = widget(null, false);
        hooks.newOfferTypeVarbit = 1;
        hooks.offerContainer = widget("Choose an item", false);
        hooks.offerContainerItemId = 4151;
        OfferPreviewItemResolver resolver = new OfferPreviewItemResolver(hooks, new String[] {"choose an item"});

        OfferPreviewItemResolver.Resolution resolution = resolver.resolve();

        assertTrue(resolution.shouldClear());
        assertNull(resolution.getItemId());
    }

    @Test
    public void resolveUsesSetupItemWhenAvailable() {
        TestHooks hooks = new TestHooks();
        hooks.geRoot = widget(null, false);
        hooks.newOfferTypeVarbit = 1;
        hooks.offerContainer = widget("Buy offer", false);
        hooks.offerContainerItemId = 560;
        OfferPreviewItemResolver resolver = new OfferPreviewItemResolver(hooks, new String[] {"choose an item"});

        OfferPreviewItemResolver.Resolution resolution = resolver.resolve();

        assertEquals(Integer.valueOf(560), resolution.getItemId());
        assertNull(resolution.getItemName());
    }

    @Test
    public void resolveUsesSetupItemWhenOfferContainerVisibleEvenIfVarbitIsZero() {
        TestHooks hooks = new TestHooks();
        hooks.geRoot = null;
        hooks.newOfferTypeVarbit = 0;
        hooks.offerContainer = widget("Buy offer", false);
        hooks.offerContainerItemId = 987;
        OfferPreviewItemResolver resolver = new OfferPreviewItemResolver(hooks, new String[] {"choose an item"});

        OfferPreviewItemResolver.Resolution resolution = resolver.resolve();

        assertEquals(Integer.valueOf(987), resolution.getItemId());
        assertNull(resolution.getItemName());
    }

    @Test
    public void resolveClearsWhenSetupBlockerExistsInOfferContainerChildren() {
        TestHooks hooks = new TestHooks();
        Widget childWithBlocker = widget("Choose an item...", false);
        hooks.geRoot = widget(null, false);
        hooks.newOfferTypeVarbit = 1;
        hooks.offerContainer = widgetWithChildren(null, false, new Widget[] {childWithBlocker});
        hooks.offerContainerItemId = 4151;
        OfferPreviewItemResolver resolver = new OfferPreviewItemResolver(hooks, new String[] {"choose an item"});

        OfferPreviewItemResolver.Resolution resolution = resolver.resolve();

        assertTrue(resolution.shouldClear());
    }

    @Test
    public void resolvePrefersVarpOverSelectedSlotWhenBothPresent() {
        TestHooks hooks = new TestHooks();
        hooks.geRoot = widget(null, false);
        hooks.selectedSlotVarbit = 1;
        hooks.currentGeItemVarp = 4151;
        hooks.selectedOffer = offer(560);
        OfferPreviewItemResolver resolver = new OfferPreviewItemResolver(hooks, new String[0]);

        OfferPreviewItemResolver.Resolution resolution = resolver.resolve();

        assertEquals(Integer.valueOf(4151), resolution.getItemId());
    }

    @Test
    public void resolveUsesTextFallbackWhenAllowed() {
        TestHooks hooks = new TestHooks();
        hooks.geRoot = widget(null, false);
        hooks.selectedSlotVarbit = 1;
        hooks.itemNameCandidate = "Rune knife";
        hooks.resolvedItemId = 560;
        OfferPreviewItemResolver resolver = new OfferPreviewItemResolver(hooks, new String[0]);

        OfferPreviewItemResolver.Resolution resolution = resolver.resolve();

        assertEquals(Integer.valueOf(560), resolution.getItemId());
        assertEquals("Rune knife", resolution.getItemName());
    }

    @Test
    public void resolveClearsWhenGeOpenWithoutSignalsAndNoSlot() {
        TestHooks hooks = new TestHooks();
        hooks.geRoot = widget(null, false);
        hooks.selectedSlotVarbit = 0;
        hooks.currentGeItemVarp = 4151;
        OfferPreviewItemResolver resolver = new OfferPreviewItemResolver(hooks, new String[0]);

        OfferPreviewItemResolver.Resolution resolution = resolver.resolve();

        assertTrue(resolution.shouldClear());
    }

    private static GrandExchangeOffer offer(int itemId) {
        return (GrandExchangeOffer) Proxy.newProxyInstance(
            GrandExchangeOffer.class.getClassLoader(),
            new Class<?>[] {GrandExchangeOffer.class},
            (proxy, method, args) -> {
                if ("getItemId".equals(method.getName())) {
                    return itemId;
                }
                return defaultValue(method);
            }
        );
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

    private static Widget widgetWithChildren(String text, boolean hidden, Widget[] children) {
        return (Widget) Proxy.newProxyInstance(
            Widget.class.getClassLoader(),
            new Class<?>[] {Widget.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getText":
                        return text;
                    case "isHidden":
                        return hidden;
                    case "getChildren":
                        return children;
                    case "getDynamicChildren":
                    case "getNestedChildren":
                        return null;
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

    private static final class TestHooks implements OfferPreviewItemResolver.Hooks {
        private Widget geRoot;
        private boolean offerStatusOpen;
        private int newOfferTypeVarbit;
        private int selectedSlotVarbit = -1;
        private Widget offerContainer;
        private int offerContainerItemId = -1;
        private int geRootItemId = -1;
        private int currentGeItemVarp = -1;
        private GrandExchangeOffer selectedOffer;
        private String itemNameCandidate;
        private int resolvedItemId = -1;

        @Override
        public Widget getVisibleGeRoot() {
            return geRoot;
        }

        @Override
        public boolean isOfferStatusOpen(Widget geRoot) {
            return offerStatusOpen;
        }

        @Override
        public int getNewOfferTypeVarbit() {
            return newOfferTypeVarbit;
        }

        @Override
        public int getSelectedSlotVarbit() {
            return selectedSlotVarbit;
        }

        @Override
        public Widget getOfferContainer() {
            return offerContainer;
        }

        @Override
        public String normalizeOfferText(String text) {
            return OfferPreviewWidgetParser.normalizeText(text);
        }

        @Override
        public int findFirstItemId(Widget widget) {
            if (widget == offerContainer) {
                return offerContainerItemId;
            }
            if (widget == geRoot) {
                return geRootItemId;
            }
            return -1;
        }

        @Override
        public int getCurrentGeItemVarp() {
            return currentGeItemVarp;
        }

        @Override
        public GrandExchangeOffer getSelectedOffer() {
            return selectedOffer;
        }

        @Override
        public String findItemNameCandidate(Widget geRoot) {
            return itemNameCandidate;
        }

        @Override
        public int resolveItemIdFromName(String name) {
            return name != null && name.equals(itemNameCandidate) ? resolvedItemId : -1;
        }
    }
}
