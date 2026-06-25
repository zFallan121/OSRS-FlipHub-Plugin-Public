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
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import okhttp3.OkHttpClient;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AccountwideProfileBackfillFactoryServiceTest {
    @Test
    public void createBuildsServiceThatDelegatesToRuntimeHooks() {
        AccountwideProfileBackfillFactoryService factory = new AccountwideProfileBackfillFactoryService(
            2,
            100,
            1_000L,
            5_000L
        );
        TestRuntimeHooks hooks = new TestRuntimeHooks();
        hooks.world = 535;
        hooks.snapshot = Arrays.asList(delta(1_000L, 0, 526, true, 3, 90L, 30));

        AccountwideProfileBackfillService service = factory.create(hooks);
        ScriptedApiClient api = new ScriptedApiClient(response(200, 1, 0, 0));
        MutableConfig config = new MutableConfig("token", "secret");
        BackfillUploader uploader = new BackfillUploader(new NoopUploaderHooks());

        boolean ok = service.backfillProfileTrades(123L, api, config, uploader);

        assertTrue(ok);
        assertEquals(1, hooks.snapshotCalls);
        assertEquals(123L, hooks.lastSnapshotProfileKey);
        assertEquals(1, hooks.resolveWorldCalls);
        assertEquals(Arrays.asList(1), api.batchSizes);
    }

    @Test
    public void createWithNullRuntimeHooksReturnsNoopService() {
        AccountwideProfileBackfillFactoryService factory = new AccountwideProfileBackfillFactoryService(
            2,
            100,
            1_000L,
            5_000L
        );
        AccountwideProfileBackfillService service = factory.create(null);
        ScriptedApiClient api = new ScriptedApiClient(response(200, 1, 0, 0));
        MutableConfig config = new MutableConfig("token", "secret");
        BackfillUploader uploader = new BackfillUploader(new NoopUploaderHooks());

        boolean ok = service.backfillProfileTrades(123L, api, config, uploader);

        assertFalse(ok);
        assertTrue(api.batchSizes.isEmpty());
    }

    private static LocalTradeDelta delta(long ts,
                                         int slot,
                                         int itemId,
                                         boolean isBuy,
                                         int deltaQty,
                                         long deltaGp,
                                         int price) {
        return new LocalTradeDelta(ts, slot, itemId, isBuy, deltaQty, deltaGp, "OFFER_UPDATED", price, false);
    }

    private static ApiClient.EventUploadResponse response(int status, Integer accepted, Integer duplicates, Integer rejected) {
        ApiClient.EventUploadResponse response = new ApiClient.EventUploadResponse();
        response.status_code = status;
        response.accepted = accepted;
        response.duplicates = duplicates;
        response.rejected = rejected;
        return response;
    }

    private static final class TestRuntimeHooks implements AccountwideProfileBackfillFactoryService.RuntimeHooks {
        private List<LocalTradeDelta> snapshot = new ArrayList<>();
        private Integer world = 301;
        private int snapshotCalls;
        private long lastSnapshotProfileKey;
        private int resolveWorldCalls;

        @Override
        public List<LocalTradeDelta> snapshotLocalTrades(long profileKey) {
            snapshotCalls++;
            lastSnapshotProfileKey = profileKey;
            return snapshot;
        }

        @Override
        public Integer resolveWorld() {
            resolveWorldCalls++;
            return world;
        }
    }

    private static final class ScriptedApiClient extends ApiClient {
        private final Deque<ApiClient.EventUploadResponse> responses = new ArrayDeque<>();
        private final List<Integer> batchSizes = new ArrayList<>();

        private ScriptedApiClient(ApiClient.EventUploadResponse... scriptedResponses) {
            super(new OkHttpClient(), new Gson());
            if (scriptedResponses != null) {
                responses.addAll(Arrays.asList(scriptedResponses));
            }
        }

        @Override
        public ApiClient.EventUploadResponse sendEventsDetailed(String sessionToken, String signingSecret, List<GeEvent> events) {
            batchSizes.add(events != null ? events.size() : 0);
            ApiClient.EventUploadResponse next = responses.pollFirst();
            if (next != null) {
                return next;
            }
            return response(200, events != null ? events.size() : 0, 0, 0);
        }
    }

    private static final class MutableConfig implements PluginConfig {
        private final String sessionToken;
        private final String signingSecret;

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

    private static final class NoopUploaderHooks implements BackfillUploader.Hooks {
        @Override
        public boolean attemptRefresh(String currentToken) {
            return false;
        }

        @Override
        public void clearSession() {
        }

        @Override
        public void setUploadBlocked(String reason) {
        }

        @Override
        public void recordUploadAttempt() {
        }

        @Override
        public void recordUploadSuccess(int uploadedCount, int statusCode) {
        }

        @Override
        public void recordUploadFailure(Integer statusCode, String errorMessage, boolean dropped, int droppedCount) {
        }
    }
}
