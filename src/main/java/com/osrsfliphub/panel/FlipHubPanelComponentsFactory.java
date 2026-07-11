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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.swing.ImageIcon;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import net.runelite.client.game.ItemManager;

final class FlipHubPanelComponentsFactory {
    FlipHubPanelComponentBundle create(
        ItemManager itemManager,
        FlipHubPanelListener listener,
        FlipHubPanelBookmarkStore bookmarkStore,
        FlipHubPanelHiddenItemStore hiddenItemStore,
        java.awt.Component hostComponent,
        javax.swing.JButton profileButton,
        FlipHubUiStyler uiStyler,
        FlipHubPanelValueFormatService valueFormatService,
        FlipHubExternalLinkCoordinator externalLinkCoordinator,
        FlipHubPanelStateService panelStateService,
        FlipHubPanelMutableState panelState,
        FlipHubSearchCoordinator searchCoordinator,
        JTextField searchField,
        JToggleButton statsTab,
        JScrollPane scrollPane,
        Supplier<JScrollPane> statsScrollPaneSupplier,
        Map<Integer, ImageIcon> iconCache,
        Set<Integer> expandedStatsHistoryItems,
        Supplier<Integer> expandedStatsItemIdSupplier,
        Runnable renderItems,
        Runnable renderStatsItems,
        Runnable updateStatsSummary,
        IntConsumer toggleStatsItemExpanded,
        IntConsumer toggleStatsHistoryExpanded
    ) {
        FlipHubItemIconResolver itemIconResolver = new FlipHubItemIconResolver(itemManager, iconCache);
        FlipHubWheelScrollCoordinator wheelScrollCoordinator = new FlipHubWheelScrollCoordinator(
            () -> statsTab.isSelected() ? statsScrollPaneSupplier.get() : scrollPane,
            hostComponent
        );
        java.awt.event.MouseWheelListener wheelForwarder = wheelScrollCoordinator.wheelForwarder();
        FlipHubProfileMenuCoordinator profileMenuCoordinator = new FlipHubProfileMenuCoordinator(
            profileButton,
            new FlipHubProfileMenuPluginHooks(
                uiStyler::font,
                uiStyler::fontSemiBold,
                key -> {
                    if (listener != null) {
                        listener.onProfileSelected(key);
                    }
                },
                () -> {
                    if (listener != null) {
                        listener.onManageData();
                    }
                }
            )
        );

        FlipHubAgeTooltipCoordinator ageTooltipCoordinator = new FlipHubAgeTooltipCoordinator(valueFormatService);

        FlipHubItemCardBuilder itemCardBuilder = new FlipHubItemCardBuilder(
            valueFormatService,
            uiStyler,
            itemIconResolver,
            externalLinkCoordinator,
            bookmarkStore,
            hiddenItemStore,
            panelState,
            renderItems,
            ageTooltipCoordinator,
            wheelScrollCoordinator
        );

        FlipHubItemListContentRenderer itemListContentRenderer = new FlipHubItemListContentRenderer(
            new FlipHubItemListContentRendererPluginHooks(
                uiStyler::font,
                uiStyler::fontSemiBold,
                itemId -> hiddenItemStore != null && hiddenItemStore.isHidden(itemId),
                itemId -> bookmarkStore != null && bookmarkStore.isBookmarked(itemId),
                itemCardBuilder::buildItemCard
            )
        );

        FlipHubFlippingPanelBuilder flippingPanelBuilder = new FlipHubFlippingPanelBuilder(
            new FlipHubFlippingPanelBuilderPluginHooks(
                uiStyler::font,
                uiStyler::fontSemiBold,
                uiStyler::fontSymbol,
                uiStyler::roundedBorder,
                uiStyler::styleTextField,
                enabled -> panelStateService.onBookmarkFilterChanged(panelState, enabled, listener, renderItems),
                () -> panelStateService.onPrevPageRequested(panelState, listener),
                () -> panelStateService.onNextPageRequested(panelState, listener),
                () -> panelStateService.hookSearchListener(searchCoordinator, searchField, listener),
                () -> wheelForwarder
            )
        );

        FlipHubStatsPanelBuilder statsPanelBuilder = new FlipHubStatsPanelBuilder(
            new FlipHubStatsPanelBuilderPluginHooks(
                uiStyler::font,
                uiStyler::fontBold,
                uiStyler::fontSemiBold,
                uiStyler::roundedBorder,
                uiStyler::styleComboBox,
                uiStyler::styleTextField,
                wheelScrollCoordinator::installWheelForwarder,
                () -> wheelForwarder,
                () -> panelState.statsSortAscending,
                range -> panelStateService.onStatsRangeSelectionChanged(listener, range),
                sort -> panelStateService.onStatsSortSelectionChanged(listener, sort, renderStatsItems),
                () -> panelStateService.onStatsSortDirectionToggled(panelState, renderStatsItems),
                query -> panelStateService.onStatsSearchQueryChanged(panelState, query, renderStatsItems),
                renderStatsItems,
                updateStatsSummary
            )
        );

        FlipHubStatsItemCardBuilder statsItemCardBuilder = new FlipHubStatsItemCardBuilder(
            valueFormatService,
            uiStyler,
            itemIconResolver,
            expandedStatsItemIdSupplier,
            expandedStatsHistoryItems,
            panelState,
            toggleStatsItemExpanded,
            toggleStatsHistoryExpanded
        );

        return new FlipHubPanelComponentBundle(
            itemIconResolver,
            wheelScrollCoordinator,
            wheelForwarder,
            profileMenuCoordinator,
            ageTooltipCoordinator,
            itemCardBuilder,
            itemListContentRenderer,
            flippingPanelBuilder,
            statsPanelBuilder,
            statsItemCardBuilder
        );
    }
}

