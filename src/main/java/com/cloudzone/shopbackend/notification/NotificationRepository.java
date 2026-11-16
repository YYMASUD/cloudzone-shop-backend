package com.cloudzone.shopbackend.notification;

import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Owner-scoped feed, newest first. */
    Page<Notification> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    /** The unread badge count for the caller. */
    long countByUserIdAndReadAtIsNull(Long userId);

    /** Owner-scoped single lookup; a foreign id is indistinguishable from a missing one. */
    java.util.Optional<Notification> findByIdAndUserId(Long id, Long userId);

    /**
     * Marks every unread row of one user read in a single statement. Bulk JPQL bypasses
     * the persistence context, so it flushes and clears first to avoid stale entities.
     *
     * @return the number of rows that actually transitioned from unread to read
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.readAt = :now "
            + "where n.userId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") Long userId, @Param("now") OffsetDateTime now);
}