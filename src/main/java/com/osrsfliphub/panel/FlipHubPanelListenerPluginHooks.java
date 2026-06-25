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

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

final class FlipHubPanelListenerPluginHooks implements FlipHubPanelListener {
    private final Consumer<String> setCurrentQuery;
    private final IntConsumer setCurrentPage;
    private final Consumer<Boolean> setBookmarkFilterEnabled;
    private final Consumer<StatsRange> setCurrentStatsRange;
    private final Consumer<StatsItemSort> setCurrentStatsSort;
    private final Runnable refreshPanelData;
    private final Runnable refreshStatsData;
    private final ProfileSelectionState profileSelection;
    private final Runnable persistProfileSelectionState;
    private final Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier;
    private final Supplier<BookmarkStateService> bookmarkStateServiceSupplier;
    private final Set<Integer> bookmarkedItems;
    private final LongConsumer ensureProfileLoaded;
    private final Runnable updateProfileOptionsUi;
    private final Runnable updateProfileHeader;
    private final Runnable triggerPanelRefresh;
    private final Runnable triggerStatsRefresh;
    private final Runnable showManageDataDialog;

    FlipHubPanelListenerPluginHooks(
        Consumer<String> setCurrentQuery,
        IntConsumer setCurrentPage,
        Consumer<Boolean> setBookmarkFilterEnabled,
        Consumer<StatsRange> setCurrentStatsRange,
        Consumer<StatsItemSort> setCurrentStatsSort,
        Runnable refreshPanelData,
        Runnable refreshStatsData,
        ProfileSelectionState profileSelection,
        Runnable persistProfileSelectionState,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier,
        Supplier<BookmarkStateService> bookmarkStateServiceSupplier,
        Set<Integer> bookmarkedItems,
        LongConsumer ensureProfileLoaded,
        Runnable updateProfileOptionsUi,
        Runnable updateProfileHeader,
        Runnable triggerPanelRefresh,
        Runnable triggerStatsRefresh,
        Runnable showManageDataDialog
    ) {
        this.setCurrentQuery = setCurrentQuery;
        this.setCurrentPage = setCurrentPage;
        this.setBookmarkFilterEnabled = setBookmarkFilterEnabled;
        this.setCurrentStatsRange = setCurrentStatsRange;
        this.setCurrentStatsSort = setCurrentStatsSort;
        this.refreshPanelData = refreshPanelData;
        this.refreshStatsData = refreshStatsData;
        this.profileSelection = profileSelection;
        this.persistProfileSelectionState = persistProfileSelectionState;
        this.profileSelectionFacadeServiceSupplier = profileSelectionFacadeServiceSupplier;
        this.bookmarkStateServiceSupplier = bookmarkStateServiceSupplier;
        this.bookmarkedItems = bookmarkedItems;
        this.ensureProfileLoaded = ensureProfileLoaded;
        this.updateProfileOptionsUi = updateProfileOptionsUi;
        this.updateProfileHeader = updateProfileHeader;
        this.triggerPanelRefresh = triggerPanelRefresh;
        this.triggerStatsRefresh = triggerStatsRefresh;
        this.showManageDataDialog = showManageDataDialog;
    }

    @Override
    public void onSearchChanged(String query) {
        if (setCurrentQuery != null) {
            setCurrentQuery.accept(query == null ? "" : query);
        }
        if (setCurrentPage != null) {
            setCurrentPage.accept(1);
        }
        if (refreshPanelData != null) {
            refreshPanelData.run();
        }
    }

    @Override
    public void onPageChanged(int page) {
        if (setCurrentPage != null) {
            setCurrentPage.accept(Math.max(1, page));
        }
        if (refreshPanelData != null) {
            refreshPanelData.run();
        }
    }

    @Override
    public void onBookmarkFilterChanged(boolean enabled) {
        if (setBookmarkFilterEnabled != null) {
            setBookmarkFilterEnabled.accept(enabled);
        }
        if (setCurrentPage != null) {
            setCurrentPage.accept(1);
        }
        if (refreshPanelData != null) {
            refreshPanelData.run();
        }
    }

    @Override
    public void onStatsRangeChanged(StatsRange range) {
        if (setCurrentStatsRange != null) {
            setCurrentStatsRange.accept(range != null ? range : StatsRange.SESSION);
        }
        if (refreshStatsData != null) {
            refreshStatsData.run();
        }
    }

    @Override
    public void onStatsSortChanged(StatsItemSort sort) {
        if (setCurrentStatsSort != null) {
            setCurrentStatsSort.accept(sort != null ? sort : StatsItemSort.COMPLETION);
        }
        if (refreshStatsData != null) {
            refreshStatsData.run();
        }
    }

    @Override
    public void onProfileSelected(String profileKey) {
        if (profileKey == null || profileKey.trim().isEmpty()) {
            return;
        }
        if (profileSelection != null) {
            profileSelection.selectManual(profileKey);
        }
        if (persistProfileSelectionState != null) {
            persistProfileSelectionState.run();
        }

        ProfileSelectionPresentationFacadeService profileSelectionService = resolveProfileSelectionService();
        long selectedProfileKey = profileSelectionService != null
            ? profileSelectionService.resolveSelectedProfileKey()
            : -1L;
        if (selectedProfileKey > 0 && ensureProfileLoaded != null) {
            ensureProfileLoaded.accept(selectedProfileKey);
        }

        BookmarkStateService bookmarkStateService = resolveBookmarkStateService();
        if (bookmarkStateService != null && profileSelectionService != null && bookmarkedItems != null) {
            bookmarkStateService.loadSelectedBookmarks(selectedProfileKey, bookmarkedItems);
        }

        if (updateProfileOptionsUi != null) {
            updateProfileOptionsUi.run();
        }
        if (updateProfileHeader != null) {
            updateProfileHeader.run();
        }
        if (triggerPanelRefresh != null) {
            triggerPanelRefresh.run();
        }
        if (triggerStatsRefresh != null) {
            triggerStatsRefresh.run();
        }
    }

    @Override
    public void onManageData() {
        if (showManageDataDialog != null) {
            showManageDataDialog.run();
        }
    }

    private ProfileSelectionPresentationFacadeService resolveProfileSelectionService() {
        return profileSelectionFacadeServiceSupplier != null
            ? profileSelectionFacadeServiceSupplier.get()
            : null;
    }

    private BookmarkStateService resolveBookmarkStateService() {
        return bookmarkStateServiceSupplier != null ? bookmarkStateServiceSupplier.get() : null;
    }
}

