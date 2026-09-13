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

import static com.osrsfliphub.Skin.*;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * The screen where the player tells the plugin that some of their trades were one conversion.
 *
 * <p>The game never says two items were combined, so this is the only way the fact can reach the
 * plugin. It is deliberately a statement rather than a question: pick the purchases that went in,
 * pick the sales the result went out through, and the record names those exact trades for good.
 * Nothing here is inferred, and nothing here is checked against a recipe table, because there is
 * no longer a recipe table to check against. The running tally at the bottom is what catches a
 * wrong tick instead, which is why it is priced by the very calculator that will price the record
 * once it is stored.
 *
 * <p>It also lists what has already been recorded. A record that cannot be taken back is a trap:
 * a mistyped fee or a wrong tick would otherwise follow the account's figures forever, and a
 * record whose trades have since gone stops applying on its own with nothing on screen to say so.
 * Both are visible here, and both can be undone here.
 */
final class RecipeRecorder {
    /** How many unpicked trades a side shows at once. The rest are a page away. */
    private static final int PAGE_SIZE = 10;

    /** One stored trade the player may pick, and how much of it is still unspoken for. */
    private static final class Candidate {
        final Delta trade;
        final TradeKey key;
        final int available;
        int used;

        Candidate(Delta trade) {
            this.trade = trade;
            this.key = TradeKey.of(trade);
            this.available = trade.deltaQty;
        }

        boolean picked() {
            return used > 0;
        }
    }

    private final UiStyler uiStyler;
    private final PanelValueFormat valueFormat;
    private final Runnable onClose;

    private final JPanel content = new TrackingPanel(SCROLL_UNIT_INCREMENT, SCROLL_BLOCK_INCREMENT);
    private final JScrollPane scrollPane = new JScrollPane(content);
    private final JComboBox<ConversionKind> kindCombo = new JComboBox<>(ConversionKind.values());
    private final JTextField feeField = new PlaceholderTextField("0");
    private final JTextField findField = new PlaceholderTextField("Find a trade");
    private final JPanel inputsBody = column();
    private final JPanel outputsBody = column();
    private final JPanel storedBody = column();
    private final JLabel inputsCount = new JLabel();
    private final JLabel outputsCount = new JLabel();
    private final JLabel costValue = new JLabel();
    private final JLabel receivedValue = new JLabel();
    private final JLabel taxValue = new JLabel();
    private final JLabel profitValue = new JLabel();
    private final JButton recordButton = new JButton("Record");
    private final JPanel form = column();
    private final JPanel storedSection = column();
    private final JLabel nothingToDo = new JLabel();
    private final JLabel blocker = new JLabel();
    private final StatsPagerBuilder pager;

    private final List<Candidate> buys = new ArrayList<>();
    private final List<Candidate> sells = new ArrayList<>();
    private List<Delta> trades = new ArrayList<>();
    private List<RecipeFlip> stored = new ArrayList<>();
    /** What the stored records claim of those trades. Only changes when a record does. */
    private RecipeFlipLedger.Result applied = RecipeFlipLedger.empty();
    private long accountKey = -1L;
    private int appliedBefore;
    private boolean waiting;
    private int buyPage = 1;
    private int sellPage = 1;

    RecipeRecorder(UiStyler uiStyler, PanelValueFormat valueFormat, Runnable onClose) {
        this.uiStyler = uiStyler;
        this.valueFormat = valueFormat;
        this.onClose = onClose;
        this.pager = new StatsPagerBuilder(uiStyler);
        build();
    }

    JScrollPane view() {
        return scrollPane;
    }

    /**
     * Start again from what is stored now.
     *
     * <p>Done every time the screen is opened rather than once, because both the trades it
     * offers and the records it lists change while it is closed.
     */
    void open() {
        // Which character this is has to be asked of the game, on the game's own thread. The
        // screen is put up empty first so the click lands straight away, and filled in when the
        // answer comes back.
        accountKey = -1L;
        waiting = true;
        trades = new ArrayList<>();
        stored = new ArrayList<>();
        applied = RecipeFlipLedger.empty();
        appliedBefore = 0;
        buys.clear();
        sells.clear();
        kindCombo.setSelectedItem(ConversionKind.ASSEMBLE);
        feeField.setText("");
        findField.setText("");
        buyPage = 1;
        sellPage = 1;
        scrollPane.getVerticalScrollBar().setValue(0);
        refresh();

        GeLifecyclePlugin plugin = Access.pluginOrNull();
        if (plugin == null) {
            openFor(-1L);
            return;
        }
        plugin.invokeOnClientThread(() -> {
            long key = resolveAccountKey();
            SwingUtilities.invokeLater(() -> openFor(key));
        });
    }

