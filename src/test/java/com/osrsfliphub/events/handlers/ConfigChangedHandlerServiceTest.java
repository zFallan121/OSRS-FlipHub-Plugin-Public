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

import net.runelite.client.events.ConfigChanged;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ConfigChangedHandlerServiceTest {
    @Test
    public void ignoresEventsOutsideConfigGroup() {
        RecordingHooks hooks = new RecordingHooks();
        ConfigChangedHandlerService service = new ConfigChangedHandlerService(hooks);

        ConfigChanged event = new ConfigChanged();
        event.setGroup("other");
        event.setKey("licenseKey");
        service.handle(event);

        assertEquals(0, hooks.attemptLinkCount);
        assertEquals(0, hooks.loadBookmarksCount);
    }

    @Test
    public void handlesUnlinkAndBookmarkRefresh() {
        RecordingHooks hooks = new RecordingHooks();
        hooks.unlinkRequested = true;
        hooks.panelAvailable = true;
        ConfigChangedHandlerService service = new ConfigChangedHandlerService(hooks);

        ConfigChanged unlinkEvent = new ConfigChanged();
        unlinkEvent.setGroup("fliphub");
        unlinkEvent.setKey("unlinkNow");
        service.handle(unlinkEvent);

        ConfigChanged bookmarkEvent = new ConfigChanged();
        bookmarkEvent.setGroup("fliphub");
        bookmarkEvent.setKey("bookmarkKey");
        service.handle(bookmarkEvent);

        assertEquals(1, hooks.clearLinkStateCount);
        assertEquals(1, hooks.resetUploadSnapshotCount);
        assertEquals(1, hooks.setUploadBlockedCount);
        assertEquals(1, hooks.updateProfileHeaderCount);
        assertEquals(1, hooks.triggerStatsRefreshCount);
        assertEquals(1, hooks.reloadBookmarkStateCount);
        assertEquals(1, hooks.loadBookmarksCount);
        assertEquals(1, hooks.refreshBookmarksUiCount);
    }

    @Test
    public void handlesLinkAndHiddenItemsRefresh() {
        RecordingHooks hooks = new RecordingHooks();
        hooks.panelAvailable = true;
        hooks.linkInput = "  test-code  ";
        ConfigChangedHandlerService service = new ConfigChangedHandlerService(hooks);

        ConfigChanged linkEvent = new ConfigChanged();
        linkEvent.setGroup("fliphub");
        linkEvent.setKey("linkCode");
        service.handle(linkEvent);

        ConfigChanged hiddenEvent = new ConfigChanged();
        hiddenEvent.setGroup("fliphub");
        hiddenEvent.setKey("hiddenKey");
        service.handle(hiddenEvent);

        assertEquals(1, hooks.attemptLinkCount);
        assertEquals("test-code", hooks.lastAttemptedLink);
        assertEquals(1, hooks.loadHiddenItemsCount);
        assertEquals(1, hooks.refreshBookmarksUiCount);
    }

    private static final class RecordingHooks implements ConfigChangedHandlerService.Hooks {
        private String linkInput = "";
        private boolean unlinkRequested;
        private boolean panelAvailable;
        private int attemptLinkCount;
        private String lastAttemptedLink = "";
        private int clearLinkStateCount;
        private int resetUploadSnapshotCount;
        private int setUploadBlockedCount;
        private int updateProfileHeaderCount;
        private int triggerStatsRefreshCount;
        private int reloadBookmarkStateCount;
        private int loadBookmarksCount;
        private int refreshBookmarksUiCount;
        private int loadHiddenItemsCount;

        @Override
        public String configGroup() {
            return "fliphub";
        }

        @Override
        public boolean isLinkInputConfigKey(String key) {
            return "licenseKey".equals(key) || "linkCode".equals(key);
        }

        @Override
        public String getLinkInput() {
            return linkInput;
        }

        @Override
        public void attemptLink(String linkInput) {
            attemptLinkCount++;
            lastAttemptedLink = linkInput;
        }

        @Override
        public boolean isUnlinkConfigKey(String key) {
            return "unlinkNow".equals(key);
        }

        @Override
        public boolean unlinkRequested() {
            return unlinkRequested;
        }

        @Override
        public void clearLinkState() {
            clearLinkStateCount++;
        }

        @Override
        public void resetUploadSnapshot() {
            resetUploadSnapshotCount++;
        }

        @Override
        public void setUploadBlocked(String message) {
            setUploadBlockedCount++;
        }

        @Override
        public boolean isPanelAvailable() {
            return panelAvailable;
        }

        @Override
        public void updateProfileHeader() {
            updateProfileHeaderCount++;
        }

        @Override
        public void triggerStatsRefresh() {
            triggerStatsRefreshCount++;
        }

        @Override
        public boolean isBookmarksConfigKey(String key) {
            return "bookmarkKey".equals(key);
        }

        @Override
        public void reloadBookmarkState(String key) {
            reloadBookmarkStateCount++;
        }

        @Override
        public void loadBookmarks() {
            loadBookmarksCount++;
        }

        @Override
        public void refreshBookmarksUi() {
            refreshBookmarksUiCount++;
        }

        @Override
        public boolean isHiddenItemsConfigKey(String key) {
            return "hiddenKey".equals(key);
        }

        @Override
        public void loadHiddenItems() {
            loadHiddenItemsCount++;
        }
    }
}
