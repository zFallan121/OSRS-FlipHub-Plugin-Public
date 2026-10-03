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

import com.google.gson.Gson;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import okhttp3.Request;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import static com.osrsfliphub.SiteFiguresWorld.ALT;
import static com.osrsfliphub.SiteFiguresWorld.MAIN;
import static com.osrsfliphub.SiteFiguresWorld.MINUTE;
import static com.osrsfliphub.SiteFiguresWorld.SECOND;
import static com.osrsfliphub.SiteFiguresWorld.WHIP;
import static com.osrsfliphub.SiteFiguresWorld.flip;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * What the audit of the site figures found, before they reached a player: a Session that began
 * a minute early, a refused session asked about without end, answers the website might send that
 * are not what it promised, and two places where one thing failing took another down with it.
 *
 * <p>This computer's own files hold one flip, 999 profit. The website's book holds 402,078 from
 * six flips.
 */
public class SiteFiguresAuditTest {
    @Rule
    public final Timeout timeout = Timeout.seconds(120);

    private SiteFiguresWorld w;

    @Before
    public void setUp() throws Exception {
        w = SiteFiguresWorld.fresh();
        w.own(MAIN, flip(0, WHIP, 10, 10_000L, 10_999L, 180 * MINUTE, 40 * MINUTE));
        w.world.state.getItemNameCache().put(WHIP, "Abyssal whip");
        w.login(MAIN);
    }

    @After
    public void tearDown() throws Exception {
        SiteFiguresWorld.close(w);
    }

    // ---- the Session range ----

    /**
     * MAIN sells for +100,000 at 14:00:20 and logs out; ALT logs in at 14:00:50. ALT's session
     * began at 14:00:50, and Hourly profit divides by the time since then: the website is asked
     * for what was sold since 14:00:50 exactly. Asked from 14:00:00, the minute it falls in, its
     * answer would hold MAIN's sale, and ten seconds in the tab would say 36.0M an hour.
     */
    @Test
    public void theSessionRangeIsAskedForFromTheMomentTheSessionBegan() throws Exception {
        w.login(ALT);
        long minute = (System.currentTimeMillis() - 5 * MINUTE) / MINUTE * MINUTE;
        long loggedIn = minute + 50 * SECOND;
        synchronized (w.world.state.getLocalStatsLock()) {
            w.world.state.getLocalSessionStartByAccount().put(Const.ACCOUNTWIDE_KEY, loggedIn);
            w.world.state.getLocalSessionStartByAccount().put(ALT, loggedIn);
        }
        w.switchOn();
        w.openProfileTab();
        w.settle();

        w.range(StatsRange.SESSION);
        w.settle();

        assertEquals("the website's own figures for the session", "401,888 gp", w.total());
        assertEquals("from the login, to the millisecond, and never from the minute it fell in",
            Arrays.asList(String.valueOf(loggedIn)), sinceAsked());

        // It is one view all the same: looked at again and again, asked for once a minute.
        for (int i = 0; i < 3; i++) {
            w.pass(19 * SECOND);
            w.show();
        }
        assertEquals("57 seconds on", 1, sinceAsked().size());
        w.pass(4 * SECOND);
        w.show();
        assertEquals("61 seconds on: asked again, from the same moment",
            Arrays.asList(String.valueOf(loggedIn), String.valueOf(loggedIn)), sinceAsked());
        assertEquals("401,888 gp", w.total());
    }

    // ---- a session the website goes on refusing ----

