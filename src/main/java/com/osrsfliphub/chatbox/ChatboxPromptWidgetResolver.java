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

import javax.inject.*;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.widgets.*;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class ChatboxPromptWidgetResolver {
    private final int fullInputComponentId = ComponentID.CHATBOX_FULL_INPUT;
    private final int titleComponentId = ComponentID.CHATBOX_TITLE;
    private final int firstMessageComponentId = ComponentID.CHATBOX_FIRST_MESSAGE;
    private final int messageLinesComponentId = ComponentID.CHATBOX_MESSAGE_LINES;
    private final int containerComponentId = ComponentID.CHATBOX_CONTAINER;
    private final Client client;

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
        found = ChatboxSuggestionWidgets.findPromptWidget(client.getWidget(messageLinesComponentId), pricePrompt);
        if (found != null) {
            return found;
        }
        return ChatboxSuggestionWidgets.findPromptWidget(client.getWidget(containerComponentId), pricePrompt);
    }

    private Widget findDirectPromptWidget(int componentId, boolean pricePrompt) {
        Widget widget = client.getWidget(componentId);
        return isPromptWidget(widget, pricePrompt) ? widget : null;
    }

    private boolean isPromptWidget(Widget widget, boolean pricePrompt) {
        return pricePrompt
            ? ChatboxSuggestionWidgets.isPricePromptWidget(widget)
            : ChatboxSuggestionWidgets.isQuantityPromptWidget(widget);
    }
}
