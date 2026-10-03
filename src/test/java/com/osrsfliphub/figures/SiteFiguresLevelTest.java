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

import net.runelite.api.GrandExchangeOfferState;
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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * The Merchant level of a linked player, once the website's switch is on: read from the website's
 * lifetime profit, and celebrated only for a sale made on this computer.
 *
 * <p>The owner's decisions of 2 October 2026. Accountwide, the level is the account's lifetime
 * profit as the website has it. Per character, it is the logged-in character's tagged sales and
 * nothing else: the sales no character can be given are not added. A level the website's figure
 * rises over is celebrated when a sale made on this computer since the last answer is what moved
 * it; any other rise (the other computer's sale, a repair, the switch itself) is silent, and is
 * still written down as the best level so that it is never celebrated later.
 *
 * <p>This computer's own files hold one flip, 999 profit, which is level 1.
 */
public class SiteFiguresLevelTest {
    private static final long ACCOUNT = 402_078L;
    private static final long MAINS = 401_653L;
    private static final long ALTS = 235L;

    @Rule
    public final Timeout timeout = Timeout.seconds(120);

    private SiteFiguresWorld w;

    @Before
    public void setUp() throws Exception {
        w = SiteFiguresWorld.fresh();
        w.own(MAIN, flip(0, WHIP, 10, 10_000L, 10_999L, 180 * MINUTE, 40 * MINUTE));
        w.world.celebrating = true;
        assertTrue("the website's figures and this computer's own are levels apart",
            FlipLevel.levelFor(MAINS) > FlipLevel.levelFor(999L) && FlipLevel.levelFor(MAINS) > FlipLevel.levelFor(ALTS));
    }

    @After
    public void tearDown() throws Exception {
        SiteFiguresWorld.close(w);
    }

    // ---- which figure ----

    /** 402,078 on the website against 999 in the files: the level is the level of 402,078. */
    @Test
    public void accountwideTheLevelIsTheLevelOfTheWebsitesLifetimeProfit() throws Exception {
        w.login(MAIN);
        assertEquals("until the website has said it is live", 999L, level().levelProfit());

        w.switchOn();
        w.openProfileTab();
        w.settle();

        assertEquals(ACCOUNT, level().levelProfit());
        assertEquals(FlipLevel.levelFor(ACCOUNT), FlipLevel.levelFor(level().levelProfit()));
        assertEquals("what the skills tab draws", ACCOUNT, level().profit);
    }

    /** The level is of the lifetime figure. A range on the tab narrows the tab, not the level. */
    @Test
    public void theLevelIsTheLifetimeFigureWhateverRangeTheTabShows() throws Exception {
        w.login(MAIN);
        w.switchOn();
        w.openProfileTab();
        w.settle();

        w.range(StatsRange.LAST_7D);
        w.settle();

        assertEquals("the range's profit", "401,888 gp", w.total());
        assertEquals("the lifetime profit", ACCOUNT, level().levelProfit());
    }

    /** MAIN's tagged sales made 401,653. The 190 from sales no character can be given is not added. */
    @Test
    public void perCharacterTheLevelIsTheLoggedInCharactersTaggedSalesOnly() throws Exception {
        w.world.levelScope = PluginConfig.MerchantLevelScope.CHARACTER;
        w.login(MAIN);
        w.switchOn();
        w.openProfileTab();
        w.character(MAIN);
        w.settle();

        assertEquals("401,653 gp", w.total());
        assertEquals("not 401,843 with the untagged sales, and not the account's 402,078", MAINS, level().levelProfit());
    }

    /**
     * Each character's level is its own, on the website as in the files: logging in on ALT never
     * shows MAIN's level, before the website has answered for ALT or after a restart.
     */
    @Test
    public void oneCharactersLifetimeIsNeverAnotherCharactersLevel() throws Exception {
        w.own(ALT, flip(1, WHIP, 1, 1_000L, 1_100L, 120 * MINUTE, 10 * MINUTE));
        w.world.levelScope = PluginConfig.MerchantLevelScope.CHARACTER;
        w.login(MAIN);
        w.switchOn();
        w.openProfileTab();
        w.character(MAIN);
        w.settle();
        assertEquals(MAINS, level().levelProfit());

        w.world.logout();
        w.login(ALT);
        assertNotEquals("ALT is not MAIN", MAINS, level().levelProfit());
        w.character(ALT);
        w.settle();
        assertEquals(ALTS, level().levelProfit());

        // Started again, out of reach of the website: what each character's level was is remembered.
        w = w.again();
        w.world.levelScope = PluginConfig.MerchantLevelScope.CHARACTER;
        w.unreachable();
        w.login(ALT);
        assertEquals("ALT's own, remembered", ALTS, level().levelProfit());
        w.world.logout();
        w.login(MAIN);
        assertEquals("MAIN's own, remembered", MAINS, level().levelProfit());
    }

    /**
     * The game gives many characters a negative account hash, and the plugin files those under a
     * key made from the name. The tag asked for is that key's, the one its uploads carry.
     */
    @Test
    public void aCharacterFiledUnderItsNameIsLevelledByItsOwnTag() throws Exception {
        long filedAs = RecordSyncWorld.nameKey("XP V");
        w.own(filedAs, flip(0, WHIP, 1, 1_000L, 1_100L, 120 * MINUTE, 10 * MINUTE));
        w.characters.put(GeEvent.characterId(filedAs), "alt_all_time");
        w.world.levelScope = PluginConfig.MerchantLevelScope.CHARACTER;
        w.login(-8_445_759_109_730_976_879L, "XP V");
        w.switchOn();
        w.openProfileTab();
        w.character(filedAs);
        w.settle();

        assertTrue("the website was asked", !w.figuresAsked().isEmpty());
        Request asked = w.figuresAsked().get(w.figuresAsked().size() - 1);
        assertEquals(GeEvent.characterId(filedAs), asked.url().queryParameter("scope"));
        assertEquals(ALTS, level().levelProfit());
    }

    /** Started again, the level is the one the website last gave: not, until it answers, the files' own. */
    @Test
    public void afterARestartTheLevelIsTheRememberedOneNotThisComputersOwn() throws Exception {
        w.login(MAIN);
        w.switchOn();
        w.openProfileTab();
        w.settle();
        assertEquals(ACCOUNT, level().levelProfit());

        w = w.again();
        w.unreachable();
        w.login(MAIN);

        assertEquals(ACCOUNT, level().levelProfit());
    }

    // ---- nothing changes for a player who is not linked, or before the switch ----

    @Test
    public void unlinkedTheLevelIsThisComputersOwnAndNothingIsAsked() throws Exception {
        w.world.linked = false;
        w.switchOn();
        w.login(MAIN);
        w.openProfileTab();
        w.settle();

        assertEquals(999L, level().levelProfit());
        assertTrue(w.world.website.asked.isEmpty());
    }

    @Test
    public void beforeTheSwitchTheLevelIsThisComputersOwn() throws Exception {
        w.switchOff();
        w.login(MAIN);
        w.openProfileTab();
        w.settle();

        assertEquals(999L, level().levelProfit());
    }

    /** Before the switch a sale that crosses a level is celebrated when it is made, from the files, as it always was. */
    @Test
    public void beforeTheSwitchASaleIsCelebratedAsItAlwaysWas() throws Exception {
        w.switchOff();
        w.login(MAIN);
        w.openProfileTab();
        w.settle();

        sellTenWhipsAtAProfit();
        w.upload();

        long own = level().levelProfit();
        assertTrue("the sale made a profit: " + own, FlipLevel.levelFor(own) > FlipLevel.levelFor(999L));
        assertEquals(w.world.chat.toString(), 1, said("You are now level " + FlipLevel.levelFor(own) + "."));
        assertEquals(1, said("You are now level "));
    }

    /** Unlinked again, the level is the files' own again: nothing the website said is left. */
    @Test
    public void afterUnlinkingTheLevelIsThisComputersOwnAgain() throws Exception {
        w.login(MAIN);
        w.switchOn();
        w.openProfileTab();
        w.settle();
        assertEquals(ACCOUNT, level().levelProfit());

        w.world.linked = false;
        try {
            w.world.injector.getInstance(LinkAttempt.class).performUnlink();
        } catch (RuntimeException standIn) {
            // The config standing in here cannot write RuneLite's file out, which an unlink does part way.
        }
        w.show();

        assertEquals(999L, level().levelProfit());
        assertEquals(999L, level().profit);
    }

    // ---- celebrations ----

    /**
     * The day the switch is thrown the level moves from the files' figure to the website's, with
     * no sale behind it. Nothing is celebrated, and the level it lands on is written down as the
     * best, so no later sale can be congratulated for reaching it.
     */
    @Test
    public void onSwitchDayTheLevelMovesToTheWebsitesWithoutACelebration() throws Exception {
        w.login(MAIN);
        w.switchOn();
        w.openProfileTab();
        w.settle();

        assertEquals(ACCOUNT, level().levelProfit());
        assertTrue("silent: " + w.world.chat, w.world.chat.isEmpty());
        assertEquals("on record as the best level", FlipLevel.levelFor(ACCOUNT), RankUp.best(RankUp.BEST_KEY));
    }

    /**
     * The website's figure for the character rises from 235 to 401,653 between two answers, with
     * no sale on this computer in between: its other computer made the sales. Silent, on record
     * as the best, and so not celebrated by this computer's next sale either.
     */
    @Test
    public void aRiseWithNoSaleOnThisComputerIsSilentAndStillOnRecordAsTheBest() throws Exception {
        perCharacterAt235();

        w.characters.put(GeEvent.characterId(MAIN), "main_all_time");
        w.pass(61 * SECOND);
        w.show();

        assertEquals(MAINS, level().levelProfit());
        assertTrue("silent: " + w.world.chat, w.world.chat.isEmpty());
        assertEquals("on record as the best level", FlipLevel.levelFor(MAINS), RankUp.best("bestFlipLevel_" + MAIN));

        w.pass(30 * SECOND);
        sellTenWhipsAtAProfit();
        w.upload();
        assertEquals(MAINS, level().levelProfit());
        assertTrue("a sale that crosses nothing, on a level already on record: " + w.world.chat, w.world.chat.isEmpty());
    }

    /**
     * A sale on this computer; its upload is accepted; the website's answer is over a level. One
     * celebration, of the level the website's figure is on, with the Profile tab shut as it
     * usually is while flipping. The next answer, with no new sale, celebrates nothing.
     */
    @Test
    public void aSaleOnThisComputerThenAnAnswerOverALevelCelebratesOnce() throws Exception {
        perCharacterAt235();
        w.openActivityTab();
        w.pass(30 * SECOND);

        sellTenWhipsAtAProfit();
        // The website pairs the sale: the character's lifetime profit is now 401,653.
        w.characters.put(GeEvent.characterId(MAIN), "main_all_time");
        w.upload();

        int reached = FlipLevel.levelFor(MAINS);
        assertEquals(MAINS, level().levelProfit());
        assertEquals(w.world.chat.toString(), 1, said("You are now level " + reached + "."));
        assertEquals("and no other level: " + w.world.chat, 1, said("You are now level "));
        assertEquals(reached, RankUp.best("bestFlipLevel_" + MAIN));

        w.pass(61 * SECOND);
        w.openProfileTab();
        w.settle();
        assertEquals("the next answer, no new sale: " + w.world.chat, 1, said("You are now level "));
    }

    /**
     * A sale on this computer whose answer crosses nothing; then, a minute later, an answer that
     * is over a level with no sale since the one before it. That rise is somebody else's: silent.
     */
    @Test
    public void aRiseInALaterAnswerWithNoNewSaleIsSilent() throws Exception {
        perCharacterAt235();
        w.openActivityTab();
        w.pass(30 * SECOND);

        sellTenWhipsAtAProfit();
        w.upload();
        assertEquals("the sale's own answer: still 235", ALTS, level().levelProfit());
        assertTrue(w.world.chat.toString(), w.world.chat.isEmpty());

        w.characters.put(GeEvent.characterId(MAIN), "main_all_time");
        w.pass(61 * SECOND);
        w.openProfileTab();
        w.settle();

        assertEquals(MAINS, level().levelProfit());
        assertTrue("no sale on this computer since the last answer: " + w.world.chat, w.world.chat.isEmpty());
        assertEquals(FlipLevel.levelFor(MAINS), RankUp.best("bestFlipLevel_" + MAIN));
    }

    // ---- helpers ----

    private RankUp level() {
        return w.world.injector.getInstance(RankUp.class);
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

    /** A flip made in the game on this computer just now: ten whips bought at 100,000 each and sold for 1,200,000. */
    private void sellTenWhipsAtAProfit() throws Exception {
        w.world.buyTenWhips(2);
        w.world.offer(3, 0, 0, 0L, GrandExchangeOfferState.EMPTY);
        w.world.offer(3, 10, 0, 0L, GrandExchangeOfferState.SELLING);
        w.world.offer(3, 10, 10, 1_200_000L, GrandExchangeOfferState.SOLD);
        w.quiesce();
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
