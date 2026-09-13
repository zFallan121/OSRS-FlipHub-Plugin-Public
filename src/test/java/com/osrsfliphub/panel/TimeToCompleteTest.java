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

/**
 * How long a flip of an item takes, as the card reports it.
 *
 * <p>This row used to be the clock time the last sale finished, with no date on it, so a flip
 * from three weeks ago read "19:21:08" and told the reader nothing they could act on. The
 * plugin was already measuring how long each flip's money stayed tied up, for gold per hour;
 * the card simply never asked for it.
 */
public class TimeToCompleteTest {
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    private final StatsItemFormatting formatting =
        new StatsItemFormatting(new PanelValueFormat());

    /** One flip, so the stored total is that flip. */
    @Test
    public void aSingleFlipReportsItsOwnDuration() {
        assertEquals("4h 12m", formatting.formatStatsTimeToComplete(item(4 * HOUR + 12 * MINUTE, 1)));
    }

    /** Several flips, so the stored total is divided between them. */
    @Test
    public void severalFlipsReportTheirAverage() {
        assertEquals("2h", formatting.formatStatsTimeToComplete(item(8 * HOUR, 4)));
    }

    /** The largest two units that carry anything, and no more. */
    @Test
    public void theSpanIsReadableAtEveryScale() {
        PanelValueFormat values = new PanelValueFormat();

        assertEquals("3d 4h", values.formatDurationCompact(3 * DAY + 4 * HOUR + 30 * MINUTE));
        assertEquals("2d", values.formatDurationCompact(2 * DAY));
        assertEquals("5h 1m", values.formatDurationCompact(5 * HOUR + MINUTE));
        assertEquals("45m", values.formatDurationCompact(45 * MINUTE));
        assertEquals("30s", values.formatDurationCompact(30_000L));
    }

    /** Under a second is still some time, and reporting "0s" would read as an error. */
    @Test
    public void aVeryShortFlipIsNotReportedAsNothing() {
        assertEquals("1s", new PanelValueFormat().formatDurationCompact(400L));
    }

    /**
     * An item with nothing completed against it, which is what a dismissed guess or an
     * unfinished break leaves behind. There is no duration to divide and none is invented.
     */
    @Test
    public void anItemWithNoCompletedFlipsSaysSo() {
        assertEquals("N/A", formatting.formatStatsTimeToComplete(item(4 * HOUR, 0)));
        assertEquals("N/A", formatting.formatStatsTimeToComplete(item(null, 2)));
        assertEquals("N/A", formatting.formatStatsTimeToComplete(item(0L, 2)));
        assertEquals("N/A", formatting.formatStatsTimeToComplete(null));
    }

    private static StatsItem item(Long activeMs, int flips) {
        StatsItem item = new StatsItem();
        item.item_id = 4151;
        item.active_ms = activeMs;
        item.fill_count = flips;
        return item;
    }
}
