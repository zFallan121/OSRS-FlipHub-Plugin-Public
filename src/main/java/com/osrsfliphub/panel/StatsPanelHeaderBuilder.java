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
import javax.swing.*;
import lombok.RequiredArgsConstructor;
import static com.osrsfliphub.Skin.*;

@RequiredArgsConstructor
final class StatsPanelHeaderBuilder {
    private final UiStyler uiStyler;
    private final PanelState panelState;

    JPanel buildHeader(
        JComboBox<StatsRange> statsRangeCombo,
        JLabel statsUpdatedLabel
    ) {
        JPanel header = stack();

        JPanel rangeRow = plain(new BorderLayout(8, 0));
        rangeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        wide(rangeRow, 28);
        uiStyler.styleComboBox(statsRangeCombo);
        statsRangeCombo.setSelectedItem(StatsRange.SESSION);
        statsRangeCombo.addActionListener(e -> {
            StatsRange range = (StatsRange) statsRangeCombo.getSelectedItem();
            if (range != null) {
                panelState.setStatsRange(range);
            }
        });
        rangeRow.add(statsRangeCombo, BorderLayout.WEST);

        // The rank never follows the range or the profile picked: it is always lifetime profit
        // across every character. The words sit centred in the room between the range and the
        // picture; their colour, the picture and the tooltip arrive with each stats refresh.
        JLabel rankText = new TipLabel("FLIP RANK", SwingConstants.CENTER);
        rankText.setFont(uiStyler.fontMicro(12f));
        rankText.setForeground(TEXT);
        rangeRow.add(rankText, BorderLayout.CENTER);
        // Sized for the row up front. Sized by its picture, it was laid out before the picture
        // arrived and the picture was cut off.
        JLabel rankPicture = new TipLabel("", SwingConstants.CENTER);
        rankPicture.setPreferredSize(new Dimension(28, 28));
        rangeRow.add(rankPicture, BorderLayout.EAST);
        RankUp rankUp = Bridge.get(RankUp.class);
        if (rankUp != null) {
            rankUp.panelText = rankText;
            rankUp.panelPicture = rankPicture;
        }

        statsUpdatedLabel.setForeground(MUTED_2);
        statsUpdatedLabel.setFont(uiStyler.font(10.5f));
        statsUpdatedLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // The range is the only thing pinned above the list now: it says which trades the whole
        // tab is about, so it stays put while everything it describes scrolls under it.
        header.add(rangeRow);
        header.add(Box.createVerticalStrut(8));
        return header;
    }
}