    /**
     * The website refuses the session, hands out a new one, and refuses that too. The new
     * session's view has never been asked for, so nothing held it back: it was asked for at once,
     * the session renewed again, and so on, several times a second, until the website's own limit
     * stopped it. It is asked about once a minute: ten questions in ten minutes, each put a
     * second time under its renewed session, and never a sum of this computer's own on the tab.
     */
    @Test
    public void aSessionStillRefusedAfterItsRenewalIsAskedAboutOnceAMinute() throws Exception {
        showTheWebsitesFigures();
        int asked = w.figuresAsked().size();
        AtomicInteger renewals = new AtomicInteger();
        // Each renewal is a session of its own, as the website's is, and the plugin takes it.
        w.world.website.issued = () -> w.world.session = "session-" + renewals.incrementAndGet();
        w.world.website.figures = request -> FigureFixtures.refusal("no_token");

        for (int minute = 1; minute <= 10; minute++) {
            w.pass(30 * SECOND);
            w.show();
            assertEquals("half a minute after question " + (minute - 1) + ": nothing asked",
                asked + 2 * (minute - 1), w.figuresAsked().size());
            w.pass(31 * SECOND);
            w.show();
            assertEquals("minute " + minute + ": one question, put again under the renewed session",
                asked + 2 * minute, w.figuresAsked().size());
            assertEquals("minute " + minute + ": one renewal a question", minute, renewals.get());
            assertNotEquals("minute " + minute + ": this computer's own sum", "999 gp", w.total());
        }
        assertEquals("the last question was put under the session renewed for it",
            "session-10", last(w.figuresAsked()).header("X-Plugin-Token"));
        assertFalse("never a new link", w.world.website.other.contains("/api/plugin/link"));
        assertNotEquals("the session is not cleared", "",
            w.world.configManager.getConfiguration(FliphubConfigGroups.CONFIG_GROUP, "sessionToken"));

        w.switchOn();
        w.pass(61 * SECOND);
        w.show();
        assertEquals("the website takes the session again: its figures", "402,078 gp", w.total());
        assertEquals(10, renewals.get());
    }

    // ---- an upload the website accepted ----

    /**
     * The website has taken a batch, and asking for the figures it moved goes wrong. The batch
     * is the website's all the same: it is not queued again, to be sent a second time.
     */
    @Test
    public void anAcceptedUploadIsNotQueuedAgainWhenAskingForItsFiguresGoesWrong() throws Exception {
        showTheWebsitesFigures();
        SiteFigures figures = w.world.injector.getInstance(SiteFigures.class);
        Thread ioPool = Thread.currentThread();
        boolean[] wrong = {true};
        // What an accepted upload does first is tell the time: on the IO pool, which is this thread.
        figures.clock = () -> {
            if (wrong[0] && Thread.currentThread() == ioPool) {
                throw new IllegalStateException("asking for the figures went wrong");
            }
            return w.now();
        };

        w.world.queueLive("a-trade");
        w.world.tick();
        w.answer();
        wrong[0] = false;

        UploadDiagnosticsState uploads = w.world.state.getUploadState();
        assertEquals("the upload reached the website", 1, w.world.website.batches.size());
        assertEquals("and is not queued again", 0, uploads.getPendingUploadEvents());
        assertFalse("nor is any wait begun", uploads.isBackingOff(System.currentTimeMillis()));
        w.upload();
        assertEquals("nothing is sent a second time", 1, w.world.website.batches.size());
    }

    // ---- an answer the level cannot be judged on ----

    /**
     * The first answer to say the website is live arrives, and working out the level from it
     * fails. The answer is taken in and drawn all the same: the tab does not go on showing this
     * computer's own sum until the next minute's refresh.
     */
    @Test
    public void anAnswerIsDrawnWhateverBecomesOfTheLevel() throws Exception {
        w.switchOn();
        w.openProfileTab();
        assertEquals("not answered yet, and never said to be live", "999 gp", w.total());
        PluginConfig config = w.world.plugin.config;
        Thread ioPool = Thread.currentThread();
        // The level reads this setting first of all, on the IO pool, which is this thread.
        w.world.plugin.config = (PluginConfig) Proxy.newProxyInstance(PluginConfig.class.getClassLoader(),
            new Class<?>[] {PluginConfig.class}, (proxy, method, args) -> {
                if ("merchantLevelScope".equals(method.getName()) && Thread.currentThread() == ioPool) {
                    throw new IllegalStateException("the level cannot be worked out");
                }
                try {
                    return method.invoke(config, args);
                } catch (InvocationTargetException ex) {
                    throw ex.getCause();
                }
            });

        assertThrows(IllegalStateException.class, w::answer);
        w.world.plugin.config = config;
        w.quiesce();

        assertEquals("the website's figure, drawn", "402,078 gp", w.total());
        assertEquals("6", w.flipCount());
    }

