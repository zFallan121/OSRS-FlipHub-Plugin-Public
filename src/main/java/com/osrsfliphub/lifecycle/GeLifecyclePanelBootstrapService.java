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

import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

final class GeLifecyclePanelBootstrapService {
    static final class UiState {
        private final FlipHubPanel panel;
        private final NavigationButton navButton;
        private final GeOfferTimerOverlay offerTimerOverlay;

        UiState(FlipHubPanel panel, NavigationButton navButton, GeOfferTimerOverlay offerTimerOverlay) {
            this.panel = panel;
            this.navButton = navButton;
            this.offerTimerOverlay = offerTimerOverlay;
        }

        FlipHubPanel getPanel() {
            return panel;
        }

        NavigationButton getNavButton() {
            return navButton;
        }

        GeOfferTimerOverlay getOfferTimerOverlay() {
            return offerTimerOverlay;
        }
    }

    UiState initialize(
        ItemManager itemManager,
        PluginConfig config,
        Client client,
        ClientToolbar clientToolbar,
        OverlayManager overlayManager,
        GeLifecyclePlugin plugin
    ) {
        FlipHubPanel panel = new FlipHubPanel(
            itemManager,
            new FlipHubPanelPluginListener(),
            PluginInjectorBridge.get(FlipHubPanelBookmarkStoreImpl.class),
            PluginInjectorBridge.get(FlipHubPanelHiddenItemStoreImpl.class),
            config
        );

        plugin.getProfileWorkflowService().updateProfileOptionsUI();
        plugin.getProfileWorkflowService().updateProfileHeader();

        BufferedImage icon = panel.buildNavIcon();
        NavigationButton navButton = NavigationButton.builder()
            .tooltip("FlipHub OSRS")
            .icon(icon)
            .panel(panel)
            .priority(6)
            .build();
        if (clientToolbar != null) {
            clientToolbar.addNavigation(navButton);
        }

        GeOfferTimerOverlay offerTimerOverlay = new GeOfferTimerOverlay(client, config, plugin);
        if (overlayManager != null) {
            overlayManager.add(offerTimerOverlay);
        }
        return new UiState(panel, navButton, offerTimerOverlay);
    }
}
