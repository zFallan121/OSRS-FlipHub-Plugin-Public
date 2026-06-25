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

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;

final class GeOfferSlotBoundsResolver {
    private static final int MIN_SLOT_WIDTH = 80;
    private static final int MAX_SLOT_WIDTH = 200;
    private static final int MIN_SLOT_HEIGHT = 50;
    private static final int MAX_SLOT_HEIGHT = 120;
    private static final int MAX_ICON_SIZE = 40;
    private static final int SLOT_SCAN_COMPONENT_LIMIT = 300;

    private final Client client;

    GeOfferSlotBoundsResolver(Client client) {
        this.client = client;
    }

    List<Rectangle> findSlotBounds(Widget root) {
        if (root == null) {
            return Collections.emptyList();
        }
        Rectangle rootBounds = toCanvasBounds(root);
        List<Rectangle> candidates = new ArrayList<>();
        collectSlotBounds(root, rootBounds, candidates);
        List<Rectangle> unique = dedupeBounds(candidates);
        List<Rectangle> filtered = filterByCommonSize(unique);

        if (filtered.size() < 8) {
            List<Rectangle> fromIcons = findSlotBoundsFromIcons(root);
            if (fromIcons.size() > filtered.size()) {
                filtered = fromIcons;
            }
        }

        if (filtered.size() < 8) {
            List<Rectangle> fromGroup = scanSlotBoundsFromGroup(rootBounds);
            if (fromGroup.size() > filtered.size()) {
                filtered = fromGroup;
            }
        }

        if (filtered.size() > 8) {
            filtered.sort(Comparator.comparingInt((Rectangle bounds) -> bounds.y)
                .thenComparingInt(bounds -> bounds.x));
            return new ArrayList<>(filtered.subList(0, 8));
        }
        return filtered;
    }

    boolean looksLikeMainGrid(List<Rectangle> slotBounds) {
        if (slotBounds == null || slotBounds.isEmpty()) {
            return false;
        }
        if (slotBounds.size() < 6) {
            return false;
        }
        List<Integer> widths = new ArrayList<>();
        List<Integer> heights = new ArrayList<>();
        for (Rectangle rect : slotBounds) {
            widths.add(rect.width);
            heights.add(rect.height);
        }
        Collections.sort(widths);
        Collections.sort(heights);
        int medianWidth = widths.get(widths.size() / 2);
        int medianHeight = heights.get(heights.size() / 2);
        int rowTolerance = Math.max(10, medianHeight / 2);
        int colTolerance = Math.max(10, medianWidth / 2);

        List<Integer> rows = new ArrayList<>();
        List<Integer> cols = new ArrayList<>();
        for (Rectangle rect : slotBounds) {
            addCluster(rows, rect.y, rowTolerance);
            addCluster(cols, rect.x, colTolerance);
        }
        return rows.size() >= 2 && cols.size() >= 4;
    }

    private void addCluster(List<Integer> clusters, int value, int tolerance) {
        for (int i = 0; i < clusters.size(); i++) {
            if (Math.abs(clusters.get(i) - value) <= tolerance) {
                return;
            }
        }
        clusters.add(value);
    }

    private List<Rectangle> findSlotBoundsFromIcons(Widget root) {
        List<Rectangle> results = new ArrayList<>();
        collectSlotIconBounds(root, results);
        return dedupeBounds(results);
    }

    private void collectSlotIconBounds(Widget widget, List<Rectangle> out) {
        if (widget == null) {
            return;
        }
        Rectangle bounds = toCanvasBounds(widget);
        if (bounds != null && widget.getItemId() > 0 && isSlotIconBounds(bounds)) {
            Rectangle slotBounds = resolveSlotBounds(widget);
            if (slotBounds != null) {
                out.add(slotBounds);
            }
        }
        collectSlotIconBounds(widget.getChildren(), out);
        collectSlotIconBounds(widget.getDynamicChildren(), out);
        collectSlotIconBounds(widget.getNestedChildren(), out);
    }

    private void collectSlotIconBounds(Widget[] children, List<Rectangle> out) {
        if (children == null) {
            return;
        }
        for (Widget child : children) {
            collectSlotIconBounds(child, out);
        }
    }

    private void collectSlotBounds(Widget widget, Rectangle rootBounds, List<Rectangle> out) {
        if (widget == null) {
            return;
        }
        Rectangle bounds = toCanvasBounds(widget);
        if (bounds != null && isSlotContainerBounds(bounds, rootBounds)) {
            out.add(bounds);
        }
        collectSlotBounds(widget.getChildren(), rootBounds, out);
        collectSlotBounds(widget.getDynamicChildren(), rootBounds, out);
        collectSlotBounds(widget.getNestedChildren(), rootBounds, out);
    }

