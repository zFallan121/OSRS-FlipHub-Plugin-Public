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

import java.awt.*;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import javax.swing.*;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import static com.osrsfliphub.Skin.*;

public class Panel extends PluginPanel {
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardPanel = new JPanel(cardLayout);
    private final JToggleButton flippingTab = new JToggleButton("Activity");
    private final JToggleButton statsTab = new JToggleButton("Profile");
    private final JToggleButton linkTab = new JToggleButton("Link");
    private final JTextField searchField = new PlaceholderTextField("GE search");
    private final JLabel refreshLabel = new JLabel("Updated: --");
    private final JButton profileButton = new TipButton("Accountwide");
    private final JLabel pageLabel = new JLabel("Page 0 of 0");
    private final JButton prevButton = new TipButton("<");
    private final JButton nextButton = new TipButton(">");
    private final JButton bookmarkFilterButton = new TipButton(BOOKMARK_GLYPH);
    private final JComboBox<StatsItemSort> itemSortCombo = new JComboBox<>(StatsItemSort.values());
    private final JButton itemSortDirectionButton = new TipButton();
    private final JPanel listPanel = new TrackingPanel(SCROLL_UNIT_INCREMENT, SCROLL_BLOCK_INCREMENT);
    private final JScrollPane scrollPane = new JScrollPane(listPanel);
    private final JComboBox<StatsRange> statsRangeCombo = new JComboBox<>(StatsRange.values());
    private final JComboBox<StatsItemSort> statsSortCombo = new JComboBox<>(StatsItemSort.values());
    private final JComboBox<StatsRecipeFilter> statsFilterCombo = new JComboBox<>(StatsRecipeFilter.values());
    private final JButton statsSortDirectionButton = new TipButton();
    private final JTextField statsSearchField = new PlaceholderTextField("Search items");
    private final JLabel statsUpdatedLabel = new JLabel("Updated: --");
    private final JPanel statsContentPanel = new TrackingPanel(SCROLL_UNIT_INCREMENT, SCROLL_BLOCK_INCREMENT);
    private final JPanel statsItemsListPanel = new JPanel();
    private Integer expandedStatsItemId;
    private final Set<Integer> expandedStatsHistoryItems = new HashSet<>();
    private final UiStyler uiStyler = new UiStyler();
    private final PanelValueFormat valueFormatService = new PanelValueFormat();
    private final ItemsRender itemsRenderCoordinator = new ItemsRender();
    private final StatsRender statsRenderCoordinator = new StatsRender();
    private final StatsPagerBuilder statsPagerBuilder = new StatsPagerBuilder(uiStyler);
    private final StatsState statsStateCoordinator = new StatsState();
    private final PanelState panelStateService = new PanelState();
    private final PanelMutableState panelState = new PanelMutableState();
    private final ExternalLink externalLinkCoordinator = new ExternalLink(DEFAULT_BASE_URL);
    private final AgeTooltip ageTooltipCoordinator = new AgeTooltip(valueFormatService);
    private final WheelScroll wheelScrollCoordinator;
    private final ProfileMenu profileMenuCoordinator;
    private final StatsPanelBuilder statsPanelBuilder;
    private final StatsItemCardBuilder statsItemCardBuilder;
    private final ItemListContentRenderer itemListContentRenderer;
    private final AccountPanelBuilder.BuildResult accountView;
    // Filled in by the two tabs as they are built. The Profile tab asks for its summary while it
    // is still being built, before these exist, and StatsRender leaves a summary with no labels alone.
    private final JPanel footerPanel;
    private final JScrollPane statsScrollPane;
    private final JLabel statsTotalProfitValue;
    private final JLabel statsRoiValue;
    private final JLabel statsFlipsValue;
    private final JLabel statsTaxValue;
    private final JLabel statsSessionTimeValue;
    private final JLabel statsHourlyValue;

