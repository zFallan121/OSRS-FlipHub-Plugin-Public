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
import net.runelite.api.widgets.Widget;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GeHistoryWidgetReadServiceTest {
    @Test
    public void hasCompleteWidgetGroupsRequiresPositiveMultipleOfSix() {
        GeHistoryWidgetReadService service = new GeHistoryWidgetReadService();

        assertFalse(service.hasCompleteWidgetGroups(null));
        assertFalse(service.hasCompleteWidgetGroups(new Widget[0]));
        assertFalse(service.hasCompleteWidgetGroups(new Widget[5]));
        assertTrue(service.hasCompleteWidgetGroups(new Widget[6]));
        assertTrue(service.hasCompleteWidgetGroups(new Widget[12]));
    }

    @Test
    public void tryParseReadyTradesReturnsNullWhenWidgetsAreIncomplete() {
        GeHistoryWidgetReadService service = new GeHistoryWidgetReadService();

        assertNull(service.tryParseReadyTrades(null));
        assertNull(service.tryParseReadyTrades(new Widget[5]));
    }

    @Test
    public void tryParseReadyTradesReturnsParsedListWhenWidgetsAreComplete() {
        GeHistoryWidgetReadService service = new GeHistoryWidgetReadService();

        List<GeHistoryTrade> trades = service.tryParseReadyTrades(new Widget[6]);
        assertNotNull(trades);
    }
}
