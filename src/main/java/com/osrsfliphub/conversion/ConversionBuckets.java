package com.osrsfliphub;

/**
 * The per-item inventory both ledgers already keep, seen through the four
 * operations a conversion needs.
 */
interface ConversionBuckets {
    long quantityOf(int itemId);

    /**
     * How much of {@link #quantityOf} was actually bought, excluding pieces
     * that came out of taking something apart.
     */
    long convertibleQuantityOf(int itemId);

    /**
     * How much of {@link #quantityOf} may satisfy the sale being covered even
     * though it already happened: stock recovered from the GE history widget
     * rather than watched live, because only that stock has a timestamp the
     * plugin invented - less any of it the history itself lists after the
     * sale. One read of the history is one batch, imported in the history's
     * own order, and inside a batch that order is evidence. The buckets are
     * built knowing which sale is asking, so the answer is specific to it;
     * see {@link ConversionSyncedStock}.
     */
    long syncedQuantityOf(int itemId);

    long costOf(int itemId);

    /** Remove {@code quantity} units and the {@code cost} they carried. */
    void consume(int itemId, long quantity, long cost);

    /**
     * Add {@code quantity} units carrying {@code cost} as their basis, made by
     * {@code match}. Only a conversion that produced a single thing credits this
     * way, because only then does one item carry the whole cost.
     */
    void credit(int itemId, long quantity, long cost, ConversionMatch match);

    /**
     * Add {@code quantity} units that belong to {@code pending} and carry no
     * cost of their own.
     */
    void creditBreak(int itemId, long quantity, ConversionBreak pending);
}
