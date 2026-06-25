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
import static com.osrsfliphub.FlipHubPanelConstants.BG;
import static com.osrsfliphub.FlipHubPanelConstants.BG_ALT;
import static com.osrsfliphub.FlipHubPanelConstants.INPUT_ARC;
import static com.osrsfliphub.FlipHubPanelConstants.MUTED;
import static com.osrsfliphub.FlipHubPanelConstants.SOFT_BORDER;
import static com.osrsfliphub.FlipHubPanelConstants.TEXT;

import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JToggleButton;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.FontManager;

final class FlipHubUiStyler {
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

    void styleTab(JToggleButton button, boolean active) {
        button.setFocusPainted(false);
        button.setFont(fontSemiBold(12f));
        button.setForeground(active ? TEXT : MUTED);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 2, 0, active ? ACCENT : BG),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
    }

    void styleComboBox(JComboBox<?> combo) {
        combo.setBackground(BG_ALT);
        combo.setForeground(TEXT);
        combo.setFont(font(11f));
        combo.setBorder(roundedBorder(INPUT_ARC, SOFT_BORDER, new Insets(4, 8, 4, 8)));
        combo.setFocusable(false);
        combo.setOpaque(true);
    }

    void styleTextField(JTextField field) {
        field.setBackground(BG_ALT);
        field.setForeground(TEXT);
        field.setCaretColor(TEXT);
        field.setBorder(roundedBorder(INPUT_ARC, SOFT_BORDER, new Insets(6, 10, 6, 10)));
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
        String[] families = new String[] { "Avenir Next", "Segoe UI", "Trebuchet MS" };
        int fontSize = Math.max(10, Math.round(size));
        for (String family : families) {
            Font candidate = new Font(family, style, fontSize);
            if (family.equalsIgnoreCase(candidate.getFamily())) {
                return candidate;
            }
        }
        return FontManager.getDefaultFont().deriveFont(style, size);
    }
}
