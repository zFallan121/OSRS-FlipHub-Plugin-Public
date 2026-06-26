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

import com.google.gson.Gson;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;

final class GeLifecycleOfferUiRuntimeServices {
    private final GeLifecycleOfferUiRuntimeContext context;

    private GeLifecycleOfferStampStateServices offerStampStateServices;
    private OfferPreviewRuntimeFacadeService offerPreviewRuntimeFacadeService;
    private OfferPreviewItemResolver offerPreviewItemResolver;
    private GeLifecycleSuggestionServices suggestionServices;
    private GeLifecycleTickServices tickServices;
    private GeLifecycleItemServices itemServices;
    private BookmarkStateService bookmarkStateService;
    private OfferEventBuildService offerEventBuildService;
    private OfferUpdateStampService offerUpdateStampService;
    private OfferUpdateStampPersistenceService offerUpdateStampPersistenceService;

    GeLifecycleOfferUiRuntimeServices(
        GeLifecycleRuntimeUtilityServices runtimeUtilityServices,
        Supplier<PluginConfig> configSupplier,
        Supplier<ConfigManager> configManagerSupplier,
        Supplier<Gson> gsonSupplier,
        Supplier<Client> clientSupplier,
        Supplier<ClientThread> clientThreadSupplier,
        Supplier<ItemManager> itemManagerSupplier,
        Supplier<FlipHubPanel> panelSupplier,
        Supplier<ScheduledExecutorService> schedulerSupplier,
        Supplier<PanelRefreshCoordinator> panelRefreshCoordinatorSupplier,
        Supplier<GeLifecycleStatsTradesServices> statsTradesServicesSupplier,
        Supplier<GeLifecycleProfileSelectionServices> profileSelectionServicesSupplier,
        Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier,
        Supplier<GeLifecycleBackfillServices> backfillServicesSupplier,
        Supplier<GeLifecycleProfileWorkflowService> profileWorkflowServiceSupplier,
        Supplier<GeLifecycleEventManageHistoryServices> eventManageHistoryServicesSupplier,
        Supplier<GeLifecyclePanelDataRuntimeService> panelDataRuntimeServiceSupplier,
        Map<Integer, OfferUpdateStamp> offerUpdateStamps,
        Map<String, Integer> itemNameLookupCache,
        Map<Integer, String> itemNameCache,
        Set<Integer> hiddenItems,
        Map<Long, String> profileDisplayNames,
        BookmarkConfigStore bookmarkConfigStore,
        OfferUpdateStampConfigStore offerUpdateStampConfigStore,
        OfferUpdateStampLegacyMatcher offerUpdateStampLegacyMatcher,
        Supplier<Integer> offerPreviewItemIdSupplier,
        Supplier<FlipHubItem> offerPreviewItemSupplier,
        Consumer<Integer> offerPreviewItemIdSetter,
        Consumer<FlipHubItem> offerPreviewItemSetter
    ) {
        this.context = new GeLifecycleOfferUiRuntimeContext(
            runtimeUtilityServices,
            configSupplier,
            configManagerSupplier,
            gsonSupplier,
            clientSupplier,
            clientThreadSupplier,
            itemManagerSupplier,
            panelSupplier,
            schedulerSupplier,
            panelRefreshCoordinatorSupplier,
            statsTradesServicesSupplier,
            profileSelectionServicesSupplier,
            localTradesRuntimeServiceSupplier,
            backfillServicesSupplier,
            profileWorkflowServiceSupplier,
            eventManageHistoryServicesSupplier,
            panelDataRuntimeServiceSupplier,
            offerUpdateStamps,
            itemNameLookupCache,
            itemNameCache,
            hiddenItems,
            profileDisplayNames,
            bookmarkConfigStore,
            offerUpdateStampConfigStore,
            offerUpdateStampLegacyMatcher,
            offerPreviewItemIdSupplier,
            offerPreviewItemSupplier,
            offerPreviewItemIdSetter,
            offerPreviewItemSetter
        );
    }

    GeLifecycleOfferStampStateServices getOfferStampStateServices() {
        GeLifecycleOfferStampStateServices services = offerStampStateServices;
        if (services != null) {
            return services;
        }
        services = GeLifecycleOfferUiCoreFactory.createOfferStampStateServices(
            context,
            this::getOfferUpdateStampPersistenceService,
            this::getOfferUpdateStampService
        );
        offerStampStateServices = services;
        return services;
    }

