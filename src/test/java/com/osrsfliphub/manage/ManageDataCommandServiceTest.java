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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ManageDataCommandServiceTest {
    @Test
    public void buildDialogIncludesExpectedOptionsForUnlinkedState() {
        ManageDataCommand service = new ManageDataCommand();

        ManageDataCommand.DialogModel model = service.buildDialog("Profile 1", false);

        assertTrue(model.body.contains("Selected profile: Profile 1"));
        assertTrue(model.options.contains(ManageDataCommand.OPTION_WIPE_PROFILE));
        assertTrue(model.options.contains(ManageDataCommand.OPTION_WIPE_ALL_LOCAL));
        assertFalse(model.options.contains(ManageDataCommand.OPTION_WIPE_WEBSITE));
        assertEquals(ManageDataCommand.OPTION_WIPE_PROFILE, model.defaultOption);
    }

    @Test
    public void buildDialogIncludesWebsiteOptionAndWarningWhenLinked() {
        ManageDataCommand service = new ManageDataCommand();

        ManageDataCommand.DialogModel model = service.buildDialog("Profile 2", true);

        assertTrue(model.options.contains(ManageDataCommand.OPTION_WIPE_WEBSITE));
        assertTrue(model.body.contains("Website wipe deletes your My Statistics data"));
    }

    @Test
    public void resolveActionMapsKnownOptionsAndDefaultsToCancel() {
        ManageDataCommand service = new ManageDataCommand();

        assertEquals(
            ManageDataCommand.Action.WIPE_SELECTED_PROFILE,
            service.resolveAction(ManageDataCommand.OPTION_WIPE_PROFILE)
        );
        assertEquals(
            ManageDataCommand.Action.WIPE_ALL_LOCAL_PROFILES,
            service.resolveAction(ManageDataCommand.OPTION_WIPE_ALL_LOCAL)
        );
        assertEquals(
            ManageDataCommand.Action.WIPE_WEBSITE,
            service.resolveAction(ManageDataCommand.OPTION_WIPE_WEBSITE)
        );
        assertEquals(ManageDataCommand.Action.CANCEL, service.resolveAction("unknown"));
        assertEquals(ManageDataCommand.Action.CANCEL, service.resolveAction(null));
    }

    @Test
    public void selectedProfileValidationAndLabelResolutionAreStable() {
        ManageDataCommand service = new ManageDataCommand();

        assertEquals(
            "Select a non-accountwide profile to wipe a single profile.",
            service.validateSelectedProfileSelection(0L, 0L)
        );
        assertNull(service.validateSelectedProfileSelection(123L, 0L));
        assertEquals("Named Profile", service.resolveProfileLabel(123L, " Named Profile "));
        assertEquals("Profile 456", service.resolveProfileLabel(456L, " "));
    }

    @Test
    public void confirmationMatchingIgnoresCaseAndWhitespace() {
        ManageDataCommand service = new ManageDataCommand();
        assertTrue(service.confirmationMatches("  wipe   all ", "WIPE ALL"));
        assertFalse(service.confirmationMatches("WIPE SOME", "WIPE ALL"));
    }
}
