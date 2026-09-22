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
import java.io.InputStream;
import java.util.*;
import java.util.List;
import javax.swing.*;
import net.runelite.api.Skill;
import static com.osrsfliphub.Skin.*;

/**
 * The screen where the player tells the plugin that some of their trades were one conversion.
 *
 * <p>The game never says two items were combined, so this is the only way the fact can reach the
 * plugin. It is deliberately a statement rather than a question: pick the purchases that went in,
 * pick the sales the result went out through, and the record names those exact trades for good.
 * Nothing here is inferred, and nothing here is checked against a recipe table, because there is
 * no longer a recipe table to check against. The one thing it fills in is a repair's fee, and that
 * sits in the box in plain sight to be typed over. The running tally at the bottom is what catches a
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

    /**
     * What an NPC charges to repair each broken item this screen can price, by item id: the
     * Barrows and Moons of Peril pieces, whose repair is a fixed price in coins. See the file.
     */
    private static final Properties REPAIRS = new Properties();

    static {
        try (InputStream in = RecipeRecorder.class.getResourceAsStream("repairs.properties")) {
            REPAIRS.load(in);
        } catch (Exception ignored) {
            // Without the list a repair's fee is typed by hand, which is all it ever was.
        }
    }

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
    /**
     * What a recipe did. A move is not one of them: it has its own way in (the Move link) and its
     * own screen, and offered here as a sixth kind of recipe it was a second, older way to it.
     */
    private final JComboBox<ConversionKind> kindCombo = new JComboBox<>(java.util.Arrays.stream(ConversionKind.values())
        .filter(kind -> kind != ConversionKind.TRANSFER).toArray(ConversionKind[]::new));
    /** Whether this is the move screen, as it was opened. */
    private boolean moving;
    /**
     * Where the stock went, for a move and only then: the player's other accounts that have
     * logged in on this computer, and beside it the key each name stands for.
     */
    private final JComboBox<String> toCombo = new JComboBox<>();
    private final List<Long> toKeys = new ArrayList<>();
    /** The fee and the tally. A move has neither: nothing was made, and nothing is sold here. */
    private final JPanel recipeOnly = new Column();
    private final JTextField feeField = new PlaceholderTextField("0");
    /** Beside the fee's heading while the fee is one this screen filled in, saying how it priced it. */
    private final JLabel feeNote = new JLabel();
    /** The last fee this screen filled in, which is how it tells that from one the player typed. */
    private String suggested = "";
    /**
     * The Smithing level a repair is priced at: the player's own when they repair on a house
     * armour stand, 0 for an NPC's full price. Read when the screen opens, as the account is.
     */
    private int standLevel;
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
    /** The screen's title, which says which of the two it is recording. */
    private JLabel title;
    /**
     * What was just recorded. Without it a record left no trace the player could see: the
     * screen closed, and the Profile tab has nothing to show for stock that has not sold yet.
     */
    private final JLabel notice = new Line();
    /** Purchases other accounts moved to this one, which are this account's to use now. */
    private final Set<TradeKey> received = new HashSet<>();
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
     *
     * @param move whether it opens set to record stock moved to an alt, rather than a recipe
     */
    void open(boolean move) {
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
        notice.setVisible(false);
        moving = move;
        kindCombo.setSelectedItem(ConversionKind.ASSEMBLE);
        feeField.setText("");
        suggested = "";
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
            // The level now, not when the repair was done - the game keeps no record of that.
            int level = plugin.config.repairAtArmourStand() ? plugin.client.getRealSkillLevel(Skill.SMITHING) : 0;
            SwingUtilities.invokeLater(() -> {
                standLevel = level;
                openFor(key);
            });
        });
    }

    /** The screen, once the character it is about is known. */
    private void openFor(long key) {
        waiting = false;
        accountKey = key;
        trades = snapshotTrades(key);
        RecipeFlipStore store = Bridge.get(RecipeFlipStore.class);
        stored = store != null && key > 0 ? store.applicable(key) : new ArrayList<>();
        // What another account moved to this one is offered beside this account's own trades,
        // because it is this account's now: to sell, to move on again, or to make something of.
        received.clear();
        if (store != null && key > 0) {
            for (Delta gift : RecipeFlipLedger.received(store, key, new HashSet<>())) {
                received.add(TradeKey.of(gift));
                trades.add(gift);
            }
        }
        applied = RecipeFlipLedger.apply(trades, stored);
        appliedBefore = applied.activities.size();

        // Asked of the disk and not of what happens to be loaded: an account can only be picked
        // if this computer has a file for it, and that is exactly the set the move can reach.
        // The account already picked stays picked. The screen is read again after every Record,
        // and a rebuilt list falls back to its first entry: with two alts, the second move of
        // an evening went to the other one unless the player thought to look.
        int was = toCombo.getSelectedIndex();
        Long picked = was >= 0 && was < toKeys.size() ? toKeys.get(was) : null;
        toCombo.removeAllItems();
        toKeys.clear();
        ProfileCatalog catalog = Bridge.get(ProfileCatalog.class);
        if (catalog != null) {
            catalog.listed(Bridge.get(PluginState.class).getProfileDisplayNames()).forEach((account, name) -> {
                if (account != key) {
                    toKeys.add(account);
                    toCombo.addItem(name);
                }
            });
        }
        if (toKeys.contains(picked)) {
            toCombo.setSelectedIndex(toKeys.indexOf(picked));
        }

        picks.clear();
        for (Delta trade : offerable(trades, applied, received)) {
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

        title = micro("Record a recipe");
        // "Close" and not "Cancel": after a record the screen stays up to show it, and a word that
        // means "undo" beside something just recorded reads as the way to take it back.
        content.add(headingRow(title,
            uiStyler.actionLink("Close", "Back to the Profile tab", this::close)));

        for (JLabel line : new JLabel[] {notice, nothingToDo}) {
            line.setForeground(MUTED_2);
            line.setFont(uiStyler.font(10.5f));
            line.setAlignmentX(JComponent.CENTER_ALIGNMENT);
            content.add(line);
        }
        notice.setForeground(SUCCESS);

        content.add(form);
        content.add(Box.createVerticalStrut(10));
        content.add(storedSection);

        // The heading over this said only what the dropdown itself says. What it also did
        // was hold the dropdown off the title above it, so the room stays and the words go.
        form.add(Box.createVerticalStrut(18));
        for (JComboBox<?> combo : new JComboBox<?>[] {kindCombo, toCombo}) {
            uiStyler.styleComboBox(combo);
            combo.setBorder(uiStyler.roundedBorder(INPUT_ARC, CONTROL_BORDER, new Insets(2, 6, 2, 6)));
            wide(combo, kindCombo.getPreferredSize().height);
            // The whole screen, not the button alone: a move shows the account it went to and
            // hides the fee, the tally and every sale.
            combo.addActionListener(event -> refresh());
        }
        form.add(kindCombo);

        // The same search the rest of the panel uses: the field alone, at full width, with the
        // clear mark inside its own right edge. No heading over it - the placeholder says what
        // it is, and the row it sits in is the one the Profile tab draws.
        //
        // The gap above it is in two halves with the account picker between them. A hidden
        // component takes no room, so while recording a recipe the two halves are the one gap
        // that was always here.
        form.add(Box.createVerticalStrut(4));
        toCombo.setToolTipText("The account that sold them. It has to have logged in on this computer");
        form.add(toCombo);
        form.add(Box.createVerticalStrut(4));
        form.add(searchRow());
        form.add(Box.createVerticalStrut(10));

        form.add(headingRow(micro("Your trades"), picked()));
        form.add(CardSection.of(tradesBody));

        form.add(Box.createVerticalStrut(8));
        uiStyler.styleMicroLabel(feeNote, 9.5f);
        // In from the edge by what the counts over the trades are, which the last figure needs:
        // flush against it, the 9 of "Smithing 99" lost its right side.
        feeNote.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 3));
        feeNote.setToolTipText("Filled in for you. Type over it if you paid something else");
        recipeOnly.add(headingRow(micro("Fee (if any)"), feeNote));
        field(feeField, this::price);
        feeField.setToolTipText("What the recipe itself cost");
        recipeOnly.add(feeField);

        recipeOnly.add(Box.createVerticalStrut(8));
        JPanel tally = new Column();
        tally.add(detailLine("Cost", costValue));
        tally.add(detailLine("Received", receivedValue));
        tally.add(detailLine("Tax", taxValue));
        tally.add(detailLine("Profit", profitValue));
        recipeOnly.add(CardSection.of(tally));
        recipeOnly.add(Box.createVerticalStrut(8));
        form.add(recipeOnly);

        // A ghost, like every other control in the panel. This is the first thing in the panel
        // that commits anything, so it is also the first that could have argued for a filled
        // one - but a second kind of button would be a new sort of object in a panel that has
        // exactly one, and the tally above it is what says the action is ready.
        uiStyler.styleGhostControl(recordButton, 11.5f, new Insets(6, 12, 6, 12));
        wide(recordButton, recordButton.getPreferredSize().height);
        recordButton.addActionListener(event -> record());
        form.add(recordButton);

        storedSection.add(headingRow(micro("Recorded"), null));
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
        toCombo.setVisible(move());
        kindCombo.setVisible(!move());
        recipeOnly.setVisible(!move());
        // Upper case written out: the heading style capitalises a label once, when it is styled.
        title.setText(move() ? "RECORD A MOVE" : "RECORD A RECIPE");
        recordButton.setText(move() ? "Record Transfer" : "Record");

        fillTrades();
        fillStored();
        suggestFee();
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
            if (move() && !candidate.trade.isBuy) {
                // A move names what was bought here. What it sold for is the other account's.
                continue;
            }
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
            + (!candidate.trade.isBuy ? "Sold" : received.contains(candidate.key) ? "Received" : "Bought")
            + "</font> · "
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
    static List<Delta> offerable(List<Delta> trades, RecipeFlipLedger.Result applied, Set<TradeKey> received) {
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
        // What another account handed over goes first. It is dated when that account bought it, so
        // by date alone it could be pages down, and it is usually why this screen was opened.
        out.sort(Comparator.comparing(trade -> !received.contains(TradeKey.of(trade))));
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
        // Wide enough for the whole purchase. At a fixed width 11,000 logs read as "1100(": the
        // box cut the last digit, which is a player being told they are moving a tenth of them.
        int width = Math.max(QUANTITY_FIELD_WIDTH,
            used.getFontMetrics(used.getFont()).stringWidth(String.valueOf(candidate.available)) + 12);
        used.setPreferredSize(new Dimension(width, NAME_HEIGHT - 2));
        uiStyler.onEdit(used, () -> {
            // Clamped rather than refused: an empty box while the player retypes must not drop
            // the pick out from under them, and more than they bought is a typo, not a claim.
            candidate.used = (int) Math.max(1L,
                Math.min(candidate.available, parseNumber(used.getText(), 1L)));
            suggestFee();
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
            width + 3 + total.getPreferredSize().width, NAME_HEIGHT - 2));
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
        feeNote.setVisible(!suggested.isEmpty() && suggested.equals(feeField.getText()));
        updateRecordButton();
    }

    /**
     * Fill in what a repair cost, where the screen can know it.
     *
     * <p>It can for a Barrows or Moons of Peril piece: an NPC's fixed price, less the armour
     * stand's half a percent per Smithing level for a player who repairs on one - a little over
     * half price at 99, so 45,450 for a Dharok's platebody the NPC would charge 90,000 for. Left
     * blank, as it was before this, the repair counted as free.
     *
     * <p>A number the player typed is theirs and is left alone. One this filled in follows the
     * ticks, and goes when the kind stops being a repair, because an assembly's fee is nothing to
     * do with it. An empty box is not a number anybody typed; a free repair is typed as 0.
     *
     * <p>Never called from the fee box's own listener: a box cannot be written to while it is
     * telling its listeners about a change.
     */
    private void suggestFee() {
        long fee = 0L;
        for (Candidate candidate : picks) {
            if (candidate.picked() && candidate.trade.isBuy) {
                fee += repairFee(candidate.trade.itemId, candidate.used, standLevel);
            }
        }
        String text = fee > 0 && kindCombo.getSelectedItem() == ConversionKind.REPAIR
            ? String.format(Locale.US, "%,d", fee)
            : "";
        String typed = feeField.getText();
        if ((typed.isEmpty() || typed.equals(suggested)) && !typed.equals(text)) {
            feeField.setText(text);
        }
        suggested = text;
        // Upper case written out: the heading style capitalises a label once, when it is styled.
        feeNote.setText(standLevel > 0 ? "STAND · SMITHING " + standLevel : "NPC PRICE");
    }

    /**
     * What an NPC charges to repair this many of a broken item, less half a percent per level of
     * {@code standLevel}. 0 for anything not in {@link #REPAIRS}.
     */
    static long repairFee(int itemId, int quantity, int standLevel) {
        long price = Long.parseLong(REPAIRS.getProperty(String.valueOf(itemId), "0"));
        return price * (200 - standLevel) / 200 * quantity;
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
            : picks.stream().filter(Candidate::picked).count() > 64 ? "At most 64 trades in one record"
            : move() ? "Tick a purchase and pick the account" : "Tick a purchase and a sale");
    }

    /** Whether what is being recorded is stock handed to another account, not a recipe. */
    private boolean move() {
        return moving;
    }

    /**
     * The record the ticks describe, or null while they do not describe one. What makes one -
     * a purchase and a sale for a recipe, a purchase and an account for a move - is
     * {@link RecipeFlip#isUsable()}'s to say, and is not said a second time here.
     */
    private RecipeFlip buildFlip() {
        ConversionKind kind = move() ? ConversionKind.TRANSFER : (ConversionKind) kindCombo.getSelectedItem();
        int to = toCombo.getSelectedIndex();
        RecipeFlip flip = new RecipeFlip(
            kind != null ? kind : ConversionKind.ASSEMBLE,
            null,
            partsOf(true),
            // A sale ticked before the player chose to record a move is not part of it.
            move() ? null : partsOf(false),
            move() ? 0L : Math.max(0L, parseNumber(feeField.getText(), 0L)),
            System.currentTimeMillis(),
            move() && to >= 0 ? toKeys.get(to) : null, null);
        // The website takes at most 64 trades in one record and refuses the rest, and a refused
        // record is sent again every session, refused every time, for as long as it is kept.
        if (!flip.isUsable() || flip.trades().size() > 64) {
            return null;
        }
        flip.name = nameFor(flip) + (move() ? " to " + toCombo.getSelectedItem() : "");
        return flip;
    }

    /**
     * The ticked purchases, or the ticked sales. Which side a trade is on is the game's.
     *
     * <p>A move's parts carry their coins with them, because the account they go to has no
     * other way to know what they cost. The coins are those of what is left of the trade, which
     * is what the row offered: part of it may already belong to a recipe.
     */
    private List<RecipeFlip.Part> partsOf(boolean bought) {
        List<RecipeFlip.Part> parts = new ArrayList<>();
        for (Candidate candidate : picks) {
            if (candidate.picked() && candidate.trade.isBuy == bought) {
                parts.add(new RecipeFlip.Part(candidate.key, candidate.used, move()
                    ? RecipeFlipLedger.share(candidate.trade.deltaGp, candidate.used, candidate.available)
                    : null));
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
        Bridge.get(RecipeUpload.class).send(accountKey, flip);
        commit();
        // The screen stays up and is read again, so the record is there to be seen: named here,
        // at the top of the list below, and its trades gone from the ones on offer.
        open(move());
        notice.setText("Recorded: " + flip.name);
        notice.setVisible(true);
    }

    private void forget(RecipeFlip flip) {
        RecipeFlipStore store = Bridge.get(RecipeFlipStore.class);
        if (store == null || accountKey <= 0 || !store.remove(accountKey, flip)) {
            return;
        }
        Bridge.get(RecipeUpload.class).sendDeleted(accountKey, flip);
        commit();
        // Its trades are free again, so the screen is rebuilt rather than the one row redrawn -
        // still recording whichever of the two it was.
        open(move());
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
    /**
     * An amount as the chatbox reads one, so "10k" and "1.5m" mean what they say. Keeping only the
     * digits read "10k" as 10 and "1.5" as 15. A plain number is given the decimal point the chatbox
     * parser insists on ("12k" becomes "12.k"), which changes nothing about its value.
     */
    static long parseNumber(String raw, long fallback) {
        String text = raw != null ? raw.replace(",", "").trim() : "";
        String plain = ChatboxDecimalInput.toPlainAmount(
            text.indexOf('.') >= 0 ? text : text.replaceFirst("(?i)^(\\d+)([kmb]?)$", "$1.$2"));
        return plain != null ? Long.parseLong(plain) : fallback;
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

    private JPanel headingRow(JLabel label, JComponent trailing) {
        JPanel row = plain(new BorderLayout(6, 0));
        wide(row, 18);
        row.add(label, BorderLayout.WEST);
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
