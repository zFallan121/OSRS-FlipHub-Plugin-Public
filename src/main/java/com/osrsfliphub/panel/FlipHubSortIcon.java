package com.osrsfliphub;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.Icon;

/**
 * Which way a list is sorted: three bars, widest at the top for high to low and
 * narrowest at the top for low to high.
 */
final class FlipHubSortIcon implements Icon {
    private static final double GRID = 14.0;
    private static final double BAR_HEIGHT = 2.3;
    private static final double BAR_PITCH = 4.5;
    private static final double FIRST_BAR_TOP = 2.4;
    private static final double LEFT = 1.0;
    /** Top to bottom, for high to low. Ascending draws the same list backwards. */
    private static final double[] BAR_WIDTHS = {12.0, 8.0, 4.5};
    /** Top of the first bar to the bottom of the last: the mark's own height on the grid. */
    private static final double MARK_HEIGHT = (BAR_WIDTHS.length - 1) * BAR_PITCH + BAR_HEIGHT;

    private final boolean ascending;
    private final int size;

    FlipHubSortIcon(boolean ascending, int size) {
        this.ascending = ascending;
        this.size = Math.max(8, size);
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.translate(x, y);
        double scale = size / GRID;
        g2.scale(scale, scale);
        g2.setColor(c != null && c.getForeground() != null ? c.getForeground() : Color.WHITE);
        for (int bar = 0; bar < BAR_WIDTHS.length; bar++) {
            double width = BAR_WIDTHS[ascending ? BAR_WIDTHS.length - 1 - bar : bar];
            double top = FIRST_BAR_TOP + bar * BAR_PITCH;
            g2.fill(new RoundRectangle2D.Double(LEFT, top, width, BAR_HEIGHT, BAR_HEIGHT, BAR_HEIGHT));
        }
        g2.dispose();
    }

    /**
     * The size to build this icon at for its bars to stand {@code inkHeight} pixels tall.
     */
    static int sizeForMarkHeight(double inkHeight, int fallback) {
        if (!(inkHeight > 0)) {
            return fallback;
        }
        return (int) Math.round(inkHeight * GRID / MARK_HEIGHT);
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
