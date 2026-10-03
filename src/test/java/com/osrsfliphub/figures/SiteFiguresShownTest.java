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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JLabel;
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
import static org.junit.Assert.assertTrue;

/**
 * Whose figures the Profile tab shows: this computer's own sums, or the website's.
 *
 * <p>The website works out every money figure, and once its switch is on a linked plugin shows
 * what it is sent and never a sum of its own. Until then, and for a player who is not linked,
 * the tab is exactly what it was.
 *
 * <p>Throughout, this computer's own files hold one flip: ten whips bought for 10,000 and sold
 * for 10,999 after tax, 999 profit. The website's book holds 402,078 from six flips.
 */
public class SiteFiguresShownTest {
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

    // ---- nothing changes for a player who is not linked, or before the switch ----

    /** A player who never linked is asked nothing and shown nothing new, whatever the website would say. */
    @Test
    public void anUnlinkedPlayerSeesTheirOwnSumsAndTheWebsiteIsNeverAsked() throws Exception {
        w.world.linked = false;
        w.switchOn();

        w.openProfileTab();
        w.settle();
        w.pass(5 * MINUTE);
        w.show();

        assertOwnSums("unlinked");
        assertTrue("nothing is asked of the website", w.world.website.asked.isEmpty());
        assertNoStatus();
    }

    /**
     * Today: the website answers {@code {"live": false}}. The tab shows the computer's own sums
     * from the first moment, with no dashes on the way, and goes on doing so.
     */
    @Test
    public void beforeTheSwitchALinkedPlayerSeesTheirOwnSums() throws Exception {
        w.switchOff();

        w.openProfileTab();
        assertOwnSums("before the website has answered at all");
        w.settle();

        assertOwnSums("the website says it is not live");
        assertFalse("the website was asked", w.figuresAsked().isEmpty());
        assertNoStatus();
        w.pass(61 * SECOND);
        w.show();
        assertOwnSums("a minute later");
    }

    // ---- the switch on ----

    /** The owner's scenario: the files say 999 and one flip, the website 402,078 and six. */
    @Test
    public void withTheSwitchOnTheTabShowsTheWebsitesFiguresNotThisComputersOwn() throws Exception {
        w.switchOn();

        w.openProfileTab();
        w.settle();

        assertEquals("402,078 gp", w.total());
        assertEquals("6", w.flipCount());
        assertEquals("8.88%", w.roi());
        assertEquals("100,589 gp", w.tax());
        assertNoStatus();
    }

    /**
     * A view the website has not answered for yet has no figure: dashes, and a quiet line saying
     * it is asking. Never the computer's own sum for that view, which is a different number.
     */
    @Test
    public void aViewNotAnsweredYetShowsDashesAndSaysItIsAsking() throws Exception {
        showTheWebsitesFigures();

        w.range(StatsRange.LAST_7D);

        assertNoFigure("the website has not answered for the last seven days yet");
        JLabel asking = w.labelSaying("asking");
        assertNotNull("says it is asking: " + w.texts(), asking);
        assertTrue("a note, not a caution: muted",
            Skin.MUTED.equals(asking.getForeground()) || Skin.MUTED_2.equals(asking.getForeground()));

        w.settle();
        assertEquals("the website's figure for the range", "401,888 gp", w.total());
        assertEquals("4", w.flipCount());
        assertNull("nothing left to say", w.labelSaying("asking"));
    }

    /** The website cannot be reached: the last answer stays, with the time it is from, in amber. */
    @Test
    public void aFailedFetchKeepsTheLastAnswerAndSaysWhatTimeItIsFrom() throws Exception {
        showTheWebsitesFigures();
        String answeredAt = SiteFiguresWorld.clockTime(w.now());

        w.unreachable();
        w.pass(61 * SECOND);
        w.show();

        assertEquals("the answer it has", "402,078 gp", w.total());
        assertEquals("6", w.flipCount());
        assertEquals("it did ask again", 2, w.figuresAsked().size());
        JLabel from = w.labelSaying("from " + answeredAt);
        assertNotNull("says the figures are from " + answeredAt + ": " + w.texts(), from);
        assertEquals("a caution: amber", Skin.WARNING, from.getForeground());

        w.switchOn();
        w.pass(61 * SECOND);
        w.show();
        assertEquals("402,078 gp", w.total());
        assertNull("answered again, so nothing to say", w.labelSaying("from " + answeredAt));
    }

    /** A question the website refuses (a 400) is a fetch that failed like any other: the last answer stays. */
    @Test
    public void aRefusedQuestionKeepsTheLastAnswerToo() throws Exception {
        showTheWebsitesFigures();
        String answeredAt = SiteFiguresWorld.clockTime(w.now());

        w.world.website.figures = request -> FigureFixtures.refusal("bad_since");
        w.pass(61 * SECOND);
        w.show();

        assertEquals("402,078 gp", w.total());
        assertEquals("6", w.flipCount());
        assertNotNull("says the figures are from " + answeredAt + ": " + w.texts(), w.labelSaying("from " + answeredAt));
    }

