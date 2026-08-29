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

import static com.osrsfliphub.FlipHubPanelConstants.ACCENT;
import static com.osrsfliphub.FlipHubPanelConstants.CHIP_ARC;
import static com.osrsfliphub.FlipHubPanelConstants.CONTROL_BORDER;
import static com.osrsfliphub.FlipHubPanelConstants.CONTROL_FILL;
import static com.osrsfliphub.FlipHubPanelConstants.INPUT_ARC;
import static com.osrsfliphub.FlipHubPanelConstants.LINE;
import static com.osrsfliphub.FlipHubPanelConstants.MUTED;
import static com.osrsfliphub.FlipHubPanelConstants.MUTED_2;
import static com.osrsfliphub.FlipHubPanelConstants.OVERLAY_BASE;
import static com.osrsfliphub.FlipHubPanelConstants.TEXT;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.font.TextAttribute;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JToggleButton;
import javax.swing.JTextField;
import javax.swing.border.Border;
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
final class FlipHubUiStyler {
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
     * The brand's signature: uppercase, 700, tracked out, and never below --muted-2. It goes above
     * a number or over a section — never on a value, and never on a row label inside a ledger.
     */
    Font fontMicro(float size) {
        Map<TextAttribute, Object> tracking = new HashMap<>();
        tracking.put(TextAttribute.TRACKING, 0.06);
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
    }

    /** Pins a control to the height of the field it sits next to, so the row reads as one bar. */
    void matchFieldHeight(javax.swing.JComponent control, javax.swing.JComponent field) {
        int height = field.getPreferredSize().height;
        Dimension preferred = control.getPreferredSize();
        control.setPreferredSize(new Dimension(preferred.width, height));
        control.setMinimumSize(new Dimension(preferred.width, height));
        control.setMaximumSize(new Dimension(preferred.width, height));
    }

    void styleComboBox(JComboBox<?> combo) {
        // The whole delegate, not just the colours: this renders under whichever look-and-feel
        // RuneLite has installed, so the arrow, the body and the popup are all drawn by us. The
        // body is the one pre-blended fill in the panel - a combo cannot go transparent without
        // its popup and arrow coming apart, and at 24px tall the washes lose nothing under it.
        combo.setUI(new FlipHubComboBoxUI());
        combo.setBackground(CONTROL_FILL);
        combo.setForeground(TEXT);
        combo.setFont(font(11f));
        combo.setRenderer(new FlipHubComboRenderer(font(11f)));
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
        field.setBorder(roundedBorder(INPUT_ARC, CONTROL_BORDER, new Insets(6, 10, 6, 10)));
        field.setFont(font(12f));
    }

    Border roundedBorder(int arc, Color color, Insets padding) {
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
