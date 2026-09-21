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

import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import javax.inject.*;
import javax.swing.*;
import net.runelite.api.*;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.*;
import net.runelite.client.game.chatbox.*;
import net.runelite.client.input.KeyListener;
import net.runelite.client.util.ImageUtil;

/**
 * Merchant levels: the level-up message when a sale earns one, and the rank shown on the
 * Profile tab.
 *
 * <p>The level is lifetime profit across every character on this computer, added up character by
 * character the way the Accountwide view does it, read on {@link FlipLevel}'s curve. Every level
 * is celebrated, the way the game celebrates every level.
 *
 * <p>The website's ten rank names survive as BANDS. A band is now a range of LEVELS rather than
 * a gp line, so that a player is never shown a level and a title that disagree about where they
 * are. {@link #BAND_FIRST_LEVEL} holds those levels, picked as the level each of the site's old
 * gp lines falls on, and every one of them sits at or below the old line, so the change promotes
 * some accounts and demotes none. <b>The site's RANK_DEFINITIONS need the same table</b> or the
 * two will name a different rank for the same profit.
 *
 * <p>Only a live sale can set the message off. Imports, recorded recipes and wipes all move the
 * total too, but they never pass through {@link TradeDeltaRecorder}, which is the only caller of
 * {@link #onSale}. The best level ever celebrated is remembered, so a level is celebrated once,
 * however the total wanders below it and back.
 */
@Singleton
final class RankUp {
    /**
     * The level each band begins at. These are the levels the site's old gp lines
     * (0, 200K, 1M, 4M, 20M, 100M, 300M, 800M, 2B, 6B) land on, so the ladder a player knows is
     * the ladder they keep.
     */
    static final int[] BAND_FIRST_LEVEL = {1, 3, 10, 21, 36, 52, 63, 73, 82, 93};
    static final String[] TITLES = {"Lumbridge Looter", "Greenhorn", "Upstart", "Edgeville Operator",
        "Varrock Hustler", "Trader", "Professional", "Platinum Flipper", "GE Mogul", "Gielinor Elite"};

    // A new key, not the old bestFlipRank: that one holds a band index 0-9, which read as a
    // level would silently swallow every celebration up to level 9.
    static final String BEST_KEY = "bestFlipLevel";

    // Prestige is remembered apart from the level. Both stop at their own best, so a total that
    // wanders below a line and back does not pay out twice.
    static final String BEST_PRESTIGE_KEY = "bestFlipPrestige";

    // Plugins override sprites under negative ids, which the game never uses. One per band,
    // counting down from here.
    private static final int SPRITE = -21_460;

    // Every picture's longer side is 64, which is also the size it is drawn at in the chatbox.
    // Their edges are hard, because the game draws any pixel that is not fully clear as solid.
    private final BufferedImage[] pictures = new BufferedImage[TITLES.length];

    // The Profile tab's are separate files, 28 on the longer side, made from the full-size art
    // with soft edges. Shrinking the 64s instead washed out thin parts, like Trader's spikes,
    // until the picture looked cut off.
    private final Icon[] panelIcons = new Icon[TITLES.length];

    // A level earned but not yet shown, because the chatbox was busy. -1 when there is none.
    private volatile int pending = -1;

    // The same for a prestige tier. Past 99 the level stops moving, so this is the only thing
    // left that can be earned.
    private volatile int pendingTier = -1;

    // Each band's colour for the Profile tab's rank: its helm's colour, lightened to read on
    // the panel's navy. The grey and white helms are pushed apart so no two bands look alike.
    static final int[] COLOURS = {0xCD9B63, 0x9FA9B5, 0x5FA8C8, 0xE5584A, 0xB3C646,
        0xE9C46A, 0xD3D8DF, 0x6ED6EC, 0xF3E6C4, 0x7086FF};

    // Lifetime profit across every character, as it was last added up. The skills tab reads it
    // straight off here: it draws on the client thread, where adding it up would read files.
    volatile long combined;

    // The Profile tab's FLIP RANK words and rank picture, handed over when the panel is built.
    volatile JLabel panelText;
    volatile JLabel panelPicture;