    /**
     * The switch is turned off again (a rollback). The next answer is {@code {"live": false}}:
     * the computer's own sums come back, for every view, and nothing the website said is kept,
     * not even over a restart.
     */
    @Test
    public void whenTheSwitchGoesOffThisComputersOwnSumsComeBackAndNothingIsRemembered() throws Exception {
        showTheWebsitesFigures();
        w.range(StatsRange.LAST_7D);
        w.settle();
        assertEquals("401,888 gp", w.total());

        w.switchOff();
        w.pass(61 * SECOND);
        w.show();
        assertOwnSums("the website says it is not live any more");

        w.range(StatsRange.ALL_TIME);
        assertOwnSums("the answer it had for All time is forgotten too");
        w.settle();
        assertOwnSums("and stays forgotten");
        assertNoStatus();

        w = w.again();
        w.switchOff();
        w.login(MAIN);
        w.openProfileTab();
        assertOwnSums("after a restart: no dashes, it does not remember the website being live");
    }

    // ---- a restart ----

    /**
     * The plugin remembers that the website is live. Started again, the tab shows dashes until the
     * website answers: not, for a second, the computer's own 999.
     */
    @Test
    public void afterARestartTheTabShowsDashesThenTheFiguresNeverThisComputersOwnSum() throws Exception {
        showTheWebsitesFigures();

        w = w.again();
        w.switchOn();
        w.login(MAIN);
        w.openProfileTab();

        assertNoFigure("started again, not answered yet");
        assertNotNull("says it is asking: " + w.texts(), w.labelSaying("asking"));
        w.settle();
        assertEquals("402,078 gp", w.total());
        assertEquals("6", w.flipCount());
    }

    /** Started again with the website out of reach: still no sum of the computer's own. */
    @Test
    public void afterARestartWithTheWebsiteOutOfReachThereIsStillNoSumOfThisComputersOwn() throws Exception {
        showTheWebsitesFigures();

        w = w.again();
        w.unreachable();
        w.login(MAIN);
        w.openProfileTab();
        w.settle();

        assertNoFigure("the website is live as far as the plugin knows, and has not answered");
    }

    // ---- two characters ----

    /**
     * One character's view is that character's tagged sales, with the sales no character can be
     * given on a row of their own. Switching to another character shows dashes until the website
     * answers for that one: never the first character's figures.
     */
    @Test
    public void oneCharactersFiguresAreNeverShownForAnother() throws Exception {
        w.own(ALT, flip(1, WHIP, 1, 1_000L, 1_100L, 120 * MINUTE, 10 * MINUTE));
        showTheWebsitesFigures();

        w.character(MAIN);
        assertNoFigure("MAIN has not been answered for yet");
        w.settle();
        assertEquals("MAIN's tagged sales", "401,653 gp", w.total());
        assertEquals("3", w.flipCount());
        JLabel untagged = w.valueOf("Before 22 Sep 2026");
        assertNotNull("the sales no character can be given have a row: " + w.texts(), untagged);
        assertEquals("190 gp", untagged.getText());
        assertEquals("a profit: green, on the value", Skin.SUCCESS, untagged.getForeground());
        assertEquals("the label stays muted", Skin.MUTED, w.labelSaying("Before 22 Sep 2026").getForeground());

        w.character(ALT);
        assertNoFigure("ALT has not been answered for yet, and MAIN's figures are not ALT's");
        w.settle();
        assertEquals("235 gp", w.total());
        assertEquals("1", w.flipCount());
        assertEquals("the same untagged sales beside every character", "190 gp", w.valueOf("Before 22 Sep 2026").getText());

        w.accountwide();
        w.settle();
        assertEquals("402,078 gp", w.total());
        assertNull("the whole account has no such row: they are in its total", w.labelSaying("Before 22 Sep 2026"));
        assertEquals("asked once each, by the tag each character's uploads carry",
            Arrays.asList("account", GeEvent.characterId(MAIN), GeEvent.characterId(ALT)), scopesAsked());
    }

    // ---- forgetting ----

    /** Unlinked: the tab is the computer's own again, and the website is not asked another thing. */
    @Test
    public void afterUnlinkingThisComputersOwnSumsShowAndNothingMoreIsAsked() throws Exception {
        showTheWebsitesFigures();
        int asked = w.world.website.asked.size();

        unlink();
        w.pass(61 * SECOND);
        w.show();

        assertOwnSums("unlinked");
        assertNoStatus();
        assertEquals("nothing more is asked", asked, w.world.website.asked.size());
    }

