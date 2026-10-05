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

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Right-clicking an item's name in the side panel copies it, ready to paste into the Grand
 * Exchange search. The left click keeps what it did: it opens the item on the website.
 */
public class ItemNameCopyTest {
    private final List<String> copied = new ArrayList<>();
    private Consumer<String> realClipboard;
    private EllipsisLabel name;

    @Before
    public void useAFakeClipboard() {
        realClipboard = EllipsisLabel.clipboard;
        EllipsisLabel.clipboard = copied::add;
        name = new EllipsisLabel("Dragon hunter crossbow");
        name.setSize(200, 18);
        name.copyOnRightClick();
    }

    @After
    public void putTheClipboardBack() {
        EllipsisLabel.clipboard = realClipboard;
    }

    @Test
    public void aRightClickCopiesTheNameAndSaysSo() {
        rightClick(20, 9);

        assertEquals(List.of("Dragon hunter crossbow"), copied);
        assertEquals("Copied", name.getText());
    }

    @Test
    public void theWholeNameIsCopiedEvenWhenTheCardShowsOnlyPartOfIt() {
        name.setSize(60, 18);
        assertTrue(name.getText().endsWith("..."));

        rightClick(20, 9);

        assertEquals(List.of("Dragon hunter crossbow"), copied);
    }

    @Test
    public void aLeftClickCopiesNothing() {
        click(MouseEvent.BUTTON1, 20, 9);

        assertTrue(copied.isEmpty());
        assertEquals("Dragon hunter crossbow", name.getText());
    }

    @Test
    public void lettingGoOffTheNameCopiesNothing() {
        rightClick(400, 9);

        assertTrue(copied.isEmpty());
    }

    /** The panel writes every name again on each refresh, and one can land while "Copied" shows. */
    @Test
    public void aRefreshWhileCopiedIsShowingKeepsTheWordAndTheNextCopyIsTheName() {
        rightClick(20, 9);
        name.setText("Dragon hunter crossbow");
        assertEquals("Copied", name.getText());

        rightClick(20, 9);

        assertEquals(List.of("Dragon hunter crossbow", "Dragon hunter crossbow"), copied);
    }

    @Test
    public void aClipboardHeldByAnotherProgramDoesNotSayCopied() {
        EllipsisLabel.clipboard = text -> {
            throw new IllegalStateException("cannot open system clipboard");
        };

        rightClick(20, 9);

        assertEquals("Dragon hunter crossbow", name.getText());
    }

    private void rightClick(int x, int y) {
        click(MouseEvent.BUTTON3, x, y);
    }

    private void click(int button, int x, int y) {
        int mask = button == MouseEvent.BUTTON1 ? MouseEvent.BUTTON1_DOWN_MASK : MouseEvent.BUTTON3_DOWN_MASK;
        for (MouseListener listener : name.getMouseListeners()) {
            listener.mousePressed(new MouseEvent(name, MouseEvent.MOUSE_PRESSED, 0L, mask, x, y, 1, false, button));
            listener.mouseReleased(new MouseEvent(name, MouseEvent.MOUSE_RELEASED, 0L, mask, x, y, 1, false, button));
        }
    }
}
