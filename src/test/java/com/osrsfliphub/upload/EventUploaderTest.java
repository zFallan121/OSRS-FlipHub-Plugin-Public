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
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import okhttp3.OkHttpClient;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EventUploaderTest {
    private static final Logger LOG = LoggerFactory.getLogger(EventUploaderTest.class);

    @Test
    public void flushEventsRequeuesOnServerError() throws Exception {
        ScriptedApiClient api = new ScriptedApiClient(500);
        MutableConfig config = new MutableConfig("token", "secret");
        TestHooks hooks = new TestHooks();
        hooks.queue.add(sampleEvent("e1"));

        EventUploader.flushEvents(api, config, 200, hooks, LOG);

        assertEquals(1, hooks.requeuedBatches.size());
        assertEquals(500, hooks.lastFailureStatus.intValue());
        assertTrue(hooks.failureMessage.contains("queued for retry"));
        assertEquals(0, hooks.successUploaded);
    }

    @Test
    public void flushEventsRetriesAfterRefresh() throws Exception {
        ScriptedApiClient api = new ScriptedApiClient(401, 200);
        MutableConfig config = new MutableConfig("token", "secret");
        TestHooks hooks = new TestHooks();
        hooks.queue.add(sampleEvent("e2"));
        hooks.refreshShouldSucceed = true;
        hooks.onRefresh = () -> {
            config.sessionToken = "token2";
            config.signingSecret = "secret2";
        };

        EventUploader.flushEvents(api, config, 200, hooks, LOG);

        assertEquals(1, hooks.refreshAttempts);
        assertEquals(1, hooks.successUploaded);
        assertEquals(200, hooks.lastSuccessStatus);
        assertTrue(hooks.requeuedBatches.isEmpty());
    }

    @Test
    public void flushEventsUnauthorizedWithoutRefreshRecoveryRequeuesAndClearsSession() throws Exception {
        ScriptedApiClient api = new ScriptedApiClient(401);
        MutableConfig config = new MutableConfig("token", "secret");
        TestHooks hooks = new TestHooks();
        hooks.queue.add(sampleEvent("e3"));

        EventUploader.flushEvents(api, config, 200, hooks, LOG);

        assertEquals(1, hooks.refreshAttempts);
        assertEquals(1, hooks.requeuedBatches.size());
        assertEquals(401, hooks.lastFailureStatus.intValue());
        assertTrue(hooks.failureMessage.contains("Session refresh did not recover auth"));
        assertEquals(1, hooks.clearSessionCalls);
        assertEquals(1, hooks.profileHeaderUpdateCalls);
        assertEquals(0, hooks.successUploaded);
    }

    @Test
    public void flushEventsUnauthorizedAfterRefreshRequeuesAndClearsSession() throws Exception {
        ScriptedApiClient api = new ScriptedApiClient(401, 403);
        MutableConfig config = new MutableConfig("token", "secret");
        TestHooks hooks = new TestHooks();
        hooks.queue.add(sampleEvent("e4"));
        hooks.refreshShouldSucceed = true;
        hooks.onRefresh = () -> {
            config.sessionToken = "token2";
            config.signingSecret = "secret2";
        };

        EventUploader.flushEvents(api, config, 200, hooks, LOG);

        assertEquals(1, hooks.refreshAttempts);
        assertEquals(1, hooks.requeuedBatches.size());
        assertEquals(403, hooks.lastFailureStatus.intValue());
        assertTrue(hooks.failureMessage.contains("Upload rejected with status 403"));
        assertEquals(1, hooks.clearSessionCalls);
        assertEquals(1, hooks.profileHeaderUpdateCalls);
        assertEquals(0, hooks.successUploaded);
    }

    @Test
    public void flushEventsClientErrorDropsBatch() throws Exception {
        ScriptedApiClient api = new ScriptedApiClient(400);
        MutableConfig config = new MutableConfig("token", "secret");
        TestHooks hooks = new TestHooks();
        hooks.queue.add(sampleEvent("e5"));

        EventUploader.flushEvents(api, config, 200, hooks, LOG);

        assertTrue(hooks.requeuedBatches.isEmpty());
        assertEquals(400, hooks.lastFailureStatus.intValue());
        assertTrue(hooks.lastFailureDropped);
        assertEquals(1, hooks.lastFailureDroppedCount);
        assertEquals(0, hooks.successUploaded);
    }

    private GeEvent sampleEvent(String id) {
        GeEvent event = new GeEvent();
        event.event_id = id;
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

    private static final class ScriptedApiClient extends ApiClient {
        private final Deque<Integer> statuses = new ArrayDeque<>();

        private ScriptedApiClient(int... scriptedStatuses) {
            super(new OkHttpClient(), new Gson());
            for (int status : scriptedStatuses) {
                statuses.addLast(status);
            }
        }

        @Override
        public int sendEvents(String sessionToken, String signingSecret, List<GeEvent> events) {
            Integer status = statuses.pollFirst();
            return status != null ? status : 200;
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

    private static final class TestHooks implements EventUploader.Hooks {
        private final Deque<GeEvent> queue = new ArrayDeque<>();
        private final List<List<GeEvent>> requeuedBatches = new ArrayList<>();
        private Integer lastFailureStatus;
        private String failureMessage = "";
        private int successUploaded = 0;
        private int lastSuccessStatus = 0;
        private int refreshAttempts = 0;
        private boolean refreshShouldSucceed = false;
        private Runnable onRefresh;
        private int clearSessionCalls = 0;
        private int profileHeaderUpdateCalls = 0;
        private boolean lastFailureDropped = false;
        private int lastFailureDroppedCount = 0;

        @Override
        public boolean isClientLoggedIn() {
            return true;
        }

        @Override
        public int getPendingUploadEvents() {
            return queue.size();
        }

        @Override
        public GeEvent dequeueEvent() {
            return queue.pollFirst();
        }

        @Override
        public void requeue(List<GeEvent> batch) {
            requeuedBatches.add(new ArrayList<>(batch));
        }

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
        public boolean isPanelVisible() {
            return true;
        }

        @Override
        public void updateProfileHeader() {
            profileHeaderUpdateCalls++;
        }

        @Override
        public void setUploadBlocked(String reason) {
            failureMessage = reason != null ? reason : "";
        }

        @Override
        public void recordUploadAttempt() {
        }

        @Override
        public void recordUploadSuccess(int uploadedCount, int statusCode) {
            successUploaded += uploadedCount;
            lastSuccessStatus = statusCode;
        }

        @Override
        public void recordUploadFailure(Integer statusCode, String errorMessage, boolean dropped, int droppedCount) {
            lastFailureStatus = statusCode;
            failureMessage = errorMessage != null ? errorMessage : "";
            lastFailureDropped = dropped;
            lastFailureDroppedCount = droppedCount;
        }

        @Override
        public void updateUploadDiagnosticsUi() {
        }
    }
}