    /**
     * A new link may be to another website account. Nothing the last link was told is shown under
     * it, or remembered for it: not now, and not after a restart before the website has answered.
     * (As with what a link forgets of its confirmed trades, it is forgotten before the new
     * session is stored.)
     */
    @Test
    public void aNewLinkNeverShowsTheLastLinksFigures() throws Exception {
        showTheWebsitesFigures();
        unlink();

        w.unreachable();
        link("another-session");
        w.pass(SECOND);
        w.refresh();
        assertOwnSums("linked again, not answered: the last link's 402,078 is gone");
        w.settle();
        assertOwnSums("and the website cannot be reached");

        w = w.again();
        w.world.session = "another-session";
        w.unreachable();
        w.login(MAIN);
        w.openProfileTab();
        w.settle();
        assertOwnSums("after a restart: the last link's being live is not remembered for this one");

        w.switchOn();
        w.pass(61 * SECOND);
        w.show();
        Request last = w.figuresAsked().get(w.figuresAsked().size() - 1);
        assertEquals("asked under the new link", "another-session", last.header("X-Plugin-Token"));
    }

    /** The website refused the session, so the plugin cleared it: its figures go with it. */
    @Test
    public void aSessionTheWebsiteRefusedLeavesNoneOfItsFiguresShowing() throws Exception {
        showTheWebsitesFigures();
        int asked = w.world.website.asked.size();

        w.world.session = "";
        try {
            w.world.injector.getInstance(SessionRefresh.class).clearSession();
        } catch (RuntimeException standIn) {
            // The config standing in here cannot write RuneLite's file out, which is the session's last step.
        }
        w.pass(61 * SECOND);
        w.show();

        assertOwnSums("no session");
        assertEquals("nothing more is asked", asked, w.world.website.asked.size());
    }

    /**
     * "Wipe website statistics": what the website said before it is no longer true. The old
     * figures go at once, and the website is asked again without waiting out the minute.
     */
    @Test
    public void afterAWebsiteWipeTheOldFiguresAreGoneAndItAsksAgain() throws Exception {
        showTheWebsitesFigures();
        int asked = w.figuresAsked().size();
        w.pass(30 * SECOND);
        // Out of reach for figures, so that what shows next is what the plugin kept, not a new answer.
        w.unreachable();

        w.world.injector.getInstance(WebsiteStatsWipe.class).wipeWebsiteStatsAsync();
        w.settle();

        assertNotEquals("the figures from before the wipe", "402,078 gp", w.total());
        assertTrue("asked again, half a minute after the last answer", w.figuresAsked().size() > asked);
    }

    // ---- helpers ----

    /** Linked, the switch on, Accountwide, All time: the tab shows the website's 402,078 from six flips. */
    private void showTheWebsitesFigures() throws Exception {
        w.switchOn();
        w.openProfileTab();
        w.settle();
        assertEquals("402,078 gp", w.total());
        assertEquals("6", w.flipCount());
    }

    private void unlink() {
        w.world.linked = false;
        try {
            w.world.injector.getInstance(LinkAttempt.class).performUnlink();
        } catch (RuntimeException standIn) {
            // The config standing in here cannot write RuneLite's file out, which an unlink does part way.
        }
    }

    /** Links from the Link tab with another account's key: the website gives a session, and the plugin takes it. */
    private void link(String session) {
        // Linking from the panel turns the sync setting on itself; the config standing in here is told.
        w.world.linked = true;
        w.world.session = session;
        w.world.injector.getInstance(LinkAttempt.class).linkFromPanel("another-accounts-key");
        w.answer();
    }

    private void assertOwnSums(String when) throws Exception {
        assertEquals(when + ": this computer's own profit", "999 gp", w.total());
        assertEquals(when + ": this computer's own flips", "1", w.flipCount());
    }

    private void assertNoFigure(String when) throws Exception {
        for (String shown : new String[] {w.total(), w.roi(), w.flipCount(), w.tax()}) {
            assertTrue(when + ", so dashes and no figure, but the tab shows " + shown, SiteFiguresWorld.dashes(shown));
        }
    }

    /** No line about the website at all: the tab as it always was. */
    private void assertNoStatus() throws Exception {
        assertNull(w.texts().toString(), w.labelSaying("asking"));
        assertNull(w.texts().toString(), w.labelSaying("figures from"));
        assertNull(w.texts().toString(), w.labelSaying("Before 22 Sep 2026"));
    }

    private List<String> scopesAsked() {
        List<String> scopes = new ArrayList<>();
        for (Request request : w.figuresAsked()) {
            scopes.add(request.url().queryParameter("scope"));
        }
        return scopes;
    }
}
