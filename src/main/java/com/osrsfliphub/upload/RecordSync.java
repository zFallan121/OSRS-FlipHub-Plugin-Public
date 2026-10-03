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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import javax.inject.*;
import lombok.RequiredArgsConstructor;
import net.runelite.client.config.ConfigManager;

/**
 * Sends every stored trade to the website until the website says it holds it.
 *
 * <p>A trade is uploaded as it happens, fill by fill, from a queue that lives only in memory. A
 * crash, a batch the website refused, a PC clock five minutes off: the trade was in the player's
 * file, the website was short of it, and nothing said so. The catch-up that existed decided a
 * character was "already there" by comparing the website's totals with the plugin's own, and those
 * totals were the plugin's own upload, so it never saw a missing trade.
 *
 * <p>Now a stored trade is sent as the record of a finished offer ({@code OFFER_RECORD}), under an
 * id made from the record so it is the same every time, and the website judges it by what it
 * holds: nothing stored when its fills already cover the record, the record stored whole when it
 * holds none of it, the missing part when it holds some. Its answer names the records it now
 * holds ({@link UploadDiagnosticsState#answered}), and only those are written down as confirmed.
 * One it does not name is sent again.
 *
 * <p><b>Where a confirmation is written down.</b> A window writes only the file of the character
 * it is logged in as: another window may be logged in as any other character and writing that
 * file. So the logged-in character's records are marked in its file, each with the moment
 * ({@link Delta#uploadedMs}). Every other character's records are sent from their files as read
 * and their files are never written: what the website confirmed of them is remembered for the
 * session, and as one mark per character in RuneLite's config, {@link #MARK_KEY}, saying that
 * every record of that character that ended before it is confirmed. The mark only moves up to the
 * oldest record not confirmed, so a refused record holds it there and everything after it is sent
 * again next session, where the website answers that it has it.
 *
 * <p><b>When.</b> A record must never be judged before its own live fills have arrived, or the
 * website would store the record and then the fills beside it: the trade counted twice. So a
 * record is sent only when nothing of the live upload is left anywhere ({@link
 * UploadDiagnosticsState#idle}: nothing queued, no batch on its way, none held back for a retry),
 * asked straight after the live queue was sent and on that thread; only for an offer that has
 * ended, never for the fills of one still open; and only once it ended over a minute ago, or a
 * quarter of an hour for a character this window is not logged in as, whose own window may be
 * holding its fills where this one cannot see. Oldest first, one batch at a time; a batch the
 * website did not judge (a refused request, an older website) ends the pass, one it judged does
 * not, whatever it refused, and what a pass leaves unconfirmed is tried again after a wait that
 * doubles from a minute to a quarter of an hour.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class RecordSync {
    /** How long after its offer ended a record waits, so its live fills reach the website first. */
    static final long SETTLE_MS = 60_000L;
    /**
     * The same wait for a character this window is not logged in as. That character may be logged
     * in on another window, whose queue this one cannot see: its fills may be waiting there, held
     * back after a failure, for as long as its retries take.
     */
    static final long OTHER_WINDOW_SETTLE_MS = 15 * SETTLE_MS;
    private static final long LONGEST_WAIT_MS = 15 * SETTLE_MS;
    private static final String GROUP = FliphubConfigGroups.CONFIG_GROUP;
    /** Before a character's key: every record of that character that ended before this moment is confirmed. */
    static final String MARK_KEY = "recordsConfirmedBeforeV1_";
    /** When this computer was last linked. A confirmation from before it was given by another link. */
    static final String LINKED_KEY = "recordsLinkedMsV1";

    private final PluginState state;
    private final ConfigManager configManager;
    private final AccountSession accountSession;
    private final LocalTradesRuntime tradesRuntime;
    private final ProfileSelectionPresentation profiles;
    private final ProfileStorage storage;
    private final Gson gson;

    /** The ids sent in the pass under way: one the website did not confirm is not sent twice in a pass. */
    private final Set<String> sent = new HashSet<>();
    /** No pass starts before this: the wait after a pass that left something unconfirmed. */
    long nextPassMs;
    private long waitMs;
    private long nextReadMs;

    /**
     * A link was made, perhaps to another website account, which holds none of what the last one
     * confirmed. Everything is unconfirmed again and is sent once more; to the same account, that
     * costs one answer of "already here" a record.
     */
    synchronized void linked() {
        configManager.setConfiguration(GROUP, LINKED_KEY, System.currentTimeMillis());
        for (String key : configManager.getConfigurationKeys(GROUP + "." + MARK_KEY)) {
            configManager.unsetConfiguration(GROUP, key.substring(GROUP.length() + 1));
        }
        state.getUploadState().confirmed.clear();
        sent.clear();
        nextPassMs = waitMs = 0;
    }

    /**
     * For the Profile tab: how many finished trades of the character shown there, or of every
     * character when it shows them all, the website has not confirmed. Counted as they are sent:
     * an offer still open, or one that ended too lately to be sent yet, is not among them. Nothing
     * for a player who is not linked.
     */
    int waiting() {
        return profiles.isLinked() ? unconfirmed(profiles.resolveSelectedProfileKey(), false).size() : 0;
    }

    /** See the class comment, "When". Called by the upload tick after it has sent the live queue. */
    synchronized void sweep() {
        long now = System.currentTimeMillis();
        UploadDiagnosticsState queue = state.getUploadState();
        if (now < nextPassMs || accountSession.localKey <= 0 || !profiles.isLinked() || !queue.idle(now)) {
            return;
        }
        if (now >= nextReadMs) {
            // Every character's file on this computer, the logged-in one's or not.
            nextReadMs = now + SETTLE_MS;
            for (Path dir : new Path[] {storage.getProfilesDir(), storage.getLegacyProfilesDir()}) {
                ProfileHashFileWalker.walk(dir, (key, file) -> tradesRuntime.ensureProfileLoaded(key));
            }
        }
        List<GeEvent> batch = unconfirmed(Const.ACCOUNTWIDE_KEY, true);
        boolean left = batch.removeIf(event -> sent.contains(event.event_id));
        boolean stalled = !sent.isEmpty() && !queue.progress;
        if (queue.progress) {
            // The Profile tab says how many are still to be confirmed.
            Access.plugin().getPanelRefreshCoordinator().triggerStatsRefresh(Access.plugin().scheduler);
        }
        queue.progress = false;
        if (batch.isEmpty() || stalled) {
            waitMs = left || stalled ? Math.min(Math.max(SETTLE_MS, waitMs * 2), LONGEST_WAIT_MS) : 0;
            nextPassMs = now + waitMs;
            sent.clear();
            return;
        }
        batch.sort(Comparator.comparingLong(event -> event.ts_client_ms));
        for (GeEvent event : batch.subList(0, Math.min(batch.size(), Const.MAX_BATCH_SIZE))) {
            sent.add(event.event_id);
            queue.enqueueEvent(event, UploadEventDispatch.MAX_PENDING_UPLOAD_EVENTS);
        }
    }

    /**
     * The trades the website has not confirmed that may be sent now (see the class comment, "When"),
     * as the records they are sent as: one character's, or every character's for the all-characters
     * key.
     *
     * @param write whether to write down what the website has confirmed since the last look: in the
     *              file for the logged-in character, by moving the mark for any other
     */
    private List<GeEvent> unconfirmed(long shown, boolean write) {
        List<GeEvent> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        long own = accountSession.localKey;
        int world = accountSession.world;
        long linkedMs = number(LINKED_KEY);
        Set<String> held = state.getUploadState().confirmed;
        Object lock = state.getLocalStatsLock();
        // Copied, and the lock let go: an id is worked out for every record, which takes tens of
        // milliseconds for tens of thousands, and a trade being stored waits for the lock. What a
        // record's id is made of is never changed once it is stored.
        Map<Long, List<Delta>> all = new HashMap<>();
        synchronized (lock) {
            state.getLocalTradeDeltasByAccount().forEach((key, trades) -> all.put(key, new ArrayList<>(trades)));
        }
        for (Map.Entry<Long, List<Delta>> entry : all.entrySet()) {
            long key = entry.getKey();
            if (key <= 0 || shown != key && shown != Const.ACCOUNTWIDE_KEY) {
                continue;
            }
            long mark = key == own ? 0 : number(MARK_KEY + key);
            // The end of the oldest record not confirmed: every record before it is.
            long oldest = Long.MAX_VALUE;
            // Of the records the mark does not cover yet, when the first and the last ended.
            long first = Long.MAX_VALUE;
            long last = 0;
            boolean marked = false;
            // Each slot's saved position, which says whether an offer is still there: the live
            // ones for the logged-in character, any other character's as this config last saw them.
            Map<Integer, Stamp> slots = key == own ? state.getOfferUpdateStamps()
                : OfferUpdateStampStore.parse(configManager.getConfiguration(GROUP,
                    state.getOfferUpdateStampConfigStore().perAccountKey(key)), gson, 0, 7);
            for (Delta delta : entry.getValue()) {
                // A record of nothing is no trade, and the website refuses one.
                if (delta == null || delta.deltaQty <= 0 || delta.deltaGp <= 0 || delta.closedAtMs() < mark
                    || open(delta, slots.get(delta.slot))) {
                    continue;
                }
                long endMs = delta.closedAtMs();
                first = Math.min(first, endMs);
                last = Math.max(last, endMs);
                if (delta.uploadedMs > linkedMs) {
                    continue;
                }
                GeEvent event = record(key, delta, world);
                if (event == null) {
                    continue;
                }
                if (held.contains(event.event_id)) {
                    if (write && key == own) {
                        synchronized (lock) {
                            delta.uploadedMs = now;
                        }
                        marked = true;
                    }
                    continue;
                }
                oldest = Math.min(oldest, endMs);
                if (endMs + (key == own ? SETTLE_MS : OTHER_WINDOW_SETTLE_MS) <= now) {
                    out.add(event);
                }
            }
            if (marked) {
                tradesRuntime.persistLocalTrades(key);
            } else if (write && key != own && first < Math.min(oldest, last + 1)) {
                // It moves only over records that are confirmed, never up to one that is not
                // from nothing: a record that ends where the mark would stand is not behind it.
                configManager.setConfiguration(GROUP, MARK_KEY + key, Math.min(oldest, last + 1));
            }
        }
        return out;
    }

    /**
     * Whether a stored trade is a fill of an offer still open, which is never sent: only an offer
     * that has ended is. While an offer fills its fills are stored one by one, and folded into one
     * record when it ends; sent as they stand they would be judged, and perhaps stored, ahead of
     * the fills still to come.
     *
     * <p>A record with an end of its own, or a completion, is over. A fill with neither is still
     * open only while its slot's saved position is that same offer, not finished and not since
     * seen empty: same item, same side, placed at the same moment. Once the slot holds another
     * offer or nothing, the offer the fill belonged to is over, whatever became of it.
     *
     * @param slot the saved position of the trade's slot, or null when there is none
     */
    static boolean open(Delta delta, Stamp slot) {
        return delta.endMs <= 0 && !TradeOfferCollapser.isCompletion(delta) && slot != null
            && slot.itemId == delta.itemId && slot.isBuy == delta.isBuy && slot.firstSeenMs == delta.offerStartMs
            && slot.completedMs <= 0 && slot.lastEmptyMs <= 0;
    }

    /**
     * A stored trade as the record of a finished offer it is sent as, or null for one that names
     * no trade.
     *
     * <p>The id is made from the record and nothing else, so the same record has the same id every
     * time it is sent, from any window: the website keeps it on what it stores, and a record sent
     * again after being stored is known by it. It must stay exactly this, for a record already
     * confirmed under it.
     */
    static GeEvent record(long key, Delta delta, Integer world) {
        if (delta == null || delta.itemId <= 0 || delta.tsClientMs <= 0 || delta.deltaQty <= 0 || delta.deltaGp <= 0) {
            return null;
        }
        GeEvent event = new GeEvent();
        event.event_id = UUID.nameUUIDFromBytes((key + "|" + delta.tsClientMs + "|" + delta.slot + "|" + delta.itemId
            + "|" + delta.isBuy + "|" + delta.deltaQty + "|" + delta.deltaGp + "|" + delta.price + "|"
            + (delta.eventType != null ? delta.eventType : "")).getBytes(StandardCharsets.UTF_8)).toString();
        event.event_type = "OFFER_RECORD";
        event.ts_client_ms = delta.tsClientMs;
        // When the offer ended; where it began, for a fill that has no end of its own.
        event.end_ms = delta.closedAtMs();
        event.slot = Math.max(0, delta.slot);
        event.item_id = delta.itemId;
        event.is_buy = delta.isBuy;
        // The listed price as stored: at the cap for one past max cash (TradeDeltaRecorder), which the
        // website reads as not known. Worked out from the coins it would have to match the live fills
        // exactly, and a purchase that filled under its price would not. From the coins only when no
        // price was stored; a long, as a unit price can outgrow an int.
        event.price = delta.price > 0 ? delta.price : Math.max(1L, delta.deltaGp / delta.deltaQty);
        event.total_qty = event.filled_qty = event.delta_qty = delta.deltaQty;
        event.spent_gp = event.delta_gp = delta.deltaGp;
        event.state = delta.isBuy ? "BOUGHT" : "SOLD";
        event.world = world;
        event.character_id = GeEvent.characterId(key);
        event.source = "record";
        return event;
    }

    private long number(String key) {
        Long value = configManager.getConfiguration(GROUP, key, Long.class);
        return value != null ? value : 0;
    }
}
