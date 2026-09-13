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

/**
 * How much the plugin can actually prove about a conversion it is considering.
 *
 * <p>Both ledgers replay trades in order, so an input already in the buckets
 * when a sale is processed genuinely preceded it - that is {@link #ORDERED},
 * and it is the only evidence a live-observed trade ever needs.
 *
 * <p>Trades recovered from the Grand Exchange history widget have no timestamp
 * of their own; the sync invents one at replay. A player who buys and combines
 * on a phone and sells on the desktop therefore produces ingredient buys
 * stamped after the sale they paid for. {@link #SYNCED} is the retry that lets
 * those satisfy an earlier sale - and it is restricted to history-synced stock,
 * because a live buy that really did happen after a sale is evidence, and must
 * keep being believed.
 *
 * <p>So is the history's own order. One read of the history is imported as
 * one batch in the order the game lists it, and when the sale asking is itself
 * part of that batch, a part the batch lists after it was bought after it -
 * that is not an invented timestamp, it is what the history said. The retry
 * therefore refuses stock the sale's own batch places after the sale, and
 * keeps its original reasoning only across separate imports, where nothing
 * relates the two. {@link ConversionSyncedBatches} tells the batches apart.
 */
enum ConversionEvidence {
    ORDERED,
    SYNCED
}
