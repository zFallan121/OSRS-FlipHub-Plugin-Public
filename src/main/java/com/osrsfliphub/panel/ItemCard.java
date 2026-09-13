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

import javax.swing.*;
import lombok.RequiredArgsConstructor;

/**
 * One built item row, and the parts of it a refresh is allowed to change.
 *
 * <p>A row is a fixed set of labels whose text moves; the panel, the icon, the listeners and the
 * hover state are not data and have no business being rebuilt when a price changes. Holding the
 * labels here is what lets a refresh write the numbers into the row the pointer is already on,
 * instead of replacing that row with an identical one.
 */
@RequiredArgsConstructor
final class ItemCard {
    final JPanel panel;
    final int itemId;
    /** The offer preview draws its values tighter, so a card built for one cannot serve the list. */
    final boolean compactRightPadding;
    final JLabel nameLabel;
    final JLabel sellValue;
    final JLabel buyValue;
    final JLabel lastSellValue;
    final JLabel lastBuyValue;
    final JLabel marginValue;
    final JLabel marginLimitValue;
    final JLabel roiValue;
    final JLabel limitRemainingValue;
    final JLabel countdownValue;
    /** Repaints the bookmark star from the store, in whichever state the pointer has it. */
    final Runnable repaintBookmark;
    AgePairEntry agePair;
    CountdownEntry countdown;
}
