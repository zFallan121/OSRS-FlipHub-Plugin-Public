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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LinkSessionConfigStoreTest {
    @Test
    public void clearLinkStateWritesExpectedKeys() {
        RecordingHooks hooks = new RecordingHooks();
        LinkSessionConfigStore store = new LinkSessionConfigStore(hooks, "fliphub");

        store.clearLinkState();

        assertEquals(
            Arrays.asList(
                "fliphub:sessionToken=",
                "fliphub:signingSecret=",
                "fliphub:licenseKey=",
                "fliphub:linkCode=",
                "fliphub:unlinkNow=false"
            ),
            hooks.calls
        );
    }

    @Test
    public void persistLinkedSessionStoresCredentialsAndClearsInputs() {
        RecordingHooks hooks = new RecordingHooks();
        LinkSessionConfigStore store = new LinkSessionConfigStore(hooks, "fliphub");

        store.persistLinkedSession("token-1", "secret-1");

        assertEquals(
            Arrays.asList(
                "fliphub:sessionToken=token-1",
                "fliphub:signingSecret=secret-1",
                "fliphub:licenseKey=",
                "fliphub:linkCode="
            ),
            hooks.calls
        );
    }

    @Test
    public void persistLinkedSessionConvertsNullCredentialsToEmpty() {
        RecordingHooks hooks = new RecordingHooks();
        LinkSessionConfigStore store = new LinkSessionConfigStore(hooks, "fliphub");

        store.persistLinkedSession(null, null);

        assertEquals(
            Arrays.asList(
                "fliphub:sessionToken=",
                "fliphub:signingSecret=",
                "fliphub:licenseKey=",
                "fliphub:linkCode="
            ),
            hooks.calls
        );
    }

    private static final class RecordingHooks implements LinkSessionConfigStore.Hooks {
        private final List<String> calls = new ArrayList<>();

        @Override
        public void setString(String group, String key, String value) {
            calls.add(group + ":" + key + "=" + value);
        }

        @Override
        public void setBoolean(String group, String key, boolean value) {
            calls.add(group + ":" + key + "=" + value);
        }
    }
}
