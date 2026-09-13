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
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Picking up a part-finished upload of a profile's history.
 *
 * <p>This used to be a count of how many events had been sent, which only holds while the
 * list keeps the same shape. It does not: the list is rebuilt from the profile on every
 * attempt, and the in-game history sync inserts trades with older timestamps, which land
 * before the mark and shift everything after it. The next attempt then skipped exactly that
 * many events, and once the profile was eventually marked done nothing ever sent them.
 */
public class BackfillResumeTest {
    @Test
    public void withNothingSentYetItStartsAtTheBeginning() {
        assertEquals(0, ProfileBackfill.resumePoint(null, events("a", "b", "c")));
    }

    @Test
    public void itResumesJustAfterTheLastEventTheServerTook() {
        assertEquals(2, ProfileBackfill.resumePoint("b", events("a", "b", "c", "d")));
    }

    /**
     * The case the count got wrong. Two older trades arrive from the in-game history sync and
     * sort ahead of everything, so what was event two is now event four. A count of two would
     * resume at "x", skipping the two events that moved down. The identity finds it.
     */
    @Test
    public void anInsertionAheadOfTheMarkDoesNotSkipAnything() {
        List<GeEvent> rebuilt = events("older-1", "older-2", "a", "b", "c", "d");

        assertEquals(4, ProfileBackfill.resumePoint("b", rebuilt));
    }

    /** Everything was taken, so there is nothing left to send. */
    @Test
    public void aFullyAcceptedProfileResumesPastTheEnd() {
        List<GeEvent> all = events("a", "b", "c");

        assertEquals(3, ProfileBackfill.resumePoint("c", all));
    }

    /**
     * The trade the mark named is no longer in the list. Starting over re-sends events the
     * server already has, which it throws away by id; the other direction loses them.
     */
    @Test
    public void aMarkThatNoLongerExistsStartsOver() {
        assertEquals(0, ProfileBackfill.resumePoint("gone", events("a", "b", "c")));
    }

    @Test
    public void anEmptyListHasNowhereToResumeTo() {
        assertEquals(0, ProfileBackfill.resumePoint("a", new ArrayList<>()));
        assertEquals(0, ProfileBackfill.resumePoint("a", null));
    }

    /**
     * A batch can come back as accepted with every event in it thrown away. Both upload paths
     * ask this one question about it now; they used to give opposite answers.
     */
    @Test
    public void aBatchTheServerKeptNothingOfIsNotASuccess() {
        assertFalse(ApiStatusPolicy.keptSomething(upload(0, 0, 2), 2));
        assertTrue(ApiStatusPolicy.keptSomething(upload(2, 0, 0), 2));
        assertTrue(ApiStatusPolicy.keptSomething(upload(0, 2, 0), 2));
        assertTrue(ApiStatusPolicy.keptSomething(upload(1, 0, 1), 2));
        // An older server that reports no counts at all is taken at its word.
        assertTrue(ApiStatusPolicy.keptSomething(upload(null, null, null), 2));
        assertFalse(ApiStatusPolicy.keptSomething(null, 2));
        assertFalse(ApiStatusPolicy.keptSomething(upload(2, 0, 0), 0));
    }

    private static ApiClient.EventUploadResponse upload(Integer accepted, Integer duplicates, Integer rejected) {
        ApiClient.EventUploadResponse response = new ApiClient.EventUploadResponse();
        response.status_code = 200;
        response.accepted = accepted;
        response.duplicates = duplicates;
        response.rejected = rejected;
        return response;
    }

    private static List<GeEvent> events(String... ids) {
        List<GeEvent> events = new ArrayList<>();
        for (String id : ids) {
            GeEvent event = new GeEvent();
            event.event_id = id;
            events.add(event);
        }
        return events;
    }
}
