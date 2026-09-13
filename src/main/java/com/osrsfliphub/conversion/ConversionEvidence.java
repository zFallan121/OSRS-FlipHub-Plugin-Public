package com.osrsfliphub;

/**
 * How much the plugin can actually prove about a conversion it is considering.
 */
enum ConversionEvidence {
    /** The inputs were watched live, so their order against the sale is real. */
    ORDERED,
    /** The inputs came from the in-game history, whose timestamps the plugin invented. */
    SYNCED
}
