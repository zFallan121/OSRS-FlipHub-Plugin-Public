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

final class ConfigChangedPluginHooks implements ConfigChangedHandlerService.Hooks {
    private final String configGroup;
    private final Supplier<LinkAttemptService> linkAttemptServiceSupplier;
    private final Supplier<PluginConfig> configSupplier;
    private final Supplier<LinkSessionConfigStore> linkSessionConfigStoreSupplier;
    private final Supplier<AccountwideSummaryUploader> accountwideSummaryUploaderSupplier;
    private final Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier;
    private final Supplier<FlipHubPanel> panelSupplier;
    private final Runnable updateProfileHeader;
    private final Runnable triggerStatsRefresh;
    private final Supplier<BookmarkStateService> bookmarkStateServiceSupplier;
    private final Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier;
    private final Set<Integer> bookmarkedItems;
    private final HiddenItemConfigStore hiddenItemConfigStore;
    private final Set<Integer> hiddenItems;

    ConfigChangedPluginHooks(
        String configGroup,
        Supplier<LinkAttemptService> linkAttemptServiceSupplier,
        Supplier<PluginConfig> configSupplier,
        Supplier<LinkSessionConfigStore> linkSessionConfigStoreSupplier,
        Supplier<AccountwideSummaryUploader> accountwideSummaryUploaderSupplier,
        Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier,
        Supplier<FlipHubPanel> panelSupplier,
        Runnable updateProfileHeader,
        Runnable triggerStatsRefresh,
        Supplier<BookmarkStateService> bookmarkStateServiceSupplier,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier,
        Set<Integer> bookmarkedItems,
        HiddenItemConfigStore hiddenItemConfigStore,
        Set<Integer> hiddenItems
    ) {
        this.configGroup = configGroup;
        this.linkAttemptServiceSupplier = linkAttemptServiceSupplier;
        this.configSupplier = configSupplier;
        this.linkSessionConfigStoreSupplier = linkSessionConfigStoreSupplier;
        this.accountwideSummaryUploaderSupplier = accountwideSummaryUploaderSupplier;
        this.uploadEventDispatchFacadeServiceSupplier = uploadEventDispatchFacadeServiceSupplier;
        this.panelSupplier = panelSupplier;
        this.updateProfileHeader = updateProfileHeader;
        this.triggerStatsRefresh = triggerStatsRefresh;
        this.bookmarkStateServiceSupplier = bookmarkStateServiceSupplier;
        this.profileSelectionFacadeServiceSupplier = profileSelectionFacadeServiceSupplier;
        this.bookmarkedItems = bookmarkedItems;
        this.hiddenItemConfigStore = hiddenItemConfigStore;
        this.hiddenItems = hiddenItems;
    }

    @Override
    public String configGroup() {
        return configGroup != null ? configGroup : "";
    }

    @Override
    public boolean isLinkInputConfigKey(String key) {
        return "licenseKey".equals(key) || "linkCode".equals(key);
    }

    @Override
    public String getLinkInput() {
        LinkAttemptService linkAttemptService = resolveLinkAttemptService();
        PluginConfig pluginConfig = configSupplier != null ? configSupplier.get() : null;
        if (linkAttemptService == null || pluginConfig == null) {
            return null;
        }
        return linkAttemptService.resolveLinkInput(pluginConfig.licenseKey(), pluginConfig.linkCode());
    }

    @Override
    public void attemptLink(String linkInput) {
        LinkAttemptService linkAttemptService = resolveLinkAttemptService();
        if (linkAttemptService != null) {
            linkAttemptService.attemptLink(linkInput);
        }
    }

    @Override
    public boolean isUnlinkConfigKey(String key) {
        return "unlinkNow".equals(key);
    }

    @Override
    public boolean unlinkRequested() {
        PluginConfig pluginConfig = configSupplier != null ? configSupplier.get() : null;
        return pluginConfig != null && pluginConfig.unlinkNow();
    }

    @Override
    public void clearLinkState() {
        LinkSessionConfigStore store = linkSessionConfigStoreSupplier != null ? linkSessionConfigStoreSupplier.get() : null;
        if (store != null) {
            store.clearLinkState();
        }
    }

    @Override
    public void resetUploadSnapshot() {
        AccountwideSummaryUploader uploader = accountwideSummaryUploaderSupplier != null
            ? accountwideSummaryUploaderSupplier.get()
            : null;
        if (uploader != null) {
            uploader.resetUploadSnapshot();
        }
    }

    @Override
    public void setUploadBlocked(String message) {
        UploadEventDispatchFacadeService uploadService = uploadEventDispatchFacadeServiceSupplier != null
            ? uploadEventDispatchFacadeServiceSupplier.get()
            : null;
        if (uploadService != null) {
            uploadService.markBlocked(message);
        }
    }

    @Override
    public boolean isPanelAvailable() {
        return panelSupplier != null && panelSupplier.get() != null;
    }

    @Override
    public void updateProfileHeader() {
        if (updateProfileHeader != null) {
            updateProfileHeader.run();
        }
    }

    @Override
    public void triggerStatsRefresh() {
        if (triggerStatsRefresh != null) {
            triggerStatsRefresh.run();
        }
    }

    @Override
    public boolean isBookmarksConfigKey(String key) {
        BookmarkStateService bookmarkStateService = resolveBookmarkStateService();
        return bookmarkStateService != null && bookmarkStateService.isBookmarksConfigKey(key);
    }

    @Override
    public void reloadBookmarkState(String key) {
        BookmarkStateService bookmarkStateService = resolveBookmarkStateService();
        if (bookmarkStateService != null) {
            bookmarkStateService.reloadFromConfigKey(key);
        }
    }

    @Override
    public void loadBookmarks() {
        BookmarkStateService bookmarkStateService = resolveBookmarkStateService();
        ProfileSelectionPresentationFacadeService profileSelectionService = resolveProfileSelectionService();
        if (bookmarkStateService == null || profileSelectionService == null || bookmarkedItems == null) {
            return;
        }
        bookmarkStateService.loadSelectedBookmarks(
            profileSelectionService.resolveSelectedProfileKey(),
            bookmarkedItems
        );
    }

    @Override
    public void refreshBookmarksUi() {
        FlipHubPanel panel = panelSupplier != null ? panelSupplier.get() : null;
        if (panel != null) {
            panel.refreshBookmarks();
        }
    }

    @Override
    public boolean isHiddenItemsConfigKey(String key) {
        return hiddenItemConfigStore != null && hiddenItemConfigStore.isHiddenItemsConfigKey(key);
    }

    @Override
    public void loadHiddenItems() {
        if (hiddenItemConfigStore == null || hiddenItems == null) {
            return;
        }
        PluginConfig pluginConfig = configSupplier != null ? configSupplier.get() : null;
        if (pluginConfig == null) {
            return;
        }
        hiddenItems.clear();
        hiddenItems.addAll(hiddenItemConfigStore.parseItemIds(pluginConfig.hiddenItems()));
    }

    private LinkAttemptService resolveLinkAttemptService() {
        return linkAttemptServiceSupplier != null ? linkAttemptServiceSupplier.get() : null;
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
