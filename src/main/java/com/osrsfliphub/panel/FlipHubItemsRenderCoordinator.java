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

import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;

final class FlipHubItemsRenderCoordinator {
    @FunctionalInterface
    interface RefreshTextBuilder {
        String build(long asOfMs, Long priceCacheMs);
    }

    void renderItems(JPanel listPanel,
                     FlipHubAgeTooltipCoordinator ageTooltipCoordinator,
                     FlipHubItemListContentRenderer itemListContentRenderer,
                     FlipHubItem offerPreviewItem,
                     long offerAsOfMs,
                     List<FlipHubItem> lastItems,
                     long lastAsOfMs,
                     boolean showBookmarkedOnly,
                     JLabel refreshLabel,
                     Long lastPriceCacheMs,
                     Long offerPriceCacheMs,
                     RefreshTextBuilder refreshTextBuilder,
                     JPanel footerPanel,
                     JScrollPane scrollPane) {
        if (listPanel == null || ageTooltipCoordinator == null || itemListContentRenderer == null) {
            return;
        }

        listPanel.removeAll();
        ageTooltipCoordinator.clearEntriesAndHide();
        itemListContentRenderer.renderList(
            listPanel,
            offerPreviewItem,
            offerAsOfMs,
            lastItems,
            lastAsOfMs,
            showBookmarkedOnly
        );

        long refreshAsOf = lastAsOfMs > 0 ? lastAsOfMs : offerAsOfMs;
        Long refreshCacheMs = lastPriceCacheMs != null ? lastPriceCacheMs : offerPriceCacheMs;
        if (refreshAsOf > 0 && refreshLabel != null && refreshTextBuilder != null) {
            refreshLabel.setText(refreshTextBuilder.build(refreshAsOf, refreshCacheMs));
        }
        if (footerPanel != null) {
            footerPanel.setVisible(offerPreviewItem == null);
        }

        if (offerPreviewItem != null && scrollPane != null) {
            JScrollBar bar = scrollPane.getVerticalScrollBar();
            if (bar != null) {
                bar.setValue(0);
            }
        }

        ageTooltipCoordinator.ensureCountdownTimer();
        listPanel.revalidate();
        listPanel.repaint();
    }
}
