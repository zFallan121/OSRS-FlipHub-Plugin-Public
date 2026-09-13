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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicComboBoxUI;

/**
 * A label counting down, and the point it is counting from.
 *
 * <p>The base moves rather than the entry being replaced: a refresh brings a new remaining time
 * for the same row, and re-registering would mean discarding the label the row is built from.
 */
final class CountdownEntry {
    final JLabel label;
    long baseRemainingMs;
    long baseTimeMs;

    CountdownEntry(JLabel label, long baseRemainingMs, long baseTimeMs) {
        this.label = label;
        rebase(baseRemainingMs, baseTimeMs);
    }

    void rebase(long remainingMs, long baseTimeMs) {
        this.baseRemainingMs = Math.max(0, remainingMs);
        this.baseTimeMs = baseTimeMs;
    }
}

/** The two prices a row shows an age for, and when each of them last traded. */
final class AgePairEntry {
    final javax.swing.JComponent[] components;
    long buyTimestampMs;
    long sellTimestampMs;

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

/**
 * The room. Deep navy plus the two radial washes the site paints on every page, drawn once at the
 * root of the panel so that every surface above it can be transparent and let it through. Nothing
 * else in the panel is allowed to paint an opaque slab over an area this size.
 */
final class BackdropPanel extends JPanel {
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

    private final Color base;
    private final Color greenWash;
    private final Color blueWash;

    BackdropPanel(Color base, Color greenWash, Color blueWash) {
        this.base = base;
        this.greenWash = greenWash;
        this.blueWash = blueWash;
        setOpaque(true);
        setBackground(base);
    }

    @Override
    protected void paintComponent(Graphics g) {
        int width = getWidth();
        int height = getHeight();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(base);
        g2.fillRect(0, 0, width, height);
        // Both washes are sized off the panel WIDTH, not its height: the sidebar is a tall column
        // and the site's washes sit in the top corners of a wide page, so tying them to the height
        // would smear one glow down the whole rail.
        paintWash(g2, greenWash, 0.20f * width, -0.06f * width, 1.55f * width);
        paintWash(g2, blueWash, 0.92f * width, 0.16f * width, 1.35f * width);
        g2.dispose();
    }

    private void paintWash(Graphics2D g2, Color color, float centerX, float centerY, float radius) {
        if (color == null || radius <= 0f) {
            return;
        }
        g2.setPaint(new RadialGradientPaint(
            new Point2D.Float(centerX, centerY),
            radius,
            new float[] { 0f, 1f },
            new Color[] { color, TRANSPARENT }
        ));
        g2.fillRect(0, 0, getWidth(), getHeight());
    }
}

/**
 * The glass card: a top-to-bottom white-alpha gradient, a slate hairline, a 1px inset highlight
 * along the top edge and a 1px seat under the bottom one. The seat is what the site's elevation
 * ladder buys with a contact shadow — Swing has no blur to spare in a list that re-renders on
 * every tick, so the panel keeps the tight contact line and drops the ambient half.
 */
/**
 * A block of rows inside a card, set in from its left edge and sunk into its surface.
 *
 * <p>The card's own heading, its picture and its name, stays at full width; everything below is
 * held in one of these. That does two jobs with one move: it gives the rows somewhere to sit so
 * the card has a front and a back rather than being flat, and it separates one block of rows
 * from the next without needing a line drawn between them.
 */
final class CardSection extends JPanel {
    private CardSection() {
        super(new BorderLayout());
    }

    /** Wraps {@code content} as a section. The result is ready to add to a card. */
    static JPanel of(JComponent content) {
        return of(content, 6);
    }

    /**
     * @param rightPadding room inside the block on its right. Rows whose values carry a padding
     *                     of their own pass a smaller number, so the text ends up sitting the
     *                     same distance from each edge whichever card it is in.
     */
    static JPanel of(JComponent content, int rightPadding) {
        RoundedPanel well = new RoundedPanel(
            FlipHubPanelConstants.WELL_ARC,
            FlipHubPanelConstants.SURFACE_WELL,
            FlipHubPanelConstants.SURFACE_WELL);
        well.setLayout(new BorderLayout());
        well.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, Math.max(0, rightPadding)));
        well.add(content, BorderLayout.CENTER);

