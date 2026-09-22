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

import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import javax.inject.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.*;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.util.ImageUtil;

/**
 * The Merchant skill, in the game's own skills tab.
 *
 * <p>All 24 squares of the grid are real skills now that Sailing has taken the last one, and Total
 * level sits on its own strip underneath. So Merchant goes on a NINTH ROW: the skill in the left
 * square, and the Total plate moved into the space beside it. The strip it came from goes away, so
 * the tab grows only by the difference between a row and a strip.
 *
 * <p>Every position is measured off the real cells rather than written down as pixels. Jagex move
 * this interface -- Sailing did exactly that -- and a hard-coded grid would put the row in the
 * wrong place the next time they do.
 *
 * <p>Hovering reads like any other skill and clicking opens the ten ranks. Both are ours, drawn to
 * match: the game builds its tooltip and its skill guide from its own skill list, and neither can
 * be pointed at a skill that does not exist.
 */
@Singleton
@Slf4j
final class SkillTab {
    // The icon, overridden under a negative id the game never uses, as the rank pictures are.
    private static final int ICON = -21_480;

    // The sprite is drawn at its own size; it was laid out for this and never scales.
    private static final int ICON_SIZE = 25;

    // The picture again at the size a short row can hold, and its own override id.
    private static final int ICON_SMALL = -21_481;

    // The picture again as a flat shape in the glow's colour, one per size. Laid over the
    // picture and faded in and out, it washes it toward that colour and back -- which, watched
    // frame by frame, is exactly what the game does to a skill that has just levelled.
    private static final int ICON_GLOW = -21_482;
    private static final int ICON_GLOW_SMALL = -21_483;
    private static final int SMALL_ICON = 19;

    // The 24 real squares run straight through from Attack to Sailing, three columns of eight
    // filled downwards, so a square's row is its index modulo eight.
    private static final int SKILLS = 24;
    private static final int ROWS = 8;

    // How much each row gives up so the ninth has somewhere to go. One pixel, and one only.
    //
    // A square's stone draws its own bottom border on the row's LAST line: measured off the
    // untouched tab, the icon's art runs to the 27th line of a 30-line row and the border to
    // the 28th. At one pixel the row keeps both. At two the border goes, and every skill then
    // reads as though its picture has burst out of the stone -- which is exactly what it
    // looked like. The game's eight rows are not negotiable at 29.
    private static final int TIGHTEN_BY = 1;

    // The band left under the grid. The TOP takes none of ours: the frame draws two pixels of
    // its own shadow there and none at the foot, so two here is what makes the two ends read
    // as the same width.
    private static final int EDGE_FOOT = 2;

    // How far down merchant.png the art actually goes. The picture is 25 square but its last
    // two lines are empty, so a row two short of a full one still shows all of it -- which is
    // what pays for the band under the grid without the picture ever being moved or cut.
    private static final int ICON_ART = 23;

    // Where the game puts everything INSIDE a square -- the icon and the two numbers -- read
    // once, before anything moves. Our tightening takes its pixel off the BOTTOM of each
    // square, so left alone the contents end up a pixel low in the stone: measured on Magic,
    // three clear pixels above the hat and one under its brim. They are written back from
    // these captured places every time, never from where they currently sit, because reading
    // back our own shift and shifting again would walk the icon up the stone a pixel a tick.
    private int[][] childY;

    // The spacing the game itself uses, captured before we touch it. Everything is worked out
    // from this rather than from the squares, which would read back our own tightening and
    // creep a pixel tighter on every rebuild.
    private int originalStep = -1;

    // Each square's own place and height as the game laid it out, captured at the same moment,
    // so that the way out puts every one of them back exactly. The grid is NOT regular: read
    // out of the game cache, the squares sit at 1, 31 ... 181 and are 30 tall, but the bottom
    // row -- Construction, Hunter and Sailing -- sits at 211 and is 32 tall. Working the way
    // out from one pitch and Attack's height gave that row back two pixels short.
    private int[] gameY;
    private int[] gameHeight;

    // A square's stone is built from sprites this size laid side by side.
    private static final int BACKDROP = 36;

    // Where the icon sits inside a square, if a real one cannot be read for it.
    private static final int ICON_X = 3;
    private static final int ICON_Y = 4;

    // The prestige plaque, struck into the picture's bottom corner. The corner is where the
    // hand tapers away and the two rows under it are clear, so the plate covers no coin and no
    // finger joint. It is a plate rather than a bare numeral because the pixels it sits on are
    // four browns and a seam, which nothing reads against on its own.
    //
    // Nothing here may be pure black: the sprite drawer takes 0x000000 for nothing at all,
    // which is why the art's own outline is 0x000001.
    private static final int PLAQUE_INK = 0xBAB815;   // the coin face, so the mark belongs
    private static final int PLAQUE_BACK = 0x0D0B06;
    private static final int PLAQUE_EDGE = 0x000001;
    private static final int PLAQUE_LIT = 0x3E3525;   // the inner edge that reads as sunk
    private static final int PLAQUE_MARGIN = 2;

    // I, V and X at five pixels tall, a column of bits per pixel column and the low bit at the
    // top. One clear column between letters, so XVIII comes to thirteen.
    private static final int GLYPH_HEIGHT = 5;
    private static final int[] GLYPH_I = {0b11111};
    private static final int[] GLYPH_V = {0b01111, 0b10000, 0b01111};
    private static final int[] GLYPH_X = {0b11011, 0b00100, 0b11011};

    private BufferedImage icon;
    private BufferedImage small;

    // The stats interface we last built into. The game makes new widgets every time it reloads
    // the tab, so when this stops being the live one, the row went with it and is built again.
    private Widget built;
    /**
     * The tab a build was last tried on. A build that failed, because a game update changed what
     * a square holds, used to be tried again every tick, and each try added another row to the tab.
     */
    private Widget tried;

    // One layer holds the whole cell -- icon, numbers and the square that takes the mouse -- so
    // it is hidden, emptied and rebuilt as a single thing, never touching anyone else's widgets.
    private Widget cell;
    private Widget levelTop;
    private Widget levelBottom;
    private Widget tip;

    // The Total plate as the game laid it out, so it can be put back on the way out. Read the
    // FIRST time the plate is seen and never again: once the row is in, what reads back is our
    // own work, and taking that for the game's is how the plate came back from a settings
    // toggle 32 pixels left of where it belongs.
    private boolean plateKnown;
    private int plateX;
    private int plateY;
    private int plateWidth;
    private int tabHeight;

    // The plate is not one picture. It is a left end, a right end, and four plain lengths of
    // border in between that the game puts at FIXED places -- 36, 72, 108 and 144 -- because
    // its plate is always 190 wide. Only the right end follows the width. On our shorter plate
    // the lengths stayed where they were and ran out past the right end: through its bevelled
    // corners, which are see-through, and one column beyond it. Those were the two lines off
    // its right end, and why the left end, where nothing overruns, finished clean.
    //
    // So each length is kept as far in from the right as the game's own last one is, which is
    // ten pixels on the game's plate, and is back where the game had it on the way out. Keyed
    // by widget id, because the game makes new widgets each time it reloads the tab.
    private final java.util.Map<Integer, Integer> pieceX = new java.util.HashMap<>();
    private int pieceGap;

    // Where the plate was last put, and the tab height that went with it. The game re-lays this
    // interface out on its own account -- the first build in the client showed the plate back on
    // its old strip -- so the row is re-asserted whenever it has drifted.
    private int wantPlateX = -1;
    private int wantPlateY;
    private int wantPlateWidth;
    private int wantTabHeight;

    // What the row is showing, so the numbers are only rewritten when they move. Past 99 the
    // level stops, and the tier is the only one of the two that still changes.
    private int shown = -1;
    private int shownTier = -1;

    // The tier the picture in the sprite table is wearing. Kept apart from shownTier, which the
    // row forgets whenever the tab is rebuilt: the sprite outlives that and need not be struck
    // again for it.
    private int struck = -1;

    // Whether the lifetime total has been added up since this login.
    private boolean primed;

    // Whether the skills tab was on screen last tick, so the tick it comes back can be told.
    private boolean looking;

    // Ours for as long as the plugin is on, so it can be handed back on the way out.
    private final Wheel wheel = new Wheel();

    @Inject
    SkillTab() {
    }

    void start() {
        GeLifecyclePlugin plugin = Access.plugin();
        MouseManager mouse = Bridge.get(MouseManager.class);
        if (mouse != null) {
            mouse.registerMouseWheelListener(wheel);
        }
        icon = ImageUtil.loadImageResource(RankUp.class, "/com/osrsfliphub/rank/merchant.png");
        small = ImageUtil.loadImageResource(RankUp.class, "/com/osrsfliphub/rank/merchant-19.png");
        plugin.invokeOnClientThread(() -> strike(plugin.client, 0));
    }

    void stop() {
        GeLifecyclePlugin plugin = Access.plugin();
        MouseManager mouse = Bridge.get(MouseManager.class);
        if (mouse != null) {
            mouse.unregisterMouseWheelListener(wheel);
        }
        plugin.invokeOnClientThread(() -> {
            // Ours would stay up with nothing left to close it.
            letGo();
            restore();
            // Every sprite this put in the game's table, and nothing else's. That is the four
            // pictures, ICON down to ICON_GLOW_SMALL, and then each of the game's own sprites
            // flattened for the sidebar glow, which flattened() numbers on down from the last
            // of those four -- so the whole set is one unbroken run of ids.
            for (int id = ICON_GLOW_SMALL - flats.size(); id <= ICON; id++) {
                plugin.client.getSpriteOverrides().remove(id);
            }
            // Forgotten with them, or the next start would hand out ids that no longer draw.
            flats.clear();
            // In here rather than after it: a tick can be running on the client thread while
            // this is called from the settings panel's, and these four are the tick's.
            built = null;
            tried = null;
            shown = -1;
            struck = -1;
        });
    }

