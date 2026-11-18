package com.cloudzone.shopbackend.user;

import com.cloudzone.shopbackend.auth.UserProfileResponse;
import com.cloudzone.shopbackend.common.security.AuthPrincipal;
import com.cloudzone.shopbackend.common.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's own account surface under {@code /api/users}.
 *
 * <p>{@code GET /auth/me} already returns the profile (it doubles as a token-path probe);
 * this controller exists for the <em>mutation</em> side of the profile. Ownership is the
 * verified JWT's {@code uid} — there is no {@code /api/users/{id}} that could expose or
 * edit another account.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final ProfileService profileService;
    private final JwtService jwtService;

    public UserController(ProfileService profileService, JwtService jwtService) {
        this.profileService = profileService;
        this.jwtService = jwtService;
    }

    /**
     * Partially updates the caller's profile. {@code firstName}/{@code lastName}/{@code
     * phone} are editable; {@code email} and {@code role} are not (see
     * {@link ProfileUpdateRequest}).
     */
    @PatchMapping("/me")
    public UserProfileResponse updateMe(
            @Valid @RequestBody ProfileUpdateRequest patch,
            JwtAuthenticationToken authentication) {
        AuthPrincipal principal = jwtService.toPrincipal(authentication.getToken());
        return profileService.update(principal.userId(), patch);
    }
}
