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

import static com.osrsfliphub.FlipHubPanelConstants.*;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import net.runelite.client.util.ImageUtil;

final class FlipHubPanelLayoutActions {
    BufferedImage buildNavIcon(FlipHubUiStyler uiStyler) {
        BufferedImage icon = null;
        try {
            // getResourceAsStream via ImageUtil: on the hub the plugin runs from inside a jar,
            // where a resource URL does not behave like the file URL seen in the IDE.
            icon = net.runelite.client.util.ImageUtil.loadImageResource(
                getClass(), "/com/osrsfliphub/fliphub-icon.png");
        } catch (Exception ignored) {
            // no-op; fallback icon below
        }
        if (icon == null) {
            BufferedImage fallback = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = fallback.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(ACCENT);
            g.fillRoundRect(0, 0, 16, 16, 4, 4);
            g.setColor(Color.WHITE);
            g.setFont(uiStyler.fontBold(10f));
            g.drawString("F", 4, 12);
            g.dispose();
            return fallback;
        }
        return ImageUtil.resizeImage(icon, 16, 16);
    }

    FlipHubPanelLayoutResult buildAndAttachLayout(
        JPanel hostPanel,
        CardLayout cardLayout,
        JPanel cardPanel,
        JToggleButton flippingTab,
        JToggleButton statsTab,
        JToggleButton linkTab,
        JButton profileButton,
        FlipHubPanelChromeBuilder chromeBuilder,
        FlipHubPanelBodyBuilder bodyBuilder,
        FlipHubFlippingPanelBuilder flippingPanelBuilder,
        FlipHubStatsPanelBuilder statsPanelBuilder,
        JTextField searchField,
        JToggleButton bookmarkFilterButton,
        JLabel refreshLabel,
        JPanel listPanel,
        JScrollPane scrollPane,
        JButton prevButton,
        JButton nextButton,
        JLabel pageLabel,
        JComboBox<StatsRange> statsRangeCombo,
        JTextField statsSearchField,
        JButton statsClearButton,
        JLabel statsUpdatedLabel,
        JPanel statsContentPanel,
        JPanel statsItemsListPanel,
        JComboBox<StatsItemSort> statsSortCombo,
        JButton statsSortDirectionButton,
        FlipHubPanelStateService panelStateService,
        FlipHubAgeTooltipCoordinator ageTooltipCoordinator,
        FlipHubUiStyler uiStyler,
        FlipHubPanelListener listener,
        Runnable showProfileMenuAction,
        Runnable openDiscordAction
    ) {
        // The backdrop is sacred: one panel paints the navy and the two washes, and every surface
        // above it is transparent or translucent so the glow is never covered. The padding moves
        // onto the backdrop rather than the host so the wash reaches the panel's own edges.
        hostPanel.setLayout(new BorderLayout());
        hostPanel.setBackground(BG);
        hostPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JPanel backdrop = new BackdropPanel(BG, GRAD_GREEN, GRAD_BLUE);
        backdrop.setLayout(new BorderLayout());
        backdrop.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        hostPanel.add(backdrop, BorderLayout.CENTER);

        JPanel header = chromeBuilder.buildHeader(profileButton, showProfileMenuAction);
        JButton discordButton = chromeBuilder.buildDiscordButton(openDiscordAction);
        JPanel tabs = chromeBuilder.buildTabs(
            flippingTab,
            statsTab,
            linkTab,
            discordButton,
            card -> panelStateService.switchTab(
                card,
                ageTooltipCoordinator,
                uiStyler,
                flippingTab,
                statsTab,
                linkTab,
                cardLayout,
                cardPanel,
                listener,
                statsRangeCombo
            )
        );
        FlipHubPanelBodyBuilder.BuildResult body = bodyBuilder.build(
            cardPanel,
            flippingPanelBuilder,
            statsPanelBuilder,
            searchField,
            bookmarkFilterButton,
            refreshLabel,
            profileButton,
            listPanel,
            scrollPane,
            prevButton,
            nextButton,
            pageLabel,
            statsRangeCombo,
            statsSearchField,
            statsClearButton,
            statsUpdatedLabel,
            statsContentPanel,
            statsItemsListPanel,
            statsSortCombo,
            statsSortDirectionButton
        );

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);
        top.add(header);
        top.add(Box.createVerticalStrut(8));
        top.add(tabs);
        // The tab row's own 2px rule is the separator between the chrome and the body. A second
        // divider here would be a box the chrome has not earned.
        top.add(Box.createVerticalStrut(8));

        backdrop.add(top, BorderLayout.NORTH);
        backdrop.add(body.panel, BorderLayout.CENTER);

        return new FlipHubPanelLayoutResult(
            body.footerPanel,
            body.statsScrollPane,
            body.totalProfitValue,
            body.roiValue,
            body.flipsValue,
            body.taxValue,
            body.sessionTimeValue,
            body.hourlyValue
        );
    }

    JPanel buildCard(String title, String body, FlipHubUiStyler uiStyler) {
        JPanel card = RoundedPanel.glass(CARD_ARC);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(TEXT);
        titleLabel.setFont(uiStyler.fontSemiBold(12f));

        JLabel bodyLabel = new JLabel(body);
        bodyLabel.setForeground(MUTED);
        bodyLabel.setFont(uiStyler.font(10.5f));

        card.add(titleLabel);
        card.add(Box.createVerticalStrut(4));
        card.add(bodyLabel);
        return card;
    }
}
