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

import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.swing.ImageIcon;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import net.runelite.client.game.ItemManager;

final class PanelComponentsFactory {
    PanelComponentBundle create(
        ItemManager itemManager,
        PanelListener listener,
        PanelBookmarkStore bookmarkStore,
        PanelHiddenItemStore hiddenItemStore,
        java.awt.Component hostComponent,
        javax.swing.JButton profileButton,
        UiStyler uiStyler,
        PanelValueFormat valueFormatService,
        ExternalLink externalLinkCoordinator,
        PanelState panelStateService,
        PanelMutableState panelState,
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
        ItemIconResolver itemIconResolver = new ItemIconResolver(itemManager, iconCache);
        WheelScroll wheelScrollCoordinator = new WheelScroll(
            () -> statsTab.isSelected() ? statsScrollPaneSupplier.get() : scrollPane,
            hostComponent
        );
        java.awt.event.MouseWheelListener wheelForwarder = wheelScrollCoordinator.wheelForwarder();
        ProfileMenu profileMenuCoordinator =
            new ProfileMenu(profileButton, uiStyler, listener);

        AgeTooltip ageTooltipCoordinator = new AgeTooltip(valueFormatService);

        ItemCardBuilder itemCardBuilder = new ItemCardBuilder(
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

        ItemListContentRenderer itemListContentRenderer = new ItemListContentRenderer(
            uiStyler,
            hiddenItemStore,
            bookmarkStore,
            itemCardBuilder,
            ageTooltipCoordinator
        );

        FlippingPanelBuilder flippingPanelBuilder = new FlippingPanelBuilder(
            uiStyler,
            panelStateService,
            panelState,
            listener,
            renderItems,
            searchCoordinator,
            wheelForwarder
        );

        StatsPanelBuilder statsPanelBuilder = new StatsPanelBuilder(
            uiStyler,
            valueFormatService,
            panelStateService,
            panelState,
            listener,
            renderStatsItems,
            updateStatsSummary,
            wheelScrollCoordinator,
            wheelForwarder
        );

        StatsItemCardBuilder statsItemCardBuilder = new StatsItemCardBuilder(
            valueFormatService,
            uiStyler,
            itemIconResolver,
            expandedStatsItemIdSupplier,
            expandedStatsHistoryItems,
            panelState,
            toggleStatsItemExpanded,
            toggleStatsHistoryExpanded
        );

        return new PanelComponentBundle(
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

