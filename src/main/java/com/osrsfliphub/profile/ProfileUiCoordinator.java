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

import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
final class ProfileUiCoordinator {
    @Inject
    ProfileUiCoordinator() {
    }

    private FlipHubPanel panel() {
        GeLifecyclePlugin plugin = PluginAccess.pluginOrNull();
        return plugin != null ? plugin.panel : null;
    }

    private ProfileSelectionPresentationFacadeService facade() {
        return PluginInjectorBridge.get(ProfileSelectionPresentationFacadeService.class);
    }

    void updateProfileOptionsUi() {
        FlipHubPanel panel = panel();
        ProfileSelectionPresentationFacadeService service = facade();
        if (panel == null) {
            return;
        }
        List<FlipHubProfileOption> options =
            service != null ? service.buildProfileOptions() : Collections.emptyList();
        String selected = service != null ? service.resolveSelectedProfileKeyForUi() : null;
        panel.setProfileOptions(options, selected);
    }

    void updateProfileHeader() {
        FlipHubPanel panel = panel();
        ProfileSelectionPresentationFacadeService service = facade();
        if (panel == null) {
            return;
        }
        panel.setProfileHeader(
            service != null ? service.resolveProfileHeaderLabel() : null,
            service != null && service.isLinked());
        UploadEventDispatchFacadeService uploadService =
            PluginInjectorBridge.get(UploadEventDispatchFacadeService.class);
        if (uploadService != null) {
            uploadService.updateUploadDiagnosticsUi();
        }
    }
}

