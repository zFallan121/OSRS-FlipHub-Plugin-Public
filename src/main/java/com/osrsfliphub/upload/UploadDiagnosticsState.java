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
import lombok.Getter;

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
    /** Earliest the next attempt may run, while a failure is being backed off. */
    private volatile long nextAttemptAllowedMs = 0L;
    @Getter
    private volatile long currentBackoffMs = 0L;

    /**
     * Holds off the next attempt, doubling the wait each consecutive failure up to the cap. The
     * flush runs every two seconds, so without this a rate limit is answered with another
     * request two seconds later, and an unreachable server is retried forever at that rate.
     */
    void backOff(long nowMs, long initialMs, long maxMs) {
        long next = currentBackoffMs <= 0L ? Math.max(1L, initialMs) : currentBackoffMs * 2L;
        currentBackoffMs = Math.min(next, Math.max(1L, maxMs));
        nextAttemptAllowedMs = nowMs + currentBackoffMs;
    }

    /** Clears any backoff, because something got through. */
    void clearBackOff() {
        currentBackoffMs = 0L;
        nextAttemptAllowedMs = 0L;
    }

    boolean isBackingOff(long nowMs) {
        return nowMs < nextAttemptAllowedMs;
    }

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

    /**
     * Forget the failure state when the plugin stops, but keep the queued events.
     *
     * <p>These are completed trades that have not reached the server yet. The final flush is
     * handed to the IO pool and finishes after this runs, and it can also decline outright
     * while a backoff is standing or while logged out. Emptying the queue here therefore threw
     * the events away, and nothing resends them: once a profile is marked backfilled they are
     * gone for good. They are bounded already, they carry deterministic ids so a resend cannot
     * double-count, and this object outlives a disable, so holding them costs nothing and a
     * re-enable can still deliver them.</p>
     */
    void resetForPluginStop() {
        clearBackOff();
    }

    /**
     * Forget the last failure. The wait it left behind goes with it: a run of failures can
     * leave five minutes standing, and a player who has just relinked or changed a setting
     * should not sit through the rest of it for a problem they have addressed.
     */
    void resetStatus() {
        lastUploadStatusCode = null;
        lastUploadError = null;
        clearBackOff();
    }

    void markBlocked(String reason) {
        if (Str.hasText(reason)) {
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
        if (Str.hasText(errorMessage)) {
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
        if (Str.hasText(error)) {
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
