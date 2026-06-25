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

import static com.osrsfliphub.FlipHubPanelConstants.SCROLL_UNIT_INCREMENT;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.function.Supplier;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

final class FlipHubWheelScrollCoordinator {
    private final Supplier<JScrollPane> activeScrollSupplier;
    private final Component hostComponent;
    private final MouseWheelListener wheelForwarder = this::forwardWheelEvent;
    private AWTEventListener globalWheelListener;

    FlipHubWheelScrollCoordinator(Supplier<JScrollPane> activeScrollSupplier, Component hostComponent) {
        this.activeScrollSupplier = activeScrollSupplier;
        this.hostComponent = hostComponent;
    }

    MouseWheelListener wheelForwarder() {
        return wheelForwarder;
    }

    void installWheelForwarder(Component component) {
        if (component == null) {
            return;
        }
        component.addMouseWheelListener(wheelForwarder);
        if (component instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container) component).getComponents()) {
                installWheelForwarder(child);
            }
        }
    }

    void installGlobalWheelListener() {
        if (globalWheelListener != null) {
            return;
        }
        globalWheelListener = event -> {
            if (!(event instanceof MouseWheelEvent)) {
                return;
            }
            MouseWheelEvent wheelEvent = (MouseWheelEvent) event;
            if (hostComponent == null || !hostComponent.isShowing()) {
                return;
            }
            JScrollPane targetScroll = getActiveScrollPane();
            if (!isPointerOver(targetScroll) && !isPointerOver(hostComponent)) {
                return;
            }
            forwardWheelEvent(wheelEvent);
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(globalWheelListener, AWTEvent.MOUSE_WHEEL_EVENT_MASK);
    }

    void uninstallGlobalWheelListener() {
        if (globalWheelListener != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(globalWheelListener);
            globalWheelListener = null;
        }
    }

    void forwardWheelEvent(MouseWheelEvent e) {
        JScrollPane targetScroll = getActiveScrollPane();
        if (targetScroll == null) {
            return;
        }
        JViewport viewport = targetScroll.getViewport();
        if (viewport == null) {
            return;
        }
        Component view = viewport.getView();
        if (view == null) {
            return;
        }

        Dimension extent = viewport.getExtentSize();
        JScrollBar bar = targetScroll.getVerticalScrollBar();
        Dimension preferredSize = view.getPreferredSize();
        int preferredHeight = preferredSize != null ? preferredSize.height : 0;
        int maxYFromPreferred = Math.max(0, preferredHeight - extent.height);
        int maxYFromBar = bar != null ? Math.max(0, bar.getMaximum() - bar.getVisibleAmount()) : 0;
        int maxY = Math.max(maxYFromPreferred, maxYFromBar);
        if (maxY <= 0) {
            return;
        }

        int direction = e.getWheelRotation() > 0 ? 1 : -1;
        int increment = bar != null ? bar.getUnitIncrement(direction) : 0;
        if (increment <= 0) {
            increment = SCROLL_UNIT_INCREMENT;
        }
        int delta = (int) Math.round(e.getPreciseWheelRotation() * increment);
        if (delta == 0) {
            delta = direction * increment;
        }

        Point viewPos = viewport.getViewPosition();
        int newY = Math.max(0, Math.min(maxY, viewPos.y + delta));
        if (newY != viewPos.y) {
            viewport.setViewPosition(new Point(viewPos.x, newY));
        }
        e.consume();
    }

    private JScrollPane getActiveScrollPane() {
        return activeScrollSupplier != null ? activeScrollSupplier.get() : null;
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
