package com.osrsfliphub;

import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * One built item row, and the parts of it a refresh is allowed to change.
 */
final class FlipHubItemCard {
    final JPanel panel;
    final int itemId;
    /** The offer preview draws its values tighter, so a card built for one cannot serve the list. */
    final boolean compactRightPadding;
    final JLabel nameLabel;
    final JLabel sellValue;
    final JLabel buyValue;
    final JLabel lastSellValue;
    final JLabel lastBuyValue;
    final JLabel marginValue;
    final JLabel marginLimitValue;
    final JLabel roiValue;
    final JLabel limitRemainingValue;
    final JLabel countdownValue;
    /** Repaints the bookmark star from the store, in whichever state the pointer has it. */
    final Runnable repaintBookmark;
    AgePairEntry agePair;
    CountdownEntry countdown;

    FlipHubItemCard(JPanel panel,
                    int itemId,
                    boolean compactRightPadding,
                    JLabel nameLabel,
                    JLabel sellValue,
                    JLabel buyValue,
                    JLabel lastSellValue,
                    JLabel lastBuyValue,
                    JLabel marginValue,
                    JLabel marginLimitValue,
                    JLabel roiValue,
                    JLabel limitRemainingValue,
                    JLabel countdownValue,
                    Runnable repaintBookmark) {
        this.panel = panel;
        this.itemId = itemId;
        this.compactRightPadding = compactRightPadding;
        this.nameLabel = nameLabel;
        this.sellValue = sellValue;
        this.buyValue = buyValue;
        this.lastSellValue = lastSellValue;
        this.lastBuyValue = lastBuyValue;
        this.marginValue = marginValue;
        this.marginLimitValue = marginLimitValue;
        this.roiValue = roiValue;
        this.limitRemainingValue = limitRemainingValue;
        this.countdownValue = countdownValue;
        this.repaintBookmark = repaintBookmark;
    }
}