    /**
     * Puts the tab back as the game had it. Our own widgets are hidden rather than deleted:
     * deleting would take every dynamic child of the interface with it, including any another
     * plugin had added to the same tab.
     */
    private void restore() {
        Client client = Access.plugin().client;
        Widget universe = client.getWidget(InterfaceID.Stats.UNIVERSE);
        Widget plate = client.getWidget(InterfaceID.Stats.TOTAL);
        if (universe != null && gameY != null) {
            // A lift of nothing: every square back where the game had it, at its own height.
            tighten(client, 0, 0);
        }
        if (universe != null && plate != null && plateKnown) {
            plate.setOriginalX(plateX);
            plate.setOriginalY(plateY);
            plate.setOriginalWidth(plateWidth);
            pieces(plate, plateWidth);
            relayout(plate);
            universe.setOriginalHeight(tabHeight);
            universe.revalidate();
        }
        hide(cell);
        hide(tip);
        fade(stoneGlow, stoneGlowPaint, 255);
        glow = null;
        stoneGlow = null;
        stoneGlowPaint = null;
        stoneSprite = -1;
        // What the game's own plate measures is NOT forgotten here. It is the one record of how
        // the tab looked before we touched it, and re-reading it later reads back our own row.
        wantPlateX = -1;
        cell = null;
        levelTop = null;
        levelBottom = null;
        tip = null;
    }

    private static void hide(Widget widget) {
        if (widget != null) {
            widget.setHidden(true);
        }
    }

    /**
     * Stands down: the player has turned the skill off in the settings, so the tab goes back to
     * the game's own twenty-four and nothing is built into it again until they turn it on.
     *
     * <p>Only on the tick the setting changes. Afterwards {@link #built} is null and the plate
     * is no longer ours, so there is nothing left to put back and this falls straight through.
     */
    private void stand() {
        // Before the early way out below, which is the one a FAILED build takes: it leaves
        // nothing standing to put back. Turning the setting off and on again is the player's
        // way of asking for another try, so a tab a build gave up on is tried once more.
        tried = null;
        if (built == null && wantPlateX < 0 && !claiming) {
            return;
        }
        restore();
        // A guide window still showing the ranks goes back to whoever opens one next.
        letGo();
        built = null;
        shown = -1;
        shownTier = -1;
        log.info("[skill] Merchant turned off in the settings; the tab is the game's again");
    }

    /** Every client tick: put the row back if the tab has been rebuilt, then keep it current. */
    void tick() {
        Client client = Access.plugin().client;
        if (!Access.plugin().config.showMerchantSkill()) {
            stand();
            return;
        }
        prime(client);
        glowTick(client);
        // Before the tab is looked for, because the guide does not live in the tab. Logged out
        // with ours open, the tab is gone and so is the window; looked after only below, the
        // window was never let go of, and the wheel went on being swallowed over the empty
        // place it had been, login screen and all.
        dressGuide(client);
        Widget universe = client.getWidget(InterfaceID.Stats.UNIVERSE);
        if (universe == null) {
            built = null;
            return;
        }
        if (universe != built && universe != tried) {
            tried = universe;
            build(universe);
        } else if (universe == built) {
            hold(universe, client.getWidget(InterfaceID.Stats.TOTAL));
        }
        yieldToTooltip(client);
        refresh();
    }

    /**
     * Keeps the game's own tooltip clear of our square.
     *
     * <p>Our row is made of dynamic children, which the game draws after its static ones, so the
     * tooltip lands UNDERNEATH the square wherever the two overlap and there is no way to
     * reorder them. Hiding the square while covered was worse: a skill that vanishes whenever
     * you read a neighbour's tooltip. So the TOOLTIP moves instead -- it is already positioned
     * relative to the pointer and has the whole tab to sit in, while the square has one place
     * it can be.
     */
    private void yieldToTooltip(Client client) {
        if (cell == null) {
            return;
        }
        // Anything hidden earlier by the previous approach comes back and stays back.
        if (cell.isSelfHidden()) {
            cell.setHidden(false);
        }
        Widget tooltip = client.getWidget(InterfaceID.Stats.TOOLTIP);
        if (tooltip == null || tooltip.isHidden()
            || tooltip.getWidth() <= 0 || tooltip.getHeight() <= 0) {
            return;
        }
        if (!overlaps(tooltip.getRelativeX(), tooltip.getWidth(), cell.getRelativeX(), cell.getWidth())
            || !overlaps(tooltip.getRelativeY(), tooltip.getHeight(), cell.getRelativeY(), cell.getHeight())) {
            return;
        }
        int lifted = Math.max(0, cell.getRelativeY() - tooltip.getHeight());
        if (tooltip.getRelativeY() != lifted) {
            tooltip.setOriginalY(lifted);
            tooltip.revalidate();
        }
    }

    private static boolean overlaps(int a, int aSize, int b, int bSize) {
        return a < b + bSize && b < a + aSize;
    }

    /**
     * Moves the real squares onto the tightened spacing, from the top of the tab down, or --
     * with a lift of nothing -- puts every one back exactly where the game had it. Idempotent
     * both ways: it works from the step and each square's place in the grid, or from what was
     * captured before anything moved, never from where a square currently is.
     *
     * <p>Tightened, every square is one pitch tall, so they sit flush. The way out does NOT
     * work anything out: it writes back each square's own captured place and height, because
     * the game's grid is not regular -- its bottom row is two pixels taller than the rest, and
     * a way out built from one pitch and one height left that row short.
     */
    private void tighten(Client client, int step, int lift) {
        for (int i = 0; i < SKILLS; i++) {
            Widget square = client.getWidget(InterfaceID.Stats.ATTACK + i);
            if (square == null) {
                continue;
            }
            int wanted = lift == 0 ? gameY[i] : (i % ROWS) * step;
            int height = lift == 0 ? gameHeight[i] : step;
            if (square.getRelativeY() != wanted || square.getHeight() != height) {
                square.setOriginalY(wanted);
                square.setOriginalHeight(height);
                square.revalidate();
            }
            centre(square, i, lift);
        }
    }

    /**
     * Puts a square's contents back where they belong inside it.
     *
     * <p>A square gives up its pixel at the BOTTOM, so everything in it -- the icon and the two
     * numbers -- is left a pixel low, with clear stone above it and its own outline on the
     * border. Lifting the contents by exactly what the square lost puts them back in the
     * middle: on Magic that turns three clear pixels above the hat and one below into two and
     * two. A lift of nothing is the way out, and writes the game's own places back.
     *
     * <p>Worked out from {@link #childY} rather than from where a child currently sits, so it
     * can be run every tick without the contents creeping.
     */
    private void centre(Widget square, int skill, int lift) {
        if (childY == null || skill >= childY.length) {
            return;
        }
        Widget[] kids = parts(square);
        int[] places = childY[skill];
        for (int k = 0; k < kids.length && k < places.length; k++) {
            // The STONE is a child too, and it must not move: it is the rectangle the contents
            // are being centred in, and lifting it as well would shift the whole square and
            // change nothing inside it. Only the picture and the numbers ride up.
            if (!carried(kids[k])) {
                continue;
            }
            int wanted = Math.max(0, places[k] - lift);
            if (kids[k].getRelativeY() != wanted) {
                kids[k].setOriginalY(wanted);
                kids[k].revalidate();
            }
        }
    }

    /**
     * Puts the plate and the tab's height back if the game has moved them. Only when they have
     * actually drifted: writing them every tick would fight whatever moved them and flicker.
     */
    private void hold(Widget universe, Widget plate) {
        if (plate == null || wantPlateX < 0) {
            return;
        }
        // The WIDTH is held too. Without it, a re-laid-out plate kept our position and took the
        // game's full strip width back, which ran it off the right of the tab and cut the Total
        // level in half -- and nothing put it right until the tab was rebuilt.
        if (plate.getRelativeX() != wantPlateX || plate.getRelativeY() != wantPlateY
            || plate.getWidth() != wantPlateWidth) {
            plate.setOriginalX(wantPlateX);
            plate.setOriginalY(wantPlateY);
            plate.setOriginalWidth(wantPlateWidth);
            pieces(plate, wantPlateWidth);
            relayout(plate);
        }
        if (universe.getHeight() != wantTabHeight) {
            universe.setOriginalHeight(wantTabHeight);
            universe.revalidate();
        }
        if (originalStep > 0) {
            tighten(Access.plugin().client, originalStep - TIGHTEN_BY, TIGHTEN_BY);
        }
    }

