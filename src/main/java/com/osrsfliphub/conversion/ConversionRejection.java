package com.osrsfliphub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A recipe the plugin guessed and the player said was wrong.
 */
final class ConversionRejection {
    /** The item the guess was filed against: the card it appeared on. */
    int itemId;
    /** {@link ConversionKind#name()}; a string so an unknown value loads as null rather than failing. */
    String kind;
    String name;
    long completionTsMs;
    long rejectedAtMs;
    List<LocalTradeKey> trades;

    ConversionRejection() {
    }

    ConversionRejection(int itemId, String kind, String name, long completionTsMs, long rejectedAtMs,
                        List<LocalTradeKey> trades) {
        this.itemId = itemId;
        this.kind = kind;
        this.name = name;
        this.completionTsMs = completionTsMs;
        this.rejectedAtMs = rejectedAtMs;
        this.trades = trades != null ? new ArrayList<>(trades) : new ArrayList<>();
    }

    /** The correction for an entry the panel is showing, or null if it has nothing to correct. */
    static ConversionRejection of(StatsFlipInstance instance, long nowMs) {
        if (instance == null || instance.conversionKind == null || instance.conversionTrades.isEmpty()) {
            return null;
        }
        return new ConversionRejection(
            instance.itemId,
            instance.conversionKind.name(),
            instance.conversionName,
            instance.completionTsMs,
            nowMs,
            instance.conversionTrades);
    }

    ConversionKind kindOrNull() {
        return ConversionKind.parse(kind);
    }

    List<LocalTradeKey> trades() {
        return trades != null ? Collections.unmodifiableList(trades) : Collections.emptyList();
    }

    boolean covers(LocalTradeKey key) {
        return key != null && trades != null && trades.contains(key);
    }

    /** Whether any of the sales this covers is among {@code sales}. */
    boolean touches(Set<LocalTradeKey> sales) {
        if (sales == null || sales.isEmpty() || trades == null) {
            return false;
        }
        for (LocalTradeKey key : trades) {
            if (sales.contains(key)) {
                return true;
            }
        }
        return false;
    }

    /** Two corrections of the same guess: the same sales, in any order. */
    boolean sameTrades(ConversionRejection other) {
        if (other == null || trades == null || other.trades == null) {
            return false;
        }
        return new HashSet<>(trades).equals(new HashSet<>(other.trades));
    }
}
