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

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FlipHubStatsRenderCoordinatorTest {
    @Test
    public void toggleItemExpandedCollapsingCurrentItemClearsHistoryExpansion() {
        FlipHubStatsRenderCoordinator coordinator = new FlipHubStatsRenderCoordinator();
        Set<Integer> expandedHistoryItems = new HashSet<>();
        expandedHistoryItems.add(4151);

        Integer nextExpanded = coordinator.toggleItemExpanded(4151, expandedHistoryItems, 4151);

        assertNull(nextExpanded);
        assertFalse(expandedHistoryItems.contains(4151));
    }

    @Test
    public void toggleItemExpandedSwitchingItemClearsPreviousHistoryExpansion() {
        FlipHubStatsRenderCoordinator coordinator = new FlipHubStatsRenderCoordinator();
        Set<Integer> expandedHistoryItems = new HashSet<>();
        expandedHistoryItems.add(4151);
        expandedHistoryItems.add(11840);

        Integer nextExpanded = coordinator.toggleItemExpanded(4151, expandedHistoryItems, 11840);

        assertEquals(Integer.valueOf(11840), nextExpanded);
        assertFalse(expandedHistoryItems.contains(4151));
        assertTrue(expandedHistoryItems.contains(11840));
    }

    @Test
    public void toggleHistoryExpandedAddsThenRemovesItem() {
        FlipHubStatsRenderCoordinator coordinator = new FlipHubStatsRenderCoordinator();
        Set<Integer> expandedHistoryItems = new HashSet<>();

        coordinator.toggleHistoryExpanded(expandedHistoryItems, 4151);
        assertTrue(expandedHistoryItems.contains(4151));

        coordinator.toggleHistoryExpanded(expandedHistoryItems, 4151);
        assertFalse(expandedHistoryItems.contains(4151));
    }

    @Test
    public void toggleItemExpandedIgnoresInvalidItemId() {
        FlipHubStatsRenderCoordinator coordinator = new FlipHubStatsRenderCoordinator();
        Set<Integer> expandedHistoryItems = new HashSet<>();
        expandedHistoryItems.add(4151);

        Integer nextExpanded = coordinator.toggleItemExpanded(4151, expandedHistoryItems, 0);

        assertEquals(Integer.valueOf(4151), nextExpanded);
        assertTrue(expandedHistoryItems.contains(4151));
    }
}