    /** Lays the ninth row out from the real cells: one square of skill, the rest Total level. */
    private void build(Widget universe) {
        Client client = Access.plugin().client;
        Widget first = client.getWidget(InterfaceID.Stats.ATTACK);
        Widget below = client.getWidget(InterfaceID.Stats.STRENGTH);
        Widget beside = client.getWidget(InterfaceID.Stats.HITPOINTS);
        Widget last = client.getWidget(InterfaceID.Stats.SAILING);
        Widget plate = client.getWidget(InterfaceID.Stats.TOTAL);
        if (first == null || below == null || beside == null || last == null || plate == null) {
            return;
        }
        int width = first.getWidth();
        int height = first.getHeight();
        int stepX = beside.getRelativeX() - first.getRelativeX();
        int stepY = below.getRelativeY() - first.getRelativeY();
        // If any of it reads as nothing the tab is mid-build, so leave it and try the next tick.
        if (width <= 0 || height <= 0 || stepX <= 0 || stepY <= 0) {
            return;
        }
        if (originalStep < 0) {
            originalStep = stepY;
            // Captured before anything moves. Reading these back afterwards would take our own
            // adjustment as the starting point and creep further on every rebuild.
            childY = new int[SKILLS][];
            gameY = new int[SKILLS];
            gameHeight = new int[SKILLS];
            for (int i = 0; i < SKILLS; i++) {
                Widget square = client.getWidget(InterfaceID.Stats.ATTACK + i);
                Widget[] kids = parts(square);
                childY[i] = new int[kids.length];
                for (int k = 0; k < kids.length; k++) {
                    childY[i][k] = kids[k].getRelativeY();
                }
                gameY[i] = square.getRelativeY();
                gameHeight[i] = square.getHeight();
            }
        }
        // A pixel out of each of the nine rows, and the squares shortened to match so they sit
        // flush instead of overlapping. The grid starts at the very top of the tab.
        //
        // Counted off the screen, the tab gives 260 pixels. Nine rows of 29 wanted 261, so the
        // ninth ran a pixel INTO the frame and had nothing under it; nine of 28 want 252 and
        // leave eight over, which is where the bands at the two ends come from.
        int tight = originalStep - TIGHTEN_BY;
        int x = first.getRelativeX();
        tighten(client, tight, TIGHTEN_BY);
        int y = ROWS * tight;

        // Where the real squares put their icon, so ours lands in the same place. Read before
        // the row is sized, because how short the row may be depends on it.
        int iconX = ICON_X;
        int iconY = ICON_Y;
        for (Widget part : parts(first)) {
            if (part.getType() == WidgetType.GRAPHIC && part.getWidth() == ICON_SIZE
                && part.getHeight() == ICON_SIZE) {
                iconX = part.getRelativeX();
                iconY = part.getRelativeY();
                break;
            }
        }

        // What is left under the grid, stopping short of the frame to leave the band there.
        //
        // The ninth row is the one that gives up the difference -- the eight the game laid out
        // keep their 29 to the pixel -- but never so much that the picture stops fitting. Its
        // contents are NOT moved to make room: the picture and the two numbers sit at the same
        // offsets as every other skill's, which is the whole point. What the short row loses is
        // its own bottom border, and the band underneath is what that border would have been.
        int room = universe.getHeight() - EDGE_FOOT - y;
        boolean full = room >= SMALL_ICON;
        int iconSize = full ? ICON_SIZE : SMALL_ICON;
        int rowHeight = full
            ? Math.max(iconY + ICON_ART, Math.min(tight, room))
            : Math.max(SMALL_ICON, plate.getHeight());

        if (!plateKnown) {
            plateKnown = true;
            plateX = plate.getRelativeX();
            plateY = plate.getRelativeY();
            plateWidth = plate.getWidth();
            tabHeight = universe.getHeight();
            // Every picture but the right end, which is the one placed from the right.
            int end = 0;
            for (Widget part : statics(plate)) {
                if (part.getType() == WidgetType.GRAPHIC
                    && part.getXPositionMode() == WidgetPositionMode.ABSOLUTE_LEFT) {
                    pieceX.put(part.getId(), part.getOriginalX());
                    end = Math.max(end, part.getOriginalX() + part.getWidth());
                }
            }
            pieceGap = plateWidth - end;
        }
        // The plate keeps its own height and centres in the row, filling from the second square
        // to the right edge of the third. It centres in the row's REAL height, so that when the
        // ninth is the short one it rides up with the square beside it rather than sitting low.
        //
        // Its border is drawn a pixel in from each end of it, on the game's plate as on ours, so
        // both ends sit a pixel inside the stones above. An earlier fix took two more off the
        // right for the lines there, which was the wrong cause and left that end three in.
        int from = x + stepX;
        int span = last.getRelativeX() + width - from;
        int plateY9 = y + (rowHeight - plate.getHeight()) / 2;
        plate.setOriginalX(from);
        plate.setOriginalY(plateY9);
        plate.setOriginalWidth(span);
        pieces(plate, span);
        relayout(plate);

        // The tab is NOT grown. Measured in the client, the grid ends 31px above the bottom of
        // a 272-tall tab, which is a row's worth of room already there -- growing it on top of
        // that was what pushed the row out of sight.
        wantPlateX = from;
        wantPlateWidth = span;
        wantPlateY = plateY9;
        wantTabHeight = tabHeight;

        levelTop = null;
        levelBottom = null;
        cell = universe.createChild(-1, WidgetType.LAYER);
        cell.setOriginalX(x);
        cell.setOriginalY(y);
        cell.setOriginalWidth(width);
        cell.setOriginalHeight(rowHeight);
        cell.revalidate();

        // The stone behind the row, copied off whichever real thing is the right shape for it.
        //
        // A full-height row takes a square's own pieces. A SHORT row takes the Total plate's
        // instead: a square's stone is 36 tall and its bottom edge lives in the part that gets
        // cut away at 20px, which is what read as the row being chopped off. The plate is 19
        // tall and draws a clean border at that height, so it is the piece that belongs here.
        if (full) {
            for (Widget part : parts(first)) {
                if (part.getType() == WidgetType.GRAPHIC && part.getWidth() == BACKDROP
                    && part.getHeight() == BACKDROP) {
                    stone(part.getSpriteId(), part.getRelativeX(), part.getRelativeY());
                }
            }
        } else {
            Widget left = null;
            Widget right = null;
            for (Widget part : statics(plate)) {
                if (part.getType() != WidgetType.GRAPHIC) {
                    continue;
                }
                if (left == null || part.getRelativeX() < left.getRelativeX()) {
                    left = part;
                }
                if (right == null || part.getRelativeX() > right.getRelativeX()) {
                    right = part;
                }
            }
            if (left != null && right != null) {
                stone(left.getSpriteId(), 0, 0);
                stone(right.getSpriteId(), width - BACKDROP, 0);
            }
        }

        Widget picture = cell.createChild(-1, WidgetType.GRAPHIC);
        picture.setSpriteId(full ? ICON : ICON_SMALL);
        picture.setOriginalX(full ? iconX : 2);
        picture.setOriginalY(full ? iconY : (rowHeight - SMALL_ICON) / 2);
        picture.setOriginalWidth(iconSize);
        picture.setOriginalHeight(iconSize);
        picture.revalidate();
        glow = glowOver(cell, picture, full);

        if (full) {
            // The two numbers where the game puts its own, taken off the same square, so ours
            // reads its current level against its highest exactly as the rest do.
            for (Widget part : parts(first)) {
                if (part.getType() != WidgetType.TEXT) {
                    continue;
                }
                Widget made = number(cell, part.getRelativeX(), part.getRelativeY(),
                    part.getWidth());
                if (levelTop == null) {
                    levelTop = made;
                } else if (levelBottom == null) {
                    levelBottom = made;
                }
            }
        } else {
            // A short row has no height for the pair, so it carries the one number.
            levelTop = number(cell, iconSize + 5, (rowHeight - 12) / 2, 17);
            levelBottom = levelTop;
        }
        if (levelTop == null || levelBottom == null) {
            restore();
            return;
        }

        // The prestige tier is struck into the picture itself rather than laid over it, so
        // there is nothing to build for it here. See strike().
        shown = -1;
        shownTier = -1;

        Widget mouse = cell.createChild(-1, WidgetType.RECTANGLE);
        mouse.setOriginalWidth(width);
        mouse.setOriginalHeight(rowHeight);
        mouse.setFilled(true);
        // 255 is fully see-through: the square is only there to take the pointer.
        mouse.setOpacity(255);
        mouse.setNoClickThrough(true);
        // The game's own wording for a skill guide, and not for looks. RuneLite's XP Tracker
        // takes any option in this interface that starts with "View", splits it on spaces and
        // reads the second word as the skill. A bare "View" has no second word, so it threw
        // every time this square was hovered. This shape gives it one to find, and Merchant is
        // not a real skill, so it bails out where it would otherwise add an entry of its own.
        mouse.setAction(0, guideOption());
        mouse.setOnOpListener((JavaScriptCallback) ev -> openGuide());
        mouse.setOnMouseOverListener((JavaScriptCallback) ev -> showTip(true));
        mouse.setOnMouseLeaveListener((JavaScriptCallback) ev -> showTip(false));
        mouse.setHasListener(true);
        mouse.revalidate();

        buildTip(universe, x, y, stepX * 3);
        built = universe;
    }

    /** One piece of stone, at the size the game draws its own: the row clips what spills. */
    private void stone(int sprite, int x, int y) {
        Widget piece = cell.createChild(-1, WidgetType.GRAPHIC);
        piece.setSpriteId(sprite);
        piece.setOriginalX(x);
        piece.setOriginalY(y);
        piece.setOriginalWidth(BACKDROP);
        piece.setOriginalHeight(BACKDROP);
        piece.revalidate();
    }

    /**
     * Recomputes a widget AND everything inside it.
     *
     * <p>{@link Widget#revalidate()} recomputes that one widget and no further -- the plate's
     * border and its Total level text are laid out against its width, and changing the width
     * alone left them exactly where they were. That is the whole of why a settings toggle
     * looked broken until you clicked another tab and back: the game lays the interface out
     * when it rebuilds the tab, and until it did, the border sat off the end of the plate and
     * the text was centred on a width the plate no longer had.
     */
    private static void relayout(Widget widget) {
        widget.revalidate();
        for (Widget kid : statics(widget)) {
            relayout(kid);
        }
        for (Widget kid : parts(widget)) {
            relayout(kid);
        }
    }

    /**
     * Puts the plate's lengths of border where a plate this wide wants them. See {@link #pieceX}.
     *
     * <p>At the game's own width this writes back exactly where the game had them, since no
     * length there comes closer to the right than the gap was measured from. The caller lays
     * the plate out afterwards.
     */
    private void pieces(Widget plate, int width) {
        for (Widget part : statics(plate)) {
            Integer x = pieceX.get(part.getId());
            if (x != null) {
                part.setOriginalX(Math.min(x, width - pieceGap - part.getWidth()));
            }
        }
    }

    /** What a square carries INSIDE its stone: the skill's picture and its two numbers. */
    private static boolean carried(Widget kid) {
        if (kid.getType() == WidgetType.TEXT) {
            return true;
        }
        return kid.getType() == WidgetType.GRAPHIC
            && kid.getWidth() == ICON_SIZE && kid.getHeight() == ICON_SIZE;
    }

    /** A widget's static children, never null. The plate keeps its stone in those. */
    private static Widget[] statics(Widget widget) {
        Widget[] kids = widget.getStaticChildren();
        if (kids == null) {
            return new Widget[0];
        }
        int kept = 0;
        for (Widget kid : kids) {
            if (kid != null) {
                kids[kept++] = kid;
            }
        }
        return java.util.Arrays.copyOf(kids, kept);
    }

