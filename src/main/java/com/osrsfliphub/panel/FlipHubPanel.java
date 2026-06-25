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

import static com.osrsfliphub.FlipHubPanelConstants.*;

import java.awt.CardLayout;
import java.awt.image.BufferedImage;
import java.awt.event.MouseWheelListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ImageIcon;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.PluginPanel;

public class FlipHubPanel extends PluginPanel {
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardPanel = new JPanel(cardLayout);
    private final JToggleButton flippingTab = new JToggleButton("Activity");
    private final JToggleButton statsTab = new JToggleButton("Flip Profile");
    private final JTextField searchField = new JTextField();
    private final JLabel refreshLabel = new JLabel("Updated: --");
    private final JButton profileButton = new JButton("Accountwide");
    private final JLabel pageLabel = new JLabel("Page 0 of 0");
    private final JButton prevButton = new JButton("<");
    private final JButton nextButton = new JButton(">");
    private JPanel footerPanel;
    private final JToggleButton bookmarkFilterButton = new JToggleButton("\u2605");
    private final JPanel listPanel = new TrackingPanel(SCROLL_UNIT_INCREMENT, SCROLL_BLOCK_INCREMENT);
    private final JScrollPane scrollPane = new JScrollPane(listPanel);
    private final JComboBox<StatsRange> statsRangeCombo = new JComboBox<>(StatsRange.values());
    private final JComboBox<StatsItemSort> statsSortCombo = new JComboBox<>(StatsItemSort.values());
    private final JButton statsSortDirectionButton = new JButton("\u2193");
    private final JTextField statsSearchField = new JTextField();
    private final JButton statsClearButton = new JButton("Clear");
    private final JLabel statsUpdatedLabel = new JLabel("Updated: --");
    private final JPanel statsContentPanel = new TrackingPanel(SCROLL_UNIT_INCREMENT, SCROLL_BLOCK_INCREMENT);
    private final JPanel statsItemsListPanel = new JPanel();
    private JScrollPane statsScrollPane;
    private JLabel statsTotalProfitValue;
    private JLabel statsRoiValue;
    private JLabel statsFlipsValue;
    private JLabel statsTaxValue;
    private JLabel statsSessionTimeValue;
    private JLabel statsHourlyValue;
    private Integer expandedStatsItemId;
    private final Set<Integer> expandedStatsHistoryItems = new HashSet<>();
    private final FlipHubUiStyler uiStyler = new FlipHubUiStyler();
    private final FlipHubPanelChromeBuilder chromeBuilder = new FlipHubPanelChromeBuilder(uiStyler);
    private final FlipHubPanelBodyBuilder bodyBuilder = new FlipHubPanelBodyBuilder();
    private final FlipHubSearchCoordinator searchCoordinator = new FlipHubSearchCoordinator();
    private final FlipHubPanelValueFormatService valueFormatService = new FlipHubPanelValueFormatService();
    private final FlipHubFlippingPanelBuilder flippingPanelBuilder;
    private final FlipHubStatsPanelBuilder statsPanelBuilder;
    private final FlipHubStatsItemCardBuilder statsItemCardBuilder;
    private final FlipHubItemsRenderCoordinator itemsRenderCoordinator = new FlipHubItemsRenderCoordinator();
    private final FlipHubStatsRenderCoordinator statsRenderCoordinator = new FlipHubStatsRenderCoordinator();
    private final FlipHubStatsStateCoordinator statsStateCoordinator = new FlipHubStatsStateCoordinator();
    private final FlipHubPanelStateService panelStateService = new FlipHubPanelStateService();
    private final FlipHubPanelComponentsFactory componentsFactory = new FlipHubPanelComponentsFactory();
    private final FlipHubPanelLayoutActions layoutActions = new FlipHubPanelLayoutActions();
    private final FlipHubPanelAsyncActions asyncActions = new FlipHubPanelAsyncActions();
    private final FlipHubPanelRenderActions renderActions = new FlipHubPanelRenderActions();
    private final FlipHubPanelMutableState panelState = new FlipHubPanelMutableState();
    private final FlipHubAgeTooltipCoordinator ageTooltipCoordinator;
    private final FlipHubWheelScrollCoordinator wheelScrollCoordinator;
    private final FlipHubProfileMenuCoordinator profileMenuCoordinator;
    private final FlipHubExternalLinkCoordinator externalLinkCoordinator = new FlipHubExternalLinkCoordinator(DEFAULT_BASE_URL);
    private final FlipHubItemListContentRenderer itemListContentRenderer;
    private final FlipHubItemIconResolver itemIconResolver;
    private final MouseWheelListener wheelForwarder;

