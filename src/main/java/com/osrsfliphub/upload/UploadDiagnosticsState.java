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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

final class UploadDiagnosticsState {
    private static final DateTimeFormatter UPLOAD_STATUS_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Queue<GeEvent> eventQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pendingUploadEvents = new AtomicInteger(0);
    private final AtomicLong uploadedEventCount = new AtomicLong(0L);
    private final AtomicLong droppedEventCount = new AtomicLong(0L);
    private volatile long lastUploadAttemptMs = 0L;
    private volatile long lastUploadSuccessMs = 0L;
    private volatile Integer lastUploadStatusCode;
    private volatile String lastUploadError;

    void enqueueEvent(GeEvent event, int maxPendingUploadEvents) {
        if (event == null) {
            return;
        }
        int dropped = 0;
        while (pendingUploadEvents.get() >= maxPendingUploadEvents) {
            GeEvent discarded = dequeueEvent();
            if (discarded == null) {
                break;
            }
            dropped++;
        }
        if (dropped > 0) {
            markFailure(
                null,
                "Upload queue exceeded " + maxPendingUploadEvents + " events. Oldest events were dropped.",
                true,
                dropped
            );
        }
        eventQueue.offer(event);
        pendingUploadEvents.incrementAndGet();
    }

    GeEvent dequeueEvent() {
        GeEvent event = eventQueue.poll();
        if (event != null) {
            pendingUploadEvents.updateAndGet(value -> value > 0 ? value - 1 : 0);
        }
        return event;
    }

    void requeue(List<GeEvent> batch, int maxPendingUploadEvents) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        for (GeEvent event : batch) {
            enqueueEvent(event, maxPendingUploadEvents);
        }
    }

    int getPendingUploadEvents() {
        return Math.max(0, pendingUploadEvents.get());
    }

    void reset() {
        eventQueue.clear();
        pendingUploadEvents.set(0);
    }

    void resetStatus() {
        lastUploadStatusCode = null;
        lastUploadError = null;
    }

    void markBlocked(String reason) {
        if (reason != null && !reason.trim().isEmpty()) {
            lastUploadError = reason.trim();
        }
        lastUploadStatusCode = null;
    }

    void markAttempt() {
        lastUploadAttemptMs = System.currentTimeMillis();
    }

    void markSuccess(int uploadedCount, int statusCode) {
        lastUploadSuccessMs = System.currentTimeMillis();
        lastUploadStatusCode = statusCode;
        lastUploadError = null;
        if (uploadedCount > 0) {
            uploadedEventCount.addAndGet(uploadedCount);
        }
    }

    void markFailure(Integer statusCode, String errorMessage, boolean dropped, int droppedCount) {
        if (statusCode != null) {
            lastUploadStatusCode = statusCode;
        }
        if (errorMessage != null && !errorMessage.trim().isEmpty()) {
            lastUploadError = errorMessage.trim();
        }
        if (dropped && droppedCount > 0) {
            droppedEventCount.addAndGet(droppedCount);
        }
    }

    String buildTooltip(boolean linked) {
        int pending = Math.max(0, pendingUploadEvents.get());
        long uploaded = Math.max(0L, uploadedEventCount.get());
        long dropped = Math.max(0L, droppedEventCount.get());

        String statusLabel;
        if (lastUploadStatusCode != null) {
            statusLabel = formatUploadStatusCode(lastUploadStatusCode);
        } else if (!linked) {
            statusLabel = "not linked";
        } else if (pending > 0) {
            statusLabel = "queued";
        } else {
            statusLabel = "idle";
        }

        String error = lastUploadError;
        String errorLabel;
        if (error != null && !error.trim().isEmpty()) {
            errorLabel = sanitizeHtml(error.trim());
        } else if (!linked) {
            errorLabel = "not linked";
        } else if (uploaded <= 0 && pending <= 0) {
            errorLabel = "waiting for first trade event";
        } else {
            errorLabel = "none";
        }

        StringBuilder tooltip = new StringBuilder("<html><div style='font-size:10px;'>");
        tooltip.append("Pending uploads: ").append(pending).append("<br>");
        tooltip.append("Uploaded events: ").append(uploaded).append("<br>");
        tooltip.append("Dropped events: ").append(dropped).append("<br>");
        tooltip.append("Last attempt: ").append(formatUploadTime(lastUploadAttemptMs)).append("<br>");
        tooltip.append("Last success: ").append(formatUploadTime(lastUploadSuccessMs)).append("<br>");
        tooltip.append("Last status: ").append(statusLabel).append("<br>");
        tooltip.append("Last error: ").append(errorLabel).append("</div></html>");
        return tooltip.toString();
    }

    private String formatUploadTime(long timestampMs) {
        if (timestampMs <= 0) {
            return "never";
        }
        return UPLOAD_STATUS_TIME_FORMATTER.format(Instant.ofEpochMilli(timestampMs));
    }

    private String formatUploadStatusCode(Integer statusCode) {
        if (statusCode == null) {
            return "none";
        }
        if (statusCode == -1) {
            return "exception";
        }
        return String.valueOf(statusCode);
    }

    private String sanitizeHtml(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}
