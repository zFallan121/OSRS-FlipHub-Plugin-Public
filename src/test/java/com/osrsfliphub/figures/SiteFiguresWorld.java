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

import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import okhttp3.Request;

/**
 * One RuneLite window with the side panel in it, for the site-figures tests: the plugin's real
 * services over real trade files ({@link RecordSyncWorld}), the real Profile tab, and a website
 * that answers about figures only what the real website answered ({@link FigureFixtures}).
 *
 * <p>Three things are in a test's hands that are not in the client. The website answers only when
 * a test says so ({@link #answer}, {@link #settle}): the plugin asks on its IO pool, and what it
 * hands that pool waits here. Time passes only when a test says so ({@link #pass}). And the tab
 * is on screen without a screen ({@link ProfileTab}).
 *
 * <p>What a test reads, it reads off the tab's own labels, as the player does.
 */
final class SiteFiguresWorld {
    static final long MAIN = 4242L;
    static final long ALT = 777L;
    static final int WHIP = 4151;
    static final long SECOND = 1_000L;
    static final long MINUTE = 60 * SECOND;

    final RecordSyncWorld world;
    /** The plugin's scheduler: one thread, as in the client. */
    final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    /** What the plugin has handed its IO pool and the website has not answered yet. */
    private final Queue<Runnable> io = new ConcurrentLinkedQueue<>();
    private final AtomicLong clock;
    /**
     * Which of the website's answers a character's tag gets. The website's own book has a MAIN
     * and an ALT; here they are the characters a test logs in as.
     */
    final Map<String, String> characters = new ConcurrentHashMap<>();
    ProfileTab tab;

