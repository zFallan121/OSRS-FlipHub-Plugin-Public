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

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.events.ConfigChanged;

@Singleton
final class ConfigChangedHandlerService {
    interface Hooks {
        String configGroup();
        boolean isLinkInputConfigKey(String key);
        String getLinkInput();
        void attemptLink(String linkInput);
        boolean isUnlinkConfigKey(String key);
        boolean unlinkRequested();
        void clearLinkState();
        void resetUploadSnapshot();
        void setUploadBlocked(String message);
        boolean isPanelAvailable();
        void updateProfileHeader();
        void triggerStatsRefresh();
        boolean isBookmarksConfigKey(String key);
        void reloadBookmarkState(String key);
        void loadBookmarks();
        void refreshBookmarksUi();
        boolean isHiddenItemsConfigKey(String key);
        void loadHiddenItems();
    }

    private final Hooks hooks;

    @Inject
    ConfigChangedHandlerService(PluginConfig config, PluginState state) {
        this(productionHooks(config, state));
    }

    ConfigChangedHandlerService(Hooks hooks) {
        this.hooks = hooks;
    }

    private static Hooks productionHooks(PluginConfig config, PluginState state) {
        return new Hooks() {
            @Override
            public String configGroup() {
                return FliphubConfigGroups.CONFIG_GROUP;
            }

            @Override
            public boolean isLinkInputConfigKey(String key) {
                return "licenseKey".equals(key) || "linkCode".equals(key);
            }

            @Override
            public String getLinkInput() {
                LinkAttemptService linkAttemptService = PluginInjectorBridge.get(LinkAttemptService.class);
                if (linkAttemptService == null || config == null) {
                    return null;
                }
                return linkAttemptService.resolveLinkInput(config.licenseKey(), config.linkCode());
            }

            @Override
            public void attemptLink(String linkInput) {
                PluginInjectorBridge.get(LinkAttemptService.class).attemptLink(linkInput);
            }

            @Override
            public boolean isUnlinkConfigKey(String key) {
                return "unlinkNow".equals(key);
            }

            @Override
            public boolean unlinkRequested() {
                return config != null && config.unlinkNow();
            }

            @Override
            public void clearLinkState() {
                PluginInjectorBridge.get(LinkSessionConfigStore.class).clearLinkState();
            }

            @Override
            public void resetUploadSnapshot() {
                AccountwideSummaryUploader uploader =
                    PluginAccess.plugin().getBackfillServices().getAccountwideSummaryUploader();
                if (uploader != null) {
                    uploader.resetUploadSnapshot();
                }
            }

            @Override
            public void setUploadBlocked(String message) {
                UploadEventDispatchFacadeService service =
                    PluginAccess.plugin().getUploadRuntimeServices().getUploadEventDispatchFacadeService();
                if (service != null) {
                    service.markBlocked(message);
                }
            }

            @Override
            public boolean isPanelAvailable() {
                return PluginAccess.plugin().panel != null;
            }

            @Override
            public void updateProfileHeader() {
                PluginAccess.plugin().getProfileWorkflowService().updateProfileHeader();
            }

            @Override
            public void triggerStatsRefresh() {
                GeLifecyclePlugin plugin = PluginAccess.plugin();
                PanelRefreshCoordinator coordinator = plugin.getPanelRefreshCoordinator();
                if (coordinator != null) {
                    coordinator.triggerStatsRefresh(plugin.scheduler);
                }
            }

            @Override
            public boolean isBookmarksConfigKey(String key) {
                BookmarkStateService bookmarkStateService = PluginInjectorBridge.get(BookmarkStateService.class);
                return bookmarkStateService != null && bookmarkStateService.isBookmarksConfigKey(key);
            }

            @Override
            public void reloadBookmarkState(String key) {
                PluginInjectorBridge.get(BookmarkStateService.class).reloadFromConfigKey(key);
            }

            @Override
            public void loadBookmarks() {
                BookmarkStateService bookmarkStateService = PluginInjectorBridge.get(BookmarkStateService.class);
                ProfileSelectionPresentationFacadeService profileSelectionService = PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class);
                if (bookmarkStateService == null || profileSelectionService == null) {
                    return;
                }
                bookmarkStateService.loadSelectedBookmarks(
                    profileSelectionService.resolveSelectedProfileKey(), state.getBookmarkedItems());
            }

            @Override
            public void refreshBookmarksUi() {
                FlipHubPanel panel = PluginAccess.plugin().panel;
                if (panel != null) {
                    panel.refreshBookmarks();
                }
            }

            @Override
            public boolean isHiddenItemsConfigKey(String key) {
                return state.getHiddenItemConfigStore().isHiddenItemsConfigKey(key);
            }

            @Override
            public void loadHiddenItems() {
                if (config == null) {
                    return;
                }
                state.getHiddenItems().clear();
                state.getHiddenItems().addAll(
                    state.getHiddenItemConfigStore().parseItemIds(config.hiddenItems()));
            }
        };
    }

    void handle(ConfigChanged event) {
        if (hooks == null || event == null) {
            return;
        }
        if (!hooks.configGroup().equals(event.getGroup())) {
            return;
        }
        String key = event.getKey();

        if (hooks.isLinkInputConfigKey(key)) {
            String linkInput = hooks.getLinkInput();
            if (linkInput != null && !linkInput.trim().isEmpty()) {
                hooks.attemptLink(linkInput.trim());
            }
        }

        if (hooks.isUnlinkConfigKey(key)) {
            if (!hooks.unlinkRequested()) {
                return;
            }
            hooks.clearLinkState();
            hooks.resetUploadSnapshot();
            hooks.setUploadBlocked("Unlinked. Event uploads paused until relinked.");
            if (hooks.isPanelAvailable()) {
                hooks.updateProfileHeader();
            }
            hooks.triggerStatsRefresh();
        }

        if (hooks.isBookmarksConfigKey(key)) {
            hooks.reloadBookmarkState(key);
            hooks.loadBookmarks();
            if (hooks.isPanelAvailable()) {
                hooks.refreshBookmarksUi();
            }
        }

        if (hooks.isHiddenItemsConfigKey(key)) {
            hooks.loadHiddenItems();
            if (hooks.isPanelAvailable()) {
                hooks.refreshBookmarksUi();
            }
        }
    }
}
