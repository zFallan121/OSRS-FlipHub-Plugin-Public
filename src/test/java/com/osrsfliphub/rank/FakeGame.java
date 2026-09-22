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

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.HashTable;
import net.runelite.api.IndexDataBase;
import net.runelite.api.NodeCache;
import net.runelite.api.SpritePixels;
import net.runelite.api.WidgetNode;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/**
 * Enough of the game's client and widgets for the skills tab code to run against, headless.
 *
 * <p>Every widget keeps what the code can change on it -- its place, its size, whether it is
 * hidden, its sprite, its text and its children -- so a test can read back exactly what was done
 * to the tab. A widget's dynamic children are one array, handed out as it is, the way the game
 * hands out its own: a write into it shows.
 *
 * <p>Nothing here is drawn and nothing is sent anywhere. The sprite table, the window slots and
 * the chatbox are plain collections the test can look into.
 */
final class FakeGame {
    final Map<Integer, Widget> widgets = new HashMap<>();
    final Map<Integer, SpritePixels> overrides = new HashMap<>();
    final Map<Long, WidgetNode> slots = new HashMap<>();
    final List<int[]> opens = new ArrayList<>();
    final List<WidgetNode> closes = new ArrayList<>();
    final List<String> chat = new ArrayList<>();
    volatile GameState state = GameState.LOGGED_IN;
    int topLevel = InterfaceID.TOPLEVEL_OSRS_STRETCH;
    boolean closeFails;
    final Client client = stub(Client.class, this::client);

    // Any sprite the code asks the game's cache for comes back as this: a tab-sized shape, solid.
    private final BufferedImage sprite = solid(33, 36);

    private Object client(Method m, Object[] a) {
        switch (m.getName()) {
            case "getWidget":
                if (a.length == 1 && a[0] instanceof Integer) {
                    return widgets.get((Integer) a[0]);
                }
                return a.length == 2 ? widgets.get((int) a[0] << 16 | (int) a[1]) : null;
            case "getSpriteOverrides":
                return overrides;
            case "getWidgetSpriteCache":
                return stub(NodeCache.class, (m2, a2) -> null);
            case "getComponentTable":
                return stub(HashTable.class, (m2, a2) -> "get".equals(m2.getName()) ? slots.get((Long) a2[0]) : null);
            case "openInterface": {
                WidgetNode node = stub(WidgetNode.class, (m2, a2) -> null);
                opens.add(new int[] {(int) a[0], (int) a[1], (int) a[2]});
                slots.put((long) (int) a[0], node);
                return node;
            }
            case "closeInterface":
                if (closeFails) {
                    throw new IllegalStateException("the window would not close");
                }
                closes.add((WidgetNode) a[0]);
                slots.values().remove(a[0]);
                return null;
            case "getTopLevelInterfaceId":
                return topLevel;
            case "getGameState":
                return state;
            case "isClientThread":
                return true;
            case "addChatMessage":
                synchronized (chat) {
                    chat.add((String) a[2]);
                }
                return null;
            case "getIndexSprites":
                return stub(IndexDataBase.class, (m2, a2) -> null);
            case "getSprites":
                return new SpritePixels[] {stub(SpritePixels.class, (m2, a2) ->
                    "toBufferedImage".equals(m2.getName()) ? sprite : null)};
            default:
                return null;
        }
    }

    /** One of the game's own widgets, filed under its id so the client finds it. */
    Widget add(int id, int type, int x, int y, int width, int height, Widget parent) {
        Widget widget = make(id, type, x, y, width, height, parent);
        widgets.put(id, widget);
        return widget;
    }

    /** A dynamic child, as a game script would have made it. */
    static Widget child(Widget parent, int type, int x, int y, int width, int height) {
        Widget child = node(parent).create(type);
        Node node = node(child);
        node.x = x;
        node.y = y;
        node.width = width;
        node.height = height;
        return child;
    }

    static Widget make(int id, int type, int x, int y, int width, int height, Widget parent) {
        Node node = new Node();
        node.id = id;
        node.type = type;
        node.x = x;
        node.y = y;
        node.width = width;
        node.height = height;
        node.parent = parent;
        node.self = (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(),
            new Class<?>[] {Widget.class}, node);
        return node.self;
    }

    static Node node(Widget widget) {
        return (Node) Proxy.getInvocationHandler(widget);
    }

    /** Every dynamic child of a widget that is still in its array, in order. */
    static List<Widget> kids(Widget widget) {
        List<Widget> out = new ArrayList<>();
        Widget[] children = node(widget).children;
        if (children != null) {
            for (Widget child : children) {
                if (child != null) {
                    out.add(child);
                }
            }
        }
        return out;
    }

