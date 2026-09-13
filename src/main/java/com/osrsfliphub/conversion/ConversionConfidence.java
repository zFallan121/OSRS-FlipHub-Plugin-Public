package com.osrsfliphub;

/**
 * How firmly an activity is attributed. Not shown in the panel - the numbers
 * are the same either way - but the distinction is real and worth recording.
 */
enum ConversionConfidence {
    /** Every input was held before the sale. */
    CONFIRMED,
    /** The inputs are real purchases, but only a made-up timestamp orders them. */
    LIKELY
}