    /** A widget's own children, never null, so the callers above can just loop. */
    private static Widget[] parts(Widget widget) {
        Widget[] kids = widget.getChildren();
        if (kids == null) {
            return new Widget[0];
        }
        // The game's own array, which the loop below would compact in place: a gap in it became
        // a second copy of whatever followed. Twenty-four squares, every tick.
        kids = kids.clone();
        int kept = 0;
        for (Widget kid : kids) {
            if (kid != null) {
                kids[kept++] = kid;
            }
        }
        return java.util.Arrays.copyOf(kids, kept);
    }

    /** The right-click, in the game's own words. See where it is set for why the shape matters. */
    static String guideOption() {
        return "View <col=ff981f>" + FlipLevel.SKILL + "</col> guide";
    }

    private static Widget number(Widget parent, int x, int y, int width) {
        Widget text = parent.createChild(-1, WidgetType.TEXT);
        text.setFontId(FontID.PLAIN_11);
        text.setTextColor(0xFFFF00);
        text.setTextShadowed(true);
        text.setOriginalX(x);
        text.setOriginalY(y);
        text.setOriginalWidth(width);
        text.setOriginalHeight(12);
        text.setXTextAlignment(WidgetTextAlignment.CENTER);
        text.setYTextAlignment(WidgetTextAlignment.CENTER);
        text.revalidate();
        return text;
    }

    // Profit, the line for the next level, and what is left to it: the same three readings the
    // game gives for a skill, in the same order.
    private static final int TIP_LINES = 3;

    // The rest is a Prayer tooltip, measured: PLAIN_12 -- a size up from what ours had -- at a
    // pitch of 12, two pixels in from the black border, and the numbers in a COLUMN of their
    // own against the right edge, four clear of the longest label. The box is then cut to what
    // that comes to; the game's came to 130 by 43. A fixed width is what left ours with a
    // hand's width of empty parchment past the numbers, and no column is what left them ragged
    // down the middle.
    private static final int TIP_PITCH = 12;
    private static final int TIP_PAD = 2;
    private static final int TIP_GAP = 4;

    // Room under the last line for the tails of g, y and a comma.
    private static final int TIP_TAIL = 3;

    private final Widget[] tipLabels = new Widget[TIP_LINES];
    private final Widget[] tipValues = new Widget[TIP_LINES];
    private Widget tipBorder;
    private Widget tipFill;

    // Where the box would like to sit, and how much room it has to sit in. It is measured and
    // moved on every hover, because what it holds changes width as the profit grows.
    private int tipLeft;
    private int tipRoom;

    /** The hover box, built once and left hidden. It sits ABOVE the row, which is the last one. */
    private void buildTip(Widget universe, int x, int rowY, int width) {
        int height = 2 + TIP_PAD + TIP_LINES * TIP_PITCH + TIP_TAIL;
        tipLeft = x;
        tipRoom = universe.getWidth();
        tip = universe.createChild(-1, WidgetType.LAYER);
        tip.setOriginalX(x);
        tip.setOriginalY(Math.max(0, rowY - height));
        tip.setOriginalWidth(width);
        tip.setOriginalHeight(height);
        tip.setHidden(true);
        tip.revalidate();

        // Sampled off the game's own skill tooltip: a pale yellow fill inside a one pixel
        // black border, with near-black text and no shadow. Nothing here is chosen.
        tipBorder = fill(tip, 0, 0, width, height, 0x000000);
        tipFill = fill(tip, 1, 1, width - 2, height - 2, 0xFFFFA0);
        for (int line = 0; line < TIP_LINES; line++) {
            tipLabels[line] = tipText(line, WidgetTextAlignment.LEFT);
            tipValues[line] = tipText(line, WidgetTextAlignment.RIGHT);
        }
    }

    /** One half of one line. Its column is set when the box is measured, not here. */
    private Widget tipText(int line, int align) {
        Widget text = tip.createChild(-1, WidgetType.TEXT);
        text.setFontId(FontID.PLAIN_12);
        text.setTextColor(0x000001);
        text.setTextShadowed(false);
        text.setText("");
        text.setOriginalY(1 + TIP_PAD + line * TIP_PITCH);
        text.setOriginalHeight(TIP_PITCH);
        text.setXTextAlignment(align);
        text.setYTextAlignment(WidgetTextAlignment.CENTER);
        text.revalidate();
        return text;
    }

    private static Widget fill(Widget parent, int x, int y, int width, int height, int colour) {
        Widget rectangle = parent.createChild(-1, WidgetType.RECTANGLE);
        rectangle.setOriginalX(x);
        rectangle.setOriginalY(y);
        rectangle.setOriginalWidth(width);
        rectangle.setOriginalHeight(height);
        rectangle.setFilled(true);
        rectangle.setTextColor(colour);
        rectangle.revalidate();
        return rectangle;
    }

    private void showTip(boolean showing) {
        if (tip == null) {
            return;
        }
        if (showing) {
            long profit = profit();
            int level = FlipLevel.levelFor(profit);
            say(0, FlipLevel.SKILL + " profit:", exact(profit));
            if (level >= FlipLevel.MAX_LEVEL) {
                int tier = FlipLevel.prestigeFor(profit);
                say(1, "Prestige:", tier > 0 ? FlipLevel.roman(tier) : "none yet");
                // The last tier has no next one. See FlipLevel.MAX_PRESTIGE.
                say(2, "Next tier at:", tier < FlipLevel.MAX_PRESTIGE
                    ? exact(FlipLevel.profitForPrestige(tier + 1)) : "none");
            } else {
                say(1, "Next level at:", exact(FlipLevel.profitFor(level + 1)));
                say(2, "Remaining:", exact(FlipLevel.toNextLevel(profit)));
            }
            measureTip();
        }
        tip.setHidden(!showing);
    }

    private void say(int line, String label, String value) {
        tipLabels[line].setText(label);
        tipValues[line].setText(value);
    }

    /** Cuts the box to the words in it, with the numbers right-aligned in a column of their own. */
    private void measureTip() {
        // Every line is set in the same font, so one look-up does for all six pieces. It comes
        // back null before the font has loaded, and then the box is left at the width it had
        // rather than collapsing to nothing.
        FontTypeFace font = tipLabels[0].getFont();
        if (font == null) {
            return;
        }
        int labels = 0;
        int values = 0;
        for (int line = 0; line < TIP_LINES; line++) {
            labels = Math.max(labels, font.getTextWidth(tipLabels[line].getText()));
            values = Math.max(values, font.getTextWidth(tipValues[line].getText()));
        }
        int width = 2 + 2 * TIP_PAD + labels + TIP_GAP + values;
        // Kept inside the tab, so a long enough number cannot run off the right of it.
        tip.setOriginalX(Math.max(0, Math.min(tipLeft, tipRoom - width)));
        tip.setOriginalWidth(width);
        tip.revalidate();
        widen(tipBorder, width);
        widen(tipFill, width - 2);
        for (int line = 0; line < TIP_LINES; line++) {
            column(tipLabels[line], 1 + TIP_PAD, labels);
            column(tipValues[line], width - 1 - TIP_PAD - values, values);
        }
    }

    private static void widen(Widget rectangle, int width) {
        rectangle.setOriginalWidth(width);
        rectangle.revalidate();
    }

    private static void column(Widget text, int x, int width) {
        text.setOriginalX(x);
        text.setOriginalWidth(width);
        text.revalidate();
    }


    // ---- the unseen level-up -------------------------------------------------------------
    //
    // Level a real skill and two things happen: the Skills stone in the sidebar flashes, and
    // when the tab is opened, that skill's square flashes until it is looked at. Both are built
    // off the game's own skill list, so neither can happen for a skill the game does not have.
    // Both are ours, the same as the tooltip and the guide before them.

    // The Skills tab in each of the three sidebars the game has -- fixed, and the two resizable
    // ones -- as a pair: the stone, then the little picture on it. Whichever layout the player
    // is in, one pair is live and the other two read back null.
    //
    // The two are SIBLINGS, a STONEn and an ICONn side by side in the same container, which is
    // why looking for a picture among the stone's children found nothing. And the STONE is not
    // what it sounds like: RuneLite's own interface-styles plugin calls it STATS_HIGHLIGHT,
    // because it is the LIT stone, kept hidden until the tab is the one open. The plain stones
    // are painted into one background image of the whole strip and are not widgets at all.
    //
    // So the stone is read for its SHAPE, not for what is on screen: its sprite is the outline
    // of a tab, which is exactly the shape wanted, and it carries the place and size to draw
    // that shape at. Whether it is hidden says nothing except which tab is selected -- which is
    // why asking for a visible one left nothing glowing at all. The PICTURE is what says a
    // layout is the live one, because that is on screen in every tab.
    // Stone, picture, and the SHAPE of a tab in that layout. The third is needed because the
    // stone widget is hidden whenever its tab is not the open one, and a hidden one carries no
    // sprite to read -- which is why the glow kept coming out as the little picture's outline.
    // So the shape is named outright instead. Both are the lit stone the game draws under a
    // selected tab, and Skills is not a corner tab, so the middle one is its shape.
    // Not private, and nor are unseen, stoneGlow, said, stoneSprite or tabOpen: the development
    // client's tester reads them to report what the sidebar half found. Nothing that ships does.
    static final int[][] SKILLS_TAB = {
        {0x0224_0041, 0x0224_0048, SpriteID.SideStoneHighlights.MIDDLE},
        {0x00a1_003c, 0x00a1_0043, SpriteID.SideStoneHighlights.MIDDLE},
        {0x00a4_0035, 0x00a4_003c, SpriteID.PreEocStones.MIDDLE},
    };

    // One full breath of the glow, in client ticks: in and out again. Counted off a recording
    // of a real Smithing level-up, the icon goes from clear to full and back every 72 frames at
    // 30fps -- 2.4 seconds -- in a straight ramp each way. Client ticks are 20ms, so 120.
    private static final int GLOW_CYCLE = 120;

    // How solid it gets at the top of that breath. Opacity counts DOWN to solid, so this is
    // a little over half. Measured: the anvil sat at #787a7a and peaked at #b6ba94, which is
    // the colour below mixed about half and half.
    private static final int GLOW_PEAK = 115;

    // The colour it washes toward: the game's own pale yellow, the same one its tooltips are
    // filled with.
    private static final int GLOW_INK = 0xFFFFA0;

