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

import static com.osrsfliphub.Skin.MUTED;
import static com.osrsfliphub.Skin.STATS_CARD_TOGGLE_SKIP_KEY;
import static com.osrsfliphub.Skin.TEXT;

import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.util.function.IntConsumer;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

final class StatsCardInteractionInstaller {
    private final IntConsumer toggleStatsItemExpanded;
    private final IntConsumer toggleStatsHistoryExpanded;

    StatsCardInteractionInstaller(IntConsumer toggleStatsItemExpanded,
                                         IntConsumer toggleStatsHistoryExpanded) {
        this.toggleStatsItemExpanded = toggleStatsItemExpanded;
        this.toggleStatsHistoryExpanded = toggleStatsHistoryExpanded;
    }

    void installStatsCardToggle(JComponent root, int itemId) {
        MouseAdapter clickHandler = new StatsClickMouseAdapter(() -> {
            if (toggleStatsItemExpanded != null) {
                toggleStatsItemExpanded.accept(itemId);
            }
        });
        installStatsCardToggleRecursive(root, clickHandler);
        installCardHover(root);
    }

    /**
     * Hover for a whole card, installed on every descendant.
     *
     * <p>Swing does not bubble mouse events, so the card only learns about the
     * pointer through its children - and moving between two of them fires an
     * exit before the next enter. Asking whether the pointer is still inside the
     * card, rather than trusting the exit, is what stops that flickering.
     */
    private void installCardHover(JComponent root) {
        if (!(root instanceof RoundedPanel)) {
            return;
        }
        RoundedPanel card = (RoundedPanel) root;
        card.setHoverBorderColor(Skin.SURFACE_BORDER_HOVER);
        MouseAdapter hoverHandler = new MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent event) {
                card.setHovered(true);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent event) {
                java.awt.Point point = javax.swing.SwingUtilities.convertPoint(
                    event.getComponent(), event.getPoint(), card);
                card.setHovered(card.contains(point));
            }
        };
        installHoverRecursive(card, hoverHandler);
    }

    private void installHoverRecursive(Component component, MouseAdapter hoverHandler) {
        if (component == null) {
            return;
        }
        component.addMouseListener(hoverHandler);
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                installHoverRecursive(child, hoverHandler);
            }
        }
    }

    private void installStatsCardToggleRecursive(Component component, MouseAdapter clickHandler) {
        if (component == null || clickHandler == null) {
            return;
        }
        if (component instanceof JComponent
            && Boolean.TRUE.equals(((JComponent) component).getClientProperty(STATS_CARD_TOGGLE_SKIP_KEY))) {
            return;
        }
        component.addMouseListener(clickHandler);
        component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                installStatsCardToggleRecursive(child, clickHandler);
            }
        }
    }

    void installStatsHistoryToggle(Component component, int itemId) {
        if (component == null || itemId <= 0) {
            return;
        }
        MouseAdapter clickHandler = new StatsClickMouseAdapter(() -> {
            if (toggleStatsHistoryExpanded != null) {
                toggleStatsHistoryExpanded.accept(itemId);
            }
        });
        installStatsHistoryToggleRecursive(component, clickHandler);
    }

    private void installStatsHistoryToggleRecursive(Component component, MouseAdapter clickHandler) {
        if (component == null || clickHandler == null) {
            return;
        }
        component.addMouseListener(clickHandler);
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                installStatsHistoryToggleRecursive(child, clickHandler);
            }
        }
    }

    void installStatsHistoryHoverFeedback(JPanel header, JLabel title, JLabel chevron) {
        if (header == null || title == null || chevron == null) {
            return;
        }
        MouseAdapter hoverHandler = new StatsHistoryHoverMouseAdapter(title, chevron);
        header.addMouseListener(hoverHandler);
        title.addMouseListener(hoverHandler);
        chevron.addMouseListener(hoverHandler);
    }

    void markSkipStatsCardToggle(Component component) {
        if (!(component instanceof JComponent)) {
            return;
        }
        ((JComponent) component).putClientProperty(STATS_CARD_TOGGLE_SKIP_KEY, Boolean.TRUE);
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                markSkipStatsCardToggle(child);
            }
        }
    }
}