    FlipHubPanel(
        ItemManager itemManager,
        FlipHubPanelListener listener,
        FlipHubPanelBookmarkStore bookmarkStore,
        FlipHubPanelHiddenItemStore hiddenItemStore,
        PluginConfig config
    ) {
        super(false);
        Map<Integer, ImageIcon> iconCache = new HashMap<>();
        FlipHubPanelComponentBundle componentBundle = componentsFactory.create(
            itemManager,
            listener,
            bookmarkStore,
            hiddenItemStore,
            this,
            profileButton,
            uiStyler,
            valueFormatService,
            externalLinkCoordinator,
            panelStateService,
            panelState,
            searchCoordinator,
            searchField,
            statsTab,
            scrollPane,
            () -> statsScrollPane,
            iconCache,
            expandedStatsHistoryItems,
            () -> expandedStatsItemId,
            this::renderItems,
            this::renderStatsItems,
            this::updateStatsSummary,
            this::toggleStatsItemExpanded,
            this::toggleStatsHistoryExpanded
        );
        this.itemIconResolver = componentBundle.getItemIconResolver();
        this.wheelScrollCoordinator = componentBundle.getWheelScrollCoordinator();
        this.wheelForwarder = componentBundle.getWheelForwarder();
        this.profileMenuCoordinator = componentBundle.getProfileMenuCoordinator();
        this.ageTooltipCoordinator = componentBundle.getAgeTooltipCoordinator();
        this.itemListContentRenderer = componentBundle.getItemListContentRenderer();
        this.flippingPanelBuilder = componentBundle.getFlippingPanelBuilder();
        this.statsPanelBuilder = componentBundle.getStatsPanelBuilder();
        this.statsItemCardBuilder = componentBundle.getStatsItemCardBuilder();
        FlipHubPanelLayoutResult layout = layoutActions.buildAndAttachLayout(
            this,
            cardLayout,
            cardPanel,
            flippingTab,
            statsTab,
            profileButton,
            chromeBuilder,
            bodyBuilder,
            flippingPanelBuilder,
            statsPanelBuilder,
            searchField,
            bookmarkFilterButton,
            refreshLabel,
            listPanel,
            scrollPane,
            prevButton,
            nextButton,
            pageLabel,
            statsRangeCombo,
            statsSearchField,
            statsClearButton,
            statsUpdatedLabel,
            statsContentPanel,
            statsItemsListPanel,
            statsSortCombo,
            statsSortDirectionButton,
            panelStateService,
            ageTooltipCoordinator,
            uiStyler,
            listener,
            profileMenuCoordinator::showProfileMenu,
            () -> externalLinkCoordinator.openExternalUrl(DISCORD_INVITE_URL)
        );
        footerPanel = layout.footerPanel;
        statsScrollPane = layout.statsScrollPane;
        statsTotalProfitValue = layout.totalProfitValue;
        statsRoiValue = layout.roiValue;
        statsFlipsValue = layout.flipsValue;
        statsTaxValue = layout.taxValue;
        statsSessionTimeValue = layout.sessionTimeValue;
        statsHourlyValue = layout.hourlyValue;
        addMouseWheelListener(wheelForwarder);
        cardPanel.addMouseWheelListener(wheelForwarder);
        wheelScrollCoordinator.installGlobalWheelListener();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        wheelScrollCoordinator.installGlobalWheelListener();
    }

