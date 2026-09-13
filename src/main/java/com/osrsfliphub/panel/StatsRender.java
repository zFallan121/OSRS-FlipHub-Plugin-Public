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

import static com.osrsfliphub.Skin.DANGER;
import static com.osrsfliphub.Skin.STATS_ITEMS_PER_PAGE;
import static com.osrsfliphub.Skin.SUCCESS;
import static com.osrsfliphub.Skin.TEXT;

import java.awt.Color;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.UnaryOperator;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

final class StatsRender {
    /**
     * The stats summary is only re-rendered when the underlying data changes, so without a ticker
     * the session clock would freeze at whatever it read on the first render - which is 00:00:00
     * when the panel is built at login. Mirrors the countdown timer in the age tooltip.
     */
    private Timer sessionTimer;
    private boolean disposed;
    private PanelValueFormat sessionFormatService;
    private JLabel sessionTimeLabel;
    private JLabel sessionHourlyLabel;
    private long sessionTotalProfit;

    void updateSummary(StatsSummary statsSummary,
                       StatsProfitSlice slice,
                       PanelValueFormat valueFormatService,
                       JLabel statsTotalProfitValue,
                       JLabel statsRoiValue,
                       JLabel statsFlipsValue,
                       JLabel statsTaxValue,
                       JLabel statsSessionTimeValue,
                       JLabel statsHourlyValue) {
        if (statsTotalProfitValue == null || statsRoiValue == null) {
            return;
        }
        if (statsSummary == null) {
            setLabel(statsTotalProfitValue, "0 gp", SUCCESS);
            setLabel(statsRoiValue, "0.00%", TEXT);
            setLabel(statsFlipsValue, "0", null);
            setLabel(statsTaxValue, "0 gp", null);
            applySessionRows(valueFormatService, statsSessionTimeValue, statsHourlyValue, 0L);
            return;
        }

        // A slice re-answers the headline question - profit, the return it was
        // made at, and how many activities produced it - for one kind of trade.
        // Tax, session time and hourly stay whole-range: they are properties of
        // the session rather than of a subset of its trades.
        boolean sliced = slice != null;
        long totalProfit = sliced
            ? slice.profitGp
            : (statsSummary.total_profit_gp != null ? statsSummary.total_profit_gp : 0);
        // Green means profit. The figure was amber whatever its sign, which spent the caution
        // tint on the one number on the tab that is never a caution.
        setLabel(statsTotalProfitValue, valueFormatService.formatGp(totalProfit), totalProfit >= 0 ? SUCCESS : DANGER);

        Double roi = sliced ? slice.roiPercent() : statsSummary.roi_percent;
        setLabel(statsRoiValue, valueFormatService.formatPercent(roi), roi != null && roi < 0 ? DANGER : SUCCESS);

        int flips = sliced
            ? slice.count
            : (statsSummary.fill_count != null ? statsSummary.fill_count : 0);
        setLabel(statsFlipsValue, String.valueOf(flips), null);

        setLabel(statsTaxValue, valueFormatService.formatGp(statsSummary.tax_paid_gp), null);

        applySessionRows(valueFormatService, statsSessionTimeValue, statsHourlyValue, totalProfit);
    }

    /**
     * Session time and Hourly profit only mean anything while the Session range is selected: the
     * profit above them is scoped to the chosen range, so dividing a 7d or all-time total by the
     * current session's length would be nonsense. Both rows are hidden for every other range
     * rather than shown with a misleading number.
     */
    private void applySessionRows(PanelValueFormat valueFormatService,
                                  JLabel statsSessionTimeValue,
                                  JLabel statsHourlyValue,
                                  long totalProfit) {
        sessionFormatService = valueFormatService;
        sessionTimeLabel = statsSessionTimeValue;
        sessionHourlyLabel = statsHourlyValue;
        sessionTotalProfit = totalProfit;

        GeLifecyclePlugin plugin = Access.pluginOrNull();
        boolean sessionRange = plugin != null && plugin.currentStatsRange == StatsRange.SESSION;
        setRowVisible(statsSessionTimeValue, sessionRange);
        setRowVisible(statsHourlyValue, sessionRange);
        if (!sessionRange) {
            stopSessionTimer();
            return;
        }
        renderSessionRows();
        startSessionTimer();
    }

