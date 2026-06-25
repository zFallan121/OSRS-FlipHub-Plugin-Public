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
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
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
        ConfigManager configManager,
        Client client,
        ClientToolbar clientToolbar,
        OverlayManager overlayManager,
        GeLifecyclePlugin plugin,
        ProfileSelectionState profileSelection,
        Set<Integer> bookmarkedItems,
        Set<Integer> hiddenItems,
        HiddenItemConfigStore hiddenItemConfigStore,
        Consumer<String> querySetter,
        IntConsumer pageSetter,
        Consumer<Boolean> bookmarkFilterSetter,
        Consumer<StatsRange> statsRangeSetter,
        Consumer<StatsItemSort> statsSortSetter,
        Runnable refreshPanelData,
        Runnable refreshStatsData,
        Runnable persistProfileSelectionState,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionPresentationFacadeServiceSupplier,
        Supplier<BookmarkStateService> bookmarkStateServiceSupplier,
        LongConsumer ensureProfileLoaded,
        Runnable updateProfileOptionsUi,
        Runnable updateProfileHeader,
        Runnable triggerPanelRefresh,
        Runnable triggerStatsRefresh,
        Runnable showManageDataDialog
    ) {
        final FlipHubPanel[] panelRef = new FlipHubPanel[1];
        FlipHubPanel panel = new FlipHubPanel(
            itemManager,
            new FlipHubPanelListenerPluginHooks(
                query -> {
                    if (querySetter != null) {
                        querySetter.accept(query);
                    }
                },
                page -> {
                    if (pageSetter != null) {
                        pageSetter.accept(page);
                    }
                },
                enabled -> {
                    if (bookmarkFilterSetter != null) {
                        bookmarkFilterSetter.accept(enabled);
                    }
                },
                range -> {
                    if (statsRangeSetter != null) {
                        statsRangeSetter.accept(range);
                    }
                },
                sort -> {
                    if (statsSortSetter != null) {
                        statsSortSetter.accept(sort);
                    }
                },
                refreshPanelData,
                refreshStatsData,
                profileSelection,
                persistProfileSelectionState,
                profileSelectionPresentationFacadeServiceSupplier,
                bookmarkStateServiceSupplier,
                bookmarkedItems,
                ensureProfileLoaded,
                updateProfileOptionsUi,
                updateProfileHeader,
                triggerPanelRefresh,
                triggerStatsRefresh,
                showManageDataDialog
            ),
            new FlipHubPanelBookmarkStorePluginHooks(
                bookmarkedItems,
                bookmarkStateServiceSupplier,
                profileSelectionPresentationFacadeServiceSupplier,
                () -> panelRef[0]
            ),
            new FlipHubPanelHiddenItemStorePluginHooks(
                hiddenItems,
                hiddenItemConfigStore,
                () -> configManager,
                () -> panelRef[0],
                FliphubConfigGroups.CONFIG_GROUP
            ),
            config
        );
        panelRef[0] = panel;

        if (updateProfileOptionsUi != null) {
            updateProfileOptionsUi.run();
        }
        if (updateProfileHeader != null) {
            updateProfileHeader.run();
        }

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
