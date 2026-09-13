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
final class Skin {
    static final String DEFAULT_BASE_URL = "https://www.osrsfliphub.com";
    static final String DISCORD_INVITE_URL = "https://www.osrsfliphub.com/discord";

    /** The room: --page-bg, and the two washes body paints over it. */
    static final Color BG = new Color(0x05, 0x08, 0x14);
    static final Color GRAD_GREEN = new Color(34, 197, 94, 26);
    static final Color GRAD_BLUE = new Color(59, 130, 246, 26);
    /** --overlay-base: opaque ground for a popup that floats over the panel rather than in it. */
    static final Color OVERLAY_BASE = new Color(0x08, 0x0D, 0x1C);
    /**
     * The ground a mark drawn over an item icon brings with it. An icon is a full-colour sprite,
     * so a bare glyph laid on one is legible over a herb and invisible over a rune - the disc is
     * what gives the mark contrast that does not depend on which item the row happens to be.
     */
    static final Color ICON_SCRIM = new Color(0x05, 0x08, 0x14, 224);

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
    /** The same edge under the pointer, on a card that can be opened. */
    static final Color SURFACE_BORDER_HOVER = new Color(148, 163, 184, 110);
    static final Color SURFACE_HIGHLIGHT = new Color(255, 255, 255, 15);
    static final Color SURFACE_SEAT = new Color(0, 0, 0, 110);
    /**
     * The floor of a section inside a card.
     *
     * <p>Black rather than white, so a section reads as cut into the card rather than laid on
     * top of it. Every other surface in the panel lightens; this is the only one that sinks,
     * which is what lets it group a block of rows and separate it from the next block without
     * a rule or a gap doing the work.
     */
    static final Color SURFACE_WELL = new Color(0, 0, 0, 54);
    /**
     * A search field, cut into the panel rather than laid on it.
     *
     * <p>Darker along the top and lighter towards the bottom, because that is where light
     * coming from above would fall inside a recess. A gradient rather than a drawn line: the
     * depth reads the same and there is no edge to catch the eye as a rule or a divider.
     */
    static final Color INPUT_WELL_TOP = new Color(0, 0, 0, 86);
    static final Color INPUT_WELL_BOTTOM = new Color(0, 0, 0, 28);

    /** Controls are ghosts: a hairline and the text, no fill. --control-border. */
    static final Color CONTROL_BORDER = new Color(255, 255, 255, 36);
    /**
     * The same hairline under the pointer. A ghost control has no fill to
     * brighten, so the rule is the whole affordance - it has to move enough to
     * read as "this does something" without becoming a second kind of object.
     */
    static final Color CONTROL_BORDER_HOVER = new Color(255, 255, 255, 92);
    /** Combo boxes cannot go transparent cleanly, so they get the one pre-blended fill. */
    static final Color CONTROL_FILL = new Color(15, 18, 29);

    /** One action colour. There is no second blue. */
    static final Color ACCENT = new Color(0x5B, 0x9F, 0xED);

    /** State colours: green is profit or live, amber is caution, red is loss. Semantic only. */
    static final Color SUCCESS = new Color(0x34, 0xD3, 0x99);
    static final Color WARNING = new Color(0xFB, 0xBF, 0x24);
    static final Color DANGER = new Color(0xEF, 0x44, 0x44);

    /**
     * How old the trade behind a live price may get before the number is flagged as caution.
     * Long enough that an item trading through the hour never flickers, short enough that a price
     * nobody has acted on since stops being presented as if it were live.
     */
    /** Half an hour without a trade: the number is going cold. */
    static final long STALE_PRICE_AGE_MS = 30L * 60L * 1000L;
    /** An hour without a trade: old enough that it should not be typed into an offer unchecked. */
    static final long VERY_STALE_PRICE_AGE_MS = 60L * 60L * 1000L;

    /**
     * What colour a price of this age, and the age itself, should be drawn in.
     *
     * <p>Three bands, because "current" and "old" was not enough to act on: plain under half an
     * hour, amber to the hour, red past it. The number is the best one available at every
     * stage, so none of these say it is wrong; they say how much weight to put on it.
     *
     * <p>One rule in one place, because the price on the card and the age in its tooltip are
     * the same fact said twice. If they were allowed to decide separately they could disagree
     * across a threshold, and an amber price beside a white age says nothing at all.
     *
     * @param tradeTimeMs when the price was last traded, or null when nobody knows
     */
    static Color priceAgeColor(Long tradeTimeMs, long nowMs) {
        if (tradeTimeMs == null || tradeTimeMs <= 0) {
            return TEXT;
        }
        long age = nowMs - tradeTimeMs;
        if (age >= VERY_STALE_PRICE_AGE_MS) {
            return DANGER;
        }
        return age >= STALE_PRICE_AGE_MS ? WARNING : TEXT;
    }