    /** The state behind one widget. */
    static final class Node implements InvocationHandler {
        int id;
        int type;
        int x;
        int y;
        int width;
        int height;
        int xMode;
        int sprite = -1;
        int opacity;
        int index = -1;
        int scrollY;
        int scrollHeight;
        boolean hidden;
        String text = "";
        Widget self;
        Widget parent;
        Widget[] children;
        Widget[] statics;
        final Map<String, Object[]> listeners = new HashMap<>();

        @Override
        public Object invoke(Object proxy, Method m, Object[] a) {
            switch (m.getName()) {
                case "getId":
                    return id;
                case "getType":
                    return type;
                case "getRelativeX":
                case "getOriginalX":
                    return x;
                case "getRelativeY":
                case "getOriginalY":
                    return y;
                case "getWidth":
                case "getOriginalWidth":
                    return width;
                case "getHeight":
                case "getOriginalHeight":
                    return height;
                case "setOriginalX":
                    x = (int) a[0];
                    return self;
                case "setOriginalY":
                    y = (int) a[0];
                    return self;
                case "setOriginalWidth":
                    width = (int) a[0];
                    return self;
                case "setOriginalHeight":
                    height = (int) a[0];
                    return self;
                case "getXPositionMode":
                    return xMode;
                case "getSpriteId":
                    return sprite;
                case "setSpriteId":
                    sprite = (int) a[0];
                    return self;
                case "getOpacity":
                    return opacity;
                case "setOpacity":
                    opacity = (int) a[0];
                    return self;
                case "getText":
                    return text;
                case "setText":
                    text = (String) a[0];
                    return self;
                case "isSelfHidden":
                    return hidden;
                case "isHidden":
                    return hidden || parent != null && parent.isHidden();
                case "setHidden":
                    hidden = (boolean) a[0];
                    return self;
                case "getParent":
                    return parent;
                case "getParentId":
                    return parent == null ? -1 : parent.getId();
                case "getChildren":
                    return children;
                case "getStaticChildren":
                    return statics;
                case "getChild": {
                    int at = (int) a[0];
                    return children != null && at >= 0 && at < children.length ? children[at] : null;
                }
                case "getIndex":
                    return index;
                case "createChild":
                    return create(a.length == 2 ? (int) a[1] : (int) a[0]);
                case "deleteAllChildren":
                    children = null;
                    return null;
                case "getBounds":
                    return new Rectangle(x, y, width, height);
                case "getScrollY":
                    return scrollY;
                case "setScrollY":
                    scrollY = (int) a[0];
                    return self;
                case "getScrollHeight":
                    return scrollHeight;
                case "setScrollHeight":
                    scrollHeight = (int) a[0];
                    return self;
                case "equals":
                    return proxy == a[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "toString":
                    return "widget " + (id >>> 16) + ":" + (id & 0xFFFF) + (index >= 0 ? "[" + index + "]" : "");
                default:
                    if (m.getName().startsWith("setOn")) {
                        listeners.put(m.getName(), (Object[]) a[0]);
                    }
                    return nothing(m, self);
            }
        }

        Widget create(int childType) {
            Widget child = make(id, childType, 0, 0, 0, 0, self);
            int at = children == null ? 0 : children.length;
            children = children == null ? new Widget[1] : Arrays.copyOf(children, at + 1);
            children[at] = child;
            node(child).index = at;
            return child;
        }
    }

    interface Answer {
        Object answer(Method method, Object[] args);
    }

    /** An interface answering as told, and nothing (false, zero, null, itself) for the rest. */
    @SuppressWarnings("unchecked")
    static <T> T stub(Class<T> type, Answer answer) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (proxy, method, args) -> {
                if ("equals".equals(method.getName()) && args != null && args.length == 1) {
                    return proxy == args[0];
                }
                if ("hashCode".equals(method.getName()) && (args == null || args.length == 0)) {
                    return System.identityHashCode(proxy);
                }
                Object value = answer.answer(method, args == null ? new Object[0] : args);
                return value != null ? value : nothing(method, proxy);
            });
    }

    private static Object nothing(Method method, Object self) {
        Class<?> r = method.getReturnType();
        if (r == boolean.class) {
            return false;
        }
        if (r == long.class) {
            return 0L;
        }
        if (r == double.class) {
            return 0d;
        }
        if (r == float.class) {
            return 0f;
        }
        if (r == int.class || r == short.class || r == byte.class || r == char.class) {
            return r == int.class ? (Object) 0 : r == short.class ? (Object) (short) 0
                : r == byte.class ? (Object) (byte) 0 : (Object) (char) 0;
        }
        return r.isInstance(self) ? self : null;
    }

    private static BufferedImage solid(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, 0xFF3E3529);
            }
        }
        return image;
    }
}
