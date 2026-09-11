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

import java.awt.Cursor;
import javax.swing.JComponent;
import net.runelite.client.util.LinkBrowser;

final class FlipHubExternalLinkCoordinator {
    private final String baseUrl;

    FlipHubExternalLinkCoordinator(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    void attachOpenItemPageHandler(JComponent component, int itemId, String itemName) {
        if (component == null || itemId <= 0) {
            return;
        }
        String safeName = itemName != null ? itemName : "item";
        component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        component.setToolTipText("Open " + safeName + " on FlipHub");
        // The shared handler: it survives a little drift between press and release, and it
        // ignores anything but the left button. This used to open the browser on a right-click
        // as well, which is how a person reaches the menu.
        component.addMouseListener(new FlipHubStatsClickMouseAdapter(() -> openItemPage(itemId)));
    }

    void openItemPage(int itemId) {
        openExternalUrl(baseUrl + "/item/" + itemId);
    }

    void openExternalUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }
        LinkBrowser.browse(url);
    }
}
