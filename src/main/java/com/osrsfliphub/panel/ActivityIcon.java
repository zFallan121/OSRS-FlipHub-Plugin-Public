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
import java.awt.geom.*;
import javax.swing.Icon;

/**
 * What a card's activity was, drawn rather than typed.
 *
 * <p>These are painted instead of set as text because a font glyph is not
 * dependable here: the panel's stack has no hammer and no small caret, and the
 * caret shipped as a tofu box before anyone noticed. A path always draws.
 *
 * <p>They are a system, not six pictures. Every shape is solid, because a
 * stroked one turns to mush at the size these ship at, and every pair is a
 * mirror, so the two halves of a reversible trade read as opposites at a
 * glance:
 *
 * <pre>
 *   Flip           two arrows passing - the same item out as went in
 *   Assemble       two arrows pressed in against a seam
 *   Disassemble    the same two driven back out of it   (the mirror)
 *   Repair         a wrench
 *   Set combine    loose bars into a solid block
 *   Set break      a solid block into loose bars        (the mirror)
 * </pre>
 *
 * <p>Everything is laid out on a 14x14 grid and scaled to the requested size,
 * so the proportions hold wherever it is drawn.
 */
final class ActivityIcon implements Icon {
    private static final double GRID = 14.0;
    private static final float STROKE = 1.7f;
    /** Half the width of an arrowhead's base, on the grid. */
    private static final double HEAD_HALF = 3.2;
    /** How far back from the tip an arrowhead reaches. */
    private static final double HEAD_LENGTH = 4.6;

    /** Null means an ordinary flip - the one activity that is not a conversion. */
    private final ConversionKind kind;
    private final int size;
    private final java.awt.Color color;

    ActivityIcon(ConversionKind kind, int size, java.awt.Color color) {
        this.kind = kind;
        this.size = Math.max(8, size);
        this.color = color;
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            Skin.smooth(g);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.translate(x, y);
            double scale = size / GRID;
            g.scale(scale, scale);
            g.setColor(color);
            g.setStroke(new BasicStroke(STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            paintOnGrid(g);
        } finally {
            g.dispose();
        }
    }

    private void paintOnGrid(Graphics2D g) {
        if (kind == null) {
            paintFlip(g);
            return;
        }
        switch (kind) {
            case ASSEMBLE:
                paintMerge(g);
                break;
            case DISASSEMBLE:
                paintSplit(g);
                break;
            case REPAIR:
                paintWrench(g);
                break;
            case SET_COMBINE:
                paintSetCombine(g);
                break;
            case SET_BREAK:
                paintSetBreak(g);
                break;
            default:
                paintFlip(g);
                break;
        }
    }

    /**
     * Two arrows passing in opposite directions: bought, then sold.
     *
     * <p>Solid, not stroked. At the size these actually ship a hairline arrow
     * collapses into a dash and the whole mark reads as an equals sign - the
     * first cut of these did exactly that.
     */
    private void paintFlip(Graphics2D g) {
        g.fill(new Rectangle2D.Double(1.8, 3.5, 7.6, 2.1));
        arrowHead(g, 12.4, 4.55, 1, 0);
        g.fill(new Rectangle2D.Double(4.6, 8.4, 7.6, 2.1));
        arrowHead(g, 1.6, 9.45, -1, 0);
    }

    /** Two solid arrows pressed in against a seam: parts becoming one thing. */
    private void paintMerge(Graphics2D g) {
        seam(g);
        arrowHead(g, 6.35, 7.0, 1, 0);
        arrowHead(g, 7.65, 7.0, -1, 0);
    }

    /** The mirror: the same two arrows driven back out of the seam. */
    private void paintSplit(Graphics2D g) {
        seam(g);
        arrowHead(g, 1.5, 7.0, -1, 0);
        arrowHead(g, 12.5, 7.0, 1, 0);
    }

    /** The line the two halves of a reversible trade meet on. */
    private void seam(Graphics2D g) {
        g.fill(new Rectangle2D.Double(6.65, 1.6, 0.7, 10.8));
    }

    /**
     * A wrench: an open jaw on a diagonal handle. The jaw is a ring with a bite
     * out of its upper right, which is the shape that still reads as a spanner
     * once there are only a dozen pixels to say it in.
     */
    private void paintWrench(Graphics2D g) {
        Area jaw = new Area(new Ellipse2D.Double(6.6, 0.8, 6.8, 6.8));
        jaw.subtract(new Area(new Ellipse2D.Double(8.5, 2.7, 3.0, 3.0)));
        jaw.subtract(new Area(new Rectangle2D.Double(10.0, -0.4, 4.2, 4.0)));
        g.fill(jaw);

        Graphics2D handle = (Graphics2D) g.create();
        handle.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        handle.draw(new Line2D.Double(2.9, 11.4, 8.0, 6.3));
        handle.dispose();
    }

    /** Loose pieces gathered into one solid thing: an armour set, combined. */
    private void paintSetCombine(Graphics2D g) {
        pieces(g, 1.0);
        arrowHead(g, 9.3, 7.0, 1, 0);
        g.fill(new Rectangle2D.Double(10.6, 1.9, 2.7, 10.2));
    }

    /** The mirror: the solid thing broken back into its pieces. */
    private void paintSetBreak(Graphics2D g) {
        g.fill(new Rectangle2D.Double(0.7, 1.9, 2.7, 10.2));
        arrowHead(g, 8.4, 7.0, 1, 0);
        pieces(g, 9.6);
    }

    /** Three stacked bars, the mark for "the parts rather than the whole". */
    private void pieces(Graphics2D g, double left) {
        g.fill(new Rectangle2D.Double(left, 1.9, 3.4, 2.2));
        g.fill(new Rectangle2D.Double(left, 5.9, 3.4, 2.2));
        g.fill(new Rectangle2D.Double(left, 9.9, 3.4, 2.2));
    }

    /** A filled triangle at the tip, pointing along a unit vector. */
    private void arrowHead(Graphics2D g, double tipX, double tipY, double dirX, double dirY) {
        double baseX = tipX - dirX * HEAD_LENGTH;
        double baseY = tipY - dirY * HEAD_LENGTH;
        // The base is perpendicular to the direction of travel.
        double sideX = -dirY * HEAD_HALF;
        double sideY = dirX * HEAD_HALF;

        Path2D head = new Path2D.Double();
        head.moveTo(tipX, tipY);
        head.lineTo(baseX + sideX, baseY + sideY);
        head.lineTo(baseX - sideX, baseY - sideY);
        head.closePath();
        g.fill(head);
    }

    /** What this activity is called, in the plural, for a count beside it. */
    static String pluralLabel(ConversionKind kind) {
        if (kind == null) {
            return "Flips";
        }
        switch (kind) {
            case ASSEMBLE:
                return "Assembles";
            case DISASSEMBLE:
                return "Disassembles";
            case REPAIR:
                return "Repairs";
            case SET_COMBINE:
                return "Set combines";
            case SET_BREAK:
                return "Set breaks";
            default:
                return "Activities";
        }
    }

    /** What this activity is called, for a tooltip. */
    static String singularLabel(ConversionKind kind) {
        if (kind == null) {
            return "Flipped";
        }
        switch (kind) {
            case ASSEMBLE:
                return "Assembled";
            case DISASSEMBLE:
                return "Disassembled";
            case REPAIR:
                return "Repaired";
            case SET_COMBINE:
                return "Armour set combined";
            case SET_BREAK:
                return "Armour set broken";
            default:
                return "Activity";
        }
    }
}
