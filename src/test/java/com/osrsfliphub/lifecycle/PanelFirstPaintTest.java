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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Getting something onto the Activity panel as soon as the player has it open.
 *
 * <p>The refresh used to be asked for exactly once, on the tick the panel appeared. That one
 * attempt is dropped if the client is not ready yet, and at login it usually is not: the panel
 * is up a moment before the world is. Nothing tried again, so the panel stayed empty until
 * something unrelated rebuilt it, which in practice was twenty to fifty seconds later.
 */
public class PanelFirstPaintTest {
    /** The tick the panel appears on. Nothing has reached it yet, so ask. */
    @Test
    public void aPanelThatHasJustAppearedIsAskedFor() {
        assertTrue(TickServices.shouldAskForRefresh(false, false));
    }

    /**
     * Still on screen, still empty. This is the case that was missing: without it the panel
     * waited for an unrelated event, and on a quiet client there may not be one for a minute.
     */
    @Test
    public void aPanelStillWaitingForItsFirstRefreshIsAskedAgain() {
        assertTrue(TickServices.shouldAskForRefresh(true, false));
    }

    /** Once a refresh has landed, stop. Asking every tick would rebuild it forever. */
    @Test
    public void aPanelThatHasBeenFilledInIsLeftAlone() {
        assertFalse(TickServices.shouldAskForRefresh(true, true));
    }

    /**
     * Appearing and already filled in, which is what a quick hide and show looks like when
     * nothing reset the mark. Asking once more is harmless and keeps the first case simple.
     */
    @Test
    public void aPanelReappearingIsAskedOnceMore() {
        assertTrue(TickServices.shouldAskForRefresh(false, true));
    }

    /**
     * The Profile tab is skipped entirely while it is not the tab on screen, which is right.
     * But selecting it is not an event anything listens for, so it also has to be asked for on
     * the tick. Without this it arrived empty and stayed empty until something unrelated
     * happened to fire while it was showing, which is why opening the Grand Exchange and
     * switching tabs twice was what made it appear.
     */
    @Test
    public void theProfileTabIsAskedForWhileItIsShowingAndEmpty() {
        assertTrue(PanelRefresh.statsNeedRefresh(true, false));
    }

    @Test
    public void theProfileTabIsLeftAloneOnceItHasBeenFilledIn() {
        assertFalse(PanelRefresh.statsNeedRefresh(true, true));
    }

    /** And it is never computed while the player is looking at a different tab. */
    @Test
    public void theProfileTabIsNotAskedForWhileAnotherTabIsShowing() {
        assertFalse(PanelRefresh.statsNeedRefresh(false, false));
        assertFalse(PanelRefresh.statsNeedRefresh(false, true));
    }
}
