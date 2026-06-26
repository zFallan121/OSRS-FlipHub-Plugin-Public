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
    private ChatboxSuggestionCycleFactoryService chatboxSuggestionCycleFactoryService;
    private ChatboxSuggestionCycleService chatboxSuggestionCycleService;
    private ChatboxSuggestionRuntimeStateService chatboxSuggestionRuntimeStateService;
    private ChatboxSuggestionPresentationService chatboxSuggestionPresentationService;
    private ChatboxSuggestionApplyService chatboxSuggestionApplyService;
    private ChatboxSuggestionWidgetFactoryService chatboxSuggestionWidgetFactoryService;
    private ChatboxPromptWidgetResolverService chatboxPromptWidgetResolverService;
    private RemainingLimitSuggestionFactoryService remainingLimitSuggestionFactoryService;
    private RemainingLimitSuggestionService remainingLimitSuggestionService;
    private AffordableLimitSuggestionFactoryService affordableLimitSuggestionFactoryService;
    private AffordableLimitSuggestionService affordableLimitSuggestionService;

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
        ChatboxSuggestionRuntimeStateService service = chatboxSuggestionRuntimeStateService;
        if (service != null) {
            return service;
        }
        service = new ChatboxSuggestionRuntimeStateService(
            new ChatboxSuggestionRuntimeStatePluginHooks(
                clientSupplier,
                this::getChatboxSuggestionWidgetFactoryService,
                this::getChatboxPromptWidgetResolverService
            )
        );
        chatboxSuggestionRuntimeStateService = service;
        return service;
    }

    ChatboxSuggestionCycleService getChatboxSuggestionCycleService() {
        ChatboxSuggestionCycleService service = chatboxSuggestionCycleService;
        if (service != null) {
            return service;
        }
        service = getChatboxSuggestionCycleFactoryService().create(
            new ChatboxSuggestionCyclePluginHooks(
                clientSupplier,
                this::getChatboxSuggestionRuntimeStateService,
                this::getChatboxSuggestionPresentationService,
                this::getRemainingLimitSuggestionService,
                this::getOfferTypeResolver,
                System::currentTimeMillis
            )
        );
        chatboxSuggestionCycleService = service;
        return service;
    }

    ChatboxSuggestionPresentationService getChatboxSuggestionPresentationService() {
        ChatboxSuggestionPresentationService service = chatboxSuggestionPresentationService;
        if (service != null) {
            return service;
        }
        service = new ChatboxSuggestionPresentationService(
            new ChatboxSuggestionPresentationPluginHooks(
                clientSupplier,
                this::getChatboxSuggestionRuntimeStateService,
                offerPreviewItemIdSupplier,
                offerPreviewItemSupplier,
                this::getRemainingLimitSuggestionService,
                this::getAffordableLimitSuggestionService
            )
        );
        chatboxSuggestionPresentationService = service;
        return service;
    }

    ChatboxSuggestionApplyService getChatboxSuggestionApplyService() {
        ChatboxSuggestionApplyService service = chatboxSuggestionApplyService;
        if (service != null) {
            return service;
        }
        service = new ChatboxSuggestionApplyService(
            new ChatboxSuggestionApplyPluginHooks(
                clientSupplier,
                this::getOfferTypeResolver,
                offerPreviewItemSupplier,
                this::getRemainingLimitSuggestionService,
                this::getAffordableLimitSuggestionService
            )
        );
        chatboxSuggestionApplyService = service;
        return service;
    }

    ChatboxSuggestionWidgetFactoryService getChatboxSuggestionWidgetFactoryService() {
        ChatboxSuggestionWidgetFactoryService service = chatboxSuggestionWidgetFactoryService;
        if (service != null) {
            return service;
        }
        service = new ChatboxSuggestionWidgetFactoryService(
            suggestionTextColor,
            suggestionHoverTextColor,
            suggestionTopY,
            suggestionRightX,
            suggestionRightWidthPadding,
            priceSuggestionWidgetName,
            limitSuggestionWidgetName,
            affordableLimitSuggestionWidgetName,
            new ChatboxSuggestionWidgetFactoryPluginHooks(this::getChatboxSuggestionApplyService)
        );
        chatboxSuggestionWidgetFactoryService = service;
        return service;
    }

    ChatboxPromptWidgetResolverService getChatboxPromptWidgetResolverService() {
        ChatboxPromptWidgetResolverService service = chatboxPromptWidgetResolverService;
        if (service != null) {
            return service;
        }
        service = new ChatboxPromptWidgetResolverService(
            ComponentID.CHATBOX_FULL_INPUT,
            ComponentID.CHATBOX_TITLE,
            ComponentID.CHATBOX_FIRST_MESSAGE,
            ComponentID.CHATBOX_MESSAGE_LINES,
            ComponentID.CHATBOX_CONTAINER,
            new ChatboxPromptWidgetResolverPluginHooks(clientSupplier)
        );
        chatboxPromptWidgetResolverService = service;
        return service;
    }

    RemainingLimitSuggestionService getRemainingLimitSuggestionService() {
        RemainingLimitSuggestionService service = remainingLimitSuggestionService;
        if (service != null) {
            return service;
        }
        service = getRemainingLimitSuggestionFactoryService().create(
            new RemainingLimitSuggestionPluginHooks(
                localAccountSessionServiceSupplier,
                profileSelectionPresentationFacadeServiceSupplier,
                ensureProfileLoaded,
                geLimitServiceSupplier,
                itemLookupServiceSupplier,
                localTradeSessionFacadeServiceSupplier,
                offerPreviewItemSupplier,
                System::currentTimeMillis
            )
        );
        remainingLimitSuggestionService = service;
        return service;
    }

    AffordableLimitSuggestionService getAffordableLimitSuggestionService() {
        AffordableLimitSuggestionService service = affordableLimitSuggestionService;
        if (service != null) {
            return service;
        }
        service = getAffordableLimitSuggestionFactoryService().create(
            new RuneLiteAffordableLimitSuggestionPluginHooks(
                clientSupplier,
                geOfferPriceVarbit,
                coinsItemId,
                selectedSlotVarbit,
                offerPreviewRuntimeFacadeServiceSupplier
            )
        );
        affordableLimitSuggestionService = service;
        return service;
    }

    private ChatboxSuggestionCycleFactoryService getChatboxSuggestionCycleFactoryService() {
        ChatboxSuggestionCycleFactoryService service = chatboxSuggestionCycleFactoryService;
        if (service != null) {
            return service;
        }
        service = new ChatboxSuggestionCycleFactoryService();
        chatboxSuggestionCycleFactoryService = service;
        return service;
    }

    private RemainingLimitSuggestionFactoryService getRemainingLimitSuggestionFactoryService() {
        RemainingLimitSuggestionFactoryService service = remainingLimitSuggestionFactoryService;
        if (service != null) {
            return service;
        }
        service = new RemainingLimitSuggestionFactoryService();
        remainingLimitSuggestionFactoryService = service;
        return service;
    }

    private AffordableLimitSuggestionFactoryService getAffordableLimitSuggestionFactoryService() {
        AffordableLimitSuggestionFactoryService service = affordableLimitSuggestionFactoryService;
        if (service != null) {
            return service;
        }
        service = new AffordableLimitSuggestionFactoryService();
        affordableLimitSuggestionFactoryService = service;
        return service;
    }
}