    @Inject
    RankUp() {
    }

    /** The band a level falls in, 0-9. */
    static int bandFor(int level) {
        int band = 0;
        while (band + 1 < BAND_FIRST_LEVEL.length && level >= BAND_FIRST_LEVEL[band + 1]) {
            band++;
        }
        return band;
    }

    /**
     * The level a sale moving the total from {@code before} to {@code after} has just earned, or
     * -1. Several levels at once earn only the highest, and nothing at or below the best level
     * already celebrated is earned again.
     */
    static int earned(long before, long after, int best) {
        int level = FlipLevel.levelFor(after);
        return level > FlipLevel.levelFor(before) && level > best ? level : -1;
    }

    /** The same again for prestige, which is all there is to earn once the level has stopped. */
    static int earnedPrestige(long before, long after, int best) {
        int tier = FlipLevel.prestigeFor(after);
        return tier > FlipLevel.prestigeFor(before) && tier > best ? tier : -1;
    }

    /** "a Trader", "an Upstart". */
    static String named(int band) {
        return (band == 2 || band == 3 ? "an " : "a ") + TITLES[band];
    }

    static String tooltip(int band) {
        String next = band + 1 < TITLES.length
            ? "Next: " + TITLES[band + 1] + " at level " + BAND_FIRST_LEVEL[band + 1]
            : "The highest rank";
        return "<html>Flip Rank: " + TITLES[band] + "<br>" + next + "</html>";
    }

    /** Lifetime profit of one character, as its stats cache holds it. */
    static long lifetimeProfit(long accountKey) {
        LocalStatsCacheService caches = Bridge.get(LocalStatsCacheService.class);
        StatsCache cache = caches != null ? caches.getOrBuild(accountKey) : null;
        Long profit = cache != null ? cache.getSummary().total_profit_gp : null;
        return profit != null ? profit : 0L;
    }

    /**
     * Lifetime profit across every character on this computer. The first call after start-up
     * reads each character's saved trades, so never call it on the client thread.
     */
    long combinedLifetimeProfit() {
        LocalStatsSnapshotService snapshots = Bridge.get(LocalStatsSnapshotService.class);
        long total = 0L;
        if (snapshots == null) {
            return total;
        }
        for (long key : snapshots.collectAccountwideProfileKeys()) {
            Access.plugin().getLocalTradesRuntimeService().ensureProfileLoaded(key);
            total += lifetimeProfit(key);
        }
        return total;
    }

    int bestLevel() {
        return best(BEST_KEY);
    }

    int bestPrestige() {
        return best(BEST_PRESTIGE_KEY);
    }

    private static int best(String key) {
        Integer stored = Access.plugin().configManager
            .getConfiguration(FliphubConfigGroups.CONFIG_GROUP, key, Integer.class);
        return stored != null ? stored : 0;
    }

    void start() {
        GeLifecyclePlugin plugin = Access.plugin();
        for (int i = 0; i < pictures.length; i++) {
            pictures[i] = ImageUtil.loadImageResource(RankUp.class, "/com/osrsfliphub/rank/" + (i + 1) + ".png");
            panelIcons[i] = new ImageIcon(ImageUtil.loadImageResource(RankUp.class, "/com/osrsfliphub/rank/" + (i + 1) + "-panel.png"));
        }
        plugin.invokeOnClientThread(() -> {
            for (int i = 0; i < pictures.length; i++) {
                plugin.client.getSpriteOverrides().put(SPRITE - i, ImageUtil.getImageSpritePixels(pictures[i], plugin.client));
            }
        });
    }

    void stop() {
        GeLifecyclePlugin plugin = Access.plugin();
        pending = -1;
        pendingTier = -1;
        panelText = null;
        panelPicture = null;
        ChatboxPanelManager chatbox = Bridge.get(ChatboxPanelManager.class);
        if (chatbox != null && chatbox.getCurrentInput() instanceof Message) {
            chatbox.close();
        }
        plugin.invokeOnClientThread(() -> {
            for (int i = 0; i < pictures.length; i++) {
                plugin.client.getSpriteOverrides().remove(SPRITE - i);
            }
        });
    }

