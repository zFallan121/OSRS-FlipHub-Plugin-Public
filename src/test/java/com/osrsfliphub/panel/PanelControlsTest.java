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

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Every control on the side panel, pressed, and what it tells the plugin.
 *
 * <p>The panel's pictures are pinned by the headless renders, but a picture never presses a
 * button. The controls reach the plugin through the panel's state, and the plumbing between
 * them has been rebuilt more than once; this is what says it still arrives, and with what.
 */
public class PanelControlsTest {
    private final List<String> heard = new ArrayList<>();
    private Panel panel;

    @Before
    public void build() throws Exception {
        PanelListener listener = (PanelListener) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[] {PanelListener.class}, (p, m, a) -> {
                // By constant name: these enums print their display labels.
                List<Object> args = new ArrayList<>();
                for (Object arg : a == null ? new Object[0] : a) {
                    args.add(arg instanceof Enum ? ((Enum<?>) arg).name() : arg);
                }
                heard.add(m.getName() + (a == null ? "" : args.toString()));
                return null;
            });
        PanelBookmarkStore bookmarks = new PanelBookmarkStore() {
            @Override
            public boolean isBookmarked(int itemId) {
                return false;
            }

            @Override
            public void toggleBookmark(int itemId) {
            }
        };
        PanelHiddenItemStore hidden = new PanelHiddenItemStore() {
            @Override
            public boolean isHidden(int itemId) {
                return false;
            }

            @Override
            public void hideItem(int itemId) {
            }
        };
        PluginConfig config = (PluginConfig) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[] {PluginConfig.class}, (p, m, a) -> {
                Class<?> r = m.getReturnType();
                return r == boolean.class ? (Object) false : r == int.class ? (Object) 0 : null;
            });
        onSwing(() -> panel = new Panel(null, listener, bookmarks, hidden, config));
        heard.clear();
    }

    @After
    public void dispose() throws Exception {
        onSwing(panel::dispose);
    }

    @Test
    public void theBookmarkStarTurnsTheFilterOnAndOff() throws Exception {
        onSwing(() -> press("bookmarkFilterButton"));
        onSwing(() -> press("bookmarkFilterButton"));

        assertEquals(Arrays.asList("onBookmarkFilterChanged[true]", "onBookmarkFilterChanged[false]"), heard);
    }

    @Test
    public void thePagerAsksForTheNeighbouringPagesOnlyWhenTheyExist() throws Exception {
        panel.setItems(new ArrayList<>(), 2, 3, 1L, null);
        onSwing(() -> {
        });
        assertEquals("Page 2 of 3", this.<JLabel>field("pageLabel").getText());
        onSwing(() -> press("prevButton"));
        onSwing(() -> press("nextButton"));
        assertEquals(Arrays.asList("onPageChanged[1]", "onPageChanged[3]"), heard);

        heard.clear();
        panel.setItems(new ArrayList<>(), 1, 1, 1L, null);
        onSwing(() -> {
        });
        assertFalse(this.<AbstractButton>field("prevButton").isEnabled());
        assertFalse(this.<AbstractButton>field("nextButton").isEnabled());
        // Disabled buttons do not fire, so ask the state directly through a forced press.
        onSwing(() -> {
            this.<AbstractButton>field("prevButton").setEnabled(true);
            this.<AbstractButton>field("nextButton").setEnabled(true);
            press("prevButton");
            press("nextButton");
        });
        assertEquals("page 1 of 1 has no neighbours", new ArrayList<>(), heard);
    }

    @Test
    public void theActivitySortSaysWhichWayRound() throws Exception {
        onSwing(() -> this.<JComboBox<StatsItemSort>>field("itemSortCombo").setSelectedItem(StatsItemSort.PROFIT));
        onSwing(() -> press("itemSortDirectionButton"));
        onSwing(() -> press("itemSortDirectionButton"));

        assertEquals(Arrays.asList(
            "onItemSortChanged[PROFIT, false]",
            "onItemSortChanged[PROFIT, true]",
            "onItemSortChanged[PROFIT, false]"), heard);
    }

    @Test
    public void openingTheProfileTabAsksForItsRange() throws Exception {
        onSwing(() -> press("statsTab"));
        onSwing(() -> press("linkTab"));
        onSwing(() -> press("flippingTab"));

        assertEquals(Arrays.asList("onStatsRangeChanged[SESSION]"), heard);
        assertTrue(this.<AbstractButton>field("flippingTab").isSelected());
    }

    @Test
    public void theProfileControlsReachThePlugin() throws Exception {
        onSwing(() -> this.<JComboBox<StatsRange>>field("statsRangeCombo").setSelectedItem(StatsRange.ALL_TIME));
        onSwing(() -> this.<JComboBox<StatsItemSort>>field("statsSortCombo").setSelectedItem(StatsItemSort.PROFIT));

        assertEquals(Arrays.asList("onStatsRangeChanged[ALL_TIME]", "onStatsSortChanged[PROFIT]"), heard);
    }

    private void press(String name) {
        this.<AbstractButton>field(name).doClick(0);
    }

    @SuppressWarnings("unchecked")
    private <T> T field(String name) {
        try {
            Field field = Panel.class.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(panel);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("the panel has no " + name, e);
        }
    }

    private static void onSwing(Runnable work) throws Exception {
        SwingUtilities.invokeAndWait(work);
        // Setters hop onto the Swing thread themselves; let what they queued run too.
        SwingUtilities.invokeAndWait(() -> {
        });
    }
}
