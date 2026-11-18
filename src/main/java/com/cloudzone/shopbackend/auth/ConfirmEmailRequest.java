package com.cloudzone.shopbackend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Confirms the email-verification code the caller received.
 *
 * @param code the 6-digit code from the verification email
 */
public record ConfirmEmailRequest(
        @NotBlank @Pattern(regexp = "\\d{6}", message = "code must be 6 digits") String code) {
}
