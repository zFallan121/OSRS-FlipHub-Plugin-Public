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

import static com.osrsfliphub.Skin.MUTED_2;
import static com.osrsfliphub.Skin.TEXT;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The completed-flip list scrolls with the rest of the Flip Profile tab rather than sitting in a
 * fixed footer like the Activity tab, so its pager is rendered as the last row of the list.
 */
final class StatsPagerBuilder {
    private final UiStyler uiStyler;

    StatsPagerBuilder(UiStyler uiStyler) {
        this.uiStyler = uiStyler;
    }

    JPanel buildPager(int page, int totalPages, IntConsumer onPageSelected) {
        JPanel pager = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        pager.setOpaque(false);
        pager.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        pager.setBorder(BorderFactory.createEmptyBorder(4, 0, 2, 0));
        pager.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        JButton prevButton = buildPagerButton("<", page > 1, () -> onPageSelected.accept(page - 1));
        prevButton.setToolTipText("Newer items");
        JButton nextButton = buildPagerButton(">", page < totalPages, () -> onPageSelected.accept(page + 1));
        nextButton.setToolTipText("Older items");

        JLabel pageLabel = new JLabel("Page " + page + " of " + totalPages);
        pageLabel.setForeground(MUTED_2);
        pageLabel.setFont(uiStyler.font(10.5f));

        pager.add(prevButton);
        pager.add(pageLabel);
        pager.add(nextButton);
        return pager;
    }

    private JButton buildPagerButton(String text, boolean enabled, Runnable action) {
        JButton button = new JButton(text);
        uiStyler.styleGhostControl(button, 11f, new Insets(4, 12, 4, 12));
        // A control that cannot do anything says so in the ramp rather than by looking pressable:
        // page 1 has no newer page, and --muted-2 is the floor for text that still carries meaning.
        button.setForeground(enabled ? TEXT : MUTED_2);
        button.setEnabled(enabled);
        button.addActionListener(e -> action.run());
        return button;
    }
}
