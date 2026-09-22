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

import java.util.Locale;

/**
 * The five ways a Grand Exchange trader turns items into other items.
 *
 * <p>Three shapes cover all five: N to 1 (assemble, set combine), 1 to N
 * (disassemble, set break), and 1 to 1 plus a fee (repair). The shape is what
 * the ledger needs; the kind is what the panel says, which is why both exist.
 *
 * <p>{@link #TRANSFER} is not one of the five and makes nothing: it is stock handed to another
 * of the player's own accounts to be sold there. It is a kind because it is recorded on the same
 * screen, kept in the same file and sent the same way as the others. It never becomes an
 * activity, so no card, icon or filter ever meets it.
 */
enum ConversionKind {
    ASSEMBLE("Assembled"),
    DISASSEMBLE("Disassembled"),
    REPAIR("Repaired"),
    SET_COMBINE("Combined a set"),
    SET_BREAK("Broke up a set"),
    TRANSFER("Moved to an alt");

    private final String label;

    ConversionKind(String label) {
        this.label = label;
    }

    /**
     * What the player is told, in the past tense, because by the time it is written down the
     * conversion has happened. The stored form is {@link #name()}, which this does not touch.
     */
    @Override
    public String toString() {
        return label;
    }

    static ConversionKind parse(String raw) {
        if (raw == null) {
            return null;
        }
        String key = raw.trim().toUpperCase(Locale.US);
        for (ConversionKind kind : values()) {
            if (kind.name().equals(key)) {
                return kind;
            }
        }
        return null;
    }
}
