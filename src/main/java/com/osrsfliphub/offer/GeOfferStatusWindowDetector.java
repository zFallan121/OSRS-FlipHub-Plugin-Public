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

import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

final class GeOfferStatusWindowDetector {
    private final Client client;
    private final String[] markers;

    GeOfferStatusWindowDetector(Client client, String[] markers) {
        this.client = client;
        this.markers = markers;
    }

    boolean isOfferStatusWindowOpen() {
        if (client == null) {
            return false;
        }
        Widget[] roots = client.getWidgetRoots();
        if (roots == null || roots.length == 0) {
            return false;
        }
        for (Widget root : roots) {
            if (root == null || root.isHidden()) {
                continue;
            }
            if (widgetTreeContainsAnyText(root)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text
            .replace("<br>", "\n")
            .replace("<br/>", "\n")
            .replace("<br />", "\n");
        return Text.removeTags(normalized).trim();
    }

    private boolean widgetTreeContainsAnyText(Widget widget) {
        if (widget == null || markers == null || markers.length == 0) {
            return false;
        }
        String text = normalize(widget.getText());
        if (text != null && !text.isEmpty()) {
            String lower = text.toLowerCase();
            for (String marker : markers) {
                if (marker != null && !marker.isEmpty() && lower.contains(marker)) {
                    return true;
                }
            }
        }
        return widgetTreeContainsAnyText(widget.getChildren())
            || widgetTreeContainsAnyText(widget.getDynamicChildren())
            || widgetTreeContainsAnyText(widget.getNestedChildren());
    }

    private boolean widgetTreeContainsAnyText(Widget[] children) {
        if (children == null) {
            return false;
        }
        for (Widget child : children) {
            if (widgetTreeContainsAnyText(child)) {
                return true;
            }
        }
        return false;
    }
}