    /** The screen, once the character it is about is known. */
    private void openFor(long key) {
        waiting = false;
        accountKey = key;
        trades = snapshotTrades(key);
        RecipeFlipStore store = Bridge.get(RecipeFlipStore.class);
        stored = store != null && key > 0 ? store.applicable(key) : new ArrayList<>();
        applied = RecipeFlipLedger.apply(trades, stored);
        appliedBefore = applied.activities.size();

        buys.clear();
        sells.clear();
        for (Delta trade : offerable(trades, applied)) {
            (trade.isBuy ? buys : sells).add(new Candidate(trade));
        }
        refresh();
    }

    private void build() {
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setAlignmentX(Component.LEFT_ALIGNMENT);

        content.add(headingRow("Record a recipe",
            uiStyler.actionLink("Cancel", "Leave without recording anything", this::close)));

        nothingToDo.setForeground(MUTED_2);
        nothingToDo.setFont(uiStyler.font(10.5f));
        nothingToDo.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(nothingToDo);

        content.add(form);
        content.add(Box.createVerticalStrut(10));
        content.add(storedSection);

        form.add(headingRow("Recipe type", null));
        uiStyler.styleComboBox(kindCombo);
        kindCombo.setBorder(uiStyler.roundedBorder(INPUT_ARC, CONTROL_BORDER, new Insets(2, 6, 2, 6)));
        stretch(kindCombo);
        kindCombo.addActionListener(event -> updateRecordButton());
        form.add(kindCombo);

        // The same search the rest of the panel uses: the field alone, at full width, with the
        // clear mark inside its own right edge. No heading over it - the placeholder says what
        // it is, and the row it sits in is the one the Profile tab draws.
        form.add(Box.createVerticalStrut(8));
        form.add(searchRow());

        form.add(headingRow("What went in", pickedCount(inputsCount)));
        form.add(CardSection.of(inputsBody));
        form.add(headingRow("What came out", pickedCount(outputsCount)));
        form.add(CardSection.of(outputsBody));

        form.add(headingRow("Fee (if any)", null));
        field(feeField, this::price);
        feeField.setToolTipText("Coins the conversion itself cost, for the whole of this record");
        form.add(feeField);

        form.add(Box.createVerticalStrut(8));
        JPanel tally = column();
        tally.add(detailLine("Cost", costValue));
        tally.add(detailLine("Received", receivedValue));
        tally.add(detailLine("Tax", taxValue));
        tally.add(detailLine("Profit", profitValue));
        form.add(CardSection.of(tally));
        form.add(Box.createVerticalStrut(6));

        // A disabled control that will not say why is a dead end. The tooltip said it, which is
        // no use to anyone who has not already guessed there is something to hover.
        blocker.setForeground(MUTED_2);
        blocker.setFont(uiStyler.font(9.5f));
        blocker.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(blocker);
        form.add(Box.createVerticalStrut(4));

        // A ghost, like every other control in the panel. This is the first thing in the panel
        // that commits anything, so it is also the first that could have argued for a filled
        // one - but a second kind of button would be a new sort of object in a panel that has
        // exactly one, and the tally above it is what says the action is ready.
        uiStyler.styleGhostControl(recordButton, 11.5f, new Insets(6, 12, 6, 12));
        stretch(recordButton);
        recordButton.addActionListener(event -> record());
        form.add(recordButton);

        storedSection.add(headingRow("Recorded", null));
        storedSection.add(CardSection.of(storedBody));

        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        // As on the Profile tab: a transparent viewport that is blitted as it scrolls smears
        // the backdrop's washes down the column.
        scrollPane.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        JScrollBar bar = scrollPane.getVerticalScrollBar();
        bar.setUnitIncrement(SCROLL_UNIT_INCREMENT);
        bar.setBlockIncrement(SCROLL_BLOCK_INCREMENT);
    }

