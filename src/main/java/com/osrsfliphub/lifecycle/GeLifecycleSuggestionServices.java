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

import java.util.function.LongConsumer;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.widgets.ComponentID;

final class GeLifecycleSuggestionServices {
    private final Supplier<Client> clientSupplier;
    private final Supplier<OfferPreviewRuntimeFacadeService> offerPreviewRuntimeFacadeServiceSupplier;
    private final Supplier<Integer> offerPreviewItemIdSupplier;
    private final Supplier<FlipHubItem> offerPreviewItemSupplier;
    private final Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier;
    private final Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier;
    private final LongConsumer ensureProfileLoaded;
    private final Supplier<GeLimitService> geLimitServiceSupplier;
    private final Supplier<ItemLookupService> itemLookupServiceSupplier;
    private final Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier;
    private final int suggestionTextColor;
    private final int suggestionHoverTextColor;
    private final int suggestionTopY;
    private final int suggestionRightX;
    private final int suggestionRightWidthPadding;
    private final String priceSuggestionWidgetName;
    private final String limitSuggestionWidgetName;
    private final String affordableLimitSuggestionWidgetName;
    private final int geOfferPriceVarbit;
    private final int coinsItemId;
    private final int selectedSlotVarbit;

    private OfferTypeResolver offerTypeResolver;
    private ChatboxSuggestionCycleService chatboxSuggestionCycleService;
    private ChatboxSuggestionRuntimeStateService chatboxSuggestionRuntimeStateService;
    private ChatboxSuggestionPresentationService chatboxSuggestionPresentationService;
    private ChatboxSuggestionApplyService chatboxSuggestionApplyService;
    private ChatboxSuggestionWidgetFactoryService chatboxSuggestionWidgetFactoryService;
    private ChatboxPromptWidgetResolverService chatboxPromptWidgetResolverService;

    GeLifecycleSuggestionServices(
        Supplier<Client> clientSupplier,
        Supplier<OfferPreviewRuntimeFacadeService> offerPreviewRuntimeFacadeServiceSupplier,
        Supplier<Integer> offerPreviewItemIdSupplier,
        Supplier<FlipHubItem> offerPreviewItemSupplier,
        Supplier<LocalAccountSessionService> localAccountSessionServiceSupplier,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier,
        LongConsumer ensureProfileLoaded,
        Supplier<GeLimitService> geLimitServiceSupplier,
        Supplier<ItemLookupService> itemLookupServiceSupplier,
        Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier,
        int suggestionTextColor,
        int suggestionHoverTextColor,
        int suggestionTopY,
        int suggestionRightX,
        int suggestionRightWidthPadding,
        String priceSuggestionWidgetName,
        String limitSuggestionWidgetName,
        String affordableLimitSuggestionWidgetName,
        int geOfferPriceVarbit,
        int coinsItemId,
        int selectedSlotVarbit
    ) {
        this.clientSupplier = clientSupplier;
        this.offerPreviewRuntimeFacadeServiceSupplier = offerPreviewRuntimeFacadeServiceSupplier;
        this.offerPreviewItemIdSupplier = offerPreviewItemIdSupplier;
        this.offerPreviewItemSupplier = offerPreviewItemSupplier;
        this.localAccountSessionServiceSupplier = localAccountSessionServiceSupplier;
        this.profileSelectionPresentationFacadeServiceSupplier = profileSelectionPresentationFacadeServiceSupplier;
        this.ensureProfileLoaded = ensureProfileLoaded;
        this.geLimitServiceSupplier = geLimitServiceSupplier;
        this.itemLookupServiceSupplier = itemLookupServiceSupplier;
        this.localTradeSessionFacadeServiceSupplier = localTradeSessionFacadeServiceSupplier;
        this.suggestionTextColor = suggestionTextColor;
        this.suggestionHoverTextColor = suggestionHoverTextColor;
        this.suggestionTopY = suggestionTopY;
        this.suggestionRightX = suggestionRightX;
        this.suggestionRightWidthPadding = suggestionRightWidthPadding;
        this.priceSuggestionWidgetName = priceSuggestionWidgetName;
        this.limitSuggestionWidgetName = limitSuggestionWidgetName;
        this.affordableLimitSuggestionWidgetName = affordableLimitSuggestionWidgetName;
        this.geOfferPriceVarbit = geOfferPriceVarbit;
        this.coinsItemId = coinsItemId;
        this.selectedSlotVarbit = selectedSlotVarbit;
    }

    OfferTypeResolver getOfferTypeResolver() {
        return PluginInjectorBridge.get(OfferTypeResolver.class);
    }

    ChatboxSuggestionRuntimeStateService getChatboxSuggestionRuntimeStateService() {
        return PluginInjectorBridge.get(ChatboxSuggestionRuntimeStateService.class);
    }

    ChatboxSuggestionCycleService getChatboxSuggestionCycleService() {
        return PluginInjectorBridge.get(ChatboxSuggestionCycleService.class);
    }

    ChatboxSuggestionPresentationService getChatboxSuggestionPresentationService() {
        return PluginInjectorBridge.get(ChatboxSuggestionPresentationService.class);
    }

    ChatboxSuggestionApplyService getChatboxSuggestionApplyService() {
        return PluginInjectorBridge.get(ChatboxSuggestionApplyService.class);
    }

    ChatboxSuggestionWidgetFactoryService getChatboxSuggestionWidgetFactoryService() {
        return PluginInjectorBridge.get(ChatboxSuggestionWidgetFactoryService.class);
    }

    ChatboxPromptWidgetResolverService getChatboxPromptWidgetResolverService() {
        return PluginInjectorBridge.get(ChatboxPromptWidgetResolverService.class);
    }

    RemainingLimitSuggestionService getRemainingLimitSuggestionService() {
        return PluginInjectorBridge.get(RemainingLimitSuggestionService.class);
    }

    AffordableLimitSuggestionService getAffordableLimitSuggestionService() {
        return PluginInjectorBridge.get(AffordableLimitSuggestionService.class);
    }

}
