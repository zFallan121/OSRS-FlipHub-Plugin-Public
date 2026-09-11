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

import static com.osrsfliphub.FlipHubPanelConstants.*;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

final class FlipHubStatsPanelHeaderBuilder {
    private final FlipHubUiStyler uiStyler;
    private final FlipHubPanelStateService panelStateService;
    private final FlipHubPanelMutableState panelState;
    private final FlipHubPanelListener listener;
    private final Runnable renderStatsItems;

    FlipHubStatsPanelHeaderBuilder(FlipHubUiStyler uiStyler,
                                   FlipHubPanelStateService panelStateService,
                                   FlipHubPanelMutableState panelState,
                                   FlipHubPanelListener listener,
                                   Runnable renderStatsItems) {
        this.uiStyler = uiStyler;
        this.panelStateService = panelStateService;
        this.panelState = panelState;
        this.listener = listener;
        this.renderStatsItems = renderStatsItems;
    }

    JPanel buildHeader(
        JComboBox<StatsRange> statsRangeCombo,
        JLabel statsUpdatedLabel
    ) {
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JPanel rangeRow = new JPanel(new BorderLayout(8, 0));
        rangeRow.setOpaque(false);
        rangeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        rangeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        if (uiStyler != null) {
            uiStyler.styleComboBox(statsRangeCombo);
        }
        statsRangeCombo.setSelectedItem(StatsRange.SESSION);
        statsRangeCombo.addActionListener(e -> {
            StatsRange range = (StatsRange) statsRangeCombo.getSelectedItem();
            if (panelStateService != null && range != null) {
                panelStateService.onStatsRangeSelectionChanged(listener, panelState, range);
            }
        });
        rangeRow.add(statsRangeCombo, BorderLayout.WEST);

        statsUpdatedLabel.setForeground(MUTED_2);
        statsUpdatedLabel.setFont(font(10.5f));
        statsUpdatedLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // The range is the only thing pinned above the list now: it says which trades the whole
        // tab is about, so it stays put while everything it describes scrolls under it.
        header.add(rangeRow);
        header.add(Box.createVerticalStrut(8));
        return header;
    }

    private Font font(float size) {
        return uiStyler.font(size);
    }

    private Font fontSemiBold(float size) {
        return uiStyler.fontSemiBold(size);
    }

}