    Panel(
        ItemManager itemManager,
        PanelListener listener,
        PanelBookmarkStore bookmarkStore,
        PanelHiddenItemStore hiddenItemStore,
        PluginConfig config
    ) {
        super(false);
        ItemIconResolver itemIconResolver = new ItemIconResolver(itemManager, new HashMap<>());
        wheelScrollCoordinator = new WheelScroll(
            () -> statsTab.isSelected() ? activeStatsScrollPane() : scrollPane, this);
        MouseWheelListener wheelForwarder = wheelScrollCoordinator.wheelForwarder();
        profileMenuCoordinator = new ProfileMenu(profileButton, uiStyler, listener);
        ItemCardBuilder itemCardBuilder = new ItemCardBuilder(
            valueFormatService, uiStyler, itemIconResolver, externalLinkCoordinator, bookmarkStore,
            hiddenItemStore, panelState, this::renderItems, ageTooltipCoordinator, wheelScrollCoordinator);
        itemListContentRenderer = new ItemListContentRenderer(
            uiStyler, hiddenItemStore, bookmarkStore, itemCardBuilder, ageTooltipCoordinator);
        FlippingPanelBuilder flippingPanelBuilder = new FlippingPanelBuilder(
            uiStyler, panelStateService, panelState, listener, this::renderItems,
            new FlipHubSearchCoordinator(), wheelForwarder);
        statsPanelBuilder = new StatsPanelBuilder(
            uiStyler, valueFormatService, panelStateService, panelState, listener,
            this::renderStatsItems, this::updateStatsSummary, wheelScrollCoordinator, wheelForwarder);
        statsItemCardBuilder = new StatsItemCardBuilder(
            valueFormatService, uiStyler, itemIconResolver, () -> expandedStatsItemId,
            expandedStatsHistoryItems, panelState, this::toggleStatsItemExpanded,
            this::toggleStatsHistoryExpanded);

        // The backdrop is sacred: one panel paints the navy and the two washes, and every surface
        // above it is transparent or translucent so the glow is never covered. The padding moves
        // onto the backdrop rather than the host so the wash reaches the panel's own edges.
        setLayout(new BorderLayout());
        setBackground(BG);
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JPanel backdrop = new BackdropPanel(BG, GRAD_GREEN, GRAD_BLUE);
        backdrop.setLayout(new BorderLayout());
        // Two at the bottom, not twelve: the pager is meant to sit on the panel's edge.
        backdrop.setBorder(BorderFactory.createEmptyBorder(12, 12, 2, 12));
        uiStyler.installClickToDefocus(backdrop);
        add(backdrop, BorderLayout.CENTER);

        PanelChromeBuilder chromeBuilder = new PanelChromeBuilder(uiStyler);
        JPanel header = chromeBuilder.buildHeader(profileButton, profileMenuCoordinator::showProfileMenu);
        JButton discordButton = chromeBuilder.buildDiscordButton(
            () -> externalLinkCoordinator.openExternalUrl(DISCORD_INVITE_URL));
        JPanel tabs = chromeBuilder.buildTabs(flippingTab, statsTab, linkTab, discordButton,
            card -> panelStateService.switchTab(card, ageTooltipCoordinator, uiStyler, flippingTab,
                statsTab, linkTab, cardLayout, cardPanel, listener, statsRangeCombo));

        cardPanel.setOpaque(false);
        FlippingPanelBuilder.BuildResult flipping = flippingPanelBuilder.build(
            searchField, bookmarkFilterButton, itemSortCombo, itemSortDirectionButton, refreshLabel,
            profileButton, listPanel, scrollPane, prevButton, nextButton, pageLabel);
        StatsPanelBuilder.BuildResult stats = statsPanelBuilder.build(
            statsRangeCombo, statsSearchField, statsUpdatedLabel, statsContentPanel,
            statsItemsListPanel, statsSortCombo, statsFilterCombo, statsSortDirectionButton);
        cardPanel.add(flipping.panel, "flipping");
        cardPanel.add(stats.panel, "stats");

        JPanel top = stack();
        top.add(header);
        top.add(Box.createVerticalStrut(8));
        top.add(tabs);
        // The tab row's own 2px rule is the separator between the chrome and the body. A second
        // divider here would be a box the chrome has not earned.
        top.add(Box.createVerticalStrut(8));

        backdrop.add(top, BorderLayout.NORTH);
        backdrop.add(cardPanel, BorderLayout.CENTER);

        footerPanel = flipping.footerPanel;
        statsScrollPane = stats.scrollPane;
        statsTotalProfitValue = stats.totalProfitValue;
        statsRoiValue = stats.roiValue;
        statsFlipsValue = stats.flipsValue;
        statsTaxValue = stats.taxValue;
        statsSessionTimeValue = stats.sessionTimeValue;
        statsHourlyValue = stats.hourlyValue;
        // The account view shares none of the flipping/stats plumbing, so it goes straight onto
        // the card stack.
        accountView = new AccountPanelBuilder(uiStyler, listener, externalLinkCoordinator).build();
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
        JScrollPane recorder = statsPanelBuilder.openRecorderPane();
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
        BufferedImage icon = null;
        try {
            // getResourceAsStream via ImageUtil: on the hub the plugin runs from inside a jar,
            // where a resource URL does not behave like the file URL seen in the IDE.
            icon = ImageUtil.loadImageResource(getClass(), "/com/osrsfliphub/fliphub-icon.png");
        } catch (Exception ignored) {
            // no-op; fallback icon below
        }
        if (icon == null) {
            BufferedImage fallback = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = fallback.createGraphics();
            Skin.smooth(g);
            g.setColor(ACCENT);
            g.fillRoundRect(0, 0, 16, 16, 4, 4);
            g.setColor(Color.WHITE);
            g.setFont(uiStyler.fontBold(10f));
            g.drawString("F", 4, 12);
            g.dispose();
            return fallback;
        }
        return ImageUtil.resizeImage(icon, 16, 16);
    }

