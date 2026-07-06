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
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;

final class GeLifecyclePanelLocalRuntimeServices {
    private final long accountwideKey;
    private final int maxLocalTrades;
    private final long localEventBucketMs;
    private final long duplicateTradeWindowMs;
    private final Object localStatsLock;
    private final Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount;
    private final Set<Long> loadedProfiles;
    private final LocalTradesLoadCoordinator.State localTradesLoadState;

    private final Supplier<LocalOfferPreviewBuilder> localOfferPreviewBuilderSupplier;
    private final Supplier<LocalItemsResponseBuilder> localItemsResponseBuilderSupplier;
    private final Supplier<String> currentQuerySupplier;
    private final BooleanSupplier bookmarkFilterEnabledSupplier;
    private final Supplier<Set<Integer>> bookmarkedItemsSupplier;
    private final IntSupplier currentPageSupplier;
    private final Supplier<FlipHubPanel> panelSupplier;
    private final Supplier<LocalStatsViewService> localStatsViewServiceSupplier;
    private final Supplier<OfferPreviewRuntimeFacadeService> offerPreviewRuntimeFacadeServiceSupplier;
    private final Supplier<Client> clientSupplier;
    private final Supplier<Map<Integer, OfferUpdateStamp>> offerUpdateStampsSupplier;
    private final Supplier<OfferStampFallbackBuilder> offerStampFallbackBuilderSupplier;
    private final LongSupplier nowSupplier;

    private final Supplier<LocalTradesLoadCoordinator> localTradesLoadCoordinatorSupplier;
    private final Supplier<java.util.concurrent.ScheduledExecutorService> schedulerSupplier;
    private final Supplier<ClientThread> clientThreadSupplier;
    private final Supplier<LocalProfileTradesLoadService> localProfileTradesLoadServiceSupplier;
    private final Supplier<ProfileStorageFacadeService> profileStorageFacadeServiceSupplier;
    private final Runnable markLocalTradesLoadedThisLogin;
    private final Supplier<AccountwideSummaryUploader> accountwideSummaryUploaderSupplier;
    private final Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier;
    private final Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier;
    private final Runnable updateProfileOptionsUi;
    private final Runnable updateProfileHeader;

    private GeLifecyclePanelDataRuntimeService panelDataRuntimeService;
    private GeLifecycleLocalTradesRuntimeService localTradesRuntimeService;

