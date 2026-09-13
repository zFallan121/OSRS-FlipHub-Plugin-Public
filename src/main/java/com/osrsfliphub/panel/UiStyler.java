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

import static com.osrsfliphub.Skin.ACCENT;
import static com.osrsfliphub.Skin.BOOKMARK_GLYPH;
import static com.osrsfliphub.Skin.BOOKMARK_GLYPH_SIZE;
import static com.osrsfliphub.Skin.CHIP_ARC;
import static com.osrsfliphub.Skin.CLEAR_MARK_SIZE;
import static com.osrsfliphub.Skin.CONTROL_BORDER;
import static com.osrsfliphub.Skin.CONTROL_BORDER_HOVER;
import static com.osrsfliphub.Skin.CONTROL_FILL;
import static com.osrsfliphub.Skin.INLINE_CLEAR_GAP;
import static com.osrsfliphub.Skin.INLINE_CLEAR_SLOT;
import static com.osrsfliphub.Skin.INPUT_ARC;
import static com.osrsfliphub.Skin.LINE;
import static com.osrsfliphub.Skin.MUTED;
import static com.osrsfliphub.Skin.MUTED_2;
import static com.osrsfliphub.Skin.OVERLAY_BASE;
import static com.osrsfliphub.Skin.SORT_ICON_SIZE;
import static com.osrsfliphub.Skin.TEXT;
import static com.osrsfliphub.Skin.TRAILING_CONTROL_WIDTH;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JToggleButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.FontManager;

/**
 * Type and controls for the panel, following STYLEGUIDE.md §4 and §9.
 *
 * <p>Inter is the site's only text face, so it leads the family list; the rest of the list is the
 * nearest thing each platform ships when the reader does not have it installed. The one thing the
 * panel cannot borrow is tabular figures — Java2D exposes no OpenType feature toggles — so every
 * numeric value is right-aligned instead, which is what the site's numeric columns do anyway and
 * is what actually keeps magnitudes comparable down a column.
 */
final class UiStyler {
    private static final String[] TEXT_FAMILIES = new String[] {
        "Inter", "Segoe UI Variable Text", "Segoe UI", "Avenir Next", "Trebuchet MS"
    };

    Font font(float size) {
        return resolveFont(Font.PLAIN, size);
    }

    Font fontBold(float size) {
        return resolveFont(Font.BOLD, size);
    }

    Font fontSemiBold(float size) {
        return resolveFont(Font.BOLD, size - 0.5f);
    }

    Font fontSymbol(float size) {
        return FontManager.getDefaultBoldFont().deriveFont(size);
    }

    /**
     * The size to draw the sort mark at in a slot {@code slotWidth} wide: the bookmark star's own
     * ink, measured.
     *
     * <p>The two controls sit one above the other in the same trailing slot, one a drawn mark and
     * one a typed glyph, and a pair like that reads as mismatched at a couple of pixels'
     * difference. A number picked against one face would be wrong under the next, since the star
     * is whatever the symbol font makes of its point size - so the star is measured and the mark
     * is built to it. The slot is the ceiling: the profile tab draws the same mark in a half-width
     * one, and a mark wider than its button is a mark with a side clipped off.
     */
    int sortMarkSize(int slotWidth) {
        Font symbol = fontSymbol(BOOKMARK_GLYPH_SIZE);
        double ink = symbol
            .createGlyphVector(new FontRenderContext(null, true, true), BOOKMARK_GLYPH)
            .getVisualBounds()
            .getHeight();
        return Math.min(slotWidth, SortIcon.sizeForMarkHeight(ink, SORT_ICON_SIZE));
    }

    /**
     * The brand's signature: uppercase, 700, tracked out, and never below --muted-2. It goes above
     * a number or over a section — never on a value, and never on a row label inside a ledger.
     */
    Font fontMicro(float size) {
        Map<TextAttribute, Object> tracking = new HashMap<>();
        tracking.put(TextAttribute.TRACKING, 0.06);
        return resolveFont(Font.BOLD, size).deriveFont(tracking);
    }

