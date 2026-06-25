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

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class LegacyLocalTradesFilterServiceTest {
    @Test
    public void filtersWipeBarrierEntriesAndDuplicatePayloads() {
        LegacyLocalTradesFilterService service = new LegacyLocalTradesFilterService(accountKey -> accountKey == 1L);
        Map<String, String> entries = new HashMap<>();
        entries.put("hash_1", "json_a");
        entries.put("profile_1", "json_a");
        entries.put("hash_2", "json_b");
        entries.put("garbage", "json_c");

        Map<String, String> filtered = service.filter(entries);

        assertEquals(2, filtered.size());
        assertFalse(filtered.containsKey("hash_1"));
        assertFalse(filtered.containsKey("profile_1"));
        assertTrue(filtered.containsKey("hash_2"));
        assertTrue(filtered.containsKey("garbage"));
    }

    @Test
    public void returnsOriginalMapWhenNullOrEmpty() {
        LegacyLocalTradesFilterService service = new LegacyLocalTradesFilterService(accountKey -> false);
        assertNull(service.filter(null));

        Map<String, String> empty = new HashMap<>();
        assertSame(empty, service.filter(empty));
    }
}