    /**
     * A live sale has moved the selling character's lifetime profit from {@code before} to
     * {@code after}. Called on the client thread; the adding-up happens on the scheduler.
     */
    void onSale(long before, long after) {
        GeLifecyclePlugin plugin = Access.plugin();
        if (after == before || !plugin.config.celebrateRankUps()) {
            return;
        }
        plugin.executeAsync(() -> {
            // Only the selling character's total moved, so the combined total before the sale
            // is the combined total now less that character's change.
            combined = combinedLifetimeProfit();
            long was = combined - (after - before);
            int level = earned(was, combined, bestLevel());
            if (level > 0) {
                plugin.configManager.setConfiguration(FliphubConfigGroups.CONFIG_GROUP, BEST_KEY, level);
                celebrate(level);
            }
            // Checked after the level, so a sale that somehow crossed both leaves the prestige
            // on screen -- it is the further of the two.
            int tier = earnedPrestige(was, combined, bestPrestige());
            if (tier > 0) {
                plugin.configManager.setConfiguration(FliphubConfigGroups.CONFIG_GROUP,
                    BEST_PRESTIGE_KEY, tier);
                celebratePrestige(tier);
            }
        });
    }

    /** The chat line straight away, the message and fireworks once the chatbox is free. */
    void celebrate(int level) {
        GeLifecyclePlugin plugin = Access.plugin();
        int band = bandFor(level);
        plugin.invokeOnClientThread(() -> {
            plugin.runtimeUtilityServices.pushGameMessage(plugin.client,
                "Congratulations, you've just advanced your " + FlipLevel.SKILL
                    + " level. You are now level " + level + ".");
            // A level that opens a band is a rank-up as well, and these names are the only place
            // the website's ladder is spoken aloud in game.
            if (level == BAND_FIRST_LEVEL[band]) {
                plugin.runtimeUtilityServices.pushGameMessage(plugin.client,
                    "You are now " + named(band) + ".");
            }
        });
        pending = level;
        flashSkill();
    }

    /** The same again past 99, where a tier is the only thing left to earn. */
    void celebratePrestige(int tier) {
        GeLifecyclePlugin plugin = Access.plugin();
        plugin.invokeOnClientThread(() -> plugin.runtimeUtilityServices.pushGameMessage(plugin.client,
            "Congratulations, you've just advanced your " + FlipLevel.SKILL + " prestige. "
                + "You are now prestige " + FlipLevel.roman(tier) + "."));
        pendingTier = tier;
        flashSkill();
    }

    /**
     * Sets the skills tab flashing, the way the game does for a level it has just given you.
     *
     * <p>It rides with the celebration rather than beside it, so turning off Celebrate
     * level-ups turns this off too. That is the reading that matches the setting's words: it is
     * there for a player who does not want a level-up drawn to their attention, and a flashing
     * tab is exactly that. It settles once the tab is opened and the square is hovered.
     */
    private static void flashSkill() {
        SkillTab tab = Bridge.get(SkillTab.class);
        if (tab != null) {
            tab.flash();
        }
    }

    /** Every client tick: show a waiting message once nothing else has the chatbox. */
    void tick() {
        int level = pending;
        int tier = pendingTier;
        if (level < 0 && tier < 0) {
            return;
        }
        Client client = Access.plugin().client;
        if (client.getGameState() == GameState.LOGIN_SCREEN) {
            pending = -1;
            pendingTier = -1;
            return;
        }
        // A typed prompt (price, quantity, item search, or another plugin's panel) sets the
        // message layer's mode; a conversation or the game's own level-up mounts an interface
        // in the chat modal. Opening over either would close it under the player.
        if (client.getGameState() != GameState.LOGGED_IN
            || client.getVarcIntValue(VarClientID.MESLAYERMODE) != 0
            || client.getComponentTable().get(InterfaceID.Chatbox.CHATMODAL) != null) {
            return;
        }
        pending = -1;
        pendingTier = -1;
        // Both drawn by this client only: nobody else sees them, and nothing is sent to the game.
        Player player = client.getLocalPlayer();
        if (player != null) {
            player.createSpotAnim(SPRITE, SpotanimID.LEVELUP_ANIM, 0, 0);
            player.setAnimation(AnimationID.EMOTE_DANCE);
            player.setAnimationFrame(0);
        }
        // A prestige message keeps the top band's picture, which is the one a 99 account has.
        Bridge.get(ChatboxPanelManager.class)
            .openInput(new Message(tier > 0 ? FlipLevel.MAX_LEVEL : level, tier));
    }

