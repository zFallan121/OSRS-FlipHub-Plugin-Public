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

import java.awt.image.BufferedImage;
import java.util.Map;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

final class FlipHubItemIconResolver {
    private final ItemManager itemManager;
    private final Map<Integer, ImageIcon> iconCache;

    FlipHubItemIconResolver(ItemManager itemManager, Map<Integer, ImageIcon> iconCache) {
        this.itemManager = itemManager;
        this.iconCache = iconCache;
    }

    void setItemIcon(JLabel label, int itemId) {
        if (label == null) {
            return;
        }
        if (itemManager == null) {
            label.setIcon(null);
            return;
        }
        BufferedImage image = itemManager.getImage(itemId);
        if (image == null) {
            label.setIcon(null);
            return;
        }
        if (image instanceof AsyncBufferedImage) {
            AsyncBufferedImage async = (AsyncBufferedImage) image;
            async.addTo(label);
            label.setIcon(new ImageIcon(async));
            return;
        }
        ImageIcon cached = iconCache.get(itemId);
        if (cached == null) {
            BufferedImage resized = ImageUtil.resizeImage(image, 32, 32);
            cached = new ImageIcon(resized);
            iconCache.put(itemId, cached);
        }
        label.setIcon(cached);
    }
}
