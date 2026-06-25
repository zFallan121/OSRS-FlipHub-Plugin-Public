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

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OfferUpdateStampLegacyMatcherTest {
    @Test
    public void returnsTrueWhenSingleComparableSlotMatches() {
        OfferUpdateStampLegacyMatcher matcher = new OfferUpdateStampLegacyMatcher();
        Map<Integer, OfferUpdateStamp> stamps = new HashMap<>();
        stamps.put(0, stamp(4151, false));
        GrandExchangeOffer[] offers = new GrandExchangeOffer[] {offer(4151, GrandExchangeOfferState.SELLING)};

        assertTrue(matcher.matchesCurrentOffers(stamps, offers));
    }

    @Test
    public void requiresTwoMatchesWhenTwoComparableSlotsExist() {
        OfferUpdateStampLegacyMatcher matcher = new OfferUpdateStampLegacyMatcher();
        Map<Integer, OfferUpdateStamp> stamps = new HashMap<>();
        stamps.put(0, stamp(4151, false));
        stamps.put(1, stamp(560, true));

        GrandExchangeOffer[] oneMismatch = new GrandExchangeOffer[] {
            offer(4151, GrandExchangeOfferState.SELLING),
            offer(565, GrandExchangeOfferState.BUYING)
        };
        assertFalse(matcher.matchesCurrentOffers(stamps, oneMismatch));

        GrandExchangeOffer[] allMatch = new GrandExchangeOffer[] {
            offer(4151, GrandExchangeOfferState.SELLING),
            offer(560, GrandExchangeOfferState.BUYING)
        };
        assertTrue(matcher.matchesCurrentOffers(stamps, allMatch));
    }

    @Test
    public void ignoresEmptyOrInvalidOfferSlots() {
        OfferUpdateStampLegacyMatcher matcher = new OfferUpdateStampLegacyMatcher();
        Map<Integer, OfferUpdateStamp> stamps = new HashMap<>();
        stamps.put(0, stamp(4151, false));
        stamps.put(1, stamp(560, true));
        stamps.put(10, stamp(995, true)); // out of bounds and ignored

        GrandExchangeOffer[] offers = new GrandExchangeOffer[] {
            offer(4151, GrandExchangeOfferState.SELLING),
            offer(560, GrandExchangeOfferState.EMPTY)
        };

        // Only one comparable slot remains and it matches.
        assertTrue(matcher.matchesCurrentOffers(stamps, offers));
    }

    private static OfferUpdateStamp stamp(int itemId, boolean isBuy) {
        return new OfferUpdateStamp(itemId, 100, 100, 0, isBuy, 0L, 1L, 1L, 0L, 0L);
    }

    private static GrandExchangeOffer offer(int itemId, GrandExchangeOfferState state) {
        return (GrandExchangeOffer) Proxy.newProxyInstance(
            GrandExchangeOffer.class.getClassLoader(),
            new Class<?>[] {GrandExchangeOffer.class},
            (proxy, method, args) -> handleOfferMethod(method, itemId, state)
        );
    }

    private static Object handleOfferMethod(Method method, int itemId, GrandExchangeOfferState state) {
        switch (method.getName()) {
            case "getItemId":
                return itemId;
            case "getState":
                return state;
            default:
                return defaultValue(method);
        }
    }

    private static Object defaultValue(Method method) {
        Class<?> returnType = method.getReturnType();
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return 0;
    }
}
