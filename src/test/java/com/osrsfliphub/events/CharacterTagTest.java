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
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The tag that tells the website which character a trade belongs to.
 *
 * <p>The website keeps one queue of purchases per item and character, and takes a sale only from
 * its own character's. The tag is all it has to go on, so it must be the same for a character
 * every time and on every computer, different for every other character, and say nothing else.
 */
public class CharacterTagTest {
    private static final long SIPS = 13886278L;
    private static final long GPOVERXP = 1268031243L;

    @Test
    public void aCharacterAlwaysHasTheSameTag() {
        assertEquals(GeEvent.characterId(SIPS), GeEvent.characterId(SIPS));
    }

    @Test
    public void twoCharactersNeverShareOne() {
        assertNotEquals(GeEvent.characterId(SIPS), GeEvent.characterId(GPOVERXP));
        assertNotEquals(GeEvent.characterId(1L), GeEvent.characterId(2L));
    }

    @Test
    public void anUnknownCharacterHasNone() {
        assertNull(GeEvent.characterId(0L));
        assertNull(GeEvent.characterId(-1L));
    }

    @Test
    public void itIsWhatTheWebsiteAcceptsAndDoesNotCarryTheAccountHash() {
        String tag = GeEvent.characterId(SIPS);

        // The website keeps a tag of up to 64 lower-case letters, digits and hyphens, and drops anything else.
        assertTrue(tag, tag.matches("[0-9a-f-]{36}"));
        assertFalse(tag.contains(Long.toString(SIPS)));
    }

    @Test
    public void aTradeReplayedFromAProfileIsTaggedWithThatProfilesCharacter() {
        // Backfill and the history sync upload other characters' trades while someone else is logged in.
        Delta delta = new Delta(5_000L, 3, 31406, true, 182, 2_239_692L, "OFFER_UPDATED", 12_306, false);

        GeEvent event = new BackfillUploader().buildBackfillEvent(GPOVERXP, delta, 301);

        assertEquals(GeEvent.characterId(GPOVERXP), event.character_id);
    }

    @Test
    public void anEventWithNoCharacterIsSentWithoutTheField() {
        assertFalse(new Gson().toJson(new GeEvent()).contains("character_id"));
    }
}
