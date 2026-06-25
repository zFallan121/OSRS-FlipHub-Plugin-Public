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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.border.Border;

final class CountdownEntry {
    final JLabel label;
    final long baseRemainingMs;
    final long baseTimeMs;

    CountdownEntry(JLabel label, long baseRemainingMs, long baseTimeMs) {
        this.label = label;
        this.baseRemainingMs = Math.max(0, baseRemainingMs);
        this.baseTimeMs = baseTimeMs;
    }
}

final class AgePairEntry {
    final javax.swing.JComponent[] components;
    final long buyTimestampMs;
    final long sellTimestampMs;

    AgePairEntry(javax.swing.JComponent[] components, long buyTimestampMs, long sellTimestampMs) {
        this.components = components;
        this.buyTimestampMs = buyTimestampMs;
        this.sellTimestampMs = sellTimestampMs;
    }
}

final class LineComponents {
    final JPanel row;
    final JLabel left;
    final JLabel right;

    LineComponents(JPanel row, JLabel left, JLabel right) {
        this.row = row;
        this.left = left;
        this.right = right;
    }
}

final class TrackingPanel extends JPanel implements Scrollable {
    private final int scrollUnitIncrement;
    private final int scrollBlockIncrement;

    TrackingPanel(int scrollUnitIncrement, int scrollBlockIncrement) {
        this.scrollUnitIncrement = scrollUnitIncrement;
        this.scrollBlockIncrement = scrollBlockIncrement;
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return scrollUnitIncrement;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return scrollBlockIncrement;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}

final class RoundedPanel extends JPanel {
    private final int arc;
    private final Color borderColor;

    RoundedPanel(int arc, Color background, Color borderColor) {
        this.arc = arc;
        this.borderColor = borderColor;
        setOpaque(false);
        setBackground(background);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(getBackground());
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
        g2.dispose();
        super.paintComponent(g);
    }

    @Override
    protected void paintBorder(Graphics g) {
        if (borderColor == null) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(borderColor);
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
        g2.dispose();
    }
}

final class RoundedBorder implements Border {
    private final int arc;
    private final Color color;
    private final Insets insets;

    RoundedBorder(int arc, Color color, Insets insets) {
        this.arc = arc;
        this.color = color;
        this.insets = insets;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        if (color == null) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(color);
        g2.drawRoundRect(x, y, width - 1, height - 1, arc, arc);
        g2.dispose();
    }

    @Override
    public Insets getBorderInsets(Component c) {
        return insets;
    }

    @Override
    public boolean isBorderOpaque() {
        return false;
    }
}

final class EllipsisLabel extends JLabel {
    private static final String ELLIPSIS = "...";
    private String fullText = "";

    EllipsisLabel(String text) {
        super();
        setFullText(text);
    }

    @Override
    public void setText(String text) {
        setFullText(text);
    }

    private void setFullText(String text) {
        fullText = text != null ? text : "";
        updateDisplayedText();
    }

    @Override
    public void setFont(Font font) {
        super.setFont(font);
        updateDisplayedText();
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        boolean widthChanged = width != getWidth();
        super.setBounds(x, y, width, height);
        if (widthChanged) {
            updateDisplayedText();
        }
    }

    private void updateDisplayedText() {
        if (fullText == null || fullText.isEmpty()) {
            super.setText("");
            return;
        }
        int availableWidth = getAvailableWidth();
        super.setText(clipText(fullText, availableWidth));
    }

    private int getAvailableWidth() {
        int width = getWidth();
        Insets insets = getInsets();
        return Math.max(0, width - insets.left - insets.right);
    }

    private String clipText(String text, int maxWidth) {
        if (maxWidth <= 0) {
            return text;
        }
        FontMetrics metrics = getFontMetrics(getFont());
        if (metrics.stringWidth(text) <= maxWidth) {
            return text;
        }
        int ellipsisWidth = metrics.stringWidth(ELLIPSIS);
        if (ellipsisWidth >= maxWidth) {
            return "";
        }
        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) / 2;
            String candidate = text.substring(0, mid);
            if (metrics.stringWidth(candidate) + ellipsisWidth <= maxWidth) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return text.substring(0, low) + ELLIPSIS;
    }
}
