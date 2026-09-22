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

import java.awt.*;
import java.awt.event.MouseWheelListener;
import javax.swing.*;
import lombok.RequiredArgsConstructor;
import static com.osrsfliphub.Skin.*;

final class StatsPanelBuilder {
    @RequiredArgsConstructor
    static final class BuildResult {
        final JPanel panel;
        final JScrollPane scrollPane;
        final JLabel totalProfitValue;
        final JLabel roiValue;
        final JLabel flipsValue;
        final JLabel taxValue;
        final JLabel sessionTimeValue;
        final JLabel hourlyValue;
    }

    private static final String TAB_CARD = "tab";
    private static final String RECORDER_CARD = "recorder";

    private final StatsPanelHeaderBuilder headerBuilder;
    private final StatsPanelContentBuilder contentBuilder;
    private final RecipeRecorder recorder;
    private final CardLayout deckLayout = new CardLayout();
    private final JPanel deck = new JPanel(deckLayout);
    private boolean recorderOpen;

    StatsPanelBuilder(UiStyler uiStyler,
                             PanelValueFormat valueFormatService,
                             PanelState panelState,
                             WheelScroll wheelScrollCoordinator,
                             MouseWheelListener wheelForwarder) {
        this.headerBuilder = new StatsPanelHeaderBuilder(uiStyler, panelState);
        this.recorder = new RecipeRecorder(uiStyler, valueFormatService, this::showTab);
        this.contentBuilder = new StatsPanelContentBuilder(
            uiStyler, panelState, wheelScrollCoordinator, wheelForwarder,
            this::showRecorder);
    }

    /**
     * Recording takes the whole tab rather than opening over it.
     *
     * <p>A dialog would be the usual answer, but the column is 201px wide: anything floating in
     * it either covers what it is about or is too narrow to pick trades in. Swapping the tab's
     * body keeps the panel's one surface and gives the form the full width, and the tabs above
     * stay live, so leaving is never a trap.
     */
    private void showRecorder(boolean move) {
        recorder.open(move);
        recorderOpen = true;
        deckLayout.show(deck, RECORDER_CARD);
    }

    private void showTab() {
        recorderOpen = false;
        deckLayout.show(deck, TAB_CARD);
    }

    /** The recorder's own scroll surface while it is up, so the wheel reaches it. Null otherwise. */
    JScrollPane openRecorderPane() {
        return recorderOpen ? recorder.view() : null;
    }

    BuildResult build(
        JComboBox<StatsRange> statsRangeCombo,
        JTextField statsSearchField,
        JLabel statsUpdatedLabel,
        JPanel statsContentPanel,
        JPanel statsItemsListPanel,
        JComboBox<StatsItemSort> statsSortCombo,
        JComboBox<StatsRecipeFilter> statsFilterCombo,
        JButton statsSortDirectionButton
    ) {
        JPanel panel = plain(new BorderLayout());

        JPanel header = headerBuilder.buildHeader(statsRangeCombo, statsUpdatedLabel);
        StatsPanelContentBuilder.ContentResult content = contentBuilder.buildContent(
            statsContentPanel,
            statsItemsListPanel,
            statsSearchField,
            statsSortCombo,
            statsFilterCombo,
            statsSortDirectionButton
        );

        JPanel tabCard = plain(new BorderLayout());
        tabCard.add(header, BorderLayout.NORTH);
        tabCard.add(content.scrollPane, BorderLayout.CENTER);

        deck.setOpaque(false);
        deck.add(tabCard, TAB_CARD);
        deck.add(recorder.view(), RECORDER_CARD);
        panel.add(deck, BorderLayout.CENTER);
        return new BuildResult(
            panel,
            content.scrollPane,
            content.totalProfitValue,
            content.roiValue,
            content.flipsValue,
            content.taxValue,
            content.sessionTimeValue,
            content.hourlyValue
        );
    }
}
