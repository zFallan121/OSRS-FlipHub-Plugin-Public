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

/**
 * FlipHub answered and refused the request.
 *
 * <p>It carries the status code because the caller's next move depends on it: a key the
 * server rejects is worth forgetting, while a server having a bad minute is not. Without
 * the code the two were the same failure, and the plugin erased the licence key the player
 * had just pasted whenever the network was down.
 *
 * <p>Extends {@link IllegalStateException} so existing handlers keep catching it.
 */
final class ApiRefusedException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    private final int statusCode;

    ApiRefusedException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    int getStatusCode() {
        return statusCode;
    }

    /** Whether the server is saying the request itself is wrong, rather than asking for patience. */
    boolean isRefusalOfTheRequest() {
        return statusCode >= 400 && statusCode < 500 && statusCode != 408 && statusCode != 429;
    }

    /**
     * Whether this failure, or anything it wraps, is FlipHub refusing the request outright.
     *
     * <p>False for everything a failing network throws, and for a request the plugin itself
     * declined to send, because none of those say anything about what was being sent.
     */
    static boolean refusedTheRequest(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof ApiRefusedException) {
                return ((ApiRefusedException) current).isRefusalOfTheRequest();
            }
            current = current.getCause();
        }
        return false;
    }
}
