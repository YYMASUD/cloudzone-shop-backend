package com.cloudzone.shopbackend.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Fire-and-forget outbound email.
 *
 * <p>Every send runs on {@code notificationExecutor} (see {@code AsyncConfig}) so a slow
 * SMTP server never blocks the request thread that produced the event. Failures are
 * logged, never rethrown — email is best-effort notification, not transactional state.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final boolean enabled;

    public MailService(
            JavaMailSender mailSender,
            @Value("${app.mail.from:${MAIL_FROM:no-reply@shop.dev}}") String fromAddress,
            @Value("${app.mail.enabled:true}") boolean enabled) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.enabled = enabled;
    }

    /**
     * Sends a plain-text email. When {@code app.mail.enabled=false} (e.g. tests without
     * MailDev) the call is a no-op rather than a connection failure.
     */
    @Async("notificationExecutor")
    public void send(String to, String subject, String body) {
        if (!enabled || to == null || to.isBlank()) {
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (MailException e) {
            // Notification delivery is best-effort; a dead SMTP server must not fail the
            // business action that produced the event.
            log.warn("Email to {} failed: {}", to, e.getMessage());
        }
    }
}
