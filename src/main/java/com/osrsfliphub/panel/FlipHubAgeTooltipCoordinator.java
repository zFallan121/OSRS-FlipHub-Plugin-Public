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

import static com.osrsfliphub.FlipHubPanelConstants.AGE_ENTRY_KEY;
import static com.osrsfliphub.FlipHubPanelConstants.AGE_TOOLTIP_LEFT_GAP;
import static com.osrsfliphub.FlipHubPanelConstants.AGE_TOOLTIP_MIN_WIDTH;
import static com.osrsfliphub.FlipHubPanelConstants.BORDER;
import static com.osrsfliphub.FlipHubPanelConstants.TEXT;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JToolTip;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

final class FlipHubAgeTooltipCoordinator {
    private static final int HOVER_POLL_INTERVAL_MS = 120;

    private final FlipHubPanelValueFormatService valueFormatService;
    private final List<CountdownEntry> countdownEntries = new ArrayList<>();
    private final List<AgePairEntry> ageEntries = new ArrayList<>();
    private Timer countdownTimer;
    private Timer hoverTimer;
    private AgePairEntry hoveredAgeEntry;
    private Popup ageTooltipPopup;
    private JToolTip ageTooltip;

    FlipHubAgeTooltipCoordinator(FlipHubPanelValueFormatService valueFormatService) {
        this.valueFormatService = valueFormatService;
    }

    void clearEntriesAndHide() {
        countdownEntries.clear();
        ageEntries.clear();
        hoveredAgeEntry = null;
        hideAgeTooltip();
    }

    void clearHoverAndHide() {
        hoveredAgeEntry = null;
        hideAgeTooltip();
    }

    void registerAgePair(Long buyTimestampMs, Long sellTimestampMs, LineComponents buyLine, LineComponents sellLine) {
        long buyTs = buyTimestampMs != null ? buyTimestampMs : 0;
        long sellTs = sellTimestampMs != null ? sellTimestampMs : 0;
        JComponent[] components = new JComponent[] {
            buyLine.right,
            sellLine.right
        };
        AgePairEntry entry = new AgePairEntry(components, buyTs, sellTs);
        ageEntries.add(entry);
        for (JComponent component : components) {
            component.putClientProperty(AGE_ENTRY_KEY, entry);
            // Swing's own ToolTipManager would fight the popup managed here.
            component.setToolTipText(null);
        }
    }

    void registerCountdownLabel(JLabel label, Long remainingMs, long asOfMs) {
        if (label == null || remainingMs == null) {
            return;
        }
        long baseTimeMs = asOfMs > 0 ? asOfMs : System.currentTimeMillis();
        CountdownEntry entry = new CountdownEntry(label, remainingMs, baseTimeMs);
        countdownEntries.add(entry);
        updateCountdownEntry(entry, System.currentTimeMillis());
    }

    void ensureCountdownTimer() {
        if (countdownEntries.isEmpty() && ageEntries.isEmpty()) {
            hoveredAgeEntry = null;
            hideAgeTooltip();
            stopTimer(countdownTimer);
            stopTimer(hoverTimer);
            return;
        }
        if (countdownTimer == null) {
            countdownTimer = new Timer(1000, e -> updateCountdowns());
            countdownTimer.setRepeats(true);
        }
        if (!countdownTimer.isRunning()) {
            countdownTimer.start();
        }
        if (hoverTimer == null) {
            hoverTimer = new Timer(HOVER_POLL_INTERVAL_MS, e -> syncHoverFromPointer());
            hoverTimer.setRepeats(true);
        }
        if (!hoverTimer.isRunning()) {
            hoverTimer.start();
        }
        updateCountdowns();
        // renderItems() replaces every label, so the pointer can already be
        // resting on a freshly built row that never saw a mouseEntered.
        // Re-acquire it in the same pass as the rebuild so the tooltip does
        // not blink out while the user is still hovering.
        syncHoverFromPointer();
    }

    /**
     * Visibility follows where the pointer actually is rather than mouse
     * enter/exit events. Those events bind to label instances that
     * renderItems() discards on every refresh, which previously left the
     * tooltip hidden until the user moved off the row and back on.
     */
    private void syncHoverFromPointer() {
        AgePairEntry entry = entryUnderPointer();
        if (entry == null) {
            if (hoveredAgeEntry != null || ageTooltipPopup != null) {
                hoveredAgeEntry = null;
                hideAgeTooltip();
            }
            return;
        }
        if (entry != hoveredAgeEntry || !isTooltipLive()) {
            hoveredAgeEntry = entry;
            showAgeTooltip(entry, System.currentTimeMillis());
        }
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
        return ageTooltipPopup != null && ageTooltip != null && ageTooltip.isShowing();
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
    }

