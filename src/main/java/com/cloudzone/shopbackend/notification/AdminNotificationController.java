package com.cloudzone.shopbackend.notification;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin notification management: deliver a broadcast to one user and inspect their feed.
 * The whole {@code /api/admin/**} namespace is gated to ADMIN by {@code SecurityConfig},
 * so this controller only validates input — no role checks needed here.
 */
@RestController
@RequestMapping("/api/admin/notifications")
public class AdminNotificationController {

    private final NotificationService notificationService;

    public AdminNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** Delivers one notification to a user; an unknown target is a 404 {@code user_not_found}. */
    @PostMapping("/send")
    public NotificationResponse send(@Valid @RequestBody NotificationSendRequest request) {
        return notificationService.send(request);
    }

    /** The target user's feed, newest first — the admin's view of what was delivered. */
    @GetMapping(params = "userId")
    public List<NotificationResponse> listForUser(@RequestParam Long userId) {
        return notificationService.listForUser(userId);
    }
}