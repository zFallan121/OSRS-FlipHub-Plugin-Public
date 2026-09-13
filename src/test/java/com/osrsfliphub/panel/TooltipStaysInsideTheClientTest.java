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

import java.awt.Point;
import java.awt.event.MouseEvent;
import javax.swing.JToolTip;
import javax.swing.SwingConstants;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * A tooltip in this panel opens to the left of the cursor, never to the right.
 *
 * <p>The panel sits against the client's right edge, and RuneLite makes every tooltip a window
 * of its own so the game canvas cannot cover it. Swing opens that window at the cursor and only
 * pulls it back far enough to fit on the monitor, which is how a tooltip ends up hanging over
 * the desktop behind the client. Opening leftwards is what keeps it in, because then its right
 * edge is the cursor and the cursor is inside the panel.
 */
public class TooltipStaysInsideTheClientTest {
    private static final String LONGER_THAN_THE_PANEL =
        "File this as one activity against Ancient rune armour set (lg)";

    @Test
    public void aTooltipOpensEntirelyToTheLeftOfTheCursor() {
        TipLabel label = new TipLabel("Record", SwingConstants.LEADING);
        label.setToolTipText(LONGER_THAN_THE_PANEL);
        MouseEvent hover = new MouseEvent(label, MouseEvent.MOUSE_MOVED, 0L, 0, 40, 8, 0, false);

        Point opensAt = label.getToolTipLocation(hover);

        JToolTip tip = label.createToolTip();
        tip.setTipText(label.getToolTipText());
        int width = tip.getPreferredSize().width;
        assertTrue("the tooltip must be wide enough for this to be worth asserting", width > 100);
        assertTrue("a tooltip opening at " + opensAt.x + " and " + width + " wide reaches past the"
            + " cursor at " + hover.getX(), opensAt.x + width <= hover.getX());
    }

    /** Below the cursor, so it does not cover the row the cursor is on. */
    @Test
    public void andBelowIt() {
        TipButton button = new TipButton("Record");
        button.setToolTipText("Tick a purchase and a sale");
        MouseEvent hover = new MouseEvent(button, MouseEvent.MOUSE_MOVED, 0L, 0, 12, 9, 0, false);

        assertTrue(button.getToolTipLocation(hover).y > hover.getY());
    }
}