    // A level-up nobody has looked at yet. Only opening the Merchant guide answers it, which is
    // where the game stops its own (see fill()). Until then the square glows, and so does the
    // Skills stone whenever the tab is not the one on screen: opening the tab darkens the stone
    // only for as long as it stays open. Hovering the square settles nothing.
    volatile boolean unseen;
    private int beat;

    // The game's sprites we have flattened, so each is built and registered once.
    private final java.util.Map<Integer, Integer> flats = new java.util.HashMap<>();
    private final java.util.Map<Integer, int[]> sizes = new java.util.HashMap<>();

    private Widget glow;
    Widget stoneGlow;
    final java.util.Set<String> said = new java.util.HashSet<>();
    private Widget stoneGlowPaint;
    int stoneSprite = -1;

    /** A Merchant level has been earned. Flashes until the player goes and looks at it. */
    void flash() {
        unseen = true;
        beat = 0;
    }

    /**
     * Drives both flashes, once per client tick.
     *
     * <p>Runs before the tab is looked for, not after: the stone is the half that matters to a
     * player who is not in the skills tab at all, and giving up early because the tab is not
     * loaded would be giving up on the very case it is there for.
     */
    private void glowTick(Client client) {
        int opacity = unseen ? breath(beat++) : 255;
        fade(glow, opacity);
        // Asked fresh every tick rather than remembered. Remembering it was wrong twice over:
        // a level earned with the tab already open latched the stone shut before it had ever
        // glowed, and it could not come back when the player switched away again. The stone is
        // simply the half that shows while the tab is not the one on screen.
        lightStone(client, tabOpen(client) ? 255 : opacity);
    }

    /** Whether the skills tab is the one on screen right now. */
    static boolean tabOpen(Client client) {
        Widget universe = client.getWidget(InterfaceID.Stats.UNIVERSE);
        return universe != null && !universe.isHidden();
    }

    /**
     * One of the game's own sprites, washed toward the glow's colour and put back in the table
     * under an id of ours. Built once per sprite, because none of them change.
     */
    private int flattened(int sprite) {
        Integer known = flats.get(sprite);
        if (known != null) {
            return known;
        }
        SpriteManager sprites = Bridge.get(SpriteManager.class);
        BufferedImage art = sprites == null ? null : sprites.getSprite(sprite, 0);
        if (art == null) {
            return 0;
        }
        Client client = Access.plugin().client;
        int id = ICON_GLOW_SMALL - 1 - flats.size();
        client.getSpriteOverrides().put(id, ImageUtil.getImageSpritePixels(flat(art), client));
        flats.put(sprite, id);
        // Kept because a hidden widget need not carry a size, and the sprite always does.
        sizes.put(sprite, new int[]{art.getWidth(), art.getHeight()});
        return id;
    }

    /**
     * The glow over the Skills tab, built where it belongs then breathed in and out.
     *
     * <p>Anchored on the tab's PICTURE, which is the one widget here known to be on screen and
     * known to take a child that draws -- an overlay built on it is what glowed in the first
     * place. The stone beside it supplies the shape and the size when it can be read; when it
     * cannot, the picture's own shape is glowed rather than nothing at all.
     *
     * <p>Every way out of here says why, once each. Three attempts at this half failed silently
     * in three different places, and silence is what made each of them cost a round trip.
     */
    private void lightStone(Client client, int opacity) {
        if (opacity >= 255) {
            fade(stoneGlow, stoneGlowPaint, 255);
            return;
        }
        Widget icon = skillsIcon(client);
        if (icon == null) {
            bail("no Skills tab picture is on screen in any of the three sidebars");
            return;
        }
        Widget parent = icon.getParent();
        if (parent == null) {
            bail("the Skills tab picture has no container to build in");
            return;
        }
        // The whole tab's shape, drawn at the size that shape actually is and centred on the
        // picture -- which is how a tab and its picture sit on each other anyway. Nothing here
        // is read off the stone widget, because while its tab is not the open one it is hidden,
        // carries no sprite and has never been laid out.
        int source = stoneShape(client);
        int sprite = source == 0 ? 0 : flattened(source);
        if (sprite == 0) {
            bail("tab shape " + source + " could not be read out of the cache");
            return;
        }
        int[] size = sizes.get(source);
        int width = size == null ? icon.getWidth() : size[0];
        int height = size == null ? icon.getHeight() : size[1];
        int x = icon.getRelativeX() + (icon.getWidth() - width) / 2;
        int y = icon.getRelativeY() + (icon.getHeight() - height) / 2;
        // Built again whenever the one we have no longer hangs where it was put. A relog, a
        // world hop or a switch between fixed and resizable loads the sidebar afresh: its old
        // widgets, ours among them, are dropped, and a layout switch hangs the picture in a
        // different container altogether. Asked only whether the SHAPE had changed, the glow
        // went on fading a widget that was no longer drawn, and never showed again. Asking
        // whether the live container still holds ours answers every one of those at once, and
        // a sidebar that has cleared its own children as well.
        if (stoneGlow == null || stoneSprite != source
            || !java.util.Arrays.asList(parts(parent)).contains(stoneGlow)) {
            // One that is still standing is put out first, so a change of shape cannot leave
            // the old one lit beside the new.
            hide(stoneGlow);
            // A layer cut to what is being glowed, with the glow inside it. A layer clips what
            // spills, so none of it can reach past the tab whatever the sprite turns out to be.
            Widget box = parent.createChild(-1, WidgetType.LAYER);
            box.setOriginalX(x);
            box.setOriginalY(y);
            box.setOriginalWidth(width);
            box.setOriginalHeight(height);
            box.setHidden(true);
            box.revalidate();
            Widget shape = box.createChild(-1, WidgetType.GRAPHIC);
            shape.setSpriteId(sprite);
            shape.setOriginalWidth(width);
            shape.setOriginalHeight(height);
            shape.revalidate();
            stoneGlow = box;
            stoneGlowPaint = shape;
            stoneSprite = source;
            bail("glowing the whole tab: shape " + source + ", " + width + "x" + height
                + " at " + x + "," + y);
        }
        fade(stoneGlow, stoneGlowPaint, opacity);
    }

    /** Says a thing once. The same reason every tick would fill the log in a minute. */
    private void bail(String why) {
        if (said.add(why)) {
            log.info("[skill] sidebar glow: {}", why);
        }
    }

    /** The Skills tab's own picture, in whichever of the three sidebars is on screen. */
    private static Widget skillsIcon(Client client) {
        for (int[] pair : SKILLS_TAB) {
            Widget icon = client.getWidget(pair[1]);
            if (icon != null && !icon.isHidden()) {
                return icon;
            }
        }
        return null;
    }

    /**
     * The Skills tab's stone in whichever sidebar is on screen, hidden or not.
     *
     * <p>Which layout is live is settled by its PICTURE, which is drawn in every tab. The stone
     * beside it is then taken as it comes: it is wanted for its shape and its place, and being
     * hidden only means the Skills tab is not the one selected.
     */
    private static int stoneShape(Client client) {
        for (int[] tab : SKILLS_TAB) {
            Widget icon = client.getWidget(tab[1]);
            if (icon == null || icon.isHidden()) {
                continue;
            }
            // The stone's own sprite if it happens to be carrying one, and the shape named for
            // that layout otherwise. Hidden or not, the tab is the same shape either way.
            Widget stone = client.getWidget(tab[0]);
            return stone != null && stone.getSpriteId() > 0 ? stone.getSpriteId() : tab[2];
        }
        return 0;
    }

    /**
     * The glow: the picture's own shape in the glow's colour, laid exactly over it.
     *
     * <p>Two wrong turns got here. The first laid the game's stone highlight over the square as
     * a nine-slice, and its pieces turned out to be WHOLE STONES -- four of those at the
     * corners of a 62 by 26 cell meet in the middle and blanket it, so the skill vanished. The
     * second drew a line around the square, which covered nothing but glowed the wrong thing.
     * Watched frame by frame, the game glows the PICTURE and nothing else.
     */
    private static Widget glowOver(Widget parent, Widget picture, boolean full) {
        Widget shape = parent.createChild(-1, WidgetType.GRAPHIC);
        shape.setSpriteId(full ? ICON_GLOW : ICON_GLOW_SMALL);
        shape.setOriginalX(picture.getRelativeX());
        shape.setOriginalY(picture.getRelativeY());
        shape.setOriginalWidth(picture.getWidth());
        shape.setOriginalHeight(picture.getHeight());
        shape.setHidden(true);
        shape.revalidate();
        return shape;
    }

    /**
     * How far through its breath the glow is, as an opacity.
     *
     * <p>Opacity counts the wrong way round -- 0 is solid and 255 is not there at all -- so
     * this walks DOWN to {@link #GLOW_PEAK} and back up again. A pulse rather than a blink: the
     * first version simply hid and unhid the thing every twelve ticks, which read as the skill
     * disappearing and coming back.
     */
    private static int breath(int beat) {
        int half = GLOW_CYCLE / 2;
        int step = Math.floorMod(beat, GLOW_CYCLE);
        int up = step < half ? step : GLOW_CYCLE - step;
        return 255 - (255 - GLOW_PEAK) * up / half;
    }

    /** The same, for a glow that sits in a box: the box shows or hides, the paint fades. */
    private static void fade(Widget box, Widget paint, int opacity) {
        if (box == null) {
            return;
        }
        boolean lit = opacity < 255;
        if (box.isSelfHidden() == lit) {
            box.setHidden(!lit);
        }
        if (lit && paint != null && paint.getOpacity() != opacity) {
            paint.setOpacity(opacity);
            paint.revalidate();
        }
    }

    /** Sets a glow's opacity, and takes it off screen entirely once there is nothing to see. */
    private static void fade(Widget shape, int opacity) {
        if (shape == null) {
            return;
        }
        boolean lit = opacity < 255;
        if (shape.isSelfHidden() == lit) {
            shape.setHidden(!lit);
        }
        if (lit && shape.getOpacity() != opacity) {
            shape.setOpacity(opacity);
            shape.revalidate();
        }
    }

