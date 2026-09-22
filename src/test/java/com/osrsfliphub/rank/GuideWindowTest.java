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

import com.google.inject.Guice;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.HashTable;
import net.runelite.api.MenuEntry;
import net.runelite.api.WidgetNode;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.client.callback.ClientThread;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The Merchant guide is opened on the player's own screen, never by clicking a real skill for
 * them, and the server never hears about it: not when it opens, not when it is clicked.
 */
public class GuideWindowTest {
    private static final int SLOT = InterfaceID.ToplevelOsrsStretch.MAINMODAL;

    private int topLevel = InterfaceID.TOPLEVEL_OSRS_STRETCH;
    // The game's own record of which window hangs in which slot.
    private final Map<Long, WidgetNode> slots = new HashMap<>();
    private final List<int[]> opens = new ArrayList<>();
    private final List<WidgetNode> closes = new ArrayList<>();
    private final WidgetNode node = stub(WidgetNode.class, (m, a) -> null);
    private final WidgetNode theGames = stub(WidgetNode.class, (m, a) -> null);
    // Work handed to the client thread, held back while holding is set.
    private final List<Runnable> later = new ArrayList<>();
    private boolean holding;
    private boolean closeFails;
    private SkillTab tab;

    @Before
    public void setUp() {
        GeLifecyclePlugin plugin = new GeLifecyclePlugin();
        plugin.client = stub(Client.class, (m, a) -> {
            switch (m.getName()) {
                case "getTopLevelInterfaceId":
                    return topLevel;
                case "getComponentTable":
                    return stub(HashTable.class, (m2, a2) ->
                        "get".equals(m2.getName()) ? slots.get((long) a2[0]) : null);
                case "openInterface":
                    opens.add(new int[] {(int) a[0], (int) a[1], (int) a[2]});
                    slots.put((long) (int) a[0], node);
                    return node;
                case "closeInterface":
                    if (closeFails) {
                        throw new IllegalStateException("the window would not close");
                    }
                    closes.add((WidgetNode) a[0]);
                    // By identity: these stubs answer equals() with false.
                    slots.values().removeIf(open -> open == a[0]);
                    return null;
                case "getSpriteOverrides":
                    return new HashMap<>();
                default:
                    return null;
            }
        });
        plugin.clientThread = new ClientThread() {
            @Override
            public void invokeLater(Runnable r) {
                if (holding) {
                    later.add(r);
                } else {
                    r.run();
                }
            }
        };
        Access.set(plugin);
        tab = new SkillTab();
    }

    @After
    public void tearDown() {
        Bridge.set(null);
        Access.set(null);
    }

    @Test
    public void itOpensWhereEachLayoutOpensItsOwnWindows() {
        int[][] layouts = {
            {InterfaceID.TOPLEVEL, InterfaceID.Toplevel.MAINMODAL},
            {InterfaceID.TOPLEVEL_OSRS_STRETCH, InterfaceID.ToplevelOsrsStretch.MAINMODAL},
            {InterfaceID.TOPLEVEL_PRE_EOC, InterfaceID.ToplevelPreEoc.MAINMODAL},
        };
        for (int[] layout : layouts) {
            opens.clear();
            topLevel = layout[0];
            tab = new SkillTab();
            open();
            assertEquals(1, opens.size());
            assertArrayEquals(new int[] {layout[1], InterfaceID.SKILL_GUIDE,
                WidgetModalMode.MODAL_NOCLICKTHROUGH}, opens.get(0));
        }
    }

    @Test
    public void aLayoutItDoesNotKnowOpensNothing() {
        topLevel = InterfaceID.TOPLEVEL_DISPLAY;
        open();
        assertTrue(opens.isEmpty());
    }

    @Test
    public void itNeverOpensOverAnotherWindow() {
        slots.put((long) SLOT, theGames);
        open();
        assertTrue(opens.isEmpty());
    }

    @Test
    public void aClickInsideTheGuideNeverReachesTheServer() {
        open();
        assertTrue(click(InterfaceID.SkillGuide.INFO, "Select"));
        assertTrue(closes.isEmpty());
    }

    @Test
    public void closeClosesItHereAndHandsTheWindowBack() {
        open();
        assertTrue(click(InterfaceID.SkillGuide.CLOSE, "Close"));
        assertEquals(1, closes.size());
        assertSame(node, closes.get(0));
        // Gone, so the next guide window is the game's own and its clicks are the game's.
        assertFalse(click(InterfaceID.SkillGuide.INFO, "Select"));
    }

    @Test
    public void aGuideTheGameOpenedIsLeftToTheGame() {
        assertFalse(click(InterfaceID.SkillGuide.CLOSE, "Close"));
        assertTrue(closes.isEmpty());
    }

    @Test
    public void clicksOutsideTheGuideAreLeftAlone() {
        open();
        assertFalse(click(InterfaceID.Inventory.ITEMS, "Use"));
        assertTrue(closes.isEmpty());
    }

    @Test
    public void aRealSkillsGuideClosesOursBeforeTheGameOpensIt() {
        open();
        // Opened over ours, the game's Defence guide came up blank.
        assertFalse(click(InterfaceID.Stats.DEFENCE, "View <col=ff981f>Defence</col> guide"));
        assertEquals(1, closes.size());
        assertSame(node, closes.get(0));
        assertFalse(click(InterfaceID.SkillGuide.INFO, "Select"));
    }

    @Test
    public void anythingElseInTheSkillsTabLeavesOursUp() {
        open();
        assertFalse(click(InterfaceID.Stats.DEFENCE, "Select"));
        assertTrue(closes.isEmpty());
    }