    // Everything below that sets something is called off the Swing thread, so each hops onto it.

    void setItems(List<FlipHubItem> items, int page, int totalPages, long asOfMs, Long priceCacheMs) {
        SwingUtilities.invokeLater(() -> panelStateService.setItems(panelState, items, page, totalPages,
            asOfMs, priceCacheMs, pageLabel, prevButton, nextButton, this::renderItems));
    }

    void setStatsData(StatsSummary summary,
                      List<StatsItem> items,
                      Map<Integer, List<StatsFlipInstance>> historyByItem,
                      long asOfMs) {
        SwingUtilities.invokeLater(() -> expandedStatsItemId = panelStateService.setStatsData(
            panelState, summary, items, historyByItem, expandedStatsItemId, expandedStatsHistoryItems,
            statsStateCoordinator, this::updateStatsUpdatedLabel, this::updateStatsSummary,
            this::renderStatsItems, asOfMs));
    }

    void refreshBookmarks() {
        SwingUtilities.invokeLater(this::renderItems);
    }

    boolean isStatsTabSelected() {
        return statsTab.isSelected();
    }

    void setOfferPreview(FlipHubItem item, long asOfMs, Long priceCacheMs) {
        SwingUtilities.invokeLater(() -> panelStateService.setOfferPreview(
            panelState, item, asOfMs, priceCacheMs, this::renderItems));
    }

    void setAccountState(boolean linked, String keyHint, String message, Color messageColor) {
        SwingUtilities.invokeLater(
            () -> AccountPanelBuilder.applyState(accountView, linked, keyHint, message, messageColor));
    }

    void setStatusMessage(String message) {
        SwingUtilities.invokeLater(() -> profileMenuCoordinator.setStatusMessage(message));
    }

    void setProfileHeader(String label, boolean linked) {
        SwingUtilities.invokeLater(() -> profileMenuCoordinator.setProfileHeader(label, linked));
    }

    void setUploadDiagnosticsTooltip(String tooltip) {
        SwingUtilities.invokeLater(() -> profileMenuCoordinator.setUploadDiagnosticsTooltip(tooltip));
    }

    void setProfileOptions(List<ProfileOption> options, String selectedKey) {
        SwingUtilities.invokeLater(() -> profileMenuCoordinator.setProfileOptions(options, selectedKey));
    }

    private void updateStatsUpdatedLabel(long asOfMs) {
        panelStateService.updateStatsUpdatedLabel(statsUpdatedLabel, asOfMs);
    }

    private void renderItems() {
        itemsRenderCoordinator.renderItems(listPanel, ageTooltipCoordinator, itemListContentRenderer,
            panelState.offerPreviewItem, panelState.offerAsOfMs, panelState.lastItems, panelState.lastAsOfMs,
            panelState.showBookmarkedOnly, panelState.searchQuery, refreshLabel, panelState.lastPriceCacheMs,
            panelState.offerPriceCacheMs, panelStateService::buildRefreshText, footerPanel, scrollPane);
    }

    private void updateStatsSummary() {
        // ALL is the default and means the card reports the range as a whole,
        // exactly as it always has - no slice is computed at all.
        StatsRender.StatsProfitSlice slice =
            panelState.statsProfitFilter == null || panelState.statsProfitFilter == StatsRecipeFilter.ALL
                ? null
                : StatsRender.sliceActivities(
                    panelState.statsFlipHistoryByItem, panelState.statsProfitFilter);
        statsRenderCoordinator.updateSummary(panelState.statsSummary, slice, valueFormatService,
            statsTotalProfitValue, statsRoiValue, statsFlipsValue, statsTaxValue,
            statsSessionTimeValue, statsHourlyValue);
    }

    private void renderStatsItems() {
        panelState.statsPage = statsRenderCoordinator.renderItems(statsItemsListPanel, panelState.statsItems,
            panelState.statsSearchQuery, panelState.statsSort, panelState.statsRecipeFilter,
            panelState.statsSortAscending, panelState.statsPage, statsItemCardBuilder::buildStatsItemCard,
            statsItemCardBuilder::visibleItem, uiStyler::emptyCard, statsPagerBuilder, this::goToStatsPage);
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
        expandedStatsItemId = statsRenderCoordinator.toggleItemExpanded(
            expandedStatsItemId, expandedStatsHistoryItems, itemId);
        renderStatsItems();
    }

    private void toggleStatsHistoryExpanded(int itemId) {
        statsRenderCoordinator.toggleHistoryExpanded(expandedStatsHistoryItems, itemId);
        renderStatsItems();
    }
}
