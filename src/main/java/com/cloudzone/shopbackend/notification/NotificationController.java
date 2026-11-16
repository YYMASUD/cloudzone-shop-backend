package com.cloudzone.shopbackend.notification;

import com.cloudzone.shopbackend.common.security.AuthPrincipal;
import com.cloudzone.shopbackend.common.security.JwtService;
import com.cloudzone.shopbackend.common.web.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's notification feed. Ownership always comes from the verified JWT; there is
 * no user-id path parameter that could expose another account's notifications.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final JwtService jwtService;

    public NotificationController(NotificationService notificationService, JwtService jwtService) {
        this.notificationService = notificationService;
        this.jwtService = jwtService;
    }

    /** The caller's feed, newest first; oversized {@code size} is clamped. */
    @GetMapping
    public PageResponse<NotificationResponse> list(Pageable pageable, JwtAuthenticationToken authentication) {
        return notificationService.list(userId(authentication), pageable);
    }

    /** The unread badge count for the caller. */
    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(JwtAuthenticationToken authentication) {
        return notificationService.unreadCount(userId(authentication));
    }

    /** Marks one own notification read; a foreign id is a 404, not a 403. */
    @PatchMapping("/{id}/read")
    public NotificationResponse markRead(
            @PathVariable Long id,
            JwtAuthenticationToken authentication) {
        return notificationService.markRead(userId(authentication), id);
    }

    /** Marks every unread notification of the caller read; returns the affected count. */
    @PatchMapping("/read-all")
    public MarkAllReadResponse markAllRead(JwtAuthenticationToken authentication) {
        return new MarkAllReadResponse(notificationService.markAllRead(userId(authentication)));
    }

    /** Body of {@code PATCH /read-all}; {@code updated == 0} is a valid no-op. */
    public record MarkAllReadResponse(long updated) {
    }

    private long userId(JwtAuthenticationToken authentication) {
        AuthPrincipal principal = jwtService.toPrincipal(authentication.getToken());
        return principal.userId();
    }
}