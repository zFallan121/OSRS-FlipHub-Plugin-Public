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
import java.util.function.Supplier;

final class ProfileUiCoordinatorPluginHooks implements ProfileUiCoordinatorFactoryService.RuntimeHooks {
    private final Supplier<FlipHubPanel> panelSupplier;
    private final Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier;
    private final Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier;

    ProfileUiCoordinatorPluginHooks(
        Supplier<FlipHubPanel> panelSupplier,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier,
        Supplier<UploadEventDispatchFacadeService> uploadEventDispatchFacadeServiceSupplier
    ) {
        this.panelSupplier = panelSupplier;
        this.profileSelectionFacadeServiceSupplier = profileSelectionFacadeServiceSupplier;
        this.uploadEventDispatchFacadeServiceSupplier = uploadEventDispatchFacadeServiceSupplier;
    }

    @Override
    public boolean hasPanel() {
        return resolvePanel() != null;
    }

    @Override
    public List<FlipHubProfileOption> buildProfileOptions() {
        ProfileSelectionPresentationFacadeService service = resolveProfileSelectionFacadeService();
        if (service == null) {
            return Collections.emptyList();
        }
        return service.buildProfileOptions();
    }

    @Override
    public String resolveSelectedProfileKeyForUi() {
        ProfileSelectionPresentationFacadeService service = resolveProfileSelectionFacadeService();
        return service != null ? service.resolveSelectedProfileKeyForUi() : null;
    }

    @Override
    public void setProfileOptions(List<FlipHubProfileOption> options, String selectedKey) {
        FlipHubPanel panel = resolvePanel();
        if (panel != null) {
            panel.setProfileOptions(options, selectedKey);
        }
    }

    @Override
    public String resolveProfileHeaderLabel() {
        ProfileSelectionPresentationFacadeService service = resolveProfileSelectionFacadeService();
        return service != null ? service.resolveProfileHeaderLabel() : null;
    }

    @Override
    public boolean isLinked() {
        ProfileSelectionPresentationFacadeService service = resolveProfileSelectionFacadeService();
        return service != null && service.isLinked();
    }

    @Override
    public void setProfileHeader(String label, boolean linked) {
        FlipHubPanel panel = resolvePanel();
        if (panel != null) {
            panel.setProfileHeader(label, linked);
        }
    }

    @Override
    public void updateUploadDiagnosticsUi() {
        UploadEventDispatchFacadeService service = uploadEventDispatchFacadeServiceSupplier != null
            ? uploadEventDispatchFacadeServiceSupplier.get()
            : null;
        if (service != null) {
            service.updateUploadDiagnosticsUi();
        }
    }

    private FlipHubPanel resolvePanel() {
        return panelSupplier != null ? panelSupplier.get() : null;
    }

    private ProfileSelectionPresentationFacadeService resolveProfileSelectionFacadeService() {
        return profileSelectionFacadeServiceSupplier != null
            ? profileSelectionFacadeServiceSupplier.get()
            : null;
    }
}

