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

final class ProfileSelectionResolverService {
    interface Hooks {
        boolean isClientLoggedIn();
    }

    private final long accountwideKey;
    private final String accountwideKeyString;
    private final Hooks hooks;

    ProfileSelectionResolverService(long accountwideKey, String accountwideKeyString, Hooks hooks) {
        this.accountwideKey = accountwideKey;
        this.accountwideKeyString = accountwideKeyString != null ? accountwideKeyString : "accountwide";
        this.hooks = hooks;
    }

    String resolveSelectedProfileKeyForUi(ProfileSelectionState state) {
        if (state == null) {
            return accountwideKeyString;
        }
        return state.resolveSelectedProfileKeyForUi(isClientLoggedIn());
    }

    long resolveSelectedProfileKey(ProfileSelectionState state) {
        if (state == null) {
            return accountwideKey;
        }
        return state.resolveSelectedProfileKey(isClientLoggedIn(), accountwideKey);
    }

    String buildProfileKey(ProfileSelectionState state, long accountHash) {
        if (state == null) {
            return accountHash > 0 ? "hash_" + accountHash : accountwideKeyString;
        }
        return state.buildProfileKey(accountHash);
    }

    private boolean isClientLoggedIn() {
        return hooks != null && hooks.isClientLoggedIn();
    }
}