    // ---- answers that are not all there ----

    /**
     * An answer that says it is live and lacks its summary, its kinds or its items, or has a
     * null where an item or a kind should be, is no answer: the last one stays, with the time it
     * is from, and the level keeps the figure it had.
     */
    @Test
    public void anAnswerThatIsNotAllThereIsAFailedQuestionAndTheLastOneStays() throws Exception {
        showTheWebsitesFigures();
        String answeredAt = SiteFiguresWorld.clockTime(w.now());

        for (Map.Entry<String, String> shape : incomplete().entrySet()) {
            String what = shape.getKey();
            int asked = w.figuresAsked().size();
            w.world.website.figures = request -> new RecordSyncWorld.Reply(200, shape.getValue());
            w.pass(61 * SECOND);
            w.show();

            assertTrue(what + ": it was asked", w.figuresAsked().size() > asked);
            assertEquals(what, "402,078 gp", w.total());
            assertEquals(what, "6", w.flipCount());
            assertNotNull(what + ": says the figures are from " + answeredAt + ": " + w.texts(),
                w.labelSaying("from " + answeredAt));
            assertEquals(what + ": the level's figure", Long.valueOf(402_078L), remembered());
        }

        // The total of one kind, which a null kind would have broken the drawing of.
        w.totalOf(StatsRecipeFilter.FLIP);
        assertEquals("1,988 gp", w.total());
        w.totalOf(StatsRecipeFilter.ALL);

        w.switchOn();
        w.pass(61 * SECOND);
        w.show();
        assertEquals("402,078 gp", w.total());
        assertNull("answered in full again, so nothing to say", w.labelSaying("from " + answeredAt));
    }

    /**
     * The same as the first answer there ever is: {@code {"live": true, "items": []}}. The
     * website has not been heard to say it is live, and no lifetime profit of 0 is remembered
     * for the level to read.
     */
    @Test
    public void aFirstAnswerThatIsNotAllThereDoesNotMakeTheWebsiteLive() throws Exception {
        w.world.website.figures = request -> new RecordSyncWorld.Reply(200, "{\"live\": true, \"items\": []}");

        w.openProfileTab();
        w.settle();

        assertEquals("it was asked", 1, w.figuresAsked().size());
        assertEquals("this computer's own sum, as before the switch", "999 gp", w.total());
        assertEquals("1", w.flipCount());
        assertNull("not remembered as live", w.world.configManager.getConfiguration(
            FliphubConfigGroups.CONFIG_GROUP, SiteFigures.LIVE_KEY, Boolean.class));
        assertNull("no figure for the level", remembered());
    }

    /**
     * An item's flips come with a null among them. They are not kept: the list stays as it was,
     * and the tab goes on being drawn, where every draw of that list would have failed.
     */
    @Test
    public void aFlipListWithAFlipMissingIsNotKeptAndTheTabGoesOnBeingDrawn() throws Exception {
        showTheWebsitesFigures();
        w.clickCard("Abyssal whip");
        w.clickFlipList();
        w.settle();
        assertEquals(1, w.flipsAsked().size());
        assertTrue(w.texts().toString(), w.texts().contains("Profit: 973 gp"));

        // The next answer's row for the whip is another, so its flips are asked for again.
        w.world.website.figures = request -> new RecordSyncWorld.Reply(200,
            request.url().encodedPath().endsWith("/figures") ? FigureFixtures.live("main_all_time")
                : "{\"live\": true, \"more\": false, \"flips\": [null]}");
        w.pass(61 * SECOND);
        w.show();

        assertEquals("asked for again", 2, w.flipsAsked().size());
        assertEquals("the new answer is drawn", "401,653 gp", w.total());
        assertTrue("and the list is as it was: " + w.texts(), w.texts().contains("Profit: 973 gp"));

        w.switchOn();
        w.pass(61 * SECOND);
        w.show();
        assertEquals("and goes on being drawn", "402,078 gp", w.total());
    }

