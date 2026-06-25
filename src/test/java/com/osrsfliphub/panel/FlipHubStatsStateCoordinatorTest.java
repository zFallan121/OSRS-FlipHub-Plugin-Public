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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FlipHubStatsStateCoordinatorTest {
    @Test
    public void normalizeClearsExpandedItemWhenNoLongerVisibleAndPrunesHistoryExpansionSet() {
        FlipHubStatsStateCoordinator coordinator = new FlipHubStatsStateCoordinator();
        List<StatsItem> items = Arrays.asList(item(4151), item(11840));
        Map<Integer, List<StatsFlipInstance>> historyByItem = new HashMap<>();
        historyByItem.put(4151, new ArrayList<StatsFlipInstance>());

        Set<Integer> expandedHistoryItems = new HashSet<>();
        expandedHistoryItems.add(4151);
        expandedHistoryItems.add(11840);
        expandedHistoryItems.add(995);
        expandedHistoryItems.add(null);

        FlipHubStatsStateCoordinator.Result result = coordinator.normalize(
            null,
            items,
            historyByItem,
            995,
            expandedHistoryItems
        );

        assertNull(result.expandedStatsItemId);
        assertTrue(expandedHistoryItems.contains(4151));
        assertFalse(expandedHistoryItems.contains(11840));
        assertFalse(expandedHistoryItems.contains(995));
        assertFalse(expandedHistoryItems.contains(null));
    }

    @Test
    public void normalizeKeepsExpandedItemWhenVisible() {
        FlipHubStatsStateCoordinator coordinator = new FlipHubStatsStateCoordinator();
        List<StatsItem> items = Arrays.asList(item(4151));
        Map<Integer, List<StatsFlipInstance>> historyByItem = new HashMap<>();
        historyByItem.put(4151, new ArrayList<StatsFlipInstance>());
        Set<Integer> expandedHistoryItems = new HashSet<>();
        expandedHistoryItems.add(4151);

        FlipHubStatsStateCoordinator.Result result = coordinator.normalize(
            null,
            items,
            historyByItem,
            4151,
            expandedHistoryItems
        );

        assertEquals(Integer.valueOf(4151), result.expandedStatsItemId);
        assertTrue(expandedHistoryItems.contains(4151));
    }

    private static StatsItem item(int itemId) {
        StatsItem item = new StatsItem();
        item.item_id = itemId;
        return item;
    }
}

