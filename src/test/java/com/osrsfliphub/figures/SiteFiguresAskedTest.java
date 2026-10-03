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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import okhttp3.Request;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import static com.osrsfliphub.SiteFiguresWorld.MAIN;
import static com.osrsfliphub.SiteFiguresWorld.MINUTE;
import static com.osrsfliphub.SiteFiguresWorld.SECOND;
import static com.osrsfliphub.SiteFiguresWorld.WHIP;
import static com.osrsfliphub.SiteFiguresWorld.flip;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * What the plugin asks the website about its figures, and how often.
 *
 * <p>Every computer of an account shares 180 questions in ten minutes, so the plugin asks for the
 * view on show at most once a minute, again when an upload has been accepted (the figures have
 * just moved), and not at all while the website has told it to wait.
 */
public class SiteFiguresAskedTest {
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

    // ---- the question ----

    /** {@code GET /api/plugin/figures?scope=account}: the session token in a header, no body, nothing signed. */
    @Test
    public void theWholeAccountOnAllTimeIsAskedForWithOneGet() throws Exception {
        w.switchOn();

        w.openProfileTab();
        w.settle();

        assertEquals(1, w.figuresAsked().size());
        Request asked = w.figuresAsked().get(0);
        assertEquals("GET", asked.method());
        assertTrue(asked.url().toString(), asked.url().toString().startsWith(Skin.DEFAULT_BASE_URL + "/api/plugin/figures?"));
        assertEquals("/api/plugin/figures", asked.url().encodedPath());
        assertEquals("all time is no since_ms at all", new HashSet<>(Arrays.asList("scope")), asked.url().queryParameterNames());
        assertEquals("account", asked.url().queryParameter("scope"));
        assertEquals("session-token", asked.header("X-Plugin-Token"));
        assertNull("a GET has no body", asked.body());
        assertNull("and so nothing to sign", asked.header("X-Signature"));
    }

    /** A range is asked for by where it starts: the last seven days, by the moment a week ago. */
    @Test
    public void aRangeIsAskedForByItsStart() throws Exception {
        w.switchOn();
        w.openProfileTab();
        w.settle();
        long week = 7 * 24 * 60 * MINUTE;
        long earliest = Math.min(System.currentTimeMillis(), w.now()) - week - MINUTE;

        w.range(StatsRange.LAST_7D);
        w.settle();

        Request asked = last(w.figuresAsked());
        assertEquals(new HashSet<>(Arrays.asList("scope", "since_ms")), asked.url().queryParameterNames());
        assertEquals("account", asked.url().queryParameter("scope"));
        long since = Long.parseLong(asked.url().queryParameter("since_ms"));
        long latest = Math.max(System.currentTimeMillis(), w.now()) - week + MINUTE;
        assertTrue("a week ago, to the minute: " + since, since >= earliest && since <= latest);
        assertEquals("the range's own figures", "401,888 gp", w.total());
    }

    /** One character is asked for by the tag that character's uploads carry, never by its name or its key. */
    @Test
    public void oneCharacterIsAskedForByTheTagItsUploadsCarry() throws Exception {
        w.switchOn();
        w.openProfileTab();
        w.settle();

        w.character(MAIN);
        w.settle();

        Request asked = last(w.figuresAsked());
        assertEquals(GeEvent.characterId(MAIN), asked.url().queryParameter("scope"));
        assertEquals(new HashSet<>(Arrays.asList("scope")), asked.url().queryParameterNames());
        assertEquals("401,653 gp", w.total());
    }

    // ---- how often ----

    /** The tab is refreshed many times a minute. The website is asked for the view on show once in it. */
    @Test
    public void theSameViewIsAskedForOnceAMinuteAtMost() throws Exception {
        w.switchOn();
        w.openProfileTab();
        w.settle();

        for (int i = 0; i < 3; i++) {
            w.pass(19 * SECOND);
            w.show();
        }
        assertEquals("57 seconds on: the answer it has is the one it shows", 1, w.figuresAsked().size());

        w.pass(4 * SECOND);
        w.show();
        assertEquals("61 seconds on: asked again", 2, w.figuresAsked().size());
        assertEquals("402,078 gp", w.total());
    }

    /** Today, with the switch off, the same: one small {@code {"live": false}} a minute while the tab is open. */
    @Test
    public void beforeTheSwitchTheWebsiteIsAskedOnceAMinuteAtMost() throws Exception {
        w.switchOff();
        w.openProfileTab();
        w.settle();

        for (int i = 0; i < 3; i++) {
            w.pass(19 * SECOND);
            w.show();
        }
        assertEquals(1, w.figuresAsked().size());

        w.pass(4 * SECOND);
        w.show();
        assertEquals(2, w.figuresAsked().size());
    }

