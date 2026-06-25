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

import java.util.function.Supplier;
import net.runelite.api.Client;

final class ChatboxSuggestionRuntimeStatePluginHooks implements ChatboxSuggestionRuntimeStateService.Hooks {
    private final Supplier<Client> clientSupplier;
    private final Supplier<ChatboxSuggestionWidgetFactoryService> chatboxSuggestionWidgetFactoryServiceSupplier;
    private final Supplier<ChatboxPromptWidgetResolverService> chatboxPromptWidgetResolverServiceSupplier;

    ChatboxSuggestionRuntimeStatePluginHooks(
        Supplier<Client> clientSupplier,
        Supplier<ChatboxSuggestionWidgetFactoryService> chatboxSuggestionWidgetFactoryServiceSupplier,
        Supplier<ChatboxPromptWidgetResolverService> chatboxPromptWidgetResolverServiceSupplier
    ) {
        this.clientSupplier = clientSupplier;
        this.chatboxSuggestionWidgetFactoryServiceSupplier = chatboxSuggestionWidgetFactoryServiceSupplier;
        this.chatboxPromptWidgetResolverServiceSupplier = chatboxPromptWidgetResolverServiceSupplier;
    }

    @Override
    public Client getClient() {
        return clientSupplier != null ? clientSupplier.get() : null;
    }

    @Override
    public ChatboxSuggestionWidgetFactoryService getWidgetFactoryService() {
        return chatboxSuggestionWidgetFactoryServiceSupplier != null
            ? chatboxSuggestionWidgetFactoryServiceSupplier.get()
            : null;
    }

    @Override
    public ChatboxPromptWidgetResolverService getPromptWidgetResolverService() {
        return chatboxPromptWidgetResolverServiceSupplier != null
            ? chatboxPromptWidgetResolverServiceSupplier.get()
            : null;
    }
}
