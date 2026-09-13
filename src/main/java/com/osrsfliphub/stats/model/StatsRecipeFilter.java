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

import java.util.Set;

/**
 * Which activities a figure covers - the Profile list, and the total at the top
 * of the tab.
 *
 * <p>{@link #ALL} is the default everywhere and the only value most accounts
 * ever need. The rest exist because a Guardian boots card that blends two
 * assembles with an ordinary flip reports a return that is true of neither.
 */
enum StatsRecipeFilter {
    ALL("All"),
    FLIP("Flips"),
    ANY_RECIPE("Recipes"),
    ASSEMBLE("Assembled", ConversionKind.ASSEMBLE),
    DISASSEMBLE("Disassembled", ConversionKind.DISASSEMBLE),
    REPAIR("Repaired", ConversionKind.REPAIR),
    SET_COMBINE("Set combine", ConversionKind.SET_COMBINE),
    SET_BREAK("Set break", ConversionKind.SET_BREAK);

    private final String label;
    private final ConversionKind kind;

    StatsRecipeFilter(String label) {
        this(label, null);
    }

    StatsRecipeFilter(String label, ConversionKind kind) {
        this.label = label;
        this.kind = kind;
    }

    /** True when an activity of this kind belongs in the figure. Null is a flip. */
    boolean matchesKind(ConversionKind activityKind) {
        switch (this) {
            case ALL:
                return true;
            case FLIP:
                return activityKind == null;
            case ANY_RECIPE:
                return activityKind != null;
            default:
                return activityKind == kind;
        }
    }

    /**
     * True when an item has anything this filter admits. An item that was both
     * flipped and assembled appears under either - and the card it opens shows
     * only the half that was asked for.
     */
    boolean matches(Set<ConversionKind> kinds, boolean hasPlainFlip) {
        boolean hasRecipe = kinds != null && !kinds.isEmpty();
        switch (this) {
            case ALL:
                return true;
            case FLIP:
                return hasPlainFlip;
            case ANY_RECIPE:
                return hasRecipe;
            default:
                return hasRecipe && kinds.contains(kind);
        }
    }

    @Override
    public String toString() {
        return label;
    }
}
