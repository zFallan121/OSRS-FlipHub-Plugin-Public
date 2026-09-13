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

import java.util.function.Predicate;
import net.runelite.client.config.ConfigManager;

final class PanelPluginListener implements PanelListener {
    private static GeLifecyclePlugin plugin() {
        return Access.plugin();
    }

    private static ProfileWorkflow workflow() {
        return plugin().getProfileWorkflowService();
    }

    @Override
    public void onLinkSubmitted(String licenseKey) {
        LinkAttempt linkService = Bridge.get(LinkAttempt.class);
        if (linkService != null) {
            linkService.linkFromPanel(licenseKey);
        }
    }

    @Override
    public void onUnlinkRequested() {
        LinkAttempt linkService = Bridge.get(LinkAttempt.class);
        if (linkService != null) {
            linkService.unlinkFromPanel();
        }
    }

    @Override
    public void onSearchChanged(String query) {
        GeLifecyclePlugin plugin = plugin();
        plugin.currentQuery = query == null ? "" : query;
        plugin.currentPage = 1;
        plugin.refreshPanelData();
    }

    @Override
    public void onPageChanged(int page) {
        GeLifecyclePlugin plugin = plugin();
        plugin.currentPage = Math.max(1, page);
        plugin.refreshPanelData();
    }

    @Override
    public void onBookmarkFilterChanged(boolean enabled) {
        GeLifecyclePlugin plugin = plugin();
        plugin.bookmarkFilterEnabled = enabled;
        plugin.currentPage = 1;
        plugin.refreshPanelData();
    }

    @Override
    public void onItemSortChanged(StatsItemSort sort, boolean ascending) {
        GeLifecyclePlugin plugin = plugin();
        plugin.currentItemSort = sort != null ? sort : StatsItemSort.COMPLETION;
        plugin.currentItemSortAscending = ascending;
        plugin.currentPage = 1;
        ConfigManager configManager = Bridge.get(ConfigManager.class);
        if (configManager != null) {
            configManager.setConfiguration(
                FliphubConfigGroups.CONFIG_GROUP, "itemSort", plugin.currentItemSort.name());
            configManager.setConfiguration(
                FliphubConfigGroups.CONFIG_GROUP, "itemSortAscending", ascending);
        }
        plugin.refreshPanelData();
    }

    @Override
    public void onStatsRangeChanged(StatsRange range) {
        GeLifecyclePlugin plugin = plugin();
        plugin.currentStatsRange = range != null ? range : StatsRange.SESSION;
        plugin.refreshStatsData();
    }

    @Override
    public void onStatsSortChanged(StatsItemSort sort) {
        GeLifecyclePlugin plugin = plugin();
        plugin.currentStatsSort = sort != null ? sort : StatsItemSort.COMPLETION;
        plugin.refreshStatsData();
    }

    @Override
    public void onProfileSelected(String profileKey) {
        if (profileKey == null || profileKey.trim().isEmpty()) {
            return;
        }
        GeLifecyclePlugin plugin = plugin();
        PluginState state = Bridge.get(PluginState.class);
        if (state != null) {
            state.getProfileSelection().selectManual(profileKey);
        }
        workflow().persistProfileSelectionState();

        ProfileSelectionPresentation profileSelectionService =
            Bridge.get(ProfileSelectionPresentation.class);
        long selectedProfileKey = profileSelectionService != null
            ? profileSelectionService.resolveSelectedProfileKey()
            : -1L;
        // Not loaded here: this runs on the thread drawing the panel, and reading and parsing
        // the profile file freezes it. The refresh triggered at the end of this method loads
        // the selected profile on the scheduler anyway.

        BookmarkState bookmarkStateService = Bridge.get(BookmarkState.class);
        if (bookmarkStateService != null && profileSelectionService != null && state != null) {
            bookmarkStateService.loadSelectedBookmarks(selectedProfileKey, state.getBookmarkedItems());
        }

        workflow().updateProfileOptionsUI();
        workflow().updateProfileHeader();
        plugin.runtimeUtilityServices.triggerPanelRefresh(plugin.getPanelRefreshCoordinator(), plugin.scheduler);
        plugin.runtimeUtilityServices.triggerStatsRefresh(plugin.getPanelRefreshCoordinator(), plugin.scheduler);
    }

    @Override
    public void onManageData() {
        ManageDataDialog service = Bridge.get(ManageDataDialog.class);
        if (service != null) {
            service.showManageDataDialog();
        }
    }

    @Override
    public void onConversionRejected(StatsFlipInstance instance) {
        ConversionRejection rejection = ConversionRejection.of(instance, System.currentTimeMillis());
        if (rejection == null) {
            return;
        }
        correctConversion(instance.accountKey, store -> store.add(instance.accountKey, rejection));
    }

    @Override
    public void onConversionRestored(StatsFlipInstance instance) {
        if (instance == null || instance.dismissed == null) {
            return;
        }
        correctConversion(instance.accountKey, store -> store.remove(instance.accountKey, instance.dismissed));
    }

    /**
     * Both ledgers are pure functions over the stored trades and the stored
     * corrections, so correcting a guess is: change the stored fact, write it
     * beside the trades it names, throw the aggregates away, and redraw. The
     * accountwide summary the site gets is built from the reconciled snapshot,
     * so marking it dirty is enough for it to follow.
     */
    private static void correctConversion(long accountKey, Predicate<RejectionStore> change) {
        RejectionStore store = Bridge.get(RejectionStore.class);
        if (store == null || !change.test(store)) {
            return;
        }
        GeLifecyclePlugin plugin = plugin();
        plugin.getLocalTradesRuntimeService().persistLocalTrades(accountKey);
        LocalStatsCacheService statsCache = Bridge.get(LocalStatsCacheService.class);
        if (statsCache != null) {
            statsCache.invalidateAll();
        }
        plugin.markAccountwideUploadDirty();
        plugin.refreshStatsData();
    }
}
