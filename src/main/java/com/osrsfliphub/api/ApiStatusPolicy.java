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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ApiStatusPolicy {
    private static final Pattern AUTH_STATUS_PATTERN = Pattern.compile("(^|\\D)(401|403)(\\D|$)");

    private ApiStatusPolicy() {
    }

    static boolean isAuthStatus(int statusCode) {
        return statusCode == 401 || statusCode == 403;
    }

    static boolean isRetryableUploadStatus(int statusCode) {
        return statusCode == 429 || statusCode >= 500;
    }

    static boolean hasCredentials(String sessionToken, String signingSecret) {
        return hasText(sessionToken) && hasText(signingSecret);
    }

    static boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }

    static boolean isAuthorizationFailure(Throwable error) {
        Integer statusCode = extractAuthStatusCode(error);
        return statusCode != null && isAuthStatus(statusCode);
    }

    static Integer extractAuthStatusCode(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof ApiClient.ApiException) {
                return ((ApiClient.ApiException) current).statusCode;
            }
            Integer parsed = parseAuthStatusCode(current.getMessage());
            if (parsed != null) {
                return parsed;
            }
            current = current.getCause();
        }
        return null;
    }

    private static Integer parseAuthStatusCode(String message) {
        if (!hasText(message)) {
            return null;
        }
        Matcher matcher = AUTH_STATUS_PATTERN.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
