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
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

@Singleton
final class FlipHubPanelHiddenItemStoreImpl implements FlipHubPanelHiddenItemStore {
    private final Set<Integer> hiddenItems;
    private final HiddenItemConfigStore hiddenItemConfigStore;
    private final ConfigManager configManager;

    @Inject
    FlipHubPanelHiddenItemStoreImpl(PluginState pluginState, ConfigManager configManager) {
        this.hiddenItems = pluginState.getHiddenItems();
        this.hiddenItemConfigStore = pluginState.getHiddenItemConfigStore();
        this.configManager = configManager;
    }

    @Override
    public boolean isHidden(int itemId) {
        return hiddenItems != null && hiddenItems.contains(itemId);
    }

    @Override
    public void hideItem(int itemId) {
        if (itemId <= 0 || hiddenItems == null || hiddenItemConfigStore == null) {
            return;
        }
        if (!hiddenItems.add(itemId)) {
            return;
        }
        if (configManager != null) {
            String value = hiddenItemConfigStore.serializeItemIds(hiddenItems);
            configManager.setConfiguration(
                FliphubConfigGroups.CONFIG_GROUP, hiddenItemConfigStore.configKey(), value);
        }
        FlipHubPanel panel = PluginAccess.plugin().panel;
        if (panel != null) {
            panel.refreshBookmarks();
        }
    }
}
