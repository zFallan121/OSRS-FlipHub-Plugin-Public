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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The recording screen builds and opens without a plugin behind it.
 *
 * <p>Thin on purpose. What the screen works out is worked out by {@link RecipeFlipLedger}, which
 * is tested directly; what this covers is the half that only fails when it is drawn - a Swing
 * layout that throws on assembly, or a service call made before its null guard. The panel is
 * built once at construction and never rebuilt, so a break here is a break that ships.
 */
public class RecipeRecorderTest {
    @Test
    public void theScreenBuildsAndOpensWithNoPluginBehindIt() {
        RecipeRecorder recorder = new RecipeRecorder(new UiStyler(), new PanelValueFormat(), () -> {
        });

        assertNotNull(recorder.view());

        // Every service comes through Bridge, which hands back null outside the plugin. Opening
        // has to survive that, because it is also what a player sees before they have logged in.
        recorder.open();

        assertTrue(recorder.view().getViewport().getView().getPreferredSize().height > 0);
    }

    /**
     * Every stack of rows on the screen lines its rows up the same way.
     *
     * <p>A stack laid out this way places its children on their alignment points, so a row that
     * asks for the left edge standing next to a block that asks to be centred cannot have both.
     * The stack widens to hold the two demands at once and gives each child a fraction of
     * itself: rows come out half width, shoved into the right of the panel, and a row drawn
     * left-and-right in that space puts its two ends on top of one another. That is what shipped
     * - a heading reading "WHAT WENT IN4PICKED", a search field at half length, a pager with its
     * Older button wrapped off the bottom - and none of it is visible in the source of any one
     * row, only in what the rows around it asked for. So it is asserted over the whole screen.
     */
    @Test
    public void everyStackOfRowsAgreesOnHowItsRowsLineUp() {
        RecipeRecorder recorder = new RecipeRecorder(new UiStyler(), new PanelValueFormat(), () -> {
        });
        recorder.open();

        List<String> disagreements = new ArrayList<>();
        checkColumns((Container) recorder.view().getViewport().getView(), disagreements);

        assertEquals(disagreements.toString(), 0, disagreements.size());
    }

    /**
     * Laid out at the width of a sidebar, every row gets the whole of it.
     *
     * <p>The other half of the same bug, asserted on the outcome rather than the cause. Two
     * things go wrong when a stack cannot agree how to line its rows up, and neither of them
     * throws: a row is handed part of the width and shunted off the left edge, and a row drawn
     * with one thing at each end, given less width than the two of them need, shrinks neither -
     * it draws them both at full size from opposite edges and they meet in the middle.
     */
    @Test
    public void everyRowGetsTheWholeWidthOfWhatItSitsIn() throws Exception {
        List<String> collisions = new ArrayList<>();

        // On the event thread, and not because Swing asks it of every caller. Opening the screen
        // leaves a revalidate queued, and laying the same tree out from this thread takes the two
        // locks involved in the opposite order from the thread working through that queue - which
        // is a deadlock, and was one: ten minutes of a test run going nowhere.
        SwingUtilities.invokeAndWait(() -> {
            RecipeRecorder recorder =
                new RecipeRecorder(new UiStyler(), new PanelValueFormat(), () -> {
                });
            recorder.open();

            Container screen = (Container) recorder.view().getViewport().getView();
            // With no trades behind it the screen shows one line of text and hides the rest, so
            // the rows that matter would be measured at nothing. Shown, they are measured at the
            // width a player sees them at.
            showAll(screen);
            screen.setSize(SIDEBAR_WIDTH, screen.getPreferredSize().height);
            layOut(screen);
            checkRows(screen, collisions);
        });

        assertEquals(collisions.toString(), 0, collisions.size());
    }

    /** The narrow end of what RuneLite gives a sidebar panel, less the room a card takes. */
    private static final int SIDEBAR_WIDTH = 225;

    private static void showAll(Container container) {
        for (Component child : container.getComponents()) {
            child.setVisible(true);
            if (child instanceof Container) {
                showAll((Container) child);
            }
        }
    }