    /**
     * Turn on smoothing for a surface about to be drawn by hand.
     *
     * <p>Every rounded panel, every drawn mark and the backdrop's washes need it, and a shape
     * drawn without it is visibly stepped at these sizes - so it is asked for once, by name,
     * rather than spelled out at each of the dozen places that paint something.
     */
    static void smooth(java.awt.Graphics2D g2) {
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
            java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
    }

    /** The same colour as a web page would write it, for the tooltips built out of HTML. */
    static String toHex(Color color) {
        return String.format("#%06X", color != null ? color.getRGB() & 0xFFFFFF : 0xFFFFFF);
    }

    static final DateTimeFormatter REFRESH_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    static final int VALUE_RIGHT_PADDING = 4;
    static final int OFFER_VALUE_RIGHT_PADDING = 4;
    static final int SCROLL_UNIT_INCREMENT = 64;
    static final int SCROLL_BLOCK_INCREMENT = 256;
    /** The site's --surface-radius is 18px on a 1200px page; 14 is the same proportion at 225px. */
    static final int CARD_ARC = 14;
    /** A section's own corner, tighter than the card's so it reads as sitting inside it. */
    static final int WELL_ARC = 8;
    static final int INPUT_ARC = 10;
    /**
     * The remove mark struck over a 32px item icon: a big enough target to hit without
     * hunting for it, small enough to leave the sprite underneath recognisable.
     */
    static final int REMOVE_MARK_SIZE = 16;
    /** Width of the button that closes a control row, so every such row ends on one edge. */
    static final int TRAILING_CONTROL_WIDTH = 30;
    /**
     * The sort direction button on the profile tab, at half that. It carries a drawn mark rather
     * than a word, so it never needed the room a star toggle does - and the room it gives back
     * goes to the recipe filter beside it, whose longest entries were being cut off at the old
     * width. The flipping tab's sort row has no such neighbour and takes the full width instead,
     * so its mark sits on the same centre line as the bookmark star in the row above.
     */
    static final int SORT_DIRECTION_WIDTH = TRAILING_CONTROL_WIDTH / 2;
    /**
     * The star the bookmark filter is drawn with, and the size it is typed at: enough of the ghost
     * box it sits in that the box reads as holding a mark rather than framing a speck, and short
     * of the padding, so the glyph never comes near the rule around it.
     */
    static final String BOOKMARK_GLYPH = "★";
    /** The same star with its middle left out: bookmarked, and not. */
    static final String BOOKMARK_GLYPH_EMPTY = "☆";
    static final float BOOKMARK_GLYPH_SIZE = 17f;
    /**
     * The sort mark is measured against that star rather than fixed, so the drawn mark and the
     * typed one read as one size. This is what it falls back to when the star cannot be measured;
     * the ceiling is the slot each row gives it, which is not the same on both tabs.
     */
    static final int SORT_ICON_SIZE = SORT_DIRECTION_WIDTH;
    static final int TRAILING_CONTROL_GAP = 6;
    /**
     * The clear mark inside a search field: the drawn cross, the square it answers a click in,
     * and the room kept between that square and the field's own rule. The square is wider than
     * the mark because a mark this small is easy to miss and expensive to miss twice.
     */
    static final int CLEAR_MARK_SIZE = 11;
    static final int INLINE_CLEAR_SLOT = 18;
    static final int INLINE_CLEAR_GAP = 5;
    /**
     * The tick box that says a trade is part of the recipe being recorded, and the field beside
     * it that says how much of that trade is. The box matches the clear mark rather than the
     * 9.5px text it stands against: a mark the size of the type reads as a speck, and this one
     * is the target for the whole row. The field holds five digits, which is more than a buy
     * limit ever allows through one offer.
     */
    static final int PICK_MARK_SIZE = 11;
    static final int QUANTITY_FIELD_WIDTH = 34;
    /** Pills are fully round. RoundedBorder clamps this to the control's short side. */
    static final int CHIP_ARC = 999;
    static final int AGE_TOOLTIP_LEFT_GAP = 8;
    static final String STATS_CARD_TOGGLE_SKIP_KEY = "fliphub.skipStatsCardToggle";
    static final int STATS_ITEMS_PER_PAGE = 10;

    private Skin() {
    }
}
