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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FlipHubHoverRestorerTest {
    /** A card whose hover handler is installed on every descendant, as the panel installs it. */
    private static final class HoverCounter extends MouseAdapter {
        private int entered;

        @Override
        public void mouseEntered(MouseEvent event) {
            entered++;
        }
    }

    private static JPanel laidOutRoot(java.awt.Component... children) {
        JPanel root = new JPanel(null);
        int y = 0;
        for (java.awt.Component child : children) {
            child.setBounds(0, y, 100, 20);
            root.add(child);
            y += 20;
        }
        root.setSize(100, y);
        root.doLayout();
        return root;
    }

    @Test
    public void enterAtRollsOverTheButtonUnderThePoint() {
        JButton button = new JButton("x");
        button.setRolloverEnabled(true);
        JPanel root = laidOutRoot(new JLabel("above"), button);

        FlipHubHoverRestorer.enterAt(root, new Point(50, 30));

        assertTrue(button.getModel().isRollover());
    }

    @Test
    public void enterAtReachesTheNearestListeningAncestorOfTheDeepestComponent() {
        HoverCounter counter = new HoverCounter();
        JLabel value = new JLabel("114 gp");
        JPanel row = new JPanel(null);
        row.add(value);
        value.setBounds(0, 0, 100, 20);
        row.addMouseListener(counter);
        JPanel root = laidOutRoot(row);

        FlipHubHoverRestorer.enterAt(root, new Point(50, 10));

        assertEquals(1, counter.entered);
    }

    /**
     * The remove mark on an item icon is hidden until its tile is entered, so the pass that
     * enters the tile cannot also enter the mark - it did not exist to be hit tested. The second
     * pass is what puts the pointer on what the first one revealed.
     */
    @Test
    public void asecondEnterReachesAControlTheFirstOneRevealed() {
        JButton revealed = new JButton("x");
        revealed.setRolloverEnabled(true);
        revealed.setVisible(false);
        revealed.setBounds(4, 4, 12, 12);
        JLayeredPane tile = new JLayeredPane();
        tile.add(revealed, JLayeredPane.PALETTE_LAYER);
        tile.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                revealed.setVisible(true);
            }
        });
        JPanel root = laidOutRoot(tile);
        Point overTheMark = new Point(10, 10);

        FlipHubHoverRestorer.enterAt(root, overTheMark);

        assertTrue(revealed.isVisible());
        assertEquals(false, revealed.getModel().isRollover());

        FlipHubHoverRestorer.enterAt(root, overTheMark);

        assertTrue(revealed.getModel().isRollover());
    }

    @Test
    public void enterAtIgnoresAPointOutsideTheVisibleArea() {
        JButton button = new JButton("x");
        button.setRolloverEnabled(true);
        JPanel root = laidOutRoot(button);

        FlipHubHoverRestorer.enterAt(root, new Point(50, 400));

        assertEquals(false, button.getModel().isRollover());
    }
}