    private void collectSlotBounds(Widget[] children, Rectangle rootBounds, List<Rectangle> out) {
        if (children == null) {
            return;
        }
        for (Widget child : children) {
            collectSlotBounds(child, rootBounds, out);
        }
    }

    private boolean isSlotIconBounds(Rectangle bounds) {
        if (bounds == null) {
            return false;
        }
        return bounds.width > 0 && bounds.height > 0
            && bounds.width <= MAX_ICON_SIZE
            && bounds.height <= MAX_ICON_SIZE;
    }

    private List<Rectangle> scanSlotBoundsFromGroup(Rectangle rootBounds) {
        List<Rectangle> results = new ArrayList<>();
        if (client == null) {
            return results;
        }
        int groupId = InterfaceID.GRAND_EXCHANGE;
        for (int component = 0; component <= SLOT_SCAN_COMPONENT_LIMIT; component++) {
            Widget widget = client.getWidget(groupId, component);
            if (widget == null || widget.isHidden()) {
                continue;
            }
            Rectangle bounds = toCanvasBounds(widget);
            if (bounds != null && isSlotContainerBounds(bounds, rootBounds)) {
                results.add(bounds);
            }
        }
        return filterByCommonSize(dedupeBounds(results));
    }

    private boolean isSlotContainerBounds(Rectangle bounds, Rectangle rootBounds) {
        if (bounds == null) {
            return false;
        }
        if (bounds.width < MIN_SLOT_WIDTH || bounds.width > MAX_SLOT_WIDTH) {
            return false;
        }
        if (bounds.height < MIN_SLOT_HEIGHT || bounds.height > MAX_SLOT_HEIGHT) {
            return false;
        }
        if (rootBounds != null) {
            if (!rootBounds.contains(bounds)) {
                return false;
            }
            if (bounds.width > rootBounds.width * 0.75 || bounds.height > rootBounds.height * 0.75) {
                return false;
            }
        }
        return true;
    }

    private List<Rectangle> filterByCommonSize(List<Rectangle> candidates) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        List<Integer> widths = new ArrayList<>();
        List<Integer> heights = new ArrayList<>();
        for (Rectangle rect : candidates) {
            widths.add(rect.width);
            heights.add(rect.height);
        }
        Collections.sort(widths);
        Collections.sort(heights);
        int medianWidth = widths.get(widths.size() / 2);
        int medianHeight = heights.get(heights.size() / 2);
        int widthTolerance = Math.max(10, medianWidth / 4);
        int heightTolerance = Math.max(8, medianHeight / 4);

        List<Rectangle> filtered = new ArrayList<>();
        for (Rectangle rect : candidates) {
            if (Math.abs(rect.width - medianWidth) <= widthTolerance &&
                Math.abs(rect.height - medianHeight) <= heightTolerance) {
                filtered.add(rect);
            }
        }
        return filtered.isEmpty() ? candidates : filtered;
    }

    private Rectangle resolveSlotBounds(Widget widget) {
        Widget current = widget;
        while (current != null) {
            Rectangle bounds = toCanvasBounds(current);
            if (bounds != null && isSlotContainerBounds(bounds, null)) {
                return bounds;
            }
            current = current.getParent();
        }
        return null;
    }

    private List<Rectangle> dedupeBounds(List<Rectangle> candidates) {
        List<Rectangle> unique = new ArrayList<>();
        for (Rectangle rect : candidates) {
            if (!containsSimilarRect(unique, rect)) {
                unique.add(rect);
            }
        }
        return unique;
    }

    private boolean containsSimilarRect(List<Rectangle> rects, Rectangle target) {
        for (Rectangle rect : rects) {
            if (Math.abs(rect.x - target.x) <= 2 &&
                Math.abs(rect.y - target.y) <= 2 &&
                Math.abs(rect.width - target.width) <= 2 &&
                Math.abs(rect.height - target.height) <= 2) {
                return true;
            }
        }
        return false;
    }

    private Rectangle toCanvasBounds(Widget widget) {
        if (widget == null) {
            return null;
        }
        Rectangle bounds = widget.getBounds();
        Point location = widget.getCanvasLocation();
        if (bounds == null || location == null) {
            return null;
        }
        return new Rectangle(location.getX(), location.getY(), bounds.width, bounds.height);
    }
}
