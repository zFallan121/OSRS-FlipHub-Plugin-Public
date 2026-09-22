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
import com.google.inject.util.Providers;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The Merchant row, built into a skills tab laid out the way the game cache lays it out, and
 * taken out of it again.
 *
 * <p>The tab: twenty-four squares at x 1, 64 and 127 and y 1, 31 ... 181, all 30 tall except the
 * bottom row -- Construction, Hunter, Sailing -- which sits at 211 and is 32. Each square holds
 * what the game's script puts in it: two halves of stone, the skill's picture and two numbers.
 * The Total plate is a 190 by 19 strip at 241. ({@code gamecache.py iface 320}.)
 */
public class SkillTabTest {
    private static final int[] ROW_Y = {1, 31, 61, 91, 121, 151, 181, 211};

    // The Skills tab's picture in the resizable sidebar, and in fixed mode.
    private static final int RESIZABLE_ICON = 0x00a1_0043;
    private static final int FIXED_ICON = 0x0224_0048;

    // The first id flattened() hands out: one below the last of the four pictures.
    private static final int FIRST_FLAT = -21_484;

    private final FakeGame game = new FakeGame();
    private final List<Runnable> later = new ArrayList<>();
    private boolean holding;
    private volatile boolean showing = true;
    private SkillTab tab;
    private Widget universe;

    @Before
    public void setUp() throws Exception {
        GeLifecyclePlugin plugin = new GeLifecyclePlugin();
        plugin.client = game.client;
        plugin.config = FakeGame.stub(PluginConfig.class, (m, a) ->
            "showMerchantSkill".equals(m.getName()) ? (Object) showing : null);
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
        // The real sprite manager, over the fake client's cache.
        Constructor<SpriteManager> made = SpriteManager.class
            .getDeclaredConstructor(Client.class, ClientThread.class, InfoBoxManager.class);
        made.setAccessible(true);
        SpriteManager sprites = made.newInstance(game.client, null, null);
        Bridge.set(Guice.createInjector(binder -> {
            binder.bind(SpriteManager.class).toInstance(sprites);
            binder.bind(MouseManager.class).toProvider(Providers.of(null));
        }));
        tab = new SkillTab();
        tab.start();
    }

    @After
    public void tearDown() {
        Bridge.set(null);
        Access.set(null);
    }

    @Test
    public void theRowGoesInUnderTheGrid() {
        statsTab(2);
        tab.tick();
        assertSame(universe, get("built"));
        Widget cell = cell();
        assertNotNull("a row is showing", cell);
        assertEquals(1, cell.getRelativeX());
        assertEquals(232, cell.getRelativeY());
        // The plate moved in beside it, from the second square to the right edge of the third.
        Widget plate = game.widgets.get(InterfaceID.Stats.TOTAL);
        assertEquals(64, plate.getRelativeX());
        assertEquals(125, plate.getWidth());
    }

    /**
     * A game update that changes what a square holds -- here Attack has lost its two numbers --
     * makes the build give up part-way. It must give up cleanly, and only once for that tab:
     * each try used to add another row, every tick.
     */
    @Test
    public void aBuildThatFailsIsTriedOnceAndLeavesNoHalfRow() {
        statsTab(0);
        String before = layout();
        tab.tick();
        assertNull(get("built"));
        List<Widget> made = FakeGame.kids(universe);
        assertFalse("it did try", made.isEmpty());
        for (Widget widget : made) {
            assertTrue("left standing: " + widget, widget.isSelfHidden());
        }
        assertEquals("the tab is the game's again", before, layout());

        for (int i = 0; i < 5; i++) {
            tab.tick();
        }
        assertEquals("tried once for this tab", made.size(), FakeGame.kids(universe).size());
        assertEquals(before, layout());
    }

    /** The game reloads the tab with new widgets whenever it likes, and each one gets a try. */
    @Test
    public void aTabTheGameHasBuiltAfreshIsTriedAgain() {
        statsTab(0);
        tab.tick();
        assertNull(get("built"));

        statsTab(2);
        tab.tick();
        assertSame(universe, get("built"));
        assertNotNull(cell());
    }

