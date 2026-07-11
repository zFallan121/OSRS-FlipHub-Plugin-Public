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
    private final PluginConfig config;
    private final PluginState state;

    @Inject
    ConfigChangedHandlerService(PluginConfig config, PluginState state) {
        this.config = config;
        this.state = state;
    }

    private boolean isPanelAvailable() {
        return PluginAccess.plugin().panel != null;
    }

    private void refreshBookmarksUi() {
        FlipHubPanel panel = PluginAccess.plugin().panel;
        if (panel != null) {
            panel.refreshBookmarks();
        }
    }

    void handle(ConfigChanged event) {
        if (event == null) {
            return;
        }
        if (!FliphubConfigGroups.CONFIG_GROUP.equals(event.getGroup())) {
            return;
        }
        String key = event.getKey();

        if ("licenseKey".equals(key) || "linkCode".equals(key)) {
            LinkAttemptService linkAttemptService = PluginInjectorBridge.get(LinkAttemptService.class);
            String linkInput = linkAttemptService != null && config != null
                ? linkAttemptService.resolveLinkInput(config.licenseKey(), config.linkCode())
                : null;
            if (linkInput != null && !linkInput.trim().isEmpty()) {
                linkAttemptService.attemptLink(linkInput.trim());
            }
        }

        if ("unlinkNow".equals(key)) {
            if (config == null || !config.unlinkNow()) {
                return;
            }
            PluginInjectorBridge.get(LinkSessionConfigStore.class).clearLinkState();
            AccountwideSummaryUploader uploader = PluginInjectorBridge.get(AccountwideSummaryUploader.class);
            if (uploader != null) {
                uploader.resetUploadSnapshot();
            }
            UploadEventDispatchFacadeService uploadFacade =
                PluginInjectorBridge.get(UploadEventDispatchFacadeService.class);
            if (uploadFacade != null) {
                uploadFacade.markBlocked("Unlinked. Event uploads paused until relinked.");
            }
            if (isPanelAvailable()) {
                PluginAccess.plugin().getProfileWorkflowService().updateProfileHeader();
            }
            GeLifecyclePlugin plugin = PluginAccess.plugin();
            PanelRefreshCoordinator coordinator = plugin.getPanelRefreshCoordinator();
            if (coordinator != null) {
                coordinator.triggerStatsRefresh(plugin.scheduler);
            }
        }

        BookmarkStateService bookmarkStateService = PluginInjectorBridge.get(BookmarkStateService.class);
        if (bookmarkStateService != null && bookmarkStateService.isBookmarksConfigKey(key)) {
            bookmarkStateService.reloadFromConfigKey(key);
            ProfileSelectionPresentationFacadeService profileSelectionService =
                PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class);
            if (profileSelectionService != null) {
                bookmarkStateService.loadSelectedBookmarks(
                    profileSelectionService.resolveSelectedProfileKey(), state.getBookmarkedItems());
            }
            if (isPanelAvailable()) {
                refreshBookmarksUi();
            }
        }

        if (state.getHiddenItemConfigStore().isHiddenItemsConfigKey(key)) {
            if (config != null) {
                state.getHiddenItems().clear();
                state.getHiddenItems().addAll(
                    state.getHiddenItemConfigStore().parseItemIds(config.hiddenItems()));
            }
            if (isPanelAvailable()) {
                refreshBookmarksUi();
            }
        }
    }
}
