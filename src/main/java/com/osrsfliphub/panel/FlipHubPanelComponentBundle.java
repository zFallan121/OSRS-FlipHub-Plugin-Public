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

import java.awt.event.MouseWheelListener;

final class FlipHubPanelComponentBundle {
    private final FlipHubItemIconResolver itemIconResolver;
    private final FlipHubWheelScrollCoordinator wheelScrollCoordinator;
    private final MouseWheelListener wheelForwarder;
    private final FlipHubProfileMenuCoordinator profileMenuCoordinator;
    private final FlipHubAgeTooltipCoordinator ageTooltipCoordinator;
    private final FlipHubItemCardBuilder itemCardBuilder;
    private final FlipHubItemListContentRenderer itemListContentRenderer;
    private final FlipHubFlippingPanelBuilder flippingPanelBuilder;
    private final FlipHubStatsPanelBuilder statsPanelBuilder;
    private final FlipHubStatsItemCardBuilder statsItemCardBuilder;

    FlipHubPanelComponentBundle(
        FlipHubItemIconResolver itemIconResolver,
        FlipHubWheelScrollCoordinator wheelScrollCoordinator,
        MouseWheelListener wheelForwarder,
        FlipHubProfileMenuCoordinator profileMenuCoordinator,
        FlipHubAgeTooltipCoordinator ageTooltipCoordinator,
        FlipHubItemCardBuilder itemCardBuilder,
        FlipHubItemListContentRenderer itemListContentRenderer,
        FlipHubFlippingPanelBuilder flippingPanelBuilder,
        FlipHubStatsPanelBuilder statsPanelBuilder,
        FlipHubStatsItemCardBuilder statsItemCardBuilder
    ) {
        this.itemIconResolver = itemIconResolver;
        this.wheelScrollCoordinator = wheelScrollCoordinator;
        this.wheelForwarder = wheelForwarder;
        this.profileMenuCoordinator = profileMenuCoordinator;
        this.ageTooltipCoordinator = ageTooltipCoordinator;
        this.itemCardBuilder = itemCardBuilder;
        this.itemListContentRenderer = itemListContentRenderer;
        this.flippingPanelBuilder = flippingPanelBuilder;
        this.statsPanelBuilder = statsPanelBuilder;
        this.statsItemCardBuilder = statsItemCardBuilder;
    }

    FlipHubItemIconResolver getItemIconResolver() {
        return itemIconResolver;
    }

    FlipHubWheelScrollCoordinator getWheelScrollCoordinator() {
        return wheelScrollCoordinator;
    }

    MouseWheelListener getWheelForwarder() {
        return wheelForwarder;
    }

    FlipHubProfileMenuCoordinator getProfileMenuCoordinator() {
        return profileMenuCoordinator;
    }

    FlipHubAgeTooltipCoordinator getAgeTooltipCoordinator() {
        return ageTooltipCoordinator;
    }

    FlipHubItemCardBuilder getItemCardBuilder() {
        return itemCardBuilder;
    }

    FlipHubItemListContentRenderer getItemListContentRenderer() {
        return itemListContentRenderer;
    }

    FlipHubFlippingPanelBuilder getFlippingPanelBuilder() {
        return flippingPanelBuilder;
    }

    FlipHubStatsPanelBuilder getStatsPanelBuilder() {
        return statsPanelBuilder;
    }

    FlipHubStatsItemCardBuilder getStatsItemCardBuilder() {
        return statsItemCardBuilder;
    }
}
