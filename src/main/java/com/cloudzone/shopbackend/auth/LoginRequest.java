package com.cloudzone.shopbackend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Email/password exchange. Present and well-formed is all the request validates; whether the credentials are right is the service's call, deliberately indistinguishable at the edge. */
public record LoginRequest(

        @NotBlank(message = "email must not be blank")
        @Email(message = "must be a valid email")
        String email,

        @NotBlank(message = "password must not be blank")
        String password) {
}
