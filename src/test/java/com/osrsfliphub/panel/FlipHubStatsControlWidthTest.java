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

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * The Profile tab's control row has 225px and three things to put in it. This
 * measures the words rather than trusting that they fit, because they did not:
 * the sort dropdown shipped reading "Compl..." and the filter beside it
 * "Recipes" when it meant "Disassembled".
 *
 * <p>Measured with the same face and size the panel resolves, so a machine
 * without Inter checks the fallback it would actually draw, and every width it
 * subtracts is read from the constant the panel lays out with.
 */
public class FlipHubStatsControlWidthTest {
    /** PluginPanel content width. */
    private static final int PANEL_WIDTH = 225;
    /** The sort direction button and the 4px BorderLayout gap it sits behind. */
    private static final int DIRECTION_BUTTON = FlipHubPanelConstants.SORT_DIRECTION_WIDTH + 4;
    private static final int ROW_GAP = FlipHubPanelConstants.TRAILING_CONTROL_GAP;
    /** A combo box spends this much on its arrow and its rounded inset. */
    private static final int COMBO_CHROME = 30;

    /**
     * The font the row actually draws in, asked of the styler rather than
     * rebuilt here. Both halves of that mattered: this measured a hardcoded 10px
     * while a dropdown has always rendered at 11px, because the renderer sets
     * the size and overrules whatever the combo was given - so the row this
     * guards was a point larger than the guard, and "Disassembled" shipped
     * clipped anyway. Asking the styler is what stops that happening twice.
     */
    private static Font rowFont() {
        return new FlipHubUiStyler().font(FlipHubUiStyler.DROPDOWN_TEXT_SIZE);
    }

    private static int widest(Font font, Object[] values) {
        FontRenderContext context = new FontRenderContext(null, true, true);
        double widest = 0;
        for (Object value : values) {
            Rectangle2D bounds = font.getStringBounds(String.valueOf(value), context);
            widest = Math.max(widest, bounds.getWidth());
        }
        return (int) Math.ceil(widest);
    }

    @Test
    public void theSortRowFitsTheSidebar() {
        Font font = rowFont();
        int sort = widest(font, StatsItemSort.values()) + COMBO_CHROME;
        int filter = widest(font, StatsRecipeFilter.values()) + COMBO_CHROME;
        int total = sort + DIRECTION_BUTTON + ROW_GAP + filter;

        System.out.println("MEASURED sort=" + sort + " direction=" + DIRECTION_BUTTON
            + " gap=" + ROW_GAP + " filter=" + filter + " total=" + total + " of " + PANEL_WIDTH);
        assertTrue(
            "sort row needs " + total + "px of " + PANEL_WIDTH
                + " (sort " + sort + ", direction " + DIRECTION_BUTTON + ", filter " + filter + ")"
                + " - shorten a label or drop the type size",
            total <= PANEL_WIDTH);
    }

    @Test
    public void everyFilterNameSurvivesItsHalfOfTheRow() {
        // The filter takes whatever the sort and its direction button leave.
        Font font = rowFont();
        int available = PANEL_WIDTH - (widest(font, StatsItemSort.values()) + COMBO_CHROME)
            - DIRECTION_BUTTON - ROW_GAP;

        for (StatsRecipeFilter filter : StatsRecipeFilter.values()) {
            int needed = widest(font, new Object[]{filter}) + COMBO_CHROME;
            assertTrue(
                "\"" + filter + "\" needs " + needed + "px and has " + available,
                needed <= available);
        }
    }

    @Test
    public void theSummaryHeadingAndItsCaretFitAboveTheTotal() {
        // The heading carries the caret that opens the slice menu, inside the
        // card's 14px inset each side.
        Font font = new Font(rowFont().getFamily(), Font.BOLD, 10);
        int needed = widest(font, new Object[]{"TOTAL PROFIT ▼"});

        assertTrue("heading needs " + needed + "px", needed <= PANEL_WIDTH - 28);
    }
}
