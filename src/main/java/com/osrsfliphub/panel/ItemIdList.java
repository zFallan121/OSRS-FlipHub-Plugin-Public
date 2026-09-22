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
import java.util.stream.Collectors;

/**
 * How a set of item ids is stored in the config: "1,3,9". Bookmarks and hidden items both keep
 * their ids this way, and each used to carry its own identical copy of the two halves.
 */
final class ItemIdList {
    private ItemIdList() {
    }

    /** Repeats, zero, negatives and anything unreadable are dropped: the config is hand-editable. */
    static Set<Integer> parse(String raw) {
        Set<Integer> parsed = new HashSet<>();
        if (Str.isBlank(raw)) {
            return parsed;
        }
        for (String part : raw.split(",")) {
            try {
                int itemId = Integer.parseInt(part.trim());
                if (itemId > 0) {
                    parsed.add(itemId);
                }
            } catch (NumberFormatException ignored) {
                // Not an id; skipped like the rest.
            }
        }
        return parsed;
    }

    /** Sorted, so the same set is always written the same way. */
    static String join(Set<Integer> itemIds) {
        return itemIds == null ? "" : itemIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
    }
}