    // ---- a wait without end ----

    /**
     * A 429 that says to wait a year. The website counts its limit over ten minutes, so ten
     * minutes is the longest wait there is: after it the website is asked again.
     */
    @Test
    public void aRateLimitIsWaitedOutForTenMinutesAtMost() throws Exception {
        RecordSyncWorld.Reply refused = FigureFixtures.rateLimited();
        w.world.website.figures = request -> new RecordSyncWorld.Reply(refused.status, refused.body, "31536000");
        w.openProfileTab();
        w.settle();
        assertEquals(1, w.figuresAsked().size());
        w.switchOn();

        w.pass(9 * MINUTE + 59 * SECOND);
        w.show();
        assertEquals("a second short of ten minutes", 1, w.figuresAsked().size());

        w.pass(2 * SECOND);
        w.show();
        assertTrue("ten minutes on: asked", w.figuresAsked().size() > 1);
        assertEquals("402,078 gp", w.total());
    }

    // ---- two sessions begun in one minute ----

    /**
     * A session begins at 14:00:10 and its figures are asked for. The client drops, and the
     * player is back at 14:00:50: another session, begun in the same minute. It is a view of its
     * own, dashes until it is answered, and asked for at once from 14:00:50. Shown the first
     * session's answer instead, five seconds in, a +100,000 sale from 14:00:20 would read as
     * 72.0M an hour.
     */
    @Test
    public void aSecondSessionBegunInTheSameMinuteShowsDashesAndIsAskedForAtOnce() throws Exception {
        long minute = (System.currentTimeMillis() - 5 * MINUTE) / MINUTE * MINUTE;
        long first = minute + 10 * SECOND;
        long second = minute + 50 * SECOND;
        sessionBegan(first);
        w.switchOn();
        w.openProfileTab();
        w.settle();
        w.range(StatsRange.SESSION);
        w.settle();
        assertEquals("the first session's figures", "401,888 gp", w.total());
        assertEquals(Arrays.asList(String.valueOf(first)), sinceAsked());

        w.pass(25 * SECOND);
        sessionBegan(second);
        w.refresh();

        assertTrue("the last session's figures are not this one's, but the tab shows " + w.total(),
            SiteFiguresWorld.dashes(w.total()));
        assertTrue(w.flipCount(), SiteFiguresWorld.dashes(w.flipCount()));
        w.settle();
        assertEquals("asked for at once, 25 seconds after the last question, from its own start",
            Arrays.asList(String.valueOf(first), String.valueOf(second)), sinceAsked());
        assertEquals("401,888 gp", w.total());
    }

    // ---- whose figures, as the session changes ----

    /**
     * The website refuses the session and renews it: the same link, under another session. The
     * figures given under the last one stay on show, and when the question fails all the same
     * they are said to be from when they were given. Not dashes, as for a link never answered.
     */
    @Test
    public void aRenewedSessionKeepsTheFiguresItWasGivenWithTheirTime() throws Exception {
        showTheWebsitesFigures();
        String answeredAt = SiteFiguresWorld.clockTime(w.now());
        w.world.website.issued = () -> w.world.session = "session-renewed";
        w.world.website.figures = request -> FigureFixtures.refusal("no_token");

        w.pass(61 * SECOND);
        w.show();

        assertEquals("renewed, and asked again under the new session",
            "session-renewed", last(w.figuresAsked()).header("X-Plugin-Token"));
        assertEquals("the figures it had", "402,078 gp", w.total());
        assertEquals("6", w.flipCount());
        assertNotNull("says the figures are from " + answeredAt + ": " + w.texts(),
            w.labelSaying("from " + answeredAt));
        assertEquals("the level's figure", Long.valueOf(402_078L), remembered());

        w.switchOn();
        w.pass(61 * SECOND);
        w.show();
        assertEquals("402,078 gp", w.total());
        assertNull("answered under the renewed session, so nothing to say", w.labelSaying("from " + answeredAt));
    }

