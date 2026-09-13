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

    @Inject
    RecipeFlipStore() {
    }

    /** Every record that may explain trades replayed under {@code accountKey}. */
    synchronized List<RecipeFlip> applicable(long accountKey) {
        List<RecipeFlip> out = new ArrayList<>();
        if (accountKey == accountwideKey) {
            for (List<RecipeFlip> list : byAccount.values()) {
                out.addAll(list);
            }
            return out;
        }
        out.addAll(listFor(accountKey));
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
     * Forget one record, identified by the trades it names.
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
        return list.removeIf(candidate -> sameTrades(candidate, flip));
    }

    private static boolean sameTrades(RecipeFlip a, RecipeFlip b) {
        List<TradeKey> left = a.trades();
        List<TradeKey> right = b.trades();
        return left.size() == right.size() && left.containsAll(right);
    }

    /** What to write into this account's profile file. */
    synchronized List<RecipeFlip> snapshotForFile(long accountKey) {
        return new ArrayList<>(listFor(accountKey));
    }

    /** Take what a profile file held, discarding anything unusable. */
    synchronized void replace(long accountKey, List<RecipeFlip> flips) {
        List<RecipeFlip> kept = new ArrayList<>();
        if (flips != null) {
            for (RecipeFlip flip : flips) {
                if (flip != null && flip.isUsable()) {
                    kept.add(flip);
                }
            }
        }
        if (kept.isEmpty()) {
            byAccount.remove(accountKey);
        } else {
            byAccount.put(accountKey, kept);
        }
    }

    synchronized void clear(long accountKey) {
        byAccount.remove(accountKey);
    }
}