    /** Called on the scheduler whenever the Profile tab's figures refresh. */
    void refreshPanel() {
        JLabel text = panelText;
        JLabel picture = panelPicture;
        combined = combinedLifetimeProfit();
        int band = bandFor(FlipLevel.levelFor(combined));
        Icon icon = panelIcons[band];
        if (text == null || picture == null || icon == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            picture.setIcon(icon);
            text.setForeground(new java.awt.Color(COLOURS[band]));
            text.setToolTipText(tooltip(band));
            picture.setToolTipText(tooltip(band));
        });
    }

    /** The message itself, laid out like the game's level-up: picture left, three lines right. */
    private final class Message extends ChatboxInput implements KeyListener {
        private final int level;
        private final int tier;
        private final int band;

        Message(int level, int tier) {
            this.level = level;
            this.tier = tier;
            this.band = bandFor(level);
        }

        @Override
        protected void open() {
            Widget box = Bridge.get(ChatboxPanelManager.class).getContainerWidget();
            BufferedImage picture = pictures[band];
            Widget icon = box.createChild(-1, WidgetType.GRAPHIC);
            icon.setSpriteId(SPRITE - band);
            icon.setOriginalX(56 - picture.getWidth() / 2);
            icon.setYPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
            icon.setOriginalWidth(picture.getWidth());
            icon.setOriginalHeight(picture.getHeight());
            icon.revalidate();

            String what = tier > 0 ? " prestige." : " level.";
            String now = tier > 0
                ? "Your " + FlipLevel.SKILL + " prestige is now " + FlipLevel.roman(tier) + "."
                : "Your " + FlipLevel.SKILL + " level is now " + level + ".";
            line(box, "Congratulations, you just advanced a " + FlipLevel.SKILL + what, 0x000080, -26);
            line(box, now, 0x000000, -8);
            Widget next = line(box, "Click here to continue", 0x0000FF, 22);
            next.setAction(0, "Continue");
            next.setOnOpListener((JavaScriptCallback) ev -> Bridge.get(ChatboxPanelManager.class).close());
            // White under the pointer, as the game's own continue line does.
            next.setOnMouseOverListener((JavaScriptCallback) ev -> next.setTextColor(0xFFFFFF));
            next.setOnMouseLeaveListener((JavaScriptCallback) ev -> next.setTextColor(0x0000FF));
            next.setHasListener(true);
        }

        // Centred in the space right of the picture, at a height relative to the box's middle.
        private Widget line(Widget box, String text, int colour, int y) {
            Widget widget = box.createChild(-1, WidgetType.TEXT);
            widget.setText(text);
            widget.setTextColor(colour);
            widget.setFontId(FontID.QUILL_8);
            widget.setOriginalX(104);
            widget.setOriginalWidth(114);
            widget.setWidthMode(WidgetSizeMode.MINUS);
            widget.setYPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
            widget.setOriginalY(y);
            widget.setOriginalHeight(18);
            widget.setXTextAlignment(WidgetTextAlignment.CENTER);
            widget.setYTextAlignment(WidgetTextAlignment.CENTER);
            widget.revalidate();
            return widget;
        }

        @Override
        public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                e.consume();
                Bridge.get(ChatboxPanelManager.class).close();
            }
        }

        @Override
        public void keyTyped(KeyEvent e) {
        }

        @Override
        public void keyReleased(KeyEvent e) {
        }
    }
}
