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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.FontID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

public class ChatboxSuggestionWidgetFactoryServiceTest {
    private static final Map<Widget, WidgetState> STATES = new IdentityHashMap<>();

    @Test
    public void ensurePriceSuggestionWidgetCreatesAndWiresSelectAction() {
        ChatboxSuggestionWidgetFactoryService service = new ChatboxSuggestionWidgetFactoryService();
        WidgetState containerState = createWidgetState(100, null, 0, 0);

        Widget widget = service.ensurePriceSuggestionWidget(containerState.proxy, null);
        WidgetState state = STATES.get(widget);

        assertNotNull(state);
        assertEquals(1, containerState.createChildCalls);
        assertEquals(WidgetType.TEXT, state.type);
        assertEquals("FlipHub Current Price", state.name);
        assertEquals(WidgetPositionMode.ABSOLUTE_LEFT, state.xPositionMode);
        assertEquals(WidgetSizeMode.MINUS, state.widthMode);
        assertEquals(2, state.originalY);
        assertEquals(FontID.VERDANA_11_BOLD, state.fontId);
        assertNotNull(state.onOpListener);
    }

    @Test
    public void ensureLimitSuggestionWidgetReusesAttachedWidget() {
        ChatboxSuggestionWidgetFactoryService service = new ChatboxSuggestionWidgetFactoryService();
        WidgetState containerState = createWidgetState(200, null, 0, 0);
        Widget existing = containerState.proxy.createChild(-1, WidgetType.TEXT);
        WidgetState existingState = STATES.get(existing);
        existingState.name = "FlipHub Remaining Limit";
        containerState.createChildCalls = 0;

        Widget ensured = service.ensureLimitSuggestionWidget(containerState.proxy, existing);

        assertSame(existing, ensured);
        assertEquals(0, containerState.createChildCalls);
        assertEquals(WidgetPositionMode.ABSOLUTE_LEFT, existingState.xPositionMode);
        assertEquals(10, existingState.originalX);
        assertEquals(16, existingState.originalWidth);
        assertEquals(WidgetSizeMode.ABSOLUTE, existingState.widthMode);
        assertEquals(WidgetTextAlignment.LEFT, existingState.xTextAlignment);
        assertEquals(WidgetTextAlignment.CENTER, existingState.yTextAlignment);
    }

    @Test
    public void ensureAffordableSuggestionWidgetCreatesRightAlignedWidget() {
        ChatboxSuggestionWidgetFactoryService service = new ChatboxSuggestionWidgetFactoryService();
        WidgetState containerState = createWidgetState(300, null, 0, 0);

        Widget widget = service.ensureAffordableLimitSuggestionWidget(containerState.proxy, null);
        WidgetState state = STATES.get(widget);

        assertNotNull(state);
        assertEquals(1, containerState.createChildCalls);
        assertEquals("FlipHub Affordable Limit", state.name);
        assertEquals(WidgetPositionMode.ABSOLUTE_RIGHT, state.xPositionMode);
        assertEquals(8, state.originalX);
        assertEquals(16, state.originalWidth);
        assertEquals(WidgetTextAlignment.RIGHT, state.xTextAlignment);
        assertEquals(WidgetTextAlignment.CENTER, state.yTextAlignment);
        assertNotNull(state.onOpListener);
    }

    private static WidgetState createWidgetState(int id, Widget parent, int parentId, int type) {
        WidgetState state = new WidgetState();
        state.id = id;
        state.parent = parent;
        state.parentId = parentId;
        state.type = type;
        Widget proxy = (Widget) Proxy.newProxyInstance(
            Widget.class.getClassLoader(),
            new Class<?>[] {Widget.class},
            (p, method, args) -> handleWidgetMethod(state, p, method, args)
        );
        state.proxy = proxy;
        STATES.put(proxy, state);
        return state;
    }