        // No inset of its own. The card's own padding is the margin, so the block sits the same
        // distance from the left edge, the right edge and the bottom of the card.
        CardSection section = new CardSection();
        section.setOpaque(false);
        // Deliberately not given a left alignment. A column laid out this way places its
        // children against one another, and one child claiming a different alignment from the
        // struts and rows beside it shunts the whole block sideways.
        section.add(well, BorderLayout.CENTER);
        return section;
    }

    /**
     * Asked for rather than set once at build time. A card's rows are filled in after it is
     * assembled, and a height captured before that is the height of a row with no text in it.
     */
    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
}

final class RoundedPanel extends JPanel {
    private final int arc;
    private final Color topColor;
    private final Color bottomColor;
    private final Color borderColor;
    private final boolean seated;
    private Color hoverBorderColor;
    private boolean hovered;

    RoundedPanel(int arc, Color background, Color borderColor) {
        this(arc, background, background, borderColor, false);
    }

    RoundedPanel(int arc, Color topColor, Color bottomColor, Color borderColor, boolean seated) {
        this.arc = arc;
        this.topColor = topColor;
        this.bottomColor = bottomColor != null ? bottomColor : topColor;
        this.borderColor = borderColor;
        this.seated = seated;
        setOpaque(false);
        setBackground(topColor);
    }

    /**
     * Lifts the card's rule while the pointer is on it. A glass surface has no
     * fill to brighten and no shadow to raise, so the edge is the whole of the
     * affordance - the same move every ghost control in the panel makes.
     */
    void setHoverBorderColor(Color color) {
        this.hoverBorderColor = color;
    }

    void setHovered(boolean value) {
        if (hovered != value) {
            hovered = value;
            repaint();
        }
    }

    /** A glass card: the surface that groups. */
    static RoundedPanel glass(int arc) {
        return new RoundedPanel(
            arc,
            FlipHubPanelConstants.SURFACE_TOP,
            FlipHubPanelConstants.SURFACE_BOTTOM,
            FlipHubPanelConstants.SURFACE_BORDER,
            true
        );
    }

    @Override
    protected void paintComponent(Graphics g) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            super.paintComponent(g);
            return;
        }
        int radius = clampArc(arc, width, height);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (topColor != null) {
            g2.setPaint(topColor.equals(bottomColor)
                ? topColor
                : new GradientPaint(0f, 0f, topColor, 0f, height, bottomColor));
            g2.fillRoundRect(0, 0, width, height, radius, radius);
        }
        if (seated) {
            // The inset highlight along the top and the seat along the bottom: together they are
            // what makes the card read as an object resting on the navy instead of a decal on it.
            g2.setColor(FlipHubPanelConstants.SURFACE_HIGHLIGHT);
            g2.drawLine(radius / 2, 1, width - 1 - radius / 2, 1);
            g2.setColor(FlipHubPanelConstants.SURFACE_SEAT);
            g2.drawLine(radius / 2, height - 1, width - 1 - radius / 2, height - 1);
        }
        g2.dispose();
        super.paintComponent(g);
    }

    @Override
    protected void paintBorder(Graphics g) {
        Color edge = hovered && hoverBorderColor != null ? hoverBorderColor : borderColor;
        if (edge == null) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(edge);
        int radius = clampArc(arc, getWidth(), getHeight());
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
        g2.dispose();
    }

    static int clampArc(int arc, int width, int height) {
        return Math.max(0, Math.min(arc, Math.min(width, height)));
    }
}

/**
 * A combo box that looks the same under every look-and-feel. RuneLite installs its own, and the
 * default delegate draws a raised arrow button with a light bevel that belongs to neither theme,
 * so the panel supplies the whole delegate: a rounded --control-fill body, a flat --muted arrow,
 * and a popup on the overlay ground rather than the L&F's white list.
 */
final class FlipHubComboBoxUI extends BasicComboBoxUI {
    @Override
    public void update(Graphics g, JComponent c) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(FlipHubPanelConstants.CONTROL_FILL);
        int arc = RoundedPanel.clampArc(FlipHubPanelConstants.INPUT_ARC, c.getWidth(), c.getHeight());
        g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), arc, arc);
        g2.dispose();
        paint(g, c);
    }

    /**
     * No-op. The default delegate fills the value area with the combo's background before the
     * renderer runs, which drew a lighter rectangle around the text inside the rounded control -
     * a box within a box. The body is painted once, by update().
     */
    @Override
    public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
    }

    @Override
    protected JButton createArrowButton() {
        JButton button = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(FlipHubPanelConstants.MUTED);
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                g2.fillPolygon(
                    new int[] { cx - 4, cx + 4, cx },
                    new int[] { cy - 2, cy - 2, cy + 3 },
                    3
                );
                g2.dispose();
            }
        };
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusable(false);
        button.setPreferredSize(new Dimension(18, 18));
        return button;
    }
}

