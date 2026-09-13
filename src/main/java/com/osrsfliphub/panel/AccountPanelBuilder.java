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

import static com.osrsfliphub.Skin.*;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingConstants;
import lombok.RequiredArgsConstructor;

/**
 * The account card: linking lives here rather than in the RuneLite settings window.
 *
 * <p>RuneLite's config panel builds its rows once and subscribes to no config event, so a status
 * written there cannot repaint while the user is looking at it - which is the whole problem this
 * view exists to solve. Here the panel owns its own repaint, so pasting a key and being told what
 * happened are the same moment.</p>
 */
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
            + "To do that this plugin will send your GE offers (item, quantity, price and time) "
            + "and your IP address to osrsfliphub.com, a 3rd party not controlled or verified by "
            + "the RuneLite developers."
            + "<br><br>"
            + "Nothing else is sent, and you can unlink at any time."
            + "</div></html>";

    @RequiredArgsConstructor
    static final class BuildResult {
        final JPanel panel;
        final JLabel stateLabel;
        final JLabel keyHintLabel;
        final JLabel messageLabel;
        final JPanel linkedRows;
        final JPanel unlinkedRows;
    }

    private final UiStyler uiStyler;
    private final PanelListener listener;
    private final ExternalLink linkCoordinator;
    private JPasswordField keyField;

    AccountPanelBuilder(UiStyler uiStyler,
                               PanelListener listener,
                               ExternalLink linkCoordinator) {
        this.uiStyler = uiStyler;
        this.listener = listener;
        this.linkCoordinator = linkCoordinator;
    }

    BuildResult build() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);

        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        JLabel heading = new JLabel("FlipHub account");
        uiStyler.styleMicroLabel(heading, 10f);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        heading.setBorder(BorderFactory.createEmptyBorder(0, 2, 8, 0));
        column.add(heading);

        JPanel card = RoundedPanel.glass(CARD_ARC);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel stateLabel = new JLabel("Not linked");
        stateLabel.setFont(uiStyler.fontSemiBold(13f));
        stateLabel.setForeground(TEXT);
        stateLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(stateLabel);

        JLabel keyHintLabel = new JLabel(" ");
        keyHintLabel.setFont(uiStyler.font(10.5f));
        keyHintLabel.setForeground(MUTED_2);
        keyHintLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        keyHintLabel.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        card.add(keyHintLabel);

        // Shown in both states: what the page holds stays true once you have one, and it is what
        // keeps the card the same shape rather than collapsing to a stub when linked.
        JPanel pitchRows = buildPitchRows();
        pitchRows.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(pitchRows);

        JPanel unlinkedRows = buildUnlinkedRows();
        unlinkedRows.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(unlinkedRows);

        JPanel linkedRows = buildLinkedRows();
        linkedRows.setAlignmentX(Component.LEFT_ALIGNMENT);
        linkedRows.setVisible(false);
        card.add(linkedRows);

        JLabel messageLabel = new JLabel(" ");
        messageLabel.setFont(uiStyler.font(10.5f));
        messageLabel.setForeground(MUTED);
        messageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        messageLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        card.add(messageLabel);

        column.add(card);
        panel.add(column, BorderLayout.NORTH);

        return new BuildResult(panel, stateLabel, keyHintLabel, messageLabel,
            linkedRows, unlinkedRows);
    }

    private JPanel buildPitchRows() {
        JPanel rows = new JPanel();
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JLabel pitch = new JLabel(wrap(
            "Your completed flips build a private personalised insight page on osrsfliphub.com."));
        pitch.setFont(uiStyler.font(10.5f));
        pitch.setForeground(MUTED);
        pitch.setAlignmentX(Component.LEFT_ALIGNMENT);
        rows.add(pitch);

        JLabel see = new JLabel("See");
        see.setFont(uiStyler.font(10.5f));
        see.setForeground(MUTED);
        see.setAlignmentX(Component.LEFT_ALIGNMENT);
        see.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        rows.add(see);

        rows.add(benefit("Earnings over time"));
        rows.add(benefit("Personalised analytics to make you a smarter flipper"));
        rows.add(benefit("Flips worth repeating"));
        rows.add(benefit("Unlock ranks and achievements"));
        rows.add(benefit("Optional: share your wins/losses"));
        rows.add(benefit("Find out where you rank against other flippers"));

        return rows;
    }

    private JPanel buildUnlinkedRows() {
        JPanel rows = new JPanel();
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));

        JLabel keyCaption = new JLabel("License key");
        uiStyler.styleMicroLabel(keyCaption, 9.5f);
        keyCaption.setAlignmentX(Component.LEFT_ALIGNMENT);
        keyCaption.setBorder(BorderFactory.createEmptyBorder(14, 0, 4, 0));
        rows.add(keyCaption);

        keyField = new JPasswordField();
        uiStyler.styleTextField(keyField);
        keyField.setAlignmentX(Component.LEFT_ALIGNMENT);
        keyField.setMaximumSize(new Dimension(Integer.MAX_VALUE, keyField.getPreferredSize().height));
        rows.add(keyField);

        JButton link = new JButton("Link account");
        uiStyler.styleGhostControl(link, 11.5f, new Insets(7, 14, 7, 14), INPUT_ARC);
        link.setForeground(ACCENT);
        link.setAlignmentX(Component.LEFT_ALIGNMENT);
        link.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(10, 0, 0, 0), link.getBorder()));
        link.addActionListener(e -> submit());
        rows.add(link);

        rows.add(externalLink("Get your key on osrsfliphub.com", 10));
        return rows;
    }

    private JPanel buildLinkedRows() {
        JPanel rows = new JPanel();
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));

        rows.add(externalLink("Open my insight page", 14));

        JButton unlink = new JButton("Unlink this device");
        uiStyler.styleGhostControl(unlink, 11f, new Insets(6, 12, 6, 12), INPUT_ARC);
        unlink.setForeground(MUTED);
        unlink.setAlignmentX(Component.LEFT_ALIGNMENT);
        unlink.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(12, 0, 0, 0), unlink.getBorder()));
        unlink.addActionListener(e -> confirmUnlink(unlink));
        rows.add(unlink);

        return rows;
    }

    private JLabel externalLink(String text, int topGap) {
        JLabel label = new JLabel(text);
        label.setFont(uiStyler.font(10.5f));
        label.setForeground(ACCENT);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(topGap, 0, 0, 0));
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        // The shared handler, so a few pixels of drift between press and release does not
        // silently swallow the click.
        label.addMouseListener(new StatsClickMouseAdapter(
            () -> linkCoordinator.openExternalUrl(DEFAULT_BASE_URL + INSIGHTS_PATH)));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                label.setForeground(TEXT);
            }

            @Override
            public void mouseExited(MouseEvent event) {
                label.setForeground(ACCENT);
            }
        });
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
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createEmptyBorder(5, 2, 0, 0));

        JLabel dot = new JLabel("•");
        dot.setFont(uiStyler.font(10.5f));
        dot.setForeground(ACCENT);
        dot.setVerticalAlignment(SwingConstants.TOP);
        dot.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
        row.add(dot, BorderLayout.WEST);

        JLabel body = new JLabel("<html><div width=155>" + text + "</div></html>");
        body.setFont(uiStyler.font(10.5f));
        body.setForeground(MUTED);
        body.setVerticalAlignment(SwingConstants.TOP);
        row.add(body, BorderLayout.CENTER);

        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    /** A sidebar is narrow, so prose has to be told where it may break. */
    private String wrap(String text) {
        return "<html><div width=175>" + text + "</div></html>";
    }

    /**
     * The consent is asked here rather than by a config warning, because the settings toggle is no
     * longer how a user opts in - this button is.
     */
    private void submit() {
        String key = keyField == null ? "" : new String(keyField.getPassword()).trim();
        if (key.isEmpty()) {
            listener.onLinkSubmitted("");
            return;
        }
        int result = JOptionPane.showOptionDialog(
            keyField,
            CONSENT_BODY,
            "Link this device to FlipHub",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null,
            new String[] {"Link account", "Cancel"},
            "Cancel");
        if (result != JOptionPane.YES_OPTION) {
            return;
        }
        keyField.setText("");
        listener.onLinkSubmitted(key);
    }

    private void confirmUnlink(Component parent) {
        int result = JOptionPane.showOptionDialog(
            parent,
            "<html><div width=300>Unlink this device from FlipHub?<br><br>"
                + "Uploads stop and the panel goes back to local-only stats. Your flip history on "
                + "this computer is kept, and your insight page stays on osrsfliphub.com."
                + "</div></html>",
            "Unlink from FlipHub",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null,
            new String[] {"Unlink", "Cancel"},
            "Cancel");
        if (result == JOptionPane.YES_OPTION) {
            listener.onUnlinkRequested();
        }
    }

    /** Applied on the EDT by the panel; the view holds no state of its own. */
    static void applyState(BuildResult view,
                           boolean linked,
                           String keyHint,
                           String message,
                           Color messageColor) {
        if (view == null) {
            return;
        }
        view.stateLabel.setText(linked ? "Linked" : "Not linked");
        view.stateLabel.setForeground(linked ? SUCCESS : TEXT);
        boolean hasHint = linked && Str.hasText(keyHint);
        view.keyHintLabel.setText(hasHint ? "Key ending " + keyHint : " ");
        view.linkedRows.setVisible(linked);
        view.unlinkedRows.setVisible(!linked);
        view.messageLabel.setText(Str.isBlank(message) ? " " : message);
        view.messageLabel.setForeground(messageColor != null ? messageColor : MUTED);
        view.panel.revalidate();
        view.panel.repaint();
    }
}
