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
 * Development client only: buttons for trying the rank-up without earning one.
 *
 * <p>Lives under src/test, so the plugin hub never builds it and it costs nothing against the
 * hub's token count. {@code GeLifecyclePluginTest.main} loads it next to FlipHub.
 */
@PluginDescriptor(
    name = "FlipHub Rank-Up Tester",
    description = "Development client only: play a FlipHub rank-up, or stage a real one",
    developerPlugin = true
)
public class RankUpTesterPlugin extends Plugin {
    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ConfigManager configManager;

    private final long[] realLines = RankUp.LINES.clone();
    private NavigationButton navButton;
    private JLabel status;

    @Override
    protected void startUp() {
        BufferedImage icon = ImageUtil.loadImageResource(RankUp.class, "/com/osrsfliphub/rank/10.png");
        navButton = NavigationButton.builder()
            .tooltip("FlipHub rank-up tester")
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

        panel.add(heading("Rank-up tester"));
        panel.add(note("Development client only. This panel never ships."));
        status = note("Press Refresh to read your profit.");
        panel.add(Box.createVerticalStrut(8));
        panel.add(status);
        panel.add(button("Refresh", this::refreshStatus));

        panel.add(Box.createVerticalStrut(16));
        panel.add(heading("Play the celebration"));
        panel.add(note("Runs exactly what a real rank-up runs: the chat line now, the message and "
            + "fireworks once the chatbox is free. Your best rank is left alone."));
        JComboBox<String> rankPicker = new JComboBox<>(Arrays.copyOfRange(RankUp.TITLES, 1, RankUp.TITLES.length));
        rankPicker.setSelectedIndex(4);
        rankPicker.setAlignmentX(Component.LEFT_ALIGNMENT);
        rankPicker.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        panel.add(rankPicker);
        panel.add(button("Play rank-up", () -> withFlipHub(rankUp ->
            rankUp.celebrate(rankPicker.getSelectedIndex() + 1))));

        panel.add(Box.createVerticalStrut(16));
        panel.add(heading("Test a real sale"));
        panel.add(note("Moves the next rank's line to 1 gp above your profit across all characters. "
            + "Then buy any item and sell it for more than you paid: that sale sets the rank-up off "
            + "the real way. Needs Celebrate rank-ups on."));
        panel.add(button("Put the next line just above my profit", this::stageNextLine));
        panel.add(button("Restore the real lines", () -> {
            restoreLines();
            setStatus("The real rank lines are back.");
        }));

        panel.add(Box.createVerticalStrut(16));
        panel.add(heading("Reset"));
        panel.add(note("Forgets the best rank celebrated, so real sales can celebrate ranks you've already had."));
        panel.add(button("Forget best rank", () -> {
            configManager.unsetConfiguration(FliphubConfigGroups.CONFIG_GROUP, RankUp.BEST_KEY);
            refreshStatus();
        }));
        return panel;
    }

    private void refreshStatus() {
        withFlipHub(rankUp -> {
            long profit = rankUp.combinedLifetimeProfit();
            int rank = RankUp.rankFor(profit);
            int best = rankUp.bestRank();
            boolean staged = !Arrays.equals(RankUp.LINES, realLines);
            setStatus("All characters: " + QuantityFormatter.formatNumber(profit) + " gp<br>"
                + "Rank: " + RankUp.TITLES[rank] + "<br>"
                + "Best celebrated: " + RankUp.TITLES[best]
                + (staged ? "<br>Lines: staged for testing" : ""));
        });
    }

    private void stageNextLine() {
        withFlipHub(rankUp -> {
            restoreLines();
            long profit = rankUp.combinedLifetimeProfit();
            int next = RankUp.rankFor(profit) + 1;
            if (next >= RankUp.LINES.length) {
                setStatus("You're already Gielinor Elite, so there's no next line to move.");
                return;
            }
            RankUp.LINES[next] = profit + 1;
            // A rank already celebrated would stay quiet, which is not what this is testing.
            if (rankUp.bestRank() >= next) {
                configManager.setConfiguration(FliphubConfigGroups.CONFIG_GROUP, RankUp.BEST_KEY, next - 1);
            }
            setStatus("Next profitable sale makes you " + RankUp.named(next) + ".<br>"
                + "Line moved to " + QuantityFormatter.formatNumber(profit + 1) + " gp.");
        });
    }

    private void restoreLines() {
        System.arraycopy(realLines, 0, RankUp.LINES, 0, realLines.length);
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
