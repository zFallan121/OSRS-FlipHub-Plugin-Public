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

final class GeLifecycleOfferUiRuntimeFactory {
    GeLifecycleOfferUiRuntimeServices create(
        GeLifecycleRuntimeUtilityServices runtimeUtilityServices,
        Supplier<PluginConfig> configSupplier,
        Supplier<ConfigManager> configManagerSupplier,
        Gson gson,
        Supplier<Client> clientSupplier,
        Supplier<ClientThread> clientThreadSupplier,
        Supplier<ItemManager> itemManagerSupplier,
        Supplier<FlipHubPanel> panelSupplier,
        Supplier<ScheduledExecutorService> schedulerSupplier,
        Supplier<PanelRefreshCoordinator> panelRefreshCoordinatorSupplier,
        Supplier<GeLifecycleStatsTradesServices> statsTradesServicesSupplier,
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
        return new GeLifecycleOfferUiRuntimeServices(
            runtimeUtilityServices,
            configSupplier,
            configManagerSupplier,
            () -> gson,
            clientSupplier,
            clientThreadSupplier,
            itemManagerSupplier,
            panelSupplier,
            schedulerSupplier,
            panelRefreshCoordinatorSupplier,
            statsTradesServicesSupplier,
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
}
