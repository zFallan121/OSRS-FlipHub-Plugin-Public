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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import okhttp3.Request;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The item cards under the Profile tab's total, their flip lists, and the kind of flip the total
 * and the list can be narrowed to, once the figures are the website's.
 *
 * <p>The website serves money and counts. It does not serve names (the plugin has the game's) or
 * how long a flip took (each computer keeps its own clock), and it serves one item's flips only
 * when asked for them.
 *
 * <p>This computer's own files hold three whip flips, 333 profit each, each held forty minutes.
 * The website's book has two whip flips, five items, and 402,078 from six flips.
 */
public class SiteFiguresCardsTest {
    private static final int DRAGON_BONES = 536;
    private static final int NATURE_RUNE = 561;
    private static final int SHARK = 385;
    private static final int DHAROKS_SET = 12877;
    private static final List<String> NAMES = Arrays.asList(
        "Dharok's armour set", "Abyssal whip", "Dragon bones", "Shark", "Nature rune");

    @Rule
    public final Timeout timeout = Timeout.seconds(120);

    private SiteFiguresWorld w;

    @Before
    public void setUp() throws Exception {
        w = SiteFiguresWorld.fresh();
        w.own(MAIN,
            flip(0, WHIP, 1, 1_000L, 1_333L, 300 * MINUTE, 40 * MINUTE),
            flip(1, WHIP, 1, 1_000L, 1_333L, 240 * MINUTE, 40 * MINUTE),
            flip(2, WHIP, 1, 1_000L, 1_333L, 180 * MINUTE, 40 * MINUTE));
        // The names the game has given the plugin so far this session.
        Map<Integer, String> names = w.world.state.getItemNameCache();
        names.put(WHIP, "Abyssal whip");
        names.put(DRAGON_BONES, "Dragon bones");
        names.put(NATURE_RUNE, "Nature rune");
        names.put(SHARK, "Shark");
        names.put(DHAROKS_SET, "Dharok's armour set");
        w.login(MAIN);
    }

    @After
    public void tearDown() throws Exception {
        SiteFiguresWorld.close(w);
    }

    // ---- this computer's own, for a player who is not linked ----

    /** What the whip's card says from the files alone: three flips, 999, forty minutes each. */
    @Test
    public void unlinkedTheCardIsThisComputersOwn() throws Exception {
        w.world.linked = false;
        w.switchOn();
        w.openProfileTab();
        w.settle();

        assertEquals(Arrays.asList("Abyssal whip"), cards());
        w.clickCard("Abyssal whip");

        assertEquals("999 gp", w.valueOf("Total profit").getText());
        assertEquals("3", w.valueOf("Quantity").getText());
        assertEquals("40m", w.valueOf("Avg time to complete").getText());
        w.clickFlipList();
        assertEquals("its own three flips", 3, count(w.texts(), "Profit: 333 gp"));
        assertTrue(w.world.website.asked.isEmpty());
    }

    // ---- the website's ----

    /**
     * Five items on the website, one on this computer: five cards, each with the website's money
     * and count and the game's name, newest sale first as the tab sorts them (the website sends
     * them most profit first).
     */
    @Test
    public void theCardsAreTheWebsitesItemsUnderTheGamesNames() throws Exception {
        showTheWebsitesFigures();

        assertEquals("newest sale first", NAMES, cards());
        List<String> texts = w.texts();
        assertTrue("the whip is the website's two flips at 19.48%, not this computer's three: " + texts,
            texts.contains("ROI 19.48% | x2"));
        assertTrue(texts.toString(), texts.contains("1,208 gp"));
        assertFalse(texts.toString(), texts.contains("999 gp"));
    }

    /**
     * "Avg time to complete" is this computer's own: its three whip flips took 120 minutes
     * between them, forty each, and it says forty whatever the website counts (two).
     */
    @Test
    public void avgTimeToCompleteIsThisComputersOwnWhateverTheWebsiteCounts() throws Exception {
        showTheWebsitesFigures();

        w.clickCard("Abyssal whip");

        assertEquals("the website's profit", "1,208 gp", w.valueOf("Total profit").getText());
        assertEquals("the website's quantity", "6", w.valueOf("Quantity").getText());
        assertEquals("120 minutes over this computer's three flips, not over the website's two",
            "40m", w.valueOf("Avg time to complete").getText());
    }

    /** An item this computer never traded has no time to give, and still has its name. */
    @Test
    public void anItemThisComputerNeverTradedHasNoTimeAndStillHasItsName() throws Exception {
        showTheWebsitesFigures();

        w.clickCard("Dharok's armour set");

        assertEquals("400,000 gp", w.valueOf("Total profit").getText());
        assertEquals("N/A", w.valueOf("Avg time to complete").getText());
    }

    // ---- one item's flips ----

    /**
     * Opening the whip's flip list asks the website for the whip's flips, once. They are shown
     * newest first: five sold for +973 (1,040 each to buy, 1,235 each kept), then one for +235.
     * Shutting the list and opening it again asks nothing.
     */
    @Test
    public void openingAnItemsFlipListAsksOnceAndShowsTheWebsitesFlipsNewestFirst() throws Exception {
        showTheWebsitesFigures();
        w.clickCard("Abyssal whip");

        w.clickFlipList();
        w.settle();

        assertEquals(1, w.flipsAsked().size());
        Request asked = w.flipsAsked().get(0);
        assertEquals("GET", asked.method());
        assertEquals("/api/plugin/figures/flips", asked.url().encodedPath());
        assertEquals("all time is no since_ms at all", new HashSet<>(Arrays.asList("scope", "item_id")),
            asked.url().queryParameterNames());
        assertEquals("account", asked.url().queryParameter("scope"));
        assertEquals("4151", asked.url().queryParameter("item_id"));
        assertEquals("session-token", asked.header("X-Plugin-Token"));
        assertNull(asked.body());

        List<String> texts = w.texts();
        int newest = texts.indexOf("Profit: 973 gp");
        int older = texts.indexOf("Profit: 235 gp");
        assertTrue("both of the website's flips: " + texts, newest >= 0 && older >= 0);
        assertTrue("newest first: " + texts, newest < older);
        assertEquals("and no flip of this computer's own", 0, count(texts, "Profit: 333 gp"));
        for (String shown : new String[] {"Qty: 5", "Buy: 1,040 gp", "Qty: 1", "Buy: 1,000 gp"}) {
            assertTrue(shown + " in " + texts, texts.contains(shown));
        }
        assertEquals("both sold for 1,235 each after tax", 2, count(texts, "Sell: 1,235 gp"));

        w.clickFlipList();
        assertEquals("shut", 0, count(w.texts(), "Profit: 973 gp"));
        w.clickFlipList();
        w.settle();
        assertEquals("opened again: the flips it has", 1, count(w.texts(), "Profit: 973 gp"));
        assertEquals("and nothing more asked", 1, w.flipsAsked().size());
    }

    /**
     * The flips asked for are those of the view on show: one character's, in the range the tab is
     * set to. (The website's answer for a character's range has no time for its untagged sales,
     * a null: the figures are shown all the same.)
     */
    @Test
    public void theFlipListIsAskedForAsTheTabIsSet() throws Exception {
        showTheWebsitesFigures();
        w.character(MAIN);
        w.range(StatsRange.LAST_7D);
        w.settle();
        assertEquals("401,653 gp", w.total());
        assertEquals("3", w.flipCount());

        w.clickCard("Abyssal whip");
        w.clickFlipList();
        w.settle();

        Request figures = w.figuresAsked().get(w.figuresAsked().size() - 1);
        Request flips = w.flipsAsked().get(w.flipsAsked().size() - 1);
        assertEquals("the character on show", GeEvent.characterId(MAIN), flips.url().queryParameter("scope"));
        assertEquals("4151", flips.url().queryParameter("item_id"));
        assertTrue("a range, so it has a start", flips.url().queryParameter("since_ms") != null);
        // To the minute: the tab's own clock may tick over between the two questions.
        assertTrue("the range on show",
            Math.abs(Long.parseLong(figures.url().queryParameter("since_ms"))
                - Long.parseLong(flips.url().queryParameter("since_ms"))) <= MINUTE);
        List<String> texts = w.texts();
        assertEquals("MAIN's one whip flip: " + texts, 1, count(texts, "Profit: 973 gp"));
        assertEquals("and not ALT's", 0, count(texts, "Profit: 235 gp"));
    }

    // ---- kinds ----

    /**
     * The total at the top can be one kind of flip's. The website splits its figures by kind:
     * 400,000 from one assembly, 1,988 from four flips, 90 from one repair. Tax stays the range's.
     */
    @Test
    public void theTotalOfOneKindIsTheWebsites() throws Exception {
        showTheWebsitesFigures();

        w.totalOf(StatsRecipeFilter.ASSEMBLE);
        assertEquals("400,000 gp", w.total());
        assertEquals("1", w.flipCount());
        assertEquals("400,000 over 4,500,000", "8.89%", w.roi());
        assertEquals("the range's tax, whole", "100,589 gp", w.tax());

        w.totalOf(StatsRecipeFilter.FLIP);
        assertEquals("1,988 gp", w.total());
        assertEquals("4", w.flipCount());
        assertEquals("1,988 over 26,602", "7.47%", w.roi());

        w.totalOf(StatsRecipeFilter.REPAIR);
        assertEquals("90 gp", w.total());
        assertEquals("1", w.flipCount());
        assertEquals("10.00%", w.roi());

        w.totalOf(StatsRecipeFilter.ANY_RECIPE);
        assertEquals("the assembly and the repair", "400,090 gp", w.total());
        assertEquals("2", w.flipCount());

        w.totalOf(StatsRecipeFilter.DISASSEMBLE);
        assertEquals("a kind the website has none of", "0 gp", w.total());
        assertEquals("0", w.flipCount());

        w.totalOf(StatsRecipeFilter.ALL);
        assertEquals("402,078 gp", w.total());
        assertEquals("6", w.flipCount());
    }

    /** The list can be narrowed to one kind too: an item shows when the website lists that kind among its flips. */
    @Test
    public void theListNarrowedToOneKindShowsTheItemsTheWebsiteGivesThatKind() throws Exception {
        showTheWebsitesFigures();

        w.listOnly(StatsRecipeFilter.REPAIR);
        assertEquals(Arrays.asList("Shark"), cards());
        assertTrue("with the money the website gives it: " + w.texts(), w.texts().contains("90 gp"));

        w.listOnly(StatsRecipeFilter.ASSEMBLE);
        assertEquals(Arrays.asList("Dharok's armour set"), cards());

        w.listOnly(StatsRecipeFilter.ANY_RECIPE);
        assertEquals(Arrays.asList("Dharok's armour set", "Shark"), cards());

        w.listOnly(StatsRecipeFilter.FLIP);
        assertEquals(Arrays.asList("Abyssal whip", "Dragon bones", "Nature rune"), cards());

        w.listOnly(StatsRecipeFilter.ALL);
        assertEquals(NAMES, cards());
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

    /** The item cards on show, by the name each is headed with, top to bottom. */
    private List<String> cards() throws Exception {
        List<String> cards = new ArrayList<>();
        for (String text : w.texts()) {
            if (NAMES.contains(text) || text.startsWith("Item ")) {
                cards.add(text);
            }
        }
        return cards;
    }

    private static int count(List<String> texts, String text) {
        int found = 0;
        for (String shown : texts) {
            if (shown.equals(text)) {
                found++;
            }
        }
        return found;
    }
}
