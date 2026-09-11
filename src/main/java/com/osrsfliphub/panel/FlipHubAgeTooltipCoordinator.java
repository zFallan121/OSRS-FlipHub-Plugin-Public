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

import static com.osrsfliphub.FlipHubPanelConstants.AGE_TOOLTIP_LEFT_GAP;
import static com.osrsfliphub.FlipHubPanelConstants.LINE_STRONG;
import static com.osrsfliphub.FlipHubPanelConstants.MUTED_2;
import static com.osrsfliphub.FlipHubPanelConstants.OVERLAY_BASE;
import static com.osrsfliphub.FlipHubPanelConstants.TEXT;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.HierarchyBoundsAdapter;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JToolTip;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

final class FlipHubAgeTooltipCoordinator {
    private final FlipHubPanelValueFormatService valueFormatService;
    private final List<CountdownEntry> countdownEntries = new ArrayList<>();
    private final List<AgePairEntry> ageEntries = new ArrayList<>();
    private Timer countdownTimer;
    private AgePairEntry hoveredAgeEntry;
    private boolean awaitingRebind;
    private JWindow ageTooltipWindow;
    private JToolTip ageTooltip;
    private Point ageTooltipPoint;
    /**
     * The widest this popup has had to be since it appeared.
     *
     * <p>It never shrinks while it is up. The ages tick every second and the digits are not all
     * the same width, so a popup sized to the text exactly would twitch in and out by a pixel
     * or two as the clock ran. Holding the widest it has needed keeps it still, and starting
     * again at nothing each time it appears keeps one long row from bloating the next.
     */
    private int ageTooltipWidth;

    FlipHubAgeTooltipCoordinator(FlipHubPanelValueFormatService valueFormatService) {
        this.valueFormatService = valueFormatService;
    }

    /**
     * Drops the rows a rebuild is about to discard, and deliberately leaves any popup on screen.
     * The data refresh replaces every label a few times a minute; the pointer has not moved and
     * the rows coming back describe the same item, so disposing the window here and building a
     * new one milliseconds later is exactly what the user sees as a blink. The
     * {@link #syncHoverFromPointer()} at the end of the rebuild re-points it or, if the pointer
     * has genuinely left, hides it.
     */
    void clearEntriesForRebuild() {
        countdownEntries.clear();
        ageEntries.clear();
        // The components this pointed at are about to leave the panel; the popup itself stays.
        hoveredAgeEntry = null;
        // Every "is the pointer still over a row?" check is suspended until the replacement rows
        // have been laid out. Asked before that, the answer is always no - the new labels have
        // no bounds yet - and acting on that no is what closed the popup on each refresh.
        awaitingRebind = isTooltipLive();
    }

    void clearHoverAndHide() {
        hoveredAgeEntry = null;
        hideAgeTooltip();
    }

    /**
     * New timestamps for a pair that is already registered, for a refresh that reuses the rows it
     * is refreshing. A live popup is retexted in place, because the pointer is still on the row
     * that owns it and the age it is showing has just moved on.
     */
    void updateAgePair(AgePairEntry entry, Long buyTimestampMs, Long sellTimestampMs) {
        if (entry == null) {
            return;
        }
        entry.buyTimestampMs = buyTimestampMs != null ? buyTimestampMs : 0;
        entry.sellTimestampMs = sellTimestampMs != null ? sellTimestampMs : 0;
        if (hoveredAgeEntry == entry) {
            syncAgeTooltip(System.currentTimeMillis());
        }
    }

    /**
     * Stops ticking a label whose row no longer has a limit running. Without this the clock keeps
     * counting from the base it last had, which is a number for something that is not happening.
     */
    void releaseCountdown(CountdownEntry entry) {
        if (entry != null) {
            countdownEntries.remove(entry);
        }
    }

    /** The same, for the countdown on a row that is being refreshed rather than rebuilt. */
    void updateCountdown(CountdownEntry entry, Long remainingMs, long asOfMs) {
        if (entry == null || remainingMs == null) {
            return;
        }
        entry.rebase(remainingMs, asOfMs > 0 ? asOfMs : System.currentTimeMillis());
        updateCountdownEntry(entry, System.currentTimeMillis());
    }

