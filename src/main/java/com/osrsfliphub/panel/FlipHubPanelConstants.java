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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * The panel's half of the FlipHub design system (STYLEGUIDE.md, "Quiet Glass on Deep Navy").
 *
 * <p>Every colour here is the site token of the same name, translated to AWT. The white-alpha
 * tokens keep their alpha rather than being pre-blended, because the panel paints one backdrop
 * at the root — deep navy plus two radial washes — and everything above it is transparent or
 * translucent so that backdrop stays visible. Pre-blending would flatten the washes into slabs,
 * which is the one thing the identity forbids.
 */
final class FlipHubPanelConstants {
    static final String DEFAULT_BASE_URL = "https://www.osrsfliphub.com";
    static final String DISCORD_INVITE_URL = "https://discord.gg/gNakvRzXNX";

    /** The room: --page-bg, and the two washes body paints over it. */
    static final Color BG = new Color(0x05, 0x08, 0x14);
    static final Color GRAD_GREEN = new Color(34, 197, 94, 26);
    static final Color GRAD_BLUE = new Color(59, 130, 246, 26);
    /** --overlay-base: opaque ground for a popup that floats over the panel rather than in it. */
    static final Color OVERLAY_BASE = new Color(0x08, 0x0D, 0x1C);

    /** Text ramp. --muted-2 is the floor for text that carries meaning. */
    static final Color TEXT = new Color(255, 255, 255, 235);
    static final Color MUTED = new Color(255, 255, 255, 158);
    static final Color MUTED_2 = new Color(255, 255, 255, 128);

    /** Hairlines. One separator per surface — a box has to earn itself. */
    static final Color LINE = new Color(255, 255, 255, 20);
    static final Color LINE_STRONG = new Color(255, 255, 255, 31);

    /** Glass card: the gradient, the slate border, the 1px seat and the inset top highlight. */
    static final Color SURFACE_TOP = new Color(255, 255, 255, 11);
    static final Color SURFACE_BOTTOM = new Color(255, 255, 255, 5);
    static final Color SURFACE_BORDER = new Color(148, 163, 184, 41);
    static final Color SURFACE_HIGHLIGHT = new Color(255, 255, 255, 15);
    static final Color SURFACE_SEAT = new Color(0, 0, 0, 110);

    /** Controls are ghosts: a hairline and the text, no fill. --control-border. */
    static final Color CONTROL_BORDER = new Color(255, 255, 255, 36);
    /** Combo boxes cannot go transparent cleanly, so they get the one pre-blended fill. */
    static final Color CONTROL_FILL = new Color(15, 18, 29);

    /** One action colour. There is no second blue. */
    static final Color ACCENT = new Color(0x5B, 0x9F, 0xED);

    /** State colours: green is profit or live, amber is caution, red is loss. Semantic only. */
    static final Color SUCCESS = new Color(0x34, 0xD3, 0x99);
    static final Color WARNING = new Color(0xFB, 0xBF, 0x24);
    static final Color DANGER = new Color(0xEF, 0x44, 0x44);

    static final DateTimeFormatter REFRESH_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    static final int VALUE_RIGHT_PADDING = 4;
    static final int OFFER_VALUE_RIGHT_PADDING = 4;
    static final int SCROLL_UNIT_INCREMENT = 64;
    static final int SCROLL_BLOCK_INCREMENT = 256;
    /** The site's --surface-radius is 18px on a 1200px page; 14 is the same proportion at 225px. */
    static final int CARD_ARC = 14;
    static final int INPUT_ARC = 10;
    /** Width of the button that closes a control row, so every such row ends on one edge. */
    static final int TRAILING_CONTROL_WIDTH = 30;
    static final int TRAILING_CONTROL_GAP = 6;
    /** Pills are fully round. RoundedBorder clamps this to the control's short side. */
    static final int CHIP_ARC = 999;
    static final int AGE_TOOLTIP_LEFT_GAP = 8;
    static final int AGE_TOOLTIP_MIN_WIDTH = 150;
    static final String STATS_CARD_TOGGLE_SKIP_KEY = "fliphub.skipStatsCardToggle";
    static final int STATS_ITEMS_PER_PAGE = 10;

    private FlipHubPanelConstants() {
    }
}
