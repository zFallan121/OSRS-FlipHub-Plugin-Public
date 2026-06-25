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

final class GeLifecycleOfferUiRuntimeContext {
    final GeLifecycleRuntimeUtilityServices runtimeUtilityServices;
    final Supplier<PluginConfig> configSupplier;
    final Supplier<ConfigManager> configManagerSupplier;
    final Supplier<Gson> gsonSupplier;
    final Supplier<Client> clientSupplier;
    final Supplier<ClientThread> clientThreadSupplier;
    final Supplier<ItemManager> itemManagerSupplier;
    final Supplier<FlipHubPanel> panelSupplier;
    final Supplier<ScheduledExecutorService> schedulerSupplier;
    final Supplier<PanelRefreshCoordinator> panelRefreshCoordinatorSupplier;
    final Supplier<GeLifecycleStatsTradesServices> statsTradesServicesSupplier;
    final Supplier<GeLifecycleProfileSelectionServices> profileSelectionServicesSupplier;
    final Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier;
    final Supplier<GeLifecycleBackfillServices> backfillServicesSupplier;
    final Supplier<GeLifecycleProfileWorkflowService> profileWorkflowServiceSupplier;
    final Supplier<GeLifecycleEventManageHistoryServices> eventManageHistoryServicesSupplier;
    final Supplier<GeLifecyclePanelDataRuntimeService> panelDataRuntimeServiceSupplier;
    final Map<Integer, OfferUpdateStamp> offerUpdateStamps;
    final Map<String, Integer> itemNameLookupCache;
    final Map<Integer, String> itemNameCache;
    final Set<Integer> hiddenItems;
    final Map<Long, String> profileDisplayNames;
    final BookmarkConfigStore bookmarkConfigStore;
    final OfferUpdateStampConfigStore offerUpdateStampConfigStore;
    final OfferUpdateStampLegacyMatcher offerUpdateStampLegacyMatcher;
    final Supplier<Integer> offerPreviewItemIdSupplier;
    final Supplier<FlipHubItem> offerPreviewItemSupplier;
    final Consumer<Integer> offerPreviewItemIdSetter;
    final Consumer<FlipHubItem> offerPreviewItemSetter;

    GeLifecycleOfferUiRuntimeContext(
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
        this.runtimeUtilityServices = runtimeUtilityServices;
        this.configSupplier = configSupplier;
        this.configManagerSupplier = configManagerSupplier;
        this.gsonSupplier = gsonSupplier;
        this.clientSupplier = clientSupplier;
        this.clientThreadSupplier = clientThreadSupplier;
        this.itemManagerSupplier = itemManagerSupplier;
        this.panelSupplier = panelSupplier;
        this.schedulerSupplier = schedulerSupplier;
        this.panelRefreshCoordinatorSupplier = panelRefreshCoordinatorSupplier;
        this.statsTradesServicesSupplier = statsTradesServicesSupplier;
        this.profileSelectionServicesSupplier = profileSelectionServicesSupplier;
        this.localTradesRuntimeServiceSupplier = localTradesRuntimeServiceSupplier;
        this.backfillServicesSupplier = backfillServicesSupplier;
        this.profileWorkflowServiceSupplier = profileWorkflowServiceSupplier;
        this.eventManageHistoryServicesSupplier = eventManageHistoryServicesSupplier;
        this.panelDataRuntimeServiceSupplier = panelDataRuntimeServiceSupplier;
        this.offerUpdateStamps = offerUpdateStamps;
        this.itemNameLookupCache = itemNameLookupCache;
        this.itemNameCache = itemNameCache;
        this.hiddenItems = hiddenItems;
        this.profileDisplayNames = profileDisplayNames;
        this.bookmarkConfigStore = bookmarkConfigStore;
        this.offerUpdateStampConfigStore = offerUpdateStampConfigStore;
        this.offerUpdateStampLegacyMatcher = offerUpdateStampLegacyMatcher;
        this.offerPreviewItemIdSupplier = offerPreviewItemIdSupplier;
        this.offerPreviewItemSupplier = offerPreviewItemSupplier;
        this.offerPreviewItemIdSetter = offerPreviewItemIdSetter;
        this.offerPreviewItemSetter = offerPreviewItemSetter;
    }
}