    private static Object handleWidgetMethod(WidgetState state, Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "createChild":
                int childType = args != null && args.length > 1 ? (Integer) args[1] : 0;
                WidgetState childState = createWidgetState(state.id * 100 + state.children.size() + 1, (Widget) proxy, state.id, childType);
                state.children.add(childState.proxy);
                state.createChildCalls++;
                return childState.proxy;
            case "getChildren":
                return state.children.isEmpty() ? null : state.children.toArray(new Widget[0]);
            case "getDynamicChildren":
                return null;
            case "getNestedChildren":
                return null;
            case "getId":
                return state.id;
            case "getParent":
                return state.parent;
            case "getParentId":
                return state.parentId;
            case "getType":
                return state.type;
            case "getName":
                return state.name;
            case "setName":
                state.name = args != null && args.length > 0 && args[0] != null ? String.valueOf(args[0]) : null;
                return null;
            case "setTextColor":
                state.textColor = args != null && args.length > 0 ? (Integer) args[0] : 0;
                return null;
            case "setTextShadowed":
                state.textShadowed = args != null && args.length > 0 && Boolean.TRUE.equals(args[0]);
                return null;
            case "setFontId":
                state.fontId = args != null && args.length > 0 ? (Integer) args[0] : 0;
                return null;
            case "setXPositionMode":
                state.xPositionMode = args != null && args.length > 0 ? (Integer) args[0] : 0;
                return null;
            case "setYPositionMode":
                state.yPositionMode = args != null && args.length > 0 ? (Integer) args[0] : 0;
                return null;
            case "setOriginalX":
                state.originalX = args != null && args.length > 0 ? (Integer) args[0] : 0;
                return null;
            case "setOriginalY":
                state.originalY = args != null && args.length > 0 ? (Integer) args[0] : 0;
                return null;
            case "setOriginalWidth":
                state.originalWidth = args != null && args.length > 0 ? (Integer) args[0] : 0;
                return null;
            case "setOriginalHeight":
                state.originalHeight = args != null && args.length > 0 ? (Integer) args[0] : 0;
                return null;
            case "setWidthMode":
                state.widthMode = args != null && args.length > 0 ? (Integer) args[0] : 0;
                return null;
            case "setXTextAlignment":
                state.xTextAlignment = args != null && args.length > 0 ? (Integer) args[0] : 0;
                return null;
            case "setYTextAlignment":
                state.yTextAlignment = args != null && args.length > 0 ? (Integer) args[0] : 0;
                return null;
            case "setAction":
                return null;
            case "setOnOpListener":
                state.onOpListener = firstVarArg(args);
                return null;
            case "setOnMouseRepeatListener":
                state.onMouseRepeatListener = firstVarArg(args);
                return null;
            case "setOnMouseLeaveListener":
                state.onMouseLeaveListener = firstVarArg(args);
                return null;
            case "setHasListener":
                state.hasListener = args != null && args.length > 0 && Boolean.TRUE.equals(args[0]);
                return null;
            case "revalidate":
                state.revalidateCalls++;
                return null;
            case "isHidden":
                return state.hidden;
            case "setHidden":
                state.hidden = args != null && args.length > 0 && Boolean.TRUE.equals(args[0]);
                return null;
            default:
                return defaultValue(method);
        }
    }

    private static Object firstVarArg(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        Object first = args[0];
        if (first instanceof Object[]) {
            Object[] values = (Object[]) first;
            return values.length > 0 ? values[0] : null;
        }
        return first;
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
        private int id;
        private Widget proxy;
        private Widget parent;
        private int parentId;
        private int type;
        private String name;
        private boolean hidden;
        private int textColor;
        private boolean textShadowed;
        private int fontId;
        private int xPositionMode;
        private int yPositionMode;
        private int originalX;
        private int originalY;
        private int originalWidth;
        private int originalHeight;
        private int widthMode;
        private int xTextAlignment;
        private int yTextAlignment;
        private Object onOpListener;
        private Object onMouseRepeatListener;
        private Object onMouseLeaveListener;
        private boolean hasListener;
        private int revalidateCalls;
        private int createChildCalls;
        private final List<Widget> children = new ArrayList<>();
    }
}