    /** Redraw the two trade lists, the tally and the recorded list from the current picks. */
    private void refresh() {
        boolean hasTrades = !buys.isEmpty() || !sells.isEmpty();
        form.setVisible(hasTrades);
        nothingToDo.setVisible(!hasTrades);
        nothingToDo.setText(waiting
            ? "Reading your trades..."
            : accountKey <= 0
                ? "Log in to record a recipe."
                : "No finished trades of yours are left to build one from.");

        fillSide(inputsBody, buys, inputsCount, true);
        fillSide(outputsBody, sells, outputsCount, false);
        fillStored();
        price();

        content.revalidate();
        content.repaint();
    }

    /**
     * One side's trades: everything picked, then a page of what is left.
     *
     * <p>Ten at a time, because a thousand-trade history rendered whole is a screen nobody can
     * record anything from. Picks stay above the page rather than being paged with it: a tick
     * whose coins are in the tally below but whose row is four pages away is how a record ends
     * up naming trades the player can no longer see.
     */
    private void fillSide(JPanel body, List<Candidate> candidates, JLabel count, boolean buySide) {
        body.removeAll();
        String query = findField.getText() != null
            ? findField.getText().trim().toLowerCase(Locale.US)
            : "";
        List<Candidate> picked = new ArrayList<>();
        List<Candidate> rest = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (candidate.picked()) {
                picked.add(candidate);
            } else if (query.isEmpty()
                || itemName(candidate.trade.itemId).toLowerCase(Locale.US).contains(query)) {
                rest.add(candidate);
            }
        }

        int pages = Math.max(1, (rest.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.min(Math.max(1, buySide ? buyPage : sellPage), pages);
        if (buySide) {
            buyPage = page;
        } else {
            sellPage = page;
        }
        int from = (page - 1) * PAGE_SIZE;

        List<Candidate> shown = new ArrayList<>(picked);
        shown.addAll(rest.subList(from, Math.min(rest.size(), from + PAGE_SIZE)));
        boolean first = true;
        for (Candidate candidate : shown) {
            if (!first) {
                body.add(rule());
            }
            first = false;
            body.add(tradeRow(candidate));
        }
        if (shown.isEmpty()) {
            body.add(wordRow(query.isEmpty() ? "Nothing left to pick." : "No trade by that name."));
        }
        if (pages > 1) {
            body.add(pager.buildPager(page, pages, wanted -> {
                if (buySide) {
                    buyPage = wanted;
                } else {
                    sellPage = wanted;
                }
                refresh();
            }));
        }
        count.setText(String.valueOf(picked.size()));
        count.setForeground(picked.isEmpty() ? MUTED_2 : ACCENT);
    }

    /**
     * One trade, and how much of it this conversion takes.
     *
     * <p>Clicking the row takes the whole of it, because a conversion usually consumes
     * everything the purchase brought. When it did not, the quantity is a field rather than a
     * stepper: a hundred blades out of a thousand is a number to type, not to click for.
     */
    private JPanel tradeRow(Candidate candidate) {
        boolean split = candidate.picked() && candidate.available > 1;
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, split ? 40 : 32));