    /**
     * Works the level's profit out on login, and again every time the skills tab comes on
     * screen, off this thread.
     *
     * <p>Without it the square reads level 1 until something else happens to work the total out
     * -- opening the Profile tab was doing it -- so a player saw the wrong level for as long as
     * they left the panel alone. Every login counts, so with the level read per character a
     * switch to another character brings that character's level with it.
     *
     * <p>Every opening of the tab counts too, because a live sale is not the only thing that
     * moves the total. A GE history import, a recorded recipe and a wipe all do, none of them
     * passes through here, and each asks only the Profile tab to refresh -- which does nothing
     * while that tab is not the one showing. The square is only ever seen with the tab open, so
     * working the total out as the tab opens is enough for it never to be seen stale, and it
     * costs one adding-up per opening rather than a watch on every way the trades can change.
     */
    private void prime(Client client) {
        boolean open = tabOpen(client);
        boolean opened = open && !looking;
        looking = open;
        if (client.getGameState() != GameState.LOGGED_IN) {
            primed = false;
            return;
        }
        if (primed && !opened) {
            return;
        }
        primed = true;
        GeLifecyclePlugin plugin = Access.plugin();
        plugin.executeAsync(() -> {
            RankUp rankUp = Bridge.get(RankUp.class);
            if (rankUp != null) {
                rankUp.profit = rankUp.levelProfit();
            }
        });
    }

    /** The level on the square, rewritten only when it actually moves. */
    private void refresh() {
        if (levelTop == null) {
            return;
        }
        long profit = profit();
        int level = FlipLevel.levelFor(profit);
        int tier = FlipLevel.prestigeFor(profit);
        if (level == shown && tier == shownTier) {
            return;
        }
        shown = level;
        shownTier = tier;
        String text = Integer.toString(level);
        levelTop.setText(text);
        levelBottom.setText(text);
        strike(Access.plugin().client, tier);
    }

    /**
     * Puts the picture, wearing whatever tier it is on, into the game's sprite table.
     *
     * <p>Both sizes are struck, because a short row draws the small one and a player past 99 is
     * owed the mark whichever row they get.
     */
    private void strike(Client client, int tier) {
        if (tier == struck) {
            return;
        }
        struck = tier;
        client.getSpriteOverrides()
            .put(ICON, ImageUtil.getImageSpritePixels(marked(icon, tier), client));
        client.getSpriteOverrides()
            .put(ICON_SMALL, ImageUtil.getImageSpritePixels(marked(small, tier), client));
        // The glow's shape follows the picture, plaque and all, so it is struck at the same time.
        client.getSpriteOverrides()
            .put(ICON_GLOW, ImageUtil.getImageSpritePixels(flat(marked(icon, tier)), client));
        client.getSpriteOverrides()
            .put(ICON_GLOW_SMALL, ImageUtil.getImageSpritePixels(flat(marked(small, tier)), client));
        // The client holds on to the sprite a widget last drew, so a replaced override is not
        // looked at again until that cache is emptied.
        client.getWidgetSpriteCache().reset();
    }

    /**
     * The same picture as one flat colour: every pixel that is drawn becomes the glow's colour,
     * and everything clear stays clear.
     *
     * <p>Laid over the picture at a part opacity, this is a BLEND of the two -- the picture
     * washed that far toward the colour -- so fading it in and out is the whole effect, with no
     * second image to keep in step.
     */
    private static BufferedImage flat(BufferedImage art) {
        BufferedImage shape = new BufferedImage(art.getWidth(), art.getHeight(),
            BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < art.getHeight(); y++) {
            for (int x = 0; x < art.getWidth(); x++) {
                int pixel = art.getRGB(x, y);
                // Clear counts BOTH ways. Ours carry an alpha channel; the game's own sprites
                // mark a clear pixel by its colour being nothing at all, and reading only the
                // alpha took every one of those for solid -- which is how the glow came to
                // spill out past the stone into corners that are not drawn.
                boolean clear = (pixel >>> 24) == 0 || (pixel & 0x00FFFFFF) == 0;
                shape.setRGB(x, y, clear ? 0 : 0xFF000000 | GLOW_INK);
            }
        }
        return shape;
    }

    /**
     * The picture with the tier struck into its corner, or the picture itself below the cap.
     *
     * <p>Baked rather than drawn over: the sprite drawer paints every pixel that is not fully
     * clear as solid, so a struck mark has the hard edges it wants by construction, and the
     * numeral can never drift out of step with the art underneath it.
     *
     * <p>No tier past {@link FlipLevel#MAX_PRESTIGE} is ever drawn, whatever is asked for. Past
     * it the numeral runs to letters the plaque has no shape for (L at 40), and at XXVIII the
     * plate is wider than the 19-pixel picture, which then throws on the first pixel off its
     * edge -- on the client thread, every time the tier is struck.
     */
    static BufferedImage marked(BufferedImage art, int tier) {
        int[] columns = columns(FlipLevel.roman(Math.min(tier, FlipLevel.MAX_PRESTIGE)));
        if (columns.length == 0) {
            return art;
        }
        BufferedImage out =
            new BufferedImage(art.getWidth(), art.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics graphics = out.getGraphics();
        graphics.drawImage(art, 0, 0, null);
        graphics.dispose();

        int width = columns.length + PLAQUE_MARGIN * 2;
        int height = GLYPH_HEIGHT + PLAQUE_MARGIN * 2;
        int left = Math.max(0, out.getWidth() - width);
        int top = Math.max(0, out.getHeight() - height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean edge = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                boolean lit = !edge && (x == 1 || y == 1);
                out.setRGB(left + x, top + y,
                    0xFF000000 | (edge ? PLAQUE_EDGE : lit ? PLAQUE_LIT : PLAQUE_BACK));
            }
        }
        for (int x = 0; x < columns.length; x++) {
            for (int y = 0; y < GLYPH_HEIGHT; y++) {
                if ((columns[x] & 1 << y) != 0) {
                    out.setRGB(left + PLAQUE_MARGIN + x, top + PLAQUE_MARGIN + y,
                        0xFF000000 | PLAQUE_INK);
                }
            }
        }
        return out;
    }

    /** The numeral as one column of bits per pixel column, with a clear one between letters. */
    private static int[] columns(String numeral) {
        int width = 0;
        for (int i = 0; i < numeral.length(); i++) {
            width += glyph(numeral.charAt(i)).length + (i > 0 ? 1 : 0);
        }
        int[] out = new int[width];
        int at = 0;
        for (int i = 0; i < numeral.length(); i++) {
            int[] letter = glyph(numeral.charAt(i));
            at += i > 0 ? 1 : 0;
            System.arraycopy(letter, 0, out, at, letter.length);
            at += letter.length;
        }
        return out;
    }

    private static int[] glyph(char letter) {
        return letter == 'V' ? GLYPH_V : letter == 'X' ? GLYPH_X : GLYPH_I;
    }

    /**
     * The profit the level is read from, as it was last worked out. Read here, never worked out
     * here: that reads saved trades, which must not happen on this thread.
     */
    private static long profit() {
        RankUp rankUp = Bridge.get(RankUp.class);
        return rankUp != null ? rankUp.profit : 0L;
    }

    /** 1,904,221. */
    private static String exact(long gp) {
        return String.format("%,d", gp);
    }

    /**
     * 133.5k, 94.9M, 5.52B, or the whole number under a thousand.
     *
     * <p>Each step starts just below its round number rather than at it, so a value that rounds
     * up lands on 1.0M instead of reading 1000.0k.
     */
    static String shortGp(long gp) {
        if (gp >= 999_500_000L) {
            return String.format("%.2fB", gp / 1_000_000_000d);
        }
        if (gp >= 999_950L) {
            return String.format("%.1fM", gp / 1_000_000d);
        }
        if (gp >= 1_000L) {
            return String.format("%.1fk", gp / 1_000d);
        }
        return exact(gp);
    }

    // The ten ranks are real items, so the guide draws them with the game's own item sprites.
    // Nothing to resample, nothing to cut to a hard edge, and they match the Defence guide
    // exactly because they ARE the same pictures, drawn at the size it draws them and wearing
    // the same outline.
    private static final int[] RANK_ITEM = {
        ItemID.BRONZE_MED_HELM, ItemID.STEEL_FULL_HELM, ItemID.RUNE_FULL_HELM,
        ItemID.BRUT_DRAGON_FULL_HELM, ItemID.SERPENTINE_HELM, ItemID.JUSTICIAR_FACEGUARD,
        ItemID.TORVA_HELM, ItemID.ELYSIAN, ItemID.TRAIL_FIGHTER_HELM, ItemID.BLUE_PARTYHAT,
    };

    // An item sprite is 36 by 32. The game pitches its own guide rows at 35 -- that picture and
    // three pixels of air -- and ours carries a second line, so it takes one more again.
    private static final int GUIDE_ICON = 32;
    private static final int GUIDE_ROW = 36;

    // The gap between a row's two lines, which is the pitch the game writes its own second
    // lines at: "Requires: Level 75 Prayer" under an item in the Prayer guide.
    private static final int GUIDE_LINE = 13;

    // The guide's ink, sampled off the real one. It is a dark BROWN, not black: black reads as
    // harsh against the parchment, and was half of why ours looked printed on rather than part
    // of the page.
    private static final int GUIDE_INK = 0x46320A;

    // What the game colours a requirement you have met and one you have not. The same pair the
    // quest list uses.
    private static final int GUIDE_MET = 0x007F00;
    private static final int GUIDE_SHORT = 0x7F0000;

    private static final int GUIDE_SLOTS = 15;

    // A line on the category scroll beside the guide, and what its two rolls take between them.
    private static final int CATEGORY_ROW = 17;
    private static final int CATEGORY_ENDS = 28;

    // The scroll bar, in the sizes the sprites themselves are: the widget is 16 wide, each end
    // arrow is a whole 16 by 16, and the track and the dragger's caps are 16 by 5 tiles.
    private static final int SCROLL_WIDTH = 16;
    private static final int SCROLL_ARROW = 16;
    private static final int SCROLL_CAP = 5;

    // Two arrows, a track and the dragger's three pieces. Fewer than this and the bar is not
    // ours -- the guide built its own, or nothing has been built yet.
    private static final int BAR_PIECES = 6;

