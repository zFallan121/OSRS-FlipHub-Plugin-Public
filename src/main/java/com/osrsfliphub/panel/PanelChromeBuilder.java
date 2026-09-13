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
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.border.EmptyBorder;
import net.runelite.client.util.ImageUtil;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class PanelChromeBuilder {
    private static final String WORDMARK = "FlipHub OSRS";

    private final UiStyler uiStyler;

    JPanel buildHeader(JButton profileButton, Runnable onProfileMenuRequested) {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel(WORDMARK);
        title.setForeground(TEXT);
        title.setFont(uiStyler.fontBold(15f));
        Icon logo = buildWordmarkLogo(title.getFont());
        if (logo != null) {
            title.setIcon(logo);
            title.setIconTextGap(6);
        }

        // Quiet, and NOT the micro-label: this holds a character name as often as it holds
        // "Accountwide", and the micro-label uppercases what it is given. A name is not a section
        // label. The colour is set by setProfileHeader, which uses it to say whether it is linked.
        profileButton.setForeground(MUTED_2);
        profileButton.setFont(uiStyler.font(10.5f));
        profileButton.setBorder(new EmptyBorder(2, 6, 2, 6));
        profileButton.setContentAreaFilled(false);
        profileButton.setFocusPainted(false);
        profileButton.setOpaque(false);
        profileButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        profileButton.addActionListener(e -> onProfileMenuRequested.run());

        JPanel statusWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        statusWrap.setOpaque(false);
        statusWrap.add(profileButton);

        header.add(title, BorderLayout.WEST);
        header.add(statusWrap, BorderLayout.EAST);
        return header;
    }

    /**
     * The mark reads at the wordmark's own height, so it is measured off the rendered string
     * rather than off the point size: the visual bounds of "FlipHub OSRS" are the ascender of the
     * F down to the descender of the p, which is the height the user sees. A point size would
     * overshoot it by the font's internal leading and sit proud of the type.
     */
    private Icon buildWordmarkLogo(Font font) {
        try {
            // getResourceAsStream, not getResource: the plugin runs from inside a jar on the
            // hub, where a resource URL behaves differently than the file URL seen in the IDE.
            BufferedImage source = ImageUtil.loadImageResource(
                getClass(), "/com/osrsfliphub/fliphub-icon.png");
            if (source == null) {
                return null;
            }
            FontRenderContext context = new FontRenderContext(null, true, true);
            Rectangle2D ink = font.createGlyphVector(context, WORDMARK).getVisualBounds();
            LineMetrics metrics = font.getLineMetrics(WORDMARK, context);
            int size = Math.max(10, (int) Math.floor(ink.getHeight()));
            return new ImageIcon(seatOnBaseline(ImageUtil.resizeImage(source, size, size), ink, metrics));
        } catch (Exception ignored) {
            // The wordmark stands on its own if the resource is missing.
            return null;
        }
    }

    /**
     * A label centres its icon and its text against the same height, but the text's INK is not
     * centred inside its own block: the ascent carries the font's leading and accent space above
     * the cap height, so the letters sit lower in the block than the middle. Centring the mark
     * geometrically therefore leaves it riding above the type.
     *
     * <p>There is no per-icon vertical offset on JLabel, so the correction goes into the image:
     * padding it by twice the gap and drawing the mark against the far edge moves the mark by
     * exactly the gap once the label centres the whole thing.
     */
    private BufferedImage seatOnBaseline(BufferedImage mark, Rectangle2D ink, LineMetrics metrics) {
        double blockCentre = (metrics.getAscent() + metrics.getDescent()) / 2.0;
        double inkCentre = metrics.getAscent() + ink.getY() + ink.getHeight() / 2.0;
        int drop = (int) Math.round(inkCentre - blockCentre);
        if (drop == 0) {
            return mark;
        }
        int pad = Math.abs(drop) * 2;
        BufferedImage seated =
            new BufferedImage(mark.getWidth(), mark.getHeight() + pad, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = seated.createGraphics();
        g.drawImage(mark, 0, drop > 0 ? pad : 0, null);
        g.dispose();
        return seated;
    }

    JButton buildDiscordButton(Runnable onDiscordRequested) {
        JButton button = new TipButton();
        button.setToolTipText("Join the FlipHub Discord");
        Icon mark = buildDiscordMark();
        if (mark != null) {
            // A mark needs no chrome: the glyph is the affordance, and a ghost border around it
            // would read as a fourth tab sitting beside the three real ones.
            button.setIcon(mark);
            button.setContentAreaFilled(false);
            button.setBorderPainted(false);
            button.setFocusPainted(false);
            button.setOpaque(false);
            // The same trailing slot the bookmark star and the sort mark take in the two rows
            // below, so the three marks stand on one centre line down the panel's right edge.
            // Side padding cannot do that job: it centres the mark in a slot of its own width,
            // which is the icon's width plus the padding, and no icon is exactly the star's.
            // The slot is the padding - the mark is narrower than it, and the room left over is
            // split evenly by the button's own centring.
            button.setBorder(BorderFactory.createEmptyBorder());
            Dimension slot = new Dimension(TRAILING_CONTROL_WIDTH, button.getPreferredSize().height);
            button.setPreferredSize(slot);
            button.setMinimumSize(slot);
            button.setMaximumSize(slot);
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        } else {
            // The word is the fallback, so the control never becomes an empty box - and a word
            // does want the border back.
            button.setText("Discord");
            uiStyler.styleGhostControl(button, 10f, new Insets(4, 8, 4, 8));
        }
        button.setForeground(MUTED);
        button.addActionListener(e -> onDiscordRequested.run());
        return button;
    }

    /**
     * The asset is authored at its final size and tint - a muted white, so the mark sits in the
     * control set rather than importing a second brand colour the palette has no room for.
     *
     * <p>Deliberately used as-is. ImageUtil.fillImage rewrites every non-transparent pixel to the
     * flat fill colour, which discards the edge alpha and leaves a hard blocky glyph, and
     * resizeImage would scale an already-scaled bitmap a second time.</p>
     */
    private Icon buildDiscordMark() {
        try {
            BufferedImage source = ImageUtil.loadImageResource(
                getClass(), "/com/osrsfliphub/discord-icon.png");
            if (source == null) {
                return null;
            }
            return new ImageIcon(source);
        } catch (Exception ignored) {
            return null;
        }
    }

    JPanel buildTabs(JToggleButton flippingTab,
                     JToggleButton statsTab,
                     JToggleButton linkTab,
                     JButton discordButton,
                     Consumer<String> onSwitchRequested) {
        // BorderLayout, not FlowLayout: three tabs plus the mark overflow the sidebar width, and
        // FlowLayout answers that by wrapping the last child onto a row the header never shows.
        JPanel tabs = new JPanel(new BorderLayout());
        tabs.setOpaque(false);

        // BoxLayout, not FlowLayout: FlowLayout is what wrapped the mark onto an unseen second
        // row. Spacing comes from each control's own padding, so the mark sits the same distance
        // from Link as Link does from Profile.
        JPanel tabGroup = new JPanel();
        tabGroup.setOpaque(false);
        tabGroup.setLayout(new BoxLayout(tabGroup, BoxLayout.X_AXIS));

        ButtonGroup group = new ButtonGroup();
        group.add(flippingTab);
        group.add(statsTab);
        group.add(linkTab);

        uiStyler.styleTab(flippingTab, true);
        uiStyler.styleTab(statsTab, false);
        uiStyler.styleTab(linkTab, false);

        flippingTab.addActionListener(e -> onSwitchRequested.accept("flipping"));
        statsTab.addActionListener(e -> onSwitchRequested.accept("stats"));
        linkTab.addActionListener(e -> onSwitchRequested.accept("account"));

        flippingTab.setSelected(true);

        tabs.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        tabGroup.add(flippingTab);
        tabGroup.add(statsTab);
        tabGroup.add(linkTab);
        tabs.add(tabGroup, BorderLayout.WEST);
        // Pinned east so the mark stands on the same vertical as the bookmark toggle in the
        // search row below, which is east of a container with the same insets. It is pinned to
        // that toggle's slot width too, so the two glyphs share a centre line rather than only
        // an outer edge.
        tabs.add(discordButton, BorderLayout.EAST);
        return tabs;
    }
}
