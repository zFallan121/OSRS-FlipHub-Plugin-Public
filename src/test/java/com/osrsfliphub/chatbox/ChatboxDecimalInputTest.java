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
import static org.junit.Assert.assertNull;

public class ChatboxDecimalInputTest {
    @Test
    public void toPlainAmountScalesEachUnit() {
        assertEquals("1210000000", ChatboxDecimalInput.toPlainAmount("1.21b"));
        assertEquals("9400000", ChatboxDecimalInput.toPlainAmount("9.4m"));
        assertEquals("1200", ChatboxDecimalInput.toPlainAmount("1.2k"));
    }

    @Test
    public void toPlainAmountDropsAnythingPastAWholeCoin() {
        assertEquals("2532", ChatboxDecimalInput.toPlainAmount("2.5325k"));
        assertEquals("32333333", ChatboxDecimalInput.toPlainAmount("32.333333m"));
        assertEquals("0", ChatboxDecimalInput.toPlainAmount("0.000001k"));
    }

    @Test
    public void toPlainAmountKeepsPrecisionBinaryFloatingPointWouldLose() {
        assertEquals("32200000", ChatboxDecimalInput.toPlainAmount("32.2m"));
        assertEquals("32700000", ChatboxDecimalInput.toPlainAmount("32.7m"));
        assertEquals("1234567890", ChatboxDecimalInput.toPlainAmount("1.234567890b"));
    }

    @Test
    public void toPlainAmountAcceptsAnUppercaseUnit() {
        assertEquals("9400000", ChatboxDecimalInput.toPlainAmount("9.4M"));
        assertEquals("1210000000", ChatboxDecimalInput.toPlainAmount("1.21B"));
        assertEquals("1200", ChatboxDecimalInput.toPlainAmount("1.2K"));
    }

    @Test
    public void toPlainAmountClampsToTheLargestAmountTheGameTakes() {
        assertEquals("2147483647", ChatboxDecimalInput.toPlainAmount("2.147483647b"));
        assertEquals("2147483647", ChatboxDecimalInput.toPlainAmount("2.147483648b"));
        assertEquals("2147483647", ChatboxDecimalInput.toPlainAmount("99.9b"));
    }

    @Test
    public void toPlainAmountTruncatesADecimalCarryingNoUnit() {
        // Only this plugin can put the point there, so it always has to clean it up again.
        assertEquals("1", ChatboxDecimalInput.toPlainAmount("1.5"));
        assertEquals("0", ChatboxDecimalInput.toPlainAmount("0.5"));
        assertEquals("7", ChatboxDecimalInput.toPlainAmount("7."));
    }

    @Test
    public void toPlainAmountFillsInAnOmittedWholePart() {
        assertEquals("500000", ChatboxDecimalInput.toPlainAmount(".5m"));
    }

    @Test
    public void toPlainAmountLeavesTextTheGameAlreadyReads() {
        assertNull(ChatboxDecimalInput.toPlainAmount("9m"));
        assertNull(ChatboxDecimalInput.toPlainAmount("500"));
        assertNull(ChatboxDecimalInput.toPlainAmount(""));
        assertNull(ChatboxDecimalInput.toPlainAmount(null));
    }

    @Test
    public void toPlainAmountLeavesTextItCannotRead() {
        assertNull(ChatboxDecimalInput.toPlainAmount("."));
        assertNull(ChatboxDecimalInput.toPlainAmount(".m"));
        assertNull(ChatboxDecimalInput.toPlainAmount("1.2.3"));
        assertNull(ChatboxDecimalInput.toPlainAmount("1.2x"));
        assertNull(ChatboxDecimalInput.toPlainAmount("1.2km"));
    }

    @Test
    public void withDecimalPointTypesThePointTheGameDropped() {
        assertEquals("9.", ChatboxDecimalInput.withDecimalPoint("9"));
        assertEquals("32.", ChatboxDecimalInput.withDecimalPoint("32"));
    }

    @Test
    public void withDecimalPointLeadsWithAZeroOnAnEmptyPrompt() {
        assertEquals("0.", ChatboxDecimalInput.withDecimalPoint(""));
        assertEquals("0.", ChatboxDecimalInput.withDecimalPoint(null));
    }

    @Test
    public void withDecimalPointRefusesASecondPoint() {
        assertNull(ChatboxDecimalInput.withDecimalPoint("1."));
        assertNull(ChatboxDecimalInput.withDecimalPoint("1.2"));
    }

    @Test
    public void withDecimalPointRefusesAnAlreadyScaledAmount() {
        assertNull(ChatboxDecimalInput.withDecimalPoint("12k"));
        assertNull(ChatboxDecimalInput.withDecimalPoint("9m"));
    }
}
