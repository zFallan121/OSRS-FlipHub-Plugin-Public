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

import java.util.Collections;
import java.util.List;

/**
 * A conversion with every item name already resolved to an id.
 *
 * <p>{@code feeGp} is a coin cost that is part of making the thing at all - the
 * Zamorakian hasta's 300K, a Barrows repair's 60K to 100K. It is charged per
 * run, and it is the one number in an activity that never came from the Grand
 * Exchange. The optional NPC route that lets a player skip a skill requirement
 * is deliberately NOT here: the plugin cannot see which route was taken and the
 * two answers are millions apart, so charging it would be a guess.
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
