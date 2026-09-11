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

import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JPanel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Clicking a card, the way a hand actually does it.
 *
 * <p>The handler used to listen for the toolkit's own click event, which is only sent when the
 * pointer has not moved between the press and the release. Two pixels of drift, which is
 * ordinary on a trackpad, cancelled it and nothing happened at all. That reads as the card
 * having dead patches, and the patch appears to move, because what really varies is how steady
 * the hand was rather than where it was.
 */
public class StatsCardClickTest {
    private final AtomicInteger fired = new AtomicInteger();
    private final JPanel card = card();
    private final FlipHubStatsClickMouseAdapter adapter =
        new FlipHubStatsClickMouseAdapter(fired::incrementAndGet);

    @Test
    public void aStillClickFires() {
        press(40, 20);
        release(40, 20);

        assertEquals(1, fired.get());
    }

    /** The case that was being lost. The hand moves a little; the intent is the same. */
    @Test
    public void aClickThatDriftsAFewPixelsStillFires() {
        press(40, 20);
        release(43, 22);

        assertEquals(1, fired.get());
    }

    /** Right and middle buttons belong to the menu, not to us. */
    @Test
    public void onlyTheLeftButtonFires() {
        adapter.mousePressed(event(40, 20, MouseEvent.BUTTON3));
        adapter.mouseReleased(event(40, 20, MouseEvent.BUTTON3));

        assertEquals(0, fired.get());
    }

    /** Press, slide off, let go. That is how a person cancels, and it must be honoured. */
    @Test
    public void slidingOffBeforeLettingGoDoesNotFire() {
        press(40, 20);
        release(400, 20);

        assertEquals(0, fired.get());
    }

    /** A release with no press of ours before it is somebody else's event. */
    @Test
    public void aReleaseWithoutAPressDoesNothing() {
        release(40, 20);

        assertEquals(0, fired.get());
    }

    /** And one press cannot be spent twice. */
    @Test
    public void onePressFiresAtMostOnce() {
        press(40, 20);
        release(40, 20);
        release(40, 20);

        assertEquals(1, fired.get());
    }

    private void press(int x, int y) {
        adapter.mousePressed(event(x, y, MouseEvent.BUTTON1));
    }

    private void release(int x, int y) {
        adapter.mouseReleased(event(x, y, MouseEvent.BUTTON1));
    }

    private MouseEvent event(int x, int y, int button) {
        int modifiers = button == MouseEvent.BUTTON1
            ? MouseEvent.BUTTON1_DOWN_MASK : MouseEvent.BUTTON3_DOWN_MASK;
        return new MouseEvent(card, MouseEvent.MOUSE_PRESSED, 0L, modifiers, x, y, 1, false, button);
    }

    private static JPanel card() {
        JPanel panel = new JPanel();
        panel.setSize(new Dimension(200, 56));
        return panel;
    }
}
