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

import net.runelite.api.FontID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;

final class ChatboxSuggestionWidgetFactoryService {
    interface Hooks {
        void onApplySuggestedPriceToChat();
        void onApplySuggestedLimitToChat();
        void onApplySuggestedAffordableLimitToChat();
    }

    private final int suggestionTextColor;
    private final int suggestionHoverTextColor;
    private final int suggestionTopY;
    private final int suggestionRightX;
    private final int suggestionRightWidthPadding;
    private final String priceSuggestionWidgetName;
    private final String limitSuggestionWidgetName;
    private final String affordableLimitSuggestionWidgetName;
    private final Hooks hooks;

    ChatboxSuggestionWidgetFactoryService(int suggestionTextColor,
                                          int suggestionHoverTextColor,
                                          int suggestionTopY,
                                          int suggestionRightX,
                                          int suggestionRightWidthPadding,
                                          String priceSuggestionWidgetName,
                                          String limitSuggestionWidgetName,
                                          String affordableLimitSuggestionWidgetName,
                                          Hooks hooks) {
        this.suggestionTextColor = suggestionTextColor;
        this.suggestionHoverTextColor = suggestionHoverTextColor;
        this.suggestionTopY = suggestionTopY;
        this.suggestionRightX = suggestionRightX;
        this.suggestionRightWidthPadding = suggestionRightWidthPadding;
        this.priceSuggestionWidgetName = priceSuggestionWidgetName;
        this.limitSuggestionWidgetName = limitSuggestionWidgetName;
        this.affordableLimitSuggestionWidgetName = affordableLimitSuggestionWidgetName;
        this.hooks = hooks;
    }

    Widget ensurePriceSuggestionWidget(Widget container, Widget currentWidget) {
        if (container == null) {
            return currentWidget;
        }
        Widget widget = currentWidget;
        if (!isSuggestionWidgetAttached(container, widget)) {
            widget = ChatboxSuggestionWidgets.findNamedTextWidget(container, priceSuggestionWidgetName);
        }
        if (!isSuggestionWidgetAttached(container, widget)) {
            widget = container.createChild(-1, WidgetType.TEXT);
            widget.setTextColor(suggestionTextColor);
            widget.setTextShadowed(false);
            widget.setFontId(FontID.VERDANA_11_BOLD);
            widget.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
            widget.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
            widget.setOriginalX(10);
            widget.setOriginalWidth(16);
            widget.setWidthMode(WidgetSizeMode.MINUS);
            widget.setOriginalHeight(20);
            widget.setXTextAlignment(WidgetTextAlignment.LEFT);
            widget.setYTextAlignment(WidgetTextAlignment.CENTER);
            widget.setName(priceSuggestionWidgetName);
            widget.setAction(0, "Select");
            widget.setOnOpListener((JavaScriptCallback) ev -> {
                if (hooks != null) {
                    hooks.onApplySuggestedPriceToChat();
                }
            });
            widget.setHasListener(true);
            widget.revalidate();
        }
        configureSuggestionWidgetStyle(widget);
        int y = ChatboxSuggestionWidgets.computeSuggestionY(container, suggestionTopY, 20);
        widget.setOriginalY(y);
        widget.setName(priceSuggestionWidgetName);
        return widget;
    }

