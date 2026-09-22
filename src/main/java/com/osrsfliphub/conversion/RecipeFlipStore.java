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

import java.util.*;
import javax.inject.*;

/**
 * The conversions each account has recorded, held in memory and written into that account's
 * profile file.
 *
 * <p>Scoped per account for the same reason the trades are: one character's recorded conversion
 * must not explain another's trades. The accountwide view sees every account's records, because
 * it replays every account's trades.</p>
 */
@Singleton
final class RecipeFlipStore {
    private final long accountwideKey = Const.ACCOUNTWIDE_KEY;
    private final Map<Long, List<RecipeFlip>> byAccount = new HashMap<>();
    /**
     * When each account's history was wiped. A move recorded to it before then went with it: it
     * lives in the other account's file, where the wipe could not reach, and kept handing over
     * stock the wiped account no longer had any record of.
     */
    private final Map<Long, Long> wipedAt = new HashMap<>();
    /**
     * A character once filed under its name's key and now under its account hash: its trades are
     * folded into the new key (AccountMerge), and its records, and every move to it, count as the
     * new key's. They stay filed under the old one, which their ids are made from - moved, they
     * would reach the website as new records and be counted twice.
     */
    private final Map<Long, Long> foldedInto = new HashMap<>();

    @Inject
    RecipeFlipStore() {
    }

    /** Every record that may explain trades replayed under {@code accountKey}. */
    synchronized List<RecipeFlip> applicable(long accountKey) {
        List<RecipeFlip> out = new ArrayList<>();
        byAccount.forEach((account, flips) -> flips.forEach(flip -> {
            if ((accountKey == accountwideKey || foldedInto.getOrDefault(account, account) == accountKey)
                && flip.isUsable()) {
                out.add(flip);
            }
        }));
        return out;
    }

    /**
     * The moves every other account recorded to this one, by the account that recorded them.
     * Whether each still hands anything over is the ledger's to say: {@link RecipeFlipLedger#received}.
     */
    synchronized Map<Long, List<RecipeFlip>> movesTo(long accountKey) {
        Map<Long, List<RecipeFlip>> out = new HashMap<>();
        long wiped = wipedAt.getOrDefault(accountKey, 0L);
        byAccount.forEach((giver, flips) -> flips.forEach(flip -> {
            if (flip.toAccount != null && foldedInto.getOrDefault(flip.toAccount, flip.toAccount) == accountKey
                && flip.recordedMs > wiped && flip.isUsable()) {
                // By the account the giver is now: a folded key's trades are filed under the new one.
                out.computeIfAbsent(foldedInto.getOrDefault(giver, giver), ignored -> new ArrayList<>()).add(flip);
            }
        }));
        return out;
    }

    private List<RecipeFlip> listFor(long accountKey) {
        List<RecipeFlip> list = byAccount.get(accountKey);
        return list != null ? list : new ArrayList<>();
    }

    /** @return whether it was stored; a record naming no trades or no kind is refused. */
    synchronized boolean add(long accountKey, RecipeFlip flip) {
        if (flip == null || !flip.isUsable()) {
            return false;
        }
        byAccount.computeIfAbsent(accountKey, ignored -> new ArrayList<>()).add(flip);
        return true;
    }

    /**
     * Forget one record, identified by the trades it names and when it was recorded.
     *
     * <p>The trades alone are not enough. Half a purchase moved to one account and half to
     * another are two records naming the same trade, and forgetting one used to take both -
     * while telling the website about only the one.
     *
     * @return whether anything was removed
     */
    synchronized boolean remove(long accountKey, RecipeFlip flip) {
        if (flip == null) {
            return false;
        }
        List<RecipeFlip> list = byAccount.get(accountKey);
        if (list == null) {
            return false;
        }
        if (!list.removeIf(candidate -> sameTrades(candidate, flip))) {
            return false;
        }
        // What the website has to be told, kept until it surely has been. See RecipeFlip.voided.
        RecipeFlip forgotten = new RecipeFlip();
        forgotten.voided = RecipeUpload.recipeId(accountKey, flip);
        forgotten.recordedMs = flip.recordedMs;
        list.add(forgotten);
        return true;
    }

    private static boolean sameTrades(RecipeFlip a, RecipeFlip b) {
        List<TradeKey> left = a.trades();
        List<TradeKey> right = b.trades();
        return a.recordedMs == b.recordedMs && left.size() == right.size() && left.containsAll(right);
    }

    /** Every account's records, copied, so they can be worked through without holding the store. */
    synchronized Map<Long, List<RecipeFlip>> snapshotAll() {
        Map<Long, List<RecipeFlip>> out = new HashMap<>();
        byAccount.forEach((account, flips) -> out.put(account, new ArrayList<>(flips)));
        return out;
    }

    /** What to write into this account's profile file. */
    synchronized List<RecipeFlip> snapshotForFile(long accountKey) {
        return new ArrayList<>(listFor(accountKey));
    }

    /**
     * Take what a profile file held. Kept whole, though only what makes sense is used: a record of a
     * kind this build does not know - written by a newer one - used to be dropped here, and the file
     * then written back without it.
     *
     * @return whether a move is among what was held before or what is held now. A move changes
     *         another account's totals, so whoever loaded this has every account's to drop.
     */
    synchronized boolean replace(long accountKey, List<RecipeFlip> flips) {
        List<RecipeFlip> kept = new ArrayList<>();
        boolean moves = false;
        for (RecipeFlip flip : listFor(accountKey)) {
            moves |= flip.toAccount != null;
        }
        if (flips != null) {
            for (RecipeFlip flip : flips) {
                if (flip != null) {
                    kept.add(flip);
                    moves |= flip.toAccount != null;
                }
            }
        }
        if (kept.isEmpty()) {
            byAccount.remove(accountKey);
        } else {
            byAccount.put(accountKey, kept);
        }
        return moves;
    }

    synchronized void fold(long from, long into) {
        foldedInto.put(from, into);
    }

    synchronized void wiped(long accountKey, Long ms) {
        if (ms != null) {
            wipedAt.put(accountKey, ms);
        }
    }

    synchronized void clear(long accountKey) {
        byAccount.remove(accountKey);
    }
}
