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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * What a conversion's fee actually cost the player, as opposed to what it says
 * on the recipe.
 *
 * <p>The table ships the NPC price, because that is the one price that is the
 * same for everybody. Almost nobody pays it: a repair done at a player-owned
 * house armour stand costs
 * {@code npcPrice x (1 - smithing / 200)} - half a percent off per Smithing
 * level, so a little over half price at 99 - and a player repairing Barrows to
 * flip it has every reason to use the stand. Charging them the NPC price
 * overstates what the item cost them and understates the flip.
 *
 * <p>Two things here are estimates and are meant to be. The plugin cannot see
 * where a repair happened, so it is a setting; and it cannot see what the
 * player's Smithing level was at the time, so it uses the level now. Both are
 * the same kind of approximation the ledger already makes elsewhere, and both
 * move the cost basis rather than the revenue - a sale's gp is always real.
 *
 * <p>Only a repair is discounted. An assembly fee - a Voidwaker, a Zamorakian
 * hasta - is a fixed price paid to an NPC for a job no armour stand does.
 */
@Singleton
final class FeeService {
    /** Every two Smithing levels take one percent off, so 200 is the whole of it. */
    private static final int ARMOUR_STAND_SCALE = 200;
    private static final int MAX_SMITHING_LEVEL = 99;

    private final PluginConfig config;
    /**
     * Smithing level by account. Written on the client thread by a stat
     * change, read on the ledger's. One character's level says nothing about
     * another's, and the profile being viewed is not always the one logged in.
     */
    private final Map<Long, Integer> smithingLevelByAccount = new ConcurrentHashMap<>();
    /**
     * A level reported before the client could say whose it was. At login the
     * stat packets can land while the client is still loading, and dropping
     * that report would price the whole session at NPC rates.
     */
    private volatile int pendingSmithingLevel;

    @Inject
    FeeService(PluginConfig config) {
        this.config = config;
    }

    /**
     * This account's Smithing level, as of the last time the client reported
     * it. Returns whether that is news: every repair of the account was priced
     * with the old level, so the caller throws the aggregates away.
     */
    boolean onSmithingLevel(long accountKey, int level) {
        if (level <= 0) {
            return false;
        }
        int capped = Math.min(MAX_SMITHING_LEVEL, level);
        if (accountKey <= 0) {
            pendingSmithingLevel = capped;
            return false;
        }
        Integer previous = smithingLevelByAccount.put(accountKey, capped);
        return previous == null || previous != capped;
    }

    /** Attach a level reported before the account was known. Returns whether anything changed. */
    boolean adoptPendingSmithingLevel(long accountKey) {
        int level = pendingSmithingLevel;
        if (level <= 0 || accountKey <= 0) {
            return false;
        }
        pendingSmithingLevel = 0;
        return onSmithingLevel(accountKey, level);
    }

    int smithingLevel(long accountKey) {
        Integer level = smithingLevelByAccount.get(accountKey);
        return level != null ? level : 0;
    }

    /** A logged-out client knows nobody's level. Returns whether anything was forgotten. */
    boolean clearSmithingLevels() {
        boolean forgot = !smithingLevelByAccount.isEmpty() || pendingSmithingLevel > 0;
        smithingLevelByAccount.clear();
        pendingSmithingLevel = 0;
        return forgot;
    }

    /** What {@code runs} of this recipe would cost this account in fees. */
    long feeFor(ConversionRecipe recipe, long runs, long accountKey) {
        if (recipe == null || runs <= 0L || recipe.feeGp <= 0L) {
            return 0L;
        }
        long npcPrice = recipe.feeGp * runs;
        if (recipe.kind != ConversionKind.REPAIR || !isArmourStandRepair()) {
            return npcPrice;
        }
        int level = smithingLevel(accountKey);
        if (level <= 0) {
            // No level known for this account - it has not logged in this
            // session, say. The NPC price is the one that cannot flatter the
            // flip, so it stands.
            return npcPrice;
        }
        return npcPrice * (ARMOUR_STAND_SCALE - level) / ARMOUR_STAND_SCALE;
    }

    private boolean isArmourStandRepair() {
        return config != null && config.repairAtArmourStand();
    }
}
