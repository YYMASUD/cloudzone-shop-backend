package com.cloudzone.shopbackend.auth;

import com.cloudzone.shopbackend.common.enums.Role;

/**
 * The caller's own profile, as returned by {@code GET /auth/me}. A read-model over the
 * authenticated identity — sensitive fields like the password hash are never present.
 */
public record UserProfileResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        Role role,
        boolean emailVerified) {
}