    /** With another tab on show there is no view to ask for, however long it stays that way. */
    @Test
    public void withTheProfileTabShutNothingIsAsked() throws Exception {
        w.switchOn();
        w.openProfileTab();
        w.settle();
        assertEquals(1, w.figuresAsked().size());

        w.openActivityTab();
        for (int minute = 0; minute < 10; minute++) {
            w.pass(MINUTE);
            w.show();
        }
        // An upload that is no sale of this computer's: nothing is waiting on the website's answer.
        w.world.queueLive("an-offer-placed");
        w.upload();

        assertEquals("ten minutes on the Activity tab", 1, w.figuresAsked().size());
    }

    /** An upload the website accepted has moved its figures: it is asked again without waiting out the minute. */
    @Test
    public void anAcceptedUploadAsksAgainAtOnce() throws Exception {
        w.switchOn();
        w.openProfileTab();
        w.settle();
        w.pass(30 * SECOND);

        w.world.queueLive("a-trade");
        w.upload();

        assertEquals("the upload reached the website", 1, w.world.website.batches.size());
        assertEquals("asked again, half a minute after the last answer", 2, w.figuresAsked().size());
        assertEquals("402,078 gp", w.total());
    }

    /** A burst of uploads is not a burst of questions: no view is asked for twice in twenty seconds. */
    @Test
    public void uploadsCloseTogetherDoNotEachAskAgain() throws Exception {
        w.switchOn();
        w.openProfileTab();
        w.settle();

        w.pass(30 * SECOND);
        w.world.queueLive("first");
        w.upload();
        assertEquals(2, w.figuresAsked().size());

        w.pass(5 * SECOND);
        w.world.queueLive("second");
        w.upload();
        w.pass(5 * SECOND);
        w.world.queueLive("third");
        w.upload();
        assertEquals("three uploads accepted", 3, w.world.website.batches.size());
        assertEquals("ten seconds after the last question: not asked again", 2, w.figuresAsked().size());

        w.pass(11 * SECOND);
        w.world.queueLive("fourth");
        w.upload();
        assertEquals("twenty-one seconds after it: asked", 3, w.figuresAsked().size());
    }

    /**
     * The website says it has been asked too much, and to wait 37 seconds. Nothing is asked for 37
     * seconds: not the same view, and not another one.
     */
    @Test
    public void aRateLimitStopsEveryQuestionForAsLongAsTheWebsiteSays() throws Exception {
        w.rateLimited();
        w.openProfileTab();
        w.settle();
        assertEquals(1, w.figuresAsked().size());
        w.switchOn();

        w.pass(36 * SECOND);
        w.show();
        assertEquals("36 seconds on, the same view", 1, w.figuresAsked().size());
        w.range(StatsRange.LAST_7D);
        w.settle();
        assertEquals("36 seconds on, another view", 1, w.figuresAsked().size());

        w.pass(2 * SECOND);
        w.show();
        assertTrue("38 seconds on: asked", w.figuresAsked().size() > 1);
        assertEquals("401,888 gp", w.total());
    }

    /**
     * The website refuses the session (a 401). The plugin gets a new one the way its uploads do and
     * asks again; it does not throw the link away.
     */
    @Test
    public void aRefusedSessionIsRefreshedAndTheLinkIsKept() throws Exception {
        boolean[] refused = new boolean[1];
        w.switchOn();
        java.util.function.Function<Request, RecordSyncWorld.Reply> live = w.world.website.figures;
        w.world.website.figures = request -> {
            if (!refused[0]) {
                refused[0] = true;
                return FigureFixtures.refusal("no_token");
            }
            return live.apply(request);
        };

        w.openProfileTab();
        w.settle();
        w.pass(61 * SECOND);
        w.show();

        assertEquals("402,078 gp", w.total());
        assertTrue("a new session was asked for: " + w.world.website.other,
            w.world.website.other.contains("/api/plugin/refresh"));
        assertFalse("never a new link", w.world.website.other.contains("/api/plugin/link"));
        assertNotEquals("the session is not cleared", "",
            w.world.configManager.getConfiguration(FliphubConfigGroups.CONFIG_GROUP, "sessionToken"));
    }

    // ---- where from ----

    /**
     * The scheduler runs the offer poll four times a second and the Swing thread draws the panel:
     * neither may wait on the network. Every question leaves from the IO pool, which here is the
     * thread the test answers on.
     */
    @Test
    public void theWebsiteIsAskedFromTheIoPoolOnly() throws Exception {
        w.switchOn();
        w.openProfileTab();
        w.settle();
        w.range(StatsRange.LAST_7D);
        w.settle();
        w.pass(61 * SECOND);
        w.show();
        w.world.queueLive("a-trade");
        w.upload();

        List<RecordSyncWorld.Asked> asked = w.world.website.asked;
        assertTrue("it asked " + asked.size() + " times", asked.size() >= 3);
        for (RecordSyncWorld.Asked one : asked) {
            assertSame("asked on " + one.thread.getName(), Thread.currentThread(), one.thread);
        }
    }

    private static Request last(List<Request> asked) {
        assertFalse("the website was asked nothing", asked.isEmpty());
        return asked.get(asked.size() - 1);
    }
}
