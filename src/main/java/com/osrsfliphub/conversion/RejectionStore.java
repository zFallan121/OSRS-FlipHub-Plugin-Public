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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * The recipe guesses each account's player has dismissed, held beside the
 * account's trades and written into the same file.
 *
 * <p>Both ledgers are pure functions over the stored trades and are rebuilt
 * from them on every refresh, so a correction has to be a stored fact they
 * read back, not an edit to their output. {@link Ledger} asks here
 * before it runs a recipe, and refuses when the sale asking is one the player
 * has ruled out.
 *
 * <p>A correction belongs to the profile whose trades it names. The
 * accountwide view is built profile by profile, so each profile answers for
 * its own; the pooled accountwide replay - the fallback when no per-profile
 * file can be found - has every profile's trades in one list and is answered
 * by every profile's corrections.
 */
@Singleton
final class RejectionStore {
    private final long accountwideKey = Const.ACCOUNTWIDE_KEY;
    private final Map<Long, List<ConversionRejection>> byAccount = new HashMap<>();

    @Inject
    RejectionStore() {
    }

    synchronized boolean isRejected(long accountKey, TradeKey key) {
        return find(accountKey, key) != null;
    }

    synchronized ConversionRejection find(long accountKey, TradeKey key) {
        if (key == null) {
            return null;
        }
        for (ConversionRejection rejection : applicable(accountKey)) {
            if (rejection.covers(key)) {
                return rejection;
            }
        }
        return null;
    }

    /**
     * Every correction a replay of this account's trades has to honour: the
     * account's own, and those filed against the pool - which, for the pool
     * itself, is all of them.
     */
    synchronized List<ConversionRejection> applicable(long accountKey) {
        List<ConversionRejection> out = new ArrayList<>();
        if (accountKey == accountwideKey) {
            for (List<ConversionRejection> list : byAccount.values()) {
                addDistinct(out, list);
            }
        } else {
            addDistinct(out, listFor(accountKey));
            addDistinct(out, listFor(accountwideKey));
        }
        return out;
    }

    /** The same guess filed twice - once per file it was written to - is one correction. */
    private static void addDistinct(List<ConversionRejection> out, List<ConversionRejection> source) {
        for (ConversionRejection candidate : source) {
            boolean seen = false;
            for (ConversionRejection existing : out) {
                if (existing.sameTrades(candidate)) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                out.add(candidate);
            }
        }
    }

    /** Files a correction. False when the same guess was already dismissed. */
    synchronized boolean add(long accountKey, ConversionRejection rejection) {
        if (rejection == null || rejection.trades == null || rejection.trades.isEmpty()) {
            return false;
        }
        for (ConversionRejection existing : applicable(accountKey)) {
            if (existing.sameTrades(rejection)) {
                return false;
            }
        }
        byAccount.computeIfAbsent(accountKey, ignored -> new ArrayList<>()).add(rejection);
        return true;
    }

    /** Withdraws a correction, wherever it was filed. False when there was none. */
    synchronized boolean remove(long accountKey, ConversionRejection rejection) {
        if (rejection == null) {
            return false;
        }
        boolean removed = removeFrom(accountKey, rejection);
        if (!removed && accountKey != accountwideKey) {
            removed = removeFrom(accountwideKey, rejection);
        }
        if (!removed && accountKey == accountwideKey) {
            for (Long key : new ArrayList<>(byAccount.keySet())) {
                if (removeFrom(key, rejection)) {
                    removed = true;
                }
            }
        }
        return removed;
    }

    private boolean removeFrom(long accountKey, ConversionRejection rejection) {
        List<ConversionRejection> list = byAccount.get(accountKey);
        if (list == null) {
            return false;
        }
        boolean removed = false;
        Iterator<ConversionRejection> iterator = list.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().sameTrades(rejection)) {
                iterator.remove();
                removed = true;
            }
        }
        return removed;
    }

    /** What the account's own file should carry. The pool's file carries everything. */
    synchronized List<ConversionRejection> snapshotForFile(long accountKey) {
        return accountKey == accountwideKey ? applicable(accountKey) : new ArrayList<>(listFor(accountKey));
    }

    /** What was read from the account's file; null or empty clears it. */
    synchronized void replace(long accountKey, List<ConversionRejection> loaded) {
        List<ConversionRejection> kept = new ArrayList<>();
        Set<Set<TradeKey>> seen = new HashSet<>();
        if (loaded != null) {
            for (ConversionRejection rejection : loaded) {
                if (rejection == null || rejection.trades == null || rejection.trades.isEmpty()) {
                    continue;
                }
                if (seen.add(new HashSet<>(rejection.trades))) {
                    kept.add(rejection);
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

    /** One account folded into another: its corrections go with its trades. */
    synchronized void move(long sourceKey, long targetKey) {
        List<ConversionRejection> source = byAccount.remove(sourceKey);
        if (source == null || source.isEmpty() || sourceKey == targetKey) {
            return;
        }
        for (ConversionRejection rejection : source) {
            add(targetKey, rejection);
        }
    }

    private List<ConversionRejection> listFor(long accountKey) {
        List<ConversionRejection> list = byAccount.get(accountKey);
        return list != null ? list : Collections.emptyList();
    }
}
