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

import java.util.*;
import javax.swing.*;
import net.runelite.api.*;

@javax.inject.Singleton
final class ProfileWorkflow {
    private final Client client;
    private final LocalTradesRuntime localTradesRuntime;
    private final SummaryUploader summaryUploader;
    private final ProfileSelectionPresentation selectionPresentation;
    private final UploadBackfillDispatch uploadBackfillDispatch;
    private final LocalStatsSnapshotService localStatsSnapshotService;
    private final TradeSession tradeSession;
    private final ProfileSelectionPersistence selectionPersistence;
    private final ProfileLogin login;
    private final BookmarkState bookmarkState;
    private final ProfileUi ui;
    private final AccountMerge accountMerge;
    private final LocalStatsCacheService localStatsCacheService;
    private final long accountwideKey = Const.ACCOUNTWIDE_KEY;
    private final Object localStatsLock;
    private final Set<Long> loadedProfiles;
    private final Map<Long, List<Delta>> localTradeDeltasByAccount;
    private final Map<Long, Long> localSessionStartByAccount;
    private final Map<Long, StatsCache> statsCacheByAccount;
    private final Set<Integer> bookmarkedItems;
    private final Map<Integer, OfferSnapshot> snapshots;
    private final ProfileSelectionState profileSelection;

    @javax.inject.Inject
    ProfileWorkflow(
        PluginState pluginState,
        LocalTradesRuntime localTradesRuntime,
        SummaryUploader summaryUploader,
        ProfileSelectionPresentation selectionPresentation,
        UploadBackfillDispatch uploadBackfillDispatch,
        LocalStatsSnapshotService localStatsSnapshotService,
        TradeSession tradeSession,
        ProfileSelectionPersistence selectionPersistence,
        ProfileLogin login,
        BookmarkState bookmarkState,
        ProfileUi ui,
        AccountMerge accountMerge,
        LocalStatsCacheService localStatsCacheService,
        Client client
    ) {
        this.client = client;
        this.localTradesRuntime = localTradesRuntime;
        this.summaryUploader = summaryUploader;
        this.selectionPresentation = selectionPresentation;
        this.uploadBackfillDispatch = uploadBackfillDispatch;
        this.localStatsSnapshotService = localStatsSnapshotService;
        this.tradeSession = tradeSession;
        this.selectionPersistence = selectionPersistence;
        this.login = login;
        this.bookmarkState = bookmarkState;
        this.ui = ui;
        this.accountMerge = accountMerge;
        this.localStatsCacheService = localStatsCacheService;
        this.localStatsLock = pluginState.getLocalStatsLock();
        this.loadedProfiles = pluginState.getLoadedProfiles();
        this.localTradeDeltasByAccount = pluginState.getLocalTradeDeltasByAccount();
        this.localSessionStartByAccount = pluginState.getLocalSessionStartByAccount();
        this.statsCacheByAccount = pluginState.getStatsCacheByAccount();
        this.bookmarkedItems = pluginState.getBookmarkedItems();
        this.snapshots = pluginState.getSnapshots();
        this.profileSelection = pluginState.getProfileSelection();
    }

    void reloadProfileFromDisk(long accountKey) {
        if (accountKey < 0) {
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
        summaryUploader.markDirty();
        if (selectionPresentation.isLinked()) {
            uploadBackfillDispatch.requestAccountwideSync();
            uploadBackfillDispatch.requestBackfillAttempt(Access.plugin().scheduler, 10, true);
        }
    }

    StatsSnapshot buildReconciledAccountwideSnapshot() {
        StatsSnapshot snapshot = localStatsSnapshotService
            .buildSnapshot(accountwideKey, null, StatsItemSort.COMPLETION);
        StatsSummary summary = snapshot != null && snapshot.summary != null ? snapshot.summary : new StatsSummary();
        List<StatsItem> items = snapshot != null && snapshot.items != null ? snapshot.items : new ArrayList<>();
        Map<Integer, List<StatsFlipInstance>> flipHistory = tradeSession
            .buildStatsFlipHistory(accountwideKey, null);
        StatsView.reconcileWithFlipHistory(summary, items, flipHistory);
        return new StatsSnapshot(summary, items);
    }

    void loadProfileSelectionState() {
        boolean migratedFromLegacy = selectionPersistence.load(profileSelection);
        if (migratedFromLegacy) {
            persistProfileSelectionState();
        }
    }

    void persistProfileSelectionState() {
        selectionPersistence.persist(profileSelection);
    }

    void updateProfileForLogin() {
        login.handleLogin(
            profileSelection,
            tradeSession.resolveAccountHash(),
            selectionPresentation.resolveDisplayName()
        );
        bookmarkState.loadSelectedBookmarks(
            selectionPresentation.resolveSelectedProfileKey(),
            bookmarkedItems
        );
    }

    void updateProfileOptionsUI() {
        ui.updateProfileOptionsUi();
    }

    void updateProfileHeader() {
        ui.updateProfileHeader();
    }

    void ensureSelectedProfileLoaded() {
        localTradesRuntime.ensureProfileLoaded(
            selectionPresentation.resolveSelectedProfileKey()
        );
    }

    void showManageDataError(String message) {
        Panel panel = Access.plugin().panel;
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
            mergeResult = accountMerge.merge(
                localTradeDeltasByAccount,
                localSessionStartByAccount,
                targetKey,
                sourceKey
            );
        }
        if (mergeResult != null && mergeResult.mergedSnapshot != null) {
            localStatsCacheService.rebuild(targetKey, mergeResult.mergedSnapshot);
        }
        statsCacheByAccount.remove(sourceKey);
        if (mergeResult != null && mergeResult.changed && targetKey > 0) {
            summaryUploader.markDirty();
            if (selectionPresentation.isLinked()) {
                uploadBackfillDispatch.requestAccountwideSync();
            }
        }
    }

    void primeOfferSnapshots() {
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
