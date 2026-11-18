package com.cloudzone.shopbackend.user;

import com.cloudzone.shopbackend.auth.UserProfileResponse;
import com.cloudzone.shopbackend.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The caller's own profile fields.
 *
 * <p>Read/write of firstName/lastName/phone only. {@code email}, {@code role},
 * {@code enabled} and {@code email_verified} are deliberately not editable here — the
 * first three are identity/admin concerns, and the last is owned by the verification
 * flow (Step 3's OTP/email-confirm, still to come).
 */
@Service
public class ProfileService {

    private final UserRepository userRepository;

    public ProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Applies the non-null fields of the patch to the caller's profile.
     * {@code null} is "leave as is"; a supplied value may be blank (clearing a name is
     * legitimate — these columns are nullable).
     */
    @Transactional
    public UserProfileResponse update(Long userId, ProfileUpdateRequest patch) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "user_not_found",
                        "User no longer exists"));

        if (patch.firstName() != null) {
            user.setFirstName(patch.firstName());
        }
        if (patch.lastName() != null) {
            user.setLastName(patch.lastName());
        }
        if (patch.phone() != null) {
            user.setPhone(patch.phone());
        }

        return new UserProfileResponse(
                user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getRole(), user.isEmailVerified());
    }
}
