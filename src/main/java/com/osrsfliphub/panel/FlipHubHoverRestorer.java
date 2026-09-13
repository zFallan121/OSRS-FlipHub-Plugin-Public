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
 */
final class FlipHubHoverRestorer {
    private FlipHubHoverRestorer() {
    }

    /**
     * Re-enters whatever now sits under the pointer inside {@code root}.
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
