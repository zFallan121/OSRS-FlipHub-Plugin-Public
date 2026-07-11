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
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.Border;

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
        JTextField statsSearchField,
        JButton statsClearButton,
        JLabel statsUpdatedLabel
    ) {
        JPanel header = new JPanel();
        header.setBackground(BG);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JPanel rangeRow = new JPanel(new BorderLayout(8, 0));
        rangeRow.setBackground(BG);
        rangeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        rangeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        if (uiStyler != null) {
            uiStyler.styleComboBox(statsRangeCombo);
        }
        statsRangeCombo.setSelectedItem(StatsRange.SESSION);
        statsRangeCombo.addActionListener(e -> {
            StatsRange range = (StatsRange) statsRangeCombo.getSelectedItem();
            if (panelStateService != null && range != null) {
                panelStateService.onStatsRangeSelectionChanged(listener, range);
            }
        });
        rangeRow.add(statsRangeCombo, BorderLayout.WEST);

        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.setBackground(BG);
        searchRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        if (uiStyler != null) {
            uiStyler.styleTextField(statsSearchField);
        }
        statsSearchField.setToolTipText("Filter items");
        installDocumentListener(statsSearchField, () -> {
            if (panelStateService != null) {
                panelStateService.onStatsSearchQueryChanged(panelState, statsSearchField.getText(), renderStatsItems);
            }
        });

        statsClearButton.setFocusPainted(false);
        statsClearButton.setFont(fontSemiBold(10.5f));
        statsClearButton.setBackground(CARD_ALT);
        statsClearButton.setForeground(TEXT);
        statsClearButton.setBorder(roundedBorder(CHIP_ARC, SOFT_BORDER, new Insets(4, 10, 4, 10)));
        statsClearButton.setOpaque(true);
        statsClearButton.addActionListener(e -> statsSearchField.setText(""));

        searchRow.add(statsSearchField, BorderLayout.CENTER);
        searchRow.add(statsClearButton, BorderLayout.EAST);

        statsUpdatedLabel.setForeground(MUTED);
        statsUpdatedLabel.setFont(font(10.5f));
        statsUpdatedLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        header.add(rangeRow);
        header.add(Box.createVerticalStrut(6));
        header.add(searchRow);
        header.add(Box.createVerticalStrut(4));
        return header;
    }

    private void installDocumentListener(JTextField field, Runnable onChange) {
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                onChange.run();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                onChange.run();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                onChange.run();
            }
        });
    }

    private Font font(float size) {
        return uiStyler.font(size);
    }

    private Font fontSemiBold(float size) {
        return uiStyler.fontSemiBold(size);
    }

    private Border roundedBorder(int arc, Color color, Insets padding) {
        return uiStyler.roundedBorder(arc, color, padding);
    }
}
