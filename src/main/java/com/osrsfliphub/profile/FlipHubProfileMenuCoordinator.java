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

import static com.osrsfliphub.FlipHubPanelConstants.ACCENT;
import static com.osrsfliphub.FlipHubPanelConstants.BG_ALT;
import static com.osrsfliphub.FlipHubPanelConstants.MUTED;
import static com.osrsfliphub.FlipHubPanelConstants.SUCCESS;
import static com.osrsfliphub.FlipHubPanelConstants.TEXT;

import java.awt.Font;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JButton;

final class FlipHubProfileMenuCoordinator {
    private final JButton profileButton;
    private final FlipHubUiStyler uiStyler;
    private final FlipHubPanelListener listener;
    private JPopupMenu profileMenu;
    private String selectedProfileKey;

    FlipHubProfileMenuCoordinator(JButton profileButton, FlipHubUiStyler uiStyler, FlipHubPanelListener listener) {
        this.profileButton = profileButton;
        this.uiStyler = uiStyler;
        this.listener = listener;
    }

    void showProfileMenu() {
        if (profileButton == null || profileMenu == null || profileMenu.getComponentCount() == 0) {
            return;
        }
        profileMenu.show(profileButton, 0, profileButton.getHeight() + 2);
    }

    void setStatusMessage(String message) {
        if (profileButton != null) {
            profileButton.setText(message);
        }
    }

    void setProfileHeader(String label, boolean linked) {
        if (profileButton == null) {
            return;
        }
        profileButton.setText(label != null ? label : "");
        profileButton.setForeground(linked ? SUCCESS : MUTED);
    }

    void setUploadDiagnosticsTooltip(String tooltip) {
        if (profileButton != null) {
            profileButton.setToolTipText(tooltip);
        }
    }

    void setProfileOptions(List<FlipHubProfileOption> options, String selectedKey) {
        selectedProfileKey = selectedKey;
        rebuildProfileMenu(options);
    }

    private void rebuildProfileMenu(List<FlipHubProfileOption> options) {
        if (options == null || options.isEmpty()) {
            profileMenu = null;
            return;
        }
        profileMenu = new JPopupMenu();
        for (FlipHubProfileOption option : options) {
            String label = option != null ? option.label : null;
            String key = option != null ? option.key : null;
            if (label == null || key == null) {
                continue;
            }
            JMenuItem item = new JMenuItem(label);
            item.setFont(font(11f));
            item.setForeground(TEXT);
            item.setBackground(BG_ALT);
            item.setOpaque(true);
            if (selectedProfileKey != null && selectedProfileKey.equals(key)) {
                item.setFont(fontSemiBold(11f));
                item.setForeground(ACCENT);
            }
            item.addActionListener(e -> {
                selectedProfileKey = key;
                if (listener != null) {
                    listener.onProfileSelected(key);
                }
            });
            profileMenu.add(item);
        }

        JMenuItem manageData = new JMenuItem("Manage data...");
        manageData.setFont(font(11f));
        manageData.setForeground(TEXT);
        manageData.setBackground(BG_ALT);
        manageData.setOpaque(true);
        manageData.addActionListener(e -> {
            if (listener != null) {
                listener.onManageData();
            }
        });
        profileMenu.add(manageData);
    }

    private Font font(float size) {
        return uiStyler.font(size);
    }

    private Font fontSemiBold(float size) {
        return uiStyler.fontSemiBold(size);
    }
}

