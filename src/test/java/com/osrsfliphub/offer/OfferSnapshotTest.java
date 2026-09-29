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

import java.lang.reflect.Proxy;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

// RuneLite 1.13 reports an offer's price and spend as longs, for the GE going past max cash.
public class OfferSnapshotTest {
    @Test
    public void anOfferWorthMoreThanMaxCashKeepsItsWholeTotal() {
        OfferSnapshot snap = OfferSnapshot.fromOffer(0,
            offer(1_500_000_000L, 2, 2, 3_000_000_000L), null);

        assertEquals(1_500_000_000, snap.price);
        assertEquals(3_000_000_000L, snap.spentGp);
        assertEquals(2, snap.filledQty);
    }

    @Test
    public void aPriceBeyondAnIntIsHeldAtMaxCashRatherThanWrappingNegative() {
        OfferSnapshot snap = OfferSnapshot.fromOffer(0,
            offer(3_000_000_000L, 1, 0, 0L), null);

        assertEquals(Integer.MAX_VALUE, snap.price);
    }

    private static GrandExchangeOffer offer(long price, int totalQty, int filledQty, long spent) {
        return (GrandExchangeOffer) Proxy.newProxyInstance(
            GrandExchangeOffer.class.getClassLoader(),
            new Class<?>[] {GrandExchangeOffer.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getItemId":
                        return 20997;
                    case "getPrice":
                        return price;
                    case "getTotalQuantity":
                        return totalQty;
                    case "getQuantitySold":
                        return filledQty;
                    case "getSpent":
                        return spent;
                    case "getState":
                        return GrandExchangeOfferState.BUYING;
                    default:
                        return null;
                }
            }
        );
    }
}
