package com.osrsfliphub;

import java.util.Set;

/**
 * Which activities a figure covers - the Profile list, and the total at the top
 * of the tab.
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