    // A dragger shorter than this is not worth aiming at, however little of the page is on
    // screen at once.
    private static final int SCROLL_LEAST = 20;

    // Set from asking for the window until it closes. The game's script fills that window for a
    // real skill, so ours is written over the top for as long as it is open.
    private volatile boolean claiming;

    // Whether the window has actually been on screen since we asked for it, which is how the
    // wait before it opens is told apart from it having been closed.
    private boolean sawGuide;

    // How many client ticks it has been asked for and not yet seen, and how many it gets: one
    // second, when the window is up on the very next tick. See dressGuide().
    private int waited;
    static final int GUIDE_WAIT = 50;

    // The bar we built and the pieces of it that move, kept as they were made rather than
    // fished back out of the widget they went into.
    private Widget barOwner;
    private Widget barTrack;
    private Widget barTop;
    private Widget barMid;
    private Widget barBottom;

    // Whether the ranks have gone in since the window opened. Once they have, a title that is
    // not ours again means somebody opened a different guide over the top of it.
    private volatile boolean dressed;

    // The window when we opened it ourselves. The server never opened it, so it will not close
    // it either: that is ours too. Null when there is none, or the game opened the one we took.
    private WidgetNode opened;
    private int openedAt;

    // Where the ranks are on the canvas, left here by the client thread for the wheel listener
    // on the mouse's thread, which may not read a widget itself.
    private volatile Rectangle guideArea;

    /**
     * Opens the game's own skill guide window on this client alone, the way Examine Log opens
     * its own: the window is hung in the layout's main window slot here, and nothing about it
     * is sent to the server. The server never learns it is open, so it never closes it either;
     * {@link #letGo} does that, and {@link #clicked} keeps every click on it here.
     */
    private void openGuide() {
        Client client = Access.plugin().client;
        if (claiming && sawGuide) {
            // Ours is already on screen, so there is nothing to ask for.
            return;
        }
        Widget open = client.getWidget(InterfaceID.SkillGuide.WINDOW);
        if (open != null && !open.isHidden()) {
            // The window is already up on a real skill's guide, so it is taken over where it
            // stands.
            claiming = true;
            dressed = false;
            return;
        }
        int slot = modalSlot(client.getTopLevelInterfaceId());
        // Another window is in that slot, and opening over it would close it here while the
        // server still thinks it is up.
        if (slot < 0 || client.getComponentTable().get(slot) != null) {
            return;
        }
        claiming = true;
        // After the click rather than inside it: opening runs the window's own load script.
        Access.plugin().invokeOnClientThread(() -> {
            // The slot is looked at again now that it is time to open, because the click was
            // a moment ago. Something may have been hung there since, and opening over it would
            // close it here while the server still thinks it is up. Or this is a second click
            // from the same moment, and what is there is ours, still to be dressed.
            WidgetNode there = client.getComponentTable().get(slot);
            if (there != null) {
                claiming = there == opened;
                return;
            }
            try {
                openedAt = slot;
                opened = client.openInterface(slot, InterfaceID.SKILL_GUIDE,
                    WidgetModalMode.MODAL_NOCLICKTHROUGH);
            } catch (IllegalStateException e) {
                log.info("[guide] could not open the window: {}", e.getMessage());
                claiming = false;
            }
        });
    }

    /** The slot each layout opens its main windows in, the guide among them. */
    private static int modalSlot(int topLevel) {
        switch (topLevel) {
            case InterfaceID.TOPLEVEL:
                return InterfaceID.Toplevel.MAINMODAL;
            case InterfaceID.TOPLEVEL_OSRS_STRETCH:
                return InterfaceID.ToplevelOsrsStretch.MAINMODAL;
            case InterfaceID.TOPLEVEL_PRE_EOC:
                return InterfaceID.ToplevelPreEoc.MAINMODAL;
            default:
                return -1;
        }
    }

    /**
     * A click on the window we opened. The server does not know it is open, so nothing clicked
     * on it goes there: Close closes it here, and nothing else does anything.
     */
    void clicked(MenuOptionClicked event) {
        Widget widget = event.getWidget();
        if (opened == null || widget == null) {
            return;
        }
        int group = WidgetUtil.componentToInterface(widget.getId());
        String option = event.getMenuOption();
        if (group == InterfaceID.STATS && option.startsWith("View") && !option.equals(guideOption())) {
            // A real skill's guide, which the game opens where ours is. Opened over ours it came
            // up blank, so ours goes first and the game's opens as if nothing had been up.
            letGo();
            return;
        }
        if (group != InterfaceID.SKILL_GUIDE) {
            return;
        }
        event.consume();
        if (option.startsWith("Close")) {
            letGo();
        }
    }

    /**
     * The wheel over the ranks.
     *
     * <p>The game wires a panel's wheel up as part of building its scroll bar, and it only
     * builds one for a page that needs it. The page it fills before ours goes in may fit, and
     * then nothing is wired and the wheel does nothing over the ranks.
     *
     * <p>This runs on the mouse's thread, not the client's, so it asks no widget anything.
     * Doing that is not merely unsafe: reading one throws "must be called on client thread"
     * straight out of the listener, which is what the first version of this did on every turn
     * of the wheel. It reads the rectangle the client thread leaves for it instead, and hands
     * the scrolling itself back to that thread.
     */
    private final class Wheel implements MouseWheelListener {
        @Override
        public MouseWheelEvent mouseWheelMoved(MouseWheelEvent event) {
            Rectangle area = guideArea;
            if (area == null || !area.contains(event.getX(), event.getY())) {
                return event;
            }
            int by = event.getWheelRotation() * GUIDE_ROW;
            Access.plugin().invokeOnClientThread(() -> {
                Widget panel = Access.plugin().client.getWidget(InterfaceID.SkillGuide.INFO);
                if (panel != null) {
                    scroll(panel, panel.getScrollY() + by);
                }
            });
            // Ours to handle, so nothing else gets a second go at the same turn of the wheel.
            event.consume();
            return event;
        }
    }

    /** Writes the ranks into the real window, and keeps writing them while it is open. */
    private void dressGuide(Client client) {
        if (!claiming) {
            return;
        }
        Widget window = client.getWidget(InterfaceID.SkillGuide.WINDOW);
        if (window == null || window.isHidden()) {
            // Not up YET is not the same as closed: the window opens after the click, not in it.
            //
            // But it is up within a tick of being opened, so one still not seen after a second
            // never will be -- the interface this opens by number having stopped being the
            // guide in some game update, say. Waited on for ever, that would leave an empty
            // window that lets no click through hanging over the game until the next login.
            if (sawGuide || ++waited > GUIDE_WAIT) {
                letGo();
            }
            return;
        }
        sawGuide = true;
        Widget title = client.getWidget(InterfaceID.SkillGuide.TITLE);
        Widget description = client.getWidget(InterfaceID.SkillGuide.DESCRIPTION);
        Widget info = client.getWidget(InterfaceID.SkillGuide.INFO);
        if (title == null || description == null || info == null) {
            return;
        }
        // Already ours since the last time the script ran, so the ten rows are not rebuilt every
        // tick -- but the bar below is still looked at, because the game draws that one late.
        boolean fresh = !FlipLevel.SKILL.equals(title.getText());
        if (fresh && dressed) {
            // The ranks were in this window and are not any more, which only happens when a
            // real skill's guide has been opened over ours. That one is meant to be read, so
            // the window goes back to the game rather than being taken a second time.
            letGo();
            return;
        }
        if (fresh) {
            fill(client, title, description, info);
            dressed = true;
        }
        scrollbar(info, fresh);
    }

    /** Stops writing into the guide window, whether it closed or somebody else claimed it. */
    private void letGo() {
        Client client = Access.plugin().client;
        // Only while it is still the one in its slot. Anything else there is the game's own.
        if (opened != null && client.getComponentTable().get(openedAt) == opened) {
            try {
                client.closeInterface(opened, true);
            } catch (RuntimeException e) {
                // Forgotten below all the same. A window that would not close is still not one
                // to go on writing into or taking clicks for, and stop() has more to put back
                // after this that a throw here would have skipped.
                log.info("[guide] could not close the window: {}", e.getMessage());
            }
        }
        opened = null;
        guideArea = null;
        claiming = false;
        sawGuide = false;
        waited = 0;
        dressed = false;
        barOwner = null;
        barTrack = null;
        barTop = null;
        barMid = null;
        barBottom = null;
    }

    /** The ranks, the one category, and the scroll beside them cut down to hold it. */
    private void fill(Client client, Widget title, Widget description, Widget info) {
        // Opening the guide is what settles the glow -- not hovering the square, and not
        // opening the tab. That is where the game stops its own.
        unseen = false;
        fade(glow, 255);
        title.setText(FlipLevel.SKILL);
        description.setText("Ranks");
        for (int slot = 0; slot < GUIDE_SLOTS; slot++) {
            Widget entry = client.getWidget(InterfaceID.SkillGuide._00 + slot);
            if (entry != null) {
                entry.setText(slot == 0 ? "Ranks" : "");
            }
        }
        // Everything on that little scroll is sized off its own height, so this one number is
        // the whole of it. The game gives a row 17 and the rolls at either end 28 between them:
        // the Defence guide's six categories came to 130 and Firemaking's five to 113. Without
        // it ours is left however long the last guide opened happened to be.
        Widget categories = client.getWidget(InterfaceID.SkillGuide.CATEGORIES);
        if (categories != null) {
            categories.setOriginalHeight(CATEGORY_ROW + CATEGORY_ENDS);
            categories.revalidate();
        }
        info.deleteAllChildren();
        // The game keeps a guide's item pictures in a layer of their own inside the info panel,
        // so emptying the panel leaves the last skill's items sitting on top of our rows. The
        // Defence guide proved it: two helms hanging over the ranks.
        Widget icons = client.getWidget(InterfaceID.SkillGuide.ICONS);
        if (icons != null) {
            icons.deleteAllChildren();
        }
        for (int band = 0; band < RankUp.TITLES.length; band++) {
            guideRow(info, band);
        }
        info.setScrollHeight(RankUp.TITLES.length * GUIDE_ROW);
        // Back to the top. The panel keeps whatever the LAST guide was scrolled to, and ours is
        // a short page: coming off a long one that was scrolled down left every rank above the
        // top of the panel and nothing but parchment on it. It came back on the first turn of
        // the wheel only because scrolling clamps to the new page's length.
        info.setScrollY(0);
        info.revalidateScroll();
    }

