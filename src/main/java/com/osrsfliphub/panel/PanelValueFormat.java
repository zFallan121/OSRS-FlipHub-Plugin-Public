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

import java.text.NumberFormat;
import java.util.Locale;

final class PanelValueFormat {
    String formatGp(Integer value) {
        if (value == null) {
            return "N/A";
        }
        return formatGpValue(value.longValue());
    }

    String formatGp(Long value) {
        if (value == null) {
            return "N/A";
        }
        return formatGpValue(value);
    }

    /**
     * Abbreviated gp for places where the full number would crowd out the text beside it. The
     * exact value is always still available somewhere - an expanded row, or a tooltip.
     */
    String formatGpCompact(Long value) {
        if (value == null) {
            return "N/A";
        }
        long abs = Math.abs(value);
        // The thresholds sit just under the round number so a value that would render as
        // "1000.0K" is promoted to "1.00M" instead.
        if (abs >= 999_950_000L) {
            return String.format(Locale.US, "%.2fB gp", value / 1_000_000_000.0);
        }
        if (abs >= 999_950L) {
            return String.format(Locale.US, "%.2fM gp", value / 1_000_000.0);
        }
        if (abs >= 9_995L) {
            return String.format(Locale.US, "%.1fK gp", value / 1_000.0);
        }
        return formatGpValue(value);
    }

    /**
     * Two decimal places, except for a return too small to show at that width.
     *
     * <p>Rounding alone printed a negative return as "-0.00%", which reads as a visible
     * negative zero and was painted red; and it printed a real, if tiny, gain as "0.00%",
     * which claims there was no return at all. A one-coin margin on an expensive item lands
     * in that band routinely.</p>
     */
    String formatPercent(Double value) {
        if (value == null) {
            return "N/A";
        }
        if (value != 0d && Math.abs(value) < 0.005d) {
            return value > 0d ? "<0.01%" : ">-0.01%";
        }
        return String.format(Locale.US, "%.2f%%", value);
    }

    String formatGpPerHour(Double value) {
        if (value == null) {
            return "N/A";
        }
        long rounded = Math.round(value);
        return formatNumber(rounded) + " gp/hr";
    }

    String formatNumber(long value) {
        NumberFormat formatter = NumberFormat.getIntegerInstance(Locale.US);
        return formatter.format(value);
    }

    String formatLimit(Integer remaining, Integer total) {
        if (total == null || total == 0) {
            return "N/A";
        }
        int remainingVal = remaining != null ? remaining : 0;
        return remainingVal + " / " + total;
    }

    String formatDuration(Long ms) {
        if (ms == null) {
            return "N/A";
        }
        return formatAgeClock(ms);
    }

    /**
     * A span of time in the largest two units that carry any of it: "3d 4h", "4h 12m", "45m".
     *
     * <p>The clock form beside this one is right for a countdown, where the seconds are the
     * point. For "how long does a flip of this take" they are noise, and a flip that took two
     * days reads as "51:20:00" there, which nobody parses as two days.
     */
    String formatDurationCompact(Long ms) {
        if (ms == null || ms <= 0L) {
            return "N/A";
        }
        long totalSeconds = ms / 1000L;
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        if (days > 0) {
            return hours > 0 ? days + "d " + hours + "h" : days + "d";
        }
        if (hours > 0) {
            return minutes > 0 ? hours + "h " + minutes + "m" : hours + "h";
        }
        if (minutes > 0) {
            return minutes + "m";
        }
        return Math.max(1L, totalSeconds) + "s";
    }

    String formatAgeClock(long ms) {
        long totalSeconds = Math.max(0, ms / 1000);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    private String formatGpValue(long value) {
        return formatNumber(value) + " gp";
    }
}
