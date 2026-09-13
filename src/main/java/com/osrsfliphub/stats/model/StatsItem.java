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

import java.util.*;

public class StatsItem {
    public int item_id;
    public String item_name;
    public Long total_profit_gp;
    public Long total_cost_gp;
    public Double roi_percent;
    public Integer total_qty;
    public Integer fill_count;
    public Long last_sell_ts_ms;
    /**
     * How long this item's money was tied up, summed over its completed flips.
     *
     * <p>Weighted per unit, so one purchase sold off in ten parts counts as one holding rather
     * than ten. Divided by the flip count it is what a flip of this item takes to come round,
     * which is the reading the card shows.
     */
    public Long active_ms;

    /**
     * Which activity kinds produced these totals. Local only - the API neither
     * sends nor receives it - and empty for an item that was only ever flipped.
     */
    public transient Set<ConversionKind> conversionKinds = EnumSet.noneOf(ConversionKind.class);

    /** Whether any of these totals came from an ordinary buy-then-sell flip. */
    public transient boolean hasPlainFlip;
}
