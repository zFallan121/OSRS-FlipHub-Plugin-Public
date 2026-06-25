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

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BookmarkSyncServiceTest {
    @Test
    public void profileBookmarkAddAlsoAddsToAccountwide() {
        Set<Integer> selected = new HashSet<>();
        Set<Integer> accountwide = new HashSet<>();

        BookmarkSyncService.ToggleResult result =
            BookmarkSyncService.toggleBookmark(42L, 593, selected, accountwide);

        assertTrue(result.nowBookmarked);
        assertTrue(result.selectedChanged);
        assertTrue(result.accountwideChanged);
        assertFalse(result.mirroredProfileChanged);
        assertTrue(selected.contains(593));
        assertTrue(accountwide.contains(593));
    }

    @Test
    public void profileBookmarkAddDoesNotDuplicateAccountwideEntry() {
        Set<Integer> selected = new HashSet<>();
        Set<Integer> accountwide = new HashSet<>();
        accountwide.add(593);

        BookmarkSyncService.ToggleResult result =
            BookmarkSyncService.toggleBookmark(42L, 593, selected, accountwide);

        assertTrue(result.nowBookmarked);
        assertTrue(result.selectedChanged);
        assertFalse(result.accountwideChanged);
        assertFalse(result.mirroredProfileChanged);
        assertTrue(selected.contains(593));
        assertTrue(accountwide.contains(593));
    }

    @Test
    public void profileBookmarkRemoveOnlyRemovesFromSelectedProfile() {
        Set<Integer> selected = new HashSet<>();
        Set<Integer> accountwide = new HashSet<>();
        selected.add(593);
        accountwide.add(593);

        BookmarkSyncService.ToggleResult result =
            BookmarkSyncService.toggleBookmark(42L, 593, selected, accountwide);

        assertFalse(result.nowBookmarked);
        assertTrue(result.selectedChanged);
        assertFalse(result.accountwideChanged);
        assertFalse(result.mirroredProfileChanged);
        assertFalse(selected.contains(593));
        assertTrue(accountwide.contains(593));
    }

    @Test
    public void accountwideBookmarkAddMirrorsToActiveProfile() {
        Set<Integer> accountwide = new HashSet<>();
        Set<Integer> activeProfile = new HashSet<>();

        BookmarkSyncService.ToggleResult addResult =
            BookmarkSyncService.toggleAccountwideBookmark(213, accountwide, activeProfile);

        assertTrue(addResult.nowBookmarked);
        assertTrue(addResult.selectedChanged);
        assertTrue(addResult.accountwideChanged);
        assertTrue(addResult.mirroredProfileChanged);
        assertTrue(accountwide.contains(213));
        assertTrue(activeProfile.contains(213));

        BookmarkSyncService.ToggleResult removeResult =
            BookmarkSyncService.toggleAccountwideBookmark(213, accountwide, activeProfile);

        assertFalse(removeResult.nowBookmarked);
        assertTrue(removeResult.selectedChanged);
        assertTrue(removeResult.accountwideChanged);
        assertFalse(removeResult.mirroredProfileChanged);
        assertFalse(accountwide.contains(213));
        assertTrue(activeProfile.contains(213));
    }

    @Test
    public void accountwideBookmarkAddSkipsDuplicateOnActiveProfile() {
        Set<Integer> accountwide = new HashSet<>();
        Set<Integer> activeProfile = new HashSet<>();
        activeProfile.add(526);

        BookmarkSyncService.ToggleResult result =
            BookmarkSyncService.toggleAccountwideBookmark(526, accountwide, activeProfile);

        assertTrue(result.nowBookmarked);
        assertTrue(result.accountwideChanged);
        assertFalse(result.mirroredProfileChanged);
        assertTrue(accountwide.contains(526));
        assertTrue(activeProfile.contains(526));
    }
}
