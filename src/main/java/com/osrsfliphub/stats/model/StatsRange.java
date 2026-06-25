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

public enum StatsRange {
    SESSION("Session", RangeType.SESSION, 0),
    LAST_HOUR("Last 1h", RangeType.RELATIVE, 1),
    LAST_4H("Last 4h", RangeType.RELATIVE, 4),
    LAST_24H("Last 24h", RangeType.RELATIVE, 24),
    LAST_7D("Last 7d", RangeType.RELATIVE, 168),
    ALL_TIME("All time", RangeType.ALL_TIME, 0);

    private enum RangeType {
        SESSION,
        RELATIVE,
        ALL_TIME
    }

    private final String label;
    private final RangeType type;
    private final int hours;

    StatsRange(String label, RangeType type, int hours) {
        this.label = label;
        this.type = type;
        this.hours = hours;
    }

    public Long getSinceMs(long sessionStartMs, long nowMs) {
        if (type == RangeType.ALL_TIME) {
            return null;
        }
        if (type == RangeType.SESSION) {
            return sessionStartMs > 0 ? sessionStartMs : null;
        }
        if (hours <= 0) {
            return null;
        }
        long deltaMs = hours * 60L * 60L * 1000L;
        return Math.max(0L, nowMs - deltaMs);
    }

    @Override
    public String toString() {
        return label;
    }
}
