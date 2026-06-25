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

import java.lang.reflect.Method;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class GeLifecyclePluginDirtyFlagTest
{
    @Test
    public void ensureProfileLoadedDoesNotClearAccountwideDirtyFlag() throws Exception
    {
        GeLifecyclePlugin plugin = new GeLifecyclePlugin();
        Method markDirty = GeLifecyclePlugin.class.getDeclaredMethod("markAccountwideUploadDirty");
        markDirty.setAccessible(true);
        markDirty.invoke(plugin);

        Method getUploader = GeLifecyclePlugin.class.getDeclaredMethod("getAccountwideSummaryUploader");
        getUploader.setAccessible(true);
        AccountwideSummaryUploader uploader = (AccountwideSummaryUploader) getUploader.invoke(plugin);

        Method ensureProfileLoaded = GeLifecyclePlugin.class.getDeclaredMethod("ensureProfileLoaded", long.class);
        ensureProfileLoaded.setAccessible(true);
        ensureProfileLoaded.invoke(plugin, 0L);

        assertTrue("Accountwide upload dirty flag should only clear after successful upload", uploader.isDirty());
    }
}
