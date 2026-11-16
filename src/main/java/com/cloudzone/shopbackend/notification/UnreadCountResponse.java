package com.cloudzone.shopbackend.notification;

/** The unread badge the client polls; {@code unread == 0} renders no badge at all. */
public record UnreadCountResponse(long unread) {
}