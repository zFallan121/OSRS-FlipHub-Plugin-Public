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
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.function.Supplier;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import lombok.RequiredArgsConstructor;
import static com.osrsfliphub.Skin.*;

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
@RequiredArgsConstructor
final class AgePairEntry {
    final JComponent[] components;
    long buyTimestampMs;
    long sellTimestampMs;

}

final class TrackingPanel extends JPanel implements Scrollable {
    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return SCROLL_UNIT_INCREMENT;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return SCROLL_BLOCK_INCREMENT;
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
    BackdropPanel() {
        setOpaque(true);
        setBackground(BG);
    }

    @Override
    protected void paintComponent(Graphics g) {
        int width = getWidth();
        int height = getHeight();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(BG);
        g2.fillRect(0, 0, width, height);
        // Both washes are sized off the panel WIDTH, not its height: the sidebar is a tall column
        // and the site's washes sit in the top corners of a wide page, so tying them to the height
        // would smear one glow down the whole rail.
        paintWash(g2, GRAD_GREEN, 0.20f * width, -0.06f * width, 1.55f * width);
        paintWash(g2, GRAD_BLUE, 0.92f * width, 0.16f * width, 1.35f * width);
        g2.dispose();
    }

    private void paintWash(Graphics2D g2, Color color, float centerX, float centerY, float radius) {
        if (radius <= 0f) {
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
            WELL_ARC, SURFACE_WELL, SURFACE_WELL, SURFACE_WELL, false);
        well.setLayout(new BorderLayout());
        well.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, rightPadding));
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
    private boolean hovered;

    RoundedPanel(int arc, Color topColor, Color bottomColor, Color borderColor, boolean seated) {
        this.arc = arc;
        this.topColor = topColor;
        this.bottomColor = bottomColor;
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
    void setHovered(boolean value) {
        if (hovered != value) {
            hovered = value;
            repaint();
        }
    }

    /** A glass card: the surface that groups. */
    static JPanel card(int top, int left, int bottom, int right) {
        JPanel card = new RoundedPanel(
            CARD_ARC, SURFACE_TOP, SURFACE_BOTTOM, SURFACE_BORDER, true);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(top, left, bottom, right));
        card.setAlignmentX(LEFT_ALIGNMENT);
        return card;
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
        smooth(g2);
        g2.setPaint(topColor.equals(bottomColor)
            ? topColor
            : new GradientPaint(0f, 0f, topColor, 0f, height, bottomColor));
        g2.fillRoundRect(0, 0, width, height, radius, radius);
        if (seated) {
            // The inset highlight along the top and the seat along the bottom: together they are
            // what makes the card read as an object resting on the navy instead of a decal on it.
            g2.setColor(SURFACE_HIGHLIGHT);
            g2.drawLine(radius / 2, 1, width - 1 - radius / 2, 1);
            g2.setColor(SURFACE_SEAT);
            g2.drawLine(radius / 2, height - 1, width - 1 - radius / 2, height - 1);
        }
        g2.dispose();
        super.paintComponent(g);
    }

    @Override
    protected void paintBorder(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        smooth(g2);
        g2.setColor(hovered ? SURFACE_BORDER_HOVER : borderColor);
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
final class ComboBoxUI extends BasicComboBoxUI {
    @Override
    public void update(Graphics g, JComponent c) {
        Graphics2D g2 = (Graphics2D) g.create();
        smooth(g2);
        g2.setColor(CONTROL_FILL);
        int arc = RoundedPanel.clampArc(INPUT_ARC, c.getWidth(), c.getHeight());
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
        JButton button = new TipButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                smooth(g2);
                g2.setColor(MUTED);
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
@RequiredArgsConstructor
final class ComboRenderer extends DefaultListCellRenderer {
    private final Font font;

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                  boolean isSelected, boolean hasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, hasFocus);
        // index < 0 is the closed combo drawing its own value, not a row in the popup. It sits
        // inside a control that has already been painted and padded, so it contributes neither a
        // ground nor an inset of its own.
        boolean inPopup = index >= 0;
        setOpaque(inPopup);
        setBackground(inPopup ? OVERLAY_BASE : TRANSPARENT);
        setForeground(inPopup && isSelected ? ACCENT : TEXT);
        setBorder(inPopup
            ? new EmptyBorder(3, 8, 3, 8)
            : new EmptyBorder(0, 0, 0, 0));
        setFont(font);
        return this;
    }
}

/** Lifts an unselected tab out of the muted ramp while the pointer is on it. */
@RequiredArgsConstructor
final class TabHoverAdapter extends java.awt.event.MouseAdapter {
    private final JComponent button;
    private final Color resting;

    @Override
    public void mouseEntered(MouseEvent event) {
        button.setForeground(TEXT);
    }

    @Override
    public void mouseExited(MouseEvent event) {
        button.setForeground(resting);
    }
}

@RequiredArgsConstructor
final class RoundedBorder implements Border {
    private final int arc;
    private final Supplier<Color> color;

    /**
     * A border whose colour is asked for each time it is drawn.
     *
     * <p>So a control that changes colour under the pointer can keep one border for its whole
     * life and simply repaint. Swapping the border instead throws away anything wrapped around
     * it: a button given extra spacing above it lost that spacing the first time the pointer
     * touched it, and jumped upward by however much the spacing was.
     */

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D g2 = (Graphics2D) g.create();
        smooth(g2);
        g2.setColor(color.get());
        // CHIP_ARC is 999 so a pill stays a pill at any height; clamping is what turns that into
        // the short side rather than a Java2D artefact.
        int radius = RoundedPanel.clampArc(arc, width, height);
        g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
        g2.dispose();
    }

    @Override
    public Insets getBorderInsets(Component c) {
        return new Insets(1, 1, 1, 1);
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
@RequiredArgsConstructor
final class PlaceholderTextField extends JTextField {
    private final String placeholder;

    @Override
    public Point getToolTipLocation(MouseEvent event) {
        return Tip.at(this, event);
    }

    @Override
    protected void paintComponent(Graphics g) {
        paintWell(g);
        super.paintComponent(g);
        if (!getText().isEmpty()) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(getFont());
            g2.setColor(MUTED_2);
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
            smooth(g2);
            g2.setPaint(new GradientPaint(
                0f, 0f, INPUT_WELL_TOP,
                0f, height, INPUT_WELL_BOTTOM));
            int arc = INPUT_ARC;
            g2.fillRoundRect(0, 0, width, height, arc, arc);
        } finally {
            g2.dispose();
        }
    }
}

final class EllipsisLabel extends TipLabel {
    private static final String ELLIPSIS = "...";
    private static final int NOTICE_MS = 900;
    // Swapped in tests, which must never touch the real clipboard.
    static java.util.function.Consumer<String> clipboard = text -> Toolkit.getDefaultToolkit()
        .getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(text), null);
    private String fullText = "";
    private String notice;

    EllipsisLabel(String text) {
        super(null, LEADING);
        setText(text);
    }

    /**
     * Makes a right-click copy the whole text, however much of it fits on screen. The label reads
     * "Copied" for a moment; that word is kept apart from the text, so a refresh that writes the
     * text again in that moment neither removes it nor gets copied in its place.
     */
    void copyOnRightClick() {
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (!SwingUtilities.isRightMouseButton(e) || !contains(e.getPoint()) || fullText.isEmpty()) {
                    return;
                }
                try {
                    clipboard.accept(fullText);
                } catch (IllegalStateException busy) {
                    // Another program is holding the clipboard; nothing was copied.
                    return;
                }
                notice = "Copied";
                updateDisplayedText();
                Timer back = new Timer(NOTICE_MS, done -> {
                    notice = null;
                    updateDisplayedText();
                });
                back.setRepeats(false);
                back.start();
            }
        });
    }

    @Override
    public void setText(String text) {
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
        super.setText(notice != null ? notice : clipText(fullText, availableWidth));
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

/**
 * Where the panel puts a tooltip, which is to the left of the cursor and not to the right.
 *
 * <p>RuneLite gives every tooltip a window of its own on purpose, so the game canvas cannot be
 * drawn over the top of it. Swing then opens that window at the cursor and pulls it back only
 * far enough to fit on the monitor - and this panel sits against the client's right edge, so a
 * tooltip opened the ordinary way opens over the desktop behind the client instead of over the
 * client. Nothing in Swing will keep it in: the manager is a singleton, its idea of "fits" is
 * the whole screen, and the one thing a component gets to say about it is where to open.
 *
 * <p>So it opens leftwards. That needs nothing measured against the client to come out right:
 * the tooltip's right edge is the cursor, and the cursor is inside the panel, which is inside
 * the client. Downwards is only pulled up when the client's own bottom edge is in the way. It
 * is also where the age tooltip has always put itself.
 */
final class Tip {
    private Tip() {
    }

    static Point at(JComponent on, MouseEvent event) {
        JToolTip tip = on.createToolTip();
        tip.setTipText(on.getToolTipText(event));
        Dimension size = tip.getPreferredSize();
        int x = event.getX() - size.width - AGE_TOOLTIP_LEFT_GAP;
        int y = event.getY() + 18;
        Window window = SwingUtilities.getWindowAncestor(on);
        if (window != null) {
            Point corner = SwingUtilities.convertPoint(on, x, y, window);
            x -= Math.min(0, corner.x);
            y -= Math.max(0, corner.y + size.height - window.getHeight());
        }
        return new Point(x, y);
    }
}

/** A button whose tooltip stays inside the client window. See {@link Tip}. */
class TipButton extends JButton {
    TipButton() {
    }

    TipButton(String text) {
        super(text);
    }

    TipButton(Icon icon) {
        super(icon);
    }

    @Override
    public Point getToolTipLocation(MouseEvent event) {
        return Tip.at(this, event);
    }
}

/** A label whose tooltip stays inside the client window. See {@link Tip}. */
class TipLabel extends JLabel {
    TipLabel(String text, int alignment) {
        super(text, alignment);
    }

    TipLabel(Icon icon) {
        super(icon);
    }

    @Override
    public Point getToolTipLocation(MouseEvent event) {
        return Tip.at(this, event);
    }
}
