package com.osrsfliphub;

import java.util.Objects;

/**
 * What identifies one stored trade across restarts and replays.
 */
final class LocalTradeKey {
    long tsMs;
    int slot;
    int itemId;

    LocalTradeKey() {
    }

    LocalTradeKey(long tsMs, int slot, int itemId) {
        this.tsMs = tsMs;
        this.slot = slot;
        this.itemId = itemId;
    }

    static LocalTradeKey of(LocalTradeDelta delta) {
        return delta != null ? new LocalTradeKey(delta.tsClientMs, delta.slot, delta.itemId) : null;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LocalTradeKey)) {
            return false;
        }
        LocalTradeKey that = (LocalTradeKey) other;
        return tsMs == that.tsMs && slot == that.slot && itemId == that.itemId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tsMs, slot, itemId);
    }

    @Override
    public String toString() {
        return tsMs + "/" + slot + "/" + itemId;
    }
}
