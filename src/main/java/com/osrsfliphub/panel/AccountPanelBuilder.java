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

/**
 * The account card: linking lives here rather than in the RuneLite settings window.
 *
 * <p>RuneLite's config panel builds its rows once and subscribes to no config event, so a status
 * written there cannot repaint while the user is looking at it - which is the whole problem this
 * view exists to solve. Here the panel owns its own repaint, so pasting a key and being told what
 * happened are the same moment.</p>
 */
@RequiredArgsConstructor
final class AccountPanelBuilder {
    private static final String INSIGHTS_PATH = "/my-statistics";

    /**
     * Says what linking is for before it says what it costs. The second paragraph keeps the
     * third-party wording RuneLite's own config warning uses, because that caution is the point -
     * but a warning with no purpose attached reads as a threat rather than a choice.
     *
     * <p>Wrapped in HTML at a fixed width: hard newlines fought the dialog's own layout and left
     * the paragraphs ragged.</p>
     */
    private static final String CONSENT_BODY =
        "<html><div width=360>"
            + "Linking gives you a FlipHub account. Track and chart your earnings, get "
            + "personalised flipping insights based on your own trades to make you a better "
            + "flipper, and unlock ranks and achievements as your profit climbs."
            + "<br><br>"
            + "To do that this plugin will send your GE offers (item, quantity, price, time and "
            + "world), any recipes and moves you record, your profit totals per item, a code for "
            + "each character, a random device ID, the plugin version and your IP address to "
            + "fliphubosrs.com, a 3rd party not controlled or verified by the RuneLite developers."
            + "<br><br>"
            + "Nothing else is sent, and you can unlink at any time."
            + "</div></html>";

    private final UiStyler uiStyler;
    private final PanelListener listener;
    private final ExternalLink linkCoordinator;
    private JPasswordField keyField;
    private JPanel panel;
    private JLabel stateLabel;
    private JLabel keyHintLabel;
    private JLabel messageLabel;
    private JPanel linkedRows;
    private JPanel unlinkedRows;

    JPanel build() {
        panel = plain(new BorderLayout());

        JPanel column = stack();

        JLabel heading = new JLabel("FlipHub account");
        uiStyler.styleMicroLabel(heading, 10f);
        heading.setBorder(BorderFactory.createEmptyBorder(0, 2, 8, 0));
        column.add(heading);

        JPanel card = RoundedPanel.card(14, 14, 14, 14);

        stateLabel = styled(new JLabel("Not linked"), TEXT, uiStyler.fontSemiBold(13f));
        card.add(stateLabel);

        keyHintLabel = styled(new JLabel(" "), MUTED_2, uiStyler.font(10.5f));
        keyHintLabel.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        card.add(keyHintLabel);

        // Shown in both states: what the page holds stays true once you have one, and it is what
        // keeps the card the same shape rather than collapsing to a stub when linked.
        JPanel pitchRows = buildPitchRows();
        pitchRows.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(pitchRows);

        unlinkedRows = buildUnlinkedRows();
        unlinkedRows.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(unlinkedRows);

        linkedRows = buildLinkedRows();
        linkedRows.setAlignmentX(Component.LEFT_ALIGNMENT);
        linkedRows.setVisible(false);
        card.add(linkedRows);

        messageLabel = styled(new JLabel(" "), MUTED, uiStyler.font(10.5f));
        messageLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        card.add(messageLabel);

        column.add(card);
        panel.add(column, BorderLayout.NORTH);

        return panel;
    }

    private JPanel buildPitchRows() {
        JPanel rows = stack();
        rows.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JLabel pitch = styled(new JLabel("<html><div width=175>Your completed flips build a private"
            + " personalised insight page on fliphubosrs.com.</div></html>"), MUTED, uiStyler.font(10.5f));
        rows.add(pitch);

        JLabel see = styled(new JLabel("See"), MUTED, uiStyler.font(10.5f));
        see.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        rows.add(see);

        for (String text : new String[] {
            "Earnings over time",
            "Personalised analytics to make you a smarter flipper",
            "Flips worth repeating",
            "Unlock ranks and achievements",
            "Optional: share your wins/losses",
            "Find out where you rank against other flippers"}) {
            rows.add(benefit(text));
        }

        return rows;
    }

