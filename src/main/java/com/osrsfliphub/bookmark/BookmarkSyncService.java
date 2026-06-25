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

import java.util.Set;

final class BookmarkSyncService {
    static final long ACCOUNTWIDE_KEY = 0L;

    static final class ToggleResult {
        final boolean nowBookmarked;
        final boolean selectedChanged;
        final boolean accountwideChanged;
        final boolean mirroredProfileChanged;

        private ToggleResult(boolean nowBookmarked,
                             boolean selectedChanged,
                             boolean accountwideChanged,
                             boolean mirroredProfileChanged) {
            this.nowBookmarked = nowBookmarked;
            this.selectedChanged = selectedChanged;
            this.accountwideChanged = accountwideChanged;
            this.mirroredProfileChanged = mirroredProfileChanged;
        }
    }

    private BookmarkSyncService() {
    }

    static ToggleResult toggleBookmark(long selectedProfileKey,
                                       int itemId,
                                       Set<Integer> selectedBookmarks,
                                       Set<Integer> accountwideBookmarks) {
        if (itemId <= 0 || selectedBookmarks == null || accountwideBookmarks == null) {
            return new ToggleResult(false, false, false, false);
        }

        long normalizedProfileKey = selectedProfileKey > 0 ? selectedProfileKey : ACCOUNTWIDE_KEY;
        if (normalizedProfileKey == ACCOUNTWIDE_KEY) {
            if (selectedBookmarks.remove(itemId)) {
                accountwideBookmarks.remove(itemId);
                return new ToggleResult(false, true, true, false);
            }
            selectedBookmarks.add(itemId);
            accountwideBookmarks.add(itemId);
            return new ToggleResult(true, true, true, false);
        }

        if (selectedBookmarks.remove(itemId)) {
            return new ToggleResult(false, true, false, false);
        }

        boolean selectedChanged = selectedBookmarks.add(itemId);
        boolean accountwideChanged = accountwideBookmarks.add(itemId);
        return new ToggleResult(true, selectedChanged, accountwideChanged, false);
    }

    static ToggleResult toggleAccountwideBookmark(int itemId,
                                                  Set<Integer> accountwideBookmarks,
                                                  Set<Integer> activeProfileBookmarks) {
        if (itemId <= 0 || accountwideBookmarks == null) {
            return new ToggleResult(false, false, false, false);
        }
        if (accountwideBookmarks.remove(itemId)) {
            return new ToggleResult(false, true, true, false);
        }
        accountwideBookmarks.add(itemId);
        boolean mirroredChanged = activeProfileBookmarks != null && activeProfileBookmarks.add(itemId);
        return new ToggleResult(true, true, true, mirroredChanged);
    }
}
