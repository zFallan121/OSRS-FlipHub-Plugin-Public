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
import java.util.*;
import javax.inject.*;
import lombok.RequiredArgsConstructor;

/**
 * Tells the website about the recipes the player records.
 *
 * <p>The website pairs purchases with sales item by item. It is never told that four Dharok
 * pieces became a set, so it showed the pieces as held for ever and the set as sold out of
 * nowhere, while this plugin's own ledger had the flip right. A recorded recipe already names
 * the purchases that went in and the sales that came out; this sends exactly that.
 *
 * <p>It rides the trade upload, one event to a named trade, so it needs no queue, retry or
 * signing of its own. The events are not trades and the website does not store them as trades:
 * {@code event_type} is {@code RECIPE_IN} or {@code RECIPE_OUT}, the slot, item and
 * {@code ts_client_ms} are the named trade's own (which is how the website finds it), and
 * {@code delta_qty} and {@code delta_gp} are the quantity used and the coins that go with it.
 * Deleting a recipe sends a {@code RECIPE_VOID}.
 *
 * <p>Every id is derived from the recipe, never random, so sending one again changes nothing
 * on the website. That is what lets the recipes already on disk be sent once a session: a
 * recipe recorded before this existed, or whose upload was lost, still gets there.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
final class RecipeUpload {
    private final PluginConfig config;
    private final TradeSession tradeSession;
    private final RecipeFlipStore recipeFlipStore;
    private final UploadEventDispatch uploadEventDispatch;
    private final UploadBackfillDispatch uploadBackfillDispatch;
    private final Set<String> sent = new HashSet<>();

    /** Every recipe stored for the account that has not been sent this session. */
    void sendStored(long accountKey) {
        for (RecipeFlip flip : recipeFlipStore.snapshotForFile(accountKey)) {
            send(accountKey, flip);
        }
    }

    synchronized void send(long accountKey, RecipeFlip flip) {
        if (!linked() || !sent.add(recipeId(accountKey, flip))) {
            return;
        }
        enqueue(parts(accountKey, flip, tradeSession.snapshotLocalTradeDeltas(accountKey)));
    }

    synchronized void sendDeleted(long accountKey, RecipeFlip flip) {
        if (linked()) {
            sent.remove(recipeId(accountKey, flip));
            enqueue(Collections.singletonList(deleted(accountKey, flip)));
        }
    }

    private boolean linked() {
        return config.enableFlipHubSync() && Str.hasText(config.sessionToken());
    }

    private void enqueue(List<GeEvent> events) {
        for (GeEvent event : events) {
            uploadEventDispatch.enqueueEvent(event);
        }
        if (!events.isEmpty()) {
            uploadBackfillDispatch.requestEventFlush();
        }
    }

    /**
     * One event to each trade the recipe names, purchases first. Empty when a trade it names is
     * no longer stored: the ledger does not apply such a recipe either, and half a recipe would
     * sit on the website waiting for parts that are never coming.
     */
    static List<GeEvent> parts(long accountKey, RecipeFlip flip, List<Delta> deltas) {
        Map<TradeKey, Delta> byKey = new HashMap<>();
        for (Delta delta : deltas) {
            byKey.put(TradeKey.of(delta), delta);
        }
        List<RecipeFlip.Part> named = new ArrayList<>(flip.inputParts());
        named.addAll(flip.outputParts());
        String recipeId = recipeId(accountKey, flip);
        List<GeEvent> events = new ArrayList<>();
        for (RecipeFlip.Part part : named) {
            Delta delta = byKey.get(part.trade);
            if (delta == null) {
                return new ArrayList<>();
            }
            GeEvent event = event(recipeId, String.valueOf(events.size()),
                events.size() < flip.inputParts().size() ? "RECIPE_IN" : "RECIPE_OUT", delta.tsClientMs);
            event.recipe_kind = flip.kind.name();
            event.recipe_parts = named.size();
            event.recipe_fee_gp = flip.feeGp;
            event.recipe_trade_end_ms = delta.closedAtMs();
            event.slot = delta.slot;
            event.item_id = delta.itemId;
            event.is_buy = delta.isBuy;
            event.delta_qty = part.quantity;
            event.delta_gp = RecipeFlipLedger.share(delta.deltaGp, part.quantity, delta.deltaQty);
            events.add(event);
        }
        return events;
    }

    static GeEvent deleted(long accountKey, RecipeFlip flip) {
        return event(recipeId(accountKey, flip), "void", "RECIPE_VOID", Math.max(1L, flip.recordedMs));
    }

    /**
     * The same recipe always has the same id, and a recipe recorded again after being deleted a
     * new one: when it was recorded is part of it, or the website would go on ignoring it as the
     * one it was told to forget.
     */
    static String recipeId(long accountKey, RecipeFlip flip) {
        return uuid(accountKey + "|" + flip.recordedMs + "|" + flip.trades());
    }

    private static GeEvent event(String recipeId, String part, String type, long tsMs) {
        GeEvent event = new GeEvent();
        event.event_id = uuid(recipeId + "|" + part);
        event.event_type = type;
        event.recipe_id = recipeId;
        event.ts_client_ms = tsMs;
        return event;
    }

    private static String uuid(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
