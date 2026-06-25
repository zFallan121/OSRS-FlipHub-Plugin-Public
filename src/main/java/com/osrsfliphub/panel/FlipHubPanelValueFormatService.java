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

final class FlipHubPanelValueFormatService {
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

    String formatPercent(Double value) {
        if (value == null) {
            return "N/A";
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
