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

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

final class OfferPreviewWidgetParser {
    interface ItemNameResolver {
        int resolveItemIdFromName(String name);
    }

    private OfferPreviewWidgetParser() {
    }

    static String normalizeText(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text
            .replace("<br>", "\n")
            .replace("<br/>", "\n")
            .replace("<br />", "\n");
        return Text.removeTags(normalized);
    }

    static boolean containsAny(String lower, String[] needles) {
        if (lower == null || needles == null) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && !needle.isEmpty() && lower.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    static int findFirstItemId(Widget widget) {
        if (widget == null) {
            return -1;
        }
        int itemId = widget.getItemId();
        if (itemId > 0) {
            return itemId;
        }
        int found = findFirstItemId(widget.getChildren());
        if (found > 0) {
            return found;
        }
        found = findFirstItemId(widget.getDynamicChildren());
        if (found > 0) {
            return found;
        }
        return findFirstItemId(widget.getNestedChildren());
    }

    static boolean widgetTreeContainsAnyText(Widget widget, String[] needlesLower) {
        if (widget == null || needlesLower == null || needlesLower.length == 0) {
            return false;
        }
        String text = normalizeText(widget.getText());
        if (text != null) {
            String lower = text.toLowerCase();
            for (String needle : needlesLower) {
                if (needle != null && !needle.isEmpty() && lower.contains(needle)) {
                    return true;
                }
            }
        }
        if (widgetTreeContainsAnyText(widget.getChildren(), needlesLower)) {
            return true;
        }
        if (widgetTreeContainsAnyText(widget.getDynamicChildren(), needlesLower)) {
            return true;
        }
        return widgetTreeContainsAnyText(widget.getNestedChildren(), needlesLower);
    }

    static String findItemNameCandidate(Widget root, String[] itemNameExcludes, ItemNameResolver resolver) {
        if (root == null || resolver == null) {
            return null;
        }
        List<String> texts = collectWidgetText(root);
        for (String text : texts) {
            String[] lines = text.split("\\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (!isItemNameCandidate(trimmed, itemNameExcludes)) {
                    continue;
                }
                if (resolver.resolveItemIdFromName(trimmed) > 0) {
                    return trimmed;
                }
            }
        }
        return null;
    }

    static List<String> collectWidgetText(Widget root) {
        List<String> texts = new ArrayList<>();
        collectWidgetText(root, texts);
        return texts;
    }

    private static int findFirstItemId(Widget[] children) {
        if (children == null) {
            return -1;
        }
        for (Widget child : children) {
            int found = findFirstItemId(child);
            if (found > 0) {
                return found;
            }
        }
        return -1;
    }

    private static boolean widgetTreeContainsAnyText(Widget[] children, String[] needlesLower) {
        if (children == null) {
            return false;
        }
        for (Widget child : children) {
            if (widgetTreeContainsAnyText(child, needlesLower)) {
                return true;
            }
        }
        return false;
    }

    private static void collectWidgetText(Widget widget, List<String> out) {
        if (widget == null || out == null) {
            return;
        }
        String text = normalizeText(widget.getText());
        if (text != null && !text.trim().isEmpty()) {
            out.add(text);
        }
        collectWidgetText(widget.getChildren(), out);
        collectWidgetText(widget.getDynamicChildren(), out);
        collectWidgetText(widget.getNestedChildren(), out);
    }

    private static void collectWidgetText(Widget[] children, List<String> out) {
        if (children == null) {
            return;
        }
        for (Widget child : children) {
            collectWidgetText(child, out);
        }
    }

    private static boolean isItemNameCandidate(String text, String[] itemNameExcludes) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        if (trimmed.length() < 3 || trimmed.length() > 60) {
            return false;
        }
        String lower = trimmed.toLowerCase();
        if (containsAny(lower, itemNameExcludes)) {
            return false;
        }
        boolean hasLetter = false;
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isLetter(trimmed.charAt(i))) {
                hasLetter = true;
                break;
            }
        }
        return hasLetter;
    }
}
