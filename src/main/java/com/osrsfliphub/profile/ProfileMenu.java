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

import java.util.List;
import javax.swing.*;
import lombok.RequiredArgsConstructor;
import static com.osrsfliphub.Skin.*;

@RequiredArgsConstructor
final class ProfileMenu {
    private final JButton profileButton;
    private final UiStyler uiStyler;
    private final PanelListener listener;
    private JPopupMenu profileMenu;
    private String selectedProfileKey;

    void showProfileMenu() {
        if (profileMenu == null || profileMenu.getComponentCount() == 0) {
            return;
        }
        profileMenu.show(profileButton, 0, profileButton.getHeight() + 2);
    }

    void setStatusMessage(String message) {
        profileButton.setText(message);
    }

    void setProfileHeader(String label, boolean linked) {
        profileButton.setText(label != null ? label : "");
        profileButton.setForeground(linked ? SUCCESS : MUTED);
    }

    void setUploadDiagnosticsTooltip(String tooltip) {
        profileButton.setToolTipText(tooltip);
    }

    void setProfileOptions(List<ProfileOption> options, String selectedKey) {
        selectedProfileKey = selectedKey;
        rebuildProfileMenu(options);
    }

    private void rebuildProfileMenu(List<ProfileOption> options) {
        if (options == null || options.isEmpty()) {
            profileMenu = null;
            return;
        }
        // An overlay is a surface OVER the room, not a fifth surface: opaque ground, one
        // hairline, and the same type as the panel it opens out of.
        profileMenu = new JPopupMenu();
        profileMenu.setBackground(OVERLAY_BASE);
        profileMenu.setBorder(BorderFactory.createLineBorder(LINE));
        for (ProfileOption option : options) {
            String label = option != null ? option.label : null;
            String key = option != null ? option.key : null;
            if (label == null || key == null) {
                continue;
            }
            JMenuItem item = new JMenuItem(label);
            item.setFont(uiStyler.font(11f));
            item.setForeground(TEXT);
            item.setBackground(OVERLAY_BASE);
            item.setOpaque(true);
            if (selectedProfileKey != null && selectedProfileKey.equals(key)) {
                item.setFont(uiStyler.fontSemiBold(11f));
                item.setForeground(ACCENT);
            }
            item.addActionListener(e -> {
                selectedProfileKey = key;
                listener.onProfileSelected(key);
            });
            profileMenu.add(item);
        }

        JMenuItem manageData = new JMenuItem("Manage data...");
        manageData.setFont(uiStyler.font(11f));
        manageData.setForeground(TEXT);
        manageData.setBackground(OVERLAY_BASE);
        manageData.setOpaque(true);
        manageData.addActionListener(e -> {
            listener.onManageData();
        });
        profileMenu.add(manageData);
    }

}

