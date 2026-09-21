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

import java.util.Comparator;
import java.util.function.ToLongFunction;

public enum StatsItemSort {
    COMPLETION("Completion"),
    PROFIT("Profit"),
    ROI("ROI");

    private final String label;

    StatsItemSort(String label) {
        this.label = label;
    }

    static StatsItemSort fromName(String name) {
        if (name != null) {
            for (StatsItemSort sort : values()) {
                if (sort.name().equalsIgnoreCase(name.trim())) {
                    return sort;
                }
            }
        }
        return COMPLETION;
    }

    /**
     * Best first for a sort, the other figure breaking ties. A missing sort is COMPLETION and a
     * missing figure counts as zero.
     */
    static Comparator<StatsItem> comparatorFor(StatsItemSort sort) {
        ToLongFunction<StatsItem> profit =
            item -> item != null && item.total_profit_gp != null ? item.total_profit_gp : 0L;
        ToLongFunction<StatsItem> lastSell =
            item -> item != null && item.last_sell_ts_ms != null ? item.last_sell_ts_ms : 0L;
        Comparator<StatsItem> byProfit = Comparator.comparingLong(profit).reversed();
        Comparator<StatsItem> byLastSell = Comparator.comparingLong(lastSell).reversed();
        if (sort == ROI) {
            return Comparator
                .comparingDouble((StatsItem item) -> item != null && item.roi_percent != null ? item.roi_percent : 0.0)
                .reversed()
                .thenComparing(byProfit);
        }
        return sort == PROFIT ? byProfit.thenComparing(byLastSell) : byLastSell.thenComparing(byProfit);
    }

    @Override
    public String toString() {
        return label;
    }
}
