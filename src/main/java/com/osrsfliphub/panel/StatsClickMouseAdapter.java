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

import java.awt.Component;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.SwingUtilities;

/**
 * A click on part of a card, as a person means it rather than as the toolkit defines it.
 *
 * <p>This listened for {@code mouseClicked}, which the toolkit only sends when the pointer
 * has not moved between the press and the release. A few pixels of drift, which is ordinary
 * on a trackpad and common with a mouse, cancels it outright and nothing at all happens. The
 * card then feels like it has dead patches, and which patch is dead changes every time,
 * because the real variable is how steady the hand was rather than where it was.
 *
 * <p>So the press is remembered and the release decides, as long as the pointer is still on
 * the thing that was pressed. That is the same rule a button follows: press it, slide off it
 * and let go, and nothing happens; let go while still on it, and it fires.
 */
final class StatsClickMouseAdapter extends MouseAdapter {
    /**
     * How far the pointer may sit outside the pressed component and still count.
     *
     * <p>A card's parts abut one another with no gaps, so releasing a pixel over the edge of
     * a label is still, to the person doing it, a release on the same row.
     */
    private static final int EDGE_SLACK_PX = 2;

    private final Runnable action;
    private boolean pressed;

    StatsClickMouseAdapter(Runnable action) {
        this.action = action;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        pressed = SwingUtilities.isLeftMouseButton(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        boolean wasPressed = pressed;
        pressed = false;
        if (!wasPressed || !SwingUtilities.isLeftMouseButton(e) || action == null) {
            return;
        }
        if (!releasedOnTheSameThing(e)) {
            // Pressed here and let go somewhere else. Treated as a change of mind.
            return;
        }
        action.run();
    }

    /** Whether the release landed on the component the press did, give or take its edge. */
    private static boolean releasedOnTheSameThing(MouseEvent e) {
        Component source = e.getComponent();
        if (source == null) {
            return false;
        }
        Point point = e.getPoint();
        return point.x >= -EDGE_SLACK_PX
            && point.y >= -EDGE_SLACK_PX
            && point.x < source.getWidth() + EDGE_SLACK_PX
            && point.y < source.getHeight() + EDGE_SLACK_PX;
    }
}
