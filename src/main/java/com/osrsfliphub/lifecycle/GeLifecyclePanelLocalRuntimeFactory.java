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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;

final class GeLifecyclePanelLocalRuntimeFactory {
    GeLifecyclePanelLocalRuntimeServices create(
        long accountwideKey,
        int maxLocalTrades,
        long localEventBucketMs,
        long duplicateTradeWindowMs,
        Object localStatsLock,
        Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount,
        Set<Long> loadedProfiles,
        LocalTradesLoadCoordinator.State localTradesLoadState,
        Supplier<GeLifecycleItemServices> itemServicesSupplier,
        Supplier<String> currentQuerySupplier,
        BooleanSupplier bookmarkFilterEnabledSupplier,
        Supplier<Set<Integer>> bookmarkedItemsSupplier,
        Supplier<Integer> currentPageSupplier,
        Supplier<FlipHubPanel> panelSupplier,
        Supplier<GeLifecycleStatsTradesServices> statsTradesServicesSupplier,
        Supplier<OfferPreviewRuntimeFacadeService> offerPreviewRuntimeFacadeServiceSupplier,
        Supplier<Client> clientSupplier,
        Map<Integer, OfferUpdateStamp> offerUpdateStamps,
        Supplier<java.util.concurrent.ScheduledExecutorService> schedulerSupplier,
        Supplier<ClientThread> clientThreadSupplier,
        Supplier<GeLifecycleProfileSelectionServices> profileSelectionServicesSupplier,
        Runnable setLocalTradesLoadedThisLoginAction,
        Supplier<GeLifecycleBackfillServices> backfillServicesSupplier,
        Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier,
        Supplier<GeLifecycleProfileWorkflowService> profileWorkflowServiceSupplier
    ) {
        return new GeLifecyclePanelLocalRuntimeServices(
            accountwideKey,
            maxLocalTrades,
            localEventBucketMs,
            duplicateTradeWindowMs,
            localStatsLock,
            localTradeDeltasByAccount,
            loadedProfiles,
            localTradesLoadState,
            () -> itemServicesSupplier.get().getLocalOfferPreviewBuilder(),
            () -> itemServicesSupplier.get().getLocalItemsResponseBuilder(),
            currentQuerySupplier,
            bookmarkFilterEnabledSupplier,
            bookmarkedItemsSupplier,
            currentPageSupplier::get,
            panelSupplier,
            () -> statsTradesServicesSupplier.get().getLocalStatsViewService(),
            offerPreviewRuntimeFacadeServiceSupplier,
            clientSupplier,
            () -> offerUpdateStamps,
            () -> statsTradesServicesSupplier.get().getOfferStampFallbackBuilder(),
            System::currentTimeMillis,
            () -> statsTradesServicesSupplier.get().getLocalTradesLoadCoordinator(),
            schedulerSupplier,
            clientThreadSupplier,
            () -> statsTradesServicesSupplier.get().getLocalProfileTradesLoadService(),
            () -> profileSelectionServicesSupplier.get().getProfileStorageFacadeService(),
            setLocalTradesLoadedThisLoginAction,
            () -> backfillServicesSupplier.get().getAccountwideSummaryUploader(),
            () -> profileSelectionServicesSupplier.get().getProfileSelectionPresentationFacadeService(),
            uploadBackfillDispatchServiceSupplier,
            () -> profileWorkflowServiceSupplier.get().updateProfileOptionsUI(),
            () -> profileWorkflowServiceSupplier.get().updateProfileHeader()
        );
    }
}
