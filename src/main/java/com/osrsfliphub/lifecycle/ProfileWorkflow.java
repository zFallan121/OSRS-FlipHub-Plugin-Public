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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;

@javax.inject.Singleton
final class ProfileWorkflow {
    private final long accountwideKey;
    private final Object localStatsLock;
    private final Set<Long> loadedProfiles;
    private final Map<Long, List<Delta>> localTradeDeltasByAccount;
    private final Map<Long, Long> localSessionStartByAccount;
    private final Map<Long, StatsCache> statsCacheByAccount;
    private final Set<Integer> bookmarkedItems;
    private final Map<Integer, OfferSnapshot> snapshots;
    private final Supplier<LocalTradesRuntime> localTradesRuntimeServiceSupplier;
    private final Supplier<SummaryUploader> accountwideSummaryUploaderSupplier;
    private final Supplier<ProfileSelectionPresentation> profileSelectionPresentationFacadeServiceSupplier;
    private final Supplier<UploadBackfillDispatch> uploadBackfillDispatchServiceSupplier;
    private final Supplier<ScheduledExecutorService> schedulerSupplier;
    private final Supplier<LocalStatsSnapshotService> localStatsSnapshotServiceSupplier;
    private final Supplier<TradeSession> localTradeSessionFacadeServiceSupplier;
    private final Supplier<ProfileSelectionPersistence> profileSelectionPersistenceServiceSupplier;
    private final ProfileSelectionState profileSelection;
    private final Supplier<ProfileLogin> profileLoginServiceSupplier;
    private final Supplier<BookmarkState> bookmarkStateServiceSupplier;
    private final Supplier<ProfileUi> profileUiCoordinatorSupplier;
    private final Supplier<Panel> panelSupplier;
    private final Supplier<AccountMerge> localAccountMergeServiceSupplier;
    private final Supplier<LocalStatsCacheService> localStatsCacheServiceSupplier;
    private final Supplier<Client> clientSupplier;

    @javax.inject.Inject
    ProfileWorkflow(PluginState pluginState) {
        this(Const.ACCOUNTWIDE_KEY,
            pluginState.getLocalStatsLock(),
            pluginState.getLoadedProfiles(),
            pluginState.getLocalTradeDeltasByAccount(),
            pluginState.getLocalSessionStartByAccount(),
            pluginState.getStatsCacheByAccount(),
            pluginState.getBookmarkedItems(),
            pluginState.getSnapshots(),
            () -> Bridge.get(LocalTradesRuntime.class),
            () -> Bridge.get(SummaryUploader.class),
            () -> Bridge.get(ProfileSelectionPresentation.class),
            () -> Bridge.get(UploadBackfillDispatch.class),
            () -> Access.plugin().scheduler,
            () -> Bridge.get(LocalStatsSnapshotService.class),
            () -> Bridge.get(TradeSession.class),
            () -> Bridge.get(ProfileSelectionPersistence.class),
            pluginState.getProfileSelection(),
            () -> Bridge.get(ProfileLogin.class),
            () -> Bridge.get(BookmarkState.class),
            () -> Bridge.get(ProfileUi.class),
            () -> Access.plugin().panel,
            () -> Bridge.get(AccountMerge.class),
            () -> Bridge.get(LocalStatsCacheService.class),
            () -> Access.plugin().client);
    }

    ProfileWorkflow(
        long accountwideKey,
        Object localStatsLock,
        Set<Long> loadedProfiles,
        Map<Long, List<Delta>> localTradeDeltasByAccount,
        Map<Long, Long> localSessionStartByAccount,
        Map<Long, StatsCache> statsCacheByAccount,
        Set<Integer> bookmarkedItems,
        Map<Integer, OfferSnapshot> snapshots,
        Supplier<LocalTradesRuntime> localTradesRuntimeServiceSupplier,
        Supplier<SummaryUploader> accountwideSummaryUploaderSupplier,
        Supplier<ProfileSelectionPresentation> profileSelectionPresentationFacadeServiceSupplier,
        Supplier<UploadBackfillDispatch> uploadBackfillDispatchServiceSupplier,
        Supplier<ScheduledExecutorService> schedulerSupplier,
        Supplier<LocalStatsSnapshotService> localStatsSnapshotServiceSupplier,
        Supplier<TradeSession> localTradeSessionFacadeServiceSupplier,
        Supplier<ProfileSelectionPersistence> profileSelectionPersistenceServiceSupplier,
        ProfileSelectionState profileSelection,
        Supplier<ProfileLogin> profileLoginServiceSupplier,
        Supplier<BookmarkState> bookmarkStateServiceSupplier,
        Supplier<ProfileUi> profileUiCoordinatorSupplier,
        Supplier<Panel> panelSupplier,
        Supplier<AccountMerge> localAccountMergeServiceSupplier,
        Supplier<LocalStatsCacheService> localStatsCacheServiceSupplier,
        Supplier<Client> clientSupplier
    ) {
        this.accountwideKey = accountwideKey;
        this.localStatsLock = localStatsLock;
        this.loadedProfiles = loadedProfiles;
        this.localTradeDeltasByAccount = localTradeDeltasByAccount;
        this.localSessionStartByAccount = localSessionStartByAccount;
        this.statsCacheByAccount = statsCacheByAccount;
        this.bookmarkedItems = bookmarkedItems;
        this.snapshots = snapshots;
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
    }

