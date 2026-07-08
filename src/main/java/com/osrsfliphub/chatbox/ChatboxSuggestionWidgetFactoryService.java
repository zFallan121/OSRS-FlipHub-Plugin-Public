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
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.FontID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetSizeMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;

@Singleton
final class ChatboxSuggestionWidgetFactoryService {
    private final int suggestionTextColor;
    private final int suggestionHoverTextColor;
    private final int suggestionTopY;
    private final int suggestionRightX;
    private final int suggestionRightWidthPadding;
    private final String priceSuggestionWidgetName;
    private final String limitSuggestionWidgetName;
    private final String affordableLimitSuggestionWidgetName;

    @Inject
    ChatboxSuggestionWidgetFactoryService() {
        this.suggestionTextColor = GeLifecyclePluginConstants.SUGGESTION_TEXT_COLOR;
        this.suggestionHoverTextColor = GeLifecyclePluginConstants.SUGGESTION_HOVER_TEXT_COLOR;
        this.suggestionTopY = GeLifecyclePluginConstants.SUGGESTION_TOP_Y;
        this.suggestionRightX = GeLifecyclePluginConstants.SUGGESTION_RIGHT_X;
        this.suggestionRightWidthPadding = GeLifecyclePluginConstants.SUGGESTION_RIGHT_WIDTH_PADDING;
        this.priceSuggestionWidgetName = GeLifecyclePluginConstants.PRICE_SUGGESTION_WIDGET_NAME;
        this.limitSuggestionWidgetName = GeLifecyclePluginConstants.LIMIT_SUGGESTION_WIDGET_NAME;
        this.affordableLimitSuggestionWidgetName = GeLifecyclePluginConstants.AFFORDABLE_LIMIT_SUGGESTION_WIDGET_NAME;
    }

    Widget ensurePriceSuggestionWidget(Widget container, Widget currentWidget) {
        return ensureSuggestionWidget(container, currentWidget, priceSuggestionWidgetName,
            WidgetPositionMode.ABSOLUTE_LEFT, 10, 16, WidgetSizeMode.MINUS, WidgetTextAlignment.LEFT,
            false, ChatboxSuggestionApplyService::applySuggestedPriceToChat);
    }

    Widget ensureLimitSuggestionWidget(Widget container, Widget currentWidget) {
        return ensureSuggestionWidget(container, currentWidget, limitSuggestionWidgetName,
            WidgetPositionMode.ABSOLUTE_LEFT, 10, 16, WidgetSizeMode.ABSOLUTE, WidgetTextAlignment.LEFT,
            true, ChatboxSuggestionApplyService::applySuggestedLimitToChat);
    }

    Widget ensureAffordableLimitSuggestionWidget(Widget container, Widget currentWidget) {
        return ensureSuggestionWidget(container, currentWidget, affordableLimitSuggestionWidgetName,
            WidgetPositionMode.ABSOLUTE_RIGHT, suggestionRightX, suggestionRightWidthPadding,
            WidgetSizeMode.ABSOLUTE, WidgetTextAlignment.RIGHT,
            true, ChatboxSuggestionApplyService::applySuggestedAffordableLimitToChat);
    }

    private Widget ensureSuggestionWidget(Widget container, Widget currentWidget, String name,
                                          int positionMode, int originalX, int originalWidth, int widthMode,
                                          int textAlignment, boolean reapplyLayout,
                                          Consumer<ChatboxSuggestionApplyService> onApply) {
        if (container == null) {
            return currentWidget;
        }
        Widget widget = currentWidget;
        if (!isSuggestionWidgetAttached(container, widget)) {
            widget = ChatboxSuggestionWidgets.findNamedTextWidget(container, name);
        }
        if (!isSuggestionWidgetAttached(container, widget)) {
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

    private void applySuggested(Consumer<ChatboxSuggestionApplyService> action) {
        ChatboxSuggestionApplyService service = PluginInjectorBridge.get(ChatboxSuggestionApplyService.class);
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
