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

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

final class ApiClientRequestFactory {
    private final String apiBaseUrl;
    private final MediaType jsonMediaType;

    ApiClientRequestFactory(String apiBaseUrl, MediaType jsonMediaType) {
        this.apiBaseUrl = apiBaseUrl;
        this.jsonMediaType = jsonMediaType;
    }

    Request newPostRequest(String path, String jsonBody) {
        return newPostBuilder(path, jsonBody).build();
    }

    Request.Builder newPostBuilder(String path, String jsonBody) {
        return new Request.Builder()
            .url(apiUrl(path))
            .post(RequestBody.create(jsonMediaType, jsonBody));
    }

    Request newSignedPostRequest(String path, String sessionToken, String signingSecret, String jsonBody) {
        Request.Builder requestBuilder = newPostBuilder(path, jsonBody);
        requestBuilder.addHeader("X-Plugin-Token", sessionToken);
        addSignedHeaders(requestBuilder, "POST", path, signingSecret, jsonBody);
        return requestBuilder.build();
    }

    Request newGetRequest(String url, String sessionToken) {
        return new Request.Builder()
            .url(url)
            .get()
            .addHeader("X-Plugin-Token", sessionToken)
            .build();
    }

    void addSignedHeaders(Request.Builder requestBuilder, String method, String path, String signingSecret, String jsonBody) {
        byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String timestamp = String.valueOf(System.currentTimeMillis());
        String bodyHash = Signer.sha256Hex(bodyBytes);
        String canonical = method + "\n" +
            path + "\n" +
            timestamp + "\n" +
            nonce + "\n" +
            bodyHash;
        String signature = Signer.hmacBase64(signingSecret, canonical);

        requestBuilder.addHeader("X-Nonce", nonce);
        requestBuilder.addHeader("X-Timestamp", timestamp);
        requestBuilder.addHeader("X-Signature", signature);
    }

    String apiUrl(String path) {
        return apiBaseUrl + path;
    }

    boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    boolean appendStatsQuery(StringBuilder builder, Long sinceMs, Long untilMs) {
        boolean hasQuery = false;
        if (sinceMs != null && sinceMs > 0) {
            builder.append("?since_ms=").append(sinceMs);
            hasQuery = true;
        }
        if (untilMs != null && untilMs > 0) {
            builder.append(hasQuery ? "&" : "?").append("until_ms=").append(untilMs);
            hasQuery = true;
        }
        return hasQuery;
    }
}
