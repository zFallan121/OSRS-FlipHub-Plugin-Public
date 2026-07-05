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

import static com.osrsfliphub.GeLifecyclePluginConstants.*;

import java.util.function.Supplier;

final class GeLifecycleOfferUiDataFactory {
    private GeLifecycleOfferUiDataFactory() {
    }

    static GeLifecycleItemServices createItemServices(
        GeLifecycleOfferUiRuntimeContext context,
        Supplier<PanelRefreshCoordinator> panelRefreshCoordinatorSupplier,
        Supplier<GeLifecycleBackfillServices> backfillServicesSupplier,
        Supplier<GeLifecycleProfileSelectionServices> profileSelectionServicesSupplier,
        Supplier<GeLifecycleProfileWorkflowService> profileWorkflowServiceSupplier,
        Supplier<GeLifecycleStatsTradesServices> statsTradesServicesSupplier,
        Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier
    ) {
        return new GeLifecycleItemServices(
            context.itemManagerSupplier.get(),
            context.clientThreadSupplier,
            () -> context.runtimeUtilityServices.scheduleRefreshSoon(
                panelRefreshCoordinatorSupplier.get(),
                context.schedulerSupplier.get()
            ),
            () -> context.runtimeUtilityServices.triggerStatsRefresh(
                panelRefreshCoordinatorSupplier.get(),
                context.schedulerSupplier.get()
            ),
            context.itemNameLookupCache,
            context.itemNameCache,
            () -> backfillServicesSupplier.get().getBackfillMarketServices().getLocalItemEnrichmentService(),
            () -> PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class),
            () -> statsTradesServicesSupplier.get().getLocalAccountSessionService(),
            () -> statsTradesServicesSupplier.get().getLocalTradeSessionFacadeService(),
            context.clientSupplier,
            context.hiddenItems::contains,
            () -> backfillServicesSupplier.get().getBackfillMarketServices().getGeLimitService(),
            context.panelDataRuntimeServiceSupplier,
            () -> profileWorkflowServiceSupplier.get().ensureSelectedProfileLoaded(),
            accountKey -> localTradesRuntimeServiceSupplier.get().ensureProfileLoaded(accountKey),
            () -> PluginInjectorBridge.get(ChatboxSuggestionRuntimeStateService.class),
            context.panelSupplier,
            context.offerPreviewItemIdSupplier,
            context.offerPreviewItemSupplier,
            context.offerPreviewItemIdSetter,
            context.offerPreviewItemSetter,
            DEFAULT_ITEMS_PAGE_SIZE
        );
    }

}