    GeLifecyclePanelLocalRuntimeServices(
        long accountwideKey,
        int maxLocalTrades,
        long localEventBucketMs,
        long duplicateTradeWindowMs,
        Object localStatsLock,
        Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount,
        Set<Long> loadedProfiles,
        LocalTradesLoadCoordinator.State localTradesLoadState,
        Supplier<LocalOfferPreviewBuilder> localOfferPreviewBuilderSupplier,
        Supplier<LocalItemsResponseBuilder> localItemsResponseBuilderSupplier,
        Supplier<String> currentQuerySupplier,
        BooleanSupplier bookmarkFilterEnabledSupplier,
        Supplier<Set<Integer>> bookmarkedItemsSupplier,
        IntSupplier currentPageSupplier,
        Supplier<FlipHubPanel> panelSupplier,
        Supplier<LocalStatsViewService> localStatsViewServiceSupplier,
        Supplier<OfferPreviewRuntimeFacadeService> offerPreviewRuntimeFacadeServiceSupplier,
        Supplier<Client> clientSupplier,
        Supplier<Map<Integer, OfferUpdateStamp>> offerUpdateStampsSupplier,
        Supplier<OfferStampFallbackBuilder> offerStampFallbackBuilderSupplier,
        LongSupplier nowSupplier,
        Supplier<LocalTradesLoadCoordinator> localTradesLoadCoordinatorSupplier,
        Supplier<java.util.concurrent.ScheduledExecutorService> schedulerSupplier,
        Supplier<ClientThread> clientThreadSupplier,
        Supplier<LocalProfileTradesLoadService> localProfileTradesLoadServiceSupplier,
        Supplier<ProfileStorageFacadeService> profileStorageFacadeServiceSupplier,
        Runnable markLocalTradesLoadedThisLogin,
        Supplier<AccountwideSummaryUploader> accountwideSummaryUploaderSupplier,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier,
        Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier,
        Runnable updateProfileOptionsUi,
        Runnable updateProfileHeader
    ) {
        this.accountwideKey = accountwideKey;
        this.maxLocalTrades = maxLocalTrades;
        this.localEventBucketMs = localEventBucketMs;
        this.duplicateTradeWindowMs = duplicateTradeWindowMs;
        this.localStatsLock = localStatsLock;
        this.localTradeDeltasByAccount = localTradeDeltasByAccount;
        this.loadedProfiles = loadedProfiles;
        this.localTradesLoadState = localTradesLoadState;
        this.localOfferPreviewBuilderSupplier = localOfferPreviewBuilderSupplier;
        this.localItemsResponseBuilderSupplier = localItemsResponseBuilderSupplier;
        this.currentQuerySupplier = currentQuerySupplier;
        this.bookmarkFilterEnabledSupplier = bookmarkFilterEnabledSupplier;
        this.bookmarkedItemsSupplier = bookmarkedItemsSupplier;
        this.currentPageSupplier = currentPageSupplier;
        this.panelSupplier = panelSupplier;
        this.localStatsViewServiceSupplier = localStatsViewServiceSupplier;
        this.offerPreviewRuntimeFacadeServiceSupplier = offerPreviewRuntimeFacadeServiceSupplier;
        this.clientSupplier = clientSupplier;
        this.offerUpdateStampsSupplier = offerUpdateStampsSupplier;
        this.offerStampFallbackBuilderSupplier = offerStampFallbackBuilderSupplier;
        this.nowSupplier = nowSupplier;
        this.localTradesLoadCoordinatorSupplier = localTradesLoadCoordinatorSupplier;
        this.schedulerSupplier = schedulerSupplier;
        this.clientThreadSupplier = clientThreadSupplier;
        this.localProfileTradesLoadServiceSupplier = localProfileTradesLoadServiceSupplier;
        this.profileStorageFacadeServiceSupplier = profileStorageFacadeServiceSupplier;
        this.markLocalTradesLoadedThisLogin = markLocalTradesLoadedThisLogin;
        this.accountwideSummaryUploaderSupplier = accountwideSummaryUploaderSupplier;
        this.profileSelectionPresentationFacadeServiceSupplier = profileSelectionPresentationFacadeServiceSupplier;
        this.uploadBackfillDispatchServiceSupplier = uploadBackfillDispatchServiceSupplier;
        this.updateProfileOptionsUi = updateProfileOptionsUi;
        this.updateProfileHeader = updateProfileHeader;
    }

    GeLifecyclePanelDataRuntimeService getPanelDataRuntimeService() {
        GeLifecyclePanelDataRuntimeService service = panelDataRuntimeService;
        if (service != null) {
            return service;
        }
        service = new GeLifecyclePanelDataRuntimeService(
            localOfferPreviewBuilderSupplier,
            localItemsResponseBuilderSupplier,
            currentQuerySupplier,
            bookmarkFilterEnabledSupplier,
            bookmarkedItemsSupplier,
            currentPageSupplier,
            panelSupplier,
            localStatsViewServiceSupplier,
            offerPreviewRuntimeFacadeServiceSupplier,
            clientSupplier,
            offerUpdateStampsSupplier,
            offerStampFallbackBuilderSupplier,
            nowSupplier
        );
        panelDataRuntimeService = service;
        return service;
    }

    GeLifecycleLocalTradesRuntimeService getLocalTradesRuntimeService() {
        return PluginInjectorBridge.get(GeLifecycleLocalTradesRuntimeService.class);
    }
}
