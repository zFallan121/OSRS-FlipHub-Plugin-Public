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

public class ProfileUiCoordinatorFactoryServiceTest {
    @Test
    public void createBuildsCoordinatorThatDelegatesToRuntimeHooks() {
        ProfileUiCoordinatorFactoryService factory = new ProfileUiCoordinatorFactoryService();
        TestRuntimeHooks hooks = new TestRuntimeHooks();
        hooks.options.add(new FlipHubProfileOption("hash_1", "One"));
        hooks.selectedProfileKey = "hash_1";
        hooks.headerLabel = "Accountwide";
        hooks.linked = true;

        ProfileUiCoordinator coordinator = factory.create(hooks);
        coordinator.updateProfileOptionsUi();
        coordinator.updateProfileHeader();

        assertEquals(1, hooks.profileOptionsCalls);
        assertEquals("hash_1", hooks.lastSelectedProfileKey);
        assertEquals(1, hooks.lastOptions.size());
        assertEquals(1, hooks.profileHeaderCalls);
        assertEquals("Accountwide", hooks.lastHeaderLabel);
        assertEquals(true, hooks.lastHeaderLinked);
        assertEquals(1, hooks.uploadDiagnosticsCalls);
    }

    @Test
    public void createWithNullRuntimeHooksReturnsNoopCoordinator() {
        ProfileUiCoordinatorFactoryService factory = new ProfileUiCoordinatorFactoryService();
        ProfileUiCoordinator coordinator = factory.create(null);

        coordinator.updateProfileOptionsUi();
        coordinator.updateProfileHeader();
    }

    private static final class TestRuntimeHooks implements ProfileUiCoordinatorFactoryService.RuntimeHooks {
        private final List<FlipHubProfileOption> options = new ArrayList<>();
        private String selectedProfileKey = "accountwide";
        private String headerLabel = "Profile";
        private boolean linked;

        private int profileOptionsCalls;
        private int profileHeaderCalls;
        private int uploadDiagnosticsCalls;

        private List<FlipHubProfileOption> lastOptions;
        private String lastSelectedProfileKey;
        private String lastHeaderLabel;
        private boolean lastHeaderLinked;

        @Override
        public boolean hasPanel() {
            return true;
        }

        @Override
        public List<FlipHubProfileOption> buildProfileOptions() {
            return options;
        }

        @Override
        public String resolveSelectedProfileKeyForUi() {
            return selectedProfileKey;
        }

        @Override
        public void setProfileOptions(List<FlipHubProfileOption> options, String selectedKey) {
            profileOptionsCalls++;
            lastOptions = options;
            lastSelectedProfileKey = selectedKey;
        }

        @Override
        public String resolveProfileHeaderLabel() {
            return headerLabel;
        }

        @Override
        public boolean isLinked() {
            return linked;
        }

        @Override
        public void setProfileHeader(String label, boolean linked) {
            profileHeaderCalls++;
            lastHeaderLabel = label;
            lastHeaderLinked = linked;
        }

        @Override
        public void updateUploadDiagnosticsUi() {
            uploadDiagnosticsCalls++;
        }
    }
}