    /**
     * Draws the panel's scroll bar, and keeps it where the panel has scrolled to.
     *
     * <p>A scroll height on its own leaves the panel scrollable with nothing to take hold of.
     * The game's own scrollbar script was the obvious answer and it is the wrong one: it MOVES
     * a bar, it does not make one. Asked to update a guide that had never built one -- a real
     * page that fits -- it left the widget empty with everything else about it correct, which
     * is what the log showed. So the bar is built
     * here out of the pieces the interface itself uses: a 16x16 arrow at each end and a track
     * of 16x5 tiles between them, with a dragger of a cap, a tiled middle and a cap.
     */
    private void scrollbar(Widget info, boolean fresh) {
        Widget bar = Access.plugin().client.getWidget(InterfaceID.SkillGuide.SCROLLBAR);
        if (bar == null) {
            return;
        }
        int wanted = RankUp.TITLES.length * GUIDE_ROW;
        if (info.getScrollHeight() != wanted) {
            info.setScrollHeight(wanted);
            info.revalidateScroll();
        }
        // Left for the wheel listener, which runs where widgets cannot be read.
        guideArea = info.getBounds();
        Widget[] drawn = bar.getChildren();
        if (fresh || bar != barOwner || barTrack == null
            || drawn == null || drawn.length < BAR_PIECES) {
            buildBar(bar, info);
        }
        dragger(info);
    }

    /** The bar itself, in the widget the guide keeps its own in. */
    private void buildBar(Widget bar, Widget info) {
        bar.deleteAllChildren();
        if (bar.isSelfHidden()) {
            bar.setHidden(false);
        }
        // A bar the guide has put away is not only hidden, its width goes to nothing as well.
        // It belongs immediately right of the panel and as tall, which is where the interface
        // has it: 16 by 239 at 316,76 against the panel's 295 by 239 at 21,76.
        bar.setOriginalX(info.getRelativeX() + info.getWidth());
        bar.setOriginalY(info.getRelativeY());
        bar.setOriginalWidth(SCROLL_WIDTH);
        bar.setOriginalHeight(info.getHeight());
        bar.revalidate();

        // The channel goes down FIRST, because the dragger's middle is a pair of side rails
        // with nothing between them -- what shows through the gap is meant to be this.
        barTrack = piece(bar, SpriteID.ScrollbarParchmentDraggerV2.TRACK, SCROLL_ARROW,
            Math.max(0, info.getHeight() - 2 * SCROLL_ARROW));
        // Clicked and held both, and both go to the same place: the click is the jump to where
        // you pressed, and the hold, which repeats for as long as the button is down, is the
        // drag. Asking for an absolute position each time means the two cannot fight.
        barTrack.setOnClickListener((JavaScriptCallback) ev -> scrollTo(info, ev.getMouseY()));
        barTrack.setOnHoldListener((JavaScriptCallback) ev -> scrollTo(info, ev.getMouseY()));
        barTrack.setNoClickThrough(true);
        barTrack.setHasListener(true);

        arrow(bar, info, SpriteID.ScrollbarParchmentV2.UP, 0, -GUIDE_ROW);
        arrow(bar, info, SpriteID.ScrollbarParchmentV2.DOWN,
            info.getHeight() - SCROLL_ARROW, GUIDE_ROW);

        barTop = piece(bar, SpriteID.ScrollbarParchmentDraggerV2.TOP, SCROLL_ARROW, SCROLL_CAP);
        barMid = piece(bar, SpriteID.ScrollbarParchmentDraggerV2.MIDDLE, SCROLL_ARROW, SCROLL_CAP);
        barBottom = piece(bar, SpriteID.ScrollbarParchmentDraggerV2.BOTTOM, SCROLL_ARROW,
            SCROLL_CAP);
        barOwner = bar;
    }

    /** One end of the bar: a whole sprite, and a rank of movement when it is clicked. */
    private Widget arrow(Widget bar, Widget info, int sprite, int y, int by) {
        Widget part = piece(bar, sprite, y, SCROLL_ARROW);
        part.setSpriteTiling(false);
        part.setAction(0, by < 0 ? "Scroll up" : "Scroll down");
        part.setOnOpListener((JavaScriptCallback) ev -> scroll(info, info.getScrollY() + by));
        part.setNoClickThrough(true);
        part.setHasListener(true);
        return part;
    }

    /** A strip of the bar, tiled down its length. */
    private static Widget piece(Widget bar, int sprite, int y, int height) {
        Widget part = bar.createChild(-1, WidgetType.GRAPHIC);
        part.setSpriteId(sprite);
        part.setSpriteTiling(true);
        part.setOriginalX(0);
        part.setOriginalY(y);
        part.setOriginalWidth(SCROLL_WIDTH);
        part.setOriginalHeight(height);
        part.revalidate();
        return part;
    }

    /**
     * Puts the dragger where the panel has scrolled to, however it got there.
     *
     * <p>Held by reference rather than looked up by position in the bar's children. An earlier
     * version took them as indexes 3, 4 and 5 of that array and moved whatever was there, which
     * is only the dragger while nothing else has ever been built into the same widget.
     */
    private void dragger(Widget info) {
        if (barTop == null || barMid == null || barBottom == null) {
            return;
        }
        int height = draggerHeight(info);
        int run = info.getHeight() - 2 * SCROLL_ARROW - height;
        int along = run <= 0 ? 0 : run * info.getScrollY() / scrollable(info);
        int y = SCROLL_ARROW + Math.max(0, Math.min(run, along));
        place(barTop, y, SCROLL_CAP);
        place(barMid, y + SCROLL_CAP, Math.max(0, height - 2 * SCROLL_CAP));
        place(barBottom, y + height - SCROLL_CAP, SCROLL_CAP);
    }

    private static void place(Widget part, int y, int height) {
        if (part.getRelativeY() == y && part.getHeight() == height) {
            return;
        }
        part.setOriginalY(y);
        part.setOriginalHeight(height);
        part.revalidate();
    }

    /** As much of the track as the dragger takes up, never less than a thing you can grab. */
    private static int draggerHeight(Widget info) {
        int track = info.getHeight() - 2 * SCROLL_ARROW;
        int content = Math.max(1, info.getScrollHeight());
        return Math.max(SCROLL_LEAST, Math.min(track, track * info.getHeight() / content));
    }

    /** How far the panel can go, never zero, so it is safe to divide by. */
    private static int scrollable(Widget info) {
        return Math.max(1, info.getScrollHeight() - info.getHeight());
    }

    /** Where a press on the track goes: the dragger centred on the pointer. */
    private void scrollTo(Widget info, int mouseY) {
        int run = info.getHeight() - 2 * SCROLL_ARROW - draggerHeight(info);
        if (run <= 0) {
            return;
        }
        int at = mouseY - SCROLL_ARROW - draggerHeight(info) / 2;
        scroll(info, at * scrollable(info) / run);
    }

    private void scroll(Widget info, int to) {
        int most = Math.max(0, info.getScrollHeight() - info.getHeight());
        info.setScrollY(Math.max(0, Math.min(most, to)));
        info.revalidateScroll();
    }

    /** One rank: its level, its item, and the name with what it costs underneath. */
    private void guideRow(Widget info, int band) {
        int level = RankUp.BAND_FIRST_LEVEL[band];
        long needed = FlipLevel.profitFor(level);
        int y = band * GUIDE_ROW;
        // The three columns the game's own guide uses, measured off the Defence one: the level
        // in 26, the item in 36 beside it, and the rest of the row for the words. The level
        // centres on the row rather than on a line, as it does beside a two-line entry there.
        guideText(info, Integer.toString(level), 0, y + (GUIDE_ROW - 14) / 2, 26,
            WidgetTextAlignment.RIGHT);

        Widget picture = info.createChild(-1, WidgetType.GRAPHIC);
        picture.setItemId(RANK_ITEM[band]);
        picture.setItemQuantity(0);
        // Never draw a stack number over the helm.
        picture.setItemQuantityMode(0);
        // The one-pixel black outline every item wears in the game's own guide. Without it the
        // picture sits straight on the parchment with nothing to part the two, and that was the
        // whole of why ours read as washed out beside the Defence guide's: same sprite, same
        // size, no edge. It is cut INTO the 36 by 32, so nothing has to grow to hold it.
        picture.setBorderType(1);
        picture.setOriginalX(28);
        picture.setOriginalY(y + (GUIDE_ROW - GUIDE_ICON) / 2);
        picture.setOriginalWidth(36);
        picture.setOriginalHeight(GUIDE_ICON);
        picture.revalidate();

        // The name, and what it takes on the line under it. Both centre on the picture together.
        int top = y + (GUIDE_ROW - 2 * GUIDE_LINE) / 2;
        guideText(info, RankUp.TITLES[band], 67, top, 228, WidgetTextAlignment.LEFT);
        // Green once the rank has been earned and red until then, the way the game colours a
        // requirement of its own. Written when the window opens, which is the only time a rank
        // can change hands without it being closed.
        String colour = String.format("%06x", profit() >= needed ? GUIDE_MET : GUIDE_SHORT);
        guideText(info, "Required GP: <col=" + colour + ">" + shortGp(needed) + "</col>",
            67, top + GUIDE_LINE, 228, WidgetTextAlignment.LEFT);
    }

    private static void guideText(Widget info, String value, int x, int y, int width, int align) {
        Widget text = info.createChild(-1, WidgetType.TEXT);
        text.setText(value);
        text.setTextColor(GUIDE_INK);
        text.setFontId(FontID.PLAIN_12);
        text.setOriginalX(x);
        text.setOriginalY(y);
        text.setOriginalWidth(width);
        text.setOriginalHeight(14);
        text.setXTextAlignment(align);
        text.setYTextAlignment(WidgetTextAlignment.CENTER);
        text.revalidate();
    }


}
