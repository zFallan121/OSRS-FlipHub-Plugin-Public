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

final class ChatboxPromptWidgetResolverService {
    interface Hooks {
        Widget getWidget(int componentId);
    }

    private final int fullInputComponentId;
    private final int titleComponentId;
    private final int firstMessageComponentId;
    private final int messageLinesComponentId;
    private final int containerComponentId;
    private final Hooks hooks;

    ChatboxPromptWidgetResolverService(int fullInputComponentId,
                                       int titleComponentId,
                                       int firstMessageComponentId,
                                       int messageLinesComponentId,
                                       int containerComponentId,
                                       Hooks hooks) {
        this.fullInputComponentId = fullInputComponentId;
        this.titleComponentId = titleComponentId;
        this.firstMessageComponentId = firstMessageComponentId;
        this.messageLinesComponentId = messageLinesComponentId;
        this.containerComponentId = containerComponentId;
        this.hooks = hooks;
    }

    Widget resolvePromptWidget(Widget cachedPromptWidget, boolean pricePrompt) {
        if (ChatboxSuggestionWidgets.isPromptWidgetValid(cachedPromptWidget, pricePrompt)) {
            return cachedPromptWidget;
        }
        Widget found = findDirectPromptWidget(fullInputComponentId, pricePrompt);
        if (found != null) {
            return found;
        }
        found = findDirectPromptWidget(titleComponentId, pricePrompt);
        if (found != null) {
            return found;
        }
        found = findDirectPromptWidget(firstMessageComponentId, pricePrompt);
        if (found != null) {
            return found;
        }
        Widget messageLines = hooks != null ? hooks.getWidget(messageLinesComponentId) : null;
        found = ChatboxSuggestionWidgets.findPromptWidget(messageLines, pricePrompt);
        if (found != null) {
            return found;
        }
        Widget container = hooks != null ? hooks.getWidget(containerComponentId) : null;
        return ChatboxSuggestionWidgets.findPromptWidget(container, pricePrompt);
    }

    private Widget findDirectPromptWidget(int componentId, boolean pricePrompt) {
        Widget widget = hooks != null ? hooks.getWidget(componentId) : null;
        return isPromptWidget(widget, pricePrompt) ? widget : null;
    }

    private boolean isPromptWidget(Widget widget, boolean pricePrompt) {
        return pricePrompt
            ? ChatboxSuggestionWidgets.isPricePromptWidget(widget)
            : ChatboxSuggestionWidgets.isQuantityPromptWidget(widget);
    }
}