    /** Off and on again in the settings is the player's way of asking for another go. */
    @Test
    public void turningTheSettingOffAndOnTriesTheSameTabAgain() {
        statsTab(0);
        tab.tick();
        // Whole now, on the same tab object -- as if the game's script had simply been late.
        fill(game.widgets.get(InterfaceID.Stats.ATTACK), 2);
        tab.tick();
        assertNull("still once per tab", get("built"));

        showing = false;
        tab.tick();
        showing = true;
        tab.tick();
        assertSame(universe, get("built"));
        assertNotNull(cell());
    }

    /**
     * A square's children are the game's own array. A gap in it -- a child a script deleted --
     * used to be closed up in place, leaving a second copy of whatever followed, in the game's
     * data, for all twenty-four squares, every tick.
     */
    @Test
    public void readingASquareNeverWritesIntoTheGamesOwnArray() {
        statsTab(2);
        Widget attack = game.widgets.get(InterfaceID.Stats.ATTACK);
        Widget[] own = FakeGame.node(attack).children;
        Widget[] gapped = new Widget[own.length + 1];
        gapped[0] = own[0];
        System.arraycopy(own, 1, gapped, 2, own.length - 1);
        FakeGame.node(attack).children = gapped;
        Widget[] copy = gapped.clone();

        tab.tick();
        tab.tick();
        assertSame(universe, get("built"));
        assertSame(gapped, FakeGame.node(attack).children);
        assertArrayEquals(copy, gapped);
    }

    /**
     * The way out puts every square back where the game had it. It used to write Attack's
     * height to all twenty-four, which left the bottom row two pixels short.
     */
    @Test
    public void theWayOutPutsEverySquareBackAsTheGameHadIt() {
        statsTab(2);
        String before = layout();
        tab.tick();
        assertNotEquals("the squares did move", before, layout());

        showing = false;
        tab.tick();
        assertEquals(before, layout());
        Widget sailing = game.widgets.get(InterfaceID.Stats.SAILING);
        assertEquals(211, sailing.getRelativeY());
        assertEquals(32, sailing.getHeight());
    }

    /**
     * Nothing about the way out may assume the grid is regular. Today only the bottom row's
     * height breaks the pattern; Jagex moved this interface for Sailing and can again, so here
     * the bottom row sits two pixels lower as well, and still goes back exactly.
     */
    @Test
    public void theWayOutAssumesNothingAboutTheGrid() {
        statsTab(2);
        for (int i = 7; i < 24; i += 8) {
            FakeGame.node(game.widgets.get(InterfaceID.Stats.ATTACK + i)).y = 213;
        }
        String before = layout();
        tab.tick();
        showing = false;
        tab.tick();
        assertEquals(before, layout());
    }

    @Test
    public void turningThePluginOffPutsEverySquareBackToo() {
        statsTab(2);
        String before = layout();
        tab.tick();
        tab.stop();
        assertEquals(before, layout());
        assertEquals(261, universe.getHeight());
    }

    /**
     * The settings panel turns the plugin off from its own thread. What the tick owns is left
     * to the client thread to clear, and a tab the row was on gets the row back when the plugin
     * is turned on again -- which it did not, while the tab was still remembered as tried.
     */
    @Test
    public void stopLeavesTheTicksOwnStateToTheClientThread() {
        statsTab(2);
        tab.tick();
        assertSame(universe, get("built"));

        holding = true;
        tab.stop();
        assertSame("left alone until the client thread gets there", universe, get("built"));
        assertSame(universe, get("tried"));
        assertEquals(1, get("shown"));
        assertEquals(0, get("struck"));
        runLater();
        assertNull(get("built"));
        assertNull(get("tried"));
        assertEquals(-1, get("shown"));
        assertEquals(-1, get("struck"));

        holding = false;
        tab.start();
        tab.tick();
        assertSame(universe, get("built"));
        assertNotNull(cell());
    }

    /** Logged out with ours open: the tab is gone, and the window must be let go of with it. */
    @Test
    public void loggingOutWithTheGuideOpenLetsItGo() {
        statsTab(2);
        tab.tick();
        call("openGuide");
        assertEquals(1, game.opens.size());
        Widget title = guideWindow();
        tab.tick();
        assertEquals(FlipLevel.SKILL, title.getText());
        assertNotNull("the wheel is being taken over the ranks", get("guideArea"));

        game.state = GameState.LOGIN_SCREEN;
        game.widgets.clear();
        game.slots.clear();
        tab.tick();
        assertNull("the wheel is free again", get("guideArea"));
        assertNull(get("opened"));
        assertFalse((Boolean) get("claiming"));
    }

