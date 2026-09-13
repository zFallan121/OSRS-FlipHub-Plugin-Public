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
import java.util.List;
import java.util.Locale;
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
    /** How many trades a side offers before the rest have to be searched for. */
    private static final int MAX_ROWS = 30;

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
    private final JTextField nameField = new PlaceholderTextField("What it was");
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

    private final List<Candidate> buys = new ArrayList<>();
    private final List<Candidate> sells = new ArrayList<>();
    private List<Delta> trades = new ArrayList<>();
    private List<RecipeFlip> stored = new ArrayList<>();
    private long accountKey = -1L;
    private int appliedBefore;
    private boolean nameEdited;

    RecipeRecorder(UiStyler uiStyler, PanelValueFormat valueFormat, Runnable onClose) {
        this.uiStyler = uiStyler;
        this.valueFormat = valueFormat;
        this.onClose = onClose;
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
        accountKey = resolveAccountKey();
        trades = snapshotTrades(accountKey);
        RecipeFlipStore store = Bridge.get(RecipeFlipStore.class);
        stored = store != null && accountKey > 0 ? store.applicable(accountKey) : new ArrayList<>();
        RecipeFlipLedger.Result already = RecipeFlipLedger.apply(trades, stored);
        appliedBefore = already.activities.size();

        buys.clear();
        sells.clear();
        // What a new conversion may claim is whatever no earlier record has claimed already, so
        // each trade is offered with its unspoken-for part and nothing more.
        List<Delta> free = already.remainingTrades(trades);
        if (free != null) {
            for (Delta trade : free) {
                if (trade == null || trade.deltaQty <= 0) {
                    continue;
                }
                (trade.isBuy ? buys : sells).add(new Candidate(trade));
            }
        }
        Comparator<Candidate> newestFirst = Comparator
            .comparingLong((Candidate candidate) -> candidate.trade.closedAtMs())
            .reversed();
        buys.sort(newestFirst);
        sells.sort(newestFirst);

        kindCombo.setSelectedItem(ConversionKind.ASSEMBLE);
        nameField.setText("");
        feeField.setText("");
        findField.setText("");
        nameEdited = false;
        scrollPane.getVerticalScrollBar().setValue(0);
        refresh();
    }

    private void build() {
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setAlignmentX(Component.LEFT_ALIGNMENT);

        content.add(headingRow("Record a recipe",
            actionLink("Cancel", "Leave without recording anything", this::close)));

        nothingToDo.setForeground(MUTED_2);
        nothingToDo.setFont(uiStyler.font(10.5f));
        nothingToDo.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(nothingToDo);

        content.add(form);
        content.add(Box.createVerticalStrut(10));
        content.add(storedSection);

        form.add(headingRow("What happened", null));
        uiStyler.styleComboBox(kindCombo);
        kindCombo.setBorder(uiStyler.roundedBorder(INPUT_ARC, CONTROL_BORDER, new Insets(2, 6, 2, 6)));
        stretch(kindCombo);
        kindCombo.addActionListener(event -> updateRecordButton());
        form.add(kindCombo);

        form.add(headingRow("Called it", null));
        field(nameField, () -> {
            nameEdited = true;
            updateRecordButton();
        });
        form.add(nameField);

        form.add(headingRow("Find a trade", null));
        field(findField, this::refresh);
        uiStyler.installInlineClear(findField);
        form.add(findField);

        form.add(headingRow("What went in", inputsCount));
        form.add(CardSection.of(inputsBody));
        form.add(headingRow("What came out", outputsCount));
        form.add(CardSection.of(outputsBody));

        form.add(headingRow("Fee", null));
        field(feeField, this::price);
        form.add(feeField);

        form.add(Box.createVerticalStrut(8));
        JPanel tally = column();
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
        nothingToDo.setText(accountKey <= 0
            ? "Log in to record a recipe."
            : "No trades of yours are left to build one from.");

        fillSide(inputsBody, buys, inputsCount);
        fillSide(outputsBody, sells, outputsCount);
        fillStored();
        autofillName();
        price();

        content.revalidate();
        content.repaint();
    }

    private void fillSide(JPanel body, List<Candidate> candidates, JLabel count) {
        body.removeAll();
        String query = findField.getText() != null
            ? findField.getText().trim().toLowerCase(Locale.US)
            : "";
        int picked = 0;
        int shown = 0;
        int hidden = 0;
        for (Candidate candidate : candidates) {
            if (candidate.picked()) {
                picked++;
            }
            boolean matches = query.isEmpty()
                || itemName(candidate.trade.itemId).toLowerCase(Locale.US).contains(query);
            // A pick is never hidden by the search or by the cap. Losing one off the end of the
            // list while its coins are still in the tally is how a record ends up naming trades
            // the player can no longer see.
            if (!candidate.picked() && (!matches || shown >= MAX_ROWS)) {
                if (matches) {
                    hidden++;
                }
                continue;
            }
            if (shown > 0) {
                body.add(rule());
            }
            body.add(tradeRow(candidate));
            shown++;
        }
        if (shown == 0) {
            body.add(wordRow(query.isEmpty() ? "Nothing left to pick." : "No trade by that name."));
        } else if (hidden > 0) {
            body.add(wordRow(hidden + " more - search to narrow it down."));
        }
        count.setText(picked + " picked");
        uiStyler.styleMicroLabel(count, 9.5f);
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
        onEdit(used, () -> {
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
        RecipeFlipLedger.Result applied = RecipeFlipLedger.apply(trades, stored);
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
        EllipsisLabel name = new EllipsisLabel(flip.name != null && !flip.name.trim().isEmpty()
            ? flip.name.trim()
            : itemName(flip.subjectItemId()));
        name.setForeground(applies ? TEXT : MUTED_2);
        name.setFont(uiStyler.font(9.5f));
        name.setHorizontalAlignment(SwingConstants.LEFT);
        top.add(name, BorderLayout.CENTER);
        top.add(actionLink("Forget", "Undo this record and give its trades back",
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
        boolean ready = buildFlip() != null && !name().isEmpty();
        recordButton.setEnabled(ready);
        recordButton.setForeground(ready ? TEXT : MUTED_2);
        recordButton.setToolTipText(ready
            ? "File this as one activity against " + name()
            : "Pick at least one purchase and one sale, and say what it was");
    }

    /** Names the conversion after what it sold as, until the player types one of their own. */
    private void autofillName() {
        if (nameEdited) {
            return;
        }
        List<Candidate> picked = pickedOn(sells);
        String suggested = picked.size() == 1 ? itemName(picked.get(0).trade.itemId) : "";
        if (!suggested.equals(name())) {
            nameField.setText(suggested);
            // setText is an edit like any other, so the flag it has just set comes back off.
            nameEdited = false;
        }
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
            name(),
            inputs,
            outputs,
            Math.max(0L, parseNumber(feeField.getText(), 0L)),
            System.currentTimeMillis());
        return flip.isUsable() ? flip : null;
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
     */
    private static long resolveAccountKey() {
        TradeSession session = Bridge.get(TradeSession.class);
        return session != null ? session.resolveAccountHash() : -1L;
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

    private static String itemName(int itemId) {
        ItemLookup lookup = Bridge.get(ItemLookup.class);
        String name = lookup != null ? lookup.getCachedItemName(itemId) : null;
        if (name != null && !name.trim().isEmpty()) {
            return name;
        }
        if (lookup != null) {
            // Asked for now so the next draw has it; the cache is filled on the client thread.
            lookup.cacheItemName(itemId);
        }
        return "Item " + itemId;
    }

    private String age(long tsMs) {
        return tsMs <= 0
            ? "unknown"
            : valueFormat.formatDurationCompact(System.currentTimeMillis() - tsMs) + " ago";
    }

    private String name() {
        return nameField.getText() != null ? nameField.getText().trim() : "";
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
        onEdit(input, onChange);
    }

    private static void stretch(JComponent control) {
        control.setAlignmentX(Component.LEFT_ALIGNMENT);
        control.setMaximumSize(new Dimension(Integer.MAX_VALUE, control.getPreferredSize().height));
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

    /** A word that does something, in the panel's one action colour and with no box of its own. */
    private JLabel actionLink(String text, String tooltip, Runnable action) {
        JLabel link = new JLabel(text, SwingConstants.RIGHT);
        link.setForeground(ACCENT);
        link.setFont(uiStyler.font(9.5f));
        link.setToolTipText(tooltip);
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.addMouseListener(new StatsClickMouseAdapter(action));
        // Under the pointer the word goes to plain text: the action colour says "this does
        // something", white says "this one, the one you are on".
        link.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent event) {
                link.setForeground(TEXT);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent event) {
                link.setForeground(ACCENT);
            }
        });
        return link;
    }

    private static void onEdit(JTextField input, Runnable onChange) {
        input.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent event) {
                onChange.run();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent event) {
                onChange.run();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent event) {
                onChange.run();
            }
        });
    }
}
