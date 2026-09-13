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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;

@Singleton
final class ProfileBackfill {
    private final int maxBatchSize = Const.MAX_BATCH_SIZE;
    private final long localEventBucketMs = Const.LOCAL_EVENT_BUCKET_MS;
    private final long duplicateTradeWindowMs = Const.DUPLICATE_TRADE_WINDOW_MS;
    private final Client client;

    /** How many of a profile's events the server has already taken, so a retry can resume. */
    /**
     * The last event of this profile that the server is known to have taken, by its id.
     *
     * <p>It used to be a count of events sent, which only holds while the stored list keeps
     * the same shape. The list is rebuilt from the profile on every attempt, sorted by time
     * and collapsed into offers, and the in-game history sync inserts trades with older
     * timestamps: anything inserted before the mark shifted everything after it, and the next
     * attempt skipped exactly that many events and never sent them. The id survives all of
     * that, because it is derived from the trade itself.
     */
    private final Map<Long, String> sentEventsByProfile = new ConcurrentHashMap<>();

    @Inject
    ProfileBackfill(Client client) {
        this.client = client;
    }

    private List<Delta> snapshotLocalTrades(long profileKey) {
        TradeSession service = Bridge.get(TradeSession.class);
        return service != null ? service.snapshotLocalTradeDeltas(profileKey) : null;
    }

    /**
     * Sends one profile's stored trades, picking up where the last attempt stopped.
     *
     * <p>Every batch used to be rebuilt and re-sent from the first one on each retry, so a
     * batch the server would not take meant everything before it was uploaded again on every
     * cycle, forever. The server discards the repeats by event id, but the requests were real.
     */
    BackfillUploader.Outcome backfillProfileTrades(long profileKey,
                                                   ApiClient apiClient,
                                                   PluginConfig config,
                                                   BackfillUploader uploader) {
        if (profileKey <= 0 || apiClient == null || config == null || uploader == null) {
            return BackfillUploader.Outcome.RETRY;
        }
        List<Delta> source = snapshotLocalTrades(profileKey);
        List<Delta> snapshot = source != null ? new ArrayList<>(source) : new ArrayList<>();
        snapshot = TradeDeltaUtils.dedupeLocalTrades(
            snapshot,
            localEventBucketMs,
            duplicateTradeWindowMs
        );
        if (snapshot == null || snapshot.isEmpty()) {
            sentEventsByProfile.remove(profileKey);
            return BackfillUploader.Outcome.SENT;
        }

        Integer world = client != null ? (Integer) client.getWorld() : null;
        List<GeEvent> events = new ArrayList<>(snapshot.size());
        for (Delta delta : snapshot) {
            GeEvent event = uploader.buildBackfillEvent(profileKey, delta, world);
            if (event != null) {
                events.add(event);
            }
        }
        if (events.isEmpty()) {
            sentEventsByProfile.remove(profileKey);
            return BackfillUploader.Outcome.SENT;
        }

        int start = resumePoint(sentEventsByProfile.get(profileKey), events);
        for (; start < events.size(); start += maxBatchSize) {
            List<GeEvent> batch = events.subList(start, Math.min(events.size(), start + maxBatchSize));
            BackfillUploader.Outcome outcome = uploader.sendBatch(apiClient, config, new ArrayList<>(batch));
            if (outcome != BackfillUploader.Outcome.SENT) {
                if (start > 0) {
                    sentEventsByProfile.put(profileKey, events.get(start - 1).event_id);
                } else {
                    sentEventsByProfile.remove(profileKey);
                }
                return outcome;
            }
        }
        sentEventsByProfile.remove(profileKey);
        return BackfillUploader.Outcome.SENT;
    }

    /**
     * Where to pick up. If the event the last attempt got through is no longer in the list,
     * start from the beginning: the server throws away repeats by id, so sending too much is
     * the safe direction and sending too little is not.
     */
    static int resumePoint(String lastAccepted, List<GeEvent> events) {
        if (lastAccepted == null || events == null) {
            return 0;
        }
        for (int index = 0; index < events.size(); index++) {
            GeEvent event = events.get(index);
            if (event != null && lastAccepted.equals(event.event_id)) {
                return index + 1;
            }
        }
        return 0;
    }

    /** Forget every resume point, for a relink that may be to a different website account. */
    void clearResumePoints() {
        sentEventsByProfile.clear();
    }
}
