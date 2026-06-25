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
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.border.EmptyBorder;

final class FlipHubPanelChromeBuilder {
    private final FlipHubUiStyler uiStyler;

    FlipHubPanelChromeBuilder(FlipHubUiStyler uiStyler) {
        this.uiStyler = uiStyler;
    }

    JPanel buildHeader(JButton profileButton, Runnable onProfileMenuRequested) {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG);

        JLabel title = new JLabel("FlipHub OSRS");
        title.setForeground(TEXT);
        title.setFont(uiStyler.fontBold(15f));

        profileButton.setForeground(MUTED);
        profileButton.setFont(uiStyler.font(10.5f));
        profileButton.setBorder(new EmptyBorder(2, 6, 2, 6));
        profileButton.setContentAreaFilled(false);
        profileButton.setFocusPainted(false);
        profileButton.setOpaque(false);
        profileButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        profileButton.addActionListener(e -> onProfileMenuRequested.run());

        JPanel statusWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        statusWrap.setOpaque(false);
        statusWrap.add(profileButton);

        header.add(title, BorderLayout.WEST);
        header.add(statusWrap, BorderLayout.EAST);
        return header;
    }

    JButton buildDiscordButton(Runnable onDiscordRequested) {
        JButton button = new JButton("Discord");
        button.setToolTipText("Join the FlipHub Discord");
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBackground(BG_ALT);
        button.setForeground(MUTED);
        button.setFont(uiStyler.fontSemiBold(10f));
        button.setBorder(uiStyler.roundedBorder(CHIP_ARC, SOFT_BORDER, new Insets(2, 8, 2, 8)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(e -> onDiscordRequested.run());
        return button;
    }

    JPanel buildTabs(JToggleButton flippingTab,
                     JToggleButton statsTab,
                     JButton discordButton,
                     Consumer<Boolean> onSwitchRequested) {
        JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        tabs.setBackground(BG);

        ButtonGroup group = new ButtonGroup();
        group.add(flippingTab);
        group.add(statsTab);

        uiStyler.styleTab(flippingTab, true);
        uiStyler.styleTab(statsTab, false);

        flippingTab.addActionListener(e -> onSwitchRequested.accept(Boolean.TRUE));
        statsTab.addActionListener(e -> onSwitchRequested.accept(Boolean.FALSE));

        flippingTab.setSelected(true);

        tabs.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        tabs.add(flippingTab);
        tabs.add(statsTab);
        tabs.add(discordButton);
        return tabs;
    }
}
