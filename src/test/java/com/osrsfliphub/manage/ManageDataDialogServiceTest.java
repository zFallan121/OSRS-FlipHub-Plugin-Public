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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ManageDataDialogServiceTest {
    @Test
    public void wipeSelectedProfileRunsOnClientThreadWhenConfirmed() {
        RecordingHooks hooks = new RecordingHooks();
        hooks.hasPanel = true;
        hooks.selectedKey = 42L;
        hooks.selectedLabel = "XP V";
        hooks.optionChoice = 0;
        hooks.nextInput = "WIPE XP V";

        ManageDataDialogService service = new ManageDataDialogService(0L, hooks);
        service.showManageDataDialog();

        assertEquals(1, hooks.wipeSingleCount);
        assertEquals(42L, hooks.wipedSingleKey);
        assertEquals("XP V", hooks.wipedSingleLabel);
        assertEquals(0, hooks.errorCount);
    }

    @Test
    public void wipeSelectedProfileShowsValidationErrorForAccountwide() {
        RecordingHooks hooks = new RecordingHooks();
        hooks.hasPanel = true;
        hooks.selectedKey = 0L;
        hooks.selectedLabel = "Accountwide";
        hooks.optionChoice = 0;

        ManageDataDialogService service = new ManageDataDialogService(0L, hooks);
        service.showManageDataDialog();

        assertEquals(0, hooks.wipeSingleCount);
        assertEquals(1, hooks.errorCount);
    }

    @Test
    public void wipeAllProfilesRequiresConfirmation() {
        RecordingHooks hooks = new RecordingHooks();
        hooks.hasPanel = true;
        hooks.optionChoice = 1;
        hooks.nextInput = "nope";

        ManageDataDialogService service = new ManageDataDialogService(0L, hooks);
        service.showManageDataDialog();

        assertEquals(0, hooks.wipeAllCount);
        assertEquals(1, hooks.errorCount);
    }

    @Test
    public void wipeWebsiteRunsWhenConfirmed() {
        RecordingHooks hooks = new RecordingHooks();
        hooks.hasPanel = true;
        hooks.linked = true;
        hooks.optionChoice = 2;
        hooks.nextInput = "WIPE WEBSITE";

        ManageDataDialogService service = new ManageDataDialogService(0L, hooks);
        service.showManageDataDialog();

        assertEquals(1, hooks.wipeWebsiteCount);
        assertEquals(0, hooks.errorCount);
    }

    private static final class RecordingHooks implements ManageDataDialogService.Hooks {
        private boolean hasPanel;
        private long selectedKey = 1L;
        private String selectedLabel = "Profile 1";
        private boolean linked;
        private int optionChoice = -1;
        private String nextInput;
        private int errorCount;
        private int wipeSingleCount;
        private long wipedSingleKey;
        private String wipedSingleLabel;
        private int wipeAllCount;
        private int wipeWebsiteCount;
        private final ManageDataCommandService commandService = new ManageDataCommandService();

        @Override
        public boolean hasPanel() {
            return hasPanel;
        }

        @Override
        public void invokeOnUiThread(Runnable task) {
            if (task != null) {
                task.run();
            }
        }

        @Override
        public long resolveSelectedProfileKey() {
            return selectedKey;
        }

        @Override
        public String resolveSelectedProfileLabel() {
            return selectedLabel;
        }

        @Override
        public boolean isLinked() {
            return linked;
        }

        @Override
        public ManageDataCommandService getManageDataCommandService() {
            return commandService;
        }

        @Override
        public int showOptionDialog(String body, Object[] options, Object defaultOption) {
            return optionChoice;
        }

        @Override
        public String showInputDialog(String body, String title) {
            return nextInput;
        }

        @Override
        public void showError(String message) {
            errorCount++;
        }

        @Override
        public void invokeOnClientThread(Runnable task) {
            if (task != null) {
                task.run();
            }
        }

        @Override
        public void wipeSingleLocalProfile(long accountKey, String label) {
            wipeSingleCount++;
            wipedSingleKey = accountKey;
            wipedSingleLabel = label;
        }

        @Override
        public void wipeAllLocalProfiles() {
            wipeAllCount++;
        }

        @Override
        public void wipeWebsiteStatsAsync() {
            wipeWebsiteCount++;
        }
    }
}
