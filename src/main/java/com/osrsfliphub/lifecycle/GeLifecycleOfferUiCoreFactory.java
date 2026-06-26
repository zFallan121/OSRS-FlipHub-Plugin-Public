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
import net.runelite.api.gameval.VarbitID;

final class GeLifecycleOfferUiCoreFactory {
    private GeLifecycleOfferUiCoreFactory() {
    }

    static GeLifecycleOfferStampStateServices createOfferStampStateServices(
        GeLifecycleOfferUiRuntimeContext context,
        Supplier<OfferUpdateStampPersistenceService> offerUpdateStampPersistenceServiceSupplier,
        Supplier<OfferUpdateStampService> offerUpdateStampServiceSupplier
    ) {
        return new GeLifecycleOfferStampStateServices(
            FliphubConfigGroups.CONFIG_GROUP,
            LEGACY_DEV_CONFIG_GROUP,
            LOGIN_GRACE_MS,
            context.offerUpdateStamps,
            context.configManagerSupplier,
            context.configSupplier,
            offerUpdateStampPersistenceServiceSupplier,
            offerUpdateStampServiceSupplier
        );
    }

    static GeLifecycleSuggestionServices createSuggestionServices(
        GeLifecycleOfferUiRuntimeContext context,
        Supplier<OfferPreviewRuntimeFacadeService> offerPreviewRuntimeFacadeServiceSupplier,
        Supplier<GeLifecycleStatsTradesServices> statsTradesServicesSupplier,
        Supplier<GeLifecycleProfileSelectionServices> profileSelectionServicesSupplier,
        Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier,
        Supplier<GeLifecycleBackfillServices> backfillServicesSupplier,
        Supplier<GeLifecycleItemServices> itemServicesSupplier
    ) {
        return new GeLifecycleSuggestionServices(
            context.clientSupplier,
            offerPreviewRuntimeFacadeServiceSupplier,
            context.offerPreviewItemIdSupplier,
            context.offerPreviewItemSupplier,
            () -> statsTradesServicesSupplier.get().getLocalAccountSessionService(),
            () -> profileSelectionServicesSupplier.get().getProfileSelectionPresentationFacadeService(),
            accountKey -> localTradesRuntimeServiceSupplier.get().ensureProfileLoaded(accountKey),
            () -> backfillServicesSupplier.get().getBackfillMarketServices().getGeLimitService(),
            () -> itemServicesSupplier.get().getItemLookupService(),
            () -> statsTradesServicesSupplier.get().getLocalTradeSessionFacadeService(),
            SUGGESTION_TEXT_COLOR,
            SUGGESTION_HOVER_TEXT_COLOR,
            SUGGESTION_TOP_Y,
            SUGGESTION_RIGHT_X,
            SUGGESTION_RIGHT_WIDTH_PADDING,
            PRICE_SUGGESTION_WIDGET_NAME,
            LIMIT_SUGGESTION_WIDGET_NAME,
            AFFORDABLE_LIMIT_SUGGESTION_WIDGET_NAME,
            GE_OFFER_PRICE_VARBIT,
            COINS_ITEM_ID,
            VarbitID.GE_SELECTEDSLOT
        );
    }

    static GeLifecycleTickServices createTickServices(
        GeLifecycleOfferUiRuntimeContext context,
        Supplier<GeLifecycleSuggestionServices> suggestionServicesSupplier,
        Supplier<GeLifecycleEventManageHistoryServices> eventManageHistoryServicesSupplier,
        Supplier<PanelRefreshCoordinator> panelRefreshCoordinatorSupplier,
        Supplier<GeLifecycleStatsTradesServices> statsTradesServicesSupplier,
        Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier,
        Supplier<GeLifecycleProfileSelectionServices> profileSelectionServicesSupplier,
        Supplier<GeLifecycleProfileWorkflowService> profileWorkflowServiceSupplier
    ) {
        return new GeLifecycleTickServices(
            suggestionServicesSupplier,
            () -> eventManageHistoryServicesSupplier.get().getGeHistoryAutoSyncCoordinatorService().attemptAutoSync(),
            () -> context.runtimeUtilityServices.isPanelVisible(context.panelSupplier.get()),
            () -> context.runtimeUtilityServices.triggerPanelRefresh(
                panelRefreshCoordinatorSupplier.get(),
                context.schedulerSupplier.get()
            ),
            () -> context.runtimeUtilityServices.triggerStatsRefresh(
                panelRefreshCoordinatorSupplier.get(),
                context.schedulerSupplier.get()
            ),
            context.clientSupplier,
            () -> statsTradesServicesSupplier.get().getLocalAccountSessionService(),
            context.profileDisplayNames,
            localTradesRuntimeServiceSupplier,
            () -> profileSelectionServicesSupplier.get().getProfileStorageFacadeService(),
            () -> statsTradesServicesSupplier.get().getLocalTradeSessionFacadeService(),
            () -> profileWorkflowServiceSupplier.get().updateProfileOptionsUI(),
            () -> profileWorkflowServiceSupplier.get().updateProfileHeader()
        );
    }
}