    /** Only opening the guide settles a level nobody has looked at. Hovering does not. */
    @Test
    public void onlyTheGuideSettlesAnUnseenLevel() {
        statsTab(2);
        tab.tick();
        tab.flash();
        call("showTip", true);
        tab.tick();
        assertTrue(tab.unseen);

        call("openGuide");
        guideWindow();
        tab.tick();
        assertFalse(tab.unseen);
    }

    /**
     * The sidebar glow hangs off the Skills tab's picture. A relog loads the sidebar afresh and
     * a switch to fixed mode puts the picture somewhere else entirely; either way the old glow
     * is left on a widget that is no longer drawn, and it has to be built again where the
     * picture is now.
     */
    @Test
    public void theSidebarGlowFollowsTheSidebarThroughARelogAndALayoutSwitch() {
        // No skills tab open, so the stone is the half that shows.
        Widget resizable = sidebar(RESIZABLE_ICON);
        tab.flash();
        ticks(3);
        assertTrue(lit(resizable));

        Widget relogged = sidebar(RESIZABLE_ICON);
        ticks(3);
        assertTrue("after a relog", lit(relogged));

        game.widgets.remove(RESIZABLE_ICON);
        Widget fixed = sidebar(FIXED_ICON);
        ticks(3);
        assertTrue("after a switch to fixed mode", lit(fixed));
    }

    /** A glow built again for a new shape does not leave the old one lit beside it. */
    @Test
    public void aChangeOfShapePutsTheOldGlowOut() {
        Widget container = sidebar(RESIZABLE_ICON);
        Widget stone = game.add(0x00a1_003c, WidgetType.GRAPHIC, 96, 0, 33, 36, container);
        FakeGame.node(stone).sprite = 1030;
        tab.flash();
        ticks(3);
        FakeGame.node(stone).sprite = 1031;
        ticks(3);
        List<Widget> glows = FakeGame.kids(container);
        assertEquals(2, glows.size());
        assertTrue("the old one is out", glows.get(0).isSelfHidden());
        assertFalse(glows.get(1).isSelfHidden());
    }

    /** Every picture this put in the game's sprite table comes out again, the glow's too. */
    @Test
    public void stopTakesOutEveryPictureItPutInTheTable() {
        sidebar(RESIZABLE_ICON);
        tab.flash();
        ticks(3);
        assertTrue(game.overrides.containsKey(-21_480));
        assertTrue(game.overrides.containsKey(FIRST_FLAT));

        tab.stop();
        assertTrue("still in the table: " + game.overrides.keySet(), game.overrides.isEmpty());

        // Back on, the flattened shape goes back in, rather than being taken as still there.
        tab.start();
        tab.flash();
        ticks(3);
        assertTrue(game.overrides.containsKey(FIRST_FLAT));
    }

    // ---- the tab -------------------------------------------------------------------------

    /** The skills tab as the game cache lays it out; Attack holds {@code attackNumbers} numbers. */
    private void statsTab(int attackNumbers) {
        universe = game.add(InterfaceID.Stats.UNIVERSE, WidgetType.LAYER, 0, 0, 190, 261, null);
        for (int i = 0; i < 24; i++) {
            int row = i % 8;
            Widget square = game.add(InterfaceID.Stats.ATTACK + i, WidgetType.LAYER,
                1 + i / 8 * 63, ROW_Y[row], 62, row == 7 ? 32 : 30, universe);
            fill(square, i == 0 ? attackNumbers : 2);
        }
        Widget plate = game.add(InterfaceID.Stats.TOTAL, WidgetType.LAYER, 0, 241, 190, 19, universe);
        // x, sprite and x mode of each of the plate's pictures, as the cache has them. The last
        // is the right end, placed from the right.
        int[][] pieces = {{72, 191, 0}, {108, 191, 0}, {144, 191, 0}, {36, 191, 0}, {1, 189, 0}, {1, 190, 2}};
        Widget[] statics = new Widget[pieces.length + 1];
        for (int p = 0; p < pieces.length; p++) {
            statics[p] = game.add(InterfaceID.Stats.TOTAL_GRAPHIC0 + p, WidgetType.GRAPHIC,
                pieces[p][0], 0, 36, 36, plate);
            FakeGame.node(statics[p]).sprite = pieces[p][1];
            FakeGame.node(statics[p]).xMode = pieces[p][2];
        }
        statics[pieces.length] = game.add(InterfaceID.Stats.TOTAL_TEXT6, WidgetType.TEXT, 2, 2, 186, 17, plate);
        FakeGame.node(plate).statics = statics;
    }

