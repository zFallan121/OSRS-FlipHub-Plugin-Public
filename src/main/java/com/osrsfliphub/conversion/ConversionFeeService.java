package com.osrsfliphub;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * What a conversion's fee actually cost the player, as opposed to what it says
 * on the recipe.
 */
@Singleton
final class ConversionFeeService {
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
    ConversionFeeService(PluginConfig config) {
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
