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

import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

final class ManageDataDialogPluginHooks implements ManageDataDialogService.Hooks {
    private final Supplier<FlipHubPanel> panelSupplier;
    private final Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier;
    private final Supplier<ManageDataCommandService> manageDataCommandServiceSupplier;
    private final Consumer<String> showErrorConsumer;
    private final Consumer<Runnable> invokeOnClientThreadConsumer;
    private final Supplier<LocalProfileWipeService> localProfileWipeServiceSupplier;
    private final Supplier<WebsiteStatsWipeService> websiteStatsWipeServiceSupplier;

    ManageDataDialogPluginHooks(
        Supplier<FlipHubPanel> panelSupplier,
        Supplier<ProfileSelectionPresentationFacadeService> profileSelectionFacadeServiceSupplier,
        Supplier<ManageDataCommandService> manageDataCommandServiceSupplier,
        Consumer<String> showErrorConsumer,
        Consumer<Runnable> invokeOnClientThreadConsumer,
        Supplier<LocalProfileWipeService> localProfileWipeServiceSupplier,
        Supplier<WebsiteStatsWipeService> websiteStatsWipeServiceSupplier
    ) {
        this.panelSupplier = panelSupplier;
        this.profileSelectionFacadeServiceSupplier = profileSelectionFacadeServiceSupplier;
        this.manageDataCommandServiceSupplier = manageDataCommandServiceSupplier;
        this.showErrorConsumer = showErrorConsumer;
        this.invokeOnClientThreadConsumer = invokeOnClientThreadConsumer;
        this.localProfileWipeServiceSupplier = localProfileWipeServiceSupplier;
        this.websiteStatsWipeServiceSupplier = websiteStatsWipeServiceSupplier;
    }

    @Override
    public boolean hasPanel() {
        return panelSupplier != null && panelSupplier.get() != null;
    }

    @Override
    public void invokeOnUiThread(Runnable task) {
        if (task != null) {
            SwingUtilities.invokeLater(task);
        }
    }

    @Override
    public long resolveSelectedProfileKey() {
        ProfileSelectionPresentationFacadeService service = resolveProfileSelectionFacadeService();
        return service != null ? service.resolveSelectedProfileKey() : -1L;
    }

    @Override
    public String resolveSelectedProfileLabel() {
        ProfileSelectionPresentationFacadeService service = resolveProfileSelectionFacadeService();
        return service != null ? service.resolveSelectedProfileLabel() : "";
    }

    @Override
    public boolean isLinked() {
        ProfileSelectionPresentationFacadeService service = resolveProfileSelectionFacadeService();
        return service != null && service.isLinked();
    }

    @Override
    public ManageDataCommandService getManageDataCommandService() {
        return manageDataCommandServiceSupplier != null ? manageDataCommandServiceSupplier.get() : null;
    }

    @Override
    public int showOptionDialog(String body, Object[] options, Object defaultOption) {
        FlipHubPanel panel = panelSupplier != null ? panelSupplier.get() : null;
        return JOptionPane.showOptionDialog(
            panel,
            body,
            "FlipHub Manage Data",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null,
            options,
            defaultOption
        );
    }

    @Override
    public String showInputDialog(String body, String title) {
        FlipHubPanel panel = panelSupplier != null ? panelSupplier.get() : null;
        return JOptionPane.showInputDialog(panel, body, title, JOptionPane.WARNING_MESSAGE);
    }

    @Override
    public void showError(String message) {
        if (showErrorConsumer != null) {
            showErrorConsumer.accept(message);
        }
    }

    @Override
    public void invokeOnClientThread(Runnable task) {
        if (task != null && invokeOnClientThreadConsumer != null) {
            invokeOnClientThreadConsumer.accept(task);
        }
    }

    @Override
    public void wipeSingleLocalProfile(long accountKey, String label) {
        LocalProfileWipeService service = localProfileWipeServiceSupplier != null
            ? localProfileWipeServiceSupplier.get()
            : null;
        if (service != null) {
            service.wipeSingleLocalProfile(accountKey, label);
        }
    }

    @Override
    public void wipeAllLocalProfiles() {
        LocalProfileWipeService service = localProfileWipeServiceSupplier != null
            ? localProfileWipeServiceSupplier.get()
            : null;
        if (service != null) {
            service.wipeAllLocalProfiles();
        }
    }

    @Override
    public void wipeWebsiteStatsAsync() {
        WebsiteStatsWipeService service = websiteStatsWipeServiceSupplier != null
            ? websiteStatsWipeServiceSupplier.get()
            : null;
        if (service != null) {
            service.wipeWebsiteStatsAsync();
        }
    }

    private ProfileSelectionPresentationFacadeService resolveProfileSelectionFacadeService() {
        return profileSelectionFacadeServiceSupplier != null
            ? profileSelectionFacadeServiceSupplier.get()
            : null;
    }
}
