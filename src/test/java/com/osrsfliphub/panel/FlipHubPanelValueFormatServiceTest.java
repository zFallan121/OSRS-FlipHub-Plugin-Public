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

public class FlipHubPanelValueFormatServiceTest {
    private final FlipHubPanelValueFormatService service = new FlipHubPanelValueFormatService();

    @Test
    public void formatGpSupportsIntegerAndLong() {
        assertEquals("N/A", service.formatGp((Integer) null));
        assertEquals("N/A", service.formatGp((Long) null));
        assertEquals("12,345 gp", service.formatGp(Integer.valueOf(12_345)));
        assertEquals("12,345 gp", service.formatGp(Long.valueOf(12_345L)));
    }

    @Test
    public void formatPercentAndGpPerHourUseExpectedPrecision() {
        assertEquals("N/A", service.formatPercent(null));
        assertEquals("4.13%", service.formatPercent(4.125));
        assertEquals("N/A", service.formatGpPerHour(null));
        assertEquals("1,235 gp/hr", service.formatGpPerHour(1_234.5));
    }

    @Test
    public void formatNumberAndLimitHandleEdgeCases() {
        assertEquals("-1,000", service.formatNumber(-1_000));
        assertEquals("0 / 100", service.formatLimit(null, 100));
        assertEquals("9 / 100", service.formatLimit(9, 100));
        assertEquals("N/A", service.formatLimit(9, 0));
        assertEquals("N/A", service.formatLimit(9, null));
    }

    @Test
    public void formatDurationAndAgeClockClampAndFormatTime() {
        assertEquals("N/A", service.formatDuration(null));
        assertEquals("01:01:01", service.formatDuration(3_661_000L));
        assertEquals("00:00:00", service.formatAgeClock(-1_000L));
        assertEquals("25:00:05", service.formatAgeClock(90_005_000L));
    }
}
