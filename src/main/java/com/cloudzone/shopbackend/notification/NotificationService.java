package com.cloudzone.shopbackend.notification;

import com.cloudzone.shopbackend.common.error.ApiException;
import com.cloudzone.shopbackend.common.web.PageResponse;
import com.cloudzone.shopbackend.user.UserRepository;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notification feed logic: the caller's own feed, unread badge, and read transitions,
 * plus the admin send path that produces entries until an orders module exists.
 *
 * <p>Ownership always comes from the verified JWT — every lookup is owner-scoped, so a
 * foreign id is indistinguishable from a missing one (404).
 */
@Service
public class NotificationService {

    /** Hard ceiling on {@code size}; pages larger than this are clamped, not rejected. */
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    // ── caller-facing ─────────────────────────────────────────────────────────

    /** The caller's feed, newest first, paged with a hard size cap. */
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Long userId, Pageable pageable) {
        Pageable effective = PageRequest.of(
                Math.max(pageable.getPageNumber(), 0),
                Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE));
        Page<NotificationResponse> page = notificationRepository
                .findAllByUserIdOrderByCreatedAtDescIdDesc(userId, effective)
                .map(NotificationResponse::from);
        return PageResponse.from(page);
    }

    /** The unread badge count for the caller. */
    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(Long userId) {
        return new UnreadCountResponse(
                notificationRepository.countByUserIdAndReadAtIsNull(userId));
    }

    /** Marks one own notification read; unknown or foreign ids are a 404. */
    @Transactional
    public NotificationResponse markRead(Long userId, Long id) {
        Notification notification = owned(userId, id);
        if (notification.getReadAt() == null) {
            // Truncated to microseconds: timestamptz columns store micros, so the
            // stamped value must match what the next read loads back from the DB.
            notification.setReadAt(OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS));
        }
        return NotificationResponse.from(notification);
    }

    /**
     * Marks all of the caller's unread notifications read in one statement.
     *
     * @return how many rows transitioned from unread to read
     */
    @Transactional
    public long markAllRead(Long userId) {
        return notificationRepository.markAllRead(
                userId, OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS));
    }

    // ── admin-facing ──────────────────────────────────────────────────────────

    /** Delivers a notification to one user; an unknown target is a 404. */
    @Transactional
    public NotificationResponse send(NotificationSendRequest request) {
        if (!userRepository.existsById(request.userId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "user_not_found",
                    "Unknown user: " + request.userId());
        }
        Notification saved = notificationRepository.save(Notification.builder()
                .userId(request.userId())
                .type(request.type())
                .title(request.title())
                .body(request.body())
                .link(request.link())
                .build());
        return NotificationResponse.from(saved);
    }

    /** Lists a target user's feed for admin inspection, newest first. */
    @Transactional(readOnly = true)
    public List<NotificationResponse> listForUser(Long userId) {
        return notificationRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId,
                        Pageable.unpaged()).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private Notification owned(Long userId, Long id) {
        return notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "notification_not_found",
                        "No notification %d for this user".formatted(id)));
    }
}