    /**
     * Type for a figure, as opposed to type for words.
     *
     * <p>The faces here space digits by eye rather than on a grid, so a run of numbers reads
     * unevenly, and at these sizes in bold a full stop is about a pixel wide and disappears
     * between two heavy digits: "4.91M" reads as "491M". Java offers no way to ask a font for
     * its lining figures, so the separation is bought with a little tracking instead, which
     * gives the stop room to be seen and evens out the run.
     */
    Font fontNumeric(float size) {
        Map<TextAttribute, Object> tracking = new HashMap<>();
        tracking.put(TextAttribute.TRACKING, 0.045);
        return resolveFont(Font.BOLD, size).deriveFont(tracking);
    }

    /** Applies the micro-label to a label wholesale: the case, the type and the colour. */
    void styleMicroLabel(JLabel label, float size) {
        label.setText(label.getText() != null ? label.getText().toUpperCase(Locale.US) : "");
        label.setFont(fontMicro(size));
        label.setForeground(MUTED_2);
    }

    void styleTab(JToggleButton button, boolean active) {
        button.setFocusPainted(false);
        button.setFont(fontSemiBold(12f));
        button.setForeground(active ? TEXT : MUTED);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createCompoundBorder(
            // A marker, not a container: the inactive tab gets a transparent rule of the same
            // height so the two never shift by a pixel as the selection moves.
            BorderFactory.createMatteBorder(0, 0, 2, 0, active ? ACCENT : new Color(0, 0, 0, 0)),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        // The selected tab is already at full strength; an unselected one lifts
        // out of the muted ramp under the pointer to say it can be reached.
        for (java.awt.event.MouseListener existing : button.getMouseListeners()) {
            if (existing instanceof TabHoverAdapter) {
                button.removeMouseListener(existing);
            }
        }
        button.addMouseListener(new TabHoverAdapter(button, active));
    }

    /**
     * A ghost control: a hairline, the text, and the backdrop showing through. The site's
     * --control-bg is 4% white — at this size the fill is worth less than the artefacts an opaque
     * square behind a rounded border would cost, so the panel keeps the rule and drops the fill.
     * There is no filled control here because nothing in the panel commits anything.
     */
    void styleGhostControl(AbstractButton button, float size, Insets padding) {
        styleGhostControl(button, size, padding, CHIP_ARC);
    }

    /**
     * The same ghost at a chosen radius. A control standing beside an input is part of that
     * input's row rather than a pill in its own right, so it takes the input's radius and the
     * input's height; a pill next to a ruled box reads as two different kinds of thing.
     */
    void styleGhostControl(AbstractButton button, float size, Insets padding, int arc) {
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(true);
        button.setOpaque(false);
        button.setFont(fontSemiBold(size));
        button.setForeground(TEXT);
        button.setBorder(roundedBorder(arc, CONTROL_BORDER, padding));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        installGhostHover(button, arc, padding);
    }

    /**
     * A control with no container at all: the mark is the whole of it.
     *
     * <p>A ghost's rule is the affordance a control needs when its content is a
     * word. A drawn mark does not need it - the sort direction says which way it
     * points whether or not it is boxed - and at 15px the box was the loudest
     * thing in the row, reading as an empty well beside a full dropdown. The
     * hand cursor stays, because that is the part that says it can be clicked.
     */
    void styleBareControl(AbstractButton button) {
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setOpaque(false);
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setForeground(TEXT);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    /**
     * A clear mark inside the field's own right edge, shown only once there is something to clear.
     *
     * <p>A button beside the field spends a slot of the row on a control that is doing nothing
     * most of the time, and spells out an action the mark states in a third of the room. Inside
     * the field the mark also sits where the text it clears is, so the two read as one object.
     *
     * <p>The strip it stands in is taken out of the field's padding rather than laid over the
     * text: a caret that runs under the mark is a field that has to be scrolled to be read.
     */
    void installInlineClear(JTextField field) {
        field.setBorder(roundedBorder(INPUT_ARC, CONTROL_BORDER,
            new Insets(3, 8, 3, INLINE_CLEAR_SLOT + INLINE_CLEAR_GAP)));

        JButton clear = new JButton(new ClearIcon(CLEAR_MARK_SIZE));
        clear.setFocusPainted(false);
        clear.setContentAreaFilled(false);
        clear.setBorderPainted(false);
        clear.setOpaque(false);
        clear.setBorder(BorderFactory.createEmptyBorder());
        clear.setMargin(new Insets(0, 0, 0, 0));
        clear.setForeground(MUTED_2);
        clear.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clear.setToolTipText("Clear");
        // Focus stays with the caret: the mark is an edit to the field, not somewhere to be.
        clear.setFocusable(false);
        clear.setRequestFocusEnabled(false);
        clear.getModel().addChangeListener(event -> {
            ButtonModel model = clear.getModel();
            clear.setForeground(model.isRollover() || model.isPressed() ? TEXT : MUTED_2);
            clear.repaint();
        });
        clear.addActionListener(event -> {
            field.setText("");
            field.requestFocusInWindow();
        });

        // Placed by a layout manager rather than on a resize event, so the mark is where it
        // belongs the first time the field is laid out instead of one repaint later. A text
        // field takes its preferred size from its UI, so the sizes returned here are never asked
        // for - only layoutContainer does any work.
        field.setLayout(new LayoutManager() {
            @Override
            public void addLayoutComponent(String name, Component component) {
            }

            @Override
            public void removeLayoutComponent(Component component) {
            }

            @Override
            public Dimension preferredLayoutSize(Container parent) {
                return parent.getSize();
            }

            @Override
            public Dimension minimumLayoutSize(Container parent) {
                return new Dimension(0, 0);
            }

            @Override
            public void layoutContainer(Container parent) {
                clear.setBounds(
                    parent.getWidth() - INLINE_CLEAR_SLOT - INLINE_CLEAR_GAP,
                    (parent.getHeight() - INLINE_CLEAR_SLOT) / 2,
                    INLINE_CLEAR_SLOT,
                    INLINE_CLEAR_SLOT);
            }
        });
        field.add(clear);
        clear.setVisible(!field.getText().isEmpty());

        // A document cannot be asked what it will hold after the edit that is being announced, so
        // the mark is set from the field once the edit has landed.
        field.getDocument().addDocumentListener(new DocumentListener() {
            private void sync() {
                SwingUtilities.invokeLater(() -> clear.setVisible(!field.getText().isEmpty()));
            }

            @Override
            public void insertUpdate(DocumentEvent event) {
                sync();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                sync();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                sync();
            }
        });
    }

    /**
     * Brightens the rule under the pointer, and only the rule.
     *
     * <p>The text is deliberately left alone: a control whose colour carries a
     * state would either have that erased by the hover, or have to restore a
     * value the button changes for itself on the very click being hovered.
     */
    private void installGhostHover(AbstractButton button, int arc, Insets padding) {
        // One border for the life of the button, which asks what colour it should be each time
        // it draws. Swapping borders instead discarded anything a caller had wrapped around
        // this one, so a button given a gap above it lost the gap and jumped up the moment the
        // pointer went anywhere near it.
        button.setBorder(roundedBorder(arc, () -> {
            ButtonModel model = button.getModel();
            return model.isRollover() || model.isPressed() ? CONTROL_BORDER_HOVER : CONTROL_BORDER;
        }, padding));
        button.getModel().addChangeListener(event -> button.repaint());
    }

    /**
     * A text field keeps the caret until something else takes focus, so a click on the panel
     * itself has to be that something: without this the search box stays active - and keeps
     * swallowing the keyboard - however far away the user clicks.
     */
    void installClickToDefocus(javax.swing.JComponent surface) {
        if (surface == null) {
            return;
        }
        surface.setFocusable(true);
        surface.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                surface.requestFocusInWindow();
            }
        });
    }

    /**
     * Pins a row's trailing button to the shared width at the height of the control it follows,
     * so every row that ends in one ends at the same x and they read as a single right edge.
     */
    void sizeTrailingControl(AbstractButton button, JComponent field) {
        sizeTrailingControl(button, field, TRAILING_CONTROL_WIDTH);
    }

    /** The same, at a width of its own, for a trailing control that holds a mark and not a word. */
    void sizeTrailingControl(AbstractButton button, JComponent field, int width) {
        int height = Math.max(field.getPreferredSize().height, button.getPreferredSize().height);
        Dimension size = new Dimension(width, height);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(size);
    }

    /** Pins a control to the height of the field it sits next to, so the row reads as one bar. */
    void matchFieldHeight(javax.swing.JComponent control, javax.swing.JComponent field) {
        int height = field.getPreferredSize().height;
        Dimension preferred = control.getPreferredSize();
        control.setPreferredSize(new Dimension(preferred.width, height));
        control.setMinimumSize(new Dimension(preferred.width, height));
        control.setMaximumSize(new Dimension(preferred.width, height));
    }

    /**
     * The size every dropdown reads at - the combo's own text, its popup rows, and the one
     * dropdown that is a label rather than a combo. It lives here because the renderer, not the
     * control, owns the text: a {@code setFont} on the combo after this is silently overruled.
     */
    static final float DROPDOWN_TEXT_SIZE = 11f;

    void styleComboBox(JComboBox<?> combo) {
        // The whole delegate, not just the colours: this renders under whichever look-and-feel
        // RuneLite has installed, so the arrow, the body and the popup are all drawn by us. The
        // body is the one pre-blended fill in the panel - a combo cannot go transparent without
        // its popup and arrow coming apart, and at 24px tall the washes lose nothing under it.
        combo.setUI(new ComboBoxUI());
        combo.setBackground(CONTROL_FILL);
        combo.setForeground(TEXT);
        combo.setFont(font(DROPDOWN_TEXT_SIZE));
        combo.setRenderer(new ComboRenderer(font(DROPDOWN_TEXT_SIZE)));
        combo.setBorder(roundedBorder(INPUT_ARC, CONTROL_BORDER, new Insets(4, 8, 4, 8)));
        combo.setFocusable(false);
        combo.setOpaque(false);
        combo.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // An overlay is a surface over the room: the popup takes the same opaque ground and the
        // same hairline as the profile menu, rather than the look-and-feel's own list.
        Object popup = combo.getUI().getAccessibleChild(combo, 0);
        if (popup instanceof JComponent) {
            ((JComponent) popup).setBorder(BorderFactory.createLineBorder(LINE));
            ((JComponent) popup).setBackground(OVERLAY_BASE);
        }
    }

    /** A ruled input rather than a boxed one: no fill, one hairline, the caret in --text. */
    void styleTextField(JTextField field) {
        field.setOpaque(false);
        field.setBackground(new Color(0, 0, 0, 0));
        field.setForeground(TEXT);
        field.setCaretColor(TEXT);
        field.setSelectionColor(new Color(91, 159, 237, 70));
        field.setSelectedTextColor(TEXT);
        field.setBorder(roundedBorder(INPUT_ARC, CONTROL_BORDER, new Insets(3, 8, 3, 8)));
        field.setFont(font(11f));
    }

    /**
     * A word that does something. The panel has one action colour and this is where it goes;
     * there is no rule around it, because a box inside a ledger row has nothing to earn itself
     * with. Under the pointer the word goes to plain text: the action colour says "this does
     * something", white says "this one, the one you are on".
     */
    JLabel actionLink(String text, String tooltip, Runnable action) {
        JLabel link = new JLabel(text, javax.swing.SwingConstants.RIGHT);
        link.setForeground(ACCENT);
        link.setFont(font(9.5f));
        link.setToolTipText(tooltip);
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.addMouseListener(new StatsClickMouseAdapter(action));
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

    /** Run something whenever what is typed in a field changes, however it changed. */
    void onEdit(JTextField field, Runnable onChange) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                onChange.run();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                onChange.run();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                onChange.run();
            }
        });
    }

    Border roundedBorder(int arc, Color color, Insets padding) {
        return roundedBorder(arc, () -> color, padding);
    }

    /** The same, for a control whose outline colour depends on what the pointer is doing. */
    Border roundedBorder(int arc, java.util.function.Supplier<Color> color, Insets padding) {
        Insets borderInsets = new Insets(1, 1, 1, 1);
        Border border = new RoundedBorder(arc, color, borderInsets);
        if (padding == null) {
            return border;
        }
        return BorderFactory.createCompoundBorder(border, new EmptyBorder(padding));
    }

    private Font resolveFont(int style, float size) {
        int fontSize = Math.max(10, Math.round(size));
        for (String family : TEXT_FAMILIES) {
            Font candidate = new Font(family, style, fontSize);
            if (family.equalsIgnoreCase(candidate.getFamily())) {
                return candidate;
            }
        }
        return FontManager.getDefaultFont().deriveFont(style, size);
    }
}
