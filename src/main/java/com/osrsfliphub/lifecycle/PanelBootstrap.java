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
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.*;
import net.runelite.client.ui.overlay.OverlayManager;

final class PanelBootstrap {
    static final class UiState {
        @Getter
        private final Panel panel;
        @Getter
        private final NavigationButton navButton;
        @Getter
        private final GeOfferTimerOverlay offerTimerOverlay;

        UiState(Panel panel, NavigationButton navButton, GeOfferTimerOverlay offerTimerOverlay) {
            this.panel = panel;
            this.navButton = navButton;
            this.offerTimerOverlay = offerTimerOverlay;
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
        Panel panel = new Panel(
            itemManager,
            new PanelPluginListener(),
            Bridge.get(PanelBookmarkStoreImpl.class),
            Bridge.get(PanelHiddenItemStoreImpl.class),
            config
        );

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