    /** A square's contents: two halves of stone, the skill's picture, and its numbers. */
    private static void fill(Widget square, int numbers) {
        FakeGame.node(square).children = null;
        FakeGame.node(FakeGame.child(square, WidgetType.GRAPHIC, 0, 0, 36, 36)).sprite = 174;
        FakeGame.node(FakeGame.child(square, WidgetType.GRAPHIC, 26, 0, 36, 36)).sprite = 175;
        FakeGame.node(FakeGame.child(square, WidgetType.GRAPHIC, 3, 3, 25, 25)).sprite = 197;
        for (int n = 0; n < numbers; n++) {
            FakeGame.child(square, WidgetType.TEXT, 31, 3 + n * 13, 30, 12);
        }
    }

    /** Every square's place and height and where its contents sit, and the plate's, as text. */
    private String layout() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 24; i++) {
            Widget square = game.widgets.get(InterfaceID.Stats.ATTACK + i);
            out.append(i).append(": ").append(square.getRelativeY()).append('/').append(square.getHeight());
            for (Widget kid : FakeGame.kids(square)) {
                out.append(' ').append(kid.getRelativeY());
            }
            out.append('\n');
        }
        Widget plate = game.widgets.get(InterfaceID.Stats.TOTAL);
        out.append("plate ").append(plate.getRelativeX()).append(',').append(plate.getRelativeY())
            .append(' ').append(plate.getWidth());
        for (Widget piece : FakeGame.node(plate).statics) {
            out.append(' ').append(piece.getRelativeX());
        }
        return out.append(" tab ").append(universe.getHeight()).toString();
    }

    /** The row, if one is showing: a layer of ours in the tab that is not hidden. */
    private Widget cell() {
        for (Widget kid : FakeGame.kids(universe)) {
            if (kid.getType() == WidgetType.LAYER && !kid.isSelfHidden() && kid.getRelativeY() == 232) {
                return kid;
            }
        }
        return null;
    }

    /** The guide window, as the game's load script leaves it. Returns its title. */
    private Widget guideWindow() {
        Widget window = game.add(InterfaceID.SkillGuide.WINDOW, WidgetType.LAYER, 0, 0, 488, 332, null);
        Widget title = game.add(InterfaceID.SkillGuide.TITLE, WidgetType.TEXT, 0, 8, 488, 20, window);
        title.setText("Attack");
        game.add(InterfaceID.SkillGuide.DESCRIPTION, WidgetType.TEXT, 0, 30, 488, 20, window);
        game.add(InterfaceID.SkillGuide.INFO, WidgetType.LAYER, 21, 76, 295, 239, window);
        game.add(InterfaceID.SkillGuide.SCROLLBAR, WidgetType.LAYER, 316, 76, 16, 239, window);
        return title;
    }

    /** A sidebar with the Skills tab's picture in it, under a container of its own. */
    private Widget sidebar(int iconId) {
        Widget container = FakeGame.make(iconId & 0xFFFF_0000, WidgetType.LAYER, 0, 0, 240, 40, null);
        game.add(iconId, WidgetType.GRAPHIC, 100, 3, 25, 25, container);
        return container;
    }

    /** Whether a glow of ours is showing in this container. */
    private static boolean lit(Widget container) {
        for (Widget kid : FakeGame.kids(container)) {
            if (kid.getType() == WidgetType.LAYER && !kid.isSelfHidden()) {
                return true;
            }
        }
        return false;
    }

    private void ticks(int count) {
        for (int i = 0; i < count; i++) {
            tab.tick();
        }
    }

    private void runLater() {
        List<Runnable> queued = new ArrayList<>(later);
        later.clear();
        queued.forEach(Runnable::run);
    }

    private Object get(String name) {
        try {
            Field field = SkillTab.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(tab);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private Object call(String name, Object... args) {
        for (Method method : SkillTab.class.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                method.setAccessible(true);
                try {
                    return method.invoke(tab, args);
                } catch (InvocationTargetException e) {
                    throw new AssertionError(e.getCause());
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            }
        }
        throw new AssertionError("SkillTab has no " + name + " taking " + Arrays.toString(args));
    }
}