    @Test
    public void clickingMerchantAgainLeavesOursUp() {
        open();
        assertFalse(click(InterfaceID.Stats.UNIVERSE, SkillTab.guideOption()));
        assertTrue(closes.isEmpty());
        assertTrue(click(InterfaceID.SkillGuide.INFO, "Select"));
    }

    @Test
    public void aWindowTheGameHasAlreadyClosedIsForgotten() {
        open();
        // Esc, or logging out.
        slots.clear();
        call("letGo");
        assertTrue(closes.isEmpty());
        assertFalse(click(InterfaceID.SkillGuide.INFO, "Select"));
    }

    @Test
    public void aRealSkillsGuideOpenedInOurPlaceIsLeftStanding() {
        open();
        // The game has hung a window of its own in the slot ours was in.
        slots.put((long) SLOT, theGames);
        call("letGo");
        // That one is the game's to close, not ours.
        assertTrue(closes.isEmpty());
        assertSame(theGames, slots.get((long) SLOT));
        assertFalse(click(InterfaceID.SkillGuide.INFO, "Select"));
    }

    @Test
    public void thePluginHandsEveryClickToTheGuide() {
        open();
        Bridge.set(Guice.createInjector(binder -> binder.bind(SkillTab.class).toInstance(tab)));
        MenuOptionClicked event = event(InterfaceID.SkillGuide.INFO, "Select");
        Access.plugin().onMenuOptionClicked(event);
        assertTrue(event.isConsumed());
    }

    @Test
    public void turningThePluginOffClosesIt() {
        open();
        tab.stop();
        assertEquals(1, closes.size());
    }

    /**
     * The window is opened by number. Should that number stop being the guide, nothing ever
     * shows, and waiting for it for ever left an empty window that lets no click through
     * hanging over the game until the next login.
     */
    @Test
    public void aWindowThatNeverShowsIsLetGo() {
        open();
        for (int tick = 0; tick < SkillTab.GUIDE_WAIT; tick++) {
            dress();
        }
        assertTrue("given its second", closes.isEmpty());
        dress();
        assertEquals(1, closes.size());
        assertSame(node, closes.get(0));
        assertFalse(click(InterfaceID.SkillGuide.INFO, "Select"));
        assertFalse(claiming());

        // The next one gets its full second too, not what was left of the last one's.
        open();
        dress();
        assertEquals(1, closes.size());
        assertTrue(claiming());
    }

    /**
     * A slot already taken is seen at the click itself: nothing is handed on to open later and
     * nothing is claimed, so there is no wait for a window that was never going to come.
     */
    @Test
    public void aTakenSlotIsSeenAtTheClick() {
        holding = true;
        slots.put((long) SLOT, theGames);
        open();
        assertTrue(later.isEmpty());
        assertFalse(claiming());
    }

    /** Between the click and the opening, the game hung a window of its own in the slot. */
    @Test
    public void aWindowHungThereSinceTheClickIsNeverOpenedOver() {
        holding = true;
        open();
        slots.put((long) SLOT, theGames);
        runLater();
        assertTrue(opens.isEmpty());
        assertSame(theGames, slots.get((long) SLOT));
        assertFalse("nothing of ours to wait for", claiming());
    }

    /** Two clicks in the same moment open one window, and it is still ours to dress. */
    @Test
    public void aSecondClickInTheSameMomentOpensNothingMore() {
        holding = true;
        open();
        open();
        runLater();
        assertEquals(1, opens.size());
        assertTrue(claiming());
    }

    /** A close that throws must not leave the window thought to be ours. */
    @Test
    public void aWindowThatWillNotCloseIsForgottenAllTheSame() {
        open();
        closeFails = true;
        call("letGo");
        assertFalse(click(InterfaceID.SkillGuide.INFO, "Select"));
        assertFalse(claiming());
    }

    private void open() {
        call("openGuide");
    }

    private void dress() {
        try {
            Method method = SkillTab.class.getDeclaredMethod("dressGuide", Client.class);
            method.setAccessible(true);
            method.invoke(tab, Access.plugin().client);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private boolean claiming() {
        try {
            java.lang.reflect.Field field = SkillTab.class.getDeclaredField("claiming");
            field.setAccessible(true);
            return field.getBoolean(tab);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private void runLater() {
        List<Runnable> queued = new ArrayList<>(later);
        later.clear();
        queued.forEach(Runnable::run);
    }

    private void call(String name) {
        try {
            Method method = SkillTab.class.getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(tab);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** Whether the click was kept from going any further. */
    private boolean click(int widgetId, String option) {
        MenuOptionClicked event = event(widgetId, option);
        tab.clicked(event);
        return event.isConsumed();
    }

    private static MenuOptionClicked event(int widgetId, String option) {
        Widget widget = stub(Widget.class, (m, a) -> "getId".equals(m.getName()) ? widgetId : null);
        MenuEntry entry = stub(MenuEntry.class, (m, a) -> {
            switch (m.getName()) {
                case "getWidget":
                    return widget;
                case "getOption":
                    return option;
                default:
                    return null;
            }
        });
        return new MenuOptionClicked(entry);
    }

    interface Answer {
        Object answer(Method method, Object[] args);
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, Answer answer) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (proxy, method, args) -> {
                Object value = answer.answer(method, args);
                if (value != null || !method.getReturnType().isPrimitive()) {
                    return value;
                }
                Class<?> r = method.getReturnType();
                return r == boolean.class ? (Object) false : r == long.class ? (Object) 0L
                    : r == void.class ? null : (Object) 0;
            });
    }
}
