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

final class FlipHubPanelPluginListener implements FlipHubPanelListener {
    private static GeLifecyclePlugin plugin() {
        return PluginAccess.plugin();
    }

    private static GeLifecycleProfileWorkflowService workflow() {
        return plugin().getProfileWorkflowService();
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
        PluginState state = PluginInjectorBridge.get(PluginState.class);
        if (state != null) {
            state.getProfileSelection().selectManual(profileKey);
        }
        workflow().persistProfileSelectionState();

        ProfileSelectionPresentationFacadeService profileSelectionService =
            PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class);
        long selectedProfileKey = profileSelectionService != null
            ? profileSelectionService.resolveSelectedProfileKey()
            : -1L;
        if (selectedProfileKey > 0) {
            plugin.getLocalTradesRuntimeService().ensureProfileLoaded(selectedProfileKey);
        }

        BookmarkStateService bookmarkStateService = PluginInjectorBridge.get(BookmarkStateService.class);
        if (bookmarkStateService != null && profileSelectionService != null && plugin.bookmarkedItems != null) {
            bookmarkStateService.loadSelectedBookmarks(selectedProfileKey, plugin.bookmarkedItems);
        }

        workflow().updateProfileOptionsUI();
        workflow().updateProfileHeader();
        plugin.runtimeUtilityServices.triggerPanelRefresh(plugin.getPanelRefreshCoordinator(), plugin.scheduler);
        plugin.runtimeUtilityServices.triggerStatsRefresh(plugin.getPanelRefreshCoordinator(), plugin.scheduler);
    }

    @Override
    public void onManageData() {
        ManageDataDialogService service = PluginInjectorBridge.get(ManageDataDialogService.class);
        if (service != null) {
            service.showManageDataDialog();
        }
    }
}
