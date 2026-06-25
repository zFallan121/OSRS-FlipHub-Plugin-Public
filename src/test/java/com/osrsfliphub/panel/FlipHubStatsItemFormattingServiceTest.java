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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FlipHubStatsItemFormattingServiceTest {
    @Test
    public void avgSellUsesFloorDivisionToAvoidRoundingUp() {
        FlipHubStatsItemFormattingService service = new FlipHubStatsItemFormattingService(new FlipHubPanelValueFormatService());
        StatsItem item = new StatsItem();
        item.total_qty = 2;
        item.total_cost_gp = 1_152L;
        item.total_profit_gp = -47L; // revenue = 1105, exact average = 552.5

        assertEquals("552 gp", service.formatStatsAvgSell(item));
    }

    @Test
    public void avgBuyUsesFloorDivision() {
        FlipHubStatsItemFormattingService service = new FlipHubStatsItemFormattingService(new FlipHubPanelValueFormatService());
        StatsItem item = new StatsItem();
        item.total_qty = 2;
        item.total_cost_gp = 1_153L; // exact average = 576.5

        assertEquals("576 gp", service.formatStatsAvgBuy(item));
    }
}
