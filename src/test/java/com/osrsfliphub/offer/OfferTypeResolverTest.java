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
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.widgets.Widget;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class OfferTypeResolverTest {
    @Test
    public void resolveOfferTypePrefersSelectedOfferOverVarbit() {
        TestHooks hooks = new TestHooks();
        hooks.newOfferTypeVarbit = 2;
        hooks.selectedOffer = offerWithState(GrandExchangeOfferState.BUYING);
        OfferTypeResolver resolver = new OfferTypeResolver(hooks);

        assertEquals(Boolean.TRUE, resolver.resolveOfferType());
    }

    @Test
    public void resolveOfferTypeUsesKnownVarbitDefaults() {
        TestHooks hooks = new TestHooks();
        OfferTypeResolver resolver = new OfferTypeResolver(hooks);

        hooks.newOfferTypeVarbit = 1;
        assertEquals(Boolean.TRUE, resolver.resolveOfferType());

        hooks.newOfferTypeVarbit = 2;
        hooks.selectedOffer = null;
        assertEquals(Boolean.FALSE, resolver.resolveOfferType());
    }

    @Test
    public void resolveOfferTypeCachesMappedVarbitFromSetupText() {
        TestHooks hooks = new TestHooks();
        hooks.newOfferTypeVarbit = 45;
        hooks.offerContainer = widget("<col=00ff00>Buy offer</col>", false, null, null, null);
        OfferTypeResolver resolver = new OfferTypeResolver(hooks);

        assertEquals(Boolean.TRUE, resolver.resolveOfferType());

        hooks.offerContainer = null;
        hooks.selectedOffer = null;
        hooks.geRoot = null;
        hooks.newOfferTypeVarbit = 45;
        assertEquals(Boolean.TRUE, resolver.resolveOfferType());
    }

    @Test
    public void resolveOfferTypeUsesGeRootTextAsLastFallback() {
        TestHooks hooks = new TestHooks();
        hooks.geRoot = widget(null, false, null, null, null);
        hooks.geRootTexts.add("Sell offer");
        OfferTypeResolver resolver = new OfferTypeResolver(hooks);

        assertEquals(Boolean.FALSE, resolver.resolveOfferType());
    }

    @Test
    public void resolveOfferTypeReturnsNullWhenNoSignalsAvailable() {
        TestHooks hooks = new TestHooks();
        OfferTypeResolver resolver = new OfferTypeResolver(hooks);

        assertNull(resolver.resolveOfferType());
    }

    @Test
    public void resolveOfferTypeFallsBackToLastResolvedWhenSignalsDrop() {
        TestHooks hooks = new TestHooks();
        hooks.newOfferTypeVarbit = 1;
        OfferTypeResolver resolver = new OfferTypeResolver(hooks);

        assertEquals(Boolean.TRUE, resolver.resolveOfferType());

        hooks.newOfferTypeVarbit = 0;
        hooks.offerContainer = null;
        hooks.selectedOffer = null;
        hooks.geRoot = null;
        hooks.geRootTexts.clear();

        assertEquals(Boolean.TRUE, resolver.resolveOfferType());
    }

    private static GrandExchangeOffer offerWithState(GrandExchangeOfferState state) {
        return (GrandExchangeOffer) Proxy.newProxyInstance(
            GrandExchangeOffer.class.getClassLoader(),
            new Class<?>[] {GrandExchangeOffer.class},
            (proxy, method, args) -> {
                if ("getState".equals(method.getName())) {
                    return state;
                }
                return defaultValue(method);
            }
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
            (proxy, method, args) -> {
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

    private static final class TestHooks implements OfferTypeResolver.Hooks {
        private Widget offerContainer;
        private GrandExchangeOffer selectedOffer;
        private int newOfferTypeVarbit;
        private Widget geRoot;
        private final List<String> geRootTexts = new ArrayList<>();

        @Override
        public Widget getOfferContainer() {
            return offerContainer;
        }

        @Override
        public GrandExchangeOffer getSelectedOffer() {
            return selectedOffer;
        }

        @Override
        public int getNewOfferTypeVarbit() {
            return newOfferTypeVarbit;
        }

        @Override
        public Widget getVisibleGeRoot() {
            return geRoot;
        }

        @Override
        public List<String> collectWidgetText(Widget widget) {
            if (widget == geRoot) {
                return geRootTexts;
            }
            return new ArrayList<>();
        }

        @Override
        public String normalizeOfferText(String text) {
            return OfferPreviewWidgetParser.normalizeText(text);
        }
    }
}
