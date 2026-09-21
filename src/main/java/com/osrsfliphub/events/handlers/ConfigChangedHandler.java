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

import javax.inject.*;
import lombok.RequiredArgsConstructor;
import net.runelite.client.events.ConfigChanged;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class ConfigChangedHandler {
    private final PluginConfig config;
    private final PluginState state;
    private final LinkStatus linkStatus;
    private final LinkAttempt linkAttempt;
    private final UploadEventDispatch uploadEventDispatch;
    private final BookmarkState bookmarkState;
    private final ProfileSelectionPresentation profileSelectionPresentation;
    private final ProfileWorkflow profileWorkflow;

    private void refreshBookmarksUi() {
        Panel panel = Access.plugin().panel;
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

        if ("enableFlipHubSync".equals(key)) {
            if (config.enableFlipHubSync()) {
                linkAttempt.attemptLink(config.licenseKey());
                uploadEventDispatch.resetStatus();
            } else {
                uploadEventDispatch.markBlocked("FlipHub sync is disabled in the plugin settings.");
            }
            linkStatus.refresh();
            if (Access.plugin().panel != null) {
                profileWorkflow.updateProfileHeader();
            }
            GeLifecyclePlugin plugin = Access.plugin();
            plugin.refreshPanelData();
            PanelRefresh coordinator = plugin.getPanelRefreshCoordinator();
            if (coordinator != null) {
                coordinator.triggerStatsRefresh(plugin.scheduler);
            }
        }

        if ("licenseKey".equals(key)) {
            linkAttempt.attemptLink(config.licenseKey());
        }

        if (bookmarkState.isBookmarksConfigKey(key)) {
            bookmarkState.reloadFromConfigKey(key);
            bookmarkState.loadSelectedBookmarks(
                profileSelectionPresentation.resolveSelectedProfileKey(), state.getBookmarkedItems());
            if (Access.plugin().panel != null) {
                refreshBookmarksUi();
            }
        }

        if (state.getHiddenItemConfigStore().isHiddenItemsConfigKey(key)) {
            state.getHiddenItems().clear();
            state.getHiddenItems().addAll(
                state.getHiddenItemConfigStore().parseItemIds(config.hiddenItems()));
            if (Access.plugin().panel != null) {
                refreshBookmarksUi();
            }
        }
    }
}