/** The popup's rows: the overlay ground, the action colour on the one that is current. */
final class FlipHubComboRenderer extends DefaultListCellRenderer {
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

    private final Font font;

    FlipHubComboRenderer(Font font) {
        this.font = font;
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                  boolean isSelected, boolean hasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, hasFocus);
        // index < 0 is the closed combo drawing its own value, not a row in the popup. It sits
        // inside a control that has already been painted and padded, so it contributes neither a
        // ground nor an inset of its own.
        boolean inPopup = index >= 0;
        setOpaque(inPopup);
        setBackground(inPopup ? FlipHubPanelConstants.OVERLAY_BASE : TRANSPARENT);
        setForeground(inPopup && isSelected ? FlipHubPanelConstants.ACCENT : FlipHubPanelConstants.TEXT);
        setBorder(inPopup
            ? new javax.swing.border.EmptyBorder(3, 8, 3, 8)
            : new javax.swing.border.EmptyBorder(0, 0, 0, 0));
        if (font != null) {
            setFont(font);
        }
        return this;
    }
}

/** Lifts an unselected tab out of the muted ramp while the pointer is on it. */
final class TabHoverAdapter extends java.awt.event.MouseAdapter {
    private final javax.swing.AbstractButton button;
    private final Color resting;

    TabHoverAdapter(javax.swing.AbstractButton button, boolean active) {
        this.button = button;
        this.resting = active ? FlipHubPanelConstants.TEXT : FlipHubPanelConstants.MUTED;
    }

    @Override
    public void mouseEntered(java.awt.event.MouseEvent event) {
        button.setForeground(FlipHubPanelConstants.TEXT);
    }

    @Override
    public void mouseExited(java.awt.event.MouseEvent event) {
        button.setForeground(resting);
    }
}

final class RoundedBorder implements Border {
    private final int arc;
    private final Supplier<Color> color;
    private final Insets insets;

    RoundedBorder(int arc, Color color, Insets insets) {
        this(arc, () -> color, insets);
    }

    /**
     * A border whose colour is asked for each time it is drawn.
     *
     * <p>So a control that changes colour under the pointer can keep one border for its whole
     * life and simply repaint. Swapping the border instead throws away anything wrapped around
     * it: a button given extra spacing above it lost that spacing the first time the pointer
     * touched it, and jumped upward by however much the spacing was.
     */
    RoundedBorder(int arc, Supplier<Color> color, Insets insets) {
        this.arc = arc;
        this.color = color;
        this.insets = insets;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Color color = this.color != null ? this.color.get() : null;
        if (color == null) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(color);
        // CHIP_ARC is 999 so a pill stays a pill at any height; clamping is what turns that into
        // the short side rather than a Java2D artefact.
        int radius = RoundedPanel.clampArc(arc, width, height);
        g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
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

/**
 * A field that says what it searches while it is empty. The hint is painted rather than typed
 * into the document, so it never becomes the query and never has to be stripped back out.
 */
final class PlaceholderTextField extends JTextField {
    private final String placeholder;

    PlaceholderTextField(String placeholder) {
        this.placeholder = placeholder != null ? placeholder : "";
    }

    @Override
    protected void paintComponent(Graphics g) {
        paintWell(g);
        super.paintComponent(g);
        if (placeholder.isEmpty() || !getText().isEmpty()) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(getFont());
            g2.setColor(FlipHubPanelConstants.MUTED_2);
            FontMetrics metrics = g2.getFontMetrics();
            Insets insets = getInsets();
            int baseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            g2.drawString(placeholder, insets.left, baseline);
        } finally {
            g2.dispose();
        }
    }

    /**
     * The floor of the field, painted before the text so it sits behind it.
     *
     * <p>The field is not opaque, so without this it is an outline with the panel showing
     * through and reads as flat. The rounded fill stops at the same corner the border draws,
     * which leaves the corners outside it transparent rather than square.
     */
    private void paintWell(Graphics g) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setPaint(new GradientPaint(
                0f, 0f, FlipHubPanelConstants.INPUT_WELL_TOP,
                0f, height, FlipHubPanelConstants.INPUT_WELL_BOTTOM));
            int arc = FlipHubPanelConstants.INPUT_ARC;
            g2.fillRoundRect(0, 0, width, height, arc, arc);
        } finally {
            g2.dispose();
        }
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
