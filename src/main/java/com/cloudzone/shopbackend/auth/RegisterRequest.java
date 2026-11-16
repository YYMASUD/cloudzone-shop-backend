package com.cloudzone.shopbackend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The self-service registration payload. Only the fields a client legitimately supplies
 * are present — role, auth provider and the enabled/verified flags are assigned by the
 * service so a request can never self-grant ADMIN or mark itself verified.
 */
public record RegisterRequest(

        @NotBlank(message = "email must not be blank")
        @Email(message = "must be a valid email")
        @Size(max = 255, message = "is too long")
        String email,

        @NotBlank(message = "password must not be blank")
        @Size(min = 8, max = 72, message = "must be 8-72 characters") // 72 = BCrypt's hard cap
        String password,

        @Size(max = 100, message = "is too long")
        String firstName,

        @Size(max = 100, message = "is too long")
        String lastName) {
}