    private SiteFiguresWorld(RecordSyncWorld world, AtomicLong clock) throws Exception {
        this.world = world;
        this.clock = clock;
        world.plugin.scheduler = scheduler;
        // Injected by the client; the Merchant level keeps its best level through it.
        world.plugin.configManager = world.configManager;
        world.plugin.ioExecutor = new AbstractExecutorService() {
            @Override
            public void execute(Runnable work) {
                io.add(work);
            }

            @Override
            public void shutdown() {
            }

            @Override
            public List<Runnable> shutdownNow() {
                return new ArrayList<>();
            }

            @Override
            public boolean isShutdown() {
                return false;
            }

            @Override
            public boolean isTerminated() {
                return false;
            }

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) {
                return true;
            }
        };
        // The plugin tells the time by a clock a test can set: the minute an answer is kept for, the
        // twenty seconds between two questions and the wait a rate limit asks for all read it.
        world.injector.getInstance(SiteFigures.class).clock = clock::get;
        characters.put(GeEvent.characterId(MAIN), "main_all_time");
        characters.put(GeEvent.characterId(ALT), "alt_all_time");
        onSwing(() -> {
            tab = new ProfileTab(world.plugin.config);
            world.plugin.panel = tab;
            // All time, chosen before the tab is opened: the Session range starts at the login.
            this.<JComboBox<StatsRange>>field("statsRangeCombo").setSelectedItem(StatsRange.ALL_TIME);
        });
        quiesce();
    }

    /**
     * A computer of its own, linked, at the moment the website's answers were made: 3 October
     * 2026, with the Profile tab built and not yet opened.
     */
    static SiteFiguresWorld fresh() throws Exception {
        return new SiteFiguresWorld(RecordSyncWorld.fresh(), new AtomicLong(FigureFixtures.AS_OF_MS));
    }

    /** The same computer started again: the same files and the same config, nothing in memory. */
    SiteFiguresWorld again() throws Exception {
        stop();
        return new SiteFiguresWorld(world.again(), clock);
    }

    static void close(SiteFiguresWorld figures) throws Exception {
        if (figures != null) {
            figures.stop();
            RecordSyncWorld.close(figures.world);
        }
    }

    private void stop() throws Exception {
        scheduler.shutdownNow();
        onSwing(tab::dispose);
    }

    // ---- time ----

    long now() {
        return clock.get();
    }

    void pass(long ms) {
        clock.addAndGet(ms);
    }

    /** The time of day the panel would print for a moment: 14:05. */
    static String clockTime(long ms) {
        return DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(ms));
    }

    // ---- the website ----

    /** The switch on: every question gets the answer the real website gave to the same one. */
    void switchOn() {
        world.website.figures = this::live;
    }

    /** The switch off, as it is today: {@code {"live": false}} to everything. */
    void switchOff() {
        world.website.figures = request -> new RecordSyncWorld.Reply(200,
            FigureFixtures.off(flips(request) ? "flips_off" : "figures_off"));
    }

    void unreachable() {
        world.website.figures = request -> {
            throw new UncheckedIOException(new IOException("fliphubosrs.com cannot be reached"));
        };
    }

    /** More than 180 questions in ten minutes: wait 37 seconds. */
    void rateLimited() {
        world.website.figures = request -> FigureFixtures.rateLimited();
    }

    private RecordSyncWorld.Reply live(Request request) {
        String scope = request.url().queryParameter("scope");
        boolean ranged = request.url().queryParameter("since_ms") != null;
        String answer;
        if (flips(request)) {
            // The website's book was asked for the whip's flips, and for no other item's.
            boolean whip = String.valueOf(WHIP).equals(request.url().queryParameter("item_id"));
            answer = !whip ? null : "account".equals(scope) ? "flips_whip_account"
                : GeEvent.characterId(MAIN).equals(scope) ? "flips_whip_main" : null;
        } else if ("account".equals(scope)) {
            answer = ranged ? "account_since_40000" : "account_all_time";
        } else {
            answer = scope != null ? characters.get(scope) : null;
            if (ranged && "main_all_time".equals(answer)) {
                answer = "main_since_40000";
            }
        }
        // A question the real website was never asked: there is no answer of its own to give.
        return answer != null ? new RecordSyncWorld.Reply(200, FigureFixtures.live(answer))
            : new RecordSyncWorld.Reply(404, "");
    }

    private static boolean flips(Request request) {
        return request.url().encodedPath().endsWith("/figures/flips");
    }

    /** Every question about the figures themselves that reached the website, in order. */
    List<Request> figuresAsked() {
        return asked(false);
    }

    /** Every question about one item's flips that reached the website, in order. */
    List<Request> flipsAsked() {
        return asked(true);
    }

    private List<Request> asked(boolean flips) {
        List<Request> out = new ArrayList<>();
        for (RecordSyncWorld.Asked asked : world.website.asked) {
            if (flips(asked.request) == flips) {
                out.add(asked.request);
            }
        }
        return out;
    }

    // ---- what happens ----

    /**
     * The website answers what it has been asked, and whatever the plugin does with the answers is
     * done. Run on this thread, which stands for the plugin's IO pool.
     *
     * @return whether anything was waiting for it
     */
    boolean answer() {
        boolean any = false;
        Runnable work;
        while ((work = io.poll()) != null) {
            work.run();
            any = true;
        }
        return any;
    }

    /**
     * Lets the scheduler, the game thread's queue and the Swing thread finish what they have been
     * handed. The website does not answer: what the plugin has asked it is still on its way.
     */
    void quiesce() throws Exception {
        for (int i = 0; i < 3; i++) {
            scheduler.submit(() -> { }).get(20, TimeUnit.SECONDS);
            world.runGameThread();
            onSwing(() -> { });
        }
    }

    /** Everything comes to rest: the website answers, and what follows from its answers is done. */
    void settle() throws Exception {
        for (int i = 0; i < 25; i++) {
            quiesce();
            if (!answer()) {
                return;
            }
        }
        throw new AssertionError("the plugin never stops asking the website");
    }

    /** The tab's own refresh, which the scheduler runs each minute while the plugin is on. */
    void refresh() throws Exception {
        scheduler.submit(world.plugin::refreshStatsData).get(20, TimeUnit.SECONDS);
        quiesce();
    }

    /** A refresh of the tab, answered by the website and drawn. */
    void show() throws Exception {
        refresh();
        settle();
    }

    /** The two-second upload tick, answered by the website. */
    void upload() throws Exception {
        world.tick();
        settle();
    }

    // ---- the player ----

    /** Logs a character in; a client tick later the Merchant level knows who it is. */
    void login(long hash, String name) throws Exception {
        world.login(hash, name);
        world.injector.getInstance(RankUp.class).tick();
        quiesce();
    }

    void login(long hash) throws Exception {
        login(hash, null);
    }

    /** Opens the Profile tab, which asks for its figures. The website has not answered yet. */
    void openProfileTab() throws Exception {
        onSwing(() -> this.<AbstractButton>field("statsTab").doClick(0));
        quiesce();
    }

    void openActivityTab() throws Exception {
        onSwing(() -> this.<AbstractButton>field("flippingTab").doClick(0));
        quiesce();
    }

    /** Picks a range in the tab's dropdown. The website has not answered yet. */
    void range(StatsRange range) throws Exception {
        onSwing(() -> this.<JComboBox<StatsRange>>field("statsRangeCombo").setSelectedItem(range));
        quiesce();
    }

    /** Picks one character in the profile menu. The website has not answered yet. */
    void character(long key) throws Exception {
        onSwing(() -> new PanelPluginListener().onProfileSelected("hash_" + key));
        quiesce();
    }

    /** Picks Accountwide in the profile menu. The website has not answered yet. */
    void accountwide() throws Exception {
        onSwing(() -> new PanelPluginListener().onProfileSelected(Const.ACCOUNTWIDE_KEY_STRING));
        quiesce();
    }

    /** The "Total Profit" dropdown at the top of the tab: which kind of flip the total adds up. */
    void totalOf(StatsRecipeFilter kind) throws Exception {
        onSwing(() -> this.<PanelState>field("panelState").setStatsProfitFilter(kind));
        quiesce();
    }

    /** The filter over the item list. */
    void listOnly(StatsRecipeFilter kind) throws Exception {
        onSwing(() -> this.<PanelState>field("panelState").setStatsRecipeFilter(kind));
        quiesce();
    }

    /** Clicks an item's card, which opens it or shuts it. */
    void clickCard(String itemName) throws Exception {
        click(label(itemName));
    }

    /** Clicks the heading of an open card's flip list, which opens the list or shuts it. */
    void clickFlipList() throws Exception {
        for (JLabel label : labels()) {
            String text = label.getText();
            if (text != null && (text.startsWith("Flip history") || text.startsWith("Activity ("))) {
                click(label);
                return;
            }
        }
        throw new AssertionError("no flip list heading on the tab: " + texts());
    }

    /** A click as a hand makes one: pressed and let go on the same thing. */
    private void click(Component target) throws Exception {
        if (target == null) {
            throw new AssertionError("nothing to click: " + texts());
        }
        onSwing(() -> {
            MouseEvent at = new MouseEvent(target, MouseEvent.MOUSE_PRESSED, 0L, MouseEvent.BUTTON1_DOWN_MASK,
                0, 0, 1, false, MouseEvent.BUTTON1);
            for (MouseListener listener : target.getMouseListeners()) {
                listener.mousePressed(at);
                listener.mouseReleased(at);
            }
        });
        quiesce();
    }

    // ---- what the tab shows ----

    String total() throws Exception {
        return text("statsTotalProfitValue");
    }

    String roi() throws Exception {
        return text("statsRoiValue");
    }

    String flipCount() throws Exception {
        return text("statsFlipsValue");
    }

    String tax() throws Exception {
        return text("statsTaxValue");
    }

    private String text(String name) throws Exception {
        String[] text = new String[1];
        onSwing(() -> text[0] = this.<JLabel>field(name).getText());
        return text[0];
    }

    /** Every label the open tab has on show, top to bottom: a row or a card that is hidden is not among them. */
    List<JLabel> labels() throws Exception {
        List<JLabel> out = new ArrayList<>();
        onSwing(() -> collect(tab, out));
        return out;
    }

    private static void collect(Component component, List<JLabel> out) {
        if (!component.isVisible()) {
            return;
        }
        if (component instanceof JLabel) {
            out.add((JLabel) component);
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collect(child, out);
            }
        }
    }

    /** What those labels say, in the same order. */
    List<String> texts() throws Exception {
        List<String> out = new ArrayList<>();
        for (JLabel label : labels()) {
            if (label.getText() != null && !label.getText().isEmpty()) {
                out.add(label.getText());
            }
        }
        return out;
    }

    /** The label on show that says exactly this, or null. */
    JLabel label(String text) throws Exception {
        for (JLabel label : labels()) {
            if (text.equals(label.getText())) {
                return label;
            }
        }
        return null;
    }

    /** The first label on show whose words hold these, whatever their case, or null. */
    JLabel labelSaying(String words) throws Exception {
        for (JLabel label : labels()) {
            if (label.getText() != null && label.getText().toLowerCase(java.util.Locale.US)
                .contains(words.toLowerCase(java.util.Locale.US))) {
                return label;
            }
        }
        return null;
    }

    /**
     * The value beside a row's label, on the tab's card or on an open item card; null when the row
     * is not on show. The row is the one labelled exactly so, or failing that the first whose
     * label holds the words.
     */
    JLabel valueOf(String rowLabel) throws Exception {
        JLabel label = label(rowLabel);
        if (label == null) {
            label = labelSaying(rowLabel);
        }
        if (label == null) {
            return null;
        }
        for (Component sibling : label.getParent().getComponents()) {
            if (sibling instanceof JLabel && sibling != label) {
                return (JLabel) sibling;
            }
        }
        return null;
    }

    /** Whether a figure is dashes: no number at all, and nothing a player could take for one. */
    static boolean dashes(String text) {
        return text != null && text.matches("[-–— ]*[-–—][-–— ]*");
    }

    // ---- this computer's own files ----

    /**
     * A finished flip in this computer's own files, which the website has confirmed it holds:
     * bought {@code boughtAgoMs} ago, held for {@code heldMs}, sold for {@code keptGp} after tax.
     */
    static Delta[] flip(int slot, int item, int qty, long costGp, long keptGp, long boughtAgoMs, long heldMs) {
        long bought = System.currentTimeMillis() - boughtAgoMs;
        long sold = bought + heldMs;
        return new Delta[] {
            new Delta(bought, slot, item, true, qty, costGp, "OFFER_COMPLETED", (int) (costGp / qty), false,
                bought - 5 * SECOND, bought, bought),
            new Delta(sold, slot, item, false, qty, keptGp, "OFFER_COMPLETED", (int) (keptGp / qty), false,
                sold - 5 * SECOND, sold, sold)
        };
    }

    /** Writes a character's file: these flips and nothing else. */
    void own(long key, Delta[]... flips) {
        List<Delta> trades = new ArrayList<>();
        for (Delta[] flip : flips) {
            trades.add(flip[0]);
            trades.add(flip[1]);
        }
        world.store(key, trades.toArray(new Delta[0]));
    }

    // ---- the panel ----

    /**
     * The side panel, on show without a screen: the plugin's own {@link Panel}, which only
     * answers that it is showing. The plugin draws nothing on a panel that is not.
     */
    static final class ProfileTab extends Panel {
        volatile boolean onScreen = true;
        /** How many times the plugin has drawn the tab's figures. */
        final AtomicInteger draws = new AtomicInteger();
        private volatile CountDownLatch hold;
        private volatile CountDownLatch held;

        ProfileTab(PluginConfig config) {
            super(null, new PanelPluginListener(), new PanelBookmarkStore() {
                @Override
                public boolean isBookmarked(int itemId) {
                    return false;
                }

                @Override
                public void toggleBookmark(int itemId) {
                }
            }, new PanelHiddenItemStore() {
                @Override
                public boolean isHidden(int itemId) {
                    return false;
                }

                @Override
                public void hideItem(int itemId) {
                }
            }, config);
        }

        @Override
        public boolean isShowing() {
            return onScreen;
        }

        /**
         * The next draw stops half way, until the latch this returns is let go: the refresh that
         * is drawing is then still running.
         */
        CountDownLatch holdTheNextDraw() {
            held = new CountDownLatch(1);
            hold = new CountDownLatch(1);
            return hold;
        }

        /** Waits until a draw has reached the place {@link #holdTheNextDraw} stops it. */
        void awaitHeld() throws InterruptedException {
            if (!held.await(20, TimeUnit.SECONDS)) {
                throw new AssertionError("the tab was never drawn");
            }
        }

        @Override
        void setStatsData(StatsSummary summary, List<StatsItem> items,
                          Map<Integer, List<StatsFlipInstance>> historyByItem, long asOfMs) {
            draws.incrementAndGet();
            CountDownLatch wait = hold;
            if (wait != null) {
                hold = null;
                held.countDown();
                try {
                    wait.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            super.setStatsData(summary, items, historyByItem, asOfMs);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T field(String name) {
        try {
            Field field = Panel.class.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(tab);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("the panel has no " + name, ex);
        }
    }

    static void onSwing(Runnable work) throws Exception {
        SwingUtilities.invokeAndWait(work);
        // Setters hop onto the Swing thread themselves; let what they queued run too.
        SwingUtilities.invokeAndWait(() -> {
        });
    }
}
