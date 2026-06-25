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
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

final class OfferUpdateStampStore {
    private OfferUpdateStampStore() {
    }

    static Map<Integer, OfferUpdateStamp> parse(String raw, Gson gson, int minSlot, int maxSlot) {
        Map<Integer, OfferUpdateStamp> result = new HashMap<>();
        if (gson == null || raw == null || raw.trim().isEmpty()) {
            return result;
        }
        try {
            Type type = new TypeToken<Map<String, OfferUpdateStamp>>() {}.getType();
            Map<String, OfferUpdateStamp> parsed = gson.fromJson(raw, type);
            if (parsed == null || parsed.isEmpty()) {
                return result;
            }
            for (Map.Entry<String, OfferUpdateStamp> entry : parsed.entrySet()) {
                if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                try {
                    int slot = Integer.parseInt(entry.getKey());
                    if (slot >= minSlot && slot <= maxSlot) {
                        result.put(slot, entry.getValue());
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (JsonParseException ignored) {
        }
        return result;
    }

    static String serialize(Map<Integer, OfferUpdateStamp> stamps, Gson gson) {
        if (gson == null) {
            return "{}";
        }
        Map<String, OfferUpdateStamp> output = new TreeMap<>();
        if (stamps != null && !stamps.isEmpty()) {
            for (Map.Entry<Integer, OfferUpdateStamp> entry : stamps.entrySet()) {
                if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                output.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return gson.toJson(output);
    }
}
