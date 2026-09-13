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

import static com.osrsfliphub.Skin.*;

import java.awt.CardLayout;
import java.awt.Rectangle;
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
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.PluginPanel;

public class Panel extends PluginPanel {
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardPanel = new JPanel(cardLayout);
    private final JToggleButton flippingTab = new JToggleButton("Activity");
    private final JToggleButton statsTab = new JToggleButton("Profile");
    private final JToggleButton linkTab = new JToggleButton("Link");
    private final JTextField searchField = new PlaceholderTextField("GE search");
    private final JLabel refreshLabel = new JLabel("Updated: --");
    private final JButton profileButton = new JButton("Accountwide");
    private final JLabel pageLabel = new JLabel("Page 0 of 0");
    private final JButton prevButton = new JButton("<");
    private final JButton nextButton = new JButton(">");
    private JPanel footerPanel;
    private final JButton bookmarkFilterButton = new JButton(BOOKMARK_GLYPH);
    private final JComboBox<StatsItemSort> itemSortCombo = new JComboBox<>(StatsItemSort.values());
    private final JButton itemSortDirectionButton = new JButton();
    private final JPanel listPanel = new TrackingPanel(SCROLL_UNIT_INCREMENT, SCROLL_BLOCK_INCREMENT);
    private final JScrollPane scrollPane = new JScrollPane(listPanel);
    private final JComboBox<StatsRange> statsRangeCombo = new JComboBox<>(StatsRange.values());
    private final JComboBox<StatsItemSort> statsSortCombo = new JComboBox<>(StatsItemSort.values());
    private final JComboBox<StatsRecipeFilter> statsFilterCombo = new JComboBox<>(StatsRecipeFilter.values());
    private final JButton statsSortDirectionButton = new JButton();
    private final JTextField statsSearchField = new PlaceholderTextField("Search items");
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
    private final UiStyler uiStyler = new UiStyler();
    private final PanelChromeBuilder chromeBuilder = new PanelChromeBuilder(uiStyler);
    private final PanelBodyBuilder bodyBuilder = new PanelBodyBuilder();
    private final FlipHubSearchCoordinator searchCoordinator = new FlipHubSearchCoordinator();
    private final PanelValueFormat valueFormatService = new PanelValueFormat();
    private final FlippingPanelBuilder flippingPanelBuilder;
    private final StatsPanelBuilder statsPanelBuilder;
    private final StatsItemCardBuilder statsItemCardBuilder;
    private final ItemsRender itemsRenderCoordinator = new ItemsRender();
    private final StatsRender statsRenderCoordinator = new StatsRender();
    private final StatsPagerBuilder statsPagerBuilder = new StatsPagerBuilder(uiStyler);
    private final StatsState statsStateCoordinator = new StatsState();
    private final PanelState panelStateService = new PanelState();
    private final PanelComponentsFactory componentsFactory = new PanelComponentsFactory();
    private final PanelLayoutActions layoutActions = new PanelLayoutActions();
    private final PanelAsyncActions asyncActions = new PanelAsyncActions();
    private final PanelRenderActions renderActions = new PanelRenderActions();
    private final PanelMutableState panelState = new PanelMutableState();
    private final AgeTooltip ageTooltipCoordinator;
    private final WheelScroll wheelScrollCoordinator;
    private final ProfileMenu profileMenuCoordinator;
    private final ExternalLink externalLinkCoordinator = new ExternalLink(DEFAULT_BASE_URL);
    private final AccountPanelBuilder.BuildResult accountView;
    private final ItemListContentRenderer itemListContentRenderer;
    private final ItemIconResolver itemIconResolver;
    private final MouseWheelListener wheelForwarder;

    Panel(
        ItemManager itemManager,
        PanelListener listener,
        PanelBookmarkStore bookmarkStore,
        PanelHiddenItemStore hiddenItemStore,
        PluginConfig config
    ) {
        super(false);
        Map<Integer, ImageIcon> iconCache = new HashMap<>();
        PanelComponentBundle componentBundle = componentsFactory.create(
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
            this::activeStatsScrollPane,
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
        PanelLayoutResult layout = layoutActions.buildAndAttachLayout(
            this,
            cardLayout,
            cardPanel,
            flippingTab,
            statsTab,
            linkTab,
            profileButton,
            chromeBuilder,
            bodyBuilder,
            flippingPanelBuilder,
            statsPanelBuilder,
            searchField,
            bookmarkFilterButton,
            itemSortCombo,
            itemSortDirectionButton,
            refreshLabel,
            listPanel,
            scrollPane,
            prevButton,
            nextButton,
            pageLabel,
            statsRangeCombo,
            statsSearchField,
            statsUpdatedLabel,
            statsContentPanel,
            statsItemsListPanel,
            statsSortCombo,
            statsFilterCombo,
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
        // Added straight onto the card stack rather than threaded through the layout builder:
        // the account view shares none of the flipping/stats plumbing.
        this.accountView = new AccountPanelBuilder(
            uiStyler, listener, externalLinkCoordinator).build();
        cardPanel.add(accountView.panel, "account");
        addMouseWheelListener(wheelForwarder);
        cardPanel.addMouseWheelListener(wheelForwarder);
        wheelScrollCoordinator.installGlobalWheelListener();
    }

    /**
     * Which surface the wheel scrolls while the Profile tab is up.
     *
     * <p>The tab has two: its own list, and the recipe recorder that takes its place. Both hide
     * their scrollbars, so neither scrolls itself - the wheel is delivered by hand to whichever
     * of them is showing, and pointing at the wrong one leaves the form on screen frozen.
     */
    private JScrollPane activeStatsScrollPane() {
        JScrollPane recorder = statsPanelBuilder != null ? statsPanelBuilder.openRecorderPane() : null;
        return recorder != null ? recorder : statsScrollPane;
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

    /**
     * Releases everything the panel holds outside itself.
     *
     * <p>Disabling the plugin drops the panel, but two one-second Swing timers and a global AWT
     * wheel listener kept referring back to it, so each toggle left a whole panel alive and
     * ticking. removeNotify is not enough on its own: it never runs if the side panel was never
     * opened, because the wheel listener is installed in the constructor.
     */
    void dispose() {
        ageTooltipCoordinator.shutDown();
        statsRenderCoordinator.shutDown();
        wheelScrollCoordinator.uninstallGlobalWheelListener();
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

    void setAccountState(boolean linked, String keyHint, String message, java.awt.Color messageColor) {
        SwingUtilities.invokeLater(
            () -> AccountPanelBuilder.applyState(accountView, linked, keyHint, message, messageColor));
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

    void setProfileOptions(List<ProfileOption> options, String selectedKey) {
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
            statsItemCardBuilder,
            this::buildCard,
            statsPagerBuilder,
            this::goToStatsPage
        );
    }

    /**
     * The pager lives at the bottom of the list, so a page turn leaves the view parked on the last
     * rows of the page that was just replaced - scroll back to the first card of the new page.
     */
    private void goToStatsPage(int page) {
        panelStateService.onStatsPageRequested(panelState, page, this::renderStatsItems);
        SwingUtilities.invokeLater(() -> statsItemsListPanel.scrollRectToVisible(new Rectangle(0, 0, 1, 1)));
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

