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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GeTaxTest {
    private static final int NATURE_RUNE = 561;
    private static final int TWISTED_BOW = 20997;
    private static final int OLD_SCHOOL_BOND = 13190;
    private static final int LOBSTER = 379;
    private static final int RAW_LOBSTER = 377;
    private static final int POISONED_BRONZE_ARROW = 883;
    private static final int CHOCOLATE_CAKE = 1897;
    private static final int ARCEUUS_LIBRARY_TELEPORT = 19613;
    private static final int GILDED_SPADE = 23282;

    @Test
    public void twoPercentIsRoundedDownPerItem() {
        assertEquals(20L, GeTax.perItem(NATURE_RUNE, 1049L));
        assertEquals(20L, GeTax.perItem(NATURE_RUNE, 1000L));
        assertEquals(1L, GeTax.perItem(NATURE_RUNE, 50L));
    }

    /** Below fifty coins two percent rounds away entirely, which is the game's own rule. */
    @Test
    public void anythingUnderFiftyCoinsIsTaxedNothing() {
        assertEquals(0L, GeTax.perItem(NATURE_RUNE, 49L));
        assertEquals(0L, GeTax.perItem(NATURE_RUNE, 1L));
    }

    @Test
    public void taxPerItemStopsAtFiveMillion() {
        assertEquals(4_999_999L, GeTax.perItem(NATURE_RUNE, 249_999_999L));
        assertEquals(GeTax.MAX_TAX_PER_ITEM, GeTax.perItem(NATURE_RUNE, 250_000_000L));
        assertEquals(GeTax.MAX_TAX_PER_ITEM, GeTax.perItem(NATURE_RUNE, 2_000_000_000L));
    }

    /**
     * The game rounds each item separately, so a sale is the per-item tax multiplied out. Taking
     * two percent of the whole sale instead would over-charge by the rounding on every item.
     */
    @Test
    public void aSaleIsThePerItemTaxMultipliedOutNotAPercentageOfTheTotal() {
        assertEquals(200L, GeTax.forSale(NATURE_RUNE, 1049L, 10L));
        assertEquals(209L, 10_490L * 2L / 100L);
    }

    @Test
    public void nonSalesAndUnknownPricesAreTaxedNothing() {
        assertEquals(0L, GeTax.forSale(NATURE_RUNE, 1049L, 0L));
        assertEquals(0L, GeTax.forSale(NATURE_RUNE, 1049L, -5L));
        assertEquals(0L, GeTax.perItem(NATURE_RUNE, 0L));
        assertEquals(0L, GeTax.perItem(NATURE_RUNE, -1L));
    }

    @Test
    public void aTotalWithNoKnownUnitPriceFallsBackToTwoPercentOfIt() {
        assertEquals(209L, GeTax.forGrossTotal(NATURE_RUNE, 10_490L, 5L));
        assertEquals(0L, GeTax.forGrossTotal(NATURE_RUNE, 0L, 5L));
    }

    /**
     * The estimate is still an estimate, but it cannot exceed what the game could have taken.
     * A twisted bow sells for well over 250,000,000, where two per cent of the total is five
     * times the 5,000,000 the game actually charges.
     */
    @Test
    public void theEstimateIsStillHeldToTheCap() {
        assertEquals(5_000_000L, GeTax.forGrossTotal(TWISTED_BOW, 1_200_000_000L, 1L));
        assertEquals(10_000_000L, GeTax.forGrossTotal(TWISTED_BOW, 2_400_000_000L, 2L));
        assertEquals("under the cap it is still two per cent",
            2_000_000L, GeTax.forGrossTotal(TWISTED_BOW, 100_000_000L, 1L));
    }

    /** An exempt item pays nothing at any price, so the cap never even comes into it. */
    @Test
    public void exemptItemsAreNeverTaxed() {
        assertTrue(GeTax.isExempt(OLD_SCHOOL_BOND));
        assertEquals(0L, GeTax.perItem(OLD_SCHOOL_BOND, 11_700_000L));
        assertEquals(0L, GeTax.forSale(OLD_SCHOOL_BOND, 11_700_000L, 5L));
        assertEquals(0L, GeTax.forGrossTotal(OLD_SCHOOL_BOND, 58_500_000L, 5L));

        assertTrue(GeTax.isExempt(LOBSTER));
        assertEquals(0L, GeTax.forSale(LOBSTER, 200L, 10_000L));
    }

    /**
     * The exemptions sit next to near-identical items that are taxed normally, and picking up a
     * neighbour by mistake would quietly under-report tax on a commonly flipped item.
     */
    @Test
    public void lookalikesOfExemptItemsAreStillTaxed() {
        assertFalse(GeTax.isExempt(RAW_LOBSTER));
        assertFalse(GeTax.isExempt(POISONED_BRONZE_ARROW));
        assertFalse(GeTax.isExempt(CHOCOLATE_CAKE));
        assertFalse(GeTax.isExempt(ARCEUUS_LIBRARY_TELEPORT));
        assertFalse(GeTax.isExempt(GILDED_SPADE));

        assertEquals(4L, GeTax.perItem(RAW_LOBSTER, 200L));
    }

    @Test
    public void ordinaryItemsAreNotExempt() {
        assertFalse(GeTax.isExempt(NATURE_RUNE));
        assertFalse(GeTax.isExempt(0));
        assertFalse(GeTax.isExempt(-1));
    }
}
