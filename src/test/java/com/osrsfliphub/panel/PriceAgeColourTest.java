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

import java.awt.Color;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * One rule for whether a price counts as stale, used by the price and by its age.
 *
 * <p>The card colours the price and the tooltip colours the age of that same price. They are
 * the same fact said twice, so they decide it with the same function: two copies of the
 * threshold could drift apart, and an amber price beside a white age tells the reader nothing.
 */
public class PriceAgeColourTest {
    private static final long NOW = 1_700_000_000_000L;
    private static final long AMBER_AFTER = Skin.STALE_PRICE_AGE_MS;
    private static final long RED_AFTER = Skin.VERY_STALE_PRICE_AGE_MS;

    @Test
    public void aFreshPriceIsPlain() {
        assertEquals(Skin.TEXT,
            Skin.priceAgeColor(NOW - 60_000L, NOW));
    }

    /** Half an hour is the first line, and it turns amber the moment it is reached. */
    @Test
    public void thePriceTurnsAmberAtHalfAnHourAndNotBefore() {
        assertEquals(Skin.TEXT,
            Skin.priceAgeColor(NOW - (AMBER_AFTER - 1L), NOW));
        assertEquals(Skin.WARNING,
            Skin.priceAgeColor(NOW - AMBER_AFTER, NOW));
    }

    /** An hour is the second, and amber holds right up to it. */
    @Test
    public void thePriceTurnsRedAtTheHourAndNotBefore() {
        assertEquals(Skin.WARNING,
            Skin.priceAgeColor(NOW - (RED_AFTER - 1L), NOW));
        assertEquals(Skin.DANGER,
            Skin.priceAgeColor(NOW - RED_AFTER, NOW));
    }

    /** And it stays red however old it gets, rather than wrapping round to anything else. */
    @Test
    public void aVeryOldPriceStaysRed() {
        assertEquals(Skin.DANGER,
            Skin.priceAgeColor(NOW - (30L * RED_AFTER), NOW));
    }

    /** The two lines are where they were asked to be: half an hour, then an hour. */
    @Test
    public void theBandsAreHalfAnHourAndAnHour() {
        assertEquals(30L * 60L * 1000L, AMBER_AFTER);
        assertEquals(60L * 60L * 1000L, RED_AFTER);
    }

    /** A price nobody has a time for cannot be judged, so it is not. */
    @Test
    public void anUntimedPriceIsNotJudged() {
        assertEquals(Skin.TEXT, Skin.priceAgeColor(null, NOW));
        assertEquals(Skin.TEXT, Skin.priceAgeColor(0L, NOW));
        assertEquals(Skin.TEXT, Skin.priceAgeColor(-1L, NOW));
    }

    /** The tooltips are built out of HTML, so the colour has to survive being written as text. */
    @Test
    public void aColourWritesAsSixHexDigits() {
        assertEquals("#FF0000", Skin.toHex(new Color(255, 0, 0)));
        assertEquals("#0A0B0C", Skin.toHex(new Color(10, 11, 12)));
        // Alpha is dropped rather than smuggled into the digits.
        assertEquals("#FFFFFF", Skin.toHex(new Color(255, 255, 255, 235)));
        assertEquals("#FFFFFF", Skin.toHex(null));
    }
}