    private void renderSessionRows() {
        if (sessionFormatService == null || sessionTimeLabel == null) {
            return;
        }
        long sessionElapsedMs = resolveSessionElapsedMs();
        setLabel(sessionTimeLabel, sessionFormatService.formatDuration(sessionElapsedMs), null);
        if (sessionHourlyLabel == null) {
            return;
        }
        if (sessionElapsedMs <= 0L) {
            setLabel(sessionHourlyLabel, "0 gp/hr", SUCCESS);
            return;
        }
        double gpPerHour = sessionTotalProfit / (sessionElapsedMs / 3600000.0);
        setLabel(sessionHourlyLabel, sessionFormatService.formatGpPerHour(gpPerHour), gpPerHour < 0 ? DANGER : SUCCESS);
    }

    private void startSessionTimer() {
        if (disposed) {
            return;
        }
        if (sessionTimer == null) {
            sessionTimer = new Timer(1000, e -> renderSessionRows());
            sessionTimer.setRepeats(true);
        }
        if (!sessionTimer.isRunning()) {
            sessionTimer.start();
        }
    }

    private void stopSessionTimer() {
        if (sessionTimer != null && sessionTimer.isRunning()) {
            sessionTimer.stop();
        }
    }

    /**
     * Wall-clock length of the current session. This is deliberately not {@code active_ms}: that
     * field sums each flip's buy-to-sell holding time, so it counts overlapping flips more than
     * once and reaches years across a full history. It stays in {@link StatsSummary} because it is
     * a useful time-in-market signal, it just is not a session clock.
     */
    private long resolveSessionElapsedMs() {
        // pluginOrNull, not plugin(): the latter throws when uninitialised, and this runs from a
        // repeating timer that can outlive the plugin.
        GeLifecyclePlugin plugin = Access.pluginOrNull();
        if (plugin == null) {
            stopSessionTimer();
            return 0L;
        }
        long startMs = plugin.sessionStartMs;
        long nowMs = System.currentTimeMillis();
        if (startMs <= 0L || startMs > nowMs) {
            return 0L;
        }
        return nowMs - startMs;
    }

    /**
     * Rows are built as a panel holding the label and its value, so the value's parent is the row.
     */
    private void setRowVisible(JLabel valueLabel, boolean visible) {
        if (valueLabel == null) {
            return;
        }
        Container row = valueLabel.getParent();
        if (row == null || row.isVisible() == visible) {
            return;
        }
        row.setVisible(visible);
        row.revalidate();
        row.repaint();
    }