    /** The same when it is an upload the session was renewed for, and no question about figures. */
    @Test
    public void aSessionRenewedForAnUploadKeepsTheFiguresToo() throws Exception {
        showTheWebsitesFigures();
        String answeredAt = SiteFiguresWorld.clockTime(w.now());
        w.world.website.issued = () -> w.world.session = "session-renewed";
        // The batch is refused under the old session and taken under the new one.
        w.world.website.answer = events -> "session-renewed".equals(w.world.session)
            ? w.world.website.takeAll(events) : RecordSyncWorld.Website.status(401);
        w.unreachable();

        w.pass(61 * SECOND);
        w.world.queueLive("a-trade");
        w.upload();

        assertEquals("the upload was taken under the renewed session", "session-renewed", w.world.session);
        assertEquals(0, w.world.state.getUploadState().getPendingUploadEvents());
        assertTrue("and its figures were asked for", w.figuresAsked().size() > 1);
        assertEquals("the figures it had", "402,078 gp", w.total());
        assertNotNull("says the figures are from " + answeredAt + ": " + w.texts(),
            w.labelSaying("from " + answeredAt));
    }

    /**
     * The player switches RuneLite profile, to one linked to another website account. The session
     * is that profile's, and nobody renewed it: nothing the last profile's account was told is
     * shown under it. Dashes until an answer of its own, however long the website takes to give
     * one, where a failed question would otherwise have kept 402,078 on show.
     */
    @Test
    public void aSessionThatChangedAnyOtherWayNeverShowsTheLastOnesFigures() throws Exception {
        showTheWebsitesFigures();
        int asked = w.figuresAsked().size();
        w.unreachable();

        // The profile's own config is read from here on; its website was live too.
        w.world.session = "another-profiles-session";
        w.pass(SECOND);
        w.refresh();

        assertTrue("not answered under this session yet, but the tab shows " + w.total(),
            SiteFiguresWorld.dashes(w.total()));
        w.settle();
        assertEquals("asked at once, a second after the last answer", asked + 1, w.figuresAsked().size());
        assertEquals("another-profiles-session", last(w.figuresAsked()).header("X-Plugin-Token"));
        for (int minute = 1; minute <= 3; minute++) {
            assertTrue("minute " + minute + ", and the website cannot be reached, but the tab shows " + w.total(),
                SiteFiguresWorld.dashes(w.total()));
            assertTrue(w.flipCount(), SiteFiguresWorld.dashes(w.flipCount()));
            w.pass(61 * SECOND);
            w.show();
        }

        w.switchOn();
        w.pass(61 * SECOND);
        w.show();
        assertEquals("its own answer", "402,078 gp", w.total());
    }

    // ---- a wait cut short, and one kept too long ----

    /**
     * Two questions are out at once. The first is told to wait five minutes; the second, a moment
     * later, is refused its session even after a renewal, which is a minute's wait. The five
     * minutes stand: asked again after one, the website would only say 429 once more.
     */
    @Test
    public void aRefusedSessionsMinuteNeverShortensALongerWait() throws Exception {
        showTheWebsitesFigures();
        RecordSyncWorld.Reply limited = FigureFixtures.rateLimited();
        AtomicInteger questions = new AtomicInteger();
        w.world.website.figures = request -> questions.getAndIncrement() == 0
            ? new RecordSyncWorld.Reply(limited.status, limited.body, "300") : FigureFixtures.refusal("no_token");
        w.pass(61 * SECOND);
        // Every character's view is asked for, and then MAIN's own, before either is answered.
        w.refresh();
        w.character(MAIN);
        w.settle();
        assertEquals("one told to wait, the other refused before and after its renewal", 3, questions.get());

        w.switchOn();
        int asked = w.figuresAsked().size();
        for (int minute = 1; minute <= 4; minute++) {
            w.pass(61 * SECOND);
            w.show();
            assertEquals(minute + " minutes into the five: nothing asked", asked, w.figuresAsked().size());
        }
        w.pass(61 * SECOND);
        w.show();
        assertTrue("five minutes on: asked", w.figuresAsked().size() > asked);
    }