        JLabel mark = new JLabel(new PickIcon(PICK_MARK_SIZE, candidate.picked()));
        mark.setVerticalAlignment(SwingConstants.TOP);
        mark.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));

        JPanel middle = column();
        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.setOpaque(false);
        top.setMaximumSize(new Dimension(Integer.MAX_VALUE, split ? 22 : 16));
        warmName(candidate.trade.itemId);
        EllipsisLabel name = new EllipsisLabel(itemName(candidate.trade.itemId));
        name.setForeground(candidate.picked() ? TEXT : MUTED);
        name.setFont(uiStyler.font(9.5f));
        name.setHorizontalAlignment(SwingConstants.LEFT);
        top.add(name, BorderLayout.CENTER);
        top.add(split ? quantityField(candidate) : quantityLabel(candidate), BorderLayout.EAST);
        middle.add(top);

        JLabel detail = new JLabel(valueFormat.formatGpCompact(candidate.trade.deltaGp)
            + " · " + age(candidate.trade.closedAtMs()));
        detail.setForeground(MUTED_2);
        detail.setFont(uiStyler.font(9.5f));
        detail.setAlignmentX(Component.LEFT_ALIGNMENT);
        middle.add(detail);

        row.add(mark, BorderLayout.WEST);
        row.add(middle, BorderLayout.CENTER);
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.addMouseListener(new StatsClickMouseAdapter(() -> {
            candidate.used = candidate.picked() ? 0 : candidate.available;
            refresh();
        }));
        return row;
    }

    /**
     * The trades a new conversion may still be built from, newest first.
     *
     * <p>Three rules, and the reasons matter more than the code:
     *
     * <p>Whatever an earlier record has already claimed is gone, so two conversions can never
     * both spend the same blade.
     *
     * <p>Only finished offers. While an offer is filling it is stored as one record per fill,
     * and the completion replaces that whole run with a single record - so a record naming the
     * second fill of an offer still in progress would name a trade that ceases to exist the
     * moment it finishes, and quietly stop applying. Naming the first fill is no better: after
     * the collapse that key covers the whole offer, so the conversion would reprice itself
     * under the player. Besides which, goods still being bought have not gone into anything.
     *
     * <p>One row per stored trade. A repeated key is ambiguous to the ledger, which takes the
     * first and leaves the rest, so offering both would let the player pick a row that quietly
     * resolves to the other one.
     */
    static List<Delta> offerable(List<Delta> trades, RecipeFlipLedger.Result applied) {
        List<Delta> out = new ArrayList<>();
        List<Delta> free = applied.remainingTrades(trades);
        Set<TradeKey> offered = new HashSet<>();
        if (free != null) {
            for (Delta trade : free) {
                if (trade == null || trade.deltaQty <= 0
                    || !"OFFER_COMPLETED".equals(trade.eventType)
                    || !offered.add(TradeKey.of(trade))) {
                    continue;
                }
                out.add(trade);
            }
        }
        out.sort(Comparator.comparingLong(Delta::closedAtMs).reversed());
        return out;
    }

    private JComponent quantityLabel(Candidate candidate) {
        JLabel quantity = new JLabel(String.valueOf(candidate.available), SwingConstants.RIGHT);
        quantity.setForeground(MUTED_2);
        quantity.setFont(uiStyler.fontNumeric(9.5f));
        return quantity;
    }

    private JComponent quantityField(Candidate candidate) {
        JTextField used = new JTextField(String.valueOf(candidate.used));
        uiStyler.styleTextField(used);
        used.setFont(uiStyler.fontNumeric(9.5f));
        used.setHorizontalAlignment(SwingConstants.RIGHT);
        used.setBorder(uiStyler.roundedBorder(INPUT_ARC, CONTROL_BORDER, new Insets(1, 4, 1, 4)));
        used.setPreferredSize(new Dimension(QUANTITY_FIELD_WIDTH, used.getPreferredSize().height));
        uiStyler.onEdit(used, () -> {
            // Clamped rather than refused: an empty box while the player retypes must not drop
            // the pick out from under them, and more than they bought is a typo, not a claim.
            candidate.used = (int) Math.max(1L,
                Math.min(candidate.available, parseNumber(used.getText(), 1L)));
            price();
        });

        JLabel total = new JLabel("/ " + candidate.available);
        total.setForeground(MUTED_2);
        total.setFont(uiStyler.fontNumeric(9.5f));

        JPanel holder = new JPanel(new BorderLayout(3, 0));
        holder.setOpaque(false);
        holder.add(used, BorderLayout.CENTER);
        holder.add(total, BorderLayout.EAST);
        holder.setPreferredSize(new Dimension(
            QUANTITY_FIELD_WIDTH + 3 + total.getPreferredSize().width,
            used.getPreferredSize().height));
        return holder;
    }

    /** What has already been recorded, and whether it still applies. */
    private void fillStored() {
        storedBody.removeAll();
        storedSection.setVisible(!stored.isEmpty());
        if (stored.isEmpty()) {
            return;
        }
        boolean first = true;
        for (RecipeFlip flip : stored) {
            if (!first) {
                storedBody.add(rule());
            }
            first = false;
            storedBody.add(storedRow(flip, appliesNow(applied, flip)));
        }
    }

    private JPanel storedRow(RecipeFlip flip, boolean applies) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.setOpaque(false);
        top.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
        EllipsisLabel name = new EllipsisLabel(Str.hasText(flip.name)
            ? flip.name.trim()
            : itemName(flip.subjectItemId()));
        warmName(flip.subjectItemId());
        name.setForeground(applies ? TEXT : MUTED_2);
        name.setFont(uiStyler.font(9.5f));
        name.setHorizontalAlignment(SwingConstants.LEFT);
        top.add(name, BorderLayout.CENTER);
        top.add(uiStyler.actionLink("Forget", "Undo this record and give its trades back",
            () -> forget(flip)), BorderLayout.EAST);

        // The one thing the player could not otherwise find out. A record whose trades have
        // gone - a wiped history, a re-synced one - stops applying by itself and quietly takes
        // its profit back out of every total, with nothing anywhere saying that it happened.
        JLabel state = new JLabel(applies
            ? String.valueOf(flip.kind)
            : flip.kind + " · its trades are gone");
        state.setForeground(applies ? MUTED_2 : WARNING);
        state.setFont(uiStyler.font(9.5f));
        state.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel middle = column();
        middle.add(top);
        middle.add(state);
        row.add(middle, BorderLayout.CENTER);
        return row;
    }

    /**
     * Whether the ledger took this record. A record is applied whole or not at all, so one
     * trade of it being spoken for is enough to say which happened.
     */
    private static boolean appliesNow(RecipeFlipLedger.Result applied, RecipeFlip flip) {
        for (TradeKey key : flip.trades()) {
            if (key != null && applied.claimed.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The tally, priced by the calculator that will price the record once it is stored.
     *
     * <p>Not a second copy of the arithmetic: the candidate is put through the real ledger
     * alongside everything already recorded, so what the player is shown before they commit is
     * by construction what the Profile tab shows afterwards. It also means a pick the ledger
     * would refuse - a trade another record got to first - prices as nothing here rather than
     * as a profit that never arrives.
     */
    private void price() {
        RecipeFlip candidate = buildFlip();
        RecipeFlipLedger.Activity activity = null;
        if (candidate != null) {
            List<RecipeFlip> all = new ArrayList<>(stored);
            all.add(candidate);
            List<RecipeFlipLedger.Activity> activities =
                RecipeFlipLedger.apply(trades, all).activities;
            if (activities.size() == appliedBefore + 1) {
                // Recorded last, so applied last, so appended last.
                activity = activities.get(activities.size() - 1);
            }
        }
        long cost = activity != null ? activity.costGp : 0L;
        long revenue = activity != null ? activity.revenueGp : 0L;
        long profit = revenue - cost;
        setValue(costValue, cost, TEXT);
        setValue(receivedValue, revenue, TEXT);
        setValue(taxValue, activity != null ? activity.taxGp : 0L, TEXT);
        setValue(profitValue, profit, activity == null ? TEXT : (profit >= 0 ? SUCCESS : DANGER));
        updateRecordButton();
    }

    private void setValue(JLabel label, long value, Color color) {
        label.setText(valueFormat.formatGp(value));
        label.setForeground(color);
    }

    private void updateRecordButton() {
        RecipeFlip flip = buildFlip();
        boolean ready = flip != null;
        String missing = pickedOn(buys).isEmpty()
            ? "Tick what went in."
            : pickedOn(sells).isEmpty() ? "Tick what came out." : "";
        blocker.setText(missing);
        blocker.setVisible(!missing.isEmpty());
        recordButton.setEnabled(ready);
        recordButton.setForeground(ready ? TEXT : MUTED_2);
        recordButton.setToolTipText(ready
            ? "File this as one activity against " + flip.name
            : "Pick at least one purchase and one sale");
    }

    private RecipeFlip buildFlip() {
        List<RecipeFlip.Part> inputs = partsOf(buys);
        List<RecipeFlip.Part> outputs = partsOf(sells);
        if (inputs.isEmpty() || outputs.isEmpty()) {
            return null;
        }
        ConversionKind kind = (ConversionKind) kindCombo.getSelectedItem();
        RecipeFlip flip = new RecipeFlip(
            kind != null ? kind : ConversionKind.ASSEMBLE,
            null,
            inputs,
            outputs,
            Math.max(0L, parseNumber(feeField.getText(), 0L)),
            System.currentTimeMillis());
        if (!flip.isUsable()) {
            return null;
        }
        flip.name = nameFor(flip);
        return flip;
    }

    private List<RecipeFlip.Part> partsOf(List<Candidate> candidates) {
        List<RecipeFlip.Part> parts = new ArrayList<>();
        for (Candidate candidate : pickedOn(candidates)) {
            parts.add(new RecipeFlip.Part(candidate.key, candidate.used));
        }
        return parts;
    }

    private static List<Candidate> pickedOn(List<Candidate> candidates) {
        List<Candidate> picked = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (candidate.picked()) {
                picked.add(candidate);
            }
        }
        return picked;
    }

    private void record() {
        RecipeFlip flip = buildFlip();
        RecipeFlipStore store = Bridge.get(RecipeFlipStore.class);
        if (flip == null || store == null || accountKey <= 0 || !store.add(accountKey, flip)) {
            return;
        }
        commit();
        close();
    }

    private void forget(RecipeFlip flip) {
        RecipeFlipStore store = Bridge.get(RecipeFlipStore.class);
        if (store == null || accountKey <= 0 || !store.remove(accountKey, flip)) {
            return;
        }
        commit();
        // Its trades are free again, so the screen is rebuilt rather than the one row redrawn.
        open();
    }

    /**
     * Get the change onto disk and into both ledgers.
     *
     * <p>Every aggregate is dropped rather than this account's alone, because the accountwide
     * ledger replays this account's trades too and would otherwise keep the old total.
     */
    private void commit() {
        Access.plugin().getLocalTradesRuntimeService().persistLocalTrades(accountKey);
        LocalStatsCacheService caches = Bridge.get(LocalStatsCacheService.class);
        if (caches != null) {
            caches.invalidateAll();
        }
        PanelRefresh refresh = Access.plugin().getPanelRefreshCoordinator();
        if (refresh != null) {
            refresh.triggerStatsRefresh(Access.plugin().scheduler);
        }
    }

    private void close() {
        if (onClose != null) {
            onClose.run();
        }
    }

    /**
     * Which account the record belongs to.
     *
     * <p>The logged-in character, not whichever profile the tab happens to be showing: a record
     * lives in one account's file and explains that account's trades. The accountwide view reads
     * every account's records, so one made here still counts there.
     *
     * <p>The same key {@link TradeDeltaRecorder} files the trades under, and it has to be. That
     * one is the account hash where there is one and a key made from the display name where
     * there is not - an account without a Jagex account attached has no hash, and reports -1.
     * Asking for the hash alone left this screen saying "log in" to a player who plainly was,
     * because their trades were all sitting under the other key.
     *
     * <p>Must be called on the client thread: it reads the game's own state, and on the way it
     * can merge two profiles that turn out to be one account.
     */
    private static long resolveAccountKey() {
        AccountSession session = Bridge.get(AccountSession.class);
        return session != null ? session.resolveLocalAccountKey() : -1L;
    }

    private static List<Delta> snapshotTrades(long accountKey) {
        TradeSession session = Bridge.get(TradeSession.class);
        if (session == null || accountKey <= 0) {
            return new ArrayList<>();
        }
        // The tab this was opened from may have been showing a different profile, or the
        // accountwide merge, in which case this account's own file has never been read. Cheap
        // once it has: the load is remembered, not repeated.
        Access.plugin().getLocalTradesRuntimeService().ensureProfileLoaded(accountKey);
        List<Delta> snapshot = session.snapshotLocalTradeDeltas(accountKey);
        return snapshot != null ? snapshot : new ArrayList<>();
    }

    /**
     * The name, if it has been looked up before.
     *
     * <p>Deliberately does not ask for one. Every candidate's name is read on every keystroke in
     * the search box, so asking here would post one job to the client thread per trade per
     * letter typed; only the rows actually drawn ask, through {@link #warmName(int)}.
     */
    private static String itemName(int itemId) {
        ItemLookup lookup = Bridge.get(ItemLookup.class);
        String name = lookup != null ? lookup.getCachedItemName(itemId) : null;
        return Str.hasText(name) ? name : "Item " + itemId;
    }

    /** Ask for a name this row needs, so the next draw has it. Filled on the client thread. */
    private static void warmName(int itemId) {
        ItemLookup lookup = Bridge.get(ItemLookup.class);
        if (lookup != null) {
            lookup.cacheItemName(itemId);
        }
    }

    private String age(long tsMs) {
        return tsMs <= 0
            ? "unknown"
            : valueFormat.formatDurationCompact(System.currentTimeMillis() - tsMs) + " ago";
    }

    /**
     * What to call the conversion: the item it is filed against.
     *
     * <p>There is no box to type this in, because there was never anything to say that the
     * picks do not already say. {@link RecipeFlip#subjectItemId()} decides which item that is -
     * what was made when one thing was made, what was taken apart otherwise - so the name is
     * read back off the finished record rather than worked out a second way here.
     */
    private static String nameFor(RecipeFlip flip) {
        int subject = flip.subjectItemId();
        return subject > 0 ? itemName(subject) : "";
    }

    /** Digits only, so a player who types "1,500,000" or "1500000 gp" is understood either way. */
    private static long parseNumber(String raw, long fallback) {
        if (raw == null) {
            return fallback;
        }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
            }
        }
        if (digits.length() == 0 || digits.length() > 18) {
            return fallback;
        }
        return Long.parseLong(digits.toString());
    }

    private void field(JTextField input, Runnable onChange) {
        uiStyler.styleTextField(input);
        input.setFont(uiStyler.font(10.5f));
        stretch(input);
        uiStyler.onEdit(input, onChange);
    }

    private static void stretch(JComponent control) {
        control.setAlignmentX(Component.LEFT_ALIGNMENT);
        control.setMaximumSize(new Dimension(Integer.MAX_VALUE, control.getPreferredSize().height));
    }

    /** The panel's search field, exactly as the Profile tab draws it: alone, and full width. */
    private JPanel searchRow() {
        JPanel row = new JPanel(new BorderLayout(TRAILING_CONTROL_GAP, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        uiStyler.styleTextField(findField);
        uiStyler.installInlineClear(findField);
        findField.setToolTipText("Narrow both lists to one item");
        uiStyler.onEdit(findField, this::refresh);
        row.add(findField, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    /**
     * How many of a side are ticked, as a figure rather than a word.
     *
     * <p>Two labels instead of one string, so the count can carry the eye while the word stays
     * in the heading ramp beside it - and so the pair cannot run into the heading on its left,
     * which "WHAT WENT IN4 PICKED" is what happens when they share one.
     */
    private JPanel pickedCount(JLabel number) {
        number.setFont(uiStyler.fontNumeric(9.5f));
        number.setForeground(MUTED_2);
        JLabel word = new JLabel("picked");
        uiStyler.styleMicroLabel(word, 9.5f);
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        row.add(number, BorderLayout.WEST);
        row.add(word, BorderLayout.EAST);
        return row;
    }

    private JPanel headingRow(String text, JComponent trailing) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        JLabel label = new JLabel(text);
        uiStyler.styleMicroLabel(label, 9.5f);
        row.add(label, BorderLayout.WEST);
        if (trailing != null) {
            row.add(trailing, BorderLayout.EAST);
        }
        return row;
    }

    private JPanel detailLine(String label, JLabel value) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        EllipsisLabel left = new EllipsisLabel(label);
        left.setForeground(MUTED);
        left.setFont(uiStyler.font(10f));
        value.setHorizontalAlignment(SwingConstants.RIGHT);
        value.setForeground(TEXT);
        value.setFont(uiStyler.fontNumeric(10.5f));
        row.add(left, BorderLayout.CENTER);
        row.add(value, BorderLayout.EAST);
        return row;
    }

    private JPanel wordRow(String text) {
        JLabel word = new JLabel(text);
        word.setForeground(MUTED_2);
        word.setFont(uiStyler.font(9.5f));
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));
        row.add(word, BorderLayout.WEST);
        return row;
    }

    /** The hairline between two rows of a well, with the row gap built into it. */
    private static JPanel rule() {
        JPanel line = new JPanel();
        line.setOpaque(false);
        line.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, LINE));
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 5));
        line.setPreferredSize(new Dimension(0, 5));
        return line;
    }

    private static JPanel column() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

}
