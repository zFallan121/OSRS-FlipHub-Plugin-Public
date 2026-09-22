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

import java.lang.reflect.Field;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * The offer timers' once-a-second tick, once the panel has gone.
 *
 * <p>A redraw queued just before the plugin was turned off still ran afterwards, found entries,
 * and started the tick again: a timer firing every second for ever, holding on to a panel nobody
 * could see. Found by the final audit, 22 Sep 2026.
 */
public class AgeTooltipShutDownTest {
    @Test
    public void aRedrawQueuedBeforeShutDownDoesNotStartTheTickAgain() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AgeTooltip tooltip = new AgeTooltip(new PanelValueFormat());
            tooltip.registerCountdownLabel(new JLabel(), 60_000L, System.currentTimeMillis());

            tooltip.shutDown();
            tooltip.ensureCountdownTimer();

            assertTrue("the tick stays stopped", !running(tooltip));
        });
    }

    @Test
    public void beforeShutDownTheTickRuns() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AgeTooltip tooltip = new AgeTooltip(new PanelValueFormat());
            tooltip.registerCountdownLabel(new JLabel(), 60_000L, System.currentTimeMillis());

            tooltip.ensureCountdownTimer();

            assertTrue(running(tooltip));
            tooltip.shutDown();
        });
    }

    private static boolean running(AgeTooltip tooltip) {
        try {
            Field field = AgeTooltip.class.getDeclaredField("countdownTimer");
            field.setAccessible(true);
            Timer timer = (Timer) field.get(tooltip);
            return timer != null && timer.isRunning();
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
