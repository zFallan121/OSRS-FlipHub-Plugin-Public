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

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Whether a failed batch is worth trying again.
 *
 * <p>This used to be a boolean, so "the server is having a moment" and "the server will never
 * take this" were the same answer. A batch the server had permanently refused was retried every
 * ninety seconds for as long as the client stayed open, and every cycle re-sent all the batches
 * before it as well.
 */
public class BackfillUploaderOutcomeTest {
    @Test
    public void anAcceptedBatchIsSent() {
        assertEquals(BackfillUploader.Outcome.SENT, send(response(200, 1, 0, 0), null));
    }

    @Test
    public void aServerErrorIsWorthRetrying() {
        assertEquals(BackfillUploader.Outcome.RETRY, send(response(500, null, null, null), null));
        assertEquals(BackfillUploader.Outcome.RETRY, send(response(503, null, null, null), null));
    }

    /** A rate limit is the server asking for patience, not refusing the content. */
    @Test
    public void aRateLimitIsWorthRetrying() {
        assertEquals(BackfillUploader.Outcome.RETRY, send(response(429, null, null, null), null));
    }

    @Test
    public void aTimeoutIsWorthRetrying() {
        assertEquals(BackfillUploader.Outcome.RETRY, send(null, new SocketTimeoutException("read timed out")));
    }

    /**
     * Anything else in the four hundreds is a statement about the request itself. Too large,
     * malformed, unprocessable: none of those improve by sending the same thing again.
     */
    @Test
    public void aRefusalOfTheContentIsNotWorthRetrying() {
        assertEquals(BackfillUploader.Outcome.TERMINAL, send(response(400, null, null, null), null));
        assertEquals(BackfillUploader.Outcome.TERMINAL, send(response(413, null, null, null), null));
        assertEquals(BackfillUploader.Outcome.TERMINAL, send(response(422, null, null, null), null));
    }

    /** The request was fine and the contents were thrown away. Repeating it changes nothing. */
    @Test
    public void abatchTheServerAcceptedButRejectedEveryEventOfIsNotWorthRetrying() {
        assertEquals(BackfillUploader.Outcome.TERMINAL, send(response(200, 0, 0, 2), null));
    }

    private static ApiClient.EventUploadResponse response(int status, Integer accepted,
                                                          Integer duplicates, Integer rejected) {
        ApiClient.EventUploadResponse response = new ApiClient.EventUploadResponse();
        response.status_code = status;
        response.accepted = accepted;
        response.duplicates = duplicates;
        response.rejected = rejected;
        return response;
    }

    private static BackfillUploader.Outcome send(ApiClient.EventUploadResponse response, IOException failure) {
        ApiClient apiClient = new ApiClient(null, null, null) {
            @Override
            public EventUploadResponse sendEventsDetailed(String sessionToken, String signingSecret,
                                                          List<GeEvent> events) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                return response;
            }
        };
        return new BackfillUploader().sendBatch(apiClient, linkedConfig(), batchOfTwo());
    }

    private static List<GeEvent> batchOfTwo() {
        GeEvent first = new GeEvent();
        first.item_id = 4151;
        GeEvent second = new GeEvent();
        second.item_id = 561;
        return new ArrayList<>(Arrays.asList(first, second));
    }

    private static PluginConfig linkedConfig() {
        return new PluginConfig() {
            @Override
            public boolean enableFlipHubSync() {
                return true;
            }

            @Override
            public String sessionToken() {
                return "token";
            }

            @Override
            public String signingSecret() {
                return "secret";
            }
        };
    }
}
