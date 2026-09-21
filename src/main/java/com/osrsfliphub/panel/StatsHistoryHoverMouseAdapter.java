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

import java.awt.Component;
import java.awt.event.*;
import javax.swing.JLabel;
import lombok.RequiredArgsConstructor;
import static com.osrsfliphub.Skin.*;

@RequiredArgsConstructor
final class StatsHistoryHoverMouseAdapter extends MouseAdapter {
    private final JLabel title;
    private final JLabel chevron;

    /**
     * Hover is answered from where the pointer actually is, not counted.
     *
     * <p>Counting entries and exits assumed they arrive in pairs. They do not: after the list is
     * rebuilt the pointer is re-entered synthetically, which leaves AWT still believing it is
     * elsewhere, so the next real movement delivers a second enter with no exit between. The
     * count then never returned to zero and the header stayed lit after the pointer had left.
     */
    @Override
    public void mouseEntered(MouseEvent e) {
        applyHover(true);
    }

    @Override
    public void mouseExited(MouseEvent e) {
        applyHover(isPointerStillInside(e));
    }

    private boolean isPointerStillInside(MouseEvent e) {
        Component source = e != null && e.getComponent() != null ? e.getComponent() : null;
        return source != null && source.contains(e.getPoint());
    }

    private void applyHover(boolean hovered) {
        if (hovered) {
            title.setForeground(TEXT);
            chevron.setForeground(TEXT);
            return;
        }
        // Back to the ramp the two were built at, not to one shared tier: the chevron is
        // ornament on --muted-2 and the title is a label on --muted.
        title.setForeground(MUTED);
        chevron.setForeground(MUTED_2);
    }
}
