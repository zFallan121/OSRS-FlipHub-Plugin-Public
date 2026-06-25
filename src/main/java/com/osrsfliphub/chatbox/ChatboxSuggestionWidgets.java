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

import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

final class ChatboxSuggestionWidgets {
    private ChatboxSuggestionWidgets() {
    }

    static boolean isPromptWidgetValid(Widget widget, boolean pricePrompt) {
        if (widget == null || widget.isHidden()) {
            return false;
        }
        return pricePrompt ? isPricePromptWidget(widget) : isQuantityPromptWidget(widget);
    }

    static boolean isWidgetVisible(Widget widget) {
        return widget != null && !widget.isHidden();
    }

    static Widget findPromptWidget(Widget root, boolean pricePrompt) {
        if (root == null) {
            return null;
        }
        if (pricePrompt ? isPricePromptWidget(root) : isQuantityPromptWidget(root)) {
            return root;
        }
        Widget match = findPromptWidgetInChildren(root.getChildren(), pricePrompt);
        if (match != null) {
            return match;
        }
        match = findPromptWidgetInChildren(root.getDynamicChildren(), pricePrompt);
        if (match != null) {
            return match;
        }
        return findPromptWidgetInChildren(root.getNestedChildren(), pricePrompt);
    }

    private static Widget findPromptWidgetInChildren(Widget[] children, boolean pricePrompt) {
        if (children == null) {
            return null;
        }
        for (Widget child : children) {
            Widget match = findPromptWidget(child, pricePrompt);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    static boolean isPricePromptWidget(Widget widget) {
        if (widget == null) {
            return false;
        }
        String text = OfferPreviewWidgetParser.normalizeText(widget.getText());
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase();
        return lower.contains("set a price for each item")
            || lower.contains("set a price")
            || lower.contains("how much do you wish to")
            || lower.contains("enter price")
            || lower.contains("price per item");
    }

    static boolean isQuantityPromptWidget(Widget widget) {
        if (widget == null) {
            return false;
        }
        String text = OfferPreviewWidgetParser.normalizeText(widget.getText());
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase();
        return lower.contains("how many do you wish to")
            || lower.contains("enter quantity")
            || lower.contains("set the quantity");
    }

    static int computeSuggestionY(Widget container, int suggestionTopY, int suggestionHeight) {
        return Math.max(2, suggestionTopY);
    }

    static Widget findNamedTextWidget(Widget container, String widgetName) {
        if (container == null || widgetName == null || widgetName.isEmpty()) {
            return null;
        }
        Widget match = findNamedTextWidgetInChildren(container.getChildren(), widgetName);
        if (match != null) {
            return match;
        }
        match = findNamedTextWidgetInChildren(container.getDynamicChildren(), widgetName);
        if (match != null) {
            return match;
        }
        return findNamedTextWidgetInChildren(container.getNestedChildren(), widgetName);
    }

    private static Widget findNamedTextWidgetInChildren(Widget[] children, String widgetName) {
        if (children == null) {
            return null;
        }
        for (Widget child : children) {
            if (child == null) {
                continue;
            }
            if (child.getType() == WidgetType.TEXT && widgetName.equals(child.getName())) {
                return child;
            }
            Widget match = findNamedTextWidget(child, widgetName);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    static boolean isWidgetInParent(Widget parent, Widget widget) {
        if (parent == null || widget == null) {
            return false;
        }
        if (containsWidget(parent.getChildren(), widget)) {
            return true;
        }
        if (containsWidget(parent.getDynamicChildren(), widget)) {
            return true;
        }
        return containsWidget(parent.getNestedChildren(), widget);
    }

    private static boolean containsWidget(Widget[] children, Widget target) {
        if (children == null) {
            return false;
        }
        for (Widget child : children) {
            if (child == target) {
                return true;
            }
        }
        return false;
    }
}