    /**
     * Renders one page of the completed-flip list and returns the page it actually drew: the
     * requested page is clamped, so a page that no longer exists after a search or a data refresh
     * falls back to the last page that does instead of rendering an empty list.
     */
    int renderItems(JPanel statsItemsListPanel,
                    List<StatsItem> statsItems,
                    String statsSearchQuery,
                    StatsItemSort sort,
                    StatsRecipeFilter recipeFilter,
                    boolean statsSortAscending,
                    int requestedPage,
                    Function<StatsItem, JPanel> statsItemCardBuilder,
                    UnaryOperator<StatsItem> filteredView,
                    BiFunction<String, String, JPanel> emptyCardBuilder,
                    StatsPagerBuilder pagerBuilder,
                    IntConsumer onPageSelected) {
        if (statsItemsListPanel == null) {
            return clampPage(requestedPage, 1);
        }
        statsItemsListPanel.removeAll();

        List<StatsItem> items = statsItems != null ? statsItems : new ArrayList<>();
        String normalizedQuery = statsSearchQuery != null ? statsSearchQuery : "";
        List<StatsItem> filtered = new ArrayList<>();
        for (StatsItem item : items) {
            if (item == null) {
                continue;
            }
            String name = item.item_name != null && !item.item_name.trim().isEmpty()
                ? item.item_name
                : "Item " + item.item_id;
            if (!normalizedQuery.isEmpty() && !name.toLowerCase(Locale.US).contains(normalizedQuery)) {
                continue;
            }
            if (recipeFilter != null && !recipeFilter.matches(item.conversionKinds, item.hasPlainFlip)) {
                continue;
            }
            // Cut the item down to the activities the filter leaves before it is sorted or
            // paged. Sorting the full totals and then drawing the filtered ones put an item
            // with a large flip profit and a small assembly profit above one that had made far
            // more from assembling, which is the opposite of what the filter was asked for.
            StatsItem view = filteredView != null ? filteredView.apply(item) : item;
            filtered.add(view != null ? view : item);
        }

        StatsItemSort effectiveSort = sort != null ? sort : StatsItemSort.COMPLETION;
        boolean hasSellTimestamp = false;
        for (StatsItem item : filtered) {
            if (item != null && item.last_sell_ts_ms != null && item.last_sell_ts_ms > 0) {
                hasSellTimestamp = true;
                break;
            }
        }
        if (effectiveSort != StatsItemSort.COMPLETION || hasSellTimestamp || statsSortAscending) {
            Comparator<StatsItem> comparator = buildItemsComparator(effectiveSort);
            if (statsSortAscending) {
                comparator = comparator.reversed();
            }
            filtered.sort(comparator);
        }

        int totalPages = totalPages(filtered.size());
        int page = clampPage(requestedPage, totalPages);

        if (filtered.isEmpty()) {
            if (emptyCardBuilder != null) {
                boolean filtering = recipeFilter != null && recipeFilter != StatsRecipeFilter.ALL;
                if (normalizedQuery.isEmpty() && filtering) {
                    // An empty list here is a real answer, not a missing feature:
                    // the player has not done this kind of trade yet.
                    statsItemsListPanel.add(emptyCardBuilder.apply(
                        "Nothing to show", "No " + recipeFilter.toString().toLowerCase(Locale.US) + " activity in this range."));
                } else if (normalizedQuery.isEmpty()) {
                    statsItemsListPanel.add(emptyCardBuilder.apply("No stats yet", "Make a trade to see your items here."));
                } else {
                    statsItemsListPanel.add(emptyCardBuilder.apply("No matches", "Try a different search term."));
                }
            }
        } else if (statsItemCardBuilder != null) {
            int firstIndex = (page - 1) * STATS_ITEMS_PER_PAGE;
            int lastIndex = Math.min(filtered.size(), firstIndex + STATS_ITEMS_PER_PAGE);
            for (StatsItem item : filtered.subList(firstIndex, lastIndex)) {
                statsItemsListPanel.add(statsItemCardBuilder.apply(item));
                statsItemsListPanel.add(Box.createVerticalStrut(6));
            }
            if (totalPages > 1 && pagerBuilder != null && onPageSelected != null) {
                statsItemsListPanel.add(pagerBuilder.buildPager(page, totalPages, onPageSelected));
            }
        }

        statsItemsListPanel.revalidate();
        statsItemsListPanel.repaint();
        // The cards the pointer was over are gone, replaced by cards that have never heard of it.
        HoverRestorer.restoreAfterRebuild(statsItemsListPanel);
        return page;
    }

    static int totalPages(int itemCount) {
        if (itemCount <= 0) {
            return 1;
        }
        return (itemCount + STATS_ITEMS_PER_PAGE - 1) / STATS_ITEMS_PER_PAGE;
    }

    static int clampPage(int page, int totalPages) {
        int lastPage = Math.max(1, totalPages);
        if (page < 1) {
            return 1;
        }
        return Math.min(page, lastPage);
    }

