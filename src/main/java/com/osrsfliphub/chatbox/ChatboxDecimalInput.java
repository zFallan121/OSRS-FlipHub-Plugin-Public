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

import java.math.*;
import java.util.regex.*;

/**
 * Text rules for typing a decimal amount into a chatbox quantity prompt.
 *
 * <p>The game already understands a unit suffix - 9m in a price box is nine million - but it
 * refuses the decimal point that would let you write 9.4m. These helpers cover the two moments
 * that changes: the keystroke that would have been dropped, and the Enter that has to hand the
 * game a plain number again.
 *
 * <p>Kept free of client calls so the arithmetic can be tested on its own.
 */
final class ChatboxDecimalInput {
    // A quantity is one number, optionally scaled, and must carry the decimal point that the game
    // would not have accepted on its own. Anything else is left for the game to parse as it always has.
    private static final Pattern DECIMAL_AMOUNT =
        Pattern.compile("(\\d*)\\.(\\d*)([kmbt]?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1_000L);
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000L);
    private static final BigDecimal BILLION = BigDecimal.valueOf(1_000_000_000L);
    private static final BigDecimal TRILLION = BigDecimal.valueOf(1_000_000_000_000L);
    // A quantity, and every amount but a Grand Exchange price, stops at the largest 32-bit value.
    private static final BigDecimal MAX_AMOUNT = BigDecimal.valueOf(Integer.MAX_VALUE);
    private static final BigDecimal MAX_LONG_AMOUNT = BigDecimal.valueOf(Long.MAX_VALUE);

    // The chatbox input types that ask for an amount. 7 is the one the game has always used:
    // quantity, bank withdraw-X, trade, coffers. 30 is the Grand Exchange price since 30 Sep 2026,
    // when one item could first cost more than 2,147,483,647: 19 digits, and T for trillion.
    private static final int AMOUNT_PROMPT = 7;
    private static final int LONG_AMOUNT_PROMPT = 30;

    private ChatboxDecimalInput() {
    }

    /** Whether the prompt of this input type can hold {@code amount}: past max cash, only a price prompt. */
    static boolean fits(int inputType, long amount) {
        return amount <= Integer.MAX_VALUE || inputType == LONG_AMOUNT_PROMPT;
    }

    /** The most the prompt of this input type takes, or null when it does not ask for an amount. */
    static BigDecimal largestAmount(int inputType) {
        if (inputType == LONG_AMOUNT_PROMPT) {
            return MAX_LONG_AMOUNT;
        }
        return inputType == AMOUNT_PROMPT ? MAX_AMOUNT : null;
    }

    /**
     * The prompt text after typing a decimal point into {@code inputText}, or null when the point
     * does not belong there and the keystroke should stay ignored.
     */
    static String withDecimalPoint(String inputText) {
        String current = inputText == null ? "" : inputText;
        if (current.indexOf('.') >= 0) {
            // An amount carries at most one decimal point.
            return null;
        }
        if (current.isEmpty()) {
            // Leading zero, so ".5m" reads back as a number rather than a stray point.
            return "0.";
        }
        if (!DIGITS.matcher(current).matches()) {
            // Already scaled ("12k") or otherwise not a bare number - a point after it means nothing.
            return null;
        }
        return current + ".";
    }

    /**
     * {@code inputText} rewritten as the plain integer the game expects, or null when it is
     * already something the game can read for itself.
     *
     * <p>A fraction of a coin cannot be offered, so 2.5325k becomes 2532 rather than rounding up.
     */
    static String toPlainAmount(String inputText) {
        return toPlainAmount(inputText, MAX_AMOUNT);
    }

    /** As above, for a prompt that takes at most {@code largest}. */
    static String toPlainAmount(String inputText, BigDecimal largest) {
        if (inputText == null || inputText.indexOf('.') < 0) {
            // Without a decimal point this is either plain digits or a suffix the game already handles.
            return null;
        }
        Matcher matcher = DECIMAL_AMOUNT.matcher(inputText.trim());
        if (!matcher.matches()) {
            return null;
        }
        String whole = matcher.group(1);
        String fraction = matcher.group(2);
        if (whole.isEmpty() && fraction.isEmpty()) {
            // Just "." or ".m" - there is no amount to convert.
            return null;
        }
        BigDecimal amount = new BigDecimal(
            (whole.isEmpty() ? "0" : whole) + "." + (fraction.isEmpty() ? "0" : fraction));
        BigDecimal scaled = amount.multiply(multiplierFor(matcher.group(3)));
        if (scaled.compareTo(largest) > 0) {
            scaled = largest;
        }
        return scaled.setScale(0, RoundingMode.DOWN).toPlainString();
    }

    private static BigDecimal multiplierFor(String unit) {
        if (unit == null || unit.isEmpty()) {
            return BigDecimal.ONE;
        }
        switch (Character.toLowerCase(unit.charAt(0))) {
            case 'k':
                return THOUSAND;
            case 'm':
                return MILLION;
            case 'b':
                return BILLION;
            case 't':
                return TRILLION;
            default:
                return BigDecimal.ONE;
        }
    }
}