    AgePairEntry registerAgePair(Long buyTimestampMs, Long sellTimestampMs, LineComponents buyLine, LineComponents sellLine) {
        long buyTs = buyTimestampMs != null ? buyTimestampMs : 0;
        long sellTs = sellTimestampMs != null ? sellTimestampMs : 0;
        JComponent[] components = new JComponent[] {
            buyLine.right,
            sellLine.right
        };
        AgePairEntry entry = new AgePairEntry(components, buyTs, sellTs);
        ageEntries.add(entry);
        MouseAdapter hoverListener = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                showEntry(entry);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hideUnlessStillHovered(entry);
            }
        };
        HierarchyBoundsAdapter scrollListener = new HierarchyBoundsAdapter() {
            @Override
            public void ancestorMoved(HierarchyEvent e) {
                // Scrolling moves the row's ancestor, not the row, and delivers no mouse event,
                // so without this the popup keeps the screen position it was born at.
                if (hoveredAgeEntry == entry) {
                    syncAgeTooltip(System.currentTimeMillis());
                }
            }
        };
        for (JComponent component : components) {
            // Swing's own ToolTipManager would fight the popup managed here.
            component.setToolTipText(null);
            component.addMouseListener(hoverListener);
            component.addHierarchyBoundsListener(scrollListener);
        }
        return entry;
    }

    private void showEntry(AgePairEntry entry) {
        if (entry == null) {
            return;
        }
        if (entry != hoveredAgeEntry || !isTooltipLive()) {
            hoveredAgeEntry = entry;
            showAgeTooltip(entry, System.currentTimeMillis());
        }
    }

    /**
     * The two rows of one entry are separate labels, so crossing between them
     * fires an exit before the matching enter. Re-check the pointer so the popup
     * only closes when it has genuinely left the pair.
     */
    private void hideUnlessStillHovered(AgePairEntry entry) {
        if (hoveredAgeEntry != entry) {
            return;
        }
        if (entryUnderPointer() != entry) {
            hoveredAgeEntry = null;
            hideAgeTooltip();
        }
    }

    CountdownEntry registerCountdownLabel(JLabel label, Long remainingMs, long asOfMs) {
        if (label == null || remainingMs == null) {
            return null;
        }
        long baseTimeMs = asOfMs > 0 ? asOfMs : System.currentTimeMillis();
        CountdownEntry entry = new CountdownEntry(label, remainingMs, baseTimeMs);
        countdownEntries.add(entry);
        updateCountdownEntry(entry, System.currentTimeMillis());
        return entry;
    }

    void ensureCountdownTimer() {
        if (countdownEntries.isEmpty() && ageEntries.isEmpty()) {
            awaitingRebind = false;
            hoveredAgeEntry = null;
            hideAgeTooltip();
            stopTimer(countdownTimer);
            return;
        }
        if (countdownTimer == null) {
            countdownTimer = new Timer(1000, e -> updateCountdowns());
            countdownTimer.setRepeats(true);
        }
        if (!countdownTimer.isRunning()) {
            countdownTimer.start();
        }
        updateCountdowns();
        // renderItems() replaces every label, so the pointer can already be resting on a freshly
        // built row that never saw a mouseEntered, and it has to be re-acquired for it.
        if (!awaitingRebind) {
            syncHoverFromPointer();
            return;
        }
        // The rows went in moments ago and are still unlaid-out: revalidate() only schedules the
        // layout pass. Queue behind it so the question is asked of rows that have bounds.
        SwingUtilities.invokeLater(() -> {
            awaitingRebind = false;
            syncHoverFromPointer();
        });
    }

    /**
     * One-shot re-acquire, run only after a rebuild. renderItems() discards the
     * labels the hover listeners were bound to, so a pointer already resting on a
     * row would otherwise wait for a mouseEntered that never comes until the user
     * moves off and back on.
     *
     * <p>MouseInfo reports a raw screen coordinate with no notion of which
     * application owns that pixel or of the scroll pane's clip, so this path is
     * gated on the window being active; mouseEntered/mouseExited carry that
     * context themselves and need no such guard.
     */
    private void syncHoverFromPointer() {
        AgePairEntry entry = isOwnerWindowActive() ? entryUnderPointer() : null;
        if (entry == null) {
            if (hoveredAgeEntry != null || ageTooltipWindow != null) {
                hoveredAgeEntry = null;
                hideAgeTooltip();
            }
            return;
        }
        showEntry(entry);
    }

    private boolean isOwnerWindowActive() {
        for (AgePairEntry entry : ageEntries) {
            for (JComponent component : entry.components) {
                if (component == null) {
                    continue;
                }
                Window window = SwingUtilities.getWindowAncestor(component);
                if (window != null) {
                    return window.isActive();
                }
            }
        }
        return false;
    }

    private AgePairEntry entryUnderPointer() {
        Point pointer = pointerLocation();
        if (pointer == null) {
            return null;
        }
        for (AgePairEntry entry : ageEntries) {
            if (isPointerOverAny(entry, pointer)) {
                return entry;
            }
        }
        return null;
    }

    private boolean isTooltipLive() {
        return ageTooltipWindow != null && ageTooltip != null && ageTooltipWindow.isShowing();
    }

    private void updateCountdowns() {
        long now = System.currentTimeMillis();
        for (CountdownEntry entry : countdownEntries) {
            updateCountdownEntry(entry, now);
        }
        refreshAgeTooltip(now);
    }

    private void updateCountdownEntry(CountdownEntry entry, long now) {
        long remaining = entry.baseRemainingMs - (now - entry.baseTimeMs);
        if (remaining < 0) {
            remaining = 0;
        }
        entry.label.setText(valueFormatService.formatDuration(remaining));
        if (remaining <= 0) {
            // The row was coloured amber when it was built because a limit was running. It is not
            // running any more, and the rebuild that would re-colour it may be half a minute off.
            entry.label.setForeground(MUTED_2);
        }
    }

    private void refreshAgeTooltip(long now) {
        syncAgeTooltip(now);
    }

    /**
     * Keeps a live popup honest: right text, right place, still on screen.
     *
     * <p>The window is moved rather than rebuilt, and only when the anchor has actually
     * shifted, so a stationary hover never flickers.</p>
     */
    private void syncAgeTooltip(long now) {
        JComponent owner = hoveredAgeEntry != null ? firstShowingComponent(hoveredAgeEntry) : null;
        if (owner == null) {
            // Either nothing is hovered, or renderItems() swapped in fresh labels underneath a
            // stationary pointer. Re-derive from the pointer rather than waiting for a
            // mouseEntered that will never arrive - unless a rebuild is still in flight, in
            // which case the rows cannot answer yet and the deferred pass will do it.
            if (!awaitingRebind) {
                syncHoverFromPointer();
            }
            return;
        }
        if (!isTooltipLive()) {
            showAgeTooltip(hoveredAgeEntry, now);
            return;
        }
        updateAgeTooltipContent(hoveredAgeEntry, now, owner);
    }

    /** Right text, right size, right place - without replacing the window that holds them. */
    private void updateAgeTooltipContent(AgePairEntry entry, long now, JComponent owner) {
        updateAgeTooltipText(entry, now);
        // An age gains a digit as it counts up. Without the repack the window keeps the width it
        // was first packed at and the longer text is clipped inside it.
        if (!ageTooltip.getPreferredSize().equals(ageTooltipWindow.getSize())) {
            ageTooltipWindow.pack();
        }
        Point target = tooltipPointFor(entry, owner, ageTooltipWindow.getSize());
        if (target != null && !target.equals(ageTooltipPoint)) {
            ageTooltipWindow.setLocation(target.x, target.y);
            ageTooltipPoint = target;
        }
    }

    /** Centred against the rows the ages describe; anchoring to the pointer drifted it downward. */
    private Point tooltipPointFor(AgePairEntry entry, JComponent owner, Dimension size) {
        Rectangle anchor = entryScreenBounds(entry);
        if (anchor == null || owner == null || size == null) {
            return null;
        }
        int x = anchor.x - size.width - AGE_TOOLTIP_LEFT_GAP;
        int y = anchor.y + (anchor.height - size.height) / 2;
        Rectangle screen = getUsableScreenBounds(owner);
        x = clamp(x, screen.x, screen.x + screen.width - size.width);
        y = clamp(y, screen.y, screen.y + screen.height - size.height);
        return new Point(x, y);
    }

    private void showAgeTooltip(AgePairEntry entry, long now) {
        JComponent owner = firstShowingComponent(entry);
        if (owner == null) {
            return;
        }
        Window ownerWindow = SwingUtilities.getWindowAncestor(owner);
        if (ownerWindow == null) {
            return;
        }
        // A popup already up is re-pointed at the new rows rather than torn down: same window,
        // new text and position. Only a first show, or one whose owner window has changed,
        // builds anything.
        if (isTooltipLive() && ageTooltipWindow.getOwner() == ownerWindow) {
            updateAgeTooltipContent(entry, now, owner);
            return;
        }
        hideAgeTooltip();
        ageTooltipWidth = 0;
        ageTooltip = owner.createToolTip();
        ageTooltip.setOpaque(true);
        // --overlay-base: a popup floating over the panel is a window, not a surface in it, so
        // it gets the opaque ground rather than glass that would show the list through itself.
        ageTooltip.setBackground(OVERLAY_BASE);
        ageTooltip.setForeground(TEXT);
        ageTooltip.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(LINE_STRONG),
            BorderFactory.createEmptyBorder(3, 5, 3, 5)
        ));
        updateAgeTooltipText(entry, now);
        Point point = tooltipPointFor(entry, owner, ageTooltip.getPreferredSize());
        if (point == null) {
            ageTooltip = null;
            return;
        }
        // Owned window rather than PopupFactory. A lightweight popup lives in the frame's layered
        // pane, where this panel's translucent backdrop repaints over it while isShowing() still
        // reports true - the popup looked alive to every check here but had no pixels left. A
        // child window cannot be painted over, and can be moved instead of rebuilt.
        ageTooltipWindow = new JWindow(ownerWindow);
        ageTooltipWindow.setFocusableWindowState(false);
        ageTooltipWindow.setContentPane(ageTooltip);
        ageTooltipWindow.pack();
        ageTooltipWindow.setLocation(point.x, point.y);
        ageTooltipWindow.setVisible(true);
        ageTooltipPoint = point;
    }

    private JComponent firstShowingComponent(AgePairEntry entry) {
        if (entry == null) {
            return null;
        }
        for (JComponent component : entry.components) {
            if (isOnScreen(component)) {
                return component;
            }
        }
        return null;
    }

    private Rectangle entryScreenBounds(AgePairEntry entry) {
        if (entry == null) {
            return null;
        }
        Rectangle union = null;
        for (JComponent component : entry.components) {
            if (!isOnScreen(component)) {
                continue;
            }
            Rectangle visible = component.getVisibleRect();
            Point topLeft = new Point(visible.x, visible.y);
            SwingUtilities.convertPointToScreen(topLeft, component);
            Rectangle bounds = new Rectangle(topLeft.x, topLeft.y, visible.width, visible.height);
            union = union == null ? bounds : union.union(bounds);
        }
        return union;
    }

    private Rectangle getUsableScreenBounds(Component component) {
        java.awt.GraphicsConfiguration gc = component != null ? component.getGraphicsConfiguration() : null;
        if (gc == null) {
            Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
            return new Rectangle(0, 0, size.width, size.height);
        }
        Rectangle bounds = gc.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        int x = bounds.x + insets.left;
        int y = bounds.y + insets.top;
        int width = Math.max(1, bounds.width - insets.left - insets.right);
        int height = Math.max(1, bounds.height - insets.top - insets.bottom);
        return new Rectangle(x, y, width, height);
    }

    private int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Stops the countdown for good. It otherwise only stops when both entry lists empty, which
     * closing the panel never does, so the timer kept ticking on a panel nobody could see.
     */
    void shutDown() {
        stopTimer(countdownTimer);
        countdownTimer = null;
        clearHoverAndHide();
    }

    private void stopTimer(Timer timer) {
        if (timer != null) {
            timer.stop();
        }
    }

    private void hideAgeTooltip() {
        if (ageTooltipWindow != null) {
            ageTooltipWindow.setVisible(false);
            ageTooltipWindow.dispose();
            ageTooltipWindow = null;
        }
        ageTooltip = null;
        ageTooltipPoint = null;
    }

    private Point pointerLocation() {
        PointerInfo pointerInfo = MouseInfo.getPointerInfo();
        return pointerInfo != null ? pointerInfo.getLocation() : null;
    }

    private boolean isPointerOverAny(AgePairEntry entry, Point screenPoint) {
        if (entry == null) {
            return false;
        }
        for (JComponent component : entry.components) {
            if (isPointerOver(component, screenPoint)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPointerOver(JComponent component, Point screenPoint) {
        if (!isOnScreen(component)) {
            return false;
        }
        Point location = new Point(screenPoint);
        SwingUtilities.convertPointFromScreen(location, component);
        // getVisibleRect() intersects the component with every ancestor's clip,
        // unlike contains(), which only measures the component's own bounds. A row
        // scrolled out of the viewport keeps valid bounds and isShowing() == true,
        // so contains() matched pointer positions well outside the RuneLite window.
        return component.getVisibleRect().contains(location);
    }

    /**
     * True only when the component occupies pixels the user can currently see.
     * isShowing() stays true for a minimised frame and for rows clipped away by
     * the scroll pane, both of which previously let the popup appear over other
     * applications.
     */
    private boolean isOnScreen(JComponent component) {
        if (component == null || !component.isShowing() || component.getVisibleRect().isEmpty()) {
            return false;
        }
        Window window = SwingUtilities.getWindowAncestor(component);
        if (window == null || !window.isShowing()) {
            return false;
        }
        return !(window instanceof Frame)
            || (((Frame) window).getExtendedState() & Frame.ICONIFIED) == 0;
    }

    private void updateAgeTooltipText(AgePairEntry entry, long now) {
        if (ageTooltip == null || entry == null) {
            return;
        }
        String text = buildAgePairTooltip(entry, now);
        if (text.equals(ageTooltip.getTipText())) {
            return;
        }
        ageTooltip.setTipText(text);
        ageTooltip.setPreferredSize(null);
        Dimension preferred = ageTooltip.getPreferredSize();
        ageTooltipWidth = Math.max(ageTooltipWidth, preferred.width);
        ageTooltip.setPreferredSize(new Dimension(ageTooltipWidth, preferred.height));
        if (ageTooltipWindow != null) {
            ageTooltipWindow.pack();
        }
    }

    private String buildAgePairTooltip(AgePairEntry entry, long now) {
        // Each age carries the same colour its price carries on the card, so an amber price and
        // the age that made it amber are visibly the same fact. Labels stay plain: colour on
        // this panel marks the state of a value, never decorates the word in front of it.
        return "<html><div style='font-size:10px;'>"
            + "Sell price age:&nbsp;" + agePart(entry.sellTimestampMs, now)
            + "<br>Buy price age:&nbsp;" + agePart(entry.buyTimestampMs, now)
            + "</div></html>";
    }

    private String agePart(long timestampMs, long now) {
        if (timestampMs <= 0) {
            return "N/A";
        }
        String age = valueFormatService.formatAgeClock(now - timestampMs);
        return "<span style='color:"
            + FlipHubPanelConstants.toHex(FlipHubPanelConstants.priceAgeColor(timestampMs, now))
            + ";'>" + age + "</span>";
    }
}
