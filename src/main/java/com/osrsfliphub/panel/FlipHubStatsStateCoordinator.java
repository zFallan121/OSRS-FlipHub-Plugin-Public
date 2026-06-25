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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class FlipHubStatsStateCoordinator {
    static final class Result {
        final List<StatsItem> statsItems;
        final Map<Integer, List<StatsFlipInstance>> flipHistoryByItem;
        final Integer expandedStatsItemId;

        Result(List<StatsItem> statsItems,
               Map<Integer, List<StatsFlipInstance>> flipHistoryByItem,
               Integer expandedStatsItemId) {
            this.statsItems = statsItems;
            this.flipHistoryByItem = flipHistoryByItem;
            this.expandedStatsItemId = expandedStatsItemId;
        }
    }

    Result normalize(StatsSummary summary,
                     List<StatsItem> items,
                     Map<Integer, List<StatsFlipInstance>> historyByItem,
                     Integer expandedStatsItemId,
                     Set<Integer> expandedStatsHistoryItems) {
        List<StatsItem> normalizedItems = items != null ? items : new ArrayList<>();
        Map<Integer, List<StatsFlipInstance>> normalizedHistory = historyByItem != null ? historyByItem : new HashMap<>();

        Integer normalizedExpandedItemId = expandedStatsItemId;
        if (normalizedExpandedItemId != null) {
            boolean expandedStillVisible = false;
            for (StatsItem statsItem : normalizedItems) {
                if (statsItem != null && statsItem.item_id == normalizedExpandedItemId) {
                    expandedStillVisible = true;
                    break;
                }
            }
            if (!expandedStillVisible) {
                normalizedExpandedItemId = null;
            }
        }

        Set<Integer> visibleItemIds = new HashSet<>();
        for (StatsItem statsItem : normalizedItems) {
            if (statsItem != null && statsItem.item_id > 0) {
                visibleItemIds.add(statsItem.item_id);
            }
        }
        expandedStatsHistoryItems.retainAll(visibleItemIds);
        expandedStatsHistoryItems.removeIf(itemId -> itemId == null || !normalizedHistory.containsKey(itemId));

        return new Result(normalizedItems, normalizedHistory, normalizedExpandedItemId);
    }
}
