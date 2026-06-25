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
import okhttp3.OkHttpClient;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WikiPriceFactoryServiceTest {
    @Test
    public void createBuildsServiceThatDelegatesToRuntimeHooks() {
        WikiPriceFactoryService factory = new WikiPriceFactoryService(
            1_000L,
            0L,
            "https://example.invalid/latest",
            "FlipHub Test"
        );
        TestRuntimeHooks hooks = new TestRuntimeHooks();
        hooks.panelVisible = true;
        hooks.debugEnabled = true;
        hooks.httpClient = null;
        hooks.gson = null;

        WikiPriceService service = factory.create(hooks);
        service.refreshPrices();

        assertTrue(hooks.isPanelVisibleCalls > 0);
        assertTrue(hooks.getHttpClientCalls > 0);
        assertTrue(hooks.getGsonCalls > 0);
        assertTrue(hooks.isDebugEnabledCalls > 0);
        assertTrue(hooks.logDebugCalls > 0);
        assertTrue(hooks.lastDebugMessage != null && hooks.lastDebugMessage.contains("Wiki fetch unavailable"));
    }

    @Test
    public void createWithNullRuntimeHooksReturnsNoopService() {
        WikiPriceFactoryService factory = new WikiPriceFactoryService(
            1_000L,
            0L,
            "https://example.invalid/latest",
            "FlipHub Test"
        );
        WikiPriceService service = factory.create(null);

        service.refreshPrices();
        WikiPriceEntry entry = service.getPriceEntry(4151, true);

        assertNull(entry);
    }

    private static final class TestRuntimeHooks implements WikiPriceFactoryService.RuntimeHooks {
        private boolean panelVisible;
        private boolean debugEnabled;
        private OkHttpClient httpClient;
        private Gson gson;
        private int isPanelVisibleCalls;
        private int isDebugEnabledCalls;
        private int logDebugCalls;
        private int getHttpClientCalls;
        private int getGsonCalls;
        private String lastDebugMessage;

        @Override
        public boolean isPanelVisible() {
            isPanelVisibleCalls++;
            return panelVisible;
        }

        @Override
        public boolean isDebugEnabled() {
            isDebugEnabledCalls++;
            return debugEnabled;
        }

        @Override
        public void logDebug(String message) {
            logDebugCalls++;
            lastDebugMessage = message;
        }

        @Override
        public OkHttpClient getHttpClient() {
            getHttpClientCalls++;
            return httpClient;
        }

        @Override
        public Gson getGson() {
            getGsonCalls++;
            return gson;
        }
    }
}
