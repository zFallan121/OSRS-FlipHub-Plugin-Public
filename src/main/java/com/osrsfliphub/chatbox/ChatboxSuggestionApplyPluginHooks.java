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
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.VarClientID;

final class ChatboxSuggestionApplyPluginHooks implements ChatboxSuggestionApplyService.Hooks {
    private final Supplier<Client> clientSupplier;
    private final Supplier<OfferTypeResolver> offerTypeResolverSupplier;
    private final Supplier<FlipHubItem> offerPreviewItemSupplier;
    private final Supplier<RemainingLimitSuggestionService> remainingLimitSuggestionServiceSupplier;
    private final Supplier<AffordableLimitSuggestionService> affordableLimitSuggestionServiceSupplier;

    ChatboxSuggestionApplyPluginHooks(
        Supplier<Client> clientSupplier,
        Supplier<OfferTypeResolver> offerTypeResolverSupplier,
        Supplier<FlipHubItem> offerPreviewItemSupplier,
        Supplier<RemainingLimitSuggestionService> remainingLimitSuggestionServiceSupplier,
        Supplier<AffordableLimitSuggestionService> affordableLimitSuggestionServiceSupplier
    ) {
        this.clientSupplier = clientSupplier;
        this.offerTypeResolverSupplier = offerTypeResolverSupplier;
        this.offerPreviewItemSupplier = offerPreviewItemSupplier;
        this.remainingLimitSuggestionServiceSupplier = remainingLimitSuggestionServiceSupplier;
        this.affordableLimitSuggestionServiceSupplier = affordableLimitSuggestionServiceSupplier;
    }

    @Override
    public boolean canApplyChatInput() {
        return resolveClient() != null;
    }

    @Override
    public Boolean resolveOfferType() {
        OfferTypeResolver resolver = offerTypeResolverSupplier != null ? offerTypeResolverSupplier.get() : null;
        return resolver != null ? resolver.resolveOfferType() : null;
    }

    @Override
    public FlipHubItem getOfferPreviewItem() {
        return offerPreviewItemSupplier != null ? offerPreviewItemSupplier.get() : null;
    }

    @Override
    public Integer computeRemainingLimitSuggestion(int itemId) {
        RemainingLimitSuggestionService service = remainingLimitSuggestionServiceSupplier != null
            ? remainingLimitSuggestionServiceSupplier.get()
            : null;
        return service != null ? service.computeSuggestion(itemId) : null;
    }

    @Override
    public Integer computeAffordableLimitSuggestion(Integer remainingLimit) {
        AffordableLimitSuggestionService service = affordableLimitSuggestionServiceSupplier != null
            ? affordableLimitSuggestionServiceSupplier.get()
            : null;
        return service != null ? service.computeAffordableLimit(remainingLimit) : null;
    }

    @Override
    public void setChatInput(String value) {
        Client client = resolveClient();
        if (client == null || value == null) {
            return;
        }
        client.setVarcStrValue(VarClientID.MESLAYERINPUT, value);
    }

    @Override
    public void rebuildChatInput() {
        Client client = resolveClient();
        if (client == null) {
            return;
        }
        client.runScript(ScriptID.CHAT_TEXT_INPUT_REBUILD, "");
    }

    private Client resolveClient() {
        return clientSupplier != null ? clientSupplier.get() : null;
    }
}
