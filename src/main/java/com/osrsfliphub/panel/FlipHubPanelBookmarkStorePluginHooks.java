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
import java.util.function.Supplier;

final class FlipHubPanelBookmarkStorePluginHooks implements FlipHubPanelBookmarkStore {
    private final Set<Integer> bookmarkedItems;
    private final Supplier<BookmarkStateService> bookmarkStateServiceSupplier;
    private final Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier;
    private final Supplier<FlipHubPanel> panelSupplier;

    FlipHubPanelBookmarkStorePluginHooks(
        Set<Integer> bookmarkedItems,
        Supplier<BookmarkStateService> bookmarkStateServiceSupplier,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier,
        Supplier<FlipHubPanel> panelSupplier
    ) {
        this.bookmarkedItems = bookmarkedItems;
        this.bookmarkStateServiceSupplier = bookmarkStateServiceSupplier;
        this.profileSelectionFacadeServiceSupplier = profileSelectionFacadeServiceSupplier;
        this.panelSupplier = panelSupplier;
    }

    @Override
    public boolean isBookmarked(int itemId) {
        return bookmarkedItems != null && bookmarkedItems.contains(itemId);
    }

    @Override
    public void toggleBookmark(int itemId) {
        BookmarkStateService bookmarkStateService = resolveBookmarkStateService();
        ProfileSelectionPresentationFacadeService profileSelectionService = resolveProfileSelectionService();
        if (bookmarkStateService == null || profileSelectionService == null || bookmarkedItems == null) {
            return;
        }
        long selectedProfileKey = profileSelectionService.resolveSelectedProfileKey();
        bookmarkStateService.toggleForSelected(selectedProfileKey, itemId);
        bookmarkStateService.loadSelectedBookmarks(selectedProfileKey, bookmarkedItems);
        FlipHubPanel panel = panelSupplier != null ? panelSupplier.get() : null;
        if (panel != null) {
            panel.refreshBookmarks();
        }
    }

    private BookmarkStateService resolveBookmarkStateService() {
        return bookmarkStateServiceSupplier != null ? bookmarkStateServiceSupplier.get() : null;
    }

    private ProfileSelectionPresentationFacadeService resolveProfileSelectionService() {
        return profileSelectionFacadeServiceSupplier != null
            ? profileSelectionFacadeServiceSupplier.get()
            : null;
    }
}