    /**
     * The website refuses the session, and a minute's wait begins. Ten seconds into it the player
     * links again. The wait was the last link's: the new one is asked about at once, and does not
     * sit on this computer's own 999 for the fifty seconds left of it.
     */
    @Test
    public void aNewLinkIsAskedAboutAtOnceWhateverWaitTheLastOneWasIn() throws Exception {
        showTheWebsitesFigures();
        w.world.website.figures = request -> FigureFixtures.refusal("no_token");
        w.pass(61 * SECOND);
        w.show();
        int asked = w.figuresAsked().size();

        w.pass(10 * SECOND);
        w.switchOn();
        w.world.linked = false;
        try {
            w.world.injector.getInstance(LinkAttempt.class).performUnlink();
        } catch (RuntimeException standIn) {
            // The config standing in here cannot write RuneLite's file out, which an unlink does part way.
        }
        // Linking from the panel turns the sync setting on itself; the config standing in here is told.
        w.world.linked = true;
        w.world.session = "another-session";
        w.world.injector.getInstance(LinkAttempt.class).linkFromPanel("another-accounts-key");
        w.answer();
        w.show();

        assertTrue("asked at once, under the new link", w.figuresAsked().size() > asked);
        assertEquals("another-session", last(w.figuresAsked()).header("X-Plugin-Token"));
        assertEquals("the website's figures for it", "402,078 gp", w.total());
    }

    // ---- helpers ----

    /** A session begins for the character logged in, and for every character: where the Session range starts. */
    private void sessionBegan(long atMs) {
        synchronized (w.world.state.getLocalStatsLock()) {
            w.world.state.getLocalSessionStartByAccount().put(Const.ACCOUNTWIDE_KEY, atMs);
            w.world.state.getLocalSessionStartByAccount().put(MAIN, atMs);
        }
    }

    /** Linked, the switch on, Accountwide, All time: the tab shows the website's 402,078 from six flips. */
    private void showTheWebsitesFigures() throws Exception {
        w.switchOn();
        w.openProfileTab();
        w.settle();
        assertEquals("402,078 gp", w.total());
        assertEquals("6", w.flipCount());
    }

    /** The lifetime profit kept for the level of every character, or null. */
    private Long remembered() {
        return w.world.configManager.getConfiguration(FliphubConfigGroups.CONFIG_GROUP,
            SiteFigures.LIFETIME_KEY + Const.ACCOUNTWIDE_KEY, Long.class);
    }

    /**
     * Answers that say they are live and are not all there: the website's own answer for the
     * whole account with one thing taken out of it, and the barest of them.
     */
    private static Map<String, String> incomplete() {
        Map<String, String> shapes = new LinkedHashMap<>();
        shapes.put("no summary", account(answer -> answer.remove("summary")));
        shapes.put("no kinds", account(answer -> answer.remove("by_kind")));
        shapes.put("no items", account(answer -> answer.remove("items")));
        shapes.put("an item that is null", account(answer -> answer.getAsJsonArray("items").set(0, JsonNull.INSTANCE)));
        shapes.put("a kind that is null",
            account(answer -> answer.getAsJsonObject("by_kind").add("FLIP", JsonNull.INSTANCE)));
        shapes.put("only that it is live", "{\"live\": true, \"items\": []}");
        return shapes;
    }

    private static String account(Consumer<JsonObject> change) {
        JsonObject answer = new Gson().fromJson(FigureFixtures.live("account_all_time"), JsonObject.class);
        change.accept(answer);
        // Written out by the element itself, which keeps a null.
        return answer.toString();
    }

    /** Where each question about a range was asked to start from, in order: the Session is the only range here. */
    private List<String> sinceAsked() {
        List<String> since = new ArrayList<>();
        for (Request request : w.figuresAsked()) {
            if (request.url().queryParameter("since_ms") != null) {
                since.add(request.url().queryParameter("since_ms"));
            }
        }
        return since;
    }

    private static Request last(List<Request> asked) {
        assertFalse("the website was asked nothing", asked.isEmpty());
        return asked.get(asked.size() - 1);
    }
}
