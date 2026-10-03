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
import java.util.function.BiFunction;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AffordableLimitSuggestionServiceTest {
    private final AffordableLimitSuggestion service = new AffordableLimitSuggestion(null, null);

    @Test
    public void computeUsesEnteredPriceBeforeSelectedOfferPrice() {
        assertEquals(Integer.valueOf(100), service.computeAffordableLimit(100, 200, 10_000L));
    }

    @Test
    public void computeFallsBackToSelectedOfferPrice() {
        assertEquals(Integer.valueOf(11), service.computeAffordableLimit(0, 250, 2_750L));
    }

    @Test
    public void computeIgnoresGeLimitAndUsesCashOnly() {
        // 10k coins at 100 gp each affords 100, even when the GE limit would be lower.
        assertEquals(Integer.valueOf(100), service.computeAffordableLimit(100, 0, 10_000L));
    }

    @Test
    public void computeReturnsNullWithoutUsablePriceOrCoins() {
        assertNull(service.computeAffordableLimit(0, 0, 0L));
        assertNull(service.computeAffordableLimit(0, 0, 10_000L));
        assertNull(service.computeAffordableLimit(100, 0, 0L));
    }

    @Test
    public void computeCapsAtIntegerMaxValue() {
        assertEquals(Integer.valueOf(Integer.MAX_VALUE),
            service.computeAffordableLimit(1, 0, (long) Integer.MAX_VALUE + 1000L));
    }

    @Test
    public void aPricePastMaxCashIsNotCutDownToIt() {
        // A full stack of coins buys none of an item at 3 billion each. Held in an int, that
        // price became 2,147,483,647 and the answer came out as 1.
        assertNull(service.computeAffordableLimit(3_000_000_000L, 0, Integer.MAX_VALUE));
        assertEquals(Integer.valueOf(1), service.computeAffordableLimit(0, 2_000_000_000L, Integer.MAX_VALUE));
    }

    /**
     * The game as it has been since 30 Sep 2026: the typed price is a 64-bit player variable, and
     * asking for the varbit that used to hold it throws. That throw took the whole chat line out.
     */
    @Test
    public void readsTheTypedPriceFromTheLongVariable() {
        AffordableLimitSuggestion live = new AffordableLimitSuggestion(
            client(5_702L, 57_020), new OfferPreviewRuntime());
        assertEquals(Integer.valueOf(10), live.computeAffordableLimit());
    }

    @Test
    public void aPriceTheGameWillNotGiveLeavesNoCashLimitRatherThanFailing() {
        AffordableLimitSuggestion live = new AffordableLimitSuggestion(
            client(null, 57_020), new OfferPreviewRuntime());
        assertNull(live.computeAffordableLimit());
    }

    /**
     * A 3rd age pickaxe at 2,394,000,000 costs more than a stack of coins can hold. The game takes
     * the payment in coins and platinum tokens together, a token being 1,000 coins: 1,000,000,000
     * coins and 4,000,000 tokens are 5,000,000,000, which buys two. Counting coins alone, the line
     * said nothing at all for such an item.
     */
    @Test
    public void platinumTokensCountTowardsTheCashLimitAtAThousandCoinsEach() {
        AffordableLimitSuggestion live = new AffordableLimitSuggestion(
            client(2_394_000_000L, new Item(995, 1_000_000_000), new Item(13204, 4_000_000)),
            new OfferPreviewRuntime());

        assertEquals(Integer.valueOf(2), live.computeAffordableLimit());
    }

    /** Tokens on their own pay too, and two full stacks together do not overflow. */
    @Test
    public void platinumTokensAloneAreCashAndFullStacksAddUp() {
        assertEquals(Integer.valueOf(30), new AffordableLimitSuggestion(
            client(100L, new Item(13204, 3)), new OfferPreviewRuntime()).computeAffordableLimit());
        // 2,147,483,647 coins and 2,147,483,647 tokens: 2,149,631,130,647 in all, the most an
        // offer can come to. At 2,394,000,000 each that is 897.
        assertEquals(Integer.valueOf(897), new AffordableLimitSuggestion(
            client(2_394_000_000L, new Item(995, Integer.MAX_VALUE), new Item(13204, Integer.MAX_VALUE)),
            new OfferPreviewRuntime()).computeAffordableLimit());
    }

    /** Nothing else in the inventory is money, whatever it is worth, and an empty space is nothing. */
    @Test
    public void onlyCoinsAndPlatinumTokensAreCash() {
        AffordableLimitSuggestion live = new AffordableLimitSuggestion(
            client(100L, new Item(4151, 5_000), null, new Item(13205, 9_000), new Item(995, 250)),
            new OfferPreviewRuntime());

        assertEquals(Integer.valueOf(2), live.computeAffordableLimit());
    }

    /** A fake game client: {@code typedPrice} null means the price variable is gone too. */
    private static Client client(Long typedPrice, int coins) {
        return client(typedPrice, new Item(995, coins));
    }

    private static Client client(Long typedPrice, Item... items) {
        ItemContainer inventory = fake(ItemContainer.class, (method, args) ->
            method.equals("getItems") ? items : null);
        return fake(Client.class, (method, args) -> {
            switch (method) {
                case "getVarbitValue":
                    if ((Integer) args[0] == 4398) {
                        throw new IndexOutOfBoundsException("Varbit 4398 does not exist");
                    }
                    return 0;
                case "getVarpLongValue":
                    if (typedPrice == null || (Integer) args[0] != 5753) {
                        throw new IndexOutOfBoundsException("Varp " + args[0] + " does not exist");
                    }
                    return typedPrice;
                case "getItemContainer":
                    return inventory;
                default:
                    return null;
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, BiFunction<String, Object[], Object> answers) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (proxy, method, args) -> answers.apply(method.getName(), args));
    }
}
