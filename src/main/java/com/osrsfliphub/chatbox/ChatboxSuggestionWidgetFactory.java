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

import java.util.function.Consumer;
import javax.inject.*;
import net.runelite.api.FontID;
import net.runelite.api.widgets.*;

@Singleton
final class ChatboxSuggestionWidgetFactory {
    private static final int SUGGESTION_TEXT_COLOR = 0x800000;
    private static final int SUGGESTION_HOVER_TEXT_COLOR = 0xFFFFFF;
    private static final int SUGGESTION_TOP_Y = 2;
    private static final int SUGGESTION_RIGHT_X = 8;
    private static final int SUGGESTION_RIGHT_WIDTH_PADDING = 16;
    private static final String PRICE_SUGGESTION_WIDGET_NAME = "FlipHub Current Price";
    private static final String LIMIT_SUGGESTION_WIDGET_NAME = "FlipHub Remaining Limit";
    private static final String AFFORDABLE_LIMIT_SUGGESTION_WIDGET_NAME = "FlipHub Affordable Limit";

    private final int suggestionTextColor;
    private final int suggestionHoverTextColor;
    private final int suggestionTopY;
    private final int suggestionRightX;
    private final int suggestionRightWidthPadding;
    private final String priceSuggestionWidgetName;
    private final String limitSuggestionWidgetName;
    private final String affordableLimitSuggestionWidgetName;

    @Inject
    ChatboxSuggestionWidgetFactory() {
        this.suggestionTextColor = SUGGESTION_TEXT_COLOR;
        this.suggestionHoverTextColor = SUGGESTION_HOVER_TEXT_COLOR;
        this.suggestionTopY = SUGGESTION_TOP_Y;
        this.suggestionRightX = SUGGESTION_RIGHT_X;
        this.suggestionRightWidthPadding = SUGGESTION_RIGHT_WIDTH_PADDING;
        this.priceSuggestionWidgetName = PRICE_SUGGESTION_WIDGET_NAME;
        this.limitSuggestionWidgetName = LIMIT_SUGGESTION_WIDGET_NAME;
        this.affordableLimitSuggestionWidgetName = AFFORDABLE_LIMIT_SUGGESTION_WIDGET_NAME;
    }

    Widget ensurePriceSuggestionWidget(Widget container, Widget currentWidget) {
        return ensureSuggestionWidget(container, currentWidget, priceSuggestionWidgetName,
            WidgetPositionMode.ABSOLUTE_LEFT, 10, 16, WidgetSizeMode.MINUS, WidgetTextAlignment.LEFT,
            false, ChatboxSuggestionApply::applySuggestedPriceToChat);
    }

    Widget ensureLimitSuggestionWidget(Widget container, Widget currentWidget) {
        return ensureSuggestionWidget(container, currentWidget, limitSuggestionWidgetName,
            WidgetPositionMode.ABSOLUTE_LEFT, 10, 16, WidgetSizeMode.ABSOLUTE, WidgetTextAlignment.LEFT,
            true, ChatboxSuggestionApply::applySuggestedLimitToChat);
    }

    Widget ensureAffordableLimitSuggestionWidget(Widget container, Widget currentWidget) {
        return ensureSuggestionWidget(container, currentWidget, affordableLimitSuggestionWidgetName,
            WidgetPositionMode.ABSOLUTE_RIGHT, suggestionRightX, suggestionRightWidthPadding,
            WidgetSizeMode.ABSOLUTE, WidgetTextAlignment.RIGHT,
            true, ChatboxSuggestionApply::applySuggestedAffordableLimitToChat);
    }

    private Widget ensureSuggestionWidget(Widget container, Widget currentWidget, String name,
                                          int positionMode, int originalX, int originalWidth, int widthMode,
                                          int textAlignment, boolean reapplyLayout,
                                          Consumer<ChatboxSuggestionApply> onApply) {
        if (container == null) {
            return currentWidget;
        }
        Widget widget = currentWidget;
        if (!ChatboxSuggestionWidgets.isAttached(container, widget)) {
            widget = ChatboxSuggestionWidgets.findNamedTextWidget(container, name);
        }
        if (!ChatboxSuggestionWidgets.isAttached(container, widget)) {
            widget = container.createChild(-1, WidgetType.TEXT);
            widget.setTextColor(suggestionTextColor);
            widget.setTextShadowed(false);
            widget.setFontId(FontID.VERDANA_11_BOLD);
            widget.setXPositionMode(positionMode);
            widget.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
            widget.setOriginalX(originalX);
            widget.setOriginalWidth(originalWidth);
            widget.setWidthMode(widthMode);
            widget.setOriginalHeight(20);
            widget.setXTextAlignment(textAlignment);
            widget.setYTextAlignment(WidgetTextAlignment.CENTER);
            widget.setName(name);
            widget.setAction(0, "Select");
            widget.setOnOpListener((JavaScriptCallback) ev -> applySuggested(onApply));
            widget.setHasListener(true);
            widget.revalidate();
        }
        configureSuggestionWidgetStyle(widget);
        if (reapplyLayout) {
            widget.setXPositionMode(positionMode);
            widget.setOriginalX(originalX);
            widget.setOriginalWidth(originalWidth);
            widget.setWidthMode(widthMode);
            widget.setXTextAlignment(textAlignment);
            widget.setYTextAlignment(WidgetTextAlignment.CENTER);
        }
        int y = ChatboxSuggestionWidgets.computeSuggestionY(container, suggestionTopY, 20);
        widget.setOriginalY(y);
        widget.setName(name);
        return widget;
    }

    private void applySuggested(Consumer<ChatboxSuggestionApply> action) {
        ChatboxSuggestionApply service = Bridge.get(ChatboxSuggestionApply.class);
        if (service != null) {
            action.accept(service);
        }
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
}
