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

import net.runelite.client.events.ConfigChanged;

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

    ConfigChangedHandlerService(Hooks hooks) {
        this.hooks = hooks;
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
