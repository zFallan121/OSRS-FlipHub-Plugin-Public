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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.ComponentID;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.OverlayUtil;

public class GeOfferTimerOverlay extends Overlay {
    private static final int TEXT_PADDING_X = 4;
    private static final int TEXT_PADDING_Y = 2;
    private static final String[] OFFER_STATUS_MARKERS = new String[] {
        "offer status",
        "you have bought",
        "you have sold",
        "bought a total",
        "sold a total"
    };

    // The eight offer slots are addressable directly, so the normal path never walks the widget
    // tree. The tree-walking resolvers are kept only as a fallback for layouts where the direct
    // lookup comes up empty, and their result is reused until the GE window moves or this window
    // elapses, because those walks cost several milliseconds each.
    private static final int SLOT_COUNT = 8;
    private static final long SCAN_CACHE_MS = 200L;

    private static final long GREEN_THRESHOLD_MS = 5 * 60 * 1000L;
    private static final long YELLOW_THRESHOLD_MS = 30 * 60 * 1000L;

    // The site's state ramp, so the overlay in the game window and the panel beside it agree.
    private static final Color GREEN = new Color(0x34, 0xD3, 0x99);
    private static final Color YELLOW = new Color(0xFB, 0xBF, 0x24);
    private static final Color RED = new Color(0xEF, 0x44, 0x44);

    private final Client client;
    private final PluginConfig config;
    private final GeLifecyclePlugin plugin;
    private final GeOfferStatusWindowDetector statusWindowDetector;
    private final GeOfferSlotBoundsResolver slotBoundsResolver;

    private long lastScanMs;
    private Rectangle lastGeBounds;
    private boolean cachedStatusWindowOpen;
    private List<Rectangle> cachedSlotBounds = Collections.emptyList();

    GeOfferTimerOverlay(Client client, PluginConfig config, GeLifecyclePlugin plugin) {
        this.client = client;
        this.config = config;
        this.plugin = plugin;
        this.statusWindowDetector = new GeOfferStatusWindowDetector(client, OFFER_STATUS_MARKERS);
        this.slotBoundsResolver = new GeOfferSlotBoundsResolver(client);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(OverlayPriority.HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (config == null || !config.showGeOfferTimers()) {
            return null;
        }
        if (client == null) {
            return null;
        }
        Widget geRoot = client.getWidget(ComponentID.GRAND_EXCHANGE_WINDOW_CONTAINER);
        if (geRoot == null || geRoot.isHidden()) {
            return null;
        }
        Widget offerContainer = client.getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER);
        if (offerContainer != null && !offerContainer.isHidden()) {
            return null;
        }
        if (isOfferDetailsOpen()) {
            return null;
        }
        List<Rectangle> slotBounds = resolveSlotBounds(geRoot);
        if (slotBounds.isEmpty()) {
            return null;
        }

        GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
        if (offers == null || offers.length == 0) {
            return null;
        }

        graphics.setFont(FontManager.getRunescapeSmallFont());

        for (int slot = 0; slot < offers.length && slot < slotBounds.size(); slot++) {
            GrandExchangeOffer offer = offers[slot];
            if (offer == null || offer.getState() == GrandExchangeOfferState.EMPTY || offer.getItemId() <= 0) {
                continue;
            }
            Rectangle slotRect = slotBounds.get(slot);
            long lastUpdateMs = plugin.getOfferLastUpdateMs(slot, offer);
            if (lastUpdateMs <= 0) {
                continue;
            }
            long ageMs = Math.max(0, System.currentTimeMillis() - lastUpdateMs);
            String text = formatElapsed(ageMs);
            Color color = getAgeColor(ageMs);
            renderTimerText(graphics, slotRect, text, color);
        }

        return null;
    }

