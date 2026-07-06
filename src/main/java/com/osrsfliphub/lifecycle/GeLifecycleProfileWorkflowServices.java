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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.LongPredicate;
import java.util.function.Supplier;
import net.runelite.api.Client;

final class GeLifecycleProfileWorkflowServices {
    private final Object localStatsLock;
    private final Set<Long> loadedProfiles;
    private final Map<Long, List<LocalTradeDelta>> localTradeDeltasByAccount;
    private final Map<Long, Long> localSessionStartByAccount;
    private final Map<Long, LocalStatsCache> statsCacheByAccount;
    private final Set<Integer> bookmarkedItems;
    private final Map<Integer, OfferSnapshot> snapshots;
    private final Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier;
    private final Supplier<AccountwideSummaryUploader> accountwideSummaryUploaderSupplier;
    private final Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier;
    private final Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier;
    private final Supplier<ScheduledExecutorService> schedulerSupplier;
    private final Supplier<LocalStatsSnapshotService> localStatsSnapshotServiceSupplier;
    private final Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier;
    private final Supplier<ProfileSelectionPersistenceService> profileSelectionPersistenceServiceSupplier;
    private final ProfileSelectionState profileSelection;
    private final Supplier<ProfileLoginService> profileLoginServiceSupplier;
    private final Supplier<BookmarkStateService> bookmarkStateServiceSupplier;
    private final Supplier<ProfileUiCoordinator> profileUiCoordinatorSupplier;
    private final Supplier<FlipHubPanel> panelSupplier;
    private final Supplier<LocalAccountMergeService> localAccountMergeServiceSupplier;
    private final Supplier<LocalStatsCacheService> localStatsCacheServiceSupplier;
    private final Supplier<Client> clientSupplier;
    private final LongPredicate isWipeBarrierArmed;

    private GeLifecycleProfileWorkflowService profileWorkflowService;
    private LegacyLocalTradesFilterService legacyLocalTradesFilterService;

    GeLifecycleProfileWorkflowServices(
        GeLifecycleSharedState sharedState,
        Supplier<GeLifecycleLocalTradesRuntimeService> localTradesRuntimeServiceSupplier,
        Supplier<AccountwideSummaryUploader> accountwideSummaryUploaderSupplier,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier,
        Supplier<UploadBackfillDispatchService> uploadBackfillDispatchServiceSupplier,
        Supplier<ScheduledExecutorService> schedulerSupplier,
        Supplier<LocalStatsSnapshotService> localStatsSnapshotServiceSupplier,
        Supplier<LocalTradeSessionFacadeService> localTradeSessionFacadeServiceSupplier,
        Supplier<ProfileSelectionPersistenceService> profileSelectionPersistenceServiceSupplier,
        ProfileSelectionState profileSelection,
        Supplier<ProfileLoginService> profileLoginServiceSupplier,
        Supplier<BookmarkStateService> bookmarkStateServiceSupplier,
        Supplier<ProfileUiCoordinator> profileUiCoordinatorSupplier,
        Supplier<FlipHubPanel> panelSupplier,
        Supplier<LocalAccountMergeService> localAccountMergeServiceSupplier,
        Supplier<LocalStatsCacheService> localStatsCacheServiceSupplier,
        Supplier<Client> clientSupplier,
        LongPredicate isWipeBarrierArmed
    ) {
        this.localStatsLock = sharedState.getLocalStatsLock();
        this.loadedProfiles = sharedState.getLoadedProfiles();
        this.localTradeDeltasByAccount = sharedState.getLocalTradeDeltasByAccount();
        this.localSessionStartByAccount = sharedState.getLocalSessionStartByAccount();
        this.statsCacheByAccount = sharedState.getStatsCacheByAccount();
        this.bookmarkedItems = sharedState.getBookmarkedItems();
        this.snapshots = sharedState.getSnapshots();
        this.localTradesRuntimeServiceSupplier = localTradesRuntimeServiceSupplier;
        this.accountwideSummaryUploaderSupplier = accountwideSummaryUploaderSupplier;
        this.profileSelectionPresentationFacadeServiceSupplier = profileSelectionPresentationFacadeServiceSupplier;
        this.uploadBackfillDispatchServiceSupplier = uploadBackfillDispatchServiceSupplier;
        this.schedulerSupplier = schedulerSupplier;
        this.localStatsSnapshotServiceSupplier = localStatsSnapshotServiceSupplier;
        this.localTradeSessionFacadeServiceSupplier = localTradeSessionFacadeServiceSupplier;
        this.profileSelectionPersistenceServiceSupplier = profileSelectionPersistenceServiceSupplier;
        this.profileSelection = profileSelection;
        this.profileLoginServiceSupplier = profileLoginServiceSupplier;
        this.bookmarkStateServiceSupplier = bookmarkStateServiceSupplier;
        this.profileUiCoordinatorSupplier = profileUiCoordinatorSupplier;
        this.panelSupplier = panelSupplier;
        this.localAccountMergeServiceSupplier = localAccountMergeServiceSupplier;
        this.localStatsCacheServiceSupplier = localStatsCacheServiceSupplier;
        this.clientSupplier = clientSupplier;
        this.isWipeBarrierArmed = isWipeBarrierArmed;
    }

    GeLifecycleProfileWorkflowService getProfileWorkflowService() {
        return PluginInjectorBridge.get(GeLifecycleProfileWorkflowService.class);
    }

    LegacyLocalTradesFilterService getLegacyLocalTradesFilterService() {
        return PluginInjectorBridge.get(LegacyLocalTradesFilterService.class);
    }
}
