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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.osrsfliphub.RecordSyncWorld.bought;
import static com.osrsfliphub.RecordSyncWorld.records;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The old catch-up's "done" marks and its give-up rule, as found on 2 October 2026, and what
 * replaced them. Both began as tests that failed against the code of that day.
 */
public class CompletenessFlagPathsTest {
    private static final long MAIN = 4242L;
    private static final long ALT = 777L;
    private static final long MINUTE = 60_000L;

    private RecordSyncWorld world;

    @Before
    public void setUp() throws Exception {
        world = RecordSyncWorld.fresh();
    }

    @After
    public void tearDown() throws Exception {
        RecordSyncWorld.close(world);
    }

    /**
     * A character once sent was marked as sent for good: not an unlink, not a rejected session,
     * not a new link cleared the mark. The next link may be to another website account, which then
     * never received that character's history. A link made through the Link tab now forgets every
     * confirmation, and both characters' trades go to the account it was made to.
     */
    @Test
    public void aLinkToAnotherAccountSendsItEveryCharactersHistory() throws Exception {
        world.store(MAIN, bought(30 * MINUTE, 1, 10), bought(20 * MINUTE, 2, 5));
        world.store(ALT, bought(25 * MINUTE, 1, 3));
        assertEquals(3, records(world.login(MAIN).settle()).size());
        assertTrue("the first account has it all", world.settle().isEmpty());
        Thread.sleep(5L);

        // The player unlinks, and links from the Link tab with another account's key. (The link
        // itself is the real one up to the moment it writes RuneLite's config file out, which the
        // config standing in here cannot do; what a link forgets, it has forgotten by then.)
        world.linked = false;
        assertTrue("unlinked, nothing is sent", world.settle().isEmpty());
        world.website.held.clear();
        world.injector.getInstance(LinkAttempt.class).linkFromPanel("another-accounts-key");
        world.linked = true;
        Thread.sleep(5L);

        List<GeEvent> sent = records(world.settle());

        assertEquals("with the link gone, what it confirmed went with it", 3, sent.size());
        assertEquals(3, world.website.held.size());
        assertTrue("and that account's confirmations are remembered in turn", world.again().login(MAIN).settle().isEmpty());
    }

    /**
     * A character's history went up in batches of 200 and stopped at the first the website would
     * not take; a refusal then marked the character as done, the later batches never tried. Now a
     * refused batch confirms nothing and ends that pass, and no character is ever marked done:
     * the next pass sends the same trades again, and all of them go once the website takes them.
     */
    @Test
    public void aBatchTheWebsiteRefusedDoesNotEndTheRestOfTheUpload() {
        Delta[] trades = new Delta[450];
        for (int i = 0; i < trades.length; i++) {
            trades[i] = bought((60 + i) * MINUTE, i % 8, 10);
        }
        world.store(MAIN, trades);
        AtomicInteger calls = new AtomicInteger();
        // The first batch is refused outright; every later one would be taken.
        world.website.answer = events -> calls.getAndIncrement() == 0
            ? RecordSyncWorld.Website.status(400) : world.website.takeAll(events);

        assertEquals("the refused batch", 1, world.login(MAIN).settle().size());
        world.waitOutThePause();
        List<List<GeEvent>> sent = world.settle();

        assertEquals("450 records are three batches, and the refused one goes again with the rest", 3, sent.size());
        assertEquals(450, world.website.held.size());
        for (Delta trade : world.stored(MAIN)) {
            assertTrue(trade.uploadedMs > 0L);
        }
    }
}