    Widget ensureLimitSuggestionWidget(Widget container, Widget currentWidget) {
        if (container == null) {
            return currentWidget;
        }
        Widget widget = currentWidget;
        if (!isSuggestionWidgetAttached(container, widget)) {
            widget = ChatboxSuggestionWidgets.findNamedTextWidget(container, limitSuggestionWidgetName);
        }
        if (!isSuggestionWidgetAttached(container, widget)) {
            widget = container.createChild(-1, WidgetType.TEXT);
            widget.setTextColor(suggestionTextColor);
            widget.setTextShadowed(false);
            widget.setFontId(FontID.VERDANA_11_BOLD);
            widget.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
            widget.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
            widget.setOriginalX(10);
            widget.setOriginalWidth(16);
            widget.setWidthMode(WidgetSizeMode.ABSOLUTE);
            widget.setOriginalHeight(20);
            widget.setXTextAlignment(WidgetTextAlignment.LEFT);
            widget.setYTextAlignment(WidgetTextAlignment.CENTER);
            widget.setName(limitSuggestionWidgetName);
            widget.setAction(0, "Select");
            widget.setOnOpListener((JavaScriptCallback) ev -> {
                if (hooks != null) {
                    hooks.onApplySuggestedLimitToChat();
                }
            });
            widget.setHasListener(true);
            widget.revalidate();
        }
        configureSuggestionWidgetStyle(widget);
        widget.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
        widget.setOriginalX(10);
        widget.setOriginalWidth(16);
        widget.setWidthMode(WidgetSizeMode.ABSOLUTE);
        widget.setXTextAlignment(WidgetTextAlignment.LEFT);
        widget.setYTextAlignment(WidgetTextAlignment.CENTER);
        int y = ChatboxSuggestionWidgets.computeSuggestionY(container, suggestionTopY, 20);
        widget.setOriginalY(y);
        widget.setName(limitSuggestionWidgetName);
        return widget;
    }

    Widget ensureAffordableLimitSuggestionWidget(Widget container, Widget currentWidget) {
        if (container == null) {
            return currentWidget;
        }
        Widget widget = currentWidget;
        if (!isSuggestionWidgetAttached(container, widget)) {
            widget = ChatboxSuggestionWidgets.findNamedTextWidget(container, affordableLimitSuggestionWidgetName);
        }
        if (!isSuggestionWidgetAttached(container, widget)) {
            widget = container.createChild(-1, WidgetType.TEXT);
            widget.setTextColor(suggestionTextColor);
            widget.setTextShadowed(false);
            widget.setFontId(FontID.VERDANA_11_BOLD);
            widget.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
            widget.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
            widget.setOriginalX(suggestionRightX);
            widget.setOriginalWidth(suggestionRightWidthPadding);
            widget.setWidthMode(WidgetSizeMode.ABSOLUTE);
            widget.setOriginalHeight(20);
            widget.setXTextAlignment(WidgetTextAlignment.RIGHT);
            widget.setYTextAlignment(WidgetTextAlignment.CENTER);
            widget.setName(affordableLimitSuggestionWidgetName);
            widget.setAction(0, "Select");
            widget.setOnOpListener((JavaScriptCallback) ev -> {
                if (hooks != null) {
                    hooks.onApplySuggestedAffordableLimitToChat();
                }
            });
            widget.setHasListener(true);
            widget.revalidate();
        }
        configureSuggestionWidgetStyle(widget);
        widget.setXPositionMode(WidgetPositionMode.ABSOLUTE_RIGHT);
        widget.setOriginalX(suggestionRightX);
        widget.setOriginalWidth(suggestionRightWidthPadding);
        widget.setWidthMode(WidgetSizeMode.ABSOLUTE);
        widget.setXTextAlignment(WidgetTextAlignment.RIGHT);
        widget.setYTextAlignment(WidgetTextAlignment.CENTER);
        int y = ChatboxSuggestionWidgets.computeSuggestionY(container, suggestionTopY, 20);
        widget.setOriginalY(y);
        widget.setName(affordableLimitSuggestionWidgetName);
        return widget;
    }

    private void configureSuggestionWidgetStyle(Widget widget) {
        if (widget == null) {
            return;
        }
        widget.setTextShadowed(false);
        widget.setFontId(FontID.VERDANA_11_BOLD);
        widget.setOnMouseRepeatListener((JavaScriptCallback) ev -> widget.setTextColor(suggestionHoverTextColor));
        widget.setOnMouseLeaveListener((JavaScriptCallback) ev -> widget.setTextColor(suggestionTextColor));
        widget.setHasListener(true);
    }

    private boolean isSuggestionWidgetAttached(Widget container, Widget suggestionWidget) {
        if (suggestionWidget == null || container == null) {
            return false;
        }
        if (suggestionWidget.getParent() != container) {
            return false;
        }
        if (suggestionWidget.getParentId() != container.getId()) {
            return false;
        }
        return ChatboxSuggestionWidgets.isWidgetInParent(container, suggestionWidget);
    }
}
