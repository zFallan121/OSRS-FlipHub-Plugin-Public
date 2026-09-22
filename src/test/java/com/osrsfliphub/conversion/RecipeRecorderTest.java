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
        recorder.open(false);

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
        recorder.open(false);

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
            recorder.open(false);

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

    /**
     * Recording stock as moved to another account is the same screen with different furniture:
     * the account it went to appears, and the fee and the tally - a move has neither - go.
     *
     * <p>Recipe mode has to come out of it exactly as it went in. The account picker sits inside
     * the gap that was always between the kind and the search, and takes no room while hidden.
     */
    @Test
    public void recordingAMoveShowsTheAccountAndHidesTheFeeAndTheTally() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RecipeRecorder recorder =
                new RecipeRecorder(new UiStyler(), new PanelValueFormat(), () -> {
                });
            recorder.open(false);
            Container screen = (Container) recorder.view().getViewport().getView();
            List<javax.swing.JComboBox<?>> combos = new ArrayList<>();
            List<JComponent> fee = new ArrayList<>();
            collect(screen, combos, fee);

            assertEquals("the kind, and the account", 2, combos.size());
            assertEquals(1, fee.size());
            javax.swing.JComboBox<?> kind = combos.get(0);
            javax.swing.JComboBox<?> account = combos.get(1);

            assertTrue(!account.isVisible() && fee.get(0).getParent().isVisible());
            // A move has its own way in, the Move link, and is no longer a sixth kind of recipe.
            for (int i = 0; i < kind.getItemCount(); i++) {
                assertTrue("no " + kind.getItemAt(i) + " in the recipe kinds", kind.getItemAt(i) != ConversionKind.TRANSFER);
            }
            assertEquals(5, kind.getItemCount());

            recorder.open(true);
            assertTrue(account.isVisible() && !fee.get(0).getParent().isVisible());
            assertTrue("a move has no kind to pick", !kind.isVisible());

            recorder.open(false);
            assertTrue(!account.isVisible() && fee.get(0).getParent().isVisible() && kind.isVisible());
        });
    }

    /**
     * The Profile tab's "Move" link opens the screen already recording a move, and the title says
     * so. As the sixth entry of a list of recipe kinds, under a title reading "Record a recipe",
     * the player who had asked for the feature could not find it.
     */
    @Test
    public void openedForAMoveItSaysSoAndIsReadyForOne() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RecipeRecorder recorder =
                new RecipeRecorder(new UiStyler(), new PanelValueFormat(), () -> {
                });
            Container screen = (Container) recorder.view().getViewport().getView();
            List<javax.swing.JComboBox<?>> combos = new ArrayList<>();
            collect(screen, combos, new ArrayList<>());

            recorder.open(true);
            assertTrue("no recipe kind on a move", !combos.get(0).isVisible());
            assertTrue(combos.get(1).isVisible());
            assertTrue(texts(screen).contains("RECORD A MOVE"));
            assertTrue(texts(screen).contains("Record Transfer"));

            recorder.open(false);
            assertEquals("a recipe starts where it always did", ConversionKind.ASSEMBLE, combos.get(0).getSelectedItem());
            assertTrue(!combos.get(1).isVisible());
            assertTrue(texts(screen).contains("RECORD A RECIPE"));
            assertTrue(texts(screen).contains("Record"));
            assertTrue(!texts(screen).contains("Record Transfer"));
        });
    }

    /**
     * The alt's screen offers what another account handed it, said as such, so it can move it
     * on or make something of it. Asked of the real store, through the lookup the plugin uses.
     */
    @Test
    public void whatAnotherAccountHandedOverIsOfferedAsReceived() throws Exception {
        long main = 7L;
        long alt = 8L;
        Delta logs = new Delta(1_000L, 2, 6332, true, 11_000, 1_067_000L, "OFFER_COMPLETED", 97, false);
        RecipeFlipStore store = new RecipeFlipStore();
        store.add(main, new RecipeFlip(ConversionKind.TRANSFER, "Mahogany logs to Alt",
            Collections.singletonList(new RecipeFlip.Part(TradeKey.of(logs), 11_000, 1_067_000L)),
            null, 0L, 9_000L, alt, null));
        Bridge.set(com.google.inject.Guice.createInjector(binder -> {
            binder.bind(RecipeFlipStore.class).toInstance(store);
            binder.bind(TradeSession.class).toProvider(com.google.inject.util.Providers.of(null));
            binder.bind(ProfileCatalog.class).toProvider(com.google.inject.util.Providers.of(null));
            binder.bind(ItemLookup.class).toProvider(com.google.inject.util.Providers.of(null));
        }));
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    RecipeRecorder recorder =
                        new RecipeRecorder(new UiStyler(), new PanelValueFormat(), () -> {
                        });
                    java.lang.reflect.Method openFor = RecipeRecorder.class.getDeclaredMethod("openFor", long.class);
                    openFor.setAccessible(true);
                    openFor.invoke(recorder, alt);

                    List<?> picks = (List<?>) field(recorder, "picks");
                    assertEquals("the alt has no trades of its own here, only what it was handed", 1, picks.size());
                    String row = String.join(" ", texts((Container) recorder.view().getViewport().getView()));
                    assertTrue(row, row.contains(">Received</font>"));
                    assertTrue(row, !row.contains(">Bought</font>"));

                    // And the main's own screen does not offer it back.
                    openFor.invoke(recorder, main);
                    assertTrue(((List<?>) field(recorder, "picks")).isEmpty());
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
        } finally {
            Bridge.set(null);
        }
    }

    private static List<String> texts(Container container) {
        List<String> out = new ArrayList<>();
        for (Component child : container.getComponents()) {
            if (child instanceof javax.swing.JLabel) {
                out.add(((javax.swing.JLabel) child).getText());
            } else if (child instanceof javax.swing.AbstractButton) {
                out.add(((javax.swing.AbstractButton) child).getText());
            } else if (child instanceof Container) {
                out.addAll(texts((Container) child));
            }
        }
        return out;
    }

    /**
     * What the ticks become. The screen's own fields are reached into, because outside the plugin
     * there are no trades for it to offer and no accounts for it to list.
     */
    @Test
    public void aTickedPurchaseAndAnAccountBecomeAMoveThatCarriesWhatItCost() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                RecipeRecorder recorder =
                    new RecipeRecorder(new UiStyler(), new PanelValueFormat(), () -> {
                    });
                Delta bought = trade(1_000L, 1, 385, true, 1000, "OFFER_COMPLETED");
                Delta sold = trade(5_000L, 2, 385, false, 400, "OFFER_COMPLETED");
                tick(recorder, bought, 600);
                tick(recorder, sold, 400);
                javax.swing.JComboBox<?> kind = (javax.swing.JComboBox<?>) field(recorder, "kindCombo");
                ((javax.swing.JTextField) field(recorder, "feeField")).setText("500");

                // As a recipe: both sides, the fee, and nothing about coins or accounts.
                kind.setSelectedItem(ConversionKind.ASSEMBLE);
                RecipeFlip recipe = build(recorder);
                assertEquals(1, recipe.outputParts().size());
                assertEquals(500L, recipe.feeGp);
                assertEquals(null, recipe.inputParts().get(0).gp);
                assertEquals(null, recipe.toAccount);

                // As a move, with nowhere to move it to: not a record yet.
                moving(recorder);
                assertEquals(null, build(recorder));

                @SuppressWarnings("unchecked")
                javax.swing.JComboBox<String> account = (javax.swing.JComboBox<String>) field(recorder, "toCombo");
                @SuppressWarnings("unchecked")
                List<Long> keys = (List<Long>) field(recorder, "toKeys");
                keys.add(8L);
                account.addItem("Alt Of Mine");

                RecipeFlip move = build(recorder);
                assertTrue(move.isUsable());
                assertEquals("a move, not the recipe kind picked before", ConversionKind.TRANSFER, move.kind);
                assertEquals(Long.valueOf(8L), move.toAccount);
                assertEquals("the sale ticked earlier is no part of it", 0, move.outputParts().size());
                assertEquals(1, move.inputParts().size());
                assertEquals(600, move.inputParts().get(0).quantity);
                assertEquals("600 of 1,000 bought for 1,000,000", Long.valueOf(600_000L), move.inputParts().get(0).gp);
                assertEquals("a fee typed for a recipe is not a move's", 0L, move.feeGp);
                assertTrue(move.name, move.name.endsWith(" to Alt Of Mine"));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    /** Typed the way the chatbox takes it. Keeping only the digits read "10k" as 10 and "1.5" as 15. */
    @Test
    public void anAmountIsReadTheWayTheChatboxReadsIt() {
        assertEquals(10_000L, RecipeRecorder.parseNumber("10k", -1L));
        assertEquals(1_500_000L, RecipeRecorder.parseNumber("1.5m", -1L));
        assertEquals(2_000_000_000L, RecipeRecorder.parseNumber("2B", -1L));
        assertEquals(21_780L, RecipeRecorder.parseNumber(" 21,780 ", -1L));
        assertEquals(12L, RecipeRecorder.parseNumber("12", -1L));
        assertEquals(1L, RecipeRecorder.parseNumber("1.5", -1L));
        assertEquals("nothing typed", -1L, RecipeRecorder.parseNumber("", -1L));
        assertEquals("not an amount", -1L, RecipeRecorder.parseNumber("lots", -1L));
        assertEquals(-1L, RecipeRecorder.parseNumber(null, -1L));
    }

    /**
     * The website takes at most 64 trades in one record. A 70-purchase move used to record fine here
     * and then be refused on every upload, every session, for as long as it was kept.
     */
    @Test
    public void noMoreThanSixtyFourTradesGoIntoOneRecord() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                RecipeRecorder recorder = new RecipeRecorder(new UiStyler(), new PanelValueFormat(), () -> {
                });
                moving(recorder);
                @SuppressWarnings("unchecked")
                List<Long> keys = (List<Long>) field(recorder, "toKeys");
                keys.add(8L);
                ((javax.swing.JComboBox<String>) field(recorder, "toCombo")).addItem("Alt Of Mine");
                for (int i = 0; i < 64; i++) {
                    tick(recorder, trade(1_000L + i, i % 8, 1511, true, 100, "OFFER_COMPLETED"), 100);
                }
                assertNotNull("64 is fine", build(recorder));

                tick(recorder, trade(9_000L, 0, 1511, true, 100, "OFFER_COMPLETED"), 100);
                assertEquals("65 is not", null, build(recorder));
                java.lang.reflect.Method update = RecipeRecorder.class.getDeclaredMethod("updateRecordButton");
                update.setAccessible(true);
                update.invoke(recorder);
                assertEquals("and it says why", "At most 64 trades in one record",
                    ((javax.swing.JButton) field(recorder, "recordButton")).getToolTipText());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    /** The move screen, as the Move link opens it, without reading any account's trades. */
    private static void moving(RecipeRecorder recorder) throws Exception {
        java.lang.reflect.Field moving = RecipeRecorder.class.getDeclaredField("moving");
        moving.setAccessible(true);
        moving.setBoolean(recorder, true);
    }

    private static Object field(Object target, String name) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    @SuppressWarnings("unchecked")
    private static void tick(RecipeRecorder recorder, Delta trade, int quantity) throws Exception {
        Class<?> candidate = Class.forName("com.osrsfliphub.RecipeRecorder$Candidate");
        java.lang.reflect.Constructor<?> make = candidate.getDeclaredConstructor(Delta.class);
        make.setAccessible(true);
        Object pick = make.newInstance(trade);
        java.lang.reflect.Field used = candidate.getDeclaredField("used");
        used.setAccessible(true);
        used.setInt(pick, quantity);
        ((List<Object>) field(recorder, "picks")).add(pick);
    }

    private static RecipeFlip build(RecipeRecorder recorder) throws Exception {
        java.lang.reflect.Method build = RecipeRecorder.class.getDeclaredMethod("buildFlip");
        build.setAccessible(true);
        return (RecipeFlip) build.invoke(recorder);
    }

    private static void collect(Container container, List<javax.swing.JComboBox<?>> combos, List<JComponent> fee) {
        for (Component child : container.getComponents()) {
            if (child instanceof javax.swing.JComboBox) {
                combos.add((javax.swing.JComboBox<?>) child);
            } else if (child instanceof JComponent
                && "What the recipe itself cost".equals(((JComponent) child).getToolTipText())) {
                fee.add((JComponent) child);
            } else if (child instanceof Container) {
                collect((Container) child, combos, fee);
            }
        }
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
            Arrays.asList(finished, firstFill, secondFill), RecipeFlipLedger.empty(), new java.util.HashSet<>());

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
            Arrays.asList(new RecipeFlip.Part(TradeKey.of(blades), 1, null),
                new RecipeFlip.Part(TradeKey.of(hilt), 1, null)),
            Collections.singletonList(new RecipeFlip.Part(TradeKey.of(sale), 1, null)), 0L, 10L, null, null);

        List<Delta> offered = RecipeRecorder.offerable(
            deltas, RecipeFlipLedger.apply(deltas, Collections.singletonList(flip)), new java.util.HashSet<>());

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
            Arrays.asList(older, newer), RecipeFlipLedger.empty(), new java.util.HashSet<>());

        assertEquals(HILT, offered.get(0).itemId);
        assertEquals(BLADE, offered.get(1).itemId);
    }

    /**
     * What another account handed over comes first, however long ago that account bought it:
     * it is usually why the screen was opened, and by date it could be pages down.
     */
    @Test
    public void whatWasHandedOverIsOfferedFirstAndTheRestNewestFirst() {
        Delta handed = trade(500L, 1, BLADE, true, 1, "OFFER_COMPLETED");
        Delta older = trade(1_000L, 2, HILT, true, 1, "OFFER_COMPLETED");
        Delta newer = trade(9_000L, 3, GODSWORD, true, 1, "OFFER_COMPLETED");

        List<Delta> offered = RecipeRecorder.offerable(Arrays.asList(older, handed, newer),
            RecipeFlipLedger.empty(), new java.util.HashSet<>(Collections.singletonList(TradeKey.of(handed))));

        assertEquals(Arrays.asList(BLADE, GODSWORD, HILT),
            Arrays.asList(offered.get(0).itemId, offered.get(1).itemId, offered.get(2).itemId));
    }

    /**
     * Recording keeps the screen up, read again, with a line saying what was recorded. It used to
     * close, and with nothing on the Profile tab for stock not yet sold the player could not tell
     * anything had happened.
     */
    @Test
    public void recordingSaysWhatWasRecordedAndStaysOnTheScreen() throws Exception {
        RecipeFlipStore store = new RecipeFlipStore();
        PluginConfig unlinked = (PluginConfig) java.lang.reflect.Proxy.newProxyInstance(
            PluginConfig.class.getClassLoader(), new Class<?>[] {PluginConfig.class},
            (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
        RecipeUpload upload = new RecipeUpload(unlinked, null, store, null, null);
        Bridge.set(com.google.inject.Guice.createInjector(binder -> {
            binder.bind(RecipeFlipStore.class).toInstance(store);
            binder.bind(RecipeUpload.class).toInstance(upload);
            binder.bind(TradeSession.class).toProvider(com.google.inject.util.Providers.of(null));
            binder.bind(ProfileCatalog.class).toProvider(com.google.inject.util.Providers.of(null));
            binder.bind(ItemLookup.class).toProvider(com.google.inject.util.Providers.of(null));
        }));
        java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    RecipeRecorder recorder =
                        new RecipeRecorder(new UiStyler(), new PanelValueFormat(), () -> closed.set(true));
                    tick(recorder, trade(1_000L, 2, 6332, true, 11_000, "OFFER_COMPLETED"), 11_000);
                    moving(recorder);
                    @SuppressWarnings("unchecked")
                    javax.swing.JComboBox<String> account = (javax.swing.JComboBox<String>) field(recorder, "toCombo");
                    @SuppressWarnings("unchecked")
                    List<Long> keys = (List<Long>) field(recorder, "toKeys");
                    keys.add(8L);
                    account.addItem("Alt Of Mine");
                    java.lang.reflect.Field key = RecipeRecorder.class.getDeclaredField("accountKey");
                    key.setAccessible(true);
                    key.setLong(recorder, 7L);

                    java.lang.reflect.Method record = RecipeRecorder.class.getDeclaredMethod("record");
                    record.setAccessible(true);
                    record.invoke(recorder);

                    assertEquals(1, store.applicable(7L).size());
                    javax.swing.JLabel notice = (javax.swing.JLabel) field(recorder, "notice");
                    assertTrue(notice.isVisible());
                    assertTrue(notice.getText(), notice.getText().startsWith("Recorded: ")
                        && notice.getText().endsWith(" to Alt Of Mine"));
                    assertTrue("the screen stays up", !closed.get());
                    assertEquals("and is still recording a move", Boolean.TRUE, field(recorder, "moving"));

                    // Opened afresh, the line from last time is gone.
                    recorder.open(false);
                    assertTrue(!notice.isVisible());
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
        } finally {
            Bridge.set(null);
        }
    }

    /**
     * The screen is read again after every Record and Forget, and rebuilding the list of accounts
     * put it back on its first entry. With two alts the next move went to the other one, and only
     * the "Recorded: ... to" line said so. Found by the final audit, 21 Sep 2026. The list is built
     * from real files here, because the other tests fill it by hand and never run this code.
     */
    @Test
    public void theAccountPickedStaysPickedWhenTheScreenIsReadAgain() throws Exception {
        java.nio.file.Path home = java.nio.file.Files.createTempDirectory("recorder-picker-test");
        com.google.gson.Gson gson = new com.google.gson.Gson();
        ProfileStore files = new ProfileStore(gson, "fliphub", "fliphub-dev", home);
        for (long account : new long[] {7L, 8L, 9L}) {
            ProfileData data = new ProfileData();
            data.accountHash = account;
            data.displayName = "Account " + account;
            data.deltas = Collections.emptyList();
            java.nio.file.Files.createDirectories(files.getProfilesDir());
            java.nio.file.Files.write(files.getProfilesDir().resolve("hash_" + account + ".json"),
                gson.toJson(data).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        // Names come from memory: the files are listed, not read. One is known, one is not yet.
        PluginState state = new PluginState();
        state.getProfileDisplayNames().put(8L, "Sips Potion");
        Bridge.set(com.google.inject.Guice.createInjector(binder -> {
            binder.bind(PluginState.class).toInstance(state);
            binder.bind(RecipeFlipStore.class).toInstance(new RecipeFlipStore());
            binder.bind(TradeSession.class).toProvider(com.google.inject.util.Providers.of(null));
            binder.bind(ProfileCatalog.class).toInstance(new ProfileCatalog(files));
            binder.bind(ItemLookup.class).toProvider(com.google.inject.util.Providers.of(null));
        }));
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    RecipeRecorder recorder = new RecipeRecorder(new UiStyler(), new PanelValueFormat(), () -> {
                    });
                    java.lang.reflect.Method openFor = RecipeRecorder.class.getDeclaredMethod("openFor", long.class);
                    openFor.setAccessible(true);
                    openFor.invoke(recorder, 7L);
                    javax.swing.JComboBox<?> account = (javax.swing.JComboBox<?>) field(recorder, "toCombo");
                    @SuppressWarnings("unchecked")
                    List<Long> keys = (List<Long>) field(recorder, "toKeys");
                    assertEquals("the account itself is not offered", 2, keys.size());
                    assertEquals("Sips Potion", account.getItemAt(keys.indexOf(8L)));
                    assertEquals("Profile 9", account.getItemAt(keys.indexOf(9L)));

                    for (long alt : new long[] {8L, 9L}) {
                        account.setSelectedIndex(keys.indexOf(alt));
                        openFor.invoke(recorder, 7L);
                        assertEquals(Long.valueOf(alt), keys.get(account.getSelectedIndex()));
                    }
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
        } finally {
            Bridge.set(null);
        }
    }

    /** The quantity box fits the whole purchase: at a fixed width 11,000 read as "1100(". */
    @Test
    public void theQuantityBoxFitsALargePurchase() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                RecipeRecorder recorder =
                    new RecipeRecorder(new UiStyler(), new PanelValueFormat(), () -> {
                    });
                tick(recorder, trade(1_000L, 2, 6332, true, 11_000, "OFFER_COMPLETED"), 11_000);
                set(recorder, "accountKey", 7L);
                set(recorder, "waiting", false);
                java.lang.reflect.Method refresh = RecipeRecorder.class.getDeclaredMethod("refresh");
                refresh.setAccessible(true);
                refresh.invoke(recorder);

                javax.swing.JTextField box = findBox((Container) recorder.view().getViewport().getView(), "11000");
                assertNotNull(box);
                int needed = box.getFontMetrics(box.getFont()).stringWidth("11000")
                    + box.getInsets().left + box.getInsets().right;
                assertTrue(box.getPreferredSize().width + " < " + needed, box.getPreferredSize().width >= needed);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private static void set(Object target, String name, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static javax.swing.JTextField findBox(Container container, String text) {
        for (Component child : container.getComponents()) {
            if (child instanceof javax.swing.JTextField && text.equals(((javax.swing.JTextField) child).getText())) {
                return (javax.swing.JTextField) child;
            }
            if (child instanceof Container) {
                javax.swing.JTextField found = findBox((Container) child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static final int DHAROKS_PLATEBODY_0 = 4896;
    private static final int DHAROKS_PLATEBODY = 4720;

    /**
     * An NPC's price, less half a percent per Smithing level on a house armour stand. The figures
     * are the wiki's; Torag's helm is the one piece the wiki's item list files under another name,
     * so it is checked by id.
     */
    @Test
    public void aRepairIsTheNpcPriceLessHalfAPercentPerSmithingLevel() throws Exception {
        assertEquals("an NPC, full price", 90_000L, RecipeRecorder.repairFee(DHAROKS_PLATEBODY_0, 1, 0));
        assertEquals(58_500L, RecipeRecorder.repairFee(DHAROKS_PLATEBODY_0, 1, 70));
        assertEquals("a little over half at 99", 45_450L, RecipeRecorder.repairFee(DHAROKS_PLATEBODY_0, 1, 99));
        assertEquals("Torag's helm 0", 60_000L, RecipeRecorder.repairFee(4956, 1, 0));
        assertEquals("two broken Blood moon helms at 99", 1_515_000L, RecipeRecorder.repairFee(29073, 2, 99));
        assertEquals("a repaired piece costs nothing to repair", 0L, RecipeRecorder.repairFee(DHAROKS_PLATEBODY, 1, 99));
        assertEquals("nor does a godsword", 0L, RecipeRecorder.repairFee(GODSWORD, 1, 0));
        assertEquals("24 Barrows pieces and 9 moon pieces", 33, ((java.util.Properties) staticField("REPAIRS")).size());
    }

    /**
     * Ticking a broken piece for a repair fills in the fee, which is what the player would
     * otherwise have to work out and type. It is a suggestion in plain sight: it follows the
     * kind, and a number the player typed over it is theirs.
     */
    @Test
    public void aRepairFillsInItsFeeAndLeavesATypedOneAlone() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                RecipeRecorder recorder = repairOfADharoksPlatebody(99);
                javax.swing.JComboBox<?> kind = (javax.swing.JComboBox<?>) field(recorder, "kindCombo");
                javax.swing.JTextField fee = (javax.swing.JTextField) field(recorder, "feeField");
                javax.swing.JLabel note = (javax.swing.JLabel) field(recorder, "feeNote");

                kind.setSelectedItem(ConversionKind.REPAIR);
                assertEquals("45,450", fee.getText());
                assertEquals(45_450L, build(recorder).feeGp);
                assertTrue(note.isVisible());
                assertEquals("STAND · SMITHING 99", note.getText());

                kind.setSelectedItem(ConversionKind.ASSEMBLE);
                assertEquals("an assembly's fee is nothing to do with it", "", fee.getText());
                assertTrue(!note.isVisible());

                kind.setSelectedItem(ConversionKind.REPAIR);
                fee.setText("40,000");
                refresh(recorder);
                assertEquals("typed over, it stays typed over", "40,000", fee.getText());
                assertEquals(40_000L, build(recorder).feeGp);
                assertTrue("and the note is not about it", !note.isVisible());
                kind.setSelectedItem(ConversionKind.ASSEMBLE);
                assertEquals("even when the kind changes", "40,000", fee.getText());

                kind.setSelectedItem(ConversionKind.REPAIR);
                fee.setText("");
                refresh(recorder);
                assertEquals("emptied, it is filled in again", "45,450", fee.getText());

                set(recorder, "standLevel", 0);
                refresh(recorder);
                assertEquals("repaired at an NPC", "90,000", fee.getText());
                assertEquals("NPC PRICE", note.getText());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    /** Opened afresh, nothing is left in the box from the last repair. */
    @Test
    public void openingAgainForgetsTheLastFee() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                RecipeRecorder recorder = repairOfADharoksPlatebody(99);
                ((javax.swing.JComboBox<?>) field(recorder, "kindCombo")).setSelectedItem(ConversionKind.REPAIR);
                recorder.open(false);
                assertEquals("", ((javax.swing.JTextField) field(recorder, "feeField")).getText());
                assertEquals("", field(recorder, "suggested"));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    /** The note shares the fee's heading row, and at the narrowest sidebar the two must not meet. */
    @Test
    public void theFeeNoteFitsBesideItsHeading() throws Exception {
        List<String> collisions = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                RecipeRecorder recorder = repairOfADharoksPlatebody(99);
                ((javax.swing.JComboBox<?>) field(recorder, "kindCombo")).setSelectedItem(ConversionKind.REPAIR);
                Container screen = (Container) recorder.view().getViewport().getView();
                showAll(screen);
                screen.setSize(SIDEBAR_WIDTH, screen.getPreferredSize().height);
                layOut(screen);
                checkRows(screen, collisions);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        assertEquals(collisions.toString(), 0, collisions.size());
    }

    /** A broken Dharok's platebody bought and the mended one sold, on a screen priced at this level. */
    private static RecipeRecorder repairOfADharoksPlatebody(int standLevel) throws Exception {
        RecipeRecorder recorder = new RecipeRecorder(new UiStyler(), new PanelValueFormat(), () -> {
        });
        tick(recorder, trade(1_000L, 1, DHAROKS_PLATEBODY_0, true, 1, "OFFER_COMPLETED"), 1);
        tick(recorder, trade(5_000L, 2, DHAROKS_PLATEBODY, false, 1, "OFFER_COMPLETED"), 1);
        set(recorder, "accountKey", 7L);
        set(recorder, "waiting", false);
        set(recorder, "standLevel", standLevel);
        return recorder;
    }

    private static void refresh(RecipeRecorder recorder) throws Exception {
        java.lang.reflect.Method refresh = RecipeRecorder.class.getDeclaredMethod("refresh");
        refresh.setAccessible(true);
        refresh.invoke(recorder);
    }

    private static Object staticField(String name) throws Exception {
        java.lang.reflect.Field field = RecipeRecorder.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    /** Two stored records of the same trade would be one row the ledger cannot tell apart. */
    @Test
    public void oneRowPerStoredTrade() {
        Delta first = trade(1_000L, 1, BLADE, true, 1, "OFFER_COMPLETED");
        Delta repeat = trade(1_000L, 1, BLADE, true, 1, "OFFER_COMPLETED");

        assertEquals(1, RecipeRecorder.offerable(
            Arrays.asList(first, repeat), RecipeFlipLedger.empty(), new java.util.HashSet<>()).size());
    }
}