    void reloadProfileFromDisk(long accountKey) {
        if (accountKey < 0) {
            return;
        }
        LocalTradesRuntime localTradesRuntime = localTradesRuntimeServiceSupplier.get();
        if (localTradesRuntime == null) {
            return;
        }
        localTradesRuntime.loadLocalTradesForAccount(accountKey, false);
        loadedProfiles.add(accountKey);
        if (accountKey != accountwideKey) {
            localTradesRuntime.loadLocalTradesForAccount(accountwideKey, false);
            loadedProfiles.add(accountwideKey);
        }
        updateProfileOptionsUI();
        updateProfileHeader();
        accountwideSummaryUploaderSupplier.get().markDirty();
        if (profileSelectionPresentationFacadeServiceSupplier.get().isLinked()) {
            uploadBackfillDispatchServiceSupplier.get().requestAccountwideSync();
            uploadBackfillDispatchServiceSupplier.get().requestBackfillAttempt(schedulerSupplier.get(), 10, true);
        }
    }

    StatsSnapshot buildReconciledAccountwideSnapshot() {
        StatsSnapshot snapshot = localStatsSnapshotServiceSupplier.get()
            .buildSnapshot(accountwideKey, null, StatsItemSort.COMPLETION);
        StatsSummary summary = snapshot != null && snapshot.summary != null ? snapshot.summary : new StatsSummary();
        List<StatsItem> items = snapshot != null && snapshot.items != null ? snapshot.items : new ArrayList<>();
        Map<Integer, List<StatsFlipInstance>> flipHistory = localTradeSessionFacadeServiceSupplier.get()
            .buildStatsFlipHistory(accountwideKey, null);
        StatsView.reconcileWithFlipHistory(summary, items, flipHistory);
        return new StatsSnapshot(summary, items);
    }

    void loadProfileSelectionState() {
        boolean migratedFromLegacy = profileSelectionPersistenceServiceSupplier.get().load(profileSelection);
        if (migratedFromLegacy) {
            persistProfileSelectionState();
        }
    }

    void persistProfileSelectionState() {
        profileSelectionPersistenceServiceSupplier.get().persist(profileSelection);
    }

    void updateProfileForLogin() {
        profileLoginServiceSupplier.get().handleLogin(
            profileSelection,
            localTradeSessionFacadeServiceSupplier.get().resolveAccountHash(),
            profileSelectionPresentationFacadeServiceSupplier.get().resolveDisplayName()
        );
        bookmarkStateServiceSupplier.get().loadSelectedBookmarks(
            profileSelectionPresentationFacadeServiceSupplier.get().resolveSelectedProfileKey(),
            bookmarkedItems
        );
    }

    void updateProfileOptionsUI() {
        profileUiCoordinatorSupplier.get().updateProfileOptionsUi();
    }

    void updateProfileHeader() {
        profileUiCoordinatorSupplier.get().updateProfileHeader();
    }

    void ensureSelectedProfileLoaded() {
        localTradesRuntimeServiceSupplier.get().ensureProfileLoaded(
            profileSelectionPresentationFacadeServiceSupplier.get().resolveSelectedProfileKey()
        );
    }

    void showManageDataError(String message) {
        Panel panel = panelSupplier.get();
        if (panel == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
            panel,
            message != null ? message : "Unknown error.",
            "FlipHub Manage Data",
            JOptionPane.WARNING_MESSAGE
        ));
    }

    void mergeLocalAccountData(long targetKey, long sourceKey) {
        AccountMerge.Result mergeResult;
        synchronized (localStatsLock) {
            mergeResult = localAccountMergeServiceSupplier.get().merge(
                localTradeDeltasByAccount,
                localSessionStartByAccount,
                targetKey,
                sourceKey
            );
        }
        RejectionStore rejections = Bridge.get(RejectionStore.class);
        if (rejections != null) {
            rejections.move(sourceKey, targetKey);
        }
        if (mergeResult != null && mergeResult.mergedSnapshot != null) {
            localStatsCacheServiceSupplier.get().rebuild(targetKey, mergeResult.mergedSnapshot);
        }
        statsCacheByAccount.remove(sourceKey);
        if (mergeResult != null && mergeResult.changed && targetKey > 0) {
            accountwideSummaryUploaderSupplier.get().markDirty();
            if (profileSelectionPresentationFacadeServiceSupplier.get().isLinked()) {
                uploadBackfillDispatchServiceSupplier.get().requestAccountwideSync();
            }
        }
    }

    void primeOfferSnapshots() {
        Client client = clientSupplier.get();
        if (client == null) {
            return;
        }
        snapshots.clear();
        GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
        if (offers == null || offers.length == 0) {
            return;
        }
        for (int slot = 0; slot < offers.length; slot++) {
            GrandExchangeOffer offer = offers[slot];
            if (offer == null) {
                continue;
            }
            if (offer.getItemId() <= 0 || offer.getState() == GrandExchangeOfferState.EMPTY) {
                continue;
            }
            OfferSnapshot snapshot = OfferSnapshot.fromOffer(slot, offer, null);
            if (snapshot != null) {
                snapshots.put(slot, snapshot);
            }
        }
    }
}