    /**
     * The offer details panel is a known component, so whether it is open is an O(1) question. The
     * marker-text scan is only consulted if that component is missing entirely.
     */
    private boolean isOfferDetailsOpen() {
        Widget details = client.getWidget(InterfaceID.GeOffers.DETAILS);
        if (details != null) {
            return !details.isHidden();
        }
        refreshScanIfStale(client.getWidget(ComponentID.GRAND_EXCHANGE_WINDOW_CONTAINER));
        return cachedStatusWindowOpen;
    }

    /**
     * Reads the eight offer slot bounds straight off their components. Only when that yields
     * nothing does this fall back to the geometry heuristics, which walk the whole GE tree.
     */
    private List<Rectangle> resolveSlotBounds(Widget geRoot) {
        List<Rectangle> bounds = new ArrayList<>(SLOT_COUNT);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            Widget slotWidget = client.getWidget(InterfaceID.GeOffers.INDEX_0 + slot);
            if (slotWidget == null || slotWidget.isHidden()) {
                break;
            }
            Rectangle slotBounds = slotWidget.getBounds();
            if (slotBounds == null || slotBounds.width <= 0 || slotBounds.height <= 0) {
                break;
            }
            bounds.add(slotBounds);
        }
        if (bounds.size() == SLOT_COUNT) {
            return bounds;
        }
        refreshScanIfStale(geRoot);
        return cachedSlotBounds;
    }

    /**
     * Re-runs the fallback widget-tree scans that back {@link #cachedStatusWindowOpen} and
     * {@link #cachedSlotBounds}, but only once the cached result has gone stale. Both scans walk
     * large parts of the interface tree, which costs several milliseconds while the GE item search
     * list is open, so running them per frame visibly drops the frame rate.
     */
    private void refreshScanIfStale(Widget geRoot) {
        if (geRoot == null) {
            cachedStatusWindowOpen = false;
            cachedSlotBounds = Collections.emptyList();
            return;
        }
        Rectangle geBounds = geRoot.getBounds();
        long nowMs = System.currentTimeMillis();
        boolean moved = lastGeBounds == null || !lastGeBounds.equals(geBounds);
        if (!moved && nowMs - lastScanMs < SCAN_CACHE_MS) {
            return;
        }
        lastScanMs = nowMs;
        lastGeBounds = geBounds;

        cachedStatusWindowOpen = statusWindowDetector.isOfferStatusWindowOpen()
            || (plugin != null && plugin.isOfferStatusOpen());
        if (cachedStatusWindowOpen) {
            cachedSlotBounds = Collections.emptyList();
            return;
        }

        List<Rectangle> slotBounds = slotBoundsResolver.findSlotBounds(geRoot);
        if (slotBounds.isEmpty()) {
            cachedSlotBounds = Collections.emptyList();
            return;
        }
        slotBounds.sort(Comparator.comparingInt((Rectangle bounds) -> bounds.y)
            .thenComparingInt(bounds -> bounds.x));
        cachedSlotBounds = slotBoundsResolver.looksLikeMainGrid(slotBounds)
            ? slotBounds
            : Collections.emptyList();
    }

    private void renderTimerText(Graphics2D graphics, Rectangle slotBounds, String text, Color color) {
        FontMetrics metrics = graphics.getFontMetrics();
        int textWidth = metrics.stringWidth(text);
        int x = slotBounds.x + slotBounds.width - textWidth - TEXT_PADDING_X;
        int y = slotBounds.y + metrics.getAscent() + TEXT_PADDING_Y;
        OverlayUtil.renderTextLocation(graphics, new Point(x, y), text, color);
    }

    private Color getAgeColor(long ageMs) {
        if (ageMs <= GREEN_THRESHOLD_MS) {
            return GREEN;
        }
        if (ageMs <= YELLOW_THRESHOLD_MS) {
            return YELLOW;
        }
        return RED;
    }

    private String formatElapsed(long ms) {
        long totalSeconds = Math.max(0, ms / 1000);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        // Locale.US or the clock renders in the viewer's own numerals on locales that use
        // them, so the overlay and the panel beside it would disagree on the same client.
        return String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }
}


