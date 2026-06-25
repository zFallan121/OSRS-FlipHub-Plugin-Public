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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BookmarkStateServiceTest {
    private static final long ACCOUNTWIDE_KEY = BookmarkSyncService.ACCOUNTWIDE_KEY;

    @Test
    public void reloadFromConfigKeyInvalidatesCachedProfileSet() {
        TestHooks hooks = new TestHooks();
        hooks.rawByProfile.put(ACCOUNTWIDE_KEY, "1,2");
        BookmarkStateService service = service(hooks);
        Set<Integer> selected = new HashSet<>();

        service.loadSelectedBookmarks(ACCOUNTWIDE_KEY, selected);
        assertEquals(setOf(1, 2), selected);

        hooks.rawByProfile.put(ACCOUNTWIDE_KEY, "3");
        service.loadSelectedBookmarks(ACCOUNTWIDE_KEY, selected);
        assertEquals("Should still be cached until config invalidation", setOf(1, 2), selected);

        service.reloadFromConfigKey("bookmarks");
        service.loadSelectedBookmarks(ACCOUNTWIDE_KEY, selected);
        assertEquals(setOf(3), selected);
    }

    @Test
    public void toggleForSelectedProfilePersistsProfileAndAccountwideOnAdd() {
        TestHooks hooks = new TestHooks();
        hooks.rawByProfile.put(ACCOUNTWIDE_KEY, "5");
        hooks.rawByProfile.put(123L, "5");
        BookmarkStateService service = service(hooks);

        BookmarkSyncService.ToggleResult result = service.toggleForSelected(123L, 42);

        assertTrue(result.nowBookmarked);
        assertTrue(result.selectedChanged);
        assertTrue(result.accountwideChanged);
        assertFalse(result.mirroredProfileChanged);
        assertEquals("5,42", hooks.persistedByProfile.get(123L));
        assertEquals("5,42", hooks.persistedByProfile.get(ACCOUNTWIDE_KEY));
    }

    @Test
    public void toggleForAccountwideMirrorsAddToActiveProfile() {
        TestHooks hooks = new TestHooks();
        hooks.rawByProfile.put(ACCOUNTWIDE_KEY, "");
        hooks.rawByProfile.put(777L, "9");
        hooks.activeProfileKey = 777L;
        BookmarkStateService service = service(hooks);

        BookmarkSyncService.ToggleResult addResult = service.toggleForSelected(ACCOUNTWIDE_KEY, 42);
        assertTrue(addResult.nowBookmarked);
        assertTrue(addResult.accountwideChanged);
        assertTrue(addResult.mirroredProfileChanged);
        assertEquals("42", hooks.persistedByProfile.get(ACCOUNTWIDE_KEY));
        assertEquals("9,42", hooks.persistedByProfile.get(777L));

        hooks.persistedByProfile.clear();
        BookmarkSyncService.ToggleResult removeResult = service.toggleForSelected(ACCOUNTWIDE_KEY, 42);
        assertFalse(removeResult.nowBookmarked);
        assertTrue(removeResult.accountwideChanged);
        assertFalse(removeResult.mirroredProfileChanged);
        assertEquals("", hooks.persistedByProfile.get(ACCOUNTWIDE_KEY));
    }

    @Test
    public void toggleForSelectedIgnoresInvalidItemId() {
        TestHooks hooks = new TestHooks();
        BookmarkStateService service = service(hooks);

        BookmarkSyncService.ToggleResult result = service.toggleForSelected(123L, 0);

        assertFalse(result.nowBookmarked);
        assertFalse(result.selectedChanged);
        assertFalse(result.accountwideChanged);
        assertFalse(result.mirroredProfileChanged);
        assertTrue(hooks.persistedByProfile.isEmpty());
    }

    private static BookmarkStateService service(TestHooks hooks) {
        return new BookmarkStateService(new BookmarkConfigStore(ACCOUNTWIDE_KEY), hooks);
    }

    private static Set<Integer> setOf(Integer... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    private static final class TestHooks implements BookmarkStateService.Hooks {
        private final Map<Long, String> rawByProfile = new HashMap<>();
        private final Map<Long, String> persistedByProfile = new HashMap<>();
        private Long activeProfileKey;

        @Override
        public String readBookmarksForProfile(long normalizedProfileKey) {
            if (persistedByProfile.containsKey(normalizedProfileKey)) {
                return persistedByProfile.get(normalizedProfileKey);
            }
            return rawByProfile.get(normalizedProfileKey);
        }

        @Override
        public void persistBookmarksForProfile(long normalizedProfileKey, String serializedBookmarkIds) {
            persistedByProfile.put(normalizedProfileKey, serializedBookmarkIds);
        }

        @Override
        public Long resolveActiveProfileKey() {
            return activeProfileKey;
        }
    }
}
