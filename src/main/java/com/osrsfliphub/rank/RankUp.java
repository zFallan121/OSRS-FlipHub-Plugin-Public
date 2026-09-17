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
 * FlipHub's ten ranks: the level-up style message when a sale earns a new one, and the rank
 * shown on the Profile tab.
 *
 * <p>A rank is decided by lifetime profit across every character on this computer, added up
 * character by character the way the Accountwide view does it. The lines and names are the
 * website's rank ladder (RANK_DEFINITIONS in the site's app.py) and must stay in step with it.
 *
 * <p>Only a live sale can set the message off. Imports, recorded recipes and wipes all move the
 * total too, but they never pass through {@link TradeDeltaRecorder}, which is the only caller of
 * {@link #onSale}. The best rank ever celebrated is remembered, so a rank is celebrated once,
 * however the total wanders below its line and back.
 */
@Singleton
final class RankUp {
    // Not private: the development client's rank-up tester moves a line to just above the
    // current total so that a real sale crosses it.
    static final long[] LINES = {0L, 200_000L, 1_000_000L, 4_000_000L, 20_000_000L,
        100_000_000L, 300_000_000L, 800_000_000L, 2_000_000_000L, 6_000_000_000L};
    static final String[] TITLES = {"Lumbridge Looter", "Greenhorn", "Upstart", "Edgeville Operator",
        "Varrock Hustler", "Trader", "Professional", "Platinum Flipper", "GE Mogul", "Gielinor Elite"};
    static final String BEST_KEY = "bestFlipRank";

    // Plugins override sprites under negative ids, which the game never uses. One per rank,
    // counting down from here.
    private static final int SPRITE = -21_460;

    // Every picture's longer side is 64, which is also the size it is drawn at in the chatbox.
    // Their edges are hard, because the game draws any pixel that is not fully clear as solid.
    private final BufferedImage[] pictures = new BufferedImage[TITLES.length];

    // The Profile tab's are separate files, 28 on the longer side, made from the full-size art
    // with soft edges. Shrinking the 64s instead washed out thin parts, like Trader's spikes,
    // until the picture looked cut off.
    private final Icon[] panelIcons = new Icon[TITLES.length];

    // A rank earned but not yet shown, because the chatbox was busy. -1 when there is none.
    private volatile int pending = -1;

    // Each rank's colour for the Profile tab's FLIP RANK: its helm's colour, lightened to read on
    // the panel's navy. The grey and white helms are pushed apart so no two ranks look alike.
    static final int[] COLOURS = {0xCD9B63, 0x9FA9B5, 0x5FA8C8, 0xE5584A, 0xB3C646,
        0xE9C46A, 0xD3D8DF, 0x6ED6EC, 0xF3E6C4, 0x7086FF};

    // The Profile tab's FLIP RANK words and rank picture, handed over when the panel is built.
    volatile JLabel panelText;
    volatile JLabel panelPicture;

    @Inject
    RankUp() {
    }

    static int rankFor(long profit) {
        int rank = 0;
        while (rank + 1 < LINES.length && profit >= LINES[rank + 1]) {
            rank++;
        }
        return rank;
    }

    /**
     * The rank a sale moving the total from {@code before} to {@code after} has just earned, or
     * -1. Two lines crossed at once earn only the higher rank, and nothing at or below the best
     * rank already celebrated is earned again.
     */
    static int earned(long before, long after, int best) {
        int rank = rankFor(after);
        return rank > rankFor(before) && rank > best ? rank : -1;
    }

    /** "a Trader", "an Upstart". */
    static String named(int rank) {
        return (rank == 2 || rank == 3 ? "an " : "a ") + TITLES[rank];
    }

    static String tooltip(int rank) {
        String next = rank + 1 < TITLES.length
            ? "Next: " + TITLES[rank + 1] + " at " + shortGp(LINES[rank + 1])
            : "The highest rank";
        return "<html>Flip Rank: " + TITLES[rank] + "<br>" + next + "</html>";
    }

    /** 200K, 20M, 2B. Every line is a round number, so nothing is lost. */
    static String shortGp(long gp) {
        return gp >= 1_000_000_000L ? gp / 1_000_000_000L + "B"
            : gp >= 1_000_000L ? gp / 1_000_000L + "M"
            : gp / 1_000L + "K";
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

    int bestRank() {
        Integer best = Access.plugin().configManager
            .getConfiguration(FliphubConfigGroups.CONFIG_GROUP, BEST_KEY, Integer.class);
        return best != null ? best : 0;
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
            long combined = combinedLifetimeProfit();
            int rank = earned(combined - (after - before), combined, bestRank());
            if (rank >= 0) {
                plugin.configManager.setConfiguration(FliphubConfigGroups.CONFIG_GROUP, BEST_KEY, rank);
                celebrate(rank);
            }
        });
    }

    /** The chat line straight away, the message and fireworks once the chatbox is free. */
    void celebrate(int rank) {
        GeLifecyclePlugin plugin = Access.plugin();
        plugin.invokeOnClientThread(() -> plugin.runtimeUtilityServices.pushGameMessage(plugin.client,
            "Congratulations, you've just advanced your Flipping rank. You are now " + named(rank) + "."));
        pending = rank;
    }

    /** Every client tick: show a waiting message once nothing else has the chatbox. */
    void tick() {
        int rank = pending;
        if (rank < 0) {
            return;
        }
        Client client = Access.plugin().client;
        if (client.getGameState() == GameState.LOGIN_SCREEN) {
            pending = -1;
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
        // Both drawn by this client only: nobody else sees them, and nothing is sent to the game.
        Player player = client.getLocalPlayer();
        if (player != null) {
            player.createSpotAnim(SPRITE, SpotanimID.LEVELUP_ANIM, 0, 0);
            player.setAnimation(AnimationID.EMOTE_DANCE);
            player.setAnimationFrame(0);
        }
        Bridge.get(ChatboxPanelManager.class).openInput(new Message(rank));
    }

    /** Called on the scheduler whenever the Profile tab's figures refresh. */
    void refreshPanel() {
        JLabel text = panelText;
        JLabel picture = panelPicture;
        int rank = rankFor(combinedLifetimeProfit());
        Icon icon = panelIcons[rank];
        if (text == null || picture == null || icon == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            picture.setIcon(icon);
            text.setForeground(new java.awt.Color(COLOURS[rank]));
            text.setToolTipText(tooltip(rank));
            picture.setToolTipText(tooltip(rank));
        });
    }

    /** The message itself, laid out like the game's level-up: picture left, three lines right. */
    private final class Message extends ChatboxInput implements KeyListener {
        private final int rank;

        Message(int rank) {
            this.rank = rank;
        }

        @Override
        protected void open() {
            Widget box = Bridge.get(ChatboxPanelManager.class).getContainerWidget();
            BufferedImage picture = pictures[rank];
            Widget icon = box.createChild(-1, WidgetType.GRAPHIC);
            icon.setSpriteId(SPRITE - rank);
            icon.setOriginalX(56 - picture.getWidth() / 2);
            icon.setYPositionMode(WidgetPositionMode.ABSOLUTE_CENTER);
            icon.setOriginalWidth(picture.getWidth());
            icon.setOriginalHeight(picture.getHeight());
            icon.revalidate();

            line(box, "Congratulations, you just advanced a Flipping rank.", 0x000080, -26);
            line(box, "You are now " + named(rank) + ".", 0x000000, -8);
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
