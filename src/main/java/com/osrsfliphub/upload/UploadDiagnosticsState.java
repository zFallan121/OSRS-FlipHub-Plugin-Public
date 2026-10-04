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

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
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
    /** The ids of the stored trades the website has said, this session, that it holds ({@link RecordSync}). */
    final Set<String> confirmed = ConcurrentHashMap.newKeySet();
    /**
     * Whether an answer has judged records since {@link RecordSync} last looked, whatever it
     * confirmed: refused records must not hold back the ones behind them.
     */
    volatile boolean progress;
    /** Flushes that have taken a batch off the queue and not yet seen it answered, dropped or put back. */
    final AtomicInteger flushing = new AtomicInteger();

    /**
     * Whether nothing at all is still to be uploaded: nothing queued, no batch on its way, and
     * none held back for a retry. The queue alone does not say: a batch leaves it before it is
     * sent, so it reads empty while that batch's trades are still in the air.
     */
    boolean idle(long nowMs) {
        return pendingUploadEvents.get() <= 0 && flushing.get() <= 0 && !isBackingOff(nowMs);
    }

    /**
     * The website's answer to a batch. Only the ids it names as confirmed are taken: an answer
     * without the list (an older website, which refuses the records by their type) confirms
     * nothing, and neither does any answer but a success, nor one to a batch sent before a new
     * link: that may be to another website account, which holds none of it.
     *
     * @param current whether the batch was sent under the link still in use
     */
    void answered(ApiClient.EventUploadResponse answer, boolean current) {
        List<String> ids = current && answer != null && answer.status_code < 300 && answer.records != null
            ? answer.records.confirmed : null;
        if (ids != null) {
            progress = true;
            for (String id : ids) {
                // A set that refuses a null: one in the list must not fail the upload it came with.
                if (id != null) {
                    confirmed.add(id);
                }
            }
        }
    }

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
                dropped);
        }
        // Counted before it can be taken. The other way round, a flush on its own thread could take
        // the trade first and lower a count still at nothing, which never goes below it: the count
        // then stood at one for good with nothing queued, nothing was ever idle again, and stored
        // trades stopped being sent to the website (RecordSync) until a restart.
        pendingUploadEvents.incrementAndGet();
        eventQueue.offer(event);
    }

    GeEvent dequeueEvent() {
        GeEvent event = eventQueue.poll();
        if (event != null) {
            pendingUploadEvents.updateAndGet(value -> value > 0 ? value - 1 : 0);
        }
        return event;
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
     * the events away. The trades among them are sent again from the file ({@link RecordSync}),
     * but recorded recipes wait for the next session. They are bounded already, and this object
     * outlives a disable, so holding them costs nothing and a re-enable can still deliver them.</p>
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

    void markFailure(Integer statusCode, String errorMessage, int droppedCount) {
        if (statusCode != null) {
            lastUploadStatusCode = statusCode;
        }
        if (Str.hasText(errorMessage)) {
            lastUploadError = errorMessage.trim();
        }
        if (droppedCount > 0) {
            droppedEventCount.addAndGet(droppedCount);
        }
    }

    String buildTooltip(boolean linked) {
        int pending = pendingUploadEvents.get();
        long uploaded = uploadedEventCount.get();

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

        return "<html><div style='font-size:10px;'>Pending uploads: " + pending
            + "<br>Uploaded events: " + uploaded
            + "<br>Dropped events: " + droppedEventCount.get()
            + "<br>Last attempt: " + formatUploadTime(lastUploadAttemptMs)
            + "<br>Last success: " + formatUploadTime(lastUploadSuccessMs)
            + "<br>Last status: " + statusLabel
            + "<br>Last error: " + errorLabel + "</div></html>";
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
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}
