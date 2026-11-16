package com.cloudzone.shopbackend.common.enums;

/** How a user account was originally registered / authenticates. */
public enum AuthProvider {
    LOCAL,   // email + password
    GOOGLE,
    APPLE
}
