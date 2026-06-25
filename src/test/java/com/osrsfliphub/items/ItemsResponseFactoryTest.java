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

import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ItemsResponseFactoryTest {
    @Test
    public void emptyBuildsZeroItemResponse() {
        ApiClient.ItemsResponse response = ItemsResponseFactory.empty(123L, null);

        assertEquals(0, response.total_items);
        assertEquals(1, response.total_pages);
        assertEquals(1, response.page);
        assertTrue(response.items.isEmpty());
    }

    @Test
    public void fromItemsBuildsSinglePageResponse() {
        FlipHubItem item = new FlipHubItem();
        item.item_id = 4151;
        ApiClient.ItemsResponse response = ItemsResponseFactory.fromItems(Collections.singletonList(item), 500L, 100L);

        assertEquals(1, response.total_items);
        assertEquals(1, response.total_pages);
        assertEquals(1, response.page_size);
        assertEquals(4151, response.items.get(0).item_id);
    }

    @Test
    public void pagedCopiesMetadata() {
        ApiClient.ItemsResponse response = ItemsResponseFactory.paged(Collections.emptyList(), 2, 8, 16, 2, 999L, 50L);

        assertEquals(2, response.page);
        assertEquals(8, response.page_size);
        assertEquals(16, response.total_items);
        assertEquals(2, response.total_pages);
        assertEquals(999L, response.as_of_ms);
        assertEquals(Long.valueOf(50L), response.price_cache_ms);
    }
}
