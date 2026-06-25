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

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class OfferUpdateStampStoreTest {
    @Test
    public void parseIncludesOnlyValidSlotRangeAndNonNullEntries() {
        Gson gson = new Gson();
        String raw = "{"
            + "\"0\":{\"itemId\":100},"
            + "\"7\":{\"itemId\":200},"
            + "\"8\":{\"itemId\":300},"
            + "\"bad\":{\"itemId\":400},"
            + "\"1\":null"
            + "}";

        Map<Integer, OfferUpdateStamp> parsed = OfferUpdateStampStore.parse(raw, gson, 0, 7);

        assertEquals(2, parsed.size());
        assertNotNull(parsed.get(0));
        assertNotNull(parsed.get(7));
        assertFalse(parsed.containsKey(8));
    }

    @Test
    public void parseReturnsEmptyForMalformedJson() {
        Map<Integer, OfferUpdateStamp> parsed = OfferUpdateStampStore.parse("{bad json", new Gson(), 0, 7);
        assertTrue(parsed.isEmpty());
    }

    @Test
    public void serializeSortsKeysAndSkipsNullEntries() {
        Gson gson = new Gson();
        Map<Integer, OfferUpdateStamp> stamps = new HashMap<>();
        stamps.put(2, new OfferUpdateStamp(200, 10, 20, 1, true, 10L, 1L, 1L, 0L, 0L));
        stamps.put(1, new OfferUpdateStamp(100, 10, 20, 1, true, 10L, 1L, 1L, 0L, 0L));
        stamps.put(null, new OfferUpdateStamp(999, 10, 20, 1, true, 10L, 1L, 1L, 0L, 0L));
        stamps.put(3, null);

        String json = OfferUpdateStampStore.serialize(stamps, gson);

        assertTrue(json.contains("\"1\""));
        assertTrue(json.contains("\"2\""));
        assertFalse(json.contains("\"3\""));
        assertFalse(json.contains("999"));
        assertTrue(json.indexOf("\"1\"") < json.indexOf("\"2\""));
    }
}
