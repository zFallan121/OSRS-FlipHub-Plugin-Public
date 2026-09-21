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

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import static com.osrsfliphub.Skin.*;

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
    /** How many trades a page shows. The rest are a page away. */
    private static final int PAGE_SIZE = 10;

    /** One trade's row, and the line inside it the name sits on. Neither ever changes. */
    private static final int ROW_HEIGHT = 36;
    private static final int NAME_HEIGHT = 20;

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
    private final JPanel tradesBody = new Column();
    private final JPanel storedBody = new Column();
    private final JLabel inCount = new JLabel();
    private final JLabel outCount = new JLabel();
    private final JLabel costValue = new JLabel();
    private final JLabel receivedValue = new JLabel();
    private final JLabel taxValue = new JLabel();
    private final JLabel profitValue = new JLabel();
    private final JButton recordButton = new TipButton("Record");
    private final JPanel form = new Column();
    private final JPanel storedSection = new Column();
    private final JLabel nothingToDo = new Line();
    private final StatsPagerBuilder pager;

    private final List<Candidate> picks = new ArrayList<>();
    private List<Delta> trades = new ArrayList<>();
    private List<RecipeFlip> stored = new ArrayList<>();
    /** What the stored records claim of those trades. Only changes when a record does. */
    private RecipeFlipLedger.Result applied = RecipeFlipLedger.empty();
    private long accountKey = -1L;
    private int appliedBefore;
    private boolean waiting;
    private int page = 1;

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
        picks.clear();
        kindCombo.setSelectedItem(ConversionKind.ASSEMBLE);
        feeField.setText("");
        findField.setText("");
        page = 1;
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

        picks.clear();
        for (Delta trade : offerable(trades, applied)) {
            picks.add(new Candidate(trade));
        }
        warmNames();
        refresh();
    }

    /**
     * Ask the game for every name on the list, not only the ten being drawn.
     *
     * <p>A name nothing has looked up yet reads as "Item 4151", and the search matches on what
     * a row says - so it could only ever find the items the player had already paged past,
     * which is the opposite of what a search is for. The names land on the game's thread one
     * hop behind this one, so the redraw is asked for on a hop of its own, which puts it behind
     * all of them. Names already known cost nothing.
     */
    private void warmNames() {
        GeLifecyclePlugin plugin = Access.pluginOrNull();
        if (plugin == null) {
            return;
        }
        for (Candidate candidate : picks) {
            warmName(candidate.trade.itemId);
        }
        plugin.invokeOnClientThread(() -> SwingUtilities.invokeLater(this::refresh));
    }

    private void build() {
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(headingRow("Record a recipe",
            uiStyler.actionLink("Cancel", "Leave without recording", this::close)));

        nothingToDo.setForeground(MUTED_2);
        nothingToDo.setFont(uiStyler.font(10.5f));
        nothingToDo.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        content.add(nothingToDo);

        content.add(form);
        content.add(Box.createVerticalStrut(10));
        content.add(storedSection);

        // The heading over this said only what the dropdown itself says. What it also did
        // was hold the dropdown off the title above it, so the room stays and the words go.
        form.add(Box.createVerticalStrut(18));
        uiStyler.styleComboBox(kindCombo);
        kindCombo.setBorder(uiStyler.roundedBorder(INPUT_ARC, CONTROL_BORDER, new Insets(2, 6, 2, 6)));
        wide(kindCombo, kindCombo.getPreferredSize().height);
        kindCombo.addActionListener(event -> updateRecordButton());
        form.add(kindCombo);

        // The same search the rest of the panel uses: the field alone, at full width, with the
        // clear mark inside its own right edge. No heading over it - the placeholder says what
        // it is, and the row it sits in is the one the Profile tab draws.
        form.add(Box.createVerticalStrut(8));
        form.add(searchRow());
        form.add(Box.createVerticalStrut(10));

        form.add(headingRow("Your trades", picked()));
        form.add(CardSection.of(tradesBody));

        form.add(Box.createVerticalStrut(8));
        form.add(headingRow("Fee (if any)", null));
        field(feeField, this::price);
        feeField.setToolTipText("What the recipe itself cost");
        form.add(feeField);

        form.add(Box.createVerticalStrut(8));
        JPanel tally = new Column();
        tally.add(detailLine("Cost", costValue));
        tally.add(detailLine("Received", receivedValue));
        tally.add(detailLine("Tax", taxValue));
        tally.add(detailLine("Profit", profitValue));
        form.add(CardSection.of(tally));
        form.add(Box.createVerticalStrut(8));

        // A ghost, like every other control in the panel. This is the first thing in the panel
        // that commits anything, so it is also the first that could have argued for a filled
        // one - but a second kind of button would be a new sort of object in a panel that has
        // exactly one, and the tally above it is what says the action is ready.
        uiStyler.styleGhostControl(recordButton, 11.5f, new Insets(6, 12, 6, 12));
        wide(recordButton, recordButton.getPreferredSize().height);
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

    /** Redraw the trade list, the tally and the recorded list from the current picks. */
    private void refresh() {
        boolean hasTrades = !picks.isEmpty();
        form.setVisible(hasTrades);
        nothingToDo.setVisible(!hasTrades);
        nothingToDo.setText(waiting
            ? "Reading your trades..."
            : accountKey <= 0
                ? "Log in to record a recipe."
                : "No finished trades of yours are left to build one from.");

        fillTrades();
        fillStored();
        price();

        content.revalidate();
        content.repaint();
    }

    /**
     * Every trade on offer, in one list, ten rows to a page.
     *
     * <p>One list and not one per side. The game already knows which side a trade is on, so
     * sorting them into two lists asked the player to do again what had been done for them
     * already, and cost the screen two of everything - two headings, two wells, two pagers, in
     * a column narrow enough that the pair read as one table printed over the other. Worse, it
     * put the sale that finished a recipe thirty rows below the purchases that went into it
     * when the two had happened minutes apart. In one list, in the order they happened, a
     * recipe's trades sit beside each other, which is where the player looks for them.
     *
     * <p>Ten rows, because a thousand-trade history rendered whole is a screen nobody can
     * record anything from.
     *
     * <p>Nothing moves when it is ticked. Sorting the ticked ones to the front put every pick
     * on the first page, which sounds useful until you are ticking four things in a row: each
     * tick shuffles the list under the cursor and the next thing you meant to tick is no longer
     * where you were looking at it. Keeping the order fixed costs a pick the chance of being
     * paged away from, and the two figures beside the heading are there to say how many are
     * ticked when their rows are not on screen.
     *
     * <p>The search narrows what is still pickable and never what is already picked, for that
     * same reason - typing a name must not take a tick off the screen while its money stays in
     * the tally.
     */
    private void fillTrades() {
        tradesBody.removeAll();
        String query = findField.getText() != null
            ? findField.getText().trim().toLowerCase(Locale.US)
            : "";
        List<Candidate> ordered = new ArrayList<>();
        int bought = 0;
        int sold = 0;
        for (Candidate candidate : picks) {
            if (candidate.picked()) {
                if (candidate.trade.isBuy) {
                    bought++;
                } else {
                    sold++;
                }
            } else if (!query.isEmpty()
                && !itemName(candidate.trade.itemId).toLowerCase(Locale.US).contains(query)) {
                continue;
            }
            ordered.add(candidate);
        }

        int pages = Math.max(1, (ordered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(Math.max(1, page), pages);
        int from = (page - 1) * PAGE_SIZE;

        List<Candidate> shown =
            ordered.subList(from, Math.min(ordered.size(), from + PAGE_SIZE));
        boolean first = true;
        for (Candidate candidate : shown) {
            if (!first) {
                tradesBody.add(rule());
            }
            first = false;
            tradesBody.add(tradeRow(candidate));
        }
        if (shown.isEmpty()) {
            tradesBody.add(wordRow(query.isEmpty()
                ? "Nothing left to pick."
                : "No trade by that name."));
        }
        if (pages > 1) {
            // Built for a list that lines its rows up against the left edge, where this one
            // does not; the column it is going into settles that. Left to disagree, the pager
            // gets handed half the width, which is enough to wrap its Older button onto a
            // second line and off the bottom of its own row. See Column.
            tradesBody.add(pager.buildPager(page, pages, wanted -> {
                page = wanted;
                refresh();
            }));
        }
        figure(inCount, bought);
        figure(outCount, sold);
    }

    /** A count beside the heading, lit only when there is something to count. */
    private void figure(JLabel count, int value) {
        count.setText(String.valueOf(value));
        count.setForeground(value == 0 ? MUTED_2 : ACCENT);
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
        JPanel row = plain(new BorderLayout(6, 0));
        // Fixed, and the same whether the row is ticked or not. Ticking a purchase of more than
        // one swaps its quantity for a box to type in, which is taller than the figure it
        // replaces - so the row would grow and shove everything under it down the screen. Every
        // row is already the height of the taller of the two.
        row.setPreferredSize(new Dimension(0, ROW_HEIGHT));
        wide(row, ROW_HEIGHT);

        JLabel mark = new JLabel(new PickIcon(PICK_MARK_SIZE, candidate.picked()));
        mark.setVerticalAlignment(SwingConstants.TOP);
        mark.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));

        JPanel middle = new Column();
        JPanel top = plain(new BorderLayout(6, 0));
        top.setPreferredSize(new Dimension(0, NAME_HEIGHT));
        wide(top, NAME_HEIGHT);
        warmName(candidate.trade.itemId);
        EllipsisLabel name = styled(new EllipsisLabel(itemName(candidate.trade.itemId)),
            candidate.picked() ? TEXT : MUTED, uiStyler.font(9.5f));
        name.setHorizontalAlignment(SwingConstants.LEFT);
        top.add(name, BorderLayout.CENTER);
        top.add(split ? quantityField(candidate) : quantityLabel(candidate), BorderLayout.EAST);
        middle.add(top);

        // Which side a trade is on decides which half of the recipe it lands in. It used to be
        // said by which of two lists the row was in; with one list it is said here, and in the
        // two colours the panel already spends on coins leaving and coins arriving.
        JLabel detail = new Line("<html><font color='"
            + toHex(candidate.trade.isBuy ? DANGER : SUCCESS) + "'>"
            + (candidate.trade.isBuy ? "Bought" : "Sold") + "</font> · "
            + valueFormat.formatGpCompact(candidate.trade.deltaGp)
            + " · " + age(candidate.trade.closedAtMs()) + "</html>");
        detail.setForeground(MUTED_2);
        detail.setFont(uiStyler.font(9.5f));
        middle.add(detail);

        row.add(mark, BorderLayout.WEST);
        row.add(middle, BorderLayout.CENTER);
        row.setCursor(HAND);
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
        JLabel quantity = styled(new JLabel(String.valueOf(candidate.available), SwingConstants.RIGHT),
            MUTED_2, uiStyler.fontNumeric(9.5f));
        return quantity;
    }

    private JComponent quantityField(Candidate candidate) {
        JTextField used = new JTextField(String.valueOf(candidate.used));
        uiStyler.styleTextField(used);
        used.setFont(uiStyler.fontNumeric(9.5f));
        used.setHorizontalAlignment(SwingConstants.RIGHT);
        used.setBorder(uiStyler.roundedBorder(INPUT_ARC, CONTROL_BORDER, new Insets(1, 4, 1, 4)));
        used.setPreferredSize(new Dimension(QUANTITY_FIELD_WIDTH, NAME_HEIGHT - 2));
        uiStyler.onEdit(used, () -> {
            // Clamped rather than refused: an empty box while the player retypes must not drop
            // the pick out from under them, and more than they bought is a typo, not a claim.
            candidate.used = (int) Math.max(1L,
                Math.min(candidate.available, parseNumber(used.getText(), 1L)));
            price();
        });
        // Typing is left alone while it is happening, because correcting a half-written number
        // under the cursor is worse than showing one that is briefly wrong. The box is squared
        // with what is actually being counted once the player leaves it, so it cannot sit there
        // reading 9999 against four that were bought.
        used.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                used.setText(String.valueOf(candidate.used));
            }
        });

        JLabel total = new JLabel("/ " + candidate.available);
        total.setForeground(MUTED_2);
        total.setFont(uiStyler.fontNumeric(9.5f));

        JPanel holder = plain(new BorderLayout(3, 0));
        holder.add(used, BorderLayout.CENTER);
        holder.add(total, BorderLayout.EAST);
        holder.setPreferredSize(new Dimension(
            QUANTITY_FIELD_WIDTH + 3 + total.getPreferredSize().width, NAME_HEIGHT - 2));
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
        JPanel row = plain(new BorderLayout(6, 0));
        wide(row, 32);

        JPanel top = plain(new BorderLayout(6, 0));
        wide(top, 16);
        EllipsisLabel name = new EllipsisLabel(Str.hasText(flip.name)
            ? flip.name.trim()
            : itemName(flip.subjectItemId()));
        warmName(flip.subjectItemId());
        name.setForeground(applies ? TEXT : MUTED_2);
        name.setFont(uiStyler.font(9.5f));
        name.setHorizontalAlignment(SwingConstants.LEFT);
        top.add(name, BorderLayout.CENTER);
        top.add(uiStyler.actionLink("Forget", "Undo this record",
            () -> forget(flip)), BorderLayout.EAST);

        // The one thing the player could not otherwise find out. A record whose trades have
        // gone - a wiped history, a re-synced one - stops applying by itself and quietly takes
        // its profit back out of every total, with nothing anywhere saying that it happened.
        JLabel state = new Line(applies
            ? String.valueOf(flip.kind)
            : flip.kind + " · its trades are gone");
        state.setForeground(applies ? MUTED_2 : WARNING);
        state.setFont(uiStyler.font(9.5f));

        JPanel middle = new Column();
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
        recordButton.setEnabled(ready);
        recordButton.setForeground(ready ? TEXT : MUTED_2);
        recordButton.setToolTipText(ready
            ? "Record as one activity"
            : "Tick a purchase and a sale");
    }

    private RecipeFlip buildFlip() {
        List<RecipeFlip.Part> inputs = partsOf(true);
        List<RecipeFlip.Part> outputs = partsOf(false);
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

    /** The ticked purchases, or the ticked sales. Which side a trade is on is the game's. */
    private List<RecipeFlip.Part> partsOf(boolean bought) {
        List<RecipeFlip.Part> parts = new ArrayList<>();
        for (Candidate candidate : picks) {
            if (candidate.picked() && candidate.trade.isBuy == bought) {
                parts.add(new RecipeFlip.Part(candidate.key, candidate.used));
            }
        }
        return parts;
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
        GeLifecyclePlugin plugin = Access.pluginOrNull();
        if (plugin == null) {
            return;
        }
        plugin.getLocalTradesRuntimeService().persistLocalTrades(accountKey);
        LocalStatsCacheService caches = Bridge.get(LocalStatsCacheService.class);
        if (caches != null) {
            caches.invalidateAll();
        }
        PanelRefresh refresh = plugin.getPanelRefreshCoordinator();
        if (refresh != null) {
            refresh.triggerStatsRefresh(plugin.scheduler);
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
        wide(input, input.getPreferredSize().height);
        uiStyler.onEdit(input, onChange);
    }

    /** The panel's search field, exactly as the Profile tab draws it: alone, and full width. */
    private JPanel searchRow() {
        JPanel row = plain(new BorderLayout(TRAILING_CONTROL_GAP, 0));
        uiStyler.styleTextField(findField);
        uiStyler.installInlineClear(findField);
        findField.setToolTipText("Find one item");
        // Back to the first page: a search answered on page four is a search answered out
        // of sight.
        uiStyler.onEdit(findField, () -> {
            page = 1;
            refresh();
        });
        row.add(findField, BorderLayout.CENTER);
        wide(row, row.getPreferredSize().height);
        return row;
    }

    /**
     * How many trades are ticked on each side, as two figures beside the heading.
     *
     * <p>This is also the only thing that says why Record will not go. A recipe needs something
     * bought and something sold, and a nought against one of them says so in the place the
     * player is already looking - which a line of instructions under the button did not, and
     * which the colour on the figure does without spending any words at all.
     */
    private JPanel picked() {
        inCount.setFont(uiStyler.fontNumeric(9.5f));
        outCount.setFont(uiStyler.fontNumeric(9.5f));
        JPanel row = plain(new FlowLayout(FlowLayout.RIGHT, 3, 0));
        row.add(inCount);
        row.add(micro("in"));
        row.add(micro("·"));
        row.add(outCount);
        row.add(micro("out"));
        return row;
    }

    private JLabel micro(String text) {
        JLabel label = new JLabel(text);
        uiStyler.styleMicroLabel(label, 9.5f);
        return label;
    }

    private JPanel headingRow(String text, JComponent trailing) {
        JPanel row = plain(new BorderLayout(6, 0));
        wide(row, 18);
        row.add(micro(text), BorderLayout.WEST);
        if (trailing != null) {
            row.add(trailing, BorderLayout.EAST);
        }
        return row;
    }

    private JPanel detailLine(String label, JLabel value) {
        JPanel row = plain(new BorderLayout());
        wide(row, 18);
        EllipsisLabel left = styled(new EllipsisLabel(label), MUTED, uiStyler.font(10f));
        value.setHorizontalAlignment(SwingConstants.RIGHT);
        value.setForeground(TEXT);
        value.setFont(uiStyler.fontNumeric(10.5f));
        row.add(left, BorderLayout.CENTER);
        row.add(value, BorderLayout.EAST);
        return row;
    }

    private JPanel wordRow(String text) {
        JLabel word = styled(new JLabel(text), MUTED_2, uiStyler.font(9.5f));
        JPanel row = plain(new BorderLayout());
        wide(row, 14);
        row.add(word, BorderLayout.WEST);
        return row;
    }

    /** The hairline between two rows of a well, with the row gap built into it. */
    private static JPanel rule() {
        JPanel line = new JPanel();
        line.setOpaque(false);
        line.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, LINE));
        wide(line, 5);
        line.setPreferredSize(new Dimension(0, 5));
        return line;
    }

    /**
     * A line of text that takes the whole width it is offered, not just the width of its words.
     *
     * <p>A label can only ever be as wide as what it says, and a stack of rows centres a row
     * that cannot grow - so a label dropped into one arrives in the middle, while every other
     * line of text in the panel starts at the left edge. Given the room, a label puts its words
     * on the left by itself.
     */
    private static final class Line extends JLabel {
        Line() {
        }

        Line(String text) {
            super(text);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    /**
     * A stack of rows, every one of them the full width of the stack.
     *
     * <p>A stack of this kind lines its rows up on their alignment points, so a row that asks
     * to sit against the left edge standing next to a block that asks to be centred cannot have
     * both. The stack widens to hold the two demands at once and hands each row a fraction of
     * itself: rows come out half width and shoved into the right of the panel, and a row drawn
     * left-and-right in that space puts its two ends on top of one another - which is how a
     * heading shipped reading "WHAT WENT IN4PICKED", a search field shipped at half length, and
     * a pager shipped with its Older button wrapped off the bottom of its own row.
     *
     * <p>Nothing in the panel makes that visible, because no row is wrong on its own; it is
     * only wrong next to its neighbours. Rather than trust every row to be written the same way
     * - a default is not the same thing as a decision, and a plain label does not carry the same
     * one a plain panel does - the stack settles it for them as they go in.
     */
    private static final class Column extends JPanel {
        Column() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setAlignmentX(CENTER_ALIGNMENT);
        }

        @Override
        protected void addImpl(Component child, Object constraints, int index) {
            if (child instanceof JComponent) {
                ((JComponent) child).setAlignmentX(CENTER_ALIGNMENT);
            }
            super.addImpl(child, constraints, index);
        }
    }

}