    private JPanel buildUnlinkedRows() {
        JPanel rows = stack();

        JLabel keyCaption = new JLabel("License key");
        uiStyler.styleMicroLabel(keyCaption, 9.5f);
        keyCaption.setBorder(BorderFactory.createEmptyBorder(14, 0, 4, 0));
        rows.add(keyCaption);

        keyField = new JPasswordField();
        uiStyler.styleTextField(keyField);
        keyField.setAlignmentX(Component.LEFT_ALIGNMENT);
        wide(keyField, keyField.getPreferredSize().height);
        rows.add(keyField);

        JButton link = new TipButton("Link account");
        uiStyler.styleGhostControl(link, 11.5f, new Insets(7, 14, 7, 14), INPUT_ARC);
        link.setForeground(ACCENT);
        link.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(10, 0, 0, 0), link.getBorder()));
        link.addActionListener(e -> submit());
        rows.add(link);

        rows.add(externalLink("Get your key on fliphubosrs.com", 10));
        return rows;
    }

    private JPanel buildLinkedRows() {
        JPanel rows = stack();

        rows.add(externalLink("Open my insight page", 14));

        JButton unlink = new TipButton("Unlink this device");
        uiStyler.styleGhostControl(unlink, 11f, new Insets(6, 12, 6, 12), INPUT_ARC);
        unlink.setForeground(MUTED);
        unlink.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(12, 0, 0, 0), unlink.getBorder()));
        unlink.addActionListener(e -> confirmUnlink(unlink));
        rows.add(unlink);

        return rows;
    }

    private JLabel externalLink(String text, int topGap) {
        JLabel label = styled(new JLabel(text), ACCENT, uiStyler.font(10.5f));
        label.setBorder(BorderFactory.createEmptyBorder(topGap, 0, 0, 0));
        label.setCursor(HAND);
        // The shared handler, so a few pixels of drift between press and release does not
        // silently swallow the click.
        label.addMouseListener(new StatsClickMouseAdapter(
            () -> linkCoordinator.openExternalUrl(DEFAULT_BASE_URL + INSIGHTS_PATH)));
        label.addMouseListener(new TabHoverAdapter(label, ACCENT));
        return label;
    }

    /**
     * One reason to link, marked with the action colour so the list reads as a set.
     *
     * <p>The bullet is its own component rather than part of the text: inside one HTML label a
     * wrapped second line runs back under the bullet, which breaks the left edge of the list.
     * Holding the bullet in a fixed west column gives the text a proper hanging indent.</p>
     */
    private JPanel benefit(String text) {
        JPanel row = plain(new BorderLayout());
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createEmptyBorder(5, 2, 0, 0));

        JLabel dot = styled(new JLabel("•"), ACCENT, uiStyler.font(10.5f));
        dot.setVerticalAlignment(SwingConstants.TOP);
        dot.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
        row.add(dot, BorderLayout.WEST);

        JLabel body = styled(
            new JLabel("<html><div width=155>" + text + "</div></html>"), MUTED, uiStyler.font(10.5f));
        body.setVerticalAlignment(SwingConstants.TOP);
        row.add(body, BorderLayout.CENTER);

        wide(row, row.getPreferredSize().height);
        return row;
    }

    /**
     * The consent is asked here rather than by a config warning, because the settings toggle is no
     * longer how a user opts in - this button is.
     */
    private void submit() {
        String key = new String(keyField.getPassword()).trim();
        if (key.isEmpty()) {
            listener.onLinkSubmitted("");
            return;
        }
        if (confirm(keyField, CONSENT_BODY, "Link this device to FlipHub", "Link account")) {
            keyField.setText("");
            listener.onLinkSubmitted(key);
        }
    }

    /** The question both buttons ask before they do anything, with Cancel as the answer it starts on. */
    private static boolean confirm(Component parent, String body, String title, String yes) {
        return JOptionPane.showOptionDialog(parent, body, title, JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE, null, new String[] {yes, "Cancel"}, "Cancel") == JOptionPane.YES_OPTION;
    }

    private void confirmUnlink(Component parent) {
        if (confirm(parent,
            "<html><div width=300>Unlink this device from FlipHub?<br><br>"
                + "Uploads stop and the panel goes back to local-only stats. Your flip history on "
                + "this computer is kept, and your insight page stays on fliphubosrs.com."
                + "</div></html>",
            "Unlink from FlipHub", "Unlink")) {
            listener.onUnlinkRequested();
        }
    }

    /** Applied on the EDT by the panel; the view holds no state of its own. */
    void applyState(boolean linked, String keyHint, String message, Color messageColor) {
        stateLabel.setText(linked ? "Linked" : "Not linked");
        stateLabel.setForeground(linked ? SUCCESS : TEXT);
        boolean hasHint = linked && Str.hasText(keyHint);
        keyHintLabel.setText(hasHint ? "Key ending " + keyHint : " ");
        linkedRows.setVisible(linked);
        unlinkedRows.setVisible(!linked);
        messageLabel.setText(Str.isBlank(message) ? " " : message);
        messageLabel.setForeground(messageColor != null ? messageColor : MUTED);
        panel.revalidate();
        panel.repaint();
    }
}
