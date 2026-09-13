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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class ProfileTradesLoader {
    static final class Result {
        final List<Delta> deltas;
        final String resolvedDisplayName;
        final long profileFileModifiedMs;
        /**
         * The profile file is on disk but could not be parsed, so {@link #deltas} is empty
         * because nothing could be read, not because the account has no history. Callers must
         * not treat this as an empty account: replacing the in-memory list would drop the
         * history, and persisting afterwards would write that loss to disk.
         */
        final boolean unreadable;
        /** The recipe guesses the file says the player dismissed; empty for a file that predates them. */
        final List<ConversionRejection> rejectedConversions;
        final List<RecipeFlip> recipeFlips;

        Result(List<Delta> deltas, String resolvedDisplayName, long profileFileModifiedMs) {
            this(deltas, resolvedDisplayName, profileFileModifiedMs, false);
        }

        Result(List<Delta> deltas,
               String resolvedDisplayName,
               long profileFileModifiedMs,
               boolean unreadable) {
            this(deltas, resolvedDisplayName, profileFileModifiedMs, unreadable, null);
        }

        Result(List<Delta> deltas,
               String resolvedDisplayName,
               long profileFileModifiedMs,
               boolean unreadable,
               List<ConversionRejection> rejectedConversions) {
            this(deltas, resolvedDisplayName, profileFileModifiedMs, unreadable, rejectedConversions, null);
        }

        Result(List<Delta> deltas,
               String resolvedDisplayName,
               long profileFileModifiedMs,
               boolean unreadable,
               List<ConversionRejection> rejectedConversions,
               List<RecipeFlip> recipeFlips) {
            this.recipeFlips = recipeFlips != null ? recipeFlips : new ArrayList<>();
            this.deltas = deltas != null ? deltas : new ArrayList<>();
            this.resolvedDisplayName = resolvedDisplayName;
            this.profileFileModifiedMs = profileFileModifiedMs;
            this.unreadable = unreadable;
            this.rejectedConversions = rejectedConversions != null ? rejectedConversions : new ArrayList<>();
        }
    }

    private final long accountwideKey = Const.ACCOUNTWIDE_KEY;

    @Inject
    ProfileTradesLoader() {
    }

    private ProfileStorage storage() {
        return Bridge.get(ProfileStorage.class);
    }

    Result load(long accountHash,
                long localEventBucketMs,
                long duplicateTradeWindowMs) {
        if (accountHash < 0) {
            return null;
        }
        ProfileStorage storage = storage();
        long fileMs = 0L;
        Path file = storage != null ? storage.getProfileFile(accountHash) : null;
        if (file != null) {
            fileMs = Access.plugin().getProfileFileModifiedMs(file);
        }

        ProfileData profile = storage != null ? storage.readProfileData(accountHash) : null;
        // A non-zero modification time means the file is there. Getting nothing back from a
        // file that exists means it is corrupt or truncated, which is not the same as an
        // account with no trades. Accountwide is exempt: its list is rebuilt from the
        // per-profile files below, so its own file being unreadable costs nothing.
        // A missing deltas list counts as unreadable too. Gson hands back a ProfileData with a
        // null list for any JSON object that simply lacks the field - "{}", a hand edit, a
        // recovery tool's output - and that used to read as an account with no trades, which
        // the loader then persisted straight back over the real file.
        if ((profile == null || profile.deltas == null) && fileMs > 0 && accountHash != accountwideKey) {
            return new Result(null, null, Math.max(0L, fileMs), true);
        }
        List<Delta> merged = profile != null ? profile.deltas : null;
        String profileName = profile != null ? profile.displayName : null;
        boolean placeholderName = ProfileDisplayNames.isPlaceholder(profileName);
        if (accountHash == accountwideKey) {
            TradesMerge mergeService =
                Bridge.get(TradesMerge.class);
            merged = mergeService != null ? mergeService.buildAccountwideFromDisk() : null;
        }
        merged = TradeDeltaUtils.dedupeLocalTrades(
            merged,
            localEventBucketMs,
            duplicateTradeWindowMs
        );
        if (merged == null) {
            merged = new ArrayList<>();
        }

        String resolvedName = null;
        if (profileName != null && !profileName.trim().isEmpty() && !placeholderName) {
            resolvedName = profileName.trim();
        }
        List<ConversionRejection> corrections = profile != null ? profile.rejectedConversions : null;
        List<RecipeFlip> recorded = profile != null ? profile.recipeFlips : null;
        return new Result(merged, resolvedName, Math.max(0L, fileMs), false, corrections, recorded);
    }
}
