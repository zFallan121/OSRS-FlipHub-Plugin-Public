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

import java.util.List;

/**
 * Whether a sync is due, and whether the history list has been read completely.
 */
@javax.inject.Singleton
final class GeHistoryAutoSyncStateService {
    enum ReadVerdict {
        /** Not complete yet, or complete but not yet still; read again next tick. */
        WAIT,
        /** Complete and unchanged for the settle window: safe to act on. */
        SETTLED,
        /** Never settled within the give-up window: stop trying this login. */
        GIVE_UP
    }

    private final long widgetSettleMs;
    private final long readGiveUpMs;
    private volatile boolean pending;
    private volatile long historyVisibleSinceMs;
    private volatile String lastReadFingerprint;
    private volatile long readStableSinceMs;

    @javax.inject.Inject
    GeHistoryAutoSyncStateService() {
        this(GeLifecyclePluginConstants.GE_HISTORY_SYNC_WIDGET_SETTLE_MS,
            GeLifecyclePluginConstants.GE_HISTORY_SYNC_READ_GIVE_UP_MS);
    }

    GeHistoryAutoSyncStateService(long widgetSettleMs) {
        this(widgetSettleMs, GeLifecyclePluginConstants.GE_HISTORY_SYNC_READ_GIVE_UP_MS);
    }

    GeHistoryAutoSyncStateService(long widgetSettleMs, long readGiveUpMs) {
        this.widgetSettleMs = Math.max(0L, widgetSettleMs);
        this.readGiveUpMs = Math.max(0L, readGiveUpMs);
    }

    void arm() {
        pending = true;
        forgetRead();
    }

    void disarm() {
        pending = false;
        forgetRead();
    }

    boolean isPending() {
        return pending;
    }

    void markHistoryHidden() {
        forgetRead();
    }

    void noteHistoryVisible(long nowMs) {
        if (historyVisibleSinceMs <= 0L) {
            historyVisibleSinceMs = Math.max(0L, nowMs);
        }
    }

    /**
     * One tick's read of the list. Whole rows are the first requirement; after that the
     * read - its widget count and the signatures it parsed to - has to be the same as
     * the previous tick's for the settle window before it is trusted.
     */
    ReadVerdict observeRead(boolean widgetsComplete, int widgetCount, List<String> signatures, long nowMs) {
        long now = Math.max(0L, nowMs);
        if (!widgetsComplete) {
            lastReadFingerprint = null;
            readStableSinceMs = 0L;
            return givenUp(now) ? ReadVerdict.GIVE_UP : ReadVerdict.WAIT;
        }
        String fingerprint = widgetCount + "|" + (signatures != null ? String.join(",", signatures) : "");
        if (!fingerprint.equals(lastReadFingerprint)) {
            lastReadFingerprint = fingerprint;
            readStableSinceMs = now;
            if (widgetSettleMs <= 0L) {
                return ReadVerdict.SETTLED;
            }
            return givenUp(now) ? ReadVerdict.GIVE_UP : ReadVerdict.WAIT;
        }
        if (now - readStableSinceMs >= widgetSettleMs) {
            return ReadVerdict.SETTLED;
        }
        return givenUp(now) ? ReadVerdict.GIVE_UP : ReadVerdict.WAIT;
    }

    /**
     * Whether the list has stopped making progress, as opposed to simply taking a while.
     */
    private boolean givenUp(long nowMs) {
        if (readGiveUpMs <= 0L) {
            return false;
        }
        long since = readStableSinceMs > 0L ? readStableSinceMs : historyVisibleSinceMs;
        return since > 0L && nowMs - since >= readGiveUpMs;
    }

    private void forgetRead() {
        historyVisibleSinceMs = 0L;
        lastReadFingerprint = null;
        readStableSinceMs = 0L;
    }
}
