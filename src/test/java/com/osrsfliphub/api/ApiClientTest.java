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
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ApiClientTest
{
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    @Test
    public void sendEventsDetailedSkipsHttpWhenEventsEmpty() throws Exception
    {
        AtomicInteger callCount = new AtomicInteger(0);
        ApiClient client = newApiClient(200, "{\"status\":\"ok\"}", callCount, null, null);

        ApiClient.EventUploadResponse response = client.sendEventsDetailed("token", "secret", Collections.emptyList());

        assertEquals(0, callCount.get());
        assertEquals(0, response.status_code);
        assertEquals("ok", response.status);
        assertEquals(Integer.valueOf(0), response.accepted);
        assertEquals(Integer.valueOf(0), response.duplicates);
        assertEquals(Integer.valueOf(0), response.rejected);
    }

    @Test
    public void sendEventsDetailedParsesStructuredUploadResponse() throws Exception
    {
        AtomicInteger callCount = new AtomicInteger(0);
        String body = "{\"status\":\"ok\",\"accepted\":3,\"duplicates\":1,\"rejected\":2}";
        ApiClient client = newApiClient(200, body, callCount, null, null);

        ApiClient.EventUploadResponse response = client.sendEventsDetailed(
            "token",
            "secret",
            Arrays.asList(sampleEvent())
        );

        assertEquals(1, callCount.get());
        assertEquals(200, response.status_code);
        assertEquals("ok", response.status);
        assertEquals(Integer.valueOf(3), response.accepted);
        assertEquals(Integer.valueOf(1), response.duplicates);
        assertEquals(Integer.valueOf(2), response.rejected);
        assertEquals(200, client.sendEvents("token", "secret", Arrays.asList(sampleEvent())));
    }

    @Test
    public void sendEventsDetailedHandlesMalformedJsonBody() throws Exception
    {
        AtomicInteger callCount = new AtomicInteger(0);
        ApiClient client = newApiClient(500, "not-json", callCount, null, null);

        ApiClient.EventUploadResponse response = client.sendEventsDetailed(
            "token",
            "secret",
            Arrays.asList(sampleEvent())
        );

        assertEquals(1, callCount.get());
        assertEquals(500, response.status_code);
        assertNull(response.status);
        assertNull(response.accepted);
        assertNull(response.duplicates);
        assertNull(response.rejected);
    }

    @Test
    public void sendAccountwideSummaryTargetsExpectedEndpoint() throws Exception
    {
        AtomicInteger callCount = new AtomicInteger(0);
        AtomicReference<String> pathRef = new AtomicReference<>();
        ApiClient client = newApiClient(200, "{}", callCount, pathRef, null);

        int status = client.sendAccountwideSummary("token", "secret", new StatsSummary());

        assertEquals(1, callCount.get());
        assertEquals(200, status);
        assertEquals("/api/plugin/stats/accountwide", pathRef.get());
    }

    @Test
    public void sendAccountwideSummaryIncludesItemsPayload() throws Exception
    {
        AtomicInteger callCount = new AtomicInteger(0);
        AtomicReference<String> bodyRef = new AtomicReference<>();
        ApiClient client = newApiClient(200, "{}", callCount, null, bodyRef);

        StatsItem item = new StatsItem();
        item.item_id = 4151;
        item.total_profit_gp = 650L;
        int status = client.sendAccountwideSummary("token", "secret", new StatsSummary(), Arrays.asList(item));

        assertEquals(1, callCount.get());
        assertEquals(200, status);
        String body = bodyRef.get();
        assertTrue(body.contains("\"items\""));
        assertTrue(body.contains("\"item_id\":4151"));
    }

    @Test
    public void networkCallsAreBlockedWhenSyncDisabled()
    {
        AtomicInteger callCount = new AtomicInteger(0);
        ApiClient client = newApiClient(200, "{}", callCount, null, null, syncDisabledConfig());

        assertThrows(IllegalStateException.class,
            () -> client.linkDevice("key", "device", "name", "1.0.0"));
        assertThrows(IllegalStateException.class,
            () -> client.refreshSession("token"));
        assertThrows(IllegalStateException.class,
            () -> client.refreshSession("token", "secret", "device"));
        assertThrows(IllegalStateException.class,
            () -> client.sendEvents("token", "secret", Arrays.asList(sampleEvent())));
        assertThrows(IllegalStateException.class,
            () -> client.sendEventsDetailed("token", "secret", Arrays.asList(sampleEvent())));
        assertThrows(IllegalStateException.class,
            () -> client.sendAccountwideSummary("token", "secret", new StatsSummary()));
        assertThrows(IllegalStateException.class,
            () -> client.wipeWebsiteStats("token", "secret"));
        assertThrows(IllegalStateException.class,
            () -> client.fetchItems("token", null, 1, 10));
        assertThrows(IllegalStateException.class,
            () -> client.fetchItem("token", 4151));
        assertThrows(IllegalStateException.class,
            () -> client.fetchStatsSummary("token", null, null));
        assertThrows(IllegalStateException.class,
            () -> client.fetchStatsItems("token", null, null, null, null));

        assertEquals(0, callCount.get());
    }

    private PluginConfig syncEnabledConfig()
    {
        return new PluginConfig()
        {
            @Override
            public boolean enableFlipHubSync()
            {
                return true;
            }
        };
    }

    private PluginConfig syncDisabledConfig()
    {
        return new PluginConfig()
        {
        };
    }

    private ApiClient newApiClient(int statusCode, String responseBody, AtomicInteger callCount,
                                   AtomicReference<String> pathRef,
                                   AtomicReference<String> bodyRef)
    {
        return newApiClient(statusCode, responseBody, callCount, pathRef, bodyRef, syncEnabledConfig());
    }

    private ApiClient newApiClient(int statusCode, String responseBody, AtomicInteger callCount,
                                   AtomicReference<String> pathRef,
                                   AtomicReference<String> bodyRef,
                                   PluginConfig config)
    {
        OkHttpClient httpClient = new OkHttpClient.Builder()
            .addInterceptor(chain -> {
                callCount.incrementAndGet();
                Request request = chain.request();
                if (pathRef != null) {
                    pathRef.set(request.url().encodedPath());
                }
                if (bodyRef != null && request.body() != null) {
                    Buffer buffer = new Buffer();
                    request.body().writeTo(buffer);
                    bodyRef.set(buffer.readUtf8());
                }
                ResponseBody body = ResponseBody.create(JSON, responseBody != null ? responseBody : "");
                return new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(statusCode)
                    .message("OK")
                    .body(body)
                    .build();
            })
            .build();
        return new ApiClient(httpClient, new Gson(), config);
    }

    private GeEvent sampleEvent()
    {
        GeEvent event = new GeEvent();
        event.event_id = "evt-1";
        event.event_type = "OFFER_UPDATED";
        event.ts_client_ms = 1700000000000L;
        event.slot = 1;
        event.item_id = 2;
        event.is_buy = true;
        event.price = 100;
        event.total_qty = 10;
        event.filled_qty = 10;
        event.spent_gp = 1000L;
        event.state = "BUYING";
        event.prev_state = "EMPTY";
        event.delta_qty = 10;
        event.delta_gp = 1000L;
        return event;
    }
}
