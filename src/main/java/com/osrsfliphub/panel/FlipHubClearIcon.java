package com.osrsfliphub;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import javax.swing.Icon;

/**
 * The mark that empties a search field: two strokes, drawn rather than typed.
 */
final class FlipHubClearIcon implements Icon {
    private static final double GRID = 14.0;
    /** Inset from the grid edge: the cross is smaller than its box, as a glyph would be. */
    private static final double INSET = 3.2;
    private static final float STROKE = 1.8f;

    private final int size;

    FlipHubClearIcon(int size) {
        this.size = Math.max(6, size);
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.translate(x, y);
        double scale = size / GRID;
        g2.scale(scale, scale);
        g2.setColor(c != null && c.getForeground() != null ? c.getForeground() : Color.WHITE);
        g2.setStroke(new BasicStroke(STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double near = INSET;
        double far = GRID - INSET;
        g2.draw(new Line2D.Double(near, near, far, far));
        g2.draw(new Line2D.Double(far, near, near, far));
        g2.dispose();
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }
}
