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
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import okhttp3.OkHttpClient;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BackfillUploaderTest {
    @Test
    public void buildBackfillEventBuildsExpectedFields() {
        BackfillUploader uploader = new BackfillUploader(new TestHooks());
        LocalTradeDelta delta = new LocalTradeDelta(
            1_700_000_000_000L,
            3,
            4151,
            true,
            4,
            2_000L,
            "OFFER_UPDATED",
            0,
            false
        );

        GeEvent event = uploader.buildBackfillEvent(123L, delta, 301);

        assertNotNull(event);
        assertEquals("OFFER_UPDATED", event.event_type);
        assertEquals(500, event.price);
        assertEquals(4, event.delta_qty);
        assertEquals(2_000L, event.delta_gp);
        assertEquals("BUYING", event.state);
        assertEquals(Integer.valueOf(301), event.world);
    }

    @Test
    public void sendBatchMarksBlockedWhenNotLinked() {
        MutableConfig config = new MutableConfig("", "");
        TestHooks hooks = new TestHooks();
        BackfillUploader uploader = new BackfillUploader(hooks);

        boolean ok = uploader.sendBatch(new ScriptedApiClient(), config, Arrays.asList(sampleEvent()));

        assertFalse(ok);
        assertTrue(hooks.blockedReason.contains("not linked"));
    }

    @Test
    public void sendBatchRefreshesAndRetriesOnUnauthorized() {
        ScriptedApiClient api = new ScriptedApiClient(
            response(401, 0, 0, 0),
            response(200, 1, 0, 0)
        );
        MutableConfig config = new MutableConfig("token", "secret");
        TestHooks hooks = new TestHooks();
        hooks.refreshShouldSucceed = true;
        hooks.onRefresh = () -> {
            config.sessionToken = "token2";
            config.signingSecret = "secret2";
        };
        BackfillUploader uploader = new BackfillUploader(hooks);

        boolean ok = uploader.sendBatch(api, config, Arrays.asList(sampleEvent()));

        assertTrue(ok);
        assertEquals(1, hooks.refreshAttempts);
        assertEquals(1, hooks.successUploaded);
        assertEquals(0, hooks.clearSessionCalls);
    }

    @Test
    public void sendBatchRejectsUnusableUploadResponse() {
        ScriptedApiClient api = new ScriptedApiClient(response(200, 0, 0, 2));
        MutableConfig config = new MutableConfig("token", "secret");
        TestHooks hooks = new TestHooks();
        BackfillUploader uploader = new BackfillUploader(hooks);

        boolean ok = uploader.sendBatch(api, config, Arrays.asList(sampleEvent()));

        assertFalse(ok);
        assertEquals(Integer.valueOf(200), hooks.lastFailureStatus);
        assertTrue(hooks.failureMessage.contains("rejected all events"));
    }

    private static GeEvent sampleEvent() {
        GeEvent event = new GeEvent();
        event.event_id = "evt-1";
        event.event_type = "OFFER_UPDATED";
        event.ts_client_ms = System.currentTimeMillis();
        event.slot = 1;
        event.item_id = 4151;
        event.is_buy = true;
        event.price = 1000;
        event.delta_qty = 1;
        event.delta_gp = 1000L;
        return event;
    }

    private static ApiClient.EventUploadResponse response(int status, Integer accepted, Integer duplicates, Integer rejected) {
        ApiClient.EventUploadResponse response = new ApiClient.EventUploadResponse();
        response.status_code = status;
        response.accepted = accepted;
        response.duplicates = duplicates;
        response.rejected = rejected;
        return response;
    }

    private static final class ScriptedApiClient extends ApiClient {
        private final Deque<ApiClient.EventUploadResponse> responses = new ArrayDeque<>();

        private ScriptedApiClient(ApiClient.EventUploadResponse... scriptedResponses) {
            super(new OkHttpClient(), new Gson());
            if (scriptedResponses != null) {
                responses.addAll(Arrays.asList(scriptedResponses));
            }
        }

        @Override
        public ApiClient.EventUploadResponse sendEventsDetailed(String sessionToken, String signingSecret, List<GeEvent> events) {
            ApiClient.EventUploadResponse next = responses.pollFirst();
            if (next != null) {
                return next;
            }
            return response(200, events != null ? events.size() : 0, 0, 0);
        }
    }

    private static final class MutableConfig implements PluginConfig {
        private String sessionToken;
        private String signingSecret;

        private MutableConfig(String sessionToken, String signingSecret) {
            this.sessionToken = sessionToken;
            this.signingSecret = signingSecret;
        }

        @Override
        public String sessionToken() {
            return sessionToken;
        }

        @Override
        public String signingSecret() {
            return signingSecret;
        }
    }

    private static final class TestHooks implements BackfillUploader.Hooks {
        private String blockedReason = "";
        private int refreshAttempts = 0;
        private boolean refreshShouldSucceed;
        private Runnable onRefresh;
        private int clearSessionCalls = 0;
        private int successUploaded = 0;
        private Integer lastFailureStatus;
        private String failureMessage = "";

        @Override
        public boolean attemptRefresh(String currentToken) {
            refreshAttempts++;
            if (refreshShouldSucceed && onRefresh != null) {
                onRefresh.run();
            }
            return refreshShouldSucceed;
        }

        @Override
        public void clearSession() {
            clearSessionCalls++;
        }

        @Override
        public void setUploadBlocked(String reason) {
            blockedReason = reason != null ? reason : "";
        }

        @Override
        public void recordUploadAttempt() {
        }

        @Override
        public void recordUploadSuccess(int uploadedCount, int statusCode) {
            successUploaded += uploadedCount;
        }

        @Override
        public void recordUploadFailure(Integer statusCode, String errorMessage, boolean dropped, int droppedCount) {
            lastFailureStatus = statusCode;
            failureMessage = errorMessage != null ? errorMessage : "";
        }
    }
}