    Integer toggleItemExpanded(Integer expandedStatsItemId, Set<Integer> expandedStatsHistoryItems, int itemId) {
        if (itemId <= 0) {
            return expandedStatsItemId;
        }
        Integer previousExpanded = expandedStatsItemId;
        if (expandedStatsItemId != null && expandedStatsItemId == itemId) {
            expandedStatsHistoryItems.remove(itemId);
            return null;
        }
        if (previousExpanded != null && previousExpanded > 0 && previousExpanded != itemId) {
            expandedStatsHistoryItems.remove(previousExpanded);
        }
        return itemId;
    }

    void toggleHistoryExpanded(Set<Integer> expandedStatsHistoryItems, int itemId) {
        if (itemId <= 0) {
            return;
        }
        if (expandedStatsHistoryItems.contains(itemId)) {
            expandedStatsHistoryItems.remove(itemId);
        } else {
            expandedStatsHistoryItems.add(itemId);
        }
    }

    /**
     * Profit, cost and count over just the activities a filter admits.
     *
     * <p>Sliced per activity rather than per item, because an item is usually
     * both: Guardian boots that were flipped six times and assembled twice is
     * one row in the list and two very different answers here.
     */
    static StatsProfitSlice sliceActivities(Map<Integer, List<StatsFlipInstance>> historyByItem,
                                            StatsRecipeFilter filter) {
        StatsProfitSlice slice = new StatsProfitSlice();
        if (historyByItem == null || filter == null) {
            return slice;
        }
        for (List<StatsFlipInstance> history : historyByItem.values()) {
            if (history == null) {
                continue;
            }
            for (StatsFlipInstance instance : history) {
                if (instance == null || !instance.counted() || !filter.matchesKind(instance.conversionKind)) {
                    continue;
                }
                slice.profitGp += instance.profitGp;
                slice.costGp += instance.buyCostGp;
                slice.quantity += Math.max(0, instance.quantity);
                if (!instance.inProgress) {
                    slice.count += 1;
                }
            }
        }
        return slice;
    }

    /** What one filter's worth of activity adds up to. */
    static final class StatsProfitSlice {
        long profitGp;
        long costGp;
        long quantity;
        int count;

        double roiPercent() {
            return costGp > 0 ? (profitGp * 100.0) / costGp : 0.0;
        }
    }

    private Comparator<StatsItem> buildItemsComparator(StatsItemSort sort) {
        if (sort == StatsItemSort.ROI) {
            return Comparator
                .comparingDouble(this::safeRoi)
                .reversed()
                .thenComparing(Comparator.comparingLong(this::safeProfit).reversed());
        }
        if (sort == StatsItemSort.PROFIT) {
            return Comparator
                .comparingLong(this::safeProfit)
                .reversed()
                .thenComparing(Comparator.comparingLong(this::safeLastSellTs).reversed());
        }
        return Comparator
            .comparingLong(this::safeLastSellTs)
            .reversed()
            .thenComparing(Comparator.comparingLong(this::safeProfit).reversed());
    }

    private long safeProfit(StatsItem item) {
        return item != null && item.total_profit_gp != null ? item.total_profit_gp : 0L;
    }

    private long safeLastSellTs(StatsItem item) {
        return item != null && item.last_sell_ts_ms != null ? item.last_sell_ts_ms : 0L;
    }

    private double safeRoi(StatsItem item) {
        return item != null && item.roi_percent != null ? item.roi_percent : 0.0;
    }

    private void setLabel(JLabel label, String text, Color color) {
        if (label == null) {
            return;
        }
        label.setText(text);
        if (color != null) {
            label.setForeground(color);
        }
    }

    /**
     * Stops the session clock for good. It otherwise only stopped when the plugin reference had
     * gone, which never happened, so every disabled panel left one ticking.
     */
    void shutDown() {
        disposed = true;
        if (sessionTimer != null) {
            sessionTimer.stop();
            sessionTimer = null;
        }
    }
}
