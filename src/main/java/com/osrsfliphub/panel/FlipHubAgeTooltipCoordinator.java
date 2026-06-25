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
import static com.osrsfliphub.FlipHubPanelConstants.AGE_TOOLTIP_OFFSET_Y;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
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
    private final FlipHubPanelValueFormatService valueFormatService;
    private final List<CountdownEntry> countdownEntries = new ArrayList<>();
    private final List<AgePairEntry> ageEntries = new ArrayList<>();
    private Timer countdownTimer;
    private JComponent hoveredAgeComponent;
    private AgePairEntry hoveredAgeEntry;
    private int hoveredAgeX;
    private int hoveredAgeY;
    private Popup ageTooltipPopup;
    private JToolTip ageTooltip;

    FlipHubAgeTooltipCoordinator(FlipHubPanelValueFormatService valueFormatService) {
        this.valueFormatService = valueFormatService;
    }

    void clearEntriesAndHide() {
        countdownEntries.clear();
        ageEntries.clear();
        hoveredAgeComponent = null;
        hoveredAgeEntry = null;
        hideAgeTooltip();
    }

    void clearHoverAndHide() {
        hoveredAgeComponent = null;
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
            component.setToolTipText(null);
        }
        installAgeHoverTracking(components);
        updateAgeEntry(entry, System.currentTimeMillis());
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
            hoveredAgeComponent = null;
            hoveredAgeEntry = null;
            hideAgeTooltip();
            if (countdownTimer != null) {
                countdownTimer.stop();
            }
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
    }

    private void updateCountdowns() {
        long now = System.currentTimeMillis();
        for (CountdownEntry entry : countdownEntries) {
            updateCountdownEntry(entry, now);
        }
        for (AgePairEntry entry : ageEntries) {
            updateAgeEntry(entry, now);
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

    private void updateAgeEntry(AgePairEntry entry, long now) {
        if (entry == hoveredAgeEntry && ageTooltip != null) {
            updateAgeTooltipText(entry, now);
        }
    }

    private void installAgeHoverTracking(JComponent... components) {
        for (JComponent component : components) {
            component.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hoveredAgeComponent = component;
                    hoveredAgeX = e.getX();
                    hoveredAgeY = e.getY();
                    AgePairEntry entry = (AgePairEntry) component.getClientProperty(AGE_ENTRY_KEY);
                    if (entry == null) {
                        return;
                    }
                    if (hoveredAgeEntry != entry || ageTooltipPopup == null) {
                        hoveredAgeEntry = entry;
                        showAgeTooltip(entry, component, hoveredAgeX, hoveredAgeY, System.currentTimeMillis());
                    } else {
                        hoveredAgeEntry = entry;
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    AgePairEntry entry = (AgePairEntry) component.getClientProperty(AGE_ENTRY_KEY);
                    if (entry == null || entry != hoveredAgeEntry) {
                        return;
                    }
                    if (isPointerOverAny(entry)) {
                        return;
                    }
                    hoveredAgeComponent = null;
                    hoveredAgeEntry = null;
                    hideAgeTooltip();
                }
            });
            component.addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    hoveredAgeComponent = component;
                    hoveredAgeX = e.getX();
                    hoveredAgeY = e.getY();
                }
            });
        }
    }

    private void refreshAgeTooltip(long now) {
        if (hoveredAgeEntry == null || ageTooltip == null) {
            return;
        }
        updateAgeTooltipText(hoveredAgeEntry, now);
    }

    private void showAgeTooltip(AgePairEntry entry, JComponent component, int x, int y, long now) {
        if (entry == null || component == null || !component.isShowing()) {
            return;
        }
        hideAgeTooltip();
        ageTooltip = component.createToolTip();
        ageTooltip.setOpaque(true);
        ageTooltip.setBackground(new Color(20, 24, 33));
        ageTooltip.setForeground(TEXT);
        ageTooltip.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            BorderFactory.createEmptyBorder(3, 5, 3, 5)
        ));
        updateAgeTooltipText(entry, now);
        Dimension tooltipSize = ageTooltip.getPreferredSize();
        Point componentTopLeft = new Point(0, 0);
        SwingUtilities.convertPointToScreen(componentTopLeft, component);
        int popupX = componentTopLeft.x - tooltipSize.width - AGE_TOOLTIP_LEFT_GAP;
        int popupY = componentTopLeft.y + y + AGE_TOOLTIP_OFFSET_Y;
        Rectangle screenBounds = getUsableScreenBounds(component);
        popupX = clamp(popupX, screenBounds.x, screenBounds.x + screenBounds.width - tooltipSize.width);
        popupY = clamp(popupY, screenBounds.y, screenBounds.y + screenBounds.height - tooltipSize.height);
        ageTooltipPopup = PopupFactory.getSharedInstance().getPopup(component, ageTooltip, popupX, popupY);
        ageTooltipPopup.show();
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

    private void hideAgeTooltip() {
        if (ageTooltipPopup != null) {
            ageTooltipPopup.hide();
            ageTooltipPopup = null;
        }
        ageTooltip = null;
    }

    private boolean isPointerOverAny(AgePairEntry entry) {
        if (entry == null) {
            return false;
        }
        for (JComponent component : entry.components) {
            if (isPointerOver(component)) {
                return true;
            }
        }
        return false;
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

    private boolean isPointerOver(Component component) {
        if (component == null || !component.isShowing()) {
            return false;
        }
        PointerInfo pointerInfo = MouseInfo.getPointerInfo();
        if (pointerInfo == null) {
            return false;
        }
        Point location = pointerInfo.getLocation();
        SwingUtilities.convertPointFromScreen(location, component);
        return component.contains(location);
    }
}
