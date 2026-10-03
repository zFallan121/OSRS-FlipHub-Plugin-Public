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
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import net.runelite.api.GrandExchangeOfferState;
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * What the first review of the site figures found: the places where a linked plugin could go on
 * showing a figure the website had moved past, or let a level-up go by unsaid.
 *
 * <p>The Merchant level asks for itself, Profile tab or no Profile tab. An upload accepted inside
 * the twenty seconds a view may not be asked again in is asked for when they are up. A sale is
 * judged on the answer to the upload that carried it, whichever batch that was, and whether or
 * not this computer's own files could pair it. An item's flips are asked for again only when its
 * row has changed. A rate limit is waited out however the website says how long.
 *
 * <p>And what the second found: a sale is queued for upload before it is filed, so the upload can
 * take it before the level has heard of it, and its answer is the sale's all the same. And a sale
 * noted while an answer is being taken in is not cleared along with what was answered.
 *
 * <p>This computer's own files hold one flip, 999 profit, which is level 1. What the plugin asks
 * its scheduler to run later is held here ({@link #later}) and run when a test says its wait is
 * over ({@link #runLater}): time passes only when a test says so. But for one clock, which no
 * test can set: the computer's own, which times a sale's event and a batch leaving the queue.
 * Where one has to come after the other, a test waits for it to move on ({@link #aMoment}).
 */
public class SiteFiguresFollowUpTest {
    private static final long ACCOUNT = 402_078L;
    private static final long MAINS = 401_653L;
    private static final long ALTS = 235L;

    @Rule
    public final Timeout timeout = Timeout.seconds(120);

    private SiteFiguresWorld w;
    /** What the plugin has asked its scheduler to run a second or more from now, and has not been run. */
    private final List<Later> later = new CopyOnWriteArrayList<>();

    private static final class Later {
        final long waitMs;
        final Runnable work;

        Later(long waitMs, Runnable work) {
            this.waitMs = waitMs;
            this.work = work;
        }
    }

    @Before
    public void setUp() throws Exception {
        w = SiteFiguresWorld.fresh();
        w.own(MAIN, flip(0, WHIP, 10, 10_000L, 10_999L, 180 * MINUTE, 40 * MINUTE));
        w.world.state.getItemNameCache().put(WHIP, "Abyssal whip");
        w.world.celebrating = true;
        ScheduledExecutorService real = w.scheduler;
        w.world.plugin.scheduler = (ScheduledExecutorService) Proxy.newProxyInstance(
            ScheduledExecutorService.class.getClassLoader(), new Class<?>[] {ScheduledExecutorService.class},
            (proxy, method, args) -> {
                if ("schedule".equals(method.getName()) && args[0] instanceof Runnable
                    && ((TimeUnit) args[2]).toMillis((Long) args[1]) >= SECOND) {
                    later.add(new Later(((TimeUnit) args[2]).toMillis((Long) args[1]), (Runnable) args[0]));
                    return null;
                }
                try {
                    return method.invoke(real, args);
                } catch (InvocationTargetException ex) {
                    throw ex.getCause();
                }
            });
    }

    @After
    public void tearDown() throws Exception {
        SiteFiguresWorld.close(w);
    }

    // ---- the level asks for itself ----

    /**
     * The Profile tab is never opened on this computer. The level is looked at, at login and each
     * time the skills tab is opened, and each look asks the website, once a minute at most: the
     * day the switch goes on the level becomes the website's, silently.
     */
    @Test
    public void withTheProfileTabNeverOpenedTheLevelBecomesTheWebsitesOnceTheSwitchIsOn() throws Exception {
        w.login(MAIN);
        w.settle();
        assertEquals("before the switch: this computer's own", 999L, look());

        w.switchOn();
        w.pass(61 * SECOND);
        look();
        w.settle();

        assertEquals("what the skills tab draws", ACCOUNT, level().profit);
        assertEquals(ACCOUNT, look());
        assertTrue("silent: " + w.world.chat, w.world.chat.isEmpty());
        assertEquals("on record as the best level", FlipLevel.levelFor(ACCOUNT), RankUp.best(RankUp.BEST_KEY));
        assertEquals("one look a minute, one question each", 2, w.figuresAsked().size());
        for (Request asked : w.figuresAsked()) {
            assertEquals("account", asked.url().queryParameter("scope"));
            assertNull("the lifetime figure: all time", asked.url().queryParameter("since_ms"));
        }
    }

    /**
     * Levelled per character, the Profile tab never opened. The website holds 235 for the
     * character; then the account's other computer sells and it holds 401,653. The next look, a
     * minute on, asks, and the level follows: silently, since no sale was made here.
     */
    @Test
    public void theLevelFollowsARiseFromTheOtherComputerOnItsNextLook() throws Exception {
        w.world.levelScope = PluginConfig.MerchantLevelScope.CHARACTER;
        w.characters.put(GeEvent.characterId(MAIN), "alt_all_time");
        w.login(MAIN);
        w.switchOn();
        w.settle();
        assertEquals("the website's figure for the character", ALTS, look());

        w.characters.put(GeEvent.characterId(MAIN), "main_all_time");
        w.pass(61 * SECOND);
        look();
        w.settle();

        assertEquals("what the skills tab draws", MAINS, level().profit);
        assertTrue("silent: " + w.world.chat, w.world.chat.isEmpty());
        assertEquals("on record as the best level", FlipLevel.levelFor(MAINS), RankUp.best("bestFlipLevel_" + MAIN));

        int asked = w.figuresAsked().size();
        w.pass(30 * SECOND);
        look();
        w.settle();
        assertEquals("half a minute on: the figure it has", asked, w.figuresAsked().size());
    }

    // ---- an upload accepted inside the twenty seconds ----

    /**
     * Ten whips bought, and the upload accepted: the tab's figures are asked for. Ten seconds on
     * they are sold, over a level, and that upload is accepted too. It is too soon to ask again,
     * and nothing else is uploaded and nothing redraws the tab: the question is put off, once,
     * for the ten seconds left, and the sale is celebrated on its answer.
     */
    @Test
    public void aSaleUploadedInsideTheTwentySecondsIsAskedForWhenTheyAreUp() throws Exception {
        perCharacterAt235();
        w.pass(30 * SECOND);
        w.world.buyTenWhips(2);
        w.quiesce();
        w.upload();
        int asked = w.figuresAsked().size();
        assertTrue("nothing is put off: " + waits(), later.isEmpty());

        w.pass(10 * SECOND);
        sellTenWhips(3);
        w.characters.put(GeEvent.characterId(MAIN), "main_all_time");
        w.upload();
        assertEquals("ten seconds after the last question: not asked", asked, w.figuresAsked().size());
        assertEquals(w.world.chat.toString(), 0, said("You are now level "));
        assertEquals("one question put off, for when the twenty seconds are up",
            Arrays.asList(10 * SECOND), waits());

        w.pass(10 * SECOND);
        runLater();
        w.settle();

        assertEquals("asked, twenty seconds after the last question", asked + 1, w.figuresAsked().size());
        assertEquals("401,653 gp", w.total());
        assertEquals(w.world.chat.toString(), 1, said("You are now level " + FlipLevel.levelFor(MAINS) + "."));
        assertEquals("and no other level: " + w.world.chat, 1, said("You are now level "));
        assertTrue("nothing more is put off: " + waits(), later.isEmpty());
    }

    /**
     * The same of the tab alone, with no sale: three uploads five seconds apart. The first is
     * asked for at once; the two inside the twenty seconds are asked for together, once, when the
     * twenty seconds are up.
     */
    @Test
    public void uploadsInsideTheTwentySecondsAreAskedForTogetherWhenTheyAreUp() throws Exception {
        w.login(MAIN);
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
        assertEquals("ten seconds after the last question: not asked", 2, w.figuresAsked().size());
        assertEquals("one question put off for the two of them, from the first", Arrays.asList(15 * SECOND), waits());

        w.pass(10 * SECOND);
        runLater();
        w.settle();

        assertEquals("asked once, twenty seconds after the last question", 3, w.figuresAsked().size());
        assertTrue("nothing more is put off: " + waits(), later.isEmpty());
    }

    // ---- which answer a sale is judged on ----

    /**
     * A batch leaves the queue, and a tenth of a second later, while it is on its way, ten whips
     * are sold over a level. That batch is accepted, and the website's figure after it has no
     * sale in it. The sale goes up in the next batch, and its answer is the one that is over the
     * level: celebrated then, once.
     */
    @Test
    public void aSaleMadeWhileAnOlderBatchIsOnItsWayIsJudgedOnItsOwnUploadsAnswer() throws Exception {
        perCharacterAt235();
        w.openActivityTab();
        w.pass(30 * SECOND);
        w.world.buyTenWhips(2);
        w.quiesce();
        w.upload();
        w.pass(30 * SECOND);

        w.world.queueLive("an-older-trade");
        boolean[] onItsWay = new boolean[1];
        w.world.website.answer = batch -> {
            if (!onItsWay[0]) {
                onItsWay[0] = true;
                w.pass(SECOND / 10);
                aMoment();
                sellTenWhips(3);
                w.pass(SECOND / 5);
            } else {
                // The batch with the sale in it: the website pairs it, and the character's lifetime profit is 401,653.
                w.characters.put(GeEvent.characterId(MAIN), "main_all_time");
            }
            return w.world.website.takeAll(batch);
        };
        w.upload();
        assertTrue("the sale was made while the older batch was on its way", onItsWay[0]);
        assertEquals("the older batch's answer has no sale in it: " + w.world.chat, 0, said("You are now level "));

        w.pass(2 * SECOND);
        w.upload();

        int reached = FlipLevel.levelFor(MAINS);
        assertEquals(w.world.chat.toString(), 1, said("You are now level " + reached + "."));
        assertEquals("and no other level: " + w.world.chat, 1, said("You are now level "));
        assertEquals(reached, RankUp.best("bestFlipLevel_" + MAIN));

        w.pass(61 * SECOND);
        w.openProfileTab();
        w.settle();
        assertEquals("the next answer, no new sale: " + w.world.chat, 1, said("You are now level "));
    }

    /**
     * Ten whips are sold over a level and uploaded, and while the question that brings is on its
     * way ten more are sold. The answer is still the first sale's: celebrated. And the second
     * sale still waits on an answer of its own: asked for once its upload is accepted and the
     * twenty seconds are up.
     */
    @Test
    public void aSaleMadeWhileAnEarlierSalesAnswerIsOnItsWayNeitherTakesItNorIsForgotten() throws Exception {
        perCharacterAt235();
        w.openActivityTab();
        w.pass(30 * SECOND);
        w.world.buyTenWhips(2);
        w.quiesce();
        w.upload();
        w.pass(30 * SECOND);

        sellTenWhips(3);
        w.characters.put(GeEvent.characterId(MAIN), "main_all_time");
        Function<Request, RecordSyncWorld.Reply> live = w.world.website.figures;
        boolean[] onItsWay = new boolean[1];
        w.world.website.figures = request -> {
            if (!onItsWay[0]) {
                onItsWay[0] = true;
                w.pass(SECOND / 10);
                aMoment();
                sellTenWhips(4);
            }
            return live.apply(request);
        };
        w.upload();
        assertTrue("the second sale was made while the first one's question was on its way", onItsWay[0]);
        assertEquals("the first sale's answer: " + w.world.chat, 1,
            said("You are now level " + FlipLevel.levelFor(MAINS) + "."));

        int asked = w.figuresAsked().size();
        w.pass(2 * SECOND);
        w.upload();
        assertEquals("2.1 seconds after the last question: not asked", asked, w.figuresAsked().size());
        assertEquals("the second sale's question is put off, not forgotten",
            Arrays.asList(20 * SECOND - 2 * SECOND - SECOND / 10), waits());

        w.pass(18 * SECOND);
        runLater();
        w.settle();
        assertEquals("asked", asked + 1, w.figuresAsked().size());
        assertEquals("and no level a second time: " + w.world.chat, 1, said("You are now level "));
    }

    /**
     * A sale is on its way up before it has been filed. Ten whips are sold, and the sale is
     * queued for upload as the game reports it. A twentieth of a second later the two-second
     * upload takes it. Filing it takes four tenths of a second more, and only then does the level
     * hear of the sale; the batch is accepted a quarter of a second after that. The website's
     * figure is then over a level, and that answer is this sale's, though its batch left before
     * the level had heard of it: celebrated, once, with the Profile tab on show.
     */
    @Test
    public void aSaleTakenForUploadBeforeItIsFiledIsJudgedOnThatUploadsAnswer() throws Exception {
        perCharacterAt235();
        w.pass(30 * SECOND);
        // The sale as the game reports it, and as its handler deals with it: queued first, filed after.
        GeEvent sale = GeEvent.createBase(new OfferSnapshot(3, WHIP, 120_000L, 10, 10, 1_200_000L, "SOLD", false),
            new OfferSnapshot(3, WHIP, 120_000L, 10, 0, 0L, "SELLING", false), "OFFER_COMPLETED");
        sale.delta_qty = 10;
        sale.delta_gp = 1_200_000L;
        sale.character_id = GeEvent.characterId(MAIN);
        w.world.injector.getInstance(UploadEventDispatch.class).enqueueEvent(sale);

        w.pass(SECOND / 20);
        boolean[] filed = new boolean[1];
        w.world.website.answer = batch -> {
            // On its way, the sale in it.
            w.pass(2 * SECOND / 5);
            aMoment();
            filed[0] = sale.event_id.equals(batch.get(0).event_id)
                && w.world.injector.getInstance(TradeDeltaRecorder.class).record(sale, false, 0L);
            w.pass(SECOND / 4);
            // The website pairs the sale: the character's lifetime profit is now 401,653.
            w.characters.put(GeEvent.characterId(MAIN), "main_all_time");
            return w.world.website.takeAll(batch);
        };
        w.upload();
        assertTrue("the sale was filed while the batch that held it was on its way", filed[0]);

        int reached = FlipLevel.levelFor(MAINS);
        assertEquals(w.world.chat.toString(), 1, said("You are now level " + reached + "."));
        assertEquals("and no other level: " + w.world.chat, 1, said("You are now level "));
        assertEquals(reached, RankUp.best("bestFlipLevel_" + MAIN));

        w.pass(61 * SECOND);
        w.show();
        assertEquals("the next answer, no new sale: " + w.world.chat, 1, said("You are now level "));
    }

    /**
     * A sale is noted on the game's thread while an answer is taken in on another. Each has to
     * read what is waiting and write it in one go: a sale landing between an answer's look and
     * its clearing would be cleared along with the sale that was answered. So each waits its
     * turn, and the sale noted meanwhile still waits on an answer of its own afterwards.
     */
    @Test
    public void aSaleNotedWhileAnAnswerIsTakenInWaitsItsTurnAndIsNotForgotten() throws Exception {
        perCharacterAt235();
        w.openActivityTab();
        w.pass(30 * SECOND);
        sellTenWhips(3);
        w.quiesce();
        w.characters.put(GeEvent.characterId(MAIN), "main_all_time");
        SiteFigures figures = w.world.injector.getInstance(SiteFigures.class);

        w.world.tick();
        // The upload, the question it brings and the taking in of its answer, as on the IO pool.
        Thread answering = new Thread(w::answer);
        Thread selling;
        // What is waiting to be judged is in somebody's hands: here, the test's.
        synchronized (figures.sales) {
            answering.start();
            answering.join(SECOND);
            assertTrue("the answer waits its turn", answering.isAlive());

            aMoment();
            long made = System.currentTimeMillis();
            selling = new Thread(() -> figures.sold(MAIN, made));
            selling.start();
            selling.join(SECOND / 5);
            assertTrue("and so does a sale", selling.isAlive());
        }
        answering.join(20 * SECOND);
        selling.join(20 * SECOND);
        assertFalse("each has had its turn", answering.isAlive() || selling.isAlive());
        w.settle();
        assertEquals("the first sale's answer: " + w.world.chat, 1,
            said("You are now level " + FlipLevel.levelFor(MAINS) + "."));

        w.pass(2 * SECOND);
        w.world.queueLive("the-second-sale");
        w.upload();
        assertEquals("the second sale's question is put off, not forgotten", Arrays.asList(18 * SECOND), waits());
    }

    /**
     * Ten whips bought on the account's other computer are sold on this one. This computer's
     * files have no purchase to pair the sale with, so its own total does not move; the website
     * has both halves, and its figure goes over a level. The sale was made here: celebrated here.
     */
    @Test
    public void aSaleThisComputersFilesCannotPairIsStillJudgedOnTheWebsitesAnswer() throws Exception {
        perCharacterAt235();
        w.openActivityTab();
        w.pass(30 * SECOND);
        long own = RankUp.lifetimeProfit(MAIN);

        sellTenWhips(3);
        w.quiesce();
        assertEquals("this computer's own total has not moved", own, RankUp.lifetimeProfit(MAIN));
        w.characters.put(GeEvent.characterId(MAIN), "main_all_time");
        w.upload();

        int reached = FlipLevel.levelFor(MAINS);
        assertEquals(w.world.chat.toString(), 1, said("You are now level " + reached + "."));
        assertEquals("and no other level: " + w.world.chat, 1, said("You are now level "));
        assertEquals(reached, RankUp.best("bestFlipLevel_" + MAIN));
    }

    /**
     * A purchase is not a sale. Ten whips are bought here while the other computer's sale lifts
     * the website's figure over a level: nothing is asked for on the purchase's upload, and the
     * rise, when the tab next shows it, is silent.
     */
    @Test
    public void aPurchaseIsNotASaleAndWaitsOnNoAnswer() throws Exception {
        perCharacterAt235();
        w.openActivityTab();
        w.pass(30 * SECOND);
        int asked = w.figuresAsked().size();

        w.world.buyTenWhips(2);
        w.quiesce();
        w.characters.put(GeEvent.characterId(MAIN), "main_all_time");
        w.upload();
        assertEquals("the upload reached the website", 1, w.world.website.batches.size());
        assertEquals("nothing is waiting on the website's answer", asked, w.figuresAsked().size());

        w.pass(61 * SECOND);
        w.openProfileTab();
        w.settle();
        assertEquals("401,653 gp", w.total());
        assertTrue("somebody else's rise: " + w.world.chat, w.world.chat.isEmpty());
    }

    // ---- one item's flips ----

    /**
     * The whip's flip list is open, and the website answers three times, a minute apart, with the
     * whip's row what it was: two flips, 1,208. Its flips are asked for once, and stay on show.
     */
    @Test
    public void anItemsFlipsAreAskedForOnceWhileItsRowStaysWhatItWas() throws Exception {
        showTheWhipsFlips();

        w.pass(61 * SECOND);
        w.show();
        w.pass(61 * SECOND);
        w.show();

        assertEquals("three answers", 3, w.figuresAsked().size());
        assertEquals("the whip's row is what it was", 1, w.flipsAsked().size());
        assertTrue("its flips stay on show: " + w.texts(), w.texts().contains("Profit: 973 gp"));
    }

    /**
     * The next answer's row for the whip is another: one flip, 973 (the website's own answer for
     * MAIN, given here for the account). Its flips are asked for again.
     */
    @Test
    public void anItemsFlipsAreAskedForAgainWhenItsRowChanges() throws Exception {
        showTheWhipsFlips();

        Function<Request, RecordSyncWorld.Reply> live = w.world.website.figures;
        w.world.website.figures = request -> request.url().encodedPath().endsWith("/figures")
            ? new RecordSyncWorld.Reply(200, FigureFixtures.live("main_all_time")) : live.apply(request);
        w.pass(61 * SECOND);
        w.show();

        assertTrue("the whip's row is now one flip, 973: " + w.texts(), w.texts().contains("973 gp"));
        assertFalse(w.texts().toString(), w.texts().contains("1,208 gp"));
        assertEquals("its row changed: asked for again", 2, w.flipsAsked().size());
    }

    // ---- a rate limit ----

    /**
     * A 429 that says how long to wait only in its body (37 seconds), the header lost on the way.
     * Nothing is asked for 37 seconds, of any view.
     */
    @Test
    public void aRateLimitThatSaysHowLongOnlyInItsBodyIsWaitedOut() throws Exception {
        RecordSyncWorld.Reply refused = FigureFixtures.rateLimited();
        w.world.website.figures = request -> new RecordSyncWorld.Reply(refused.status, refused.body);
        w.login(MAIN);
        w.openProfileTab();
        w.settle();
        assertEquals(1, w.figuresAsked().size());
        w.switchOn();

        w.pass(36 * SECOND);
        w.range(StatsRange.LAST_7D);
        w.settle();
        assertEquals("36 seconds on, another view", 1, w.figuresAsked().size());

        w.pass(2 * SECOND);
        w.show();
        assertEquals("38 seconds on: asked", 2, w.figuresAsked().size());
        assertEquals("401,888 gp", w.total());
    }

    /** A 429 that does not say how long, in a header or in its body: a minute. */
    @Test
    public void aRateLimitThatDoesNotSayHowLongIsAMinutesWait() throws Exception {
        // The website's own refusal, without the wait it names.
        JsonObject body = new Gson().fromJson(FigureFixtures.rateLimited().body, JsonObject.class);
        body.remove("retry_after");
        w.world.website.figures = request -> new RecordSyncWorld.Reply(429, body.toString());
        w.login(MAIN);
        w.openProfileTab();
        w.settle();
        assertEquals(1, w.figuresAsked().size());
        w.switchOn();

        w.pass(59 * SECOND);
        w.range(StatsRange.LAST_7D);
        w.settle();
        assertEquals("59 seconds on, another view", 1, w.figuresAsked().size());

        w.pass(2 * SECOND);
        w.show();
        assertTrue("61 seconds on: asked", w.figuresAsked().size() > 1);
        assertEquals("401,888 gp", w.total());
    }

    /**
     * The website refuses the session, the plugin gets a new one and asks again, and that
     * question is the one too many: a 429, wait 37 seconds. It is waited out like any other.
     */
    @Test
    public void aRateLimitOnTheQuestionAskedAgainUnderANewSessionIsWaitedOutToo() throws Exception {
        int[] questions = new int[1];
        w.world.website.figures = request -> questions[0]++ == 0
            ? FigureFixtures.refusal("no_token") : FigureFixtures.rateLimited();
        w.login(MAIN);
        w.openProfileTab();
        w.settle();
        assertEquals("refused, and asked again under the new session", 2, w.figuresAsked().size());
        assertTrue(w.world.website.other.toString(), w.world.website.other.contains("/api/plugin/refresh"));
        w.switchOn();

        w.pass(36 * SECOND);
        w.range(StatsRange.LAST_7D);
        w.settle();
        assertEquals("36 seconds on, another view", 2, w.figuresAsked().size());

        w.pass(2 * SECOND);
        w.show();
        assertEquals("38 seconds on: asked", 3, w.figuresAsked().size());
        assertEquals("401,888 gp", w.total());
    }

    // ---- an item without its kinds ----

    /**
     * The website's own answer, but for the whip's kinds, which come as a null. The answer
     * stands, with every figure in it, and the whip counts as a plain flip.
     */
    @Test
    public void anItemSentWithoutItsKindsIsAPlainFlipAndTheAnswerStands() throws Exception {
        JsonObject answer = new Gson().fromJson(FigureFixtures.live("account_all_time"), JsonObject.class);
        boolean found = false;
        for (JsonElement item : answer.getAsJsonArray("items")) {
            if (item.getAsJsonObject().get("item_id").getAsInt() == WHIP) {
                item.getAsJsonObject().add("kinds", JsonNull.INSTANCE);
                found = true;
            }
        }
        assertTrue("the website's answer has the whip", found);
        // Written out by the element itself, which keeps the null.
        w.world.website.figures = request -> new RecordSyncWorld.Reply(200, answer.toString());
        w.login(MAIN);

        w.openProfileTab();
        w.settle();

        assertEquals("402,078 gp", w.total());
        assertEquals("6", w.flipCount());
        w.listOnly(StatsRecipeFilter.FLIP);
        assertTrue("among the plain flips: " + w.texts(), w.texts().contains("Abyssal whip"));
        w.listOnly(StatsRecipeFilter.ANY_RECIPE);
        assertFalse("and in no recipe: " + w.texts(), w.texts().contains("Abyssal whip"));
    }

    // ---- helpers ----

    private RankUp level() {
        return w.world.injector.getInstance(RankUp.class);
    }

    /**
     * The skills tab is opened: the level's figure is worked out again, off the game thread, and
     * kept for the Merchant row to draw (as {@code SkillTab.prime} does).
     */
    private long look() throws Exception {
        RankUp rankUp = level();
        w.scheduler.submit(() -> {
            rankUp.profit = rankUp.levelProfit();
        }).get(20, TimeUnit.SECONDS);
        w.quiesce();
        return rankUp.profit;
    }

    /** The waits of what the scheduler is holding, in milliseconds, in the order it was asked. */
    private List<Long> waits() {
        List<Long> waits = new ArrayList<>();
        for (Later one : later) {
            waits.add(one.waitMs);
        }
        return waits;
    }

    /** The waits are over: what the scheduler was holding is run, on its own thread. */
    private void runLater() throws Exception {
        List<Later> due = new ArrayList<>(later);
        later.clear();
        for (Later one : due) {
            w.scheduler.submit(one.work).get(20, TimeUnit.SECONDS);
        }
        w.quiesce();
    }

    /**
     * Levelled per character, logged in as MAIN, the switch on, and the website holding 235 of
     * lifetime profit for the character: level 1. The tab shows the character.
     */
    private void perCharacterAt235() throws Exception {
        w.world.levelScope = PluginConfig.MerchantLevelScope.CHARACTER;
        w.characters.put(GeEvent.characterId(MAIN), "alt_all_time");
        w.login(MAIN);
        w.switchOn();
        w.openProfileTab();
        w.character(MAIN);
        w.settle();
        assertEquals(ALTS, level().levelProfit());
        assertTrue(w.world.chat.toString(), w.world.chat.isEmpty());
    }

    /** Linked, the switch on, Accountwide, All time, the whip's card open and its flip list open: two flips. */
    private void showTheWhipsFlips() throws Exception {
        w.login(MAIN);
        w.switchOn();
        w.openProfileTab();
        w.settle();
        assertEquals("402,078 gp", w.total());
        w.clickCard("Abyssal whip");
        w.clickFlipList();
        w.settle();
        assertEquals(1, w.flipsAsked().size());
        assertTrue(w.texts().toString(), w.texts().contains("Profit: 973 gp"));
    }

    /** Ten whips sold in the game on this computer just now, for 1,200,000, from one of its eight slots. */
    private void sellTenWhips(int slot) {
        w.world.offer(slot, 0, 0, 0L, GrandExchangeOfferState.EMPTY);
        w.world.offer(slot, 10, 0, 0L, GrandExchangeOfferState.SELLING);
        w.world.offer(slot, 10, 10, 1_200_000L, GrandExchangeOfferState.SOLD);
    }

    /**
     * The computer's own clock moves on a millisecond: what is timed by it after this is later
     * than what was timed by it before. At the same millisecond a sale counts as in the batch.
     */
    private static void aMoment() {
        long from = System.currentTimeMillis();
        while (System.currentTimeMillis() <= from) {
            Thread.onSpinWait();
        }
    }

    private int said(String words) {
        int lines = 0;
        for (String line : w.world.chat) {
            if (line.contains(words)) {
                lines++;
            }
        }
        return lines;
    }
}
