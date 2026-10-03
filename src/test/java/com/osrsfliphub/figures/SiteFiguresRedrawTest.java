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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import static com.osrsfliphub.SiteFiguresWorld.MAIN;
import static com.osrsfliphub.SiteFiguresWorld.MINUTE;
import static com.osrsfliphub.SiteFiguresWorld.WHIP;
import static com.osrsfliphub.SiteFiguresWorld.flip;
import static org.junit.Assert.assertEquals;

/**
 * A refresh of the Profile tab that is asked for while another is running.
 *
 * <p>The tab draws what the plugin has and is drawn again when the website's answer lands. The
 * answer lands on the IO pool, whenever it lands, and that can be while the scheduler is half
 * way through a refresh. A refresh that arrives then used to be thrown away: the tab would stay
 * on what it showed before the answer until something unrelated drew it again.
 */
public class SiteFiguresRedrawTest {
    @Rule
    public final Timeout timeout = Timeout.seconds(120);

    private SiteFiguresWorld w;

    @Before
    public void setUp() throws Exception {
        w = SiteFiguresWorld.fresh();
        w.own(MAIN, flip(0, WHIP, 10, 10_000L, 10_999L, 180 * MINUTE, 40 * MINUTE));
        w.login(MAIN);
    }

    @After
    public void tearDown() throws Exception {
        SiteFiguresWorld.close(w);
    }

    /** Asked for while one is half way: it runs once that one has ended. */
    @Test
    public void aRefreshAskedForWhileOneIsRunningIsRunOnceThatOneEnds() throws Exception {
        // Nothing of the website's here: the tab draws this computer's own sums.
        w.world.linked = false;
        w.openProfileTab();
        w.settle();
        int drawn = w.tab.draws.get();

        CountDownLatch letGo = w.tab.holdTheNextDraw();
        Future<?> running = w.scheduler.submit(w.world.plugin::refreshStatsData);
        w.tab.awaitHeld();
        // From another thread, as an answer landing on the IO pool asks.
        w.world.plugin.refreshStatsData();
        letGo.countDown();
        running.get(20, TimeUnit.SECONDS);
        w.quiesce();

        assertEquals("the one that was running, and the one asked for meanwhile", drawn + 2, w.tab.draws.get());
    }

    /**
     * The whole of it: the website's answer lands while the scheduler is drawing the tab with
     * what it had before. Nothing else asks for a refresh, and the tab ends on the answer.
     */
    @Test
    public void anAnswerThatLandsWhileTheTabIsBeingDrawnIsStillShown() throws Exception {
        w.switchOn();
        w.openProfileTab();
        assertEquals("asked, not answered: this computer's own meanwhile", "999 gp", w.total());

        CountDownLatch letGo = w.tab.holdTheNextDraw();
        Future<?> running = w.scheduler.submit(w.world.plugin::refreshStatsData);
        w.tab.awaitHeld();
        w.answer();
        letGo.countDown();
        running.get(20, TimeUnit.SECONDS);
        w.quiesce();

        assertEquals("402,078 gp", w.total());
        assertEquals("6", w.flipCount());
    }
}
