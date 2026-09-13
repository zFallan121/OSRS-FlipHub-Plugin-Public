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
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Window;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * Puts the pointer back over whatever it was already over.
 *
 * <p>Swing decides what is hovered from the pointer crossing an edge, not from where the pointer
 * is. A component built underneath a stationary pointer is never told the pointer is there, and
 * one pulled out from under it takes the hover with it - so a list that rebuilds itself every
 * refresh drops every hover it was holding, once per refresh, and none of them come back until
 * the mouse is moved. That is the highlight going off a row you are still pointing at, the star
 * losing its state under the cursor, the tooltip you were reading closing itself.
 *
 * <p>This restates what is true once the rebuild has landed: the pointer never moved, and
 * whatever now sits under it has been entered.
 */
final class FlipHubHoverRestorer {
    private FlipHubHoverRestorer() {
    }

    /**
     * Re-enters whatever now sits under the pointer inside {@code root}.
     *
     * <p>Deferred, because the components it has to hit test are laid out by the validation the
     * rebuild just queued: asked any earlier, every one of them is still at the origin.
     */
    static void restoreAfterRebuild(JComponent root) {
        if (root == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            enterUnderPointer(root);
            // A second pass, because the first one can reveal what the pointer is really over:
            // the remove mark on an item icon is hidden until its tile is entered, and a control
            // that was not there to be hit tested cannot have been entered with it. The pass that
            // finds nothing new does nothing - entering what is already entered is a no-op.
            SwingUtilities.invokeLater(() -> enterUnderPointer(root));
        });
    }

    private static void enterUnderPointer(JComponent root) {
        if (!root.isShowing() || !isOwnerWindowActive(root)) {
            return;
        }
        PointerInfo pointer = MouseInfo.getPointerInfo();
        if (pointer == null) {
            return;
        }
        Point local = pointer.getLocation();
        SwingUtilities.convertPointFromScreen(local, root);
        enterAt(root, local);
    }

    /**
     * A screen coordinate says nothing about who owns the pixel under it: the pointer can sit at
     * a point inside this panel's bounds while another application is drawn over it. A real
     * mouseEntered carries that context; a coordinate read out of the pointer does not, so the
     * window has to be the active one before the coordinate is worth anything.
     */
    private static boolean isOwnerWindowActive(JComponent root) {
        Window window = SwingUtilities.getWindowAncestor(root);
        return window != null && window.isActive();
    }

    /**
     * The hit test and the events, given a point in {@code root}'s own coordinates. Split from
     * the pointer lookup so it can be exercised without a pointer to look up.
     */
    static void enterAt(JComponent root, Point local) {
        // The visible rectangle rather than the bounds: a list inside a scroll pane is taller
        // than the window it shows through, and a pointer resting below that window still lands
        // inside the list's own coordinates - on a row that is scrolled out of sight.
        if (root == null || local == null || !root.getVisibleRect().contains(local)) {
            return;
        }
        Component target = listeningComponentAt(root, local);
        if (target == null) {
            return;
        }
        Point atTarget = SwingUtilities.convertPoint(root, local, target);
        long now = System.currentTimeMillis();
        // One component, the way Swing does it: the enter goes to the deepest thing listening for
        // it, and a card that wants the whole of itself hovered listens on every descendant.
        target.dispatchEvent(new MouseEvent(
            target, MouseEvent.MOUSE_ENTERED, now, 0, atTarget.x, atTarget.y, 0, false));
        target.dispatchEvent(new MouseEvent(
            target, MouseEvent.MOUSE_MOVED, now, 0, atTarget.x, atTarget.y, 0, false));
    }

    private static Component listeningComponentAt(JComponent root, Point local) {
        Component deepest = SwingUtilities.getDeepestComponentAt(root, local.x, local.y);
        for (Component walk = deepest; walk != null; walk = walk.getParent()) {
            if (walk.getMouseListeners().length > 0 || walk.getMouseMotionListeners().length > 0) {
                return walk;
            }
            if (walk == root) {
                return null;
            }
        }
        return null;
    }
}