    OfferPreviewRuntimeFacadeService getOfferPreviewRuntimeFacadeService() {
        OfferPreviewRuntimeFacadeService service = offerPreviewRuntimeFacadeService;
        if (service != null) {
            return service;
        }
        service = GeLifecycleOfferUiCoreFactory.createOfferPreviewRuntimeFacadeService();
        offerPreviewRuntimeFacadeService = service;
        return service;
    }

    OfferPreviewItemResolver getOfferPreviewItemResolver() {
        OfferPreviewItemResolver resolver = offerPreviewItemResolver;
        if (resolver != null) {
            return resolver;
        }
        resolver = GeLifecycleOfferUiCoreFactory.createOfferPreviewItemResolver(
            context,
            this::getOfferPreviewRuntimeFacadeService,
            () -> getItemServices().getItemLookupService()
        );
        offerPreviewItemResolver = resolver;
        return resolver;
    }

    GeLifecycleSuggestionServices getSuggestionServices() {
        GeLifecycleSuggestionServices services = suggestionServices;
        if (services != null) {
            return services;
        }
        services = GeLifecycleOfferUiCoreFactory.createSuggestionServices(
            context,
            this::getOfferPreviewRuntimeFacadeService,
            this::getStatsTradesServices,
            this::getProfileSelectionServices,
            this::getLocalTradesRuntimeService,
            this::getBackfillServices,
            this::getItemServices
        );
        suggestionServices = services;
        return services;
    }

    GeLifecycleTickServices getTickServices() {
        GeLifecycleTickServices services = tickServices;
        if (services != null) {
            return services;
        }
        services = GeLifecycleOfferUiCoreFactory.createTickServices(
            context,
            this::getSuggestionServices,
            this::getEventManageHistoryServices,
            this::getPanelRefreshCoordinator,
            this::getStatsTradesServices,
            this::getLocalTradesRuntimeService,
            this::getProfileSelectionServices,
            this::getProfileWorkflowService
        );
        tickServices = services;
        return services;
    }

    GeLifecycleItemServices getItemServices() {
        GeLifecycleItemServices services = itemServices;
        if (services != null) {
            return services;
        }
        services = GeLifecycleOfferUiDataFactory.createItemServices(
            context,
            this::getPanelRefreshCoordinator,
            this::getBackfillServices,
            this::getProfileSelectionServices,
            this::getProfileWorkflowService,
            this::getStatsTradesServices,
            this::getLocalTradesRuntimeService,
            this::getSuggestionServices
        );
        itemServices = services;
        return services;
    }

    BookmarkStateService getBookmarkStateService() {
        return PluginInjectorBridge.get(BookmarkStateService.class);
    }

    OfferEventBuildService getOfferEventBuildService() {
        OfferEventBuildService service = offerEventBuildService;
        if (service != null) {
            return service;
        }
        service = GeLifecycleOfferUiDataFactory.createOfferEventBuildService(
            this::getOfferUpdateStampService,
            this::getOfferStampStateServices,
            this::getStatsTradesServices
        );
        offerEventBuildService = service;
        return service;
    }

    OfferUpdateStampService getOfferUpdateStampService() {
        OfferUpdateStampService service = offerUpdateStampService;
        if (service != null) {
            return service;
        }
        service = GeLifecycleOfferUiDataFactory.createOfferUpdateStampService(this::getOfferStampStateServices);
        offerUpdateStampService = service;
        return service;
    }

    OfferUpdateStampPersistenceService getOfferUpdateStampPersistenceService() {
        OfferUpdateStampPersistenceService service = offerUpdateStampPersistenceService;
        if (service != null) {
            return service;
        }
        service = GeLifecycleOfferUiDataFactory.createOfferUpdateStampPersistenceService(
            context,
            this::getStatsTradesServices
        );
        offerUpdateStampPersistenceService = service;
        return service;
    }

    private PanelRefreshCoordinator getPanelRefreshCoordinator() {
        return context.panelRefreshCoordinatorSupplier.get();
    }

    private GeLifecycleStatsTradesServices getStatsTradesServices() {
        return context.statsTradesServicesSupplier.get();
    }

    private GeLifecycleProfileSelectionServices getProfileSelectionServices() {
        return context.profileSelectionServicesSupplier.get();
    }

    private GeLifecycleLocalTradesRuntimeService getLocalTradesRuntimeService() {
        return context.localTradesRuntimeServiceSupplier.get();
    }

    private GeLifecycleBackfillServices getBackfillServices() {
        return context.backfillServicesSupplier.get();
    }

    private GeLifecycleProfileWorkflowService getProfileWorkflowService() {
        return context.profileWorkflowServiceSupplier.get();
    }

    private GeLifecycleEventManageHistoryServices getEventManageHistoryServices() {
        return context.eventManageHistoryServicesSupplier.get();
    }
}
