package com.osrsfliphub;

/**
 * Which read of the Grand Exchange history a synced trade came from.
 */
final class ConversionSyncedBatches {
    /** What {@link #batchOf} answers for a trade that was watched live. */
    static final int LIVE = 0;

    private int batch = LIVE;
    private int lastSlot = Integer.MIN_VALUE;

    /**
     * The batch this trade belongs to: {@link #LIVE} for a trade watched live,
     * else 1 upward in replay order. Must be fed every trade in the order they
     * are replayed.
     */
    int batchOf(LocalTradeDelta delta) {
        if (!LocalTradeDeltaUtils.isSyncedFromHistory(delta)) {
            return LIVE;
        }
        if (batch == LIVE || delta.slot <= lastSlot) {
            batch++;
        }
        lastSlot = delta.slot;
        return batch;
    }

    void reset() {
        batch = LIVE;
        lastSlot = Integer.MIN_VALUE;
    }
}