    /** {@code validate()} does nothing to a panel with no window behind it, so lay it out by hand. */
    private static void layOut(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container) {
                layOut((Container) child);
            }
        }
    }

    private static void checkRows(Container container, List<String> collisions) {
        if (container.getLayout() instanceof BoxLayout) {
            for (Component child : container.getComponents()) {
                if (child.getX() != 0 || child.getWidth() != container.getWidth()) {
                    collisions.add(name(container) + " squashed " + name(child) + " into "
                        + child.getBounds() + " of " + container.getWidth());
                }
            }
        }
        if (container.getLayout() instanceof BorderLayout) {
            BorderLayout layout = (BorderLayout) container.getLayout();
            Component west = layout.getLayoutComponent(BorderLayout.WEST);
            Component east = layout.getLayoutComponent(BorderLayout.EAST);
            if (west != null && east != null && west.getX() + west.getWidth() > east.getX()) {
                collisions.add(name(container) + " ends overlap: " + west.getBounds()
                    + " into " + east.getBounds() + " in width " + container.getWidth());
            }
        }
        for (Component child : container.getComponents()) {
            if (child instanceof Container) {
                checkRows((Container) child, collisions);
            }
        }
    }

    private static void checkColumns(Container container, List<String> disagreements) {
        if (container.getLayout() instanceof BoxLayout) {
            Float agreed = null;
            for (Component child : container.getComponents()) {
                // A strut has no width, so it takes no side and cannot pull the stack apart.
                if (child.getPreferredSize().width == 0) {
                    continue;
                }
                float alignment = child.getAlignmentX();
                if (agreed == null) {
                    agreed = alignment;
                } else if (agreed.floatValue() != alignment) {
                    disagreements.add(name(container) + " has rows at " + agreed
                        + " and at " + alignment + " (" + name(child) + ")");
                }
            }
        }
        for (Component child : container.getComponents()) {
            if (child instanceof Container) {
                checkColumns((Container) child, disagreements);
            }
        }
    }

    private static String name(Component component) {
        String text = component instanceof JComponent
            ? String.valueOf(((JComponent) component).getToolTipText())
            : "";
        return component.getClass().getSimpleName() + "[" + text + "]";
    }

    private static final int BLADE = 11690;
    private static final int HILT = 11810;
    private static final int GODSWORD = 11802;

    private static Delta trade(long tsMs, int slot, int itemId, boolean isBuy, int qty, String event) {
        return new Delta(tsMs, slot, itemId, isBuy, qty, qty * 1000L, event, 1000, false);
    }

    /**
     * An offer still filling is stored as one record per fill, and the completion replaces that
     * whole run with a single record. A conversion built on one of those fills would therefore
     * name a trade that stops existing the moment the offer finishes - so the screen does not
     * offer them, and this is the test that says so.
     */
    @Test
    public void anOfferStillFillingIsNotSomethingAConversionCanBeBuiltFrom() {
        Delta finished = trade(1_000L, 1, BLADE, true, 1, "OFFER_COMPLETED");
        Delta firstFill = trade(2_000L, 2, HILT, true, 1, "OFFER_UPDATED");
        Delta secondFill = trade(2_500L, 2, HILT, true, 1, "OFFER_UPDATED");

        List<Delta> offered = RecipeRecorder.offerable(
            Arrays.asList(finished, firstFill, secondFill), RecipeFlipLedger.empty());

        assertEquals(1, offered.size());
        assertEquals(BLADE, offered.get(0).itemId);
    }

    /** What an earlier record has already spoken for is not on offer to a second one. */
    @Test
    public void aTradeAnEarlierRecordAlreadyClaimedIsNotOfferedAgain() {
        Delta blades = trade(1_000L, 1, BLADE, true, 4, "OFFER_COMPLETED");
        Delta hilt = trade(2_000L, 2, HILT, true, 1, "OFFER_COMPLETED");
        Delta sale = trade(3_000L, 3, GODSWORD, false, 1, "OFFER_COMPLETED");
        List<Delta> deltas = Arrays.asList(blades, hilt, sale);
        RecipeFlip flip = new RecipeFlip(ConversionKind.ASSEMBLE, "Armadyl godsword",
            Arrays.asList(new RecipeFlip.Part(TradeKey.of(blades), 1),
                new RecipeFlip.Part(TradeKey.of(hilt), 1)),
            Collections.singletonList(new RecipeFlip.Part(TradeKey.of(sale), 1)), 0L, 10L);

        List<Delta> offered = RecipeRecorder.offerable(
            deltas, RecipeFlipLedger.apply(deltas, Collections.singletonList(flip)));

        assertEquals("the hilt and the sale are spoken for; three blades are not",
            1, offered.size());
        assertEquals(BLADE, offered.get(0).itemId);
        assertEquals(3, offered.get(0).deltaQty);
    }

    /** Newest first, because a conversion is usually recorded soon after the trades that made it. */
    @Test
    public void theNewestTradeIsOfferedFirst() {
        Delta older = trade(1_000L, 1, BLADE, true, 1, "OFFER_COMPLETED");
        Delta newer = trade(9_000L, 2, HILT, true, 1, "OFFER_COMPLETED");

        List<Delta> offered = RecipeRecorder.offerable(
            Arrays.asList(older, newer), RecipeFlipLedger.empty());

        assertEquals(HILT, offered.get(0).itemId);
        assertEquals(BLADE, offered.get(1).itemId);
    }

    /** Two stored records of the same trade would be one row the ledger cannot tell apart. */
    @Test
    public void oneRowPerStoredTrade() {
        Delta first = trade(1_000L, 1, BLADE, true, 1, "OFFER_COMPLETED");
        Delta repeat = trade(1_000L, 1, BLADE, true, 1, "OFFER_COMPLETED");

        assertEquals(1, RecipeRecorder.offerable(
            Arrays.asList(first, repeat), RecipeFlipLedger.empty()).size());
    }
}
