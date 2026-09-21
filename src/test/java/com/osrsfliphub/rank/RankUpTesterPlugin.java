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
import java.awt.image.BufferedImage;
import java.util.Arrays;
import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.QuantityFormatter;

/**
 * Development client only: buttons for trying the level-up without earning one.
 *
 * <p>Lives under src/test, so the plugin hub never builds it and it costs nothing against the
 * hub's token count. {@code GeLifecyclePluginTest.main} loads it next to FlipHub.
 */
@PluginDescriptor(
    name = "FlipHub Level-Up Tester",
    description = "Development client only: play a FlipHub level-up, or stage a real one",
    developerPlugin = true
)
public class RankUpTesterPlugin extends Plugin {
    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ConfigManager configManager;

    // Every tier the plaque can hold: at XVIII it fills the corner it has, so there is nothing
    // past it to look at.
    private static final int MARKS = 18;

    private final long[] realLines = FlipLevel.PROFIT.clone();
    private NavigationButton navButton;
    private JLabel status;

    @Override
    protected void startUp() {
        BufferedImage icon = ImageUtil.loadImageResource(RankUp.class, "/com/osrsfliphub/rank/10.png");
        navButton = NavigationButton.builder()
            .tooltip("FlipHub level-up tester")
            .icon(ImageUtil.resizeImage(icon, 16, 14))
            .priority(20)
            .panel(buildPanel())
            .build();
        clientToolbar.addNavigation(navButton);
    }

    @Override
    protected void shutDown() {
        restoreLines();
        clientToolbar.removeNavigation(navButton);
    }

    private PluginPanel buildPanel() {
        PluginPanel panel = new PluginPanel() {
        };
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        panel.add(heading("Level-up tester"));
        panel.add(note("Development client only. This panel never ships."));
        status = note("Press Refresh to read your profit.");
        panel.add(Box.createVerticalStrut(8));
        panel.add(status);
        panel.add(button("Refresh", this::refreshStatus));

        panel.add(Box.createVerticalStrut(16));
        panel.add(heading("Play the celebration"));
        panel.add(note("Runs exactly what a real level-up runs: the chat line now, the message and "
            + "fireworks once the chatbox is free. Your best level is left alone."));
        String[] levels = new String[FlipLevel.MAX_LEVEL - 1];
        for (int i = 0; i < levels.length; i++) {
            levels[i] = "Level " + (i + 2) + " - " + RankUp.TITLES[RankUp.bandFor(i + 2)];
        }
        JComboBox<String> levelPicker = new JComboBox<>(levels);
        // Level 36, where the picture changes to the Varrock Hustler's.
        levelPicker.setSelectedIndex(34);
        levelPicker.setAlignmentX(Component.LEFT_ALIGNMENT);
        levelPicker.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        panel.add(levelPicker);
        panel.add(button("Play level-up", () -> withFlipHub(rankUp ->
            rankUp.celebrate(levelPicker.getSelectedIndex() + 2))));

        String[] tiers = new String[FlipLevel.PRESTIGE_PIPS];
        for (int i = 0; i < tiers.length; i++) {
            tiers[i] = "Prestige " + FlipLevel.roman(i + 1);
        }
        JComboBox<String> tierPicker = new JComboBox<>(tiers);
        tierPicker.setAlignmentX(Component.LEFT_ALIGNMENT);
        tierPicker.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        panel.add(tierPicker);
        panel.add(button("Play prestige", () -> withFlipHub(rankUp ->
            rankUp.celebratePrestige(tierPicker.getSelectedIndex() + 1))));

        panel.add(Box.createVerticalStrut(16));
        panel.add(heading("See a prestige on the square"));
        panel.add(note("Hands the square the profit that tier costs, so the picture, its hover and "
            + "the guide all draw it the real way -- the plaque is struck into the sprite, so there "
            + "is no other way to look at it. Open the skills tab first and watch it change. Your "
            + "saved trades are not touched: a sale, or opening the Profile tab, works the real "
            + "total out again."));
        String[] marks = new String[MARKS + 1];
        marks[0] = "No prestige - level 99";
        for (int i = 1; i <= MARKS; i++) {
            marks[i] = "Prestige " + FlipLevel.roman(i);
        }
        JComboBox<String> markPicker = new JComboBox<>(marks);
        // III, which is the one the spec page draws.
        markPicker.setSelectedIndex(3);
        markPicker.setAlignmentX(Component.LEFT_ALIGNMENT);
        markPicker.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        panel.add(markPicker);
        panel.add(button("Show it on the square", () -> wear(markPicker.getSelectedIndex())));
        // The plate widens a letter at a time, and stepping through is also the only way to see
        // that a tier CHANGE reaches the client -- the sprite is re-registered under an id the
        // widget already drew once, which only lands because the sprite cache is emptied with it.
        panel.add(button("Next tier", () -> {
            int next = (markPicker.getSelectedIndex() + 1) % marks.length;
            markPicker.setSelectedIndex(next);
            wear(next);
        }));
        panel.add(button("Put my real profit back", () -> withFlipHub(rankUp -> {
            rankUp.combined = rankUp.combinedLifetimeProfit();
            setStatus("The square is reading your real profit again.");
        })));

        panel.add(Box.createVerticalStrut(16));
        panel.add(heading("The unseen level-up"));
        panel.add(note("Sets the glow going exactly as a real level does, without earning one. "
            + "The Merchant picture washes to pale yellow and back every 2.4 seconds, the same as a "
            + "real skill's. With the tab CLOSED the Skills stone's own picture glows too; opening "
            + "the tab settles that half, and opening the Merchant guide settles the rest. Playing "
            + "a level-up above arms this too -- this is the way to see it on its own."));
        panel.add(button("Flash the tab", () -> withSkillTab(tab -> {
            tab.flash();
            setStatus("Glowing.<br>Close the skills tab to watch the stone's picture, "
                + "then open the tab and click Merchant to settle it.");
        })));
        panel.add(button("What did it find on the sidebar?", () -> withSkillTab(tab ->
            setStatus(tab.stoneReport()))));
        panel.add(button("Is it still flashing?", () -> withSkillTab(tab ->
            setStatus(tab.flashing()
                ? "Still glowing -- the guide has not been opened on it yet."
                : "Settled. Opening the guide is what does it."))));

        panel.add(Box.createVerticalStrut(16));
        panel.add(heading("Test a real sale"));
        panel.add(note("Moves the next level's line to 1 gp above your profit across all characters. "
            + "Then buy any item and sell it for more than you paid: that sale sets the level-up off "
            + "the real way. Needs Celebrate level-ups on."));
        panel.add(button("Put the next line just above my profit", this::stageNextLine));
        panel.add(button("Restore the real lines", () -> {
            restoreLines();
            setStatus("The real level lines are back.");
        }));

        panel.add(Box.createVerticalStrut(16));
        panel.add(heading("Reset"));
        panel.add(note("Forgets the best level celebrated, so real sales can celebrate levels you've already had."));
        panel.add(button("Forget best level", () -> {
            configManager.unsetConfiguration(FliphubConfigGroups.CONFIG_GROUP, RankUp.BEST_KEY);
            configManager.unsetConfiguration(FliphubConfigGroups.CONFIG_GROUP, RankUp.BEST_PRESTIGE_KEY);
            refreshStatus();
        }));
        return panel;
    }