    private void refreshAgeTooltip(long now) {
        if (hoveredAgeEntry == null || ageTooltip == null) {
            return;
        }
        updateAgeTooltipText(hoveredAgeEntry, now);
    }

    private void showAgeTooltip(AgePairEntry entry, long now) {
        JComponent owner = firstShowingComponent(entry);
        Rectangle anchor = entryScreenBounds(entry);
        if (owner == null || anchor == null) {
            return;
        }
        hideAgeTooltip();
        ageTooltip = owner.createToolTip();
        ageTooltip.setOpaque(true);
        ageTooltip.setBackground(new Color(20, 24, 33));
        ageTooltip.setForeground(TEXT);
        ageTooltip.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            BorderFactory.createEmptyBorder(3, 5, 3, 5)
        ));
        updateAgeTooltipText(entry, now);
        Dimension tooltipSize = ageTooltip.getPreferredSize();
        // Centre against the Sell Price / Buy Price rows the ages describe.
        // Anchoring to the pointer drifted the popup down onto later rows.
        int popupX = anchor.x - tooltipSize.width - AGE_TOOLTIP_LEFT_GAP;
        int popupY = anchor.y + (anchor.height - tooltipSize.height) / 2;
        Rectangle screenBounds = getUsableScreenBounds(owner);
        popupX = clamp(popupX, screenBounds.x, screenBounds.x + screenBounds.width - tooltipSize.width);
        popupY = clamp(popupY, screenBounds.y, screenBounds.y + screenBounds.height - tooltipSize.height);
        ageTooltipPopup = PopupFactory.getSharedInstance().getPopup(owner, ageTooltip, popupX, popupY);
        ageTooltipPopup.show();
    }

    private JComponent firstShowingComponent(AgePairEntry entry) {
        if (entry == null) {
            return null;
        }
        for (JComponent component : entry.components) {
            if (component != null && component.isShowing()) {
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
            if (component == null || !component.isShowing()) {
                continue;
            }
            Point topLeft = new Point(0, 0);
            SwingUtilities.convertPointToScreen(topLeft, component);
            Rectangle bounds = new Rectangle(topLeft.x, topLeft.y, component.getWidth(), component.getHeight());
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

    private void stopTimer(Timer timer) {
        if (timer != null) {
            timer.stop();
        }
    }

    private void hideAgeTooltip() {
        if (ageTooltipPopup != null) {
            ageTooltipPopup.hide();
            ageTooltipPopup = null;
        }
        ageTooltip = null;
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

    private boolean isPointerOver(Component component, Point screenPoint) {
        if (component == null || !component.isShowing()) {
            return false;
        }
        Point location = new Point(screenPoint);
        SwingUtilities.convertPointFromScreen(location, component);
        return component.contains(location);
    }

    private void updateAgeTooltipText(AgePairEntry entry, long now) {
        if (ageTooltip == null || entry == null) {
            return;
        }
        ageTooltip.setTipText(buildAgePairTooltip(entry, now));
        ageTooltip.revalidate();
        ageTooltip.doLayout();
        Dimension preferred = ageTooltip.getPreferredSize();
        int width = Math.max(preferred.width, AGE_TOOLTIP_MIN_WIDTH);
        ageTooltip.setPreferredSize(new Dimension(width, preferred.height));
        ageTooltip.setSize(width, preferred.height);
    }

    private String buildAgePairTooltip(AgePairEntry entry, long now) {
        String buyAge = entry.buyTimestampMs > 0
            ? valueFormatService.formatAgeClock(now - entry.buyTimestampMs)
            : "N/A";
        String sellAge = entry.sellTimestampMs > 0
            ? valueFormatService.formatAgeClock(now - entry.sellTimestampMs)
            : "N/A";
        return "<html><div style='font-size:10px;'>"
            + "<span style='color:#22C55E;'>Sell price age:&nbsp;</span>" + sellAge
            + "<br><span style='color:#22C55E;'>Buy price age:&nbsp;</span>" + buyAge
            + "</div></html>";
    }
}
