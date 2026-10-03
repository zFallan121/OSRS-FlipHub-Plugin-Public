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
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * What the website answers about figures: its real answers, made by its own code from its test
 * book and copied here as they came. No test writes an answer of its own.
 *
 * <p>The book they were made from: MAIN sold five whips in one offer (three bought at 1,000 and two
 * at 1,101, so 1,040 each, sold for 1,235 each after tax: +973), ten dragon bones (+680) and an
 * assembled Dharok's set (+400,000); ALT sold one whip (+235); and 100 nature runes (+100) and a
 * repaired shark (+90) were sold with no character's tag on them. 402,078 from six flips in all:
 * 401,653 from three on MAIN, 235 from one on ALT, 190 from two that are no character's.
 */
final class FigureFixtures {
    /** When the website worked out the account's figures. */
    static final long AS_OF_MS = entry("figures_live.json", "account_all_time").get("as_of_ms").getAsLong();

    private FigureFixtures() {
    }

    /** One answer of the website with its switch on, as it sent it. */
    static String live(String name) {
        // Written out by the element itself, which keeps a null where the website sent one.
        return entry("figures_live.json", name).toString();
    }

    /** The answer with the switch off: {@code {"live": false}} and nothing else. */
    static String off(String name) {
        return entry("figures_off.json", name).toString();
    }

    /** More than 180 requests in ten minutes: a 429 that says to wait 37 seconds, in its body and in a header. */
    static RecordSyncWorld.Reply rateLimited() {
        JsonObject refused = file("figures_429.json");
        return new RecordSyncWorld.Reply(refused.get("status").getAsInt(), refused.get("body").toString(),
            refused.get("retry_after_header").getAsString());
    }

    /** A refusal: {@code no_token} is the 401, {@code bad_since} the 400. */
    static RecordSyncWorld.Reply refusal(String name) {
        JsonObject refused = entry("figures_live.json", name);
        return new RecordSyncWorld.Reply(refused.get("status").getAsInt(), refused.get("body").toString());
    }

    private static JsonObject entry(String file, String name) {
        JsonObject found = file(file).getAsJsonObject(name);
        if (found == null) {
            throw new AssertionError(file + " has no answer called " + name);
        }
        return found;
    }

    private static JsonObject file(String name) {
        try (InputStream in = FigureFixtures.class.getResourceAsStream("/com/osrsfliphub/" + name)) {
            if (in == null) {
                throw new AssertionError("src/test/resources/com/osrsfliphub/" + name + " is not on the test classpath");
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return new Gson().fromJson(reader, JsonObject.class);
            }
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }
}