    /** Makes the square read as a tier, by handing it the profit that tier costs. */
    private void wear(int tier) {
        withFlipHub(rankUp -> {
            long profit = FlipLevel.profitForPrestige(tier);
            rankUp.combined = profit;
            setStatus((tier > 0 ? "Prestige " + FlipLevel.roman(tier) : "Level 99, no prestige")
                + " on the square.<br>"
                + "Reading " + QuantityFormatter.formatNumber(profit) + " gp.<br>"
                + "Open the skills tab to see it.");
        });
    }

    private void refreshStatus() {
        withFlipHub(rankUp -> {
            long profit = rankUp.combinedLifetimeProfit();
            int level = FlipLevel.levelFor(profit);
            int best = rankUp.bestLevel();
            boolean staged = !Arrays.equals(FlipLevel.PROFIT, realLines);
            setStatus("All characters: " + QuantityFormatter.formatNumber(profit) + " gp<br>"
                + FlipLevel.SKILL + " level: " + level + " (" + RankUp.TITLES[RankUp.bandFor(level)] + ")<br>"
                + "Best celebrated: " + (best > 0 ? "level " + best : "none")
                + (FlipLevel.prestigeFor(profit) > 0
                    ? "<br>Prestige: " + FlipLevel.roman(FlipLevel.prestigeFor(profit)) : "")
                + (staged ? "<br>Lines: staged for testing" : ""));
        });
    }

    private void stageNextLine() {
        withFlipHub(rankUp -> {
            restoreLines();
            long profit = rankUp.combinedLifetimeProfit();
            int next = FlipLevel.levelFor(profit) + 1;
            if (next > FlipLevel.MAX_LEVEL) {
                setStatus("You're already level 99, so there's no next line to move.");
                return;
            }
            FlipLevel.PROFIT[next] = profit + 1;
            // A level already celebrated would stay quiet, which is not what this is testing.
            if (rankUp.bestLevel() >= next) {
                configManager.setConfiguration(FliphubConfigGroups.CONFIG_GROUP, RankUp.BEST_KEY, next - 1);
            }
            setStatus("Next profitable sale takes you to level " + next + ".<br>"
                + "Line moved to " + QuantityFormatter.formatNumber(profit + 1) + " gp.");
        });
    }

    private void restoreLines() {
        System.arraycopy(realLines, 0, FlipLevel.PROFIT, 0, realLines.length);
    }

    /** Runs off the Swing thread, because adding up every character can read files. */
    private void withFlipHub(java.util.function.Consumer<RankUp> work) {
        RankUp rankUp = Access.pluginOrNull() != null ? Bridge.get(RankUp.class) : null;
        if (rankUp == null) {
            setStatus("Turn on OSRS FlipHub first.");
            return;
        }
        Access.plugin().executeAsync(() -> work.accept(rankUp));
    }

    /** The same as {@link #withFlipHub}, for the half of this that lives on the skill tab. */
    private void withSkillTab(java.util.function.Consumer<SkillTab> work) {
        SkillTab tab = Access.pluginOrNull() != null ? Bridge.get(SkillTab.class) : null;
        if (tab == null) {
            setStatus("Turn on OSRS FlipHub first.");
            return;
        }
        work.accept(tab);
    }

    private void setStatus(String html) {
        SwingUtilities.invokeLater(() -> status.setText("<html>" + html + "</html>"));
    }

    private static JLabel heading(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(Color.WHITE);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, 4, 0));
        return label;
    }

    private static JLabel note(String text) {
        JLabel label = new JLabel("<html>" + text + "</html>");
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, 6, 0));
        return label;
    }

    private static JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        button.addActionListener(e -> action.run());
        return button;
    }
}
