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
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BookmarkConfigStoreTest {
    private static final long ACCOUNTWIDE_KEY = 0L;

    @Test
    public void configKeyRecognitionMatchesBookmarkKeyPatterns() {
        BookmarkConfigStore store = new BookmarkConfigStore(ACCOUNTWIDE_KEY);

        assertTrue(store.isBookmarksConfigKey("bookmarks"));
        assertTrue(store.isBookmarksConfigKey("bookmarks_123"));
        assertTrue(store.isBookmarksConfigKey(" bookmarks_456 "));
        assertTrue(store.isBookmarksConfigKey("bookmarks_invalid"));

        assertFalse(store.isBookmarksConfigKey(null));
        assertFalse(store.isBookmarksConfigKey(""));
        assertFalse(store.isBookmarksConfigKey("hiddenItems"));
    }

    @Test
    public void parseProfileKeyParsesAccountwideAndNumericProfileSuffix() {
        BookmarkConfigStore store = new BookmarkConfigStore(ACCOUNTWIDE_KEY);

        assertEquals(Long.valueOf(ACCOUNTWIDE_KEY), store.parseProfileKey("bookmarks"));
        assertEquals(Long.valueOf(42L), store.parseProfileKey("bookmarks_42"));
        assertEquals(Long.valueOf(77L), store.parseProfileKey(" bookmarks_77 "));

        assertNull(store.parseProfileKey("bookmarks_0"));
        assertNull(store.parseProfileKey("bookmarks_-3"));
        assertNull(store.parseProfileKey("bookmarks_abc"));
        assertNull(store.parseProfileKey("hiddenItems"));
    }

    @Test
    public void buildConfigKeyNormalizesAccountwideAndPositiveProfileKeys() {
        BookmarkConfigStore store = new BookmarkConfigStore(ACCOUNTWIDE_KEY);

        assertEquals("bookmarks", store.buildConfigKey(0L));
        assertEquals("bookmarks", store.buildConfigKey(-10L));
        assertEquals("bookmarks_99", store.buildConfigKey(99L));
    }

    @Test
    public void parseAndSerializeItemIdsNormalizeDuplicatesAndOrdering() {
        BookmarkConfigStore store = new BookmarkConfigStore(ACCOUNTWIDE_KEY);

        Set<Integer> parsed = store.parseItemIds(" 5,1,5,0,-2,abc,3 ");
        assertEquals(new HashSet<>(Arrays.asList(1, 3, 5)), parsed);
        assertEquals("1,3,5", store.serializeItemIds(parsed));
        assertEquals("", store.serializeItemIds(new HashSet<>()));
    }
}
