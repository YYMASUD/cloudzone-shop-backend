package com.cloudzone.shopbackend.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    /**
     * The caller's latest still-open code. Resends supersede earlier unused codes — the
     * confirm path only ever checks the most recent one, so a stale code can't be
     * replayed after a resend.
     */
    Optional<EmailVerificationToken>
            findFirstByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(Long userId);
}
