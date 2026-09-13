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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.Icon;

/**
 * Which way a list is sorted: three bars, widest at the top for high to low and
 * narrowest at the top for low to high.
 *
 * <p>Drawn rather than typed for the reason {@link FlipHubActivityIcon} is, and
 * for one more. The mark this replaces was a down-pointing triangle - the same
 * mark every dropdown in the panel uses to say "this opens". A control that
 * toggles cannot borrow the mark that means expand, or the two stop meaning
 * anything; so the direction takes a shape of its own and the triangle goes
 * back to saying one thing.
 *
 * <p>Laid out on the same 14x14 grid as the activity icons and scaled to the
 * requested size, so the two read as one family. The bars take the colour of
 * the control they are drawn on, which keeps the state colour set in one place.
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
     *
     * <p>A drawn mark and a typed glyph are sized by different things - one by the grid it is
     * laid out on, the other by whatever the face makes of a point size - so a mark that has to
     * match a glyph beside it has to be told that glyph's height rather than handed the same
     * number. Unmeasurable ink - an empty string, or a face without the character - takes the
     * caller's fallback.
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
