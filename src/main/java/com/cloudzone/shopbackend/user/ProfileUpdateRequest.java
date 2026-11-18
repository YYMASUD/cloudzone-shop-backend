package com.cloudzone.shopbackend.user;

import jakarta.validation.constraints.Size;

/**
 * Body of {@code PATCH /api/users/me}.
 *
 * <p>Every field optional — {@code null} means "leave as is". {@code email} is deliberately
 * absent: the login identity is tied to the JWT {@code sub} and to
 * {@code email_verified}; changing it would invalidate the caller's token silently and
 * un-verify the account. Email changes are a separate verified-flow concern, out of scope
 * for a plain profile edit.
 */
public record ProfileUpdateRequest(
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Size(max = 30) String phone) {
}
