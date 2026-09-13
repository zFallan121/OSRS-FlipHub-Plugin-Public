package com.osrsfliphub;

/** One side of a conversion: an item, and how many of it the conversion moves. */
final class ConversionItem {
    final int itemId;
    final int quantity;

    ConversionItem(int itemId, int quantity) {
        this.itemId = itemId;
        this.quantity = Math.max(0, quantity);
    }
}