    @Override
    public void removeNotify() {
        ageTooltipCoordinator.clearHoverAndHide();
        wheelScrollCoordinator.uninstallGlobalWheelListener();
        super.removeNotify();
    }

    BufferedImage buildNavIcon() {
        return layoutActions.buildNavIcon(uiStyler);
    }

    void setItems(List<FlipHubItem> items, int page, int totalPages, long asOfMs, Long priceCacheMs) {
        asyncActions.setItemsAsync(
            panelStateService,
            panelState,
            items,
            page,
            totalPages,
            asOfMs,
            priceCacheMs,
            pageLabel,
            prevButton,
            nextButton,
            this::renderItems
        );
    }

    void setStatsData(StatsSummary summary,
                      List<StatsItem> items,
                      Map<Integer, List<StatsFlipInstance>> historyByItem,
                      long asOfMs) {
        asyncActions.setStatsDataAsync(
            panelStateService,
            panelState,
            summary,
            items,
            historyByItem,
            () -> expandedStatsItemId,
            value -> expandedStatsItemId = value,
            expandedStatsHistoryItems,
            statsStateCoordinator,
            this::updateStatsUpdatedLabel,
            this::updateStatsSummary,
            this::renderStatsItems,
            asOfMs
        );
    }

    void refreshBookmarks() {
        asyncActions.refreshBookmarksAsync(this::renderItems);
    }

    boolean isStatsTabSelected() {
        return statsTab.isSelected();
    }

    void setOfferPreview(FlipHubItem item, long asOfMs, Long priceCacheMs) {
        asyncActions.setOfferPreviewAsync(
            panelStateService,
            panelState,
            item,
            asOfMs,
            priceCacheMs,
            this::renderItems
        );
    }

    void setStatusMessage(String message) {
        asyncActions.setStatusMessageAsync(profileMenuCoordinator, message);
    }

    void setProfileHeader(String label, boolean linked) {
        asyncActions.setProfileHeaderAsync(profileMenuCoordinator, label, linked);
    }

    void setUploadDiagnosticsTooltip(String tooltip) {
        asyncActions.setUploadDiagnosticsTooltipAsync(profileMenuCoordinator, tooltip);
    }

    void setProfileOptions(List<FlipHubProfileOption> options, String selectedKey) {
        asyncActions.setProfileOptionsAsync(profileMenuCoordinator, options, selectedKey);
    }

    private void updateStatsUpdatedLabel(long asOfMs) {
        renderActions.updateStatsUpdatedLabel(panelStateService, statsUpdatedLabel, asOfMs);
    }

    private JPanel buildCard(String title, String body) {
        return layoutActions.buildCard(title, body, uiStyler);
    }

    private void renderItems() {
        renderActions.renderItems(
            itemsRenderCoordinator,
            listPanel,
            ageTooltipCoordinator,
            itemListContentRenderer,
            panelState,
            refreshLabel,
            panelStateService,
            footerPanel,
            scrollPane
        );
    }

    private void updateStatsSummary() {
        renderActions.updateStatsSummary(
            statsRenderCoordinator,
            panelState,
            valueFormatService,
            statsTotalProfitValue,
            statsRoiValue,
            statsFlipsValue,
            statsTaxValue,
            statsSessionTimeValue,
            statsHourlyValue
        );
    }

    private void renderStatsItems() {
        renderActions.renderStatsItems(
            statsRenderCoordinator,
            statsItemsListPanel,
            panelState,
            (StatsItemSort) statsSortCombo.getSelectedItem(),
            statsItemCardBuilder,
            this::buildCard
        );
    }

    private void toggleStatsItemExpanded(int itemId) {
        expandedStatsItemId = renderActions.toggleStatsItemExpanded(
            statsRenderCoordinator,
            expandedStatsItemId,
            expandedStatsHistoryItems,
            itemId
        );
        renderStatsItems();
    }

    private void toggleStatsHistoryExpanded(int itemId) {
        renderActions.toggleStatsHistoryExpanded(statsRenderCoordinator, expandedStatsHistoryItems, itemId);
        renderStatsItems();
    }

}

