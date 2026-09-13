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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import javax.swing.Icon;

/**
 * The mark that empties a search field: two strokes, drawn rather than typed.
 *
 * <p>Typed, this is a multiplication sign standing in for a cross, and every
 * face draws it at a different weight and height - which matters more here than
 * elsewhere, because the mark sits inside a field whose padding is measured in
 * single pixels. Laid out on the same 14x14 grid as the sort and activity marks
 * and scaled to the requested size, so the panel's drawn marks stay one family.
 *
 * <p>The stroke takes the colour of the control it is drawn on, which keeps the
 * resting and hovered states in the one place that already owns them.
 */
final class ClearIcon implements Icon {
    private static final double GRID = 14.0;
    /** Inset from the grid edge: the cross is smaller than its box, as a glyph would be. */
    private static final double INSET = 3.2;
    private static final float STROKE = 1.8f;

    private final int size;

    ClearIcon(int size) {
        this.size = Math.max(6, size);
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        Skin.smooth(g2);
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
