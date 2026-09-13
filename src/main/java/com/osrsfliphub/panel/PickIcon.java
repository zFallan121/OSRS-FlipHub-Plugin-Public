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
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.Icon;

/**
 * Whether a row has been picked: an empty box, or the box filled with a tick.
 *
 * <p>Drawn rather than typed, for the reason every other mark in the panel is - the text stack
 * leads with Inter, which has no ballot box, and a glyph the face does not carry ships as a tofu
 * square. Laid out on the same 14x14 grid as the clear, sort and activity marks and scaled to the
 * size asked for, so the panel's drawn marks stay one family.
 *
 * <p>Picked is the panel's one action colour with the room's own navy cut out of it, because a
 * tick in white on accent would be a second light tone and the backdrop is the darkest thing
 * there is to cut with.
 */
final class PickIcon implements Icon {
    private static final double GRID = 14.0;
    private static final float STROKE = 1.6f;

    private final int size;
    private final boolean picked;

    PickIcon(int size, boolean picked) {
        this.size = Math.max(6, size);
        this.picked = picked;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        Skin.smooth(g2);
        g2.translate(x, y);
        double scale = size / GRID;
        g2.scale(scale, scale);

        RoundRectangle2D box = new RoundRectangle2D.Double(0.8, 0.8, GRID - 1.6, GRID - 1.6, 4, 4);
        if (picked) {
            g2.setColor(Skin.ACCENT);
            g2.fill(box);
            g2.setColor(Skin.BG);
            g2.setStroke(new BasicStroke(STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Line2D.Double(3.6, 7.2, 6.0, 9.8));
            g2.draw(new Line2D.Double(6.0, 9.8, 10.4, 4.4));
        } else {
            g2.setColor(Skin.CONTROL_BORDER);
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(box);
        }
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
