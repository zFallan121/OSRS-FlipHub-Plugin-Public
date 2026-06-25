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

import static com.osrsfliphub.FlipHubPanelConstants.DANGER;
import static com.osrsfliphub.FlipHubPanelConstants.SUCCESS;
import static com.osrsfliphub.FlipHubPanelConstants.TEXT;
import static com.osrsfliphub.FlipHubPanelConstants.WARNING;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;

final class FlipHubStatsRenderCoordinator {
    void updateSummary(StatsSummary statsSummary,
                       FlipHubPanelValueFormatService valueFormatService,
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
            setLabel(statsTotalProfitValue, "0 gp", WARNING);
            setLabel(statsRoiValue, "0.00%", TEXT);
            setLabel(statsFlipsValue, "0", null);
            setLabel(statsTaxValue, "0 gp", null);
            setLabel(statsSessionTimeValue, "00:00:00", null);
            setLabel(statsHourlyValue, "0 gp/hr", SUCCESS);
            return;
        }

        long totalProfit = statsSummary.total_profit_gp != null ? statsSummary.total_profit_gp : 0;
        setLabel(statsTotalProfitValue, valueFormatService.formatGp(totalProfit), totalProfit >= 0 ? WARNING : DANGER);

        Double roi = statsSummary.roi_percent;
        setLabel(statsRoiValue, valueFormatService.formatPercent(roi), roi != null && roi < 0 ? DANGER : TEXT);

        int flips = statsSummary.fill_count != null ? statsSummary.fill_count : 0;
        setLabel(statsFlipsValue, String.valueOf(flips), null);

        setLabel(statsTaxValue, valueFormatService.formatGp(statsSummary.tax_paid_gp), null);
        setLabel(statsSessionTimeValue, valueFormatService.formatDuration(statsSummary.active_ms), null);

        Double gpPerHour = statsSummary.gp_per_hour;
        setLabel(
            statsHourlyValue,
            valueFormatService.formatGpPerHour(gpPerHour),
            gpPerHour != null && gpPerHour < 0 ? DANGER : SUCCESS
        );
    }

    void renderItems(JPanel statsItemsListPanel,
                     List<StatsItem> statsItems,
                     String statsSearchQuery,
                     StatsItemSort sort,
                     boolean statsSortAscending,
                     Function<StatsItem, JPanel> statsItemCardBuilder,
                     BiFunction<String, String, JPanel> emptyCardBuilder) {
        if (statsItemsListPanel == null) {
            return;
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
            filtered.add(item);
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

        if (filtered.isEmpty()) {
            if (emptyCardBuilder != null) {
                if (normalizedQuery.isEmpty()) {
                    statsItemsListPanel.add(emptyCardBuilder.apply("No stats yet", "Make a trade to see your items here."));
                } else {
                    statsItemsListPanel.add(emptyCardBuilder.apply("No matches", "Try a different search term."));
                }
            }
        } else if (statsItemCardBuilder != null) {
            for (StatsItem item : filtered) {
                statsItemsListPanel.add(statsItemCardBuilder.apply(item));
                statsItemsListPanel.add(Box.createVerticalStrut(6));
            }
        }

        statsItemsListPanel.revalidate();
        statsItemsListPanel.repaint();
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
}
