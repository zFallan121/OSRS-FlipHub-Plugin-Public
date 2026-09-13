package com.osrsfliphub;

import java.util.Collections;
import java.util.List;

/**
 * A conversion with every item name already resolved to an id.
 */
final class ConversionRecipe {
    final ConversionKind kind;
    final String displayName;
    final List<ConversionItem> inputs;
    final List<ConversionItem> outputs;
    final long feeGp;

    ConversionRecipe(ConversionKind kind,
                     String displayName,
                     List<ConversionItem> inputs,
                     List<ConversionItem> outputs,
                     long feeGp) {
        this.kind = kind;
        this.displayName = displayName != null ? displayName : "";
        this.inputs = inputs != null ? Collections.unmodifiableList(inputs) : Collections.emptyList();
        this.outputs = outputs != null ? Collections.unmodifiableList(outputs) : Collections.emptyList();
        this.feeGp = Math.max(0L, feeGp);
    }

    int outputQuantityOf(int itemId) {
        for (ConversionItem output : outputs) {
            if (output.itemId == itemId) {
                return output.quantity;
            }
        }
        return 0;
    }

    boolean isUsable() {
        if (kind == null || inputs.isEmpty() || outputs.isEmpty()) {
            return false;
        }
        return allResolved(inputs) && allResolved(outputs);
    }

    private static boolean allResolved(List<ConversionItem> items) {
        for (ConversionItem item : items) {
            if (item == null || item.itemId <= 0 || item.quantity <= 0) {
                return false;
            }
        }
        return true;
    }
}
