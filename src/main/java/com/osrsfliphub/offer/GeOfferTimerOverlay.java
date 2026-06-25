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
import java.util.Comparator;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Point;
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

    private static final long GREEN_THRESHOLD_MS = 5 * 60 * 1000L;
    private static final long YELLOW_THRESHOLD_MS = 30 * 60 * 1000L;

    private static final Color GREEN = new Color(16, 185, 129);
    private static final Color YELLOW = new Color(251, 191, 36);
    private static final Color RED = new Color(239, 68, 68);

    private final Client client;
    private final PluginConfig config;
    private final GeLifecyclePlugin plugin;
    private final GeOfferStatusWindowDetector statusWindowDetector;
    private final GeOfferSlotBoundsResolver slotBoundsResolver;

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
        if (statusWindowDetector.isOfferStatusWindowOpen()) {
            return null;
        }
        if (plugin != null && plugin.isOfferStatusOpen()) {
            return null;
        }
        List<Rectangle> slotBounds = slotBoundsResolver.findSlotBounds(geRoot);
        if (slotBounds.isEmpty()) {
            return null;
        }
        slotBounds.sort(Comparator.comparingInt((Rectangle bounds) -> bounds.y)
            .thenComparingInt(bounds -> bounds.x));
        if (!slotBoundsResolver.looksLikeMainGrid(slotBounds)) {
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
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}